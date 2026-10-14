import tempfile
import unittest
import zipfile
from pathlib import Path
from stage_resident_cp_qairt247 import stage, inspect_apk, RUNTIME, JNI

class RuntimeStageTest(unittest.TestCase):
    def test_staging_and_legacy_apk_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            base=Path(root)
            sdk=base/'sdk'; runtime=sdk/'lib/aarch64-android'; runtime.mkdir(parents=True)
            for name in RUNTIME: (runtime/name).write_bytes(('247:'+name).encode())
            jni=base/JNI; jni.write_bytes(b'arm64')
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
            m=stage(base/'sdk',jni,base/'stage')
            apk=base/'ok.apk'
            with zipfile.ZipFile(apk,'w') as z:
                for name in (*RUNTIME,JNI): z.write(base/'stage'/name,arcname='lib/arm64-v8a/'+name)
            self.assertTrue(inspect_apk(apk,m))
            with zipfile.ZipFile(apk,'a') as z: z.writestr('lib/arm64-v8a/libcdsprpc.so',b'x')
            with self.assertRaisesRegex(ValueError,'bundled'): inspect_apk(apk,m)
if __name__=='__main__': unittest.main()
