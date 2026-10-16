"""Compare fixed CPU/NPU functional prompts without installing APKs or changing model slots."""
import argparse
import base64
import hashlib
import json
import re
import subprocess
import time
import unicodedata
import urllib.request
import uuid
from pathlib import Path

PROMPTS = ["What is 17 plus 25? Reply with the number only.",
           "Now multiply that result by 2. Reply with the number only."]
EXPECTED = ["42", "84"]
SYSTEM = "You are a helpful assistant. Follow the user instructions."


def receiver_acknowledged(stdout):
    return re.search(rb"Broadcast completed:\s+result=244\b", stdout) is not None


def normalize(text):
    return "".join(unicodedata.normalize("NFKC", text).split()).rstrip("。.!！")


def score(run, scenario, backend):
    errors = []
    if not isinstance(run, dict):
        return {"execution": "failed", "quality": "not_evaluated", "errors": ["Missing result object"]}
    if run.get("scenarioId") != scenario or run.get("backend") != backend:
        errors.append("Scenario/backend identity mismatch")
    turns = run.get("turns", [])
    if not isinstance(turns, list) or len(turns) != len(PROMPTS):
        errors.append("Missing or extra turns")
        turns = []
    for index, (turn, prompt) in enumerate(zip(turns, PROMPTS), 1):
        if not isinstance(turn, dict):
            errors.append("Invalid turn object")
            continue
        if turn.get("index") != index or turn.get("prompt") != prompt:
            errors.append("Prompt/order mismatch at turn " + str(index))
        if turn.get("status") != "success" or not isinstance(turn.get("sanitizedOutput"), str) or not turn["sanitizedOutput"].strip():
            errors.append("Failed or empty turn " + str(index))
    if run.get("status") != "success":
        errors.append(str(run.get("reason", "Execution failed")))
    if errors:
        return {"execution": "failed", "quality": "not_evaluated", "errors": errors}
    quality = [normalize(t["sanitizedOutput"]) == expected for t, expected in zip(turns, EXPECTED)]
    return {"execution": "passed", "quality": "passed" if all(quality) else "failed", "turn_quality": quality, "errors": []}


def cpu_run(port, model, scenario):
    messages = [{"role": "system", "content": SYSTEM}]
    run = {"scenarioId": scenario, "backend": "HOST_CPU_REFERENCE", "status": "failure", "turns": []}
    try:
        for index, prompt in enumerate(PROMPTS, 1):
            messages.append({"role": "user", "content": prompt})
            body = {"model": model, "messages": messages, "stream": True, "options": {"num_gpu": 0}}
            request = urllib.request.Request("http://127.0.0.1:" + str(port) + "/api/chat", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
            started = time.monotonic(); chunks = []; terminal = None
            with urllib.request.urlopen(request, timeout=130) as response:
                for line in response:
                    if len(line) > 1048576:
                        raise ValueError("Oversized CPU event")
                    event = json.loads(line)
                    if event.get("error"):
                        raise ValueError(event["error"])
                    chunk = event.get("message", {}).get("content", "")
                    if chunk:
                        chunks.append(chunk)
                    if event.get("done") is True:
                        terminal = event
            identity = (terminal or {}).get("reference_backend", {})
            if identity.get("backend") != "host_litert_cpu" or identity.get("model_alias") != model or not identity.get("model_sha256"):
                raise ValueError("Missing or unexpected CPU backend/model identity")
            text = "".join(chunks)
            if not text.strip():
                raise ValueError("Empty CPU response")
            run["turns"].append({"index": index, "prompt": prompt, "status": "success", "sanitizedOutput": text,
                "sendMs": round((time.monotonic() - started)*1000), "reference_backend": identity,
                "reference_metrics": terminal.get("reference_metrics")})
            messages.append({"role": "assistant", "content": text})
        run["status"] = "success"
    except (OSError, ValueError, TypeError, AttributeError) as error:
        run["reason"] = str(error)
    return run


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="/home/sato/Android/Sdk/platform-tools/adb")
    parser.add_argument("--serial")
    parser.add_argument("--package", default="io.github.ninbyo02.lami")
    parser.add_argument("--host-port", type=int, default=11436)
    parser.add_argument("--model", default="gemma4-e2b-litert-cpu")
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--cpu-only", action="store_true")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    if not 1024 <= args.host_port <= 65535 or not 1 <= args.timeout <= 300:
        parser.error("Invalid port/timeout")
    if not args.cpu_only and (not args.serial or args.serial.startswith("emulator-")):
        parser.error("Explicit physical --serial required (or use --cpu-only)")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    scenario = "cpu_npu_reference_" + uuid.uuid4().hex
    root = args.output_dir / scenario
    root.mkdir()
    def save(name, value):
        (root / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    save("request.json", {"scenarioId": scenario, "prompts": PROMPTS, "system_instruction": SYSTEM,
        "prompt_sha256": [hashlib.sha256(p.encode()).hexdigest() for p in PROMPTS]})
    cpu = cpu_run(args.host_port, args.model, scenario)
    save("cpu.json", cpu)
    npu = None
    if not args.cpu_only:
        adb = [args.adb, "-s", args.serial]
        def call(parts):
            return subprocess.run(adb + parts, capture_output=True, timeout=20)
        try:
            state = call(["get-state"])
            if state.returncode or state.stdout.strip() != b"device":
                raise ValueError("Physical device not connected")
            qemu = call(["shell", "getprop", "ro.kernel.qemu"])
            if qemu.returncode or qemu.stdout.strip() == b"1":
                raise ValueError("Physical device required")
            for name in ["conversation_ab_benchmark_result.json", "dev_only_npu_one_turn_conversation_result.txt",
                         "qairt244_conversation_api_probe_result.txt", "qairt244_conversation_api_probe_diag.txt"]:
                old = call(["exec-out", "run-as", args.package, "cat", "files/" + name])
                if old.returncode == 0:
                    (root / ("previous-" + name)).write_bytes(old.stdout)
            def enc(value):
                return base64.b64encode(value.encode()).decode()
            broadcast = call(["shell", "am", "broadcast", "--include-stopped-packages", "-a",
                "io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION", "-n",
                args.package + "/io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationReceiver",
                "--es", "native_probe_mode", "conversation_api", "--es", "scenario_id", scenario,
                "--es", "conversation_prompts_base64", enc(json.dumps(PROMPTS)),
                "--es", "conversation_sampler_profile", "greedy_top_k_1_v1",
                "--es", "conversation_system_instruction_base64", enc(SYSTEM), "--ei", "max_output_tokens", "64"])
            (root / "broadcast.txt").write_bytes(broadcast.stdout + broadcast.stderr)
            if broadcast.returncode or not receiver_acknowledged(broadcast.stdout):
                raise ValueError("NPU receiver did not acknowledge delivery; launch a newly installed diagnostic app once and verify its component")
            deadline = time.monotonic() + args.timeout
            while time.monotonic() < deadline:
                raw = call(["exec-out", "run-as", args.package, "cat", "files/conversation_ab_benchmark_result.json"])
                try:
                    candidate = json.loads(raw.stdout)
                except ValueError:
                    candidate = None
                if isinstance(candidate, dict) and candidate.get("scenarioId") == scenario and candidate.get("status") in ("success", "failure"):
                    npu = candidate
                    break
                time.sleep(.5)
            if npu is None:
                raise TimeoutError("No matching fresh NPU terminal result")
            native = call(["exec-out", "run-as", args.package, "cat", "files/dev_only_npu_one_turn_conversation_result.txt"])
            (root / "npu-native.txt").write_bytes(native.stdout)
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            npu = {"scenarioId": scenario, "backend": "NPU", "status": "failure", "reason": str(error), "turns": []}
        # Preserve diagnostic evidence even when the process exits before writing terminal JSON.
        for name in ["qairt244_conversation_api_probe_result.txt", "qairt244_conversation_api_probe_diag.txt"]:
            try:
                diagnostic = call(["exec-out", "run-as", args.package, "cat", "files/" + name])
                if diagnostic.returncode == 0:
                    (root / name).write_bytes(diagnostic.stdout)
            except (OSError, subprocess.SubprocessError):
                pass
        if npu.get("status") != "success":
            try:
                exits = call(["shell", "dumpsys", "activity", "exit-info", args.package])
                (root / "npu-exit-info.txt").write_bytes(exits.stdout)
            except (OSError, subprocess.SubprocessError):
                pass
        save("npu.json", npu)
    checks = {"cpu": score(cpu, scenario, "HOST_CPU_REFERENCE"),
              "npu": score(npu, scenario, "NPU") if npu is not None else {"execution": "not_run", "quality": "not_evaluated"}}
    passed = all(c["execution"] == "passed" and c["quality"] == "passed" for c in checks.values())
    report = {"scenarioId": scenario, "validation": "passed" if passed else "cpu_only" if args.cpu_only and checks["cpu"]["quality"] == "passed" else "failed", "checks": checks,
        "npu_numerical_parity": "not_verified", "npu_hardware_attestation": "not_performed",
        "limitations": ["Functional answers only; not token/logit numerical equivalence.",
            "PC CPU and phone NPU timings are different environments, not a hardware speedup measurement.",
            "Model artifacts/runtime versions differ. CPU seed 0, NPU requested greedy seed 42; thinking/configuration parity is not established.",
            "CPU reconstructs history each turn; NPU requests a persistent Conversation. NPU execution identity relies on existing diagnostic report, not independent hardware attestation."],
        "evidence_dir": str(root)}
    save("comparison.json", report)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if passed or (args.cpu_only and checks["cpu"]["quality"] == "passed") else 1


if __name__ == "__main__":
    raise SystemExit(main())
