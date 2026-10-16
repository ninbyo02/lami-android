"""Exercise HTTP framing and validation without a native inference runtime."""
import json
import socket
import subprocess
import sys
import tempfile
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path


class BridgeTest(unittest.TestCase):
    def test_batched_stream_and_invalid_request(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            model = root / "model.litertlm"
            model.write_bytes(b"test model")
            runtime = root / "runtime"
            runtime.write_text("#!/usr/bin/env python3\n" +
                "import json,sys\nfrom pathlib import Path\n" +
                "if sys.argv[1] == '-c': print('test'); sys.exit(0)\n" +
                "output = Path(sys.argv[sys.argv.index('--output')+1])\n" +
                "events = [json.dumps({'message':{'content':'x'*100},'done':False}) for _ in range(200)]\n" +
                "sys.stdout.write('\\n'.join(events)+'\\n'); sys.stdout.flush()\n" +
                "output.write_text(json.dumps({'response':'x'*20000}))\n")
            runtime.chmod(0o755)
            with socket.socket() as sock:
                sock.bind(("127.0.0.1", 0))
                port = sock.getsockname()[1]
            server = subprocess.Popen([sys.executable, str(Path(__file__).with_name("serve_litert_host_cpu.py")),
                "--model", str(model), "--runtime-python", str(runtime), "--port", str(port)], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            try:
                base = "http://127.0.0.1:" + str(port)
                for _ in range(100):
                    try:
                        with urllib.request.urlopen(base + "/health", timeout=1) as response:
                            self.assertEqual(json.load(response)["runtime_version"], "test")
                        break
                    except (OSError, urllib.error.URLError):
                        if server.poll() is not None:
                            self.fail(server.stderr.read().decode())
                        time.sleep(.05)
                else:
                    self.fail("Server did not start")
                body = {"model":"gemma4-e2b-litert-cpu", "stream":True, "options":{"num_gpu":0},
                        "messages":[{"role":"user", "content":"test"}]}
                request = urllib.request.Request(base + "/api/chat", data=json.dumps(body).encode())
                with urllib.request.urlopen(request, timeout=10) as response:
                    events = [json.loads(line) for line in response]
                self.assertEqual(len(events), 201)
                self.assertEqual("".join(e.get("message", {}).get("content", "") for e in events), "x"*20000)
                self.assertTrue(events[-1]["done"])
                self.assertEqual(events[-1]["reference_backend"]["npu_execution"], "not_run")
                body["options"] = []
                request = urllib.request.Request(base + "/api/chat", data=json.dumps(body).encode())
                with self.assertRaises(urllib.error.HTTPError) as caught:
                    urllib.request.urlopen(request, timeout=5)
                self.assertEqual(caught.exception.code, 400)
            finally:
                server.terminate()
                server.wait(timeout=10)
                server.stderr.close()


if __name__ == "__main__":
    unittest.main()
