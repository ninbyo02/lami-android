"""Run a .litertlm model on host CPU in an isolated, time-bounded child process."""
import argparse
import hashlib
import importlib.metadata
import json
import subprocess
import sys
import time
from pathlib import Path


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def worker(args):
    import resource
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    import litert_lm
    started = time.monotonic()
    litert_lm.set_min_log_severity(litert_lm.LogSeverity.ERROR)
    with litert_lm.Engine(str(args.model), backend=litert_lm.Backend.CPU(thread_count=4), max_num_tokens=2048) as engine:
        loaded = time.monotonic()
        initial = []
        if args.messages:
            constructors = {"user": litert_lm.Message.user, "assistant": litert_lm.Message.model, "system": litert_lm.Message.system}
            initial = [constructors[item["role"]](litert_lm.Contents.of(item["content"]) if item["role"] == "assistant" else item["content"]) for item in json.loads(args.messages.read_text())]
        with engine.create_conversation(messages=initial, sampler_config=litert_lm.SamplerConfig(top_k=1, seed=0), max_output_tokens=64, thinking_config=litert_lm.ThinkingConfig(enable_thinking=False, thinking_token_budget=0)) as conversation:
            text = []
            first = None
            chunks = 0
            for chunk in conversation.send_message_async(args.prompt):
                for item in chunk.get("content", []):
                    if item.get("type") == "text" and item.get("text"):
                        if first is None:
                            first = time.monotonic()
                        chunks += 1
                        text.append(item["text"])
                        if args.stream:
                            print(json.dumps({"message": {"role": "assistant", "content": item["text"]}, "done": False}, ensure_ascii=False), flush=True)
            response = "".join(text)
            if not response.strip():
                raise ValueError("No non-empty generated text")
    result = {"status": "passed", "response": response, "content_chunks": chunks,
              "runtime_version": importlib.metadata.version("litert-lm-api"),
              "sampling": "top_k_1_seed_0", "max_output_tokens": 64,
              "input_sha256": hashlib.sha256(args.prompt.encode()).hexdigest(),
              "model_load_ms": round((loaded - started) * 1000),
              "first_content_ms": round((first - started) * 1000),
              "total_ms": round((time.monotonic() - started) * 1000)}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--prompt", default="What is 17 plus 25? Reply briefly.")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--runtime-python", default=sys.executable)
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--messages", type=Path, help=argparse.SUPPRESS)
    parser.add_argument("--stream", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--worker", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.worker:
        return worker(args)
    report = {"validation": "failed", "backend": "host_litert_cpu", "model": str(args.model),
              "npu_execution": "not_run", "npu_numerical_parity": "not_verified"}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    import tempfile
    try:
        before = args.model.stat()
        report["model_sha256"] = sha(args.model)
        report["model_bytes"] = before.st_size
        with tempfile.TemporaryDirectory(prefix="lami-litert-cpu-") as directory:
            child_output = Path(directory) / "result.json"
            command = [args.runtime_python, str(Path(__file__).resolve()), "--worker", "--model", str(args.model.resolve()),
                       "--prompt", args.prompt, "--output", str(child_output)]
            try:
                child = subprocess.run(command, capture_output=True, text=True, timeout=args.timeout)
                report["child_exit_code"] = child.returncode
                report["child_diagnostics"] = (child.stdout + child.stderr)[-12000:]
            except subprocess.TimeoutExpired as error:
                raise TimeoutError("Host CPU inference exceeded " + str(args.timeout) + " seconds") from error
            if child.returncode != 0 or not child_output.is_file():
                raise ValueError("Host runtime failed; inspect child_diagnostics")
            result = json.loads(child_output.read_text())
            after = args.model.stat()
            if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                raise ValueError("Model changed during validation")
            if result.get("input_sha256") != hashlib.sha256(args.prompt.encode()).hexdigest():
                raise ValueError("Input identity mismatch")
            report["result"] = result
            report["validation"] = "passed"
    except (OSError, ValueError, TimeoutError, subprocess.SubprocessError) as error:
        report["error"] = str(error)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["validation"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
