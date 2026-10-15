"""Extract historical device synthesis timing only; never infer missing PCM/provenance."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def prepare(capture, output):
    summary_path = capture / "summary.json"
    summary = json.loads(summary_path.read_text())
    if summary.get("all_outputs_match") is not True or summary.get("restore", {}).get("status") != "restored_and_hash_verified":
        raise ValueError("Capture acceptance/restore evidence missing")
    rows = []
    for request in summary["requests"]:
        path = capture / f'{request["run"]}-{request["variant"]}.txt'
        lines = [line for line in path.read_text().splitlines()
                 if line.startswith(f'request={request["request"]} ')]
        def event(name):
            matches = [line for line in lines if name in line]
            if len(matches) != 1:
                raise ValueError(f"Missing or duplicate event: {name}")
            return int(re.search(r"elapsed_ms=(\d+)", matches[0]).group(1))
        start, end = event("synthesis=started"), event("synthesis=complete")
        playback = event("playback=started")
        if not 0 <= start < end <= playback:
            raise ValueError("Invalid event order")
        rows.append({"id": f'run{request["run"]}-{request["variant"]}-request{request["request"]}',
                     "synthesis_ms": end-start, "frames": request["metrics"]["codec"]["frames"],
                     "request_to_playback_ms": playback-start,
                     "log": str(path), "log_sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    if not rows or len({row["id"] for row in rows}) != len(rows):
        raise ValueError("Empty/duplicate requests")
    output.mkdir(parents=True, exist_ok=False)
    (output / "timing.tsv").write_text("".join(
        f'{row["id"]}\t{row["synthesis_ms"]}\t{row["frames"]}\n' for row in rows))
    manifest = {"scope": "historical_device_serial_synthesis_timing_only",
                "summary_sha256": hashlib.sha256(summary_path.read_bytes()).hexdigest(), "requests": rows,
                "current_device_execution": "not_run", "pcm": "not_imported",
                "model_runtime_identity": "not_verified",
                "limitations": ["Historical CPU diagnostic timing, not NPU inference evidence.",
                                "No corresponding PCM imported; do not combine with other audio captures.",
                                "Virtual-clock queue replay cannot predict new hardware performance."]}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2)+"\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.capture, args.output_dir)
