"""Acoustic partition adapted from pinned MeloTTS SynthesizerTrn.infer.

The pretrained encoder, predictors and flow are reused unchanged. See
THIRD_PARTY_NOTICES.md for MyShell.ai's MIT notice and the source revision.
"""
import torch


class MeloAcoustic(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, x, x_lengths, sid, tone, language, bert, ja_bert):
        from melo import commons
        model = self.model
        g = model.emb_g(sid).unsqueeze(-1)
        x, m_p, logs_p, x_mask = model.enc_p(
            x, x_lengths, tone, language, bert, ja_bert,
            g=None if model.use_vc else g,
        )
        logw = model.sdp(x, x_mask, g=g, reverse=True, noise_scale=0.8) * 0.2
        logw = logw + model.dp(x, x_mask, g=g) * (1 - 0.2)
        w_ceil = torch.ceil(torch.exp(logw) * x_mask)
        y_lengths = torch.clamp_min(torch.sum(w_ceil, [1, 2]), 1).long()
        y_mask = torch.unsqueeze(commons.sequence_mask(y_lengths, None), 1).to(x_mask.dtype)
        attn_mask = torch.unsqueeze(x_mask, 2) * torch.unsqueeze(y_mask, -1)
        attn = commons.generate_path(w_ceil, attn_mask)
        m_p = torch.matmul(attn.squeeze(1), m_p.transpose(1, 2)).transpose(1, 2)
        logs_p = torch.matmul(attn.squeeze(1), logs_p.transpose(1, 2)).transpose(1, 2)
        z_p = m_p + torch.randn_like(m_p) * torch.exp(logs_p) * 0.6
        z = model.flow(z_p, y_mask, g=g, reverse=True)
        return z * y_mask, g
