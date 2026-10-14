"""Use existing SDK headers to test C loader ordering; never load any SDK runtime."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
import resource


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--verify-order-regression', action='store_true')
    args = parser.parse_args()
    include = args.sdk / 'include/QNN'
    compiler = shutil.which('cc')
    if not compiler or not (include / 'QnnInterface.h').is_file():
        raise RuntimeError('Existing C compiler and SDK headers are required; no installation')
    with tempfile.TemporaryDirectory(prefix='lami-rpc-loader-test-') as directory:
        binary = Path(directory) / 'rpc-loader-test'
        subprocess.run([compiler, '-std=gnu11', '-O2', '-Wall', '-Wextra', '-Werror',
                        '-I', str(include), str(ROOT / 'diagnostics/resident-cp-rust/native/test_rpc_loader.c'),
                        '-o', str(binary)], check=True, timeout=120)
        subprocess.run([str(binary)], check=True, timeout=30)
        if args.verify_order_regression:
            native = ROOT / 'diagnostics/resident-cp-rust/native'
            source = (native / 'qnn_bridge.c').read_text()
            correct = ('    const char *stage = "rpc_loader";\n'
                       '    uint64_t status = rpc_library ? prepare_rpc(s, rpc_library) : 0;\n'
                       '    if (!status) { stage = "providers"; status = providers(s, backend, system); }')
            wrong = ('    const char *stage = "providers";\n'
                     '    uint64_t status = providers(s, backend, system);\n'
                     '    if (!status && rpc_library) { stage = "rpc_loader"; status = prepare_rpc(s, rpc_library); }')
            if source.count(correct) != 1:
                raise RuntimeError('Order mutation anchor changed; inspect the test rather than silently skipping it')
            work = Path(directory)
            (work / 'qnn_bridge.c').write_text(source.replace(correct, wrong))
            shutil.copyfile(native / 'test_rpc_loader.c', work / 'test_rpc_loader.c')
            mutant = work / 'wrong-order-test'
            subprocess.run([compiler, '-std=gnu11', '-O2', '-Wall', '-Wextra', '-Werror',
                            '-I', str(include), str(work / 'test_rpc_loader.c'), '-o', str(mutant)],
                           check=True, timeout=120)
            result = subprocess.run([str(mutant)], capture_output=True, timeout=30,
                                    preexec_fn=lambda: resource.setrlimit(resource.RLIMIT_CORE, (0, 0)))
            if result.returncode == 0:
                raise RuntimeError('Loader tests failed to detect SDK-before-RPC regression')
            print('Negative control: SDK-before-RPC order was rejected by the same test')


if __name__ == '__main__':
    main()
