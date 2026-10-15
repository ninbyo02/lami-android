"""Exercise normal Android chat and Room persistence using fresh host CPU inference.
Creates one conversation in the explicit emulator debug app; never clears app data.
"""
import argparse
import hashlib
import json
import re
import shlex
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="/home/sato/Android/Sdk/platform-tools/adb")
    parser.add_argument("--serial", default="emulator-5580")
    parser.add_argument("--package", default="io.github.ninbyo02.lami.assistantprobe")
    parser.add_argument("--model", default="sam860/LFM2:350m")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = {"validation": "failed", "serial": args.serial, "model": args.model,
              "scope": "normal_local_chat_to_host_cpu", "npu_execution": "not_run",
              "npu_numerical_parity": "not_verified", "cases": []}
    adb = [args.adb, "-s", args.serial]
    package = args.package
    def shell(*parts):
        p = subprocess.run(adb + ["shell", shlex.join(parts)], capture_output=True, text=True, timeout=25)
        if p.returncode:
            raise ValueError(p.stderr.strip() or p.stdout.strip())
        return p.stdout
    def dump():
        shell("uiautomator", "dump", "/sdcard/lami-host-development-ui.xml")
        return ET.fromstring(shell("cat", "/sdcard/lami-host-development-ui.xml"))
    def tap(node):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        shell("input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    def send(prompt):
        ui = dump()
        tap(next(n for n in ui.iter("node") if n.get("class") == "android.widget.EditText"))
        shell("input", "text", prompt.replace(" ", "%s"))
        shell("input", "keyevent", "4")
        ui = dump()
        tap(next(n for n in ui.iter("node") if n.get("content-desc") == "Send Button"))
    def read_report():
        try:
            data = json.loads(shell("run-as", package, "cat", "files/emulator_main_chat_reference.json"))
            return data if isinstance(data, dict) else {}
        except ValueError:
            return {}
    try:
        if not args.serial.startswith("emulator-") or shell("getprop", "ro.kernel.qemu").strip() != "1":
            raise ValueError("Explicit verified emulator required; physical devices are refused")
        result = shell("am", "start", "-W", "-n", package + "/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity",
                       "--ez", "enable_main_chat", "true", "--es", "model", args.model)
        if "Error" in result:
            raise ValueError("Development screen launch failed: " + result)
        old_id = read_report().get("request_id")
        prompts = ["What is 17 plus 25? Reply briefly.", "Now multiply that result by 2. Reply briefly."]
        for index, prompt in enumerate(prompts):
            send(prompt)
            expected_hash = hashlib.sha256(prompt.encode()).hexdigest()
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                record = read_report()
                fresh = (record.get("request_id") and record["request_id"] != old_id
                         and record.get("input_sha256") == expected_hash and record.get("model") == args.model)
                if fresh and record.get("status") in ("success", "failed", "cancelled"):
                    break
                time.sleep(0.25)
            else:
                raise TimeoutError("No matching fresh terminal result")
            report["cases"].append(record)
            if record["status"] != "success" or not record.get("response"):
                raise ValueError("Inference failed: " + str(record))
            if record.get("requested_num_gpu") != 0 or record.get("backend") != "host_cpu_reference":
                raise ValueError("Unexpected backend")
            if index == 1 and record.get("history_messages") != 2:
                raise ValueError("Previous user/assistant pair was not passed")
            # Wait for the normal UI to finish persistence, not only the backend report.
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                ui = dump()
                texts = [n.get("text", "") for n in ui.iter("node")]
                if record["response"] in texts and any(n.get("content-desc") == "Send Button" for n in ui.iter("node")):
                    break
            else:
                raise ValueError("Completed response is not visible in normal chat")
            old_id = record["request_id"]
        shell("am", "force-stop", package)
        with tempfile.TemporaryDirectory(prefix="lami-emulator-room-") as directory:
            root = Path(directory)
            for name in ("chat-database", "chat-database-wal", "chat-database-shm"):
                p = subprocess.run(adb + ["exec-out", "run-as", package, "cat", "databases/" + name], capture_output=True, timeout=20)
                if p.returncode == 0:
                    (root / name).write_bytes(p.stdout)
                elif name == "chat-database":
                    raise ValueError("Room database missing")
            con = sqlite3.connect(root / "chat-database")
            con.row_factory = sqlite3.Row
            row = con.execute("SELECT chatId FROM chat_table WHERE isSendbyMe=1 AND message=? ORDER BY messageID DESC LIMIT 1", (prompts[0],)).fetchone()
            if row is None:
                raise ValueError("Saved conversation missing")
            rows = [dict(r) for r in con.execute("SELECT message,isSendbyMe,status,modelName,localSourceSummary FROM chat_table WHERE chatId=? ORDER BY messageID", (row["chatId"],))]
            con.close()
            expected = [prompts[0], report["cases"][0]["response"], prompts[1], report["cases"][1]["response"]]
            if [r["message"] for r in rows] != expected or any(r["status"] != "COMPLETED" for r in rows):
                raise ValueError("Room messages do not match completed UI responses")
            if any("host_cpu_reference" not in (r["localSourceSummary"] or "") for r in rows if not r["isSendbyMe"]):
                raise ValueError("Saved backend provenance missing")
            report["saved_messages"] = [{k: r[k] for k in ("message", "isSendbyMe", "status", "modelName")} for r in rows]
        shell("am", "start", "-W", "-n", package + "/io.github.ninbyo02.lami.MainActivity")
        report["process_restart"] = "passed"
        report["validation"] = "passed"
    except (OSError, ValueError, TimeoutError, subprocess.SubprocessError, sqlite3.Error, ET.ParseError, StopIteration) as error:
        report["error"] = str(error) or type(error).__name__
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["validation"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
