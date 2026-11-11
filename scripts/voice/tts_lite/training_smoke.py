#!/usr/bin/env python3
"""Check a prepared LAMI batch through upstream training forward/backward.

This does not optimize weights, run a GAN trainer or produce a learned voice.
"""
import argparse
import json
import time
from pathlib import Path

import soundfile
import torch

from melo_japanese import JapaneseMelo


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, required=True)
    ap.add_argument("--model-dir", type=Path, required=True)
    ap.add_argument("--prepared-data", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    engine = JapaneseMelo(args.source, args.model_dir, frontend="openjtalk")
    from melo.mel_processing import spectrogram_torch
    from melo.losses import kl_loss
    config = json.loads((args.prepared_data / "config.prepared.json").read_text())
    filename, speaker, language, normalized, phones, tones, word2ph = (args.prepared_data / "train.list").read_text().splitlines()[0].split("|")
    phone, tone, languages = engine.to_ids(phones.split(), list(map(int, tones.split())), language, engine.symbol_to_id)
    if engine.config["data"]["add_blank"]:
        phone = engine.commons.intersperse(phone, 0)
        tone = engine.commons.intersperse(tone, 0)
        languages = engine.commons.intersperse(languages, 0)
    placeholder = torch.load(Path(filename).with_suffix(".bert.pt"), weights_only=True)
    if placeholder.shape != (768, len(phone)) or torch.count_nonzero(placeholder):
        raise ValueError("Invalid BERT-disabled feature placeholder")
    waveform, rate = soundfile.read(filename, dtype="float32")
    if rate != engine.sample_rate:
        raise ValueError("Prepared audio sample rate mismatch")
    audio = torch.tensor(waveform).unsqueeze(0)
    cfg = config["data"]
    spec = spectrogram_torch(audio, cfg["filter_length"], rate, cfg["hop_length"], cfg["win_length"], center=False)
    model = engine.model.train()
    torch.manual_seed(42)
    started = time.perf_counter()
    result = model(torch.tensor([phone]), torch.tensor([len(phone)]), spec, torch.tensor([spec.shape[-1]]),
                   torch.tensor([0]), torch.tensor([tone]), torch.tensor([languages]),
                   torch.zeros(1, 1024, len(phone)), torch.zeros(1, 768, len(phone)))
    generated, duration_loss, _, offset, _, mask, latent, _ = result
    start = int(offset[0]) * cfg["hop_length"]
    target = audio[:, start:start + generated.shape[-1]].unsqueeze(1)
    if target.shape != generated.shape:
        raise ValueError("Training segment alignment mismatch")
    _, z_p, m_p, logs_p, _, logs_q = latent
    reconstruction = torch.mean(torch.abs(generated - target))
    kl = kl_loss(z_p, logs_q, m_p, logs_p, mask)
    duration = duration_loss.sum()
    # A finite plumbing objective, deliberately not the full adversarial loss.
    loss = reconstruction + 0.01 * duration + 0.001 * kl
    if not torch.isfinite(loss):
        raise ValueError("Non-finite smoke objective")
    loss.backward()
    gradients = [(name, p.grad) for name, p in model.named_parameters() if p.grad is not None]
    if not gradients or any(not torch.isfinite(g).all() for _, g in gradients):
        raise ValueError("Missing or non-finite training gradients")
    report = {"schema_version": 1, "scope": "cpu_upstream_generator_training_forward_backward_smoke",
              "provenance": engine.provenance, "voice_data_id": Path(filename).stem,
              "phone_ids": len(phone), "spectrogram_shape": list(spec.shape),
              "segment_shape": list(generated.shape),
              "reconstruction_l1": float(reconstruction.detach()), "duration_loss": float(duration.detach()),
              "kl_loss": float(kl.detach()), "finite_gradient_tensors": len(gradients),
              "elapsed_ms": (time.perf_counter() - started) * 1000,
              "optimizer_steps": 0, "checkpoint_written": False,
              "notes": ["Uses one example from the prepared training split, not the validation split.",
                        "Runs original MeloTTS training forward, MAS alignment and backward on CPU.",
                        "No optimizer step or discriminator training is performed.",
                        "This checks data/gradient compatibility; it does not validate a learned voice or GPU training."]}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
