import hashlib
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

from package_voice_integration_trial import accept_host_pipeline, deliver, verify_model
from package_resident_cp_diagnostic_apk import pack, TRIAL_RECEIPT
from stage_resident_cp_qairt247 import FULL_RUNTIME, JNI, SKELETON


class TrialDeliveryTest(unittest.TestCase):
    def test_model_hashes_and_escape_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            model = root/'manifest.json'
            (root/'weights').write_bytes(b'unchanged')
            def manifest(files):
                model.write_text(json.dumps({'speaker': 'lami_cute', 'sha256': files}))
                return hashlib.sha256(model.read_bytes()).hexdigest()
            digest = manifest({'weights': hashlib.sha256(b'unchanged').hexdigest()})
            self.assertEqual(verify_model(model, digest)['speaker'], 'lami_cute')
            (root/'weights').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'Model file'): verify_model(model, digest)
            digest = manifest({'../escape': '0'*64})
            with self.assertRaisesRegex(ValueError, 'path mismatch'): verify_model(model, digest)
            with self.assertRaisesRegex(ValueError, 'manifest SHA'): verify_model(model, '0'*64)

    def test_full_payload_replaces_old_jni_and_places_dsp_in_assets(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            staged = root/'stage'; staged.mkdir()
            files = [*FULL_RUNTIME, JNI, 'hexagon-v79/' + SKELETON]
            for name in files:
                target = staged/name; target.parent.mkdir(exist_ok=True)
                target.write_bytes(name.encode())
            (staged/'manifest.json').write_text(json.dumps({'profile': 'voice-trial',
                'files': {n: hashlib.sha256(n.encode()).hexdigest() for n in files}}))
            source = root/'source.apk'
            with zipfile.ZipFile(source, 'w') as z:
                z.writestr('classes.dex', b'preserved')
                z.writestr('META-INF/MANIFEST.MF', b'old signing metadata')
                z.writestr('lib/arm64-v8a/' + JNI, b'old')
                z.writestr('lib/arm64-v8a/libQnnCpu.so', b'244')
                z.writestr(TRIAL_RECEIPT, b'stale receipt')
            key = root/'key'; key.write_bytes(b'key')
            def run(command, **kwargs):
                if 'badging' in command:
                    return SimpleNamespace(stdout="package: name='io.github.ninbyo02.lami.voiceintegrationtrial'")
                if 'xmltree' in command:
                    return SimpleNamespace(stdout='extractNativeLibs(0x010104ea)=true')
                if 'zipalign' in command[0]:
                    Path(command[-1]).write_bytes(Path(command[-2]).read_bytes())
                if 'sign' in command:
                    with zipfile.ZipFile(Path(command[-1])) as origin, \
                         zipfile.ZipFile(Path(command[command.index('--out')+1]), 'w') as signed:
                        for item in origin.infolist():
                            signed.writestr(item, b'new signing metadata' if
                                            item.filename == 'META-INF/MANIFEST.MF' else origin.read(item))
                return SimpleNamespace(stdout='')
            output = root/'out.apk'
            with patch('package_resident_cp_diagnostic_apk.subprocess.run',
                       return_value=SimpleNamespace(stdout="package: name='io.github.ninbyo02.lami'")):
                with self.assertRaisesRegex(ValueError, 'isolated source'):
                    pack(source, staged, output, root, key, voice_trial=True)
            self.assertFalse(output.exists())
            with patch('package_resident_cp_diagnostic_apk.subprocess.run', side_effect=run), \
                 patch('package_resident_cp_diagnostic_apk.inspect_apk', return_value=True):
                pack(source, staged, output, root, key, voice_trial=True,
                     trial_receipt={'schema': 1, 'device_speech': 'unverified'})
            with zipfile.ZipFile(output) as z:
                self.assertEqual(z.read('classes.dex'), b'preserved')
                self.assertEqual(z.namelist().count('lib/arm64-v8a/'+JNI), 1)
                self.assertEqual(z.read('lib/arm64-v8a/'+JNI), JNI.encode())
                self.assertIn('assets/hexagon-v79/'+SKELETON, z.namelist())
                self.assertNotIn('lib/arm64-v8a/libQnnCpu.so', z.namelist())
                self.assertEqual(z.namelist().count(TRIAL_RECEIPT), 1)
                self.assertEqual(json.loads(z.read(TRIAL_RECEIPT)),
                                 {'schema': 1, 'device_speech': 'unverified'})
            # The signed artifact must retain isolation and native extraction,
            # even when the input APK and runtime hashes were accepted.
            for altered in ('package', 'extraction'):
                def changed_identity(command, **kwargs):
                    if str(root/'changed.apk') in command:
                        if altered == 'package' and 'badging' in command:
                            return SimpleNamespace(stdout="package: name='io.github.ninbyo02.lami'")
                        if altered == 'extraction' and 'xmltree' in command:
                            return SimpleNamespace(stdout='extractNativeLibs(0x010104ea)=false')
                    return run(command, **kwargs)
                changed = root/'changed.apk'
                with self.subTest(altered=altered), \
                     patch('package_resident_cp_diagnostic_apk.subprocess.run', side_effect=changed_identity), \
                     patch('package_resident_cp_diagnostic_apk.inspect_apk', return_value=True):
                    with self.assertRaisesRegex(ValueError, 'isolated source|extractNativeLibs'):
                        pack(source, staged, changed, root, key, voice_trial=True)
                changed.unlink()
            # Signature tooling must preserve the accepted input receipt.
            def corrupt(command, **kwargs):
                result = run(command, **kwargs)
                if 'sign' in command:
                    with zipfile.ZipFile(Path(command[command.index('--out')+1]), 'a') as z:
                        z.writestr(TRIAL_RECEIPT, b'changed')
                return result
            with patch('package_resident_cp_diagnostic_apk.subprocess.run', side_effect=corrupt), \
                 patch('package_resident_cp_diagnostic_apk.inspect_apk', return_value=True):
                with self.assertWarns(UserWarning), self.assertRaisesRegex(ValueError, 'provenance mismatch'):
                    pack(source, staged, root/'corrupt.apk', root, key, voice_trial=True,
                         trial_receipt={'schema': 1})

            # A valid signature/identity/runtime receipt cannot authorize changed
            # app code, missing resources or ambiguous duplicate payload entries.
            for alteration in ('changed', 'missing', 'duplicate'):
                def corrupt_payload(command, **kwargs):
                    result = run(command, **kwargs)
                    if 'sign' in command:
                        signed = Path(command[command.index('--out')+1])
                        with zipfile.ZipFile(signed) as z:
                            entries = [(item, z.read(item)) for item in z.infolist()]
                        with zipfile.ZipFile(signed, 'w') as z:
                            for item, data in entries:
                                if item.filename == 'classes.dex':
                                    if alteration == 'missing':
                                        continue
                                    if alteration == 'changed':
                                        data = b'changed speech wiring'
                                z.writestr(item, data)
                            if alteration == 'duplicate':
                                z.writestr('classes.dex', b'preserved')
                    return result
                with self.subTest(alteration=alteration), \
                     patch('package_resident_cp_diagnostic_apk.subprocess.run', side_effect=corrupt_payload), \
                     patch('package_resident_cp_diagnostic_apk.inspect_apk', return_value=True):
                    if alteration == 'duplicate':
                        with self.assertWarns(UserWarning), self.assertRaisesRegex(ValueError, 'source payload mismatch'):
                            pack(source, staged, root/'duplicate.apk', root, key, voice_trial=True)
                    else:
                        with self.assertRaisesRegex(ValueError, 'source payload mismatch'):
                            pack(source, staged, root/(alteration+'.apk'), root, key, voice_trial=True)

    def test_verified_delivery_reports_skipped_behavior_and_rejects_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = SimpleNamespace(output=root/'out.apk', result=root/'result.json',
                model_manifest=root/'manifest', model_manifest_sha256='a'*64,
                sdk=root/'sdk', jni=root/'jni', source=root/'source.apk',
                build_tools=root, keystore=root/'key')
            args.source.write_bytes(b'source apk')
            def host_acceptance(output):
                # A concurrent controller build must not change this delivery.
                args.source.write_bytes(b'new controller build')
                return {'host_pipeline_tests': 'passed'}
            def package(source, staged, candidate, tools, key, **kwargs):
                self.assertNotEqual(source, args.source)
                self.assertEqual(source.read_bytes(), b'source apk')
                self.assertTrue(kwargs['voice_trial'])
                self.assertEqual(kwargs['trial_receipt']['source_apk_sha256'],
                                 hashlib.sha256(b'source apk').hexdigest())
                self.assertEqual(kwargs['trial_receipt']['model']['speaker'], 'lami_cute')
                self.assertEqual(kwargs['trial_receipt']['device_speech'], 'unverified')
                self.assertEqual(kwargs['trial_receipt']['host_pipeline_evidence']['host_pipeline_tests'], 'passed')
                candidate.write_bytes(b'candidate')
            with patch('package_voice_integration_trial.verify_model', return_value={'speaker': 'lami_cute'}), \
                 patch('package_voice_integration_trial.verify_jni', return_value={'jni_sha256': 'b'*64}), \
                 patch('package_voice_integration_trial.stage', return_value={'files': {JNI: 'b'*64}}), \
                 patch('package_voice_integration_trial.accept_host_pipeline', side_effect=host_acceptance), \
                 patch('package_voice_integration_trial.pack', side_effect=package):
                self.assertEqual(deliver(args), 0)
            report = json.loads(args.result.read_text())
            self.assertEqual(report['local_delivery'], 'passed')
            self.assertEqual(report['host_pipeline_tests'], 'passed')
            self.assertEqual(report['source_payload_preserved'], 'passed')
            self.assertEqual(report['device_speech'], 'unverified')
            self.assertEqual(report['pcm_eos_stop'], 'unverified')
            self.assertEqual(report['normal_llm_streaming'], 'unverified')
            self.assertEqual(report['fallback']['classification'], 'not_executed')
            self.assertEqual(report['signed_provenance_asset'], TRIAL_RECEIPT)
            self.assertEqual(report['source_apk_sha256'], hashlib.sha256(b'source apk').hexdigest())
            self.assertEqual(args.output.read_bytes(), b'candidate')
            with self.assertRaisesRegex(ValueError, 'Refuse existing'): deliver(args)

    def test_host_failure_blocks_packaging_and_publication(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = SimpleNamespace(output=root/'out.apk', result=root/'result.json',
                model_manifest=root/'manifest', model_manifest_sha256='a'*64,
                sdk=root/'sdk', jni=root/'jni', source=root/'source.apk')
            args.source.write_bytes(b'source apk')
            with patch('package_voice_integration_trial.verify_model', return_value={}), \
                 patch('package_voice_integration_trial.verify_jni', return_value={}), \
                 patch('package_voice_integration_trial.accept_host_pipeline',
                       side_effect=ValueError('Offline host speech pipeline acceptance failed')), \
                 patch('package_voice_integration_trial.pack') as package, \
                 patch('package_voice_integration_trial.stage') as stage:
                self.assertEqual(deliver(args), 1)
                package.assert_not_called()
                stage.assert_not_called()
            report = json.loads(args.result.read_text())
            self.assertEqual(report['host_pipeline_tests'], 'failed')
            self.assertEqual(report['local_delivery'], 'failed')
            self.assertEqual(report['device_speech'], 'unverified')
            self.assertFalse(args.output.exists())

    def test_changed_source_copy_blocks_delivery_before_host_acceptance(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = SimpleNamespace(output=root/'out.apk', result=root/'result.json',
                model_manifest=root/'manifest', model_manifest_sha256='a'*64,
                sdk=root/'sdk', jni=root/'jni', source=root/'source.apk')
            args.source.write_bytes(b'original')
            def changed_copy(source, destination):
                destination.write_bytes(b'concurrent replacement')
            with patch('package_voice_integration_trial.verify_model', return_value={}), \
                 patch('package_voice_integration_trial.verify_jni', return_value={}), \
                 patch('package_voice_integration_trial.shutil.copyfile', side_effect=changed_copy), \
                 patch('package_voice_integration_trial.accept_host_pipeline') as host, \
                 patch('package_voice_integration_trial.pack') as package:
                self.assertEqual(deliver(args), 1)
                host.assert_not_called()
                package.assert_not_called()
            report = json.loads(args.result.read_text())
            self.assertEqual(report['local_delivery'], 'failed')
            self.assertIn('Source APK changed', report['error'])
            self.assertEqual(report['host_pipeline_tests'], 'unverified')
            self.assertEqual(report['device_speech'], 'unverified')
            self.assertFalse(args.output.exists())

    def test_host_acceptance_requires_success_and_explicit_evidence(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)/'host.json'
            for code, status in ((0, None), (0, 'unverified'), (1, 'passed')):
                with self.subTest(code=code, status=status):
                    output.write_text(json.dumps({'host_pipeline_tests': status}))
                    with patch('package_voice_integration_trial.subprocess.run',
                               return_value=SimpleNamespace(returncode=code, stderr='')):
                        with self.assertRaisesRegex(ValueError, 'acceptance failed'):
                            accept_host_pipeline(output)
            output.write_text(json.dumps({'host_pipeline_tests': 'passed'}))
            with patch('package_voice_integration_trial.subprocess.run',
                       return_value=SimpleNamespace(returncode=0, stderr='')) as run:
                self.assertEqual(accept_host_pipeline(output)['host_pipeline_tests'], 'passed')
                self.assertIn('run_speech_pipeline_host_tests.py', run.call_args.args[0][1])

    def test_failed_delivery_keeps_behavior_unverified(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = SimpleNamespace(output=root/'out.apk', result=root/'result.json',
                                   model_manifest=root/'missing', model_manifest_sha256='0'*64)
            self.assertEqual(deliver(args), 1)
            report = json.loads(args.result.read_text())
            self.assertEqual(report['local_delivery'], 'failed')
            self.assertEqual(report['pcm_eos_stop'], 'unverified')
            self.assertEqual(report['source_payload_preserved'], 'unverified')
            self.assertEqual(report['fallback']['classification'], 'not_executed')
            self.assertFalse(args.output.exists())


if __name__ == '__main__':
    unittest.main()
