import unittest
import numpy as np

from prepare_qnn_inputs import pack_native


class QnnInputContractTests(unittest.TestCase):
    def test_native_bytes_are_time_major_not_channel_major(self):
        source = np.arange(6, dtype=np.float32).reshape(1, 2, 3)
        native, restored = pack_native(source, {"data_type": 0x0216, "dims": [1, 3, 2], "permute_order_to_src": [0, 2, 1]})
        self.assertEqual(np.frombuffer(native.tobytes(), dtype="<f2").tolist(), [0, 3, 1, 4, 2, 5])
        np.testing.assert_array_equal(restored, source)

    def test_unexpected_native_type_or_dimensions_are_rejected(self):
        for descriptor in [{"data_type": 0, "dims": [1, 2, 3]},
                           {"data_type": 0x0216, "dims": [1, 3, 2]}]:
            with self.assertRaises(ValueError):
                pack_native(np.zeros((1, 2, 3), dtype=np.float32), descriptor)


if __name__ == "__main__":
    unittest.main()
