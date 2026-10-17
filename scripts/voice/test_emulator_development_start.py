import io
import json
import unittest
from unittest.mock import patch
from start_emulator_development import health


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


if __name__=='__main__': unittest.main()
