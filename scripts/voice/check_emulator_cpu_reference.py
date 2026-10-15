"""Run fresh real host-CPU requests through the debug Android app, on an emulator only."""
import argparse
import hashlib
import json
import shlex
import subprocess
import time
import uuid
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, default=Path("/home/sato/Android/Sdk/platform-tools/adb"))
    parser.add_argument("--serial", default="emulator-5580")
    parser.add_argument("--package", default="io.github.ninbyo02.lami.assistantprobe")
    parser.add_argument("--model", default="sam860/LFM2:350m")
    parser.add_argument("--prompt", action="append")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("This runner only targets an explicit emulator serial")
    package = args.package
    def shell(*parts):
        return subprocess.run([str(args.adb), "-s", args.serial, "shell", shlex.join(parts)],
                              capture_output=True, text=True, timeout=20)
    report = {"validation": "failed", "serial": args.serial, "model": args.model,
              "scope": "android_emulator_to_host_cpu_real_inference",
              "npu_execution": "not_run", "npu_numerical_parity": "not_verified", "cases": []}
    try:
        device = shell("getprop", "ro.kernel.qemu")
        if device.returncode or device.stdout.strip() != "1":
            raise ValueError("Target is not a verified Android emulator")
        for prompt in args.prompt or ["What is 17 plus 25? Reply briefly.", "Name two colors. Reply briefly."]:
            run_id = str(uuid.uuid4())
            shell("am", "force-stop", package).check_returncode()
            launch = shell("am", "start", "-W", "-n", package + "/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity",
                           "--es", "model", args.model, "--es", "prompt", prompt,
                           "--es", "run_id", run_id, "--ez", "auto_run", "true")
            if launch.returncode or "Error" in launch.stdout or "Error" in launch.stderr:
                raise ValueError("Activity launch failed: " + launch.stdout + launch.stderr)
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                result = shell("run-as", package, "cat", "files/emulator_cpu_reference.json")
                try:
                    record = json.loads(result.stdout)
                except (ValueError, TypeError):
                    record = {}
                if record.get("run_id") == run_id and record.get("status") in ("success", "failed", "cancelled"):
                    break
                time.sleep(0.5)
            else:
                raise TimeoutError("No fresh terminal inference report")
            report["cases"].append(record)
            if record.get("status") != "success":
                raise ValueError("Inference failed: " + str(record.get("error")))
            if (record.get("input_sha256") != hashlib.sha256(prompt.encode()).hexdigest()
                    or record.get("requested_num_gpu") != 0 or not record.get("response")):
                raise ValueError("Request/result identity or CPU configuration mismatch")
        report["validation"] = "passed"
    except (OSError, ValueError, subprocess.SubprocessError, TimeoutError) as error:
        report["error"] = str(error)
    finally:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["validation"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
