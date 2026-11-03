#!/usr/bin/env python3
"""Read-only BRAVIA setup helper. TV changes are limited to explicit client registration.

Runtime credentials stay in the user's private configuration directory, outside the repo.
The Android app registers its own client through Settings > TV control.
"""
from __future__ import annotations

import argparse
import base64
import ipaddress
import json
import os
from pathlib import Path
import sys
import uuid
import urllib.error
import urllib.request


def private_host(value: str) -> str:
    address = ipaddress.IPv4Address(value)
    if not any(address in ipaddress.IPv4Network(net) for net in
               ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")):
        raise ValueError("A private LAN IPv4 address is required")
    return str(address)


def save_private(path: Path, data: dict) -> None:
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w") as stream:
        json.dump(data, stream, ensure_ascii=False)
    temporary.chmod(0o600)
    temporary.replace(path)
    path.chmod(0o600)


def request(state: dict, service: str, method: str, params: list, pin: str | None = None,
            send_cookie: bool = True) -> tuple[int, dict, list[str]]:
    headers = {"Content-Type": "application/json", "Cache-Control": "no-cache"}
    if send_cookie and state.get("cookie"):
        headers["Cookie"] = state["cookie"]
    if pin is not None:
        headers["Authorization"] = "Basic " + base64.b64encode((":" + pin).encode()).decode()
    body = json.dumps({"method": method, "params": params, "id": 1, "version": "1.0"}).encode()
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    req = urllib.request.Request("http://" + state["host"] + "/sony/" + service, data=body, headers=headers)
    try:
        response = opener.open(req, timeout=8)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read(1024 * 1024 + 1)
        if len(raw) > 1024 * 1024:
            raise RuntimeError("TV response too large")
        try:
            data = json.loads(raw)
        except (ValueError, UnicodeDecodeError):
            data = {"error": [response.code, "Unexpected response format"]}
        return response.code, data, response.headers.get_all("Set-Cookie", [])


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def registration_params(state: dict) -> list:
    return [{"clientid": state["client_id"], "nickname": "LAMI Setup", "level": "private"},
            [{"value": "yes", "function": "WOL"}]]


def inspect(state: dict) -> dict:
    status, info, _ = request(state, "system", "getSystemInformation", [])
    if status != 200 or "error" in info:
        raise RuntimeError("TV authentication is required")
    device = info["result"][0]
    if device.get("model") != "KJ-55A9F":
        raise RuntimeError("The authenticated model is not KJ-55A9F")
    result = {"authenticated": True, "model": device["model"], "host": state["host"]}
    state["model"] = device["model"]
    if device.get("macAddr"):
        state["mac"] = device["macAddr"]
    for service, method in [("system", "getRemoteControllerInfo"), ("system", "getPowerStatus"),
                            ("audio", "getVolumeInformation"), ("appControl", "getApplicationList")]:
        status, data, _ = request(state, service, method, [])
        if status != 200 or "error" in data:
            result[method] = {"available": False, "http_status": status, "error_code": data.get("error", [None])[0]}
        elif method == "getRemoteControllerInfo":
            keys = data["result"][1]
            result[method] = {"available": True, "key_names": [item["name"] for item in keys]}
        elif method == "getApplicationList":
            apps = data["result"][0]
            result[method] = {"available": True, "count": len(apps),
                             "media_apps": [item["title"] for item in apps if any(n in item.get("title", "").lower()
                                            for n in ("youtube", "netflix", "prime", "hulu", "u-next", "tver"))]}
        else:
            result[method] = {"available": True, "result": data["result"]}
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("begin", "complete", "inspect"))
    parser.add_argument("--host", type=private_host)
    parser.add_argument("--state", type=Path, default=Path.home() / ".config/lami-bravia/setup-session.json")
    args = parser.parse_args()
    state = json.loads(args.state.read_text()) if args.state.exists() else {}
    if args.action == "begin":
        if not args.host:
            parser.error("--host is required for begin")
        if state.get("host") != args.host or not state.get("client_id"):
            state = {"host": args.host, "client_id": "LAMI:" + str(uuid.uuid4())}
        # Persist the client identifier before showing a PIN so a later invocation can finish.
        save_private(args.state, state)
        status, data, cookies = request(state, "accessControl", "actRegister",
                                        registration_params(state), send_cookie=False)
        if status == 401:
            print(json.dumps({"pairing_pending": True, "tv_label": "LAMI Setup", "pin_source": "TV screen"}))
            return 0
        if status != 200 or "error" in data:
            raise RuntimeError("TV registration could not be started (HTTP " + str(status) + ")")
        if cookies:
            state["cookie"] = "; ".join(line.split(";", 1)[0].strip() for line in cookies)
        result = inspect(state)
        save_private(args.state, state)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    if not state.get("host") or not state.get("client_id"):
        raise RuntimeError("Run begin first")
    state["host"] = private_host(state["host"])
    if args.action == "complete":
        print("Enter the four-digit PIN from the TV (stdin; never stored):", flush=True)
        pin = sys.stdin.readline().strip()
        if len(pin) != 4 or not pin.isascii() or not pin.isdigit():
            raise ValueError("A four-digit PIN is required")
        status, data, cookies = request(state, "accessControl", "actRegister", registration_params(state),
                                        pin=pin, send_cookie=False)
        pin = None
        if status != 200 or "error" in data:
            raise RuntimeError("TV registration failed; request a new PIN if it expired")
        pairs = [line.split(";", 1)[0].strip() for line in cookies]
        if pairs:
            state["cookie"] = "; ".join(pairs)
    result = inspect(state)
    save_private(args.state, state)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError, KeyError) as error:
        print(type(error).__name__ + ": " + str(error), file=sys.stderr)
        raise SystemExit(1)
