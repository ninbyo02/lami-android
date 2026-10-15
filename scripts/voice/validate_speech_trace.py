"""Validate a captured request and PCM identity; does not certify hardware or play audio."""
import argparse
import hashlib
import json
import math
import re
import struct
from pathlib import Path


def validate(path):
    if path.stat().st_size > 16384:
        raise ValueError("Trace metadata exceeds bound")
    data = json.loads(path.read_text())
    if not isinstance(data, dict) or type(data.get("schema_version")) is not int or data["schema_version"] != 1:
        raise ValueError("Unsupported trace schema")
    if not isinstance(data.get("request_id"), str) or not re.fullmatch(r"[a-zA-Z0-9_-]{1,80}", data["request_id"]):
        raise ValueError("Invalid request ID")
    for key in ("input_sha256", "model_sha256", "runtime_sha256"):
        if not isinstance(data.get(key), str) or not re.fullmatch("[0-9a-f]{64}", data[key]):
            raise ValueError("Invalid provenance hash")
    events = data.get("events")
    if not isinstance(events, list) or not 2 <= len(events) <= 5:
        raise ValueError("Invalid events")
    stages = [event["stage"] for event in events]
    middle = ["synthesis_started", "pcm_ready", "playback_started"]
    if stages != ["request_started"] + middle[:len(events)-2] + ["request_ended"]:
        raise ValueError("Stage order mismatch")
    times = [event["elapsed_ms"] for event in events]
    if any(type(t) is not int or t < 0 for t in times) or times[0] != 0 or times != sorted(times):
        raise ValueError("Invalid monotonic times")
    status = data.get("status")
    pcm = data.get("pcm")
    result = {"request_id": data["request_id"], "recorded_status": status,
              "recorded_duration_ms": times[-1], "pcm_integrity": "not_present",
              "trace_sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    if status == "failure":
        if data.get("failure_stage") not in ("load", "synthesis", "playback", "cancelled") or pcm is not None:
            raise ValueError("Invalid failure record")
    elif status == "success":
        if data.get("failure_stage") is not None or "pcm_ready" not in stages or not isinstance(pcm, dict):
            raise ValueError("Success requires PCM-ready")
        if pcm.get("path") != "output.f32" or pcm.get("format") != "float32le" or pcm.get("sample_rate") != 24000:
            raise ValueError("PCM contract mismatch")
        samples = pcm.get("samples")
        if type(samples) is not int or not 0 < samples <= 1048576:
            raise ValueError("Invalid sample count")
        file = path.parent / "output.f32"
        if file.is_symlink() or file.stat().st_size != samples * 4:
            raise ValueError("PCM size/path mismatch")
        raw = file.read_bytes()
        if hashlib.sha256(raw).hexdigest() != pcm.get("sha256"):
            raise ValueError("PCM SHA256 mismatch")
        if not all(math.isfinite(value[0]) for value in struct.iter_unpack("<f", raw)):
            raise ValueError("Non-finite PCM")
        result.update(pcm_integrity="verified", samples=samples,
                      audio_ms=samples * 1000 / 24000,
                      playback_start_recorded="playback_started" in stages)
    else:
        raise ValueError("Invalid terminal status")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = {"validation": "failed", "npu_execution": "not_run", "audio_output": "not_run"}
    try:
        report["record"] = validate(args.trace)
        report["validation"] = "passed"
    except (OSError, ValueError, KeyError, TypeError) as error:
        report["error"] = f"{type(error).__name__}: {error}"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 0 if report["validation"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
