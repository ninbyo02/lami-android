import unittest
import numpy as np
from metrics import audio_metrics, summarize


class MetricBoundaryTests(unittest.TestCase):
    def test_rtf_uses_audio_duration_not_wall_clock_total(self):
        result = audio_metrics(np.full(24000, 0.25), 24000, 200)
        self.assertEqual(result["audio_seconds"], 1)
        self.assertAlmostEqual(result["rtf"], 0.2)

    def test_invalid_audio_cannot_count_as_success(self):
        for pcm in [[], [float("nan")], [float("inf")], [0.0] * 10, [1.01], [[0.2]]]:
            with self.subTest(pcm=pcm), self.assertRaises(ValueError):
                audio_metrics(pcm, 24000, 20)

    def test_warm_summary_excludes_cold_and_failed_requests(self):
        rows = [
            {"status": "ok", "start_kind": "cold_first_inference", "pcm_ready_ms": 9000, "rtf": 9, "audio_seconds": 1, "repeat": 0},
            {"status": "ok", "start_kind": "warm", "pcm_ready_ms": 100, "rtf": 0.1, "audio_seconds": 1, "repeat": 0},
            {"status": "ok", "start_kind": "warm", "pcm_ready_ms": 400, "rtf": 0.2, "audio_seconds": 2, "repeat": 1},
            {"status": "failed", "start_kind": "warm"},
        ]
        result = summarize(rows)
        self.assertEqual(result["warm_pcm_ready_ms_p50"], 250)
        self.assertAlmostEqual(result["warm_rtf_aggregate"], 0.5 / 3)
        self.assertEqual(result["failed"], 1)
        self.assertEqual(result["first_pass"]["observations"], 1)
        self.assertEqual(result["repeated"]["rtf_p50"], 0.2)


if __name__ == "__main__":
    unittest.main()
