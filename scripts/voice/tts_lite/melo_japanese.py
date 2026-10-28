"""Japanese-only adapter around the unmodified, pinned MeloTTS implementation.

Import the neural model and Japanese frontend directly; the upstream general API
eagerly imports unrelated language frontends. The acoustic split is separately
checked against upstream inference; model modules and G2P are reused.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path
from unittest.mock import patch

MELO_REVISION = "209145371cff8fc3bd60d7be902ea69cbdb7965a"
TOKENIZER_REVISION = "65243d6e5629b969c77309f217bd7b1a79d43c7e"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


class JapaneseMelo:
    def __init__(self, source: Path, model_dir: Path, threads: int = 4, frontend: str = "upstream"):
        started = time.perf_counter()
        actual = subprocess.check_output(
            ["git", "-C", str(source), "rev-parse", "HEAD"], text=True
        ).strip()
        if actual != MELO_REVISION:
            raise ValueError(f"MeloTTS source revision mismatch: {actual}")
        if subprocess.check_output(
            ["git", "-C", str(source), "status", "--porcelain"], text=True
        ).strip():
            raise ValueError("MeloTTS source must be unmodified")
        sys.path.insert(0, str(source.resolve()))
        import torch
        from melo import commons
        from melo.models import SynthesizerTrn
        from melo.text import cleaned_text_to_sequence
        from transformers import AutoTokenizer
        tokenizer = AutoTokenizer.from_pretrained(
            "tohoku-nlp/bert-base-japanese-v3", revision=TOKENIZER_REVISION,
            token=False,
        )
        # The upstream module loads a tokenizer at import time. Supply the pinned
        # tokenizer during this isolated process's import without forking its G2P.
        with patch.object(AutoTokenizer, "from_pretrained", return_value=tokenizer):
            from melo.text import japanese

        if threads < 1:
            raise ValueError("threads must be positive")
        torch.set_num_threads(threads)
        torch.set_num_interop_threads(1)
        self.torch = torch
        self.commons = commons
        self.frontend = japanese
        if frontend not in ("upstream", "openjtalk"):
            raise ValueError(f"Unsupported frontend: {frontend}")
        self.frontend_mode = frontend
        self.reading = None
        if frontend == "openjtalk":
            import pyopenjtalk
            self.reading = pyopenjtalk
        self.to_ids = cleaned_text_to_sequence
        self.config = json.loads((model_dir / "config.json").read_text())
        cfg = self.config
        if not cfg["data"].get("disable_bert"):
            raise ValueError("This adapter requires the Japanese BERT-disabled model")
        self.symbol_to_id = {s: i for i, s in enumerate(cfg["symbols"])}
        self.sample_rate = cfg["data"]["sampling_rate"]
        self.speaker_id = cfg["data"]["spk2id"]["JP"]
        self.model = SynthesizerTrn(
            len(cfg["symbols"]), cfg["data"]["filter_length"] // 2 + 1,
            cfg["train"]["segment_size"] // cfg["data"]["hop_length"],
            n_speakers=cfg["data"]["n_speakers"],
            num_tones=cfg["num_tones"], num_languages=cfg["num_languages"],
            **cfg["model"],
        ).cpu().eval()
        checkpoint = torch.load(model_dir / "checkpoint.pth", map_location="cpu", weights_only=True)
        self.model.load_state_dict(checkpoint["model"], strict=True)
        candidate = checkpoint.get("tts_lite", {})
        self.init_ms = (time.perf_counter() - started) * 1000
        self.provenance = {
            "source_revision": actual,
            "checkpoint_sha256": sha256(model_dir / "checkpoint.pth"),
            "config_sha256": sha256(model_dir / "config.json"),
            "model": "myshell-ai/MeloTTS-Japanese",
            "voice": candidate.get("voice", "upstream_JP_0_not_LAMI"),
            "torch": torch.__version__, "threads": threads,
            "sample_rate": self.sample_rate,
            "tokenizer_revision": TOKENIZER_REVISION,
            "frontend": frontend,
            "reading_version": getattr(self.reading, "__version__", None),
        }
        if candidate:
            self.provenance["training"] = candidate

    def prepare_text(self, text: str):
        torch = self.torch
        started = time.perf_counter()
        reading = self.reading.g2p(text, kana=True) if self.reading else text
        normalized = self.frontend.text_normalize(reading)
        if not normalized.strip():
            raise ValueError("No pronounceable text after upstream normalization")
        phones, tones, _ = self.frontend.g2p(normalized)
        phone, tone, language = self.to_ids(phones, tones, "JP", self.symbol_to_id)
        if self.config["data"]["add_blank"]:
            phone = self.commons.intersperse(phone, 0)
            tone = self.commons.intersperse(tone, 0)
            language = self.commons.intersperse(language, 0)
        if len(phone) > 1024:
            raise ValueError("Probe input exceeds 1024 phone IDs; split at sentence boundaries")
        inputs = (
            torch.tensor([phone]), torch.tensor([len(phone)]),
            torch.tensor([self.speaker_id]), torch.tensor([tone]),
            torch.tensor([language]), torch.zeros(1, 1024, len(phone)),
            torch.zeros(1, 768, len(phone)),
        )
        return inputs, {
            "reading_input": reading, "normalized_text": normalized,
            "phone_ids": len(phone), "frontend_ms": (time.perf_counter() - started) * 1000,
        }

    def generate_latents(self, text: str, seed: int = 42):
        from melo_acoustic import MeloAcoustic
        started = time.perf_counter()
        inputs, metrics = self.prepare_text(text)
        frontend_done = time.perf_counter()
        with self.torch.inference_mode():
            self.torch.manual_seed(seed)
            z, speaker = MeloAcoustic(self.model)(*inputs)
        finished = time.perf_counter()
        metrics.update(acoustic_ms=(finished - frontend_done) * 1000,
                       latent_ready_ms=(finished - started) * 1000, latent_frames=z.shape[-1])
        return z, speaker, metrics

    def generate(self, text: str, seed: int = 42, capture_decoder: bool = False):
        torch = self.torch
        started = time.perf_counter()
        inputs, frontend_metrics = self.prepare_text(text)
        frontend_done = time.perf_counter()
        captured = {}
        def before_decoder(module, args, kwargs):
            captured["z"] = args[0].detach().clone()
            captured["g"] = kwargs["g"].detach().clone()
        hook = self.model.dec.register_forward_pre_hook(before_decoder, with_kwargs=True) if capture_decoder else None
        try:
            with torch.inference_mode():
                torch.manual_seed(seed)
                pcm = self.model.infer(
                    *inputs, sdp_ratio=0.2, noise_scale=0.6,
                    noise_scale_w=0.8, length_scale=1.0,
                )[0][0, 0].cpu().numpy().copy()
        finally:
            if hook is not None:
                hook.remove()
        finished = time.perf_counter()
        from metrics import audio_metrics
        metrics = audio_metrics(pcm, self.sample_rate, (finished - started) * 1000)
        metrics.update(frontend_metrics)
        metrics.update({
            "frontend_ms": (frontend_done - started) * 1000,
            "neural_ms": (finished - frontend_done) * 1000,
        })
        return pcm, metrics, captured
