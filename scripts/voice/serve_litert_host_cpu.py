"""Loopback-only Ollama chat adapter for a fixed .litertlm CPU reference model."""
import argparse
import json
import signal
import socket
import select
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from check_litert_host_cpu import sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--runtime-python", required=True)
    parser.add_argument("--alias", default="gemma4-e2b-litert-cpu")
    parser.add_argument("--port", type=int, default=11435)
    args = parser.parse_args()
    model = args.model.resolve(strict=True)
    model_stat = model.stat()
    model_sha = sha(model)
    runtime_version = subprocess.check_output([args.runtime_python, "-c", "import importlib.metadata;print(importlib.metadata.version('litert-lm-api'))"], text=True).strip()
    identity = {"backend": "host_litert_cpu", "runtime": "litert-lm-api", "runtime_version": runtime_version,
                "model_sha256": model_sha, "model_bytes": model_stat.st_size, "model_alias": args.alias,
                "npu_execution": "not_run", "npu_numerical_parity": "not_verified"}
    lock = threading.Lock()
    children = set()
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *unused):
            pass
        def json_response(self, code, data):
            raw = json.dumps(data).encode()
            self.send_response(code); self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw))); self.end_headers(); self.wfile.write(raw)
        def do_GET(self):
            self.json_response(200 if self.path == "/health" else 404, identity if self.path == "/health" else {"error": "not found"})
        def do_POST(self):
            if self.path != "/api/chat":
                self.json_response(404, {"error": "not found"}); return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 65536:
                    raise ValueError("Body must be 1..65536 bytes")
                body = json.loads(self.rfile.read(length))
                if not isinstance(body, dict):
                    raise ValueError("Expected object")
                if body.get("model") != args.alias:
                    self.json_response(404, {"error": "Unknown fixed reference model"}); return
                if body.get("stream") is not True or body.get("options", {}).get("num_gpu") != 0:
                    raise ValueError("Streaming CPU requests only")
                messages = body.get("messages")
                if not isinstance(messages, list) or not 1 <= len(messages) <= 13:
                    raise ValueError("Expected 1..13 text messages")
                for item in messages:
                    if not isinstance(item, dict) or item.get("role") not in ("user", "assistant", "system") or not isinstance(item.get("content"), str) or item.get("images"):
                        raise ValueError("Text messages only")
                if messages[-1]["role"] != "user" or not messages[-1]["content"].strip():
                    raise ValueError("Last message must contain user text")
                if sum(len(m["content"]) for m in messages) > 20000:
                    raise ValueError("Input too long")
                current = model.stat()
                if (current.st_size, current.st_mtime_ns) != (model_stat.st_size, model_stat.st_mtime_ns):
                    raise ValueError("Model changed; restart reference server")
            except (ValueError, TypeError, AttributeError, OSError) as error:
                self.json_response(400, {"error": str(error)}); return
            if not lock.acquire(blocking=False):
                self.json_response(503, {"error": "Reference CPU busy"}); return
            child = None
            timer = None
            try:
                with tempfile.TemporaryDirectory(prefix="lami-litert-request-") as directory:
                    root = Path(directory)
                    history = root / "history.json"; history.write_text(json.dumps(messages[:-1]))
                    result = root / "result.json"
                    command = [args.runtime_python, str(Path(__file__).with_name("check_litert_host_cpu.py")), "--worker", "--stream",
                               "--model", str(model), "--prompt", messages[-1]["content"], "--messages", str(history), "--output", str(result)]
                    with (root / "error.txt").open("w+") as errors:
                        child = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=errors, text=True, bufsize=1)
                        children.add(child)
                        timer = threading.Timer(120, child.kill); timer.daemon = True; timer.start()
                        self.send_response(200); self.send_header("Content-Type", "application/x-ndjson"); self.send_header("Connection", "close"); self.end_headers()
                        while True:
                            ready, _, _ = select.select([child.stdout, self.connection], [], [], .2)
                            if self.connection in ready and self.connection.recv(1, socket.MSG_PEEK) == b"":
                                raise ConnectionError("Client disconnected")
                            if child.stdout in ready:
                                line = child.stdout.readline()
                                if not line:
                                    break
                                event = json.loads(line)
                                self.wfile.write((json.dumps(event, ensure_ascii=False) + "\n").encode()); self.wfile.flush()
                        child.wait(timeout=10)
                        if child.returncode != 0 or not result.is_file():
                            errors.flush(); errors.seek(0)
                            raise ValueError("LiteRT CPU worker failed: " + errors.read()[-6000:])
                        terminal = json.loads(result.read_text())
                        if not terminal.get("response"):
                            raise ValueError("Empty generated response")
                        event = {"model": args.alias, "done": True, "done_reason": "reference_complete",
                                 "reference_backend": identity, "reference_metrics": terminal}
                        self.wfile.write((json.dumps(event, ensure_ascii=False) + "\n").encode()); self.wfile.flush()
            except (OSError, ValueError, subprocess.SubprocessError) as error:
                try:
                    self.wfile.write((json.dumps({"error": str(error), "done": False}) + "\n").encode()); self.wfile.flush()
                except OSError:
                    pass
            finally:
                if timer:
                    timer.cancel()
                if child:
                    if child.poll() is None:
                        child.kill(); child.wait(timeout=10)
                    children.discard(child)
                lock.release()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.daemon_threads = True
    def stop(signum, frame):
        for child in list(children):
            if child.poll() is None:
                child.kill()
        raise SystemExit(0)
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    print(json.dumps(dict(identity, listen="127.0.0.1:" + str(args.port))), flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
