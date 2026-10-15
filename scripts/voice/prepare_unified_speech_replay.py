"""Build one replay fixture from one validated successful request."""
import argparse
import hashlib
import json
from pathlib import Path
from validate_speech_trace import validate


def prepare(source, output):
    verified = validate(source)
    data = json.loads(source.read_text())
    if data["status"] != "success":
        raise ValueError("PCM replay requires a successful request")
    ready = next(event["elapsed_ms"] for event in data["events"] if event["stage"] == "pcm_ready")
    raw = (source.parent / "output.f32").read_bytes()
    # Check the bytes we will actually copy, rather than relying on the earlier read.
    if hashlib.sha256(raw).hexdigest() != data["pcm"]["sha256"]:
        raise ValueError("PCM changed during fixture preparation")
    output.mkdir(parents=True, exist_ok=False)
    (output / "output.f32").write_bytes(raw)
    (output / "unified.tsv").write_text(
        f'{data["request_id"]}\t{ready}\toutput.f32\t{data["pcm"]["sha256"]}\t{data["pcm"]["samples"]}\n')
    manifest = {"source": str(source), "source_sha256": verified["trace_sha256"],
                "request_id": data["request_id"], "prepare_delay_ms": ready,
                "input_sha256": data["input_sha256"], "model_sha256": data["model_sha256"],
                "runtime_sha256": data["runtime_sha256"], "pcm_sha256": data["pcm"]["sha256"],
                "scope": "same_request_time_and_pcm_virtual_replay",
                "npu_execution": "not_run", "audio_output": "not_run"}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.trace, args.output_dir), indent=2))
