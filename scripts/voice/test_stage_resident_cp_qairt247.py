import hashlib
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

if __name__=='__main__': unittest.main()
