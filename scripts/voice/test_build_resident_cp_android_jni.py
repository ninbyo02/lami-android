import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import build_resident_cp_android_jni as build


class NativeBuildProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.sdk = self.root/'sdk'
        header = self.sdk/'include/QNN/QnnInterface.h'
        header.parent.mkdir(parents=True)
        header.write_bytes(b'247 headers')
        (self.sdk/'sdk.yaml').write_text(
            'product: QAIRT\nversion: 2.47.0\nbuild_id: 260601114230\n')
        digest = hashlib.sha256(b'QnnInterface.h\0' +
                                hashlib.sha256(header.read_bytes()).digest()).hexdigest()
        pins = {}
        for name in build.FULL_RUNTIME:
            path = self.sdk/'lib/aarch64-android'/name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(name.encode())
            pins[name] = build.sha(path)
        self.skel = self.sdk/'lib/hexagon-v79/unsigned'/build.SKELETON
        self.skel.parent.mkdir(parents=True)
        self.skel.write_bytes(b'v79 dsp')
        pins[build.SKELETON] = build.sha(self.skel)
        for name, value in (('HEADER_PIN', digest), ('SDK_PINS', pins)):
            mock = patch.object(build, name, value)
            mock.start()
            self.addCleanup(mock.stop)

    def test_pinned_sdk_rejects_header_runtime_and_version_mixtures(self):
        identity = build.sdk_identity(self.sdk)
        self.assertEqual(identity['android_abi'], 'arm64-v8a')
        self.assertEqual(identity['dsp_arch'], 'hexagon-v79')
        for path in (self.sdk/'include/QNN/QnnInterface.h', self.skel,
                     self.sdk/'lib/aarch64-android/libQnnHtp.so', self.sdk/'sdk.yaml'):
            original = path.read_bytes()
            path.write_bytes(b'244')
            with self.assertRaises(ValueError):
                build.sdk_identity(self.sdk)
            path.write_bytes(original)

    def test_rust_identity_binds_compiler_and_android_standard_library(self):
        sysroot = self.root/'rust'
        (sysroot/'bin').mkdir(parents=True)
        for name in ('rustc', 'cargo'):
            (sysroot/'bin'/name).write_bytes(name.encode())
        libraries = sysroot/'target-libs'
        libraries.mkdir()
        std = libraries/'libstd-test.rlib'
        std.write_bytes(b'android std')
        from types import SimpleNamespace
        def run(args, **kwargs):
            value = (str(sysroot) if args[-1] == 'sysroot' else
                     str(libraries) if 'target-libdir' in args else 'version')
            return SimpleNamespace(stdout=value)
        with patch.object(build.subprocess, 'run', side_effect=run):
            identity = build.rust_identity()
            self.assertEqual(identity['tools_sha256']['rustc'], build.sha(sysroot/'bin/rustc'))
            std.write_bytes(b'different std')
            self.assertNotEqual(build.rust_identity(), identity)
            std.unlink()
            with self.assertRaisesRegex(ValueError, 'Missing installed Rust Android'):
                build.rust_identity()

    def test_receipt_binds_binary_sources_and_separate_architectures(self):
        jni = self.root/'test.so'
        jni.write_bytes(b'jni')
        with self.assertRaises(FileNotFoundError):
            build.verify_jni(jni, self.sdk)
        rust = {'rustc_version': 'test compiler', 'target_rlib_sha256': {'std.rlib': 'a'*64}}
        mock = patch.object(build, 'rust_identity', return_value=rust)
        mock.start()
        self.addCleanup(mock.stop)
        receipt = {'schema': 2, 'rust': rust, 'sdk': build.sdk_identity(self.sdk),
                   'sources': build.source_identity(), 'api': 35,
                   'tools_sha256': {'clang': 'a'*64}, 'jni_sha256': build.sha(jni)}
        sidecar = build.receipt_path(jni)
        sidecar.write_text(json.dumps(receipt))
        self.assertEqual(build.verify_jni(jni, self.sdk), receipt)
        for field, value in (('jni_sha256', '0'*64), ('sources', {}),
                             ('sdk', {'android_abi': 'hexagon-v79'}), ('rust', {}), ('schema', 1)):
            wrong = {**receipt, field: value}
            sidecar.write_text(json.dumps(wrong))
            with self.assertRaisesRegex(ValueError, 'rebuild required'):
                build.verify_jni(jni, self.sdk)
        sidecar.write_text(json.dumps(receipt))
        jni.write_bytes(b'unknown binary')
        with self.assertRaisesRegex(ValueError, 'rebuild required'):
            build.verify_jni(jni, self.sdk)


if __name__ == '__main__':
    unittest.main()
