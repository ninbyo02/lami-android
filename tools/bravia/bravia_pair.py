#!/usr/bin/env python3
"""Pair the user's BRAVIA and inspect capabilities, without operating the TV.

PINs are read from the terminal/stdin. Cookies stay in a private local directory.
The Android preference export is for local provisioning after the app update is allowed.
"""
from __future__ import annotations

import argparse
import getpass
import http.cookiejar
import ipaddress
import json
import os
from pathlib import Path
import sys
import uuid
import xml.etree.ElementTree as ET

import requests


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["begin", "finish", "probe", "export-android"])
    parser.add_argument("--host", required=True)
    parser.add_argument("--pin-stdin", action="store_true")
    arguments = parser.parse_args()
    address = ipaddress.IPv4Address(arguments.host)
    if not any(address in ipaddress.IPv4Network(net) for net in ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"]):
        parser.error("Select the television's private LAN IPv4 address.")
    root = Path.home() / ".config" / "lami" / "bravia" / str(address)
    root.mkdir(parents=True, mode=0o700, exist_ok=True)
    root.chmod(0o700)
    state_path = root / "client.json"
    if state_path.exists():
        state = json.loads(state_path.read_text())
    else:
        state = {"client_id": "LAMI:" + str(uuid.uuid4()), "address": str(address)}
        fd = os.open(state_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as output:
            json.dump(state, output)
    state_path.chmod(0o600)
    cookies_path = root / "cookies.txt"
    jar = http.cookiejar.MozillaCookieJar(str(cookies_path))
    if cookies_path.exists():
        jar.load(ignore_discard=True, ignore_expires=False)
    session = requests.Session()
    session.trust_env = False
    session.cookies = jar
    base = "http://" + str(address)

    def request(service, method, params=None, pin=None):
        response = session.post(
            base + "/sony/" + service,
            json={"method": method, "params": params or [], "id": 1, "version": "1.0"},
            auth=("", pin) if pin is not None else None,
            timeout=(4, 8), allow_redirects=False,
        )
        try:
            body = response.json()
        except ValueError:
            body = {}
        return response.status_code, body

    def persist_session():
        # Pre-create with private permissions: never expose the cookie before chmod.
        fd = os.open(cookies_path, os.O_WRONLY | os.O_CREAT, 0o600)
        os.close(fd)
        cookies_path.chmod(0o600)
        jar.save(ignore_discard=True, ignore_expires=False)

    if arguments.action in {"begin", "finish"}:
        pin = "0000" if arguments.action == "begin" else (
            sys.stdin.readline().strip() if arguments.pin_stdin else getpass.getpass("TV PIN: ").strip()
        )
        if len(pin) != 4 or not pin.isascii() or not pin.isdigit():
            raise SystemExit("Enter the four digits displayed by the TV.")
        parameters = [
            {"clientid": state["client_id"], "nickname": "LAMI", "level": "private"},
            [{"value": "yes", "function": "WOL"}],
        ]
        status, body = request("accessControl", "actRegister", parameters, pin=pin)
        if arguments.action == "begin" and status in {401, 403}:
            print(json.dumps({"state": "waiting_for_tv_pin", "http_status": status, "client_name": "LAMI"}))
            return 0
        if status != 200 or "error" in body or "result" not in body:
            print(json.dumps({"state": "pairing_failed", "http_status": status}))
            return 1
        persist_session()
        print(json.dumps({"state": "paired", "http_status": status, "credentials_stored_privately": True}))
        return 0

    status, body = request("system", "getSystemInformation")
    if status != 200 or "result" not in body:
        print(json.dumps({"state": "authentication_required", "http_status": status}))
        return 1
    info = body["result"][0]
    if info.get("model") != "KJ-55A9F":
        raise SystemExit("The selected endpoint did not identify itself as KJ-55A9F.")
    if arguments.action == "export-android":
        root_xml = ET.Element("map")
        values = {
            "address": str(address), "client_id": state["client_id"], "model": info["model"],
            "mac": info.get("macAddr", ""),
            "cookie": "; ".join(cookie.name + "=" + cookie.value for cookie in jar
                                if cookie.name == "auth" and not cookie.is_expired()),
        }
        if not values["cookie"]:
            raise SystemExit("No reusable TV authentication cookie is available.")
        for name, value in values.items():
            ET.SubElement(root_xml, "string", {"name": name}).text = value
        ET.SubElement(root_xml, "boolean", {"name": "enabled", "value": "true"})
        target = root / "bravia_tv.xml"
        fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "wb") as output:
            ET.ElementTree(root_xml).write(output, encoding="utf-8", xml_declaration=True)
        print(json.dumps({"state": "android_preferences_ready", "private_path": str(target)}))
        return 0

    result = {"model": info["model"], "name": info.get("name"), "control_commands_sent": False}
    for service, method in [
        ("system", "getPowerStatus"), ("audio", "getVolumeInformation"),
        ("system", "getRemoteControllerInfo"), ("avContent", "getCurrentExternalInputsStatus"),
    ]:
        status, body = request(service, method)
        if method == "getRemoteControllerInfo" and "result" in body:
            result[method] = {"http_status": status, "key_names": [key["name"] for key in body["result"][1]]}
        else:
            result[method] = {"http_status": status, "result": body.get("result"), "error": body.get("error")}
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except requests.RequestException:
        raise SystemExit("The TV did not respond. Check the local network and television power state.")
