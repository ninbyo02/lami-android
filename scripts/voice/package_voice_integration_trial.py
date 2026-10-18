#!/usr/bin/env python3
"""PC-only isolated trial delivery; never builds with Gradle or installs an APK.

Build the isolated source APK first, using the installed Android SDK and JDK 21:
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ANDROID_HOME=/home/sato/Android/Sdk ANDROID_SDK_ROOT=/home/sato/Android/Sdk \
  ./gradlew --offline --no-daemon :app:assembleStandardDebug -Plami.voiceIntegrationTrial=true

The trial flag disconnects the QAIRT 2.44 standardDebug staging/overlays and
excludes dependency-provided QNN libraries. That intermediate APK is not a
complete voice runtime: this script supplies the verified QAIRT 2.47 payload.

Then invoke this script with that APK, existing QAIRT SDK, rebuilt JNI with its
.provenance.json receipt, lami_cute model manifest + independently expected SHA,
build-tools and debug keystore. --result is a new machine-readable JSON file.
The existing offline JVM speech suite must pass before packaging. Its result is
bound into the signed receipt. No device behavioral check is counted as passed.
The signed assets/lami-voice-trial-provenance.json binds the source APK,
verified model hashes and QAIRT/JNI inputs; it is not device acceptance.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
import tempfile

from build_resident_cp_android_jni import verify_jni
from package_resident_cp_diagnostic_apk import pack, TRIAL_RECEIPT
from stage_resident_cp_qairt247 import stage, sha, JNI


def verify_model(manifest, expected):
    if not re.fullmatch('[0-9a-f]{64}', expected) or sha(manifest) != expected:
        raise ValueError('Model manifest SHA-256 mismatch')
    data = json.loads(manifest.read_text())
    if data.get('speaker') != 'lami_cute' or not data.get('sha256'):
        raise ValueError('Expected lami_cute model manifest with file hashes')
    root = manifest.parent.resolve()
    for name, digest in data['sha256'].items():
        path = (root / name).resolve()
        if (not path.is_relative_to(root) or not path.is_file() or
                not re.fullmatch('[0-9a-f]{64}', digest) or sha(path) != digest):
            raise ValueError('Model file SHA-256/path mismatch: ' + name)
    return {'manifest_sha256': expected, 'files': data['sha256'], 'speaker': 'lami_cute'}


def accept_host_pipeline(output):
    """Run the maintained offline suite; never accept missing or skipped evidence."""
    runner = Path(__file__).with_name('run_speech_pipeline_host_tests.py')
    completed = subprocess.run(
        [sys.executable, str(runner), '--output', str(output)],
        capture_output=True, text=True, timeout=180,
    )
    evidence = json.loads(output.read_text()) if output.is_file() else {}
    if completed.returncode != 0 or evidence.get('host_pipeline_tests') != 'passed':
        raise ValueError('Offline host speech pipeline acceptance failed: ' +
                         (completed.stderr[-2000:] or str(evidence)))
    return evidence


def deliver(args):
    report = {'schema': 1, 'local_delivery': 'failed',
              'application_id': 'io.github.ninbyo02.lami.voiceintegrationtrial',
              'device_speech': 'unverified', 'pcm_eos_stop': 'unverified',
              'normal_llm_streaming': 'unverified',
              'fallback': {'status': 'unverified', 'classification': 'not_executed'},
              'controller_gradle_tests': 'unverified',
              'source_payload_preserved': 'unverified',
              'host_pipeline_tests': 'unverified'}
    if args.result.exists() or args.output.exists() or args.result.resolve() == args.output.resolve():
        raise ValueError('Refuse existing or overlapping output/result')
    try:
        model = verify_model(args.model_manifest, args.model_manifest_sha256)
        receipt = verify_jni(args.jni, args.sdk)
        # Reuse the pinned full-profile staging and existing alignment/signature
        # pipeline; publish only after its APK library hash acceptance succeeds.
        with tempfile.TemporaryDirectory(prefix='.voice-trial-', dir=args.output.parent) as temp:
            root = Path(temp)
            # Bind the receipt and packaged code to the same immutable input.
            # The controller may replace its build output while host tests run.
            source_sha256 = sha(args.source)
            source_snapshot = root/'source.apk'
            shutil.copyfile(args.source, source_snapshot)
            if sha(source_snapshot) != source_sha256:
                raise ValueError('Source APK changed while snapshotting')
            report['host_pipeline_tests'] = 'failed'
            host = accept_host_pipeline(root/'host-pipeline.json')
            report['host_pipeline_tests'] = 'passed'
            report['host_pipeline_evidence'] = host
            runtime = stage(args.sdk, args.jni, root/'runtime', 'voice-trial')
            if runtime['files'][JNI] != receipt['jni_sha256']:
                raise ValueError('JNI changed after provenance verification')
            # Stage's generic warning is superseded only by verify_jni above.
            runtime['jni_provenance'] = 'verified_source_build_receipt'
            candidate = root/'trial.apk'
            trial_receipt = {'schema': 1, 'application_id': report['application_id'],
                             'source_apk_sha256': source_sha256,
                             'model': model, 'runtime': runtime,
                             'jni_provenance': receipt,
                             'host_pipeline_evidence': host,
                             'device_speech': 'unverified'}
            pack(source_snapshot, root/'runtime', candidate, args.build_tools,
                 args.keystore, voice_trial=True, trial_receipt=trial_receipt)
            report.update(local_delivery='passed', apk_sha256=sha(candidate),
                          source_payload_preserved='passed',
                          model=model, runtime=runtime,
                          jni_provenance=receipt,
                          signed_provenance_asset=TRIAL_RECEIPT,
                          source_apk_sha256=trial_receipt['source_apk_sha256'])
            args.output.hardlink_to(candidate)
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError,
            zipfile.BadZipFile) as error:
        report['local_delivery'] = 'failed'
        report['error'] = type(error).__name__ + ': ' + str(error)
    with args.result.open('x') as result:
        result.write(json.dumps(report, indent=2) + '\n')
    return 0 if report['local_delivery'] == 'passed' else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    for name in ('source', 'sdk', 'jni', 'model-manifest', 'build-tools',
                 'keystore', 'output', 'result'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--model-manifest-sha256', required=True)
    args = parser.parse_args()
    return deliver(args)


if __name__ == '__main__':
    raise SystemExit(main())
