"""Run offline host checks and always emit a scoped result JSON."""
import argparse
import datetime
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path


def run_step(command, cwd, timeout):
    try:
        result = subprocess.run(command, cwd=cwd, capture_output=True, text=True, timeout=timeout)
        return {"status": "passed" if result.returncode == 0 else "failed",
                "exit_code": result.returncode, "output": result.stdout + result.stderr}
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"status": "failed", "error": str(error)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cache", type=Path)
    parser.add_argument("--replay-fixture", type=Path)
    parser.add_argument("--pcm-fixture", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    replay = args.replay_fixture or root / "docs/reviews/npu-host-replay-20261015/codec.tsv"
    pcm = args.pcm_fixture or root / "docs/reviews/npu-host-pcm-20261015/pcm.tsv"
    report = {
        "schema_version": 1, "executed_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "host_suite": "failed", "steps": {}, "fixtures": [],
        "npu_execution": "not_run", "soc_compile": "not_run",
        "android_audio": "not_run", "sigabrt_reproduction": "not_run",
        "limitations": [
            "Historical timing and audio fixtures; no new NPU inference.",
            "Queue behavior uses virtual time and an injected playback consumer.",
            "Passing does not certify NPU compatibility, performance or speech quality.",
        ],
    }
    try:
        for fixture in (replay, pcm):
            if not fixture.is_file():
                raise FileNotFoundError(f"Missing fixture: {fixture}")
            report["fixtures"].append({"path": str(fixture),
                                      "sha256": hashlib.sha256(fixture.read_bytes()).hexdigest()})
        for pattern in ("test_npu_host_preflight.py", "test_speech_replay_fixture.py", "test_pcm_replay_fixture.py", "test_speech_trace_validation.py"):
            report["steps"][pattern] = run_step(
                [sys.executable, "-m", "unittest", "discover", "-s", "scripts/voice", "-p", pattern, "-v"],
                root, 30)
        with tempfile.TemporaryDirectory(prefix="lami-host-suite-") as directory:
            result_file = Path(directory) / "pipeline.json"
            command = [sys.executable, "scripts/voice/run_speech_pipeline_host_tests.py",
                       "--replay-fixture", str(replay.resolve()), "--pcm-fixture", str(pcm.resolve()),
                       "--output", str(result_file), "--capture-export", str(Path(directory) / "captured")]
            if args.cache:
                command += ["--cache", str(args.cache)]
            step = run_step(command, root, 180)
            report["steps"]["actual_speech_pipeline"] = step
            if result_file.is_file():
                detail = json.loads(result_file.read_text())
                step["detail"] = detail
                if detail.get("host_pipeline_tests") != "passed":
                    step["status"] = "failed"
            else:
                step["status"] = "failed"
                step["error"] = "Pipeline result JSON missing"
            report["steps"]["kotlin_capture_python_readback"] = run_step(
                [sys.executable, "scripts/voice/validate_speech_trace.py",
                 str(Path(directory) / "captured/trace.json"), "--output", str(Path(directory) / "readback.json")], root, 30)
            readback = Path(directory) / "readback.json"
            if readback.is_file():
                report["steps"]["kotlin_capture_python_readback"]["detail"] = json.loads(readback.read_text())
            unified = Path(directory) / "unified"
            report["steps"]["prepare_same_request_fixture"] = run_step(
                [sys.executable, "scripts/voice/prepare_unified_speech_replay.py",
                 str(Path(directory) / "captured/trace.json"), "--output-dir", str(unified)], root, 30)
            terminal = Path(directory) / "terminal"
            report["steps"]["prepare_terminal_fixture"] = run_step(
                [sys.executable, "scripts/voice/prepare_terminal_speech_replay.py",
                 str(Path(directory) / "captured-failure/trace.json"),
                 str(Path(directory) / "captured-cancelled/trace.json"),
                 "--output-dir", str(terminal)], root, 30)
            unified_result = Path(directory) / "unified-results.json"
            unified_command = [sys.executable, "scripts/voice/run_speech_pipeline_host_tests.py",
                "--unified-fixture", str(unified / "unified.tsv"),
                "--terminal-fixture", str(terminal / "terminal.tsv"), "--output", str(unified_result)]
            if args.cache:
                unified_command += ["--cache", str(args.cache)]
            report["steps"]["same_request_queue_replay"] = run_step(unified_command, root, 180)
            if unified_result.is_file():
                report["steps"]["same_request_queue_replay"]["detail"] = json.loads(unified_result.read_text())
            else:
                report["steps"]["same_request_queue_replay"]["status"] = "failed"
        if all(step["status"] == "passed" for step in report["steps"].values()):
            report["host_suite"] = "passed"
    except Exception as error:
        report["error"] = f"{type(error).__name__}: {error}"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"host_suite": report["host_suite"], "output": str(args.output),
                      "steps": {key: value["status"] for key, value in report["steps"].items()},
                      "error": report.get("error"), "npu_execution": "not_run"}, indent=2))
    return 0 if report["host_suite"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
