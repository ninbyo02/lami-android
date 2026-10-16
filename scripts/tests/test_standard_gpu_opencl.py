import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("staging", Path(__file__).resolve().parents[1]/"stage_standard_gpu_opencl.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class RuntimePinTests(unittest.TestCase):
    def test_qnn_only_keeps_all_qnn_components(self):
        pins = {"libQnnSystem.so": "a", "libQnnHtp.so": "b", "libLiteRt.so": "c", "libLiteRtOpenClAccelerator.so": "d"}
        self.assertEqual(m.select_expected(pins, True), {"libQnnSystem.so": "a", "libQnnHtp.so": "b"})
        self.assertEqual(m.select_expected(pins), pins)
    def test_old_qnn_system_blocks_npu_only(self):
        pins = m.select_expected({"libQnnSystem.so": m.digest(b"sdk244"), "libLiteRtOpenClAccelerator.so": "ignored"}, True)
        self.assertEqual(m.validate({"libQnnSystem.so": b"old"}, pins), ["hash mismatch libQnnSystem.so"])
        self.assertEqual(m.validate({"libQnnSystem.so": b"sdk244"}, pins), [])
    def test_complete_set_passes(self):
        self.assertEqual([], m.validate({"gpu.so": b"gpu", "npu.so": b"npu"}, {"gpu.so": m.digest(b"gpu"), "npu.so": m.digest(b"npu")}))
    def test_npu_mismatch_blocks_gpu_staging(self):
        self.assertEqual(["hash mismatch npu.so"], m.validate({"gpu.so": b"gpu", "npu.so": b"wrong"}, {"gpu.so": m.digest(b"gpu"), "npu.so": m.digest(b"npu")}))
    def test_missing_component_fails(self):
        self.assertEqual(["missing gpu.so"], m.validate({}, {"gpu.so": m.digest(b"gpu")}))

if __name__ == "__main__": unittest.main()
