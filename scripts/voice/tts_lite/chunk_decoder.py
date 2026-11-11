"""Bounded, fixed-shape vocoder windows with exact finite-signal masking.

Generator orchestration is adapted from pinned MeloTTS; the original convolution
and residual-block modules are reused. See THIRD_PARTY_NOTICES.md.
"""
from dataclasses import dataclass

import numpy as np
import torch
from torch.nn import functional as F


@dataclass(frozen=True)
class Window:
    core_start: int
    core_end: int
    source_start: int
    source_end: int
    valid_start: int
    valid_end: int


def plan_windows(total: int, core: int, context: int):
    if total < 1 or core < 1 or context < 0:
        raise ValueError("Expected positive frame counts and nonnegative context")
    for start in range(0, total, core):
        end = min(total, start + core)
        source_start, source_end = max(0, start - context), min(total, start + core + context)
        valid_start = source_start - (start - context)
        yield Window(start, end, source_start, source_end,
                     valid_start, valid_start + source_end - source_start)


def make_window(z, window, core, context, upsample_rates):
    width = core + 2 * context
    latent = torch.zeros(z.shape[0], z.shape[1], width, dtype=z.dtype, device=z.device)
    latent[:, :, window.valid_start:window.valid_end] = z[:, :, window.source_start:window.source_end]
    masks, scale = [], 1
    for rate in [1, *upsample_rates]:
        scale *= rate
        mask = torch.zeros(1, 1, width * scale, dtype=z.dtype, device=z.device)
        mask[:, :, window.valid_start * scale:window.valid_end * scale] = 1
        masks.append(mask)
    return latent, tuple(masks)


class MaskedWindowDecoder(torch.nn.Module):
    def __init__(self, decoder, speaker):
        super().__init__()
        self.decoder = decoder
        self.register_buffer("speaker", speaker.detach().clone())

    def forward(self, latent, *masks):
        decoder = self.decoder
        x = (decoder.conv_pre(latent) + decoder.cond(self.speaker)) * masks[0]
        for i in range(decoder.num_upsamples):
            x = decoder.ups[i](F.leaky_relu(x, 0.1)) * masks[i + 1]
            xs = None
            for j in range(decoder.num_kernels):
                # Upstream ResBlock1 already supports masking before each
                # convolution, which prevents padded activations leaking inward.
                branch = decoder.resblocks[i * decoder.num_kernels + j](x, x_mask=masks[i + 1])
                if xs is None:
                    xs = branch
                else:
                    xs = xs + branch
            x = xs / decoder.num_kernels
        x = decoder.conv_post(F.leaky_relu(x)) * masks[-1]
        return torch.tanh(x)


def iter_pcm_windows(z, decoder, core, context, upsample_rates, runner=None):
    hop = int(np.prod(upsample_rates))
    for window in plan_windows(z.shape[-1], core, context):
        with torch.inference_mode():
            latent, masks = make_window(z, window, core, context, upsample_rates)
            if runner is None:
                pcm = decoder(latent, *masks)[0, 0].detach().cpu().numpy()
            else:
                feeds = {"latent": latent.cpu().numpy()}
                feeds.update({f"mask_{i}": mask.cpu().numpy() for i, mask in enumerate(masks)})
                pcm = runner.run(None, feeds)[0][0, 0]
        if pcm.shape != ((core + 2 * context) * hop,) or not np.isfinite(pcm).all():
            raise ValueError("Invalid fixed-window decoder output")
        length = (window.core_end - window.core_start) * hop
        offset = context * hop
        yield window, pcm[offset:offset + length].copy()


def compare_pcm(reference, actual, hop, core):
    if reference.shape != actual.shape or not np.isfinite(actual).all():
        raise ValueError("Invalid reconstructed waveform")
    error = np.abs(reference - actual)
    seams = []
    for index in range(core * hop, len(reference), core * hop):
        seams.append(float(np.max(error[max(0, index - hop):index + hop])))
    return {"max_abs_error": float(np.max(error)),
            "rms_error": float(np.sqrt(np.mean(error.astype(np.float64) ** 2))),
            "max_seam_error": max(seams, default=0.0), "seams": len(seams)}


def projected_playback_gaps(ready_ms, lengths, sample_rate):
    """A timestamp projection, not an AudioTrack underrun measurement."""
    if not ready_ms or len(ready_ms) != len(lengths) or sample_rate <= 0:
        raise ValueError("Expected matching, nonempty chunk timestamps and lengths")
    frontier = ready_ms[0]
    gaps = []
    for ready, length in zip(ready_ms, lengths):
        if ready > frontier:
            gaps.append(ready - frontier)
            frontier = ready
        frontier += length / sample_rate * 1000
    return {"projected_gaps": len(gaps), "projected_gap_ms": sum(gaps)}
