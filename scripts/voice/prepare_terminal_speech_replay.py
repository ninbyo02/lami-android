"""Export validated failure/cancellation durations without inventing PCM."""
import argparse
import json
from pathlib import Path
from validate_speech_trace import validate


def prepare(sources, output):
    rows = []
    seen = set()
    for source in sources:
        verified = validate(source)
        data = json.loads(source.read_text())
        if data["status"] != "failure":
            raise ValueError("Expected terminal failure/cancellation")
        if data["request_id"] in seen:
            raise ValueError("Duplicate request ID")
        seen.add(data["request_id"])
        rows.append({"id": data["request_id"], "delay_ms": verified["recorded_duration_ms"],
                     "stage": data["failure_stage"], "source_sha256": verified["trace_sha256"]})
    if not rows:
        raise ValueError("No terminal records")
    output.mkdir(parents=True, exist_ok=False)
    (output / "terminal.tsv").write_text("".join(
        f'{r["id"]}\t{r["delay_ms"]}\t{r["stage"]}\n' for r in rows))
    (output / "manifest.json").write_text(json.dumps({
        "rows": rows, "scope": "recorded_terminal_event_injection",
        "npu_execution": "not_run", "sigabrt_reproduction": "not_run"}, indent=2)+"\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("traces", nargs="+", type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    prepare(args.traces, args.output_dir)
