#!/usr/bin/env python3
"""Run the bounded GPU trial in an isolated ROCm image on the development PC."""
import argparse
import json
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--steps", type=int, default=200)
    parser.add_argument("--max-minutes", type=float, default=20)
    parser.add_argument("--image", default="lami-tts-lite-training:20261028")
    parser.add_argument("--freeze-text-duration", action="store_true")
    args = parser.parse_args()
    work = Path(__file__).resolve().parents[3]
    artifacts = work / "artifacts/tts-lite"
    output = args.output.resolve()
    output.relative_to(artifacts)
    if output.exists() or args.steps < 1 or args.max_minutes <= 0:
        parser.error("Choose a new artifact output and positive limits")
    cache = artifacts / "training-cache"
    cache.mkdir(exist_ok=True)
    image = json.loads(subprocess.check_output(["docker", "image", "inspect", args.image], text=True))[0]
    command = ["docker", "run", "--rm", "--network", "none", "--device=/dev/kfd", "--device=/dev/dri",
               "--shm-size=2g", "--user", "1000:1000"]
    # Group names inside the image need not match the host's render group ID.
    nodes = [Path("/dev/kfd"), *Path("/dev/dri").glob("renderD*")]
    for gid in sorted({node.stat().st_gid for node in nodes}):
        command.extend(["--group-add", str(gid)])
    command.extend(["-v", f"{work}:{work}:ro", "-v", f"{artifacts}:{artifacts}"])
    for sub in ("MeloTTS", "MeloTTS-Japanese", "lami-voice-prepared-44100"):
        command.extend(["-v", f"{artifacts / sub}:{artifacts / sub}:ro"])
    command.extend(["-w", str(work), "-e", "HIP_VISIBLE_DEVICES=0", "-e", f"XDG_CACHE_HOME={cache}",
                    "-e", f"NUMBA_CACHE_DIR={cache}/numba", "-e", f"MIOPEN_USER_DB_PATH={cache}/miopen",
                    args.image, "python", "scripts/voice/tts_lite/finetune_voice.py",
                    "--source", str(artifacts / "MeloTTS"), "--model-dir", str(artifacts / "MeloTTS-Japanese"),
                    "--prepared-data", str(artifacts / "lami-voice-prepared-44100"), "--fresh-discriminators",
                    "--output", str(output), "--steps", str(args.steps), "--max-minutes", str(args.max_minutes)])
    if args.freeze_text_duration:
        command.append("--freeze-text-duration")
    output.with_name(output.name + "-launch.json").write_text(json.dumps({"image": image["Id"], "argv": command}, indent=2) + "\n")
    return subprocess.run(command).returncode


if __name__ == "__main__":
    raise SystemExit(main())
