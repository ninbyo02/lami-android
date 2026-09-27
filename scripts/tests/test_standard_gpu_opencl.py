import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("staging", Path(__file__).resolve().parents[1]/"stage_standard_gpu_opencl.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class RuntimePinTests(unittest.TestCase):
    def test_complete_set_passes(self):
        self.assertEqual([], m.validate({"gpu.so": b"gpu", "npu.so": b"npu"}, {"gpu.so": m.digest(b"gpu"), "npu.so": m.digest(b"npu")}))
    def test_npu_mismatch_blocks_gpu_staging(self):
        self.assertEqual(["hash mismatch npu.so"], m.validate({"gpu.so": b"gpu", "npu.so": b"wrong"}, {"gpu.so": m.digest(b"gpu"), "npu.so": m.digest(b"npu")}))
    def test_missing_component_fails(self):
        self.assertEqual(["missing gpu.so"], m.validate({}, {"gpu.so": m.digest(b"gpu")}))

if __name__ == "__main__": unittest.main()
