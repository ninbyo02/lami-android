#!/usr/bin/env python3
"""Validate and stream external TTS bundles into an explicitly selected debug app."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import shlex
import subprocess
import sys
import tarfile
import tempfile
from typing import BinaryIO
import uuid


SUPER_FILES = (
    "duration_predictor.int8.onnx", "text_encoder.int8.onnx",
    "vector_estimator.int8.onnx", "vocoder.int8.onnx",
    "tts.json", "unicode_indexer.bin", "voice.bin",
)
QWEN_TARGET = "files/local_models/lami_tts/prepared_hai"
QWEN_DEFAULT_MAIN = "stateless-28-int4-cache256-et14.pte"
QWEN_DEFAULT_CP = "cp-stateless-fp32-cache32-et14.pte"
QWEN_DECODER = "speech-decoder-dynamic-et14.pte"
QWEN_DECODER_SHA256 = "05288ee2c763f80067139019ca66915bad150de705acc940200735f653c1b7c5"
QWEN_MAIN_PILOTS = {
    "stateless-28-fp32-cache256-et14.pte": "0813bb92405360aa285560f16270319491f7757480a98cb882e897a0e4d7531a",
    "main-int8-permute-cache256.pte": "5a03abc2481cc050ea37950247b73450fd3be64d6a4b7854dee4251b39db9bba",
    "main-int8-cache256.pte": "6a00b9912080e057db86fe29e0c67bb3a8fa6c0851c8f5daea04175578d5c338",
    "main-int8-permute-cache128.pte": "ec6e5027e623b054f711905113c1424b0a6de0cc7e43dc11b24ccc03b0dc1d8c",
    "main-int8-grouped-cache128.pte": "83ef62e9ac80c6db43d532b43c7e85d0586e5860e6718c552054f321d8da5dca",
}
QWEN_CP_PILOTS = {
    "cp-int8-cache32.pte": "8d0843096887167a64610e33569cc6c15b61dfedd19ddada23ae98c205cd1d87",
    "cp-int8-cache16.pte": "43d29ccbd80d8f9c6e0e2cf57d84c5939a772d31262301088b73f7613e22e22c",
    "cp-int8-grouped-cache16.pte": "54aa8ae8872e3bcbf78165f76255136627ca9de48ff02edf2bd9bb1257df1d01",
    "cp-int8-grouped-delta-cache16.pte": "0f801eac3fbdd26db5e972d95b79e57362e75582ff09efe24a249da62ee046cf",
}
SUPER_TARGET = "files/supertonic3-light5"
KOKORO_TARGET = "files/local_models/kokoro_scratch"
KOKORO_FILES = ("frontend.json", "render.onnx", "text.onnx")
KOKORO_HASH_PATTERN = re.compile(r"[0-9a-f]{64}")
HASH_PATTERN = re.compile(r"[0-9a-fA-F]{64}")


@dataclass(frozen=True)
class Asset:
    name: str
    source: Path
    size: int
    sha256: str


@dataclass(frozen=True)
class Bundle:
    engine: str
    source: Path
    target: str
    assets: tuple[Asset, ...]
    content_sha256: str

    def summary(self) -> dict:
        return {"engine": self.engine, "source": str(self.source), "target": self.target,
                "file_count": len(self.assets), "bytes": sum(a.size for a in self.assets),
                "content_sha256": self.content_sha256}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def relative_name(name: str) -> str:
    require(isinstance(name, str) and bool(re.fullmatch(r"[A-Za-z0-9_./-]+", name)),
            f"Unsafe bundle path: {name!r}")
    path = PurePosixPath(name)
    require(bool(path.parts) and not path.is_absolute() and path.as_posix() == name and
            all(part not in (".", "..") for part in path.parts), f"Unsafe bundle path: {name!r}")
    return name


def asset_path(root: Path, name: str) -> Path:
    name = relative_name(name)
    path = root
    for part in PurePosixPath(name).parts:
        path = path / part
        require(not path.is_symlink(), f"Bundle symlink is not supported: {name}")
    require(path.is_file(), f"Missing or empty bundle asset: {name}")
    return path


def asset_at(root: Path, name: str, expected_hash: str | None = None,
             expected_size: int | None = None) -> Asset:
    path = asset_path(root, name)
    size = path.stat().st_size
    require(size > 0, f"Missing or empty bundle asset: {name}")
    require(expected_size is None or size == expected_size, f"Size mismatch: {name}")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    actual_hash = digest.hexdigest()
    if expected_hash is not None:
        require(isinstance(expected_hash, str) and bool(HASH_PATTERN.fullmatch(expected_hash)),
                f"Invalid SHA256: {name}")
        require(actual_hash == expected_hash.lower(), f"SHA256 mismatch: {name}")
    return Asset(name, path, size, actual_hash)


def canonical_hash(assets: list[Asset] | tuple[Asset, ...]) -> str:
    rows = [{"bytes": a.size, "name": a.name, "sha256": a.sha256}
            for a in sorted(assets, key=lambda item: item.name)]
    return hashlib.sha256(json.dumps(rows, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def read_manifest(root: Path, name: str) -> tuple[dict, Asset]:
    asset = asset_at(root, name)
    require(asset.size < 64 * 1024 * 1024, f"Manifest is too large: {name}")
    with asset.source.open(encoding="utf-8") as stream:
        manifest = json.load(stream)
    require(isinstance(manifest, dict), f"Manifest must be an object: {name}")
    return manifest, asset


def qwen_required_files(manifest: dict) -> set[str]:
    """Keep manifest eligibility aligned with Android's LamiQwenBundleContract."""
    require(manifest.get("version") == 2 and manifest.get("capacity") == 256,
            "Qwen requires voice-text-bundle.json version 2 / capacity 256")
    require("speaker" not in manifest or manifest["speaker"] == "lami_cute",
            "Qwen comparison requires speaker lami_cute")
    hashes = manifest.get("sha256")
    require(isinstance(hashes, dict) and bool(hashes), "Qwen manifest has no SHA256 assets")
    main = manifest.get("main_program", QWEN_DEFAULT_MAIN)
    cp = manifest.get("cp_program", QWEN_DEFAULT_CP)
    require(isinstance(main, str) and (main == QWEN_DEFAULT_MAIN or main in QWEN_MAIN_PILOTS),
            f"Unsupported Qwen main program: {main}")
    require(isinstance(cp, str) and (cp == QWEN_DEFAULT_CP or cp in QWEN_CP_PILOTS),
            f"Unsupported Qwen CP program: {cp}")
    for program, pilots in ((main, QWEN_MAIN_PILOTS), (cp, QWEN_CP_PILOTS)):
        require(program not in pilots or hashes.get(program) == pilots[program],
                f"Unverified Qwen program: {program}")
    require(hashes.get(QWEN_DECODER) == QWEN_DECODER_SHA256,
            "Qwen requires the validated causal PCM decoder")
    require(all(manifest.get(name) == value for name, value in (
        ("decoder_frames_min", 2), ("decoder_frames_max", 256),
        ("samples_per_frame", 1920), ("sample_rate", 24_000))), "Qwen decoder contract mismatch")
    prepacked = manifest.get("cp_head_prepacked4", False)
    require(isinstance(prepacked, bool), "Invalid Qwen packed-head option")
    require(not prepacked or not manifest.get("cp_head_fixed_benchmark", False),
            "Qwen fixed benchmark requires row-major heads")
    required = {main, cp, QWEN_DECODER, "main.head.f32", "main.embedding.f32",
                "text_frontend/vocab.json", "text_frontend/merges.txt",
                "text_frontend/tokenizer_config.json", "text_frontend/projected-text.f32"}
    required.update(f"cp.head.{index}{'.packed4' if prepacked else ''}.f32" for index in range(15))
    required.update(f"cp.embedding.{index}.f32" for index in range(15))
    require(required <= hashes.keys(), f"Missing required Qwen assets: {sorted(required - hashes.keys())}")
    require("voice-text-bundle.json" not in hashes, "Qwen manifest must not hash itself")
    capacity = 128 if main in {"main-int8-permute-cache128.pte", "main-int8-grouped-cache128.pte"} else 256
    for name in ("rope_cos", "rope_sin"):
        positions = manifest.get(name)
        require(isinstance(positions, list) and len(positions) >= capacity, f"Qwen {name} has insufficient positions")
        require(all(isinstance(row, list) and len(row) == 128 and
                    all(isinstance(value, (int, float)) and not isinstance(value, bool) and
                        math.isfinite(value) and abs(value) <= 3.4028234663852886e38 for value in row)
                    for row in positions[:capacity]), f"Invalid Qwen {name} values")
    return required


def validate_qwen(directory: Path) -> Bundle:
    root = directory.resolve(strict=True)
    manifest, metadata = read_manifest(root, "voice-text-bundle.json")
    qwen_required_files(manifest)
    hashes = manifest["sha256"]
    require(all(isinstance(value, str) and bool(HASH_PATTERN.fullmatch(value)) for value in hashes.values()),
            "Qwen manifest contains an invalid SHA256")
    assets = [asset_at(root, name, expected_hash) for name, expected_hash in sorted(hashes.items())]
    assets.append(metadata)
    return Bundle("qwen", root, QWEN_TARGET, tuple(assets), canonical_hash(assets))


def validate_supertonic(directory: Path) -> Bundle:
    root = directory.resolve(strict=True)
    manifest, metadata = read_manifest(root, "manifest.json")
    # The selector uses sid0 / five inference steps. A valid model hash alone does
    # not establish compatibility with those runtime options (HQ10 needs ten).
    profile = manifest.get("profile")
    if isinstance(profile, str):
        step_profile = re.fullmatch(r"(?:hq|light|steps?)[ _-]?(\d+)", profile.strip(), re.IGNORECASE)
        require(step_profile is None or int(step_profile.group(1)) == 5,
                "Supertonic Light5 destination requires a five-step profile; HQ10 is incompatible")
    elif profile is not None:
        require(isinstance(profile, dict), "Invalid Supertonic profile metadata")
    options = manifest.get("recommended_runtime_options", {})
    require(isinstance(options, dict), "Invalid Supertonic recommended_runtime_options")
    for declared in (manifest, options, profile if isinstance(profile, dict) else {}):
        for name, expected in (("steps", 5), ("sid", 0)):
            require(name not in declared or type(declared[name]) is int and declared[name] == expected,
                    f"Supertonic Light5 destination requires {name}={expected}; declared {declared.get(name)!r}")
    rows = manifest.get("files")
    require(isinstance(rows, list) and len(rows) == len(SUPER_FILES) and
            all(isinstance(row, dict) for row in rows), "Supertonic manifest must list seven files")
    names = [row.get("name") for row in rows]
    require(len(set(names)) == len(SUPER_FILES) and set(names) == set(SUPER_FILES),
            "Supertonic manifest must list exactly the seven required runtime files")
    assets = []
    for row in sorted(rows, key=lambda item: item["name"]):
        require(isinstance(row.get("bytes"), int) and row["bytes"] > 0,
                f"Invalid asset size: {row['name']}")
        require(isinstance(row.get("sha256"), str) and bool(HASH_PATTERN.fullmatch(row["sha256"])),
                f"Invalid SHA256: {row['name']}")
        assets.append(asset_at(root, row["name"], row.get("sha256"), row["bytes"]))
    fingerprint = canonical_hash(assets)
    require(fingerprint == manifest.get("bundle_sha256"), "Supertonic canonical bundle SHA256 mismatch")
    require(manifest.get("total_bytes", sum(a.size for a in assets)) == sum(a.size for a in assets),
            "Supertonic total byte count mismatch")
    return Bundle("supertonic3_light5", root, SUPER_TARGET, tuple([*assets, metadata]), fingerprint)


def read_kokoro_metadata(root: Path, name: str, max_bytes: int) -> tuple[dict, Asset]:
    path = asset_path(root, name)
    with path.open("rb") as stream:
        data = stream.read(max_bytes + 1)
    require(0 < len(data) <= max_bytes, f"Invalid Kokoro metadata size: {name}")
    metadata = json.loads(data.decode("utf-8"))
    require(isinstance(metadata, dict), f"Kokoro metadata must be an object: {name}")
    return metadata, Asset(name, path, len(data), hashlib.sha256(data).hexdigest())


def validate_kokoro(directory: Path) -> Bundle:
    """Keep the provisioning contract aligned with Android's LamiKokoroScratchBundle."""
    root = directory.resolve(strict=True)
    manifest, metadata = read_kokoro_metadata(root, "manifest.json", 1_048_576)
    require(manifest.get("diagnostic_only") is False and manifest.get("quality_verified") is True and
            manifest.get("android_device_verified") is True,
            "Kokoro requires a non-diagnostic bundle verified for pronunciation quality and Android")
    format_integers = {"format_version": 1, "opset": 17, "sample_rate": 24_000, "channels": 1,
                       "samples_per_frame": 600, "harmonics": 9, "acoustic_channels": 512,
                       "prosody_channels": 640}
    max_frames = manifest.get("max_frames")
    require(all(type(manifest.get(key)) is int and manifest[key] == expected
                for key, expected in format_integers.items()) and
            manifest.get("runtime") == "onnxruntime-cpu" and
            manifest.get("origin") == "all-weights-random-initialized" and
            manifest.get("teacher_forced") is False and manifest.get("strict_state_reload") is True and
            type(max_frames) is int and 1 <= max_frames <= 600,
            "Incompatible Kokoro bundle format")
    checkpoint = manifest.get("checkpoint_sha256")
    require(isinstance(checkpoint, str) and bool(KOKORO_HASH_PATTERN.fullmatch(checkpoint)),
            "Invalid Kokoro checkpoint SHA256")
    entries = manifest.get("files")
    require(isinstance(entries, dict) and set(entries) == set(KOKORO_FILES),
            "Kokoro manifest must list exactly the three required runtime files")

    assets = []
    for name in KOKORO_FILES:
        entry = entries[name]
        require(isinstance(entry, dict), f"Invalid Kokoro asset metadata: {name}")
        size, digest = entry.get("bytes"), entry.get("sha256")
        require(type(size) is int and size > 0, f"Invalid Kokoro asset size: {name}")
        require(isinstance(digest, str) and bool(KOKORO_HASH_PATTERN.fullmatch(digest)),
                f"Invalid SHA256: {name}")
        if name == "frontend.json":
            frontend, asset = read_kokoro_metadata(root, name, 65_536)
            require(asset.size == size, f"Size mismatch: {name}")
            require(asset.sha256 == digest, f"SHA256 mismatch: {name}")
            vocab = frontend.get("vocab")
            require(all(type(frontend.get(key)) is int and frontend[key] == expected
                        for key, expected in {"bos_id": 0, "eos_id": 0, "min_tokens": 3, "max_tokens": 512}.items()) and
                    isinstance(vocab, dict) and 1 <= len(vocab) <= 512 and
                    all(len(symbol) == 1 and type(token_id) in (int, float) and
                        1 <= token_id <= 65_535 and int(token_id) == token_id
                        for symbol, token_id in vocab.items()), "Invalid Kokoro phoneme vocabulary")
        else:
            asset = asset_at(root, name, digest, size)
        assets.append(asset)

    # Report the same identity that the app uses for its compiled approval list.
    fingerprint = f"{checkpoint}\nmax_frames:{max_frames}\n" + "".join(
        f"{asset.name}:{asset.size}:{asset.sha256}\n" for asset in assets)
    identity = hashlib.sha256(fingerprint.encode("utf-8")).hexdigest()
    return Bundle("kokoro_scratch", root, KOKORO_TARGET, (*assets, metadata), identity)


class HashingReader:
    """Checks that the file sent after validation still matches the validated bytes."""

    def __init__(self, stream: BinaryIO):
        self.stream = stream
        self.digest = hashlib.sha256()

    def read(self, size: int = -1) -> bytes:
        data = self.stream.read(size)
        self.digest.update(data)
        return data


def write_tar(bundle: Bundle, destination: BinaryIO) -> None:
    # Stream the uncompressed archive: neither host nor device needs another tar copy.
    with tarfile.open(fileobj=destination, mode="w|", format=tarfile.USTAR_FORMAT) as archive:
        directories: set[str] = set()
        for asset in bundle.assets:
            require(asset.source.is_file() and not asset.source.is_symlink() and
                    asset.source.stat().st_size == asset.size, f"Asset changed after validation: {asset.name}")
            for parent in reversed(PurePosixPath(asset.name).parents):
                name = parent.as_posix()
                if name == "." or name in directories:
                    continue
                directory = tarfile.TarInfo(name)
                directory.type = tarfile.DIRTYPE
                directory.mode = 0o700
                archive.addfile(directory)
                directories.add(name)
            info = tarfile.TarInfo(asset.name)
            info.size = asset.size
            info.mode = 0o600
            info.mtime = 0
            with asset.source.open("rb") as stream:
                reader = HashingReader(stream)
                archive.addfile(info, reader)
                require(reader.digest.hexdigest() == asset.sha256,
                        f"Asset changed during upload: {asset.name}")


class Adb:
    def __init__(self, serial: str, package: str):
        self.prefix = ["adb", "-s", serial]
        self.package = package

    def run(self, *args: str) -> str:
        result = subprocess.run([*self.prefix, *args], check=True, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True)
        return result.stdout.strip()

    def app_command(self, script: str) -> list[str]:
        return [*self.prefix, "shell", "-T", shlex.join(["run-as", self.package, "sh", "-c", script])]

    def app_run(self, script: str) -> str:
        result = subprocess.run(self.app_command(script), check=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        return result.stdout.strip()

    def upload(self, bundle: Bundle, stage: str) -> None:
        script = f"umask 077; mkdir -p {shlex.quote(stage)} && tar -xf - -C {shlex.quote(stage)}"
        # Files hold only the small adb/tar diagnostic output, never model content.
        with tempfile.TemporaryFile() as output:
            process = subprocess.Popen(self.app_command(script), stdin=subprocess.PIPE,
                                       stdout=output, stderr=output)
            try:
                assert process.stdin is not None
                write_tar(bundle, process.stdin)
                process.stdin.close()
                status = process.wait()
                if status:
                    output.seek(0)
                    raise RuntimeError(f"ADB tar upload failed ({status}): {output.read(8192).decode(errors='replace')}")
            except BaseException:
                if process.stdin is not None and not process.stdin.closed:
                    process.stdin.close()
                if process.poll() is None:
                    process.terminate()
                process.wait()
                raise


def provision(adb: Adb, bundle: Bundle) -> None:
    suffix = uuid.uuid4().hex
    stage = bundle.target + ".stage-" + suffix
    backup = bundle.target + ".previous-" + suffix
    try:
        adb.upload(bundle, stage)
        # Validate the bytes on the device, including the manifest, before promotion.
        for asset in bundle.assets:
            relative = stage + "/" + asset.name
            actual = adb.app_run("sha256sum " + shlex.quote(relative)).split()
            require(bool(actual) and actual[0] == asset.sha256, f"Device SHA256 mismatch: {asset.name}")
        target_q, stage_q, backup_q = map(shlex.quote, (bundle.target, stage, backup))
        # Stage and target share a parent/filesystem. If the final rename fails, restore
        # the old model instead of leaving a missing or partially replaced bundle.
        adb.app_run(f"set -e; if [ -e {target_q} ]; then mv {target_q} {backup_q}; fi; "
                    f"if mv {stage_q} {target_q}; then rm -rf {backup_q}; "
                    f"else if [ -e {backup_q} ]; then mv {backup_q} {target_q}; fi; exit 1; fi")
    finally:
        # This path is generated, app-private, and unique to this upload.
        adb.app_run("rm -rf " + shlex.quote(stage))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit adb target; unused with --dry-run")
    parser.add_argument("--package", default="io.github.ninbyo02.lami.assistantprobe")
    parser.add_argument("--qwen-model-dir", type=Path)
    parser.add_argument("--supertonic-model-dir", type=Path)
    parser.add_argument("--kokoro-model-dir", type=Path, help="Verified Kokoro scratch bundle directory")
    parser.add_argument("--apk", type=Path, help="Optional debug APK to install with adb install -r")
    parser.add_argument("--dry-run", action="store_true", help="Validate local files only; never invoke adb")
    args = parser.parse_args(argv)
    try:
        require(bool(args.serial.strip()), "--serial cannot be empty")
        require(bool(re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", args.package)),
                "Invalid Android package name")
        require(any(directory is not None for directory in
                    (args.qwen_model_dir, args.supertonic_model_dir, args.kokoro_model_dir)),
                "Supply at least one of --qwen-model-dir, --supertonic-model-dir, or --kokoro-model-dir")
        if args.apk is not None:
            require(args.apk.is_file() and args.apk.stat().st_size > 0, "APK is missing or empty")
        bundles = []
        if args.qwen_model_dir is not None:
            bundles.append(validate_qwen(args.qwen_model_dir))
        if args.supertonic_model_dir is not None:
            bundles.append(validate_supertonic(args.supertonic_model_dir))
        if args.kokoro_model_dir is not None:
            bundles.append(validate_kokoro(args.kokoro_model_dir))
        print(json.dumps({"dry_run": args.dry_run, "serial": args.serial, "package": args.package,
                          "apk": str(args.apk) if args.apk else None,
                          "bundles": [bundle.summary() for bundle in bundles]}, indent=2), flush=True)
        if args.dry_run:
            return 0
        adb = Adb(args.serial, args.package)
        require(adb.run("get-state") == "device", "ADB target is not authorized/connected")
        if args.apk is not None:
            adb.run("install", "-r", str(args.apk.resolve()))
        adb.app_run("pwd")  # Fail before upload if this is not an installed debuggable app.
        adb.run("shell", shlex.join(["am", "force-stop", args.package]))
        for bundle in bundles:
            provision(adb, bundle)
            print(f"Provisioned {bundle.engine}: {bundle.target}", flush=True)
        return 0
    except (ValueError, OSError, subprocess.SubprocessError, RuntimeError) as error:
        print(f"Provisioning failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
