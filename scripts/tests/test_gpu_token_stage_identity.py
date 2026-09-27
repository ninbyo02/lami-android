"""Reject stale device success and preserve separate batch artifacts."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'run_gpu_token_stage_isolated.sh'
MOCK_ADB = '''#!/usr/bin/env python3
import os, pathlib, sys
args = sys.argv[1:]
root = pathlib.Path(os.environ['MOCK_ROOT'])
if 'broadcast' in args:
    root.joinpath('timestamp').write_text(args[args.index('timestamp') + 1])
elif 'files/litert_lm_gpu_benchmark_state.txt' in args:
    stamp = root.joinpath('timestamp').read_text() if os.environ['MOCK_STATE'] == 'fresh' else 'previous_run'
    print('timestamp=' + stamp)
    print('status=success\\nreason=completed\\nfallback_count=0\\ntimeout_count=0\\ncsv_file=old.csv')
elif 'files/old.csv' in args:
    root.joinpath('csv_read').touch()
'''

class StageIdentityTest(unittest.TestCase):
    def run_stage(self, mode):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adb = root / 'adb'
            adb.write_text(MOCK_ADB)
            adb.chmod(0o755)
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'],
                       MOCK_ROOT=directory, MOCK_STATE=mode)
            result = subprocess.run(['bash', str(SCRIPT), '512', '1', '1', 'saturation'],
                                    cwd=root, env=env, capture_output=True, text=True, timeout=30)
            summaries = list(root.glob('artifacts/gpu-token-stage-512-saturation/*/summary.tsv'))
            self.assertEqual(1, len(summaries))
            summary = summaries[0].read_text()
            if mode == 'fresh':
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn('\tsuccess\tcompleted\t0\t0', summary)
                self.assertTrue((root / 'csv_read').exists())
            else:
                self.assertNotEqual(0, result.returncode)
                self.assertIn('\tfailure\tmissing_current_run_state\tunknown\tunknown', summary)
                self.assertFalse((root / 'csv_read').exists())
    def test_current_run_success(self):
        self.run_stage('fresh')
    def test_previous_success_is_rejected(self):
        self.run_stage('stale')

if __name__ == '__main__':
    unittest.main()
