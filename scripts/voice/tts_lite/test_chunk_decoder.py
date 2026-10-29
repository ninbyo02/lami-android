import unittest
import torch

from chunk_decoder import iter_pcm_windows, plan_windows, projected_playback_gaps


class ChunkBoundaryTests(unittest.TestCase):
    def test_short_exact_and_partial_tail_cover_the_signal_once(self):
        for length in [1, 15, 16, 17, 31, 32, 47, 48, 49, 137]:
            with self.subTest(length=length):
                windows = list(plan_windows(length, 16, 16))
                covered = [i for w in windows for i in range(w.core_start, w.core_end)]
                self.assertEqual(covered, list(range(length)))
                for w in windows:
                    self.assertEqual(w.core_start - w.source_start + w.valid_start, 16)
                    self.assertLessEqual(w.valid_end, 48)
                    self.assertGreater(w.valid_end, w.valid_start)

    def test_invalid_window_sizes_are_rejected(self):
        for values in [(0, 16, 16), (10, 0, 16), (10, 16, -1)]:
            with self.assertRaises(ValueError):
                list(plan_windows(*values))

    def test_gap_projection_uses_generated_audio_frontier(self):
        result = projected_playback_gaps([20, 40, 250], [100, 100, 100], 1000)
        self.assertEqual(result, {"projected_gaps": 1, "projected_gap_ms": 30})
        self.assertEqual(projected_playback_gaps([20, 40, 60], [100, 100, 100], 1000)["projected_gaps"], 0)

    def test_stop_does_not_compute_remaining_chunks_or_build_gradients(self):
        calls = []
        class Decoder(torch.nn.Module):
            def forward(self, latent, *masks):
                calls.append(torch.is_grad_enabled())
                return torch.ones(1, 1, latent.shape[-1] * 2)
        stream = iter_pcm_windows(torch.ones(1, 3, 40), Decoder(), 4, 2, [2])
        _, pcm = next(stream)
        self.assertEqual(len(pcm), 8)
        stream.close()
        self.assertEqual(calls, [False])


if __name__ == "__main__":
    unittest.main()
