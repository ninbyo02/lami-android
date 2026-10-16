"""Terminate only owned test children, then inspect their incomplete capture."""
import os
import signal
import subprocess
import time
from pathlib import Path
from validate_speech_trace import inspect_incomplete


def probe(classpath, directory):
    rows = []
    for name, sig in (("SIGKILL", signal.SIGKILL), ("SIGABRT", signal.SIGABRT)):
        root = Path(directory) / name
        root.mkdir()
        process = subprocess.Popen(
            ["java", "-XX:-CreateCoredumpOnCrash", "-XX:ErrorFile=" + str(root / "jvm-error.txt"),
             "-cp", classpath, "io.github.ninbyo02.lami.tts.LamiTraceExitProbe", str(root)],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, cwd=root)
        try:
            deadline = time.monotonic() + 10
            while not (root / "ready").is_file():
                if process.poll() is not None:
                    raise RuntimeError("Probe exited before ready")
                if time.monotonic() > deadline:
                    raise TimeoutError("Probe readiness timed out")
                time.sleep(0.05)
            os.kill(process.pid, sig)
            process.communicate(timeout=10)
            result = inspect_incomplete(root / "capture")
            passed = (process.returncode in (-int(sig), 128 + int(sig))
                      and not (root / "capture/trace.json").exists()
                      and result["status"] == "incomplete"
                      and result["last_recorded_stage"] == "synthesis_started"
                      and result["last_recorded_elapsed_ms"] == 25)
            rows.append({"signal": name, "child_exit_code": process.returncode,
                         "result": "passed" if passed else "failed", "capture": result})
        finally:
            if process.poll() is None:
                process.kill()
                process.communicate(timeout=10)
    return {"status": "passed" if all(row["result"] == "passed" for row in rows) else "failed",
            "cases": rows, "scope": "host_child_process_death_journal_survival",
            "npu_crash_reproduction": "not_run",
            "limits": ["Owned Linux JVM test children only; no Android driver or NPU is loaded.",
                       "Does not test power loss, storage failure, or the actual LAMI SIGABRT cause."]}
