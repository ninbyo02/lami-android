#!/usr/bin/env python3
"""Fetch public, revision-pinned baseline assets into a local ignored directory."""
import argparse
import hashlib
import json
import subprocess
import urllib.request
from pathlib import Path

from melo_japanese import MELO_REVISION

MODEL_REVISION = "367f8795464b531b4e97c1515bddfc1243e60891"


def download(url: str, target: Path) -> str:
    temporary = target.with_suffix(target.suffix + ".part")
    digest = hashlib.sha256()
    try:
        with urllib.request.urlopen(url, timeout=60) as source, temporary.open("wb") as output:
            while block := source.read(1024 * 1024):
                digest.update(block)
                output.write(block)
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    return digest.hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    root = args.output.resolve()
    root.mkdir(parents=True, exist_ok=True)
    source = root / "MeloTTS"
    if not source.exists():
        subprocess.run(["git", "clone", "--filter=blob:none", "--no-checkout", "https://github.com/myshell-ai/MeloTTS.git", str(source)], check=True)
        subprocess.run(["git", "-C", str(source), "checkout", "--detach", MELO_REVISION], check=True)
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if revision != MELO_REVISION:
        raise ValueError("Existing MeloTTS checkout has a different revision; use a new output directory")
    model_dir = root / "MeloTTS-Japanese"
    model_dir.mkdir(exist_ok=True)
    lock = json.loads(Path(__file__).with_name("assets.lock.json").read_text())
    for filename in ["config.json", "checkpoint.pth"]:
        target = model_dir / filename
        url = f"https://huggingface.co/myshell-ai/MeloTTS-Japanese/resolve/{MODEL_REVISION}/{filename}"
        if target.exists():
            from melo_japanese import sha256
            digest = sha256(target)
        else:
            digest = download(url, target)
        if digest != lock["files"][filename]["sha256"]:
            raise ValueError(f"Asset SHA256 mismatch: {filename}")
        print(json.dumps({"file": filename, "sha256": digest}), flush=True)
    (model_dir / "provenance.json").write_text(json.dumps({"repo_id": "myshell-ai/MeloTTS-Japanese", "revision": MODEL_REVISION}, indent=2) + "\n")


if __name__ == "__main__":
    main()
