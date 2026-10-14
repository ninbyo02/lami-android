"""Read-only artifact checks. Never certifies NPU execution or speech quality."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def inspect(report):
    result = {"source": str(report), "errors": [], "artifacts": []}
    try:
        data = json.loads(report.read_text())
        if not isinstance(data, dict):
            raise ValueError("Report must be an object")
    except (OSError, ValueError) as exc:
        result["errors"].append(str(exc))
        return result
    result["recorded_status"] = data.get("status", "unspecified")
    for key in ("artifact", "context"):
        if key not in data:
            continue
        value = data[key]
        digest = data.get(key + "_sha256")
        row = {"kind": key, "path": value, "integrity": "unknown"}
        result["artifacts"].append(row)
        if not isinstance(value, str) or not Path(value).is_absolute():
            result["errors"].append(key + ": absolute path required")
            continue
        path = Path(value)
        if not path.is_file():
            row["integrity"] = "missing"
            result["errors"].append(key + ": missing file")
            continue
        expected_size = data.get(key + "_bytes")
        row["bytes"] = path.stat().st_size
        if expected_size is not None and (
            type(expected_size) is not int or row["bytes"] != expected_size
        ):
            result["errors"].append(key + ": size mismatch or invalid size")
        if digest is None:
            row["integrity"] = "unverified_no_expected_hash"
            continue
        if not isinstance(digest, str) or not re.fullmatch("[0-9a-fA-F]{64}", digest):
            result["errors"].append(key + ": invalid expected SHA256")
            continue
        h = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1048576), b""):
                h.update(chunk)
        row["sha256"] = h.hexdigest()
        row["integrity"] = "match" if h.hexdigest() == digest.lower() else "mismatch"
        if row["integrity"] == "mismatch":
            result["errors"].append(key + ": hash mismatch")
    result["limitations"] = data.get("limitations", [])
    # Existing synthetic rollout evidence is summarized without promoting its scope.
    steps = data.get("steps")
    if isinstance(steps, list):
        result["recorded_rollout_steps"] = len(steps)
        result["recorded_argmax_mismatch_positions"] = [
            s.get("position") for s in steps if isinstance(s, dict)
            and s.get("npu_cpu_argmax_mismatch_heads")
        ]
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("reports", type=Path, nargs="+")
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    rows = [inspect(f) for f in a.reports]
    out = {
        "schema_version": 1,
        "execution": "host_report_and_artifact_check_only",
        "host_preflight": "failed" if any(r["errors"] for r in rows) else "completed",
        "soc_compile": "not_run",
        "npu_execution": "not_run",
        "continuous_speech": "not_run",
        "reports": rows,
        "limitations": [
            "Recorded status is historical evidence, not a current execution result.",
            "Hash parity proves file identity only.",
            "No Android driver, NPU timing, power or SIGABRT emulation.",
        ],
    }
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 1 if out["host_preflight"] == "failed" else 0


if __name__ == "__main__":
    raise SystemExit(main())
