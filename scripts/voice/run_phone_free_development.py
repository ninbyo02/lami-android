"""Validate real CPU answers and Android UI lifecycle without accessing a phone."""
import argparse
import json
import subprocess
import sys
import uuid
from pathlib import Path

from check_cpu_npu_reference import cpu_run, load_case_set, score


def lifecycle_passed(report):
    return (isinstance(report, dict) and report.get("validation") == "passed"
            and report.get("backend") == "host_cpu"
            and report.get("package") == "io.github.ninbyo02.lami.assistantprobe"
            and report.get("room") == "passed" and report.get("recovery_ui") == "passed"
            and report.get("stop_persisted") == "CANCELLED"
            and report.get("restart_launch") == "passed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="/home/sato/Android/Sdk/platform-tools/adb")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    root = args.output_dir / ("phone_free_" + uuid.uuid4().hex)
    root.mkdir(parents=True)
    report = {"validation": "failed", "scope": "phone_free_cpu_development",
              "serial": args.serial, "npu_execution": "not_run",
              "npu_numerical_parity": "not_verified", "evidence_dir": str(root),
              "limitations": ["Requires a PC CPU bridge on port 11436 and an installed debug emulator app.",
                              "Does not validate NPU binaries, drivers, timing, memory or TTS.",
                              "Creates a chat and restarts only the emulator debug app; CPU mode remains enabled."]}
    def save(name, value):
        target = root / name
        temporary = target.with_suffix(target.suffix + ".tmp")
        temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
        temporary.replace(target)
    try:
        if not args.serial.startswith("emulator-"):
            raise ValueError("Explicit emulator serial required; no phone commands were issued")
        device = subprocess.run([args.adb, "-s", args.serial, "shell", "getprop", "ro.kernel.qemu"],
                                capture_output=True, text=True, timeout=20)
        if device.returncode or device.stdout.strip() != "1":
            raise ValueError("Target is not a verified Android emulator")
        scripts = Path(__file__).resolve().parent
        case = scripts.parent / "data/cpu_npu_japanese_history_v1.json"
        prompts, expected = load_case_set(case)
        scenario = root.name
        save("request.json", {"scenarioId": scenario, "prompts": prompts, "expected": expected})
        cpu = cpu_run(11436, "gemma4-e2b-litert-cpu", scenario, prompts)
        save("cpu.json", cpu)
        report["cpu_answers"] = score(cpu, scenario, "HOST_CPU_REFERENCE", prompts, expected)
        # Continue independent UI checks even if the model answers are wrong.
        output = root / "lifecycle.json"
        result = subprocess.run([sys.executable, str(scripts / "check_normal_chat_lifecycle.py"),
                                 "--backend", "host_cpu", "--serial", args.serial,
                                 "--adb", args.adb, "--output", str(output)],
                                capture_output=True, text=True, timeout=480)
        (root / "lifecycle.stdout.txt").write_text(result.stdout)
        (root / "lifecycle.stderr.txt").write_text(result.stderr)
        lifecycle = json.loads(output.read_text())
        report["android_lifecycle"] = "passed" if result.returncode == 0 and lifecycle_passed(lifecycle) else "failed"
        if (report["cpu_answers"]["execution"] == "passed"
                and report["cpu_answers"]["quality"] == "passed"
                and report["android_lifecycle"] == "passed"):
            report["validation"] = "passed"
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        report["error"] = str(error)
    save("summary.json", report)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["validation"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
