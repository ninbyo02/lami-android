import hashlib
import copy
import shutil
from unittest.mock import patch
import tempfile
import unittest
import zipfile
from pathlib import Path
from stage_resident_cp_qairt247 import stage, inspect_apk, RUNTIME, JNI, FULL_RUNTIME, SKELETON

class RuntimeStageTest(unittest.TestCase):
    def setUp(self):
        self.pins = {}
        self.patch = patch('stage_resident_cp_qairt247.SDK_PINS', self.pins)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def evidence(self, sdk):
        (sdk/'sdk.yaml').write_text('product: QAIRT\nversion: 2.47.0\nbuild_id: 260601114230\n')
        for path in (sdk/'lib').rglob('*.so'):
            self.pins[path.name] = hashlib.sha256(path.read_bytes()).hexdigest()

    def test_ambiguous_sdk_version_evidence_rejected_before_staging(self):
        with tempfile.TemporaryDirectory() as root:
            base = Path(root)
            sdk = base/'sdk'
            runtime = sdk/'lib/aarch64-android'
            runtime.mkdir(parents=True)
            for name in RUNTIME:
                (runtime/name).write_bytes(name.encode())
            jni = base/JNI
            jni.write_bytes(b'jni')
            self.evidence(sdk)
            metadata = sdk/'sdk.yaml'
            valid = metadata.read_text()
            for key, wrong in (('product', 'OTHER'), ('version', '2.44.0'),
                               ('build_id', 'legacy')):
                for declaration in (key + ': ' + wrong,
                                    '"' + key + '": ' + wrong,
                                    "'" + key + "': " + wrong,
                                    '  ' + key + ': ' + wrong):
                    with self.subTest(declaration=declaration):
                        metadata.write_text(valid + declaration + '\n')
                        destination = base/'rejected'
                        with self.assertRaisesRegex(ValueError, 'version evidence: ' + key):
                            stage(sdk, jni, destination)
                        self.assertFalse(destination.exists())
            # A value on another line must not be consumed by whitespace matching.
            metadata.write_text(valid.replace('version: 2.47.0', 'version:\n2.47.0'))
            with self.assertRaisesRegex(ValueError, 'version evidence: version'):
                stage(sdk, jni, base/'rejected')
            self.assertFalse((base/'rejected').exists())
            # Preserve the official metadata's nested OS mapping and comments.
            metadata.write_text('# SDK metadata\nos:\n  Ubuntu: 22.04\n' + valid)
            self.assertEqual(stage(sdk, jni, base/'valid')['profile'], 'diagnostic')

    def test_full_stage_rejects_legacy_jni_before_renaming(self):
        with tempfile.TemporaryDirectory() as root:
            base = Path(root)
            sdk = base/'sdk'
            for name in FULL_RUNTIME:
                path = sdk/'lib/aarch64-android'/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(name.encode())
            skeleton = sdk/'lib/hexagon-v79/unsigned'/SKELETON
            skeleton.parent.mkdir(parents=True)
            skeleton.write_bytes(b'hexagon')
            self.evidence(sdk)
            legacy = base/'liblami_QAIRT244_npu_jni.so'
            legacy.write_bytes(b'legacy JNI')
            alias = base/JNI
            alias.symlink_to(legacy.name)
            # Reject both the explicit name and a neutral symlink alias before
            # creating output that would hide the known legacy identity.
            for jni in (legacy, alias):
                with self.subTest(jni=jni):
                    destination = base/'rejected'
                    with self.assertRaisesRegex(ValueError, 'Legacy QAIRT JNI input'):
                        stage(sdk, jni, destination, 'voice-trial')
                    self.assertFalse(destination.exists())
            manifest = stage(sdk, legacy, base/'diagnostic')
            self.assertEqual(manifest['profile'], 'diagnostic')
            self.assertIn('unverified', manifest['jni_provenance'])

    def test_changed_copy_cannot_receive_a_verified_manifest(self):
        with tempfile.TemporaryDirectory() as root:
            base = Path(root)
            sdk = base/'sdk'
            for name in FULL_RUNTIME:
                path = sdk/'lib/aarch64-android'/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(name.encode())
            skeleton = sdk/'lib/hexagon-v79/unsigned'/SKELETON
            skeleton.parent.mkdir(parents=True)
            skeleton.write_bytes(b'hexagon')
            jni = base/JNI
            jni.write_bytes(b'jni')
            self.evidence(sdk)
            copyfile = shutil.copyfile

            for index, changed in enumerate((*FULL_RUNTIME, SKELETON, JNI)):
                with self.subTest(library=changed):
                    destination = base/('stage-' + str(index))

                    def changed_copy(src, dst):
                        result = copyfile(src, dst)
                        if src.name == changed:
                            dst.write_bytes(b'changed after input verification')
                        return result

                    with patch('stage_resident_cp_qairt247.shutil.copyfile',
                               side_effect=changed_copy):
                        with self.assertRaisesRegex(ValueError, 'Staged copy/hash mismatch'):
                            stage(sdk, jni, destination, 'voice-trial')
                    self.assertFalse((destination/'manifest.json').exists())

            # Failed independent stages leave the inputs usable and unchanged.
            manifest = stage(sdk, jni, base/'valid', 'voice-trial')
            for name, digest in manifest['files'].items():
                self.assertEqual(hashlib.sha256((base/'valid'/name).read_bytes()).hexdigest(),
                                 digest)

    def test_staging_and_legacy_apk_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            base=Path(root)
            sdk=base/'sdk'; runtime=sdk/'lib/aarch64-android'; runtime.mkdir(parents=True)
            for name in RUNTIME: (runtime/name).write_bytes(('247:'+name).encode())
            jni=base/JNI; jni.write_bytes(b'arm64')
            self.evidence(sdk)
            m=stage(sdk,jni,base/'stage')
            with self.assertRaises(ValueError): stage(sdk,jni,base/'stage')
            apk=base/'wrong.apk'
            with zipfile.ZipFile(apk,'w') as z:
                for name in RUNTIME: z.writestr('lib/arm64-v8a/'+name,b'244')
                z.writestr('lib/arm64-v8a/'+JNI,b'arm64')
            with self.assertRaisesRegex(ValueError,'version/hash mismatch'): inspect_apk(apk,m)
    def test_exact_hashes_accepted_and_private_rpc_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            base=Path(root); runtime=base/'sdk/lib/aarch64-android';runtime.mkdir(parents=True)
            for name in RUNTIME: (runtime/name).write_bytes(name.encode())
            jni=base/JNI;jni.write_bytes(b'jni')
            self.evidence(base/'sdk')
            m=stage(base/'sdk',jni,base/'stage')
            apk=base/'ok.apk'
            with zipfile.ZipFile(apk,'w') as z:
                for name in (*RUNTIME,JNI): z.write(base/'stage'/name,arcname='lib/arm64-v8a/'+name)
            self.assertTrue(inspect_apk(apk,m))
            with zipfile.ZipFile(apk,'a') as z: z.writestr('lib/arm64-v8a/libcdsprpc.so',b'x')
            with self.assertRaisesRegex(ValueError,'bundled'): inspect_apk(apk,m)
    def test_full_profile_and_fail_closed_inputs(self):
        with tempfile.TemporaryDirectory() as root:
            base = Path(root)
            sdk = base/'sdk'
            for name in FULL_RUNTIME:
                path = sdk/'lib/aarch64-android'/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(name.encode())
            skel = sdk/'lib/hexagon-v79/unsigned'/SKELETON
            skel.parent.mkdir(parents=True)
            skel.write_bytes(b'hexagon')
            jni = base/JNI
            jni.write_bytes(b'jni')
            with self.assertRaisesRegex(ValueError, 'version evidence'):
                stage(sdk, jni, base/'missing')
            self.evidence(sdk)
            metadata = sdk/'sdk.yaml'
            valid = metadata.read_text()
            metadata.write_text(valid.replace('2.47.0', '2.44.0'))
            with self.assertRaisesRegex(ValueError, 'version evidence'):
                stage(sdk, jni, base/'wrong')
            metadata.write_text(valid)
            original = skel.read_bytes()
            skel.write_bytes(b'244')
            with self.assertRaisesRegex(ValueError, 'version/hash mismatch'):
                stage(sdk, jni, base/'mixed', 'voice-trial')
            skel.write_bytes(original)
            m = stage(sdk, jni, base/'stage', 'voice-trial')
            self.assertFalse((base/'stage'/SKELETON).exists())
            apk = base/'full.apk'

            def package(extra=None):
                with zipfile.ZipFile(apk, 'w') as z:
                    for name in m['files']:
                        prefix = 'assets/' if name.startswith('hexagon-v79/') else 'lib/arm64-v8a/'
                        z.write(base/'stage'/name, prefix+name)
                    if extra:
                        z.writestr(extra, b'legacy')

            package()
            self.assertTrue(inspect_apk(apk, m))
            for extra in ('lib/arm64-v8a/liblami_qairt244_npu_jni.so',
                          'lib/arm64-v8a/libQnnCpu.so',
                          'lib/arm64-v8a/'+SKELETON):
                package(extra)
                with self.assertRaisesRegex(ValueError, 'Unpinned/legacy'):
                    inspect_apk(apk, m)
            package('lib/arm64-v8a/'+JNI)
            with self.assertRaisesRegex(ValueError, 'Duplicate'):
                inspect_apk(apk, m)
            package()
            m['files']['libQnnGpu.so'] = '0'*64
            with self.assertRaisesRegex(ValueError, 'Unpinned'):
                inspect_apk(apk, m)

    def test_diagnostic_manifest_cannot_override_sdk_pins_or_skip_libraries(self):
        with tempfile.TemporaryDirectory() as root:
            base = Path(root)
            runtime = base/'sdk/lib/aarch64-android'
            runtime.mkdir(parents=True)
            for name in RUNTIME:
                (runtime/name).write_bytes(name.encode())
            jni = base/JNI
            jni.write_bytes(b'jni')
            self.evidence(base/'sdk')
            manifest = stage(base/'sdk', jni, base/'stage')
            # Existing callers may omit the profile field.
            del manifest['profile']
            apk = base/'diagnostic.apk'
            with zipfile.ZipFile(apk, 'w') as z:
                for name in manifest['files']:
                    z.write(base/'stage'/name, 'lib/arm64-v8a/'+name)
            self.assertTrue(inspect_apk(apk, manifest))
            cases = []
            unknown = copy.deepcopy(manifest)
            unknown['profile'] = 'voice_trial'
            cases.append((unknown, 'Unknown staging profile'))
            for name in manifest['files']:
                missing = copy.deepcopy(manifest)
                del missing['files'][name]
                cases.append((missing, 'Incomplete diagnostic manifest'))
            invalid_jni = copy.deepcopy(manifest)
            invalid_jni['files'][JNI] = None
            cases.append((invalid_jni, 'Invalid JNI hash'))
            for candidate, error in cases:
                with self.subTest(error=error, manifest=candidate):
                    with self.assertRaisesRegex(ValueError, error):
                        inspect_apk(apk, candidate)
            # Even a manifest matching the APK bytes cannot bless an unpinned runtime.
            manifest['files'][RUNTIME[0]] = hashlib.sha256(b'244').hexdigest()
            with zipfile.ZipFile(apk, 'w') as z:
                for name in manifest['files']:
                    z.writestr('lib/arm64-v8a/'+name,
                               b'244' if name == RUNTIME[0] else (base/'stage'/name).read_bytes())
            with self.assertRaisesRegex(ValueError, 'Unpinned QAIRT manifest'):
                inspect_apk(apk, manifest)

    def test_diagnostic_checks_optional_qnn_libraries_without_requiring_them(self):
        with tempfile.TemporaryDirectory() as root:
            base = Path(root)
            runtime = base/'sdk/lib/aarch64-android'
            runtime.mkdir(parents=True)
            for name in FULL_RUNTIME:
                (runtime/name).write_bytes(name.encode())
            skeleton = base/'sdk/lib/hexagon-v79/unsigned'/SKELETON
            skeleton.parent.mkdir(parents=True)
            skeleton.write_bytes(b'hexagon')
            jni = base/JNI
            jni.write_bytes(b'jni')
            self.evidence(base/'sdk')
            manifest = stage(base/'sdk', jni, base/'stage')
            del manifest['profile']  # Preserve the original diagnostic API.
            apk = base/'diagnostic.apk'

            def package(extra, payload):
                with zipfile.ZipFile(apk, 'w') as z:
                    for name in manifest['files']:
                        z.write(base/'stage'/name, 'lib/arm64-v8a/'+name)
                    z.writestr(extra, payload)

            for name in set(FULL_RUNTIME) - set(RUNTIME):
                with self.subTest(library=name):
                    path = 'lib/arm64-v8a/'+name
                    package(path, name.encode())
                    self.assertTrue(inspect_apk(apk, manifest))
                    package(path, b'244')
                    with self.assertRaisesRegex(ValueError, 'version/hash mismatch'):
                        inspect_apk(apk, manifest)
            package('assets/hexagon-v79/'+SKELETON, b'hexagon')
            self.assertTrue(inspect_apk(apk, manifest))
            package('assets/hexagon-v79/'+SKELETON, b'244')
            with self.assertRaisesRegex(ValueError, 'version/hash mismatch'):
                inspect_apk(apk, manifest)
            for path in ('lib/arm64-v8a/'+SKELETON,
                         'lib/armeabi-v7a/libQnnGpu.so',
                         'lib/arm64-v8a/libQnnCpu.so',
                         'lib/arm64-v8a/libqnnGpu.so'):
                package(path, b'unpinned')
                with self.subTest(path=path):
                    with self.assertRaisesRegex(ValueError, 'Unpinned/legacy'):
                        inspect_apk(apk, manifest)

if __name__=='__main__': unittest.main()
