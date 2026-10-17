import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import build_resident_cp_android_jni as build


class NativeLinkContractTest(unittest.TestCase):
    def test_android_link_rejects_missing_ffi_and_accepts_complete_ffi(self):
        sdk = Path(os.environ.get('ANDROID_HOME', Path.home()/'Android/Sdk'))
        compilers = sorted((sdk/'ndk').glob(
            '*/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android35-clang'))
        if not compilers:
            self.skipTest('Existing Linux Android NDK API 35 compiler required')
        clang = compilers[-1]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = root/'ffi.c'
            obj = root/'ffi.o'
            archive = root/'ffi.a'
            output = root/'jni.so'
            fixture.write_text('void empty_archive_fixture(void) {}\n')
            def compile_archive():
                subprocess.run([str(clang), '-c', '-fPIC', str(fixture), '-o', str(obj)],
                               check=True, capture_output=True)
                subprocess.run([str(clang.parent/'llvm-ar'), 'rcs', str(archive), str(obj)],
                               check=True, capture_output=True)
            compile_archive()
            run = subprocess.run
            with patch.object(build.subprocess, 'run', side_effect=lambda *a, **kw:
                              run(*a, capture_output=True, text=True, **kw)):
                with self.assertRaises(subprocess.CalledProcessError) as error:
                    build.link_jni(clang, archive, output)
            self.assertIn('undefined symbol: lami_cp_create', error.exception.stderr)
            self.assertFalse(build.receipt_path(output).exists())
            # Link-only fixtures: never executed and never used as Android build inputs.
            fixture.write_text('''#include "resident_cp_ffi.h"
int32_t lami_cp_create(const uint8_t *a, size_t b, const char *c, const char *d,
                       const char *e, uint64_t *f) { return 0; }
int32_t lami_cp_reset(uint64_t a) { return 0; }
int32_t lami_cp_cancel(uint64_t a) { return 0; }
int32_t lami_cp_release(uint64_t a) { return 0; }
int32_t lami_cp_step(uint64_t a, int32_t b, const float *c, float *d, size_t e) { return 0; }
int32_t lami_cp_stats(uint64_t a, uint64_t *b, size_t c) { return 0; }
void lami_qnn_log_read(char *out, size_t capacity) { if (capacity) out[0] = 0; }
''')
            subprocess.run([str(clang), '-c', '-fPIC', '-I', str(build.CRATE/'native'),
                            str(fixture), '-o', str(obj)], check=True, capture_output=True)
            subprocess.run([str(clang.parent/'llvm-ar'), 'rcs', str(archive), str(obj)],
                           check=True, capture_output=True)
            build.link_jni(clang, archive, output)
            self.assertTrue(output.is_file())


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

    def test_rust_identity_rejects_unrecorded_environment_before_running_tools(self):
        overrides = ('RUSTC', 'RUSTC_WRAPPER', 'RUSTC_WORKSPACE_WRAPPER',
                     'RUSTFLAGS', 'CARGO_ENCODED_RUSTFLAGS', 'CARGO_BUILD_RUSTC',
                     'CARGO_BUILD_RUSTC_WRAPPER', 'CARGO_BUILD_RUSTC_WORKSPACE_WRAPPER',
                     'CARGO_BUILD_RUSTFLAGS',
                     'CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS')
        for name in overrides:
            with self.subTest(name=name), patch.dict(os.environ, {name: 'custom'}, clear=True), \
                    patch.object(build.subprocess, 'run') as run:
                with self.assertRaisesRegex(ValueError, 'unset: ' + name + '$'):
                    build.rust_identity()
                run.assert_not_called()

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
