import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_tokenizer_only import PIN, NDK_VERSION
from verify_tokenizer_ci import LIB, artifact_files, distributable_snapshot, verify_apk
from make_tokenizer_ci_fixture import generate

class TokenizerCiTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.artifact=self.root/'artifact'
        self.payload=b'native-test';self.notice=b'license-test'
        lib=self.artifact/'jniLibs/arm64-v8a/liblami_tokenizer_only.so';lib.parent.mkdir(parents=True);lib.write_bytes(self.payload)
        notice=self.artifact/'notices/tokenizer_only/LICENSE';notice.parent.mkdir(parents=True);notice.write_bytes(self.notice)
        (self.artifact/'manifest.json').write_text(json.dumps({'sentencepiece_commit':PIN,'abi':'arm64-v8a','sha256':hashlib.sha256(self.payload).hexdigest(),'toolchain':{'ndk':NDK_VERSION}}))
    def tearDown(self):self.temp.cleanup()
    def apk(self,name,entries):
        path=self.root/name
        with zipfile.ZipFile(path,'w') as z:
            for k,v in entries.items():z.writestr(k,v)
        return path
    def enabled(self):return {LIB:self.payload,'assets/tokenizer_only/LICENSE':self.notice,'lib/arm64-v8a/libexisting.so':b'unchanged'}
    def test_inclusion_exclusion_and_existing_libraries(self):
        a=self.apk('a.apk',self.enabled());b=self.apk('b.apk',{'lib/arm64-v8a/libexisting.so':b'unchanged'})
        self.assertTrue(verify_apk(a,self.artifact,True,b)['baseline_compared'])
        verify_apk(b,self.artifact,False)
    def test_missing_license_wrong_library_and_wrong_abi_fail(self):
        for name,entries in [('license',{LIB:self.payload}),('hash',{**self.enabled(),LIB:b'bad'}),('abi',{**self.enabled(),'lib/x86_64/liblami_tokenizer_only.so':self.payload})]:
            with self.subTest(name=name),self.assertRaises(ValueError):verify_apk(self.apk(name+'.apk',entries),self.artifact,True)
    def test_disabled_leaks_and_existing_native_changes_fail(self):
        a=self.apk('a.apk',self.enabled())
        with self.assertRaises(ValueError):verify_apk(a,self.artifact,False)
        b=self.apk('b.apk',{'lib/arm64-v8a/libexisting.so':b'changed'})
        with self.assertRaises(ValueError):verify_apk(a,self.artifact,True,b)
    def test_mutated_artifact_and_extra_library_fail(self):
        p=self.artifact/'jniLibs/arm64-v8a/liblami_tokenizer_only.so';p.write_bytes(b'bad')
        with self.assertRaises(ValueError):artifact_files(self.artifact)
        p.write_bytes(self.payload);(p.parent/'libextra.so').write_bytes(b'extra')
        with self.assertRaises(ValueError):artifact_files(self.artifact)
    def test_manifest_difference_breaks_distributable_reproducibility(self):
        second=self.root/'second';shutil.copytree(self.artifact,second)
        manifest=json.loads((second/'manifest.json').read_text());manifest['source_date_epoch']=1
        (second/'manifest.json').write_text(json.dumps(manifest))
        self.assertNotEqual(distributable_snapshot(self.artifact),distributable_snapshot(second))

    def test_fixture_is_deterministic_and_small(self):
        a=self.root/'one';b=self.root/'two';self.assertEqual(generate(a),generate(b))
        self.assertEqual((a/'tiny.litertlm').read_bytes(),(b/'tiny.litertlm').read_bytes())
        self.assertLess((a/'tiny.model').stat().st_size,4096)

if __name__=='__main__':unittest.main()
