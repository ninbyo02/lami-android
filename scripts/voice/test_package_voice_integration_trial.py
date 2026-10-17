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
                    Path(command[command.index('--out')+1]).write_bytes(Path(command[-1]).read_bytes())
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

    def test_verified_delivery_reports_skipped_behavior_and_rejects_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = SimpleNamespace(output=root/'out.apk', result=root/'result.json',
                model_manifest=root/'manifest', model_manifest_sha256='a'*64,
                sdk=root/'sdk', jni=root/'jni', source=root/'source.apk',
                build_tools=root, keystore=root/'key')
            args.source.write_bytes(b'source apk')
            def package(source, staged, candidate, tools, key, **kwargs):
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
                 patch('package_voice_integration_trial.accept_host_pipeline', return_value={'host_pipeline_tests': 'passed'}), \
                 patch('package_voice_integration_trial.pack', side_effect=package):
                self.assertEqual(deliver(args), 0)
            report = json.loads(args.result.read_text())
            self.assertEqual(report['local_delivery'], 'passed')
            self.assertEqual(report['host_pipeline_tests'], 'passed')
            self.assertEqual(report['device_speech'], 'unverified')
            self.assertEqual(report['pcm_eos_stop'], 'unverified')
            self.assertEqual(report['normal_llm_streaming'], 'unverified')
            self.assertEqual(report['fallback']['classification'], 'not_executed')
            self.assertEqual(report['signed_provenance_asset'], TRIAL_RECEIPT)
            self.assertEqual(args.output.read_bytes(), b'candidate')
            with self.assertRaisesRegex(ValueError, 'Refuse existing'): deliver(args)

    def test_host_failure_blocks_packaging_and_publication(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = SimpleNamespace(output=root/'out.apk', result=root/'result.json',
                model_manifest=root/'manifest', model_manifest_sha256='a'*64,
                sdk=root/'sdk', jni=root/'jni')
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
            self.assertEqual(report['fallback']['classification'], 'not_executed')
            self.assertFalse(args.output.exists())


if __name__ == '__main__':
    unittest.main()
