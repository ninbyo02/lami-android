"""Verify historical WAV identity and export PCM16 fixtures; does not play audio."""
import argparse
import hashlib
import json
import wave
from pathlib import Path


def read_verified(report, wav):
    data = json.loads(report.read_text())
    raw = wav.read_bytes()
    if data.get("status") != "complete" or data.get("eos_reached") is not True:
        raise ValueError("Complete natural-EOS generation required")
    if hashlib.sha256(raw).hexdigest() != data.get("wav_sha256"):
        raise ValueError("Recorded WAV SHA256 mismatch")
    with wave.open(str(wav)) as stream:
        if (stream.getnchannels(), stream.getsampwidth(), stream.getframerate(), stream.getcomptype()) != (1, 2, 24000, "NONE"):
            raise ValueError("Expected mono PCM16 24kHz")
        samples = stream.getnframes()
        pcm = stream.readframes(samples)
    if samples != data.get("audio_samples") or len(pcm) != samples * 2:
        raise ValueError("Sample count mismatch")
    return pcm, samples


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pair", nargs=3, action="append", metavar=("ID", "REPORT", "WAV"), required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    rows = []
    ids = set()
    verified = []
    for identity, report_path, wav_path in args.pair:
        if not identity or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-_" for c in identity) or identity in ids:
            raise ValueError("Unique safe identifier required")
        ids.add(identity)
        report, wav = Path(report_path), Path(wav_path)
        pcm, samples = read_verified(report, wav)
        verified.append((identity, report, wav, pcm, samples))
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for identity, report, wav, pcm, samples in verified:
        target = args.output_dir / (identity + ".pcm")
        target.write_bytes(pcm)
        rows.append({"id": identity, "pcm": target.name, "pcm_sha256": hashlib.sha256(pcm).hexdigest(),
                     "samples": samples, "sample_rate": 24000,
                     "report": str(report), "report_sha256": hashlib.sha256(report.read_bytes()).hexdigest(),
                     "wav": str(wav), "wav_sha256": hashlib.sha256(wav.read_bytes()).hexdigest()})
    (args.output_dir / "pcm.tsv").write_text("".join(
        f'{r["id"]}\t{r["pcm"]}\t{r["pcm_sha256"]}\t{r["samples"]}\t{r["sample_rate"]}\n' for r in rows))
    manifest = {"rows": rows, "current_npu_execution": "not_run",
                "scope": "historical_wav_to_verified_pcm16_fixture", "audio_output": "not_run"}
    (args.output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
