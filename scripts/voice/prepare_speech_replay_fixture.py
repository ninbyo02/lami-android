"""Extract recorded codec-stage durations; never infer missing synthesis starts."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def extract(source):
    rows = []
    for run in source["runs"]:
        for line in run["metrics"]:
            match = re.fullmatch(
                r"request=(\d+) metric=codec frames=(\d+) ms=(\d+) elapsed_ms=(\d+)", line)
            if not match:
                continue
            request, frames, duration, elapsed = map(int, match.groups())
            if frames <= 0 or duration <= 0 or duration > elapsed:
                raise ValueError("Invalid recorded codec duration")
            key = run["name"] + ":" + str(request)
            if any(row["id"] == key for row in rows):
                raise ValueError("Duplicate codec metric")
            rows.append({"id": key, "frames": frames, "codec_ms": duration,
                         "recorded_elapsed_ms": elapsed})
    if not rows:
        raise ValueError("No codec-stage timings")
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    rows = extract(json.loads(args.source.read_text()))
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "codec.tsv").write_text(
        "".join(f'{r["id"]}\t{r["codec_ms"]}\t{r["frames"]}\n' for r in rows))
    manifest = {
        "source": str(args.source),
        "source_sha256": hashlib.sha256(args.source.read_bytes()).hexdigest(),
        "timing_scope": "recorded_codec_stage_only",
        "rows": rows,
        "limitations": [
            "Missing synthesis-start events; full synthesis durations are not inferred.",
            "Durations drive virtual-clock tests, not a hardware latency prediction.",
            "Replayed clip identities are placeholders; PCM/audio is not replayed.",
        ],
    }
    (args.output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
