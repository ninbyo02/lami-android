"""SDK-free real JNI smoke test; fake FFI is prohibited in Android builds."""
import hashlib
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]


def run(command, **kwargs):
    subprocess.run([str(value) for value in command], check=True, timeout=180, **kwargs)


def main():
    crate = ROOT / 'diagnostics/resident-cp-rust'
    java = Path(shutil.which('java')).resolve()
    jdk = java.parents[1]
    compiler = shutil.which('cc')
    cargo = shutil.which('cargo') or str(Path.home() / '.cargo/bin/cargo')
    if not compiler or not (jdk / 'include/jni.h').is_file():
        raise RuntimeError('Existing C compiler/JDK headers are required; no automatic installation')
    candidates = []
    for jar in (Path.home() / '.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib').glob('*/*/*.jar'):
        try:
            version = tuple(int(value) for value in jar.parents[1].name.split('-')[0].split('.'))
        except ValueError:
            continue
        candidates.append((version, jar))
    if not candidates:
        raise RuntimeError('Run the existing standardDebug unit test build first')
    stdlib = max(candidates)[1]
    classes = ROOT / 'app/build/tmp/kotlin-classes/standardDebug'
    if not (classes / 'io/github/ninbyo02/lami/tts/ResidentCpRustJniProbe.class').is_file():
        raise RuntimeError('Missing compiled debug-only JNI probe')
    with tempfile.TemporaryDirectory(prefix='lami-cp-jni-') as directory:
        work = Path(directory)
        env = {**os.environ, 'CARGO_TARGET_DIR': str(work / 'target')}
        # ffi-host-test has no QNN feature or SDK header/runtime dependency.
        run([cargo, 'build', '--release', '--offline', '--locked', '--manifest-path', crate / 'Cargo.toml',
             '--features', 'ffi-host-test', '--lib'], env=env)
        library = work / 'liblami_resident_cp_diag_host.so'
        run([compiler, '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror', '-fPIC', '-shared',
             crate / 'native/resident_cp_jni.c', '-I', crate / 'native', '-I', jdk / 'include',
             '-I', jdk / 'include/linux', work / 'target/release/liblami_resident_cp.a',
             '-ldl', '-lm', '-lpthread', '-o', library])
        runtime = work / 'host-test'; runtime.mkdir()
        for name in ('libQnnHtp.so', 'libQnnSystem.so'):
            (runtime / name).write_bytes(b'fake host fixture only')
        # Exercise the same explicit SDK-stub load ordering without any vendor code.
        stub_source = work / 'host_stub.c'
        stub_source.write_text('void lami_cp_host_stub_fixture(void) {}\n')
        run([compiler, '-fPIC', '-shared', stub_source, '-o', runtime / 'libQnnHtpV79Stub.so'])
        model = runtime / 'context.bin'; model.write_bytes(b'LAMI_CP_HOST_TEST')
        inputs = work / 'inputs.f32'
        inputs.write_bytes(b''.join(struct.pack('<f', float((position + column) % 17) / 16)
                                   for position in range(16) for column in range(1024)))
        output = work / 'output'
        run([java, '-Xcheck:jni', '-cp', os.pathsep.join((str(classes), str(stdlib))),
             'io.github.ninbyo02.lami.tts.ResidentCpRustJniProbe', library, runtime, model,
             hashlib.sha256(model.read_bytes()).hexdigest(), inputs, inputs, output, 'host-test'])
        result = dict(line.split('=', 1) for line in (output / 'result.txt').read_text().splitlines())
        if result['status'] != 'complete' or result['golden_bit_equal'] != 'true' or result['after_cancel_native_stats'].split(',')[:5] != ['1', '49', '0', '4', '1']:
            raise RuntimeError('JNI lifecycle smoke proof incomplete')
        print('SDK-free JNI: reuse/cancel/release/stale rejection/fresh recovery passed; no hardware or artifacts uploaded')


if __name__ == '__main__':
    main()
