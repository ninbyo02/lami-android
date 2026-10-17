import io
import json
import unittest
import tempfile
import shlex
import sys
from pathlib import Path
from unittest.mock import patch
from start_emulator_development import health, install_shortcuts


class BridgeIdentityTest(unittest.TestCase):
    def check(self, value):
        with patch('start_emulator_development.urllib.request.urlopen',
                   return_value=io.StringIO(json.dumps(value))):
            return health()

    def test_known_cpu_reference_is_reusable(self):
        self.assertIsNotNone(self.check(dict(backend='host_litert_cpu', model_alias='gemma4-e2b-litert-cpu')))

    def test_other_service_or_model_is_not_reused(self):
        self.assertIsNone(self.check([]))
        self.assertIsNone(self.check(None))
        self.assertIsNone(self.check(dict(backend='ollama', model_alias='gemma4-e2b-litert-cpu')))
        self.assertIsNone(self.check(dict(backend='host_litert_cpu', model_alias='other-model')))

    def test_unavailable_or_malformed_health_is_not_success(self):
        with patch('start_emulator_development.urllib.request.urlopen',side_effect=OSError('offline')):
            self.assertIsNone(health())
        with patch('start_emulator_development.urllib.request.urlopen',return_value=io.StringIO('not JSON')):
            self.assertIsNone(health())


class LauncherInstallTest(unittest.TestCase):
    def test_current_checkout_and_space_in_path(self):
        with tempfile.TemporaryDirectory() as directory:
            home=Path(directory); script=home/'repo with spaces'/'start.py'
            with patch('start_emulator_development.subprocess.run'):
                paths=install_shortcuts(home,script)
            self.assertEqual(len(paths),2)
            for name in paths:
                text=Path(name).read_text()
                line=next(line for line in text.splitlines() if line.startswith('Exec='))
                self.assertEqual(shlex.split(line[5:]),[sys.executable,str(script),'start'])
                self.assertTrue(Path(name).stat().st_mode & 0o111)

    def test_newline_path_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                install_shortcuts(Path(directory),Path('bad\npath.py'))


if __name__=='__main__': unittest.main()
