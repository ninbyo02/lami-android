#!/usr/bin/env python3
"""Bounded single-GPU LAMI trial using original Melo networks and GAN losses.

Adapted from pinned MeloTTS melo/train.py; see THIRD_PARTY_NOTICES.md.
This wrapper consumes already prepared JP data and never changes source assets.
"""
import argparse
import contextlib
import hashlib
import json
import random
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import soundfile
import torch
from torch.nn import functional as F
from torch.nn.utils.rnn import pad_sequence

SOURCE_REVISION = "209145371cff8fc3bd60d7be902ea69cbdb7965a"
MANIFEST_SHA256 = "52c1fb660d58c2f4d9b7b881c3b55c505db019929cc22fa24c4c7917335aa2b0"
BASE_CHECKPOINT_SHA256 = "96ae783e6ec0177aa810e2a645aec5d136a6f4992fdea26ee92b7b04d9688ad0"


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def save_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def atomic_save(data, path):
    temporary = path.with_suffix(path.suffix + ".tmp")
    torch.save(data, temporary)
    temporary.replace(path)


class PreparedDataset(torch.utils.data.Dataset):
    """Read seven-field Melo lists with pinned symbols, no online G2P/BERT."""
    def __init__(self, path, config):
        from melo.text import cleaned_text_to_sequence
        from melo import commons
        self.config = config
        self.rows = []
        symbols = {symbol: i for i, symbol in enumerate(config["symbols"])}
        for line in path.read_text().splitlines():
            filename, speaker, language, text, phones, tones, word2ph = line.split("|")
            if speaker != "lami_cute" or language != "JP":
                raise ValueError("Unexpected training speaker or language")
            phones = phones.split()
            tones = list(map(int, tones.split()))
            if len(phones) != len(tones) or sum(map(int, word2ph.split())) != len(phones):
                raise ValueError("Invalid prepared phone metadata")
            phone, tone, languages = cleaned_text_to_sequence(phones, tones, language, symbols)
            if config["data"]["add_blank"]:
                phone, tone, languages = [commons.intersperse(values, 0) for values in (phone, tone, languages)]
            self.rows.append({"path": Path(filename), "text": text, "phone": torch.tensor(phone),
                              "tone": torch.tensor(tone), "language": torch.tensor(languages)})
        self.cache = {}

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, index):
        from melo.mel_processing import spectrogram_torch
        row = self.rows[index]
        if index not in self.cache:
            pcm, rate = soundfile.read(row["path"], dtype="float32")
            cfg = self.config["data"]
            if rate != cfg["sampling_rate"] or pcm.ndim != 1 or not np.isfinite(pcm).all():
                raise ValueError("Invalid prepared waveform")
            audio = torch.from_numpy(pcm).unsqueeze(0)
            spec = spectrogram_torch(audio, cfg["filter_length"], rate,
                                     cfg["hop_length"], cfg["win_length"], center=False)[0]
            self.cache[index] = (spec, audio)
        spec, audio = self.cache[index]
        return row["phone"], spec, audio, row["tone"], row["language"]


def collate(rows):
    # Same tensors as upstream TextAudioSpeakerCollate; use standard padding.
    rows = sorted(rows, key=lambda row: row[1].shape[-1], reverse=True)
    phone = pad_sequence([r[0] for r in rows], batch_first=True)
    spec = pad_sequence([r[1].T for r in rows], batch_first=True).transpose(1, 2)
    audio = pad_sequence([r[2].T for r in rows], batch_first=True).transpose(1, 2)
    return (phone, torch.tensor([r[0].numel() for r in rows]), spec,
            torch.tensor([r[1].shape[-1] for r in rows]), audio,
            pad_sequence([r[3] for r in rows], batch_first=True),
            pad_sequence([r[4] for r in rows], batch_first=True))


def unpack(batch, device):
    phone, lengths, spec, spec_lengths, audio, tone, language = [x.to(device) for x in batch]
    batch_size, width = phone.shape
    inputs = (phone, lengths, spec, spec_lengths, torch.zeros(batch_size, device=device, dtype=torch.long),
              tone, language, torch.zeros(batch_size, 1024, width, device=device),
              torch.zeros(batch_size, 768, width, device=device))
    return inputs, audio


def synthesis_terms(model, batch, config, device):
    from melo import commons
    from melo.losses import kl_loss
    from melo.mel_processing import mel_spectrogram_torch, spec_to_mel_torch
    inputs, audio = unpack(batch, device)
    generated, duration, _, offsets, x_mask, z_mask, latent, durations = model(*inputs)
    cfg = config["data"]
    train = config["train"]
    mel = spec_to_mel_torch(inputs[2], cfg["filter_length"], cfg["n_mel_channels"],
                            cfg["sampling_rate"], cfg["mel_fmin"], cfg["mel_fmax"])
    target_mel = commons.slice_segments(mel, offsets, train["segment_size"] // cfg["hop_length"])
    predicted_mel = mel_spectrogram_torch(generated.squeeze(1), cfg["filter_length"], cfg["n_mel_channels"],
                                         cfg["sampling_rate"], cfg["hop_length"], cfg["win_length"],
                                         cfg["mel_fmin"], cfg["mel_fmax"])
    target_audio = commons.slice_segments(audio, offsets * cfg["hop_length"], train["segment_size"])
    _, z_p, m_p, logs_p, _, logs_q = latent
    losses = {"mel_l1": F.l1_loss(target_mel, predicted_mel),
              "duration": duration.sum(), "kl": kl_loss(z_p, logs_q, m_p, logs_p, z_mask)}
    return generated, target_audio, x_mask, durations, losses


def finite_step(loss, model, optimizer):
    if not torch.isfinite(loss):
        raise ValueError("Non-finite training loss")
    optimizer.zero_grad(set_to_none=True)
    loss.backward()
    norm = torch.nn.utils.clip_grad_norm_(model.parameters(), float("inf"), error_if_nonfinite=True)
    if not torch.isfinite(norm) or norm == 0:
        raise ValueError("Missing or non-finite training gradient")
    optimizer.step()
    return float(norm)


@contextlib.contextmanager
def fixed_rng(seed):
    with torch.random.fork_rng(devices=[torch.cuda.current_device()]):
        torch.manual_seed(seed)
        yield


def evaluate(model, dataset, config, device, output, step):
    output.mkdir(parents=True, exist_ok=False)
    model.eval()
    rows = []
    # Upstream mel transforms cache windows/bases for subsequent training.
    # no_grad keeps those reusable tensors compatible with autograd.
    with fixed_rng(42), torch.no_grad():
        for index, row in enumerate(dataset.rows):
            batch = collate([dataset[index]])
            _, _, _, _, terms = synthesis_terms(model, batch, config, device)
            inputs, _ = unpack(batch, device)
            pcm = model.infer(inputs[0], inputs[1], inputs[4], *inputs[5:],
                              sdp_ratio=0.2, noise_scale=0.6, noise_scale_w=0.8, length_scale=1.0)[0][0, 0].cpu().numpy()
            if not np.isfinite(pcm).all() or len(pcm) == 0:
                raise ValueError("Non-finite validation audio")
            wav = output / (row["path"].stem + ".wav")
            soundfile.write(wav, pcm, config["data"]["sampling_rate"], subtype="PCM_16")
            rows.append({"id": row["path"].stem, "normalized_text": row["text"],
                         "reference": str(row["path"]), "wav": wav.name, "sha256": sha256(wav),
                         "duration_seconds": len(pcm) / config["data"]["sampling_rate"],
                         "peak": float(np.abs(pcm).max()), "rms": float(np.sqrt(np.mean(pcm ** 2))),
                         **{key: float(value) for key, value in terms.items()}})
    summary = {"step": step, "held_out_examples": len(rows),
               "mean": {key: float(np.mean([r[key] for r in rows])) for key in ("mel_l1", "duration", "kl")},
               "listening": "pending", "notes": ["Teacher-forced losses do not establish voice similarity or intelligibility.",
                                                        "Generated audio uses the held-out text with no reference audio conditioning."]}
    save_json(output / "report.json", summary)
    (output / "requests.jsonl").write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows))
    print(json.dumps({"validation": summary}), flush=True)
    model.train()
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "model-dir", "prepared-data", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    initialization = parser.add_mutually_exclusive_group(required=True)
    initialization.add_argument("--discriminators", type=Path)
    initialization.add_argument("--fresh-discriminators", action="store_true")
    parser.add_argument("--steps", type=int, default=200)
    parser.add_argument("--max-minutes", type=float, default=20)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--learning-rate", type=float, default=1e-5)
    parser.add_argument("--discriminator-learning-rate", type=float, default=2e-4)
    parser.add_argument("--save-every", type=int, default=100)
    parser.add_argument("--freeze-text-duration", action="store_true",
                        help="Preserve text encoder, duration predictors and speaker embedding; adapt acoustic/decoder modules")
    args = parser.parse_args()
    if min(args.steps, args.max_minutes, args.batch_size, args.learning_rate, args.discriminator_learning_rate, args.save_every) <= 0:
        parser.error("Expected positive training limits")
    if not torch.cuda.is_available():
        parser.error("This trial requires a working GPU; CPU baseline environment is separate")
    revision = subprocess.check_output(["git", "-C", str(args.source), "rev-parse", "HEAD"], text=True).strip()
    dirty = subprocess.check_output(["git", "-C", str(args.source), "status", "--porcelain"], text=True).strip()
    if revision != SOURCE_REVISION or dirty or sha256(args.model_dir / "checkpoint.pth") != BASE_CHECKPOINT_SHA256:
        raise ValueError("Pinned source/base checkpoint mismatch")
    report = json.loads((args.prepared_data / "report.json").read_text())
    if report["source_manifest_sha256"] != MANIFEST_SHA256 or (report["train"], report["validation"]) != (480, 20):
        raise ValueError("Unexpected finalized data")
    for split, key in (("train", "train_list_sha256"), ("validation", "validation_list_sha256")):
        if sha256(args.prepared_data / (split + ".list")) != report[key]:
            raise ValueError("Prepared split changed")
    for row in map(json.loads, (args.prepared_data / "prepared.jsonl").read_text().splitlines()):
        if sha256(args.prepared_data / "audio" / (row["id"] + ".wav")) != row["converted_sha256"]:
            raise ValueError("Prepared WAV changed")
    sys.path.insert(0, str(args.source.resolve()))
    from melo.models import SynthesizerTrn, MultiPeriodDiscriminator, DurationDiscriminator
    from melo.losses import discriminator_loss, generator_loss, feature_loss
    torch.set_num_threads(4)
    torch.set_num_interop_threads(1)
    torch.manual_seed(42)
    random.seed(42)
    np.random.seed(42)
    device = torch.device("cuda:0")
    torch.cuda.set_device(device)
    config = json.loads((args.prepared_data / "config.prepared.json").read_text())
    base_config = json.loads((args.model_dir / "config.json").read_text())
    if config["model"] != base_config["model"] or config["symbols"] != base_config["symbols"]:
        raise ValueError("Prepared model architecture changed")
    cfg, train = config["data"], config["train"]
    train["learning_rate"], train["batch_size"] = args.learning_rate, args.batch_size
    args.output.mkdir(parents=True, exist_ok=False)
    save_json(args.output / "config.training.json", config)
    model = SynthesizerTrn(len(config["symbols"]), cfg["filter_length"] // 2 + 1,
                           train["segment_size"] // cfg["hop_length"], n_speakers=cfg["n_speakers"],
                           num_tones=config["num_tones"], num_languages=config["num_languages"], **config["model"])
    model.load_state_dict(torch.load(args.model_dir / "checkpoint.pth", map_location="cpu", weights_only=True)["model"], strict=True)
    frozen_modules = ("enc_p", "dp", "sdp", "emb_g") if args.freeze_text_duration else ()
    for name in frozen_modules:
        getattr(model, name).requires_grad_(False)
    disc = MultiPeriodDiscriminator(config["model"]["use_spectral_norm"])
    duration_disc = DurationDiscriminator(config["model"]["hidden_channels"], config["model"]["hidden_channels"],
                                           3, 0.1, gin_channels=config["model"]["gin_channels"])
    assets = {"initialization": "fresh_upstream_default_seed42", "pretrained_weights": False}
    if args.discriminators:
        assets = json.loads((args.discriminators / "assets.lock.json").read_text())
        for name, net in (("D.pth", disc), ("DUR.pth", duration_disc)):
            if sha256(args.discriminators / name) != assets[name]["sha256"]:
                raise ValueError("Discriminator hash mismatch")
            state = torch.load(args.discriminators / name, map_location="cpu", weights_only=True)["model"]
            net.load_state_dict(state, strict=True)
    nets = [net.to(device).train() for net in (model, disc, duration_disc)]
    optimizers = [torch.optim.AdamW((p for p in net.parameters() if p.requires_grad), lr, betas=train["betas"], eps=train["eps"])
                  for net, lr in zip(nets, (args.learning_rate, args.discriminator_learning_rate, args.discriminator_learning_rate))]
    optim_g, optim_d, optim_dur = optimizers
    training = PreparedDataset(args.prepared_data / "train.list", config)
    validation = PreparedDataset(args.prepared_data / "validation.list", config)
    loader = torch.utils.data.DataLoader(training, batch_size=args.batch_size, shuffle=True, num_workers=0,
                                         collate_fn=collate, generator=torch.Generator().manual_seed(42))
    summary = {"schema_version": 1, "scope": "bounded_gpu_full_gan_finetuning_trial_not_adopted_voice",
               "source_revision": revision, "base_checkpoint_sha256": BASE_CHECKPOINT_SHA256,
               "source_manifest_sha256": MANIFEST_SHA256, "prepared_train_sha256": report["train_list_sha256"],
               "prepared_validation_sha256": report["validation_list_sha256"], "discriminators": assets,
               "torch": torch.__version__, "hip": torch.version.hip, "device": torch.cuda.get_device_name(0),
               "requested_steps": args.steps, "max_minutes": args.max_minutes, "batch_size": args.batch_size,
               "learning_rate": args.learning_rate, "discriminator_learning_rate": args.discriminator_learning_rate,
               "precision": "float32", "seed": 42,
               "train_examples": len(training), "validation_examples": len(validation),
               "frozen_modules": list(frozen_modules),
               "full_gan_losses": ["mel", "duration", "kl", "adversarial", "feature_matching", "duration_adversarial"],
               "notes": ["Uses unmodified upstream networks, alignment and losses; bounded loop adapts train.py.",
                         "CPU data spectra are cached in memory; no original dataset files are written.",
                         "No phone execution, NPU timing or subjective quality adoption is performed."]}
    summary["baseline_validation"] = evaluate(model, validation, config, device, args.output / "validation-step000000", 0)
    save_json(args.output / "run.json", summary)
    started = time.monotonic()
    step, epoch = 0, 0
    completed_ids = set()
    def save_candidate():
        target = args.output / ("candidate-step" + str(step).zfill(6))
        target.mkdir()
        save_json(target / "config.json", base_config)
        metadata = {"voice": "lami_cute_v3_03_trial_not_adopted", "optimizer_steps": step,
                    "source_manifest_sha256": MANIFEST_SHA256, "base_checkpoint_sha256": BASE_CHECKPOINT_SHA256,
                    "frozen_modules": list(frozen_modules)}
        atomic_save({"model": {key: value.detach().cpu() for key, value in model.state_dict().items()},
                     "tts_lite": metadata}, target / "checkpoint.pth")
        return target
    while step < args.steps and time.monotonic() - started < args.max_minutes * 60:
        epoch += 1
        for batch in loader:
            if step >= args.steps or time.monotonic() - started >= args.max_minutes * 60:
                break
            tick = time.monotonic()
            for name in frozen_modules:
                getattr(model, name).eval()
            if model.use_noise_scaled_mas:
                model.current_mas_noise_scale = max(model.mas_noise_scale_initial - model.noise_scale_delta * step, 0.0)
            generated, real, x_mask, durations, losses = synthesis_terms(model, batch, config, device)
            hidden, logw, logw_hat = durations
            real_score, fake_score, _, _ = disc(real, generated.detach())
            loss_d = discriminator_loss(real_score, fake_score)[0]
            norm_d = finite_step(loss_d, disc, optim_d)
            real_dur, fake_dur = duration_disc(hidden.detach(), x_mask.detach(), logw.detach(), logw_hat.detach())
            loss_dd = discriminator_loss(real_dur, fake_dur)[0]
            norm_dd = finite_step(loss_dd, duration_disc, optim_dur)
            for net in (disc, duration_disc):
                net.requires_grad_(False)
            _, fake_score, real_features, fake_features = disc(real, generated)
            losses["adversarial"] = generator_loss(fake_score)[0]
            losses["feature_matching"] = feature_loss(real_features, fake_features)
            losses["duration_adversarial"] = generator_loss(duration_disc(hidden, x_mask, logw, logw_hat)[1])[0]
            loss_g = (losses["mel_l1"] * train["c_mel"] + losses["kl"] * train["c_kl"] + losses["duration"]
                      + losses["adversarial"] + losses["feature_matching"] + losses["duration_adversarial"])
            norm_g = finite_step(loss_g, model, optim_g)
            for net in (disc, duration_disc):
                net.requires_grad_(True)
            step += 1
            torch.cuda.synchronize()
            metrics = {"step": step, "epoch": epoch, "loss_g": float(loss_g.detach()), "loss_d": float(loss_d.detach()),
                       "loss_duration_d": float(loss_dd.detach()), "grad_norm_g": norm_g, "grad_norm_d": norm_d,
                       "grad_norm_duration_d": norm_dd, "step_ms": (time.monotonic() - tick) * 1000,
                       **{key: float(value.detach()) for key, value in losses.items()}}
            with (args.output / "steps.jsonl").open("a") as stream:
                stream.write(json.dumps(metrics) + "\n")
            if step == 1 or step % 10 == 0:
                print(json.dumps(metrics), flush=True)
            if step % args.save_every == 0:
                save_candidate()
                completed_ids.add(step)
        if step < args.steps:
            for optimizer in optimizers:
                for group in optimizer.param_groups:
                    group["lr"] *= train["lr_decay"]
    if step == 0:
        raise RuntimeError("No optimizer steps completed")
    candidate = args.output / ("candidate-step" + str(step).zfill(6))
    if step not in completed_ids:
        candidate = save_candidate()
    summary.update(optimizer_steps=step, epochs_entered=epoch, elapsed_training_seconds=time.monotonic() - started,
                   termination="step_limit" if step == args.steps else "time_limit",
                   peak_gpu_allocated_bytes=torch.cuda.max_memory_allocated(),
                   candidate=str(candidate), candidate_sha256=sha256(candidate / "checkpoint.pth"))
    summary["final_validation"] = evaluate(model, validation, config, device, args.output / ("validation-step" + str(step).zfill(6)), step)
    atomic_save({"step": step, "models": [net.state_dict() for net in nets],
                 "optimizers": [optimizer.state_dict() for optimizer in optimizers],
                 "torch_rng": torch.get_rng_state(), "cuda_rng": torch.cuda.get_rng_state()}, args.output / "training-state.pth")
    save_json(args.output / "run.json", summary)
    print(json.dumps(summary), flush=True)


if __name__ == "__main__":
    main()
