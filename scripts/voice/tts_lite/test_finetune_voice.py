import unittest

import torch

from finetune_voice import collate, finite_step


class TrainingBoundaryTests(unittest.TestCase):
    def test_collate_preserves_audio_text_pairing(self):
        def row(token, frames):
            return (torch.tensor([token, token]), torch.full((3, frames), float(token)),
                    torch.full((1, frames * 512), float(token)), torch.tensor([1, 1]), torch.tensor([1, 1]))
        phone, lengths, spec, spec_lengths, audio, _, _ = collate([row(3, 2), row(7, 5)])
        self.assertEqual(phone[:, 0].tolist(), [7, 3])
        self.assertEqual(spec_lengths.tolist(), [5, 2])
        self.assertEqual(lengths.tolist(), [2, 2])
        self.assertTrue(torch.all(spec[0] == 7))
        self.assertTrue(torch.all(audio[1, :, :1024] == 3))
        self.assertTrue(torch.all(audio[1, :, 1024:] == 0))

    def test_finite_step_updates_weights(self):
        net = torch.nn.Linear(1, 1, bias=False)
        before = net.weight.detach().clone()
        optimizer = torch.optim.SGD(net.parameters(), lr=0.1)
        norm = finite_step((net(torch.ones(1, 1)) - 10).square().sum(), net, optimizer)
        self.assertGreater(norm, 0)
        self.assertFalse(torch.equal(before, net.weight))

    def test_nonfinite_gradient_does_not_update_weights(self):
        parameter = torch.nn.Parameter(torch.tensor(0.0))
        net = torch.nn.ParameterList([parameter])
        optimizer = torch.optim.SGD(net.parameters(), lr=0.1)
        with self.assertRaises(RuntimeError):
            finite_step(torch.sqrt(parameter), net, optimizer)
        self.assertEqual(float(parameter.detach()), 0)


if __name__ == "__main__":
    unittest.main()
