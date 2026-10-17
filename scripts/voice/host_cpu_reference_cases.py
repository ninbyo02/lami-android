"""CPU-only case loading, scoring and real LiteRT host inference helpers."""
import json
import time
import unicodedata
import urllib.request
PROMPTS = ["What is 17 plus 25? Reply with the number only.", "Now multiply that result by 2. Reply with the number only."]
EXPECTED = ["42", "84"]
SYSTEM = "You are a helpful assistant. Follow the user instructions."

def normalize(text):
    return "".join(unicodedata.normalize("NFKC", text).split()).rstrip("。.!！")

def load_case_set(path):
    data = json.loads(path.read_text())
    if not isinstance(data, dict) or not isinstance(data.get("turns"), list) or not 1 <= len(data["turns"]) <= 6:
        raise ValueError("Case set requires 1..6 turns")
    prompts, expected = [], []
    for turn in data["turns"]:
        if not isinstance(turn, dict) or any(not isinstance(turn.get(k), str) or not turn[k].strip() for k in ["prompt", "expected"]):
            raise ValueError("Each turn needs nonblank prompt and expected strings")
        prompts.append(turn["prompt"]); expected.append(turn["expected"])
    if sum(map(len, prompts)) > 12000:
        raise ValueError("Case set text too long")
    return prompts, expected

def score(run, scenario, backend, prompts=PROMPTS, expected=EXPECTED):
    errors = []
    if not isinstance(run, dict):
        return {"execution": "failed", "quality": "not_evaluated", "errors": ["Missing result object"]}
    if run.get("scenarioId") != scenario or run.get("backend") != backend:
        errors.append("Scenario/backend identity mismatch")
    turns = run.get("turns", [])
    if not isinstance(turns, list) or len(turns) != len(prompts):
        errors.append("Missing or extra turns")
        turns = []
    for index, (turn, prompt) in enumerate(zip(turns, prompts), 1):
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
    quality = [normalize(t["sanitizedOutput"]).casefold() == normalize(expected).casefold() for t, expected in zip(turns, expected)]
    return {"execution": "passed", "quality": "passed" if all(quality) else "failed", "turn_quality": quality, "errors": []}

def cpu_run(port, model, scenario, prompts=PROMPTS):
    messages = [{"role": "system", "content": SYSTEM}]
    run = {"scenarioId": scenario, "backend": "HOST_CPU_REFERENCE", "status": "failure", "turns": []}
    try:
        for index, prompt in enumerate(prompts, 1):
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
