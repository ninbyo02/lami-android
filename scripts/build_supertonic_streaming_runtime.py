#!/usr/bin/env python3
"""Build the pinned LAMI causal-vocoder JNI and overlay it on the original sherpa AAR."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile


REPO = Path(__file__).resolve().parents[1]
SOURCE_URL = "https://github.com/k2-fsa/sherpa-onnx.git"
SOURCE_TAG = "v1.13.6"
SOURCE_COMMIT = "1cb484af5e69d3c7803c1eb0b3b5ab8041e0e911"
ORT_VERSION = "1.27.1"
NDK_VERSION = "28.2.13676358"
ORIGINAL_AAR_SHA256 = "0012d9a28f15bd6fb966b62b70a75da3990512fdccce28b83098248ce4be1698"
MARKER = "Java_io_github_ninbyo02_lami_tts_LamiSupertonicStreamingRuntime_version"
PATCH = REPO / "patches/sherpa-onnx-1.13.6-lami-causal-vocoder-stream.patch"
HEADERS = {
    "onnxruntime_c_api.h": "523131bab81173fe287c578f7dda344e1dc5c749fa3f70b55fbbf8f210850313",
    "onnxruntime_cxx_api.h": "c5f9bb73d7674cf99a197b383a144eb2a5eda71cc060193a86c7d24e6fa36ed7",
    "onnxruntime_cxx_inline.h": "114b4d008351eaa4462560438474cebd104f74259cc8af607acc0b97453200d2",
    "onnxruntime_float16.h": "88b242845d25981633a0bbd1c148e273cf8bfb016ea3f57c4af41a06530f72b0",
    "onnxruntime_run_options_config_keys.h": "973037c14b0f1af0854ae25cd676776f79dbcfd2a6791f6ee17a5374c2a62550",
    "onnxruntime_session_options_config_keys.h": "63c790b4e5f5416e7115dee7e0d818a7fd3924451fb2339102a086f7e50c011e",
    "onnxruntime_ep_c_api.h": "f65ac849718cabe3ff4902698dd2de643c3b3dbe0010242b2b0407548e7ee00c",
    "nnapi_provider_factory.h": "49f7e0952e57b7e2942f69d8df61a9d99836f05b49a89700e1f5a16278c945e2",
}
BUILD_OPTIONS = {
    "CMAKE_BUILD_TYPE": "Release", "ANDROID_PLATFORM": "android-34", "ANDROID_STL": "c++_static",
    "BUILD_SHARED_LIBS": "ON", "SHERPA_ONNX_ENABLE_JNI": "ON", "SHERPA_ONNX_ENABLE_TTS": "ON",
    "SHERPA_ONNX_ENABLE_BINARY": "OFF", "SHERPA_ONNX_ENABLE_TESTS": "OFF",
    "SHERPA_ONNX_ENABLE_PORTAUDIO": "OFF", "SHERPA_ONNX_ENABLE_WEBSOCKET": "OFF",
    "SHERPA_ONNX_ENABLE_PYTHON": "OFF", "SHERPA_ONNX_ENABLE_C_API": "OFF",
    "SHERPA_ONNX_BUILD_C_API_EXAMPLES": "OFF", "BUILD_TESTING": "OFF",
    "BUILD_ESPEAK_NG_EXE": "OFF", "BUILD_ESPEAK_NG_TESTS": "OFF",
    "BUILD_PIPER_PHONMIZE_EXE": "OFF", "BUILD_PIPER_PHONMIZE_TESTS": "OFF",
    "SHERPA_ONNX_USE_PRE_INSTALLED_ONNXRUNTIME_IF_AVAILABLE": "ON",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def sha256_stream(stream) -> str:
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
    return digest.hexdigest()


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return sha256_stream(stream)


def run(command: list[str], *, cwd: Path | None = None, env: dict | None = None,
        capture: bool = False) -> str:
    result = subprocess.run(command, cwd=cwd, env=env, check=True, text=True,
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else ""


def prepare_source(source: Path, package_only: bool) -> None:
    if not source.exists():
        require(not package_only, "--package-only requires an existing patched source/build directory")
        source.parent.mkdir(parents=True, exist_ok=True)
        run(["git", "clone", "--depth", "1", "--branch", SOURCE_TAG, SOURCE_URL, str(source)])
    require((source / ".git").exists(), f"Not a sherpa Git checkout: {source}")
    require(run(["git", "rev-parse", "HEAD"], cwd=source, capture=True) == SOURCE_COMMIT,
            f"Source must be the exact {SOURCE_TAG} commit {SOURCE_COMMIT}")
    applied = subprocess.run(["git", "apply", "--reverse", "--check", str(PATCH)], cwd=source,
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    if not applied:
        require(not package_only, "Existing source does not contain the expected LAMI patch")
        run(["git", "apply", "--check", str(PATCH)], cwd=source)
        run(["git", "apply", str(PATCH)], cwd=source)
    # Refuse unrelated tracked edits, which would otherwise be hidden behind the
    # source commit pin. Untracked build caches and measurements are harmless.
    actual = run(["git", "diff", "--no-ext-diff", "--binary"], cwd=source, capture=True)
    require(actual.strip() == PATCH.read_text().strip(),
            "Source has tracked changes beyond the exact checked-in LAMI patch")
    require(not run(["git", "diff", "--cached", "--name-only"], cwd=source, capture=True),
            "Source checkout has staged changes; use a dedicated clean build cache")


def header_url(name: str) -> str:
    relative = ("providers/nnapi/" if name == "nnapi_provider_factory.h" else "session/") + name
    return f"https://raw.githubusercontent.com/microsoft/onnxruntime/v{ORT_VERSION}/include/onnxruntime/core/{relative}"


def prepare_dependencies(source: Path, original_aar: Path, abis: list[str], package_only: bool) -> dict:
    dependencies = source / "lami-runtime-deps"
    include = dependencies / "include"
    include.mkdir(parents=True, exist_ok=True)
    for name, expected in HEADERS.items():
        target = include / name
        if not target.is_file():
            require(not package_only, f"Missing cached ORT header: {target}")
            with urllib.request.urlopen(header_url(name), timeout=30) as response:
                data = response.read()
            require(hashlib.sha256(data).hexdigest() == expected, f"Downloaded ORT header hash mismatch: {name}")
            target.write_bytes(data)
        require(sha256(target) == expected, f"Cached ORT header hash mismatch: {name}")
    ort_hashes = {}
    with zipfile.ZipFile(original_aar) as archive:
        for abi in abis:
            entry = f"jni/{abi}/libonnxruntime.so"
            with archive.open(entry) as stream:
                expected = sha256_stream(stream)
            ort = dependencies / abi / "libonnxruntime.so"
            if not ort.is_file():
                require(not package_only, f"Missing cached original ORT library: {ort}")
                ort.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(entry) as stream, ort.open("wb") as output:
                    shutil.copyfileobj(stream, output, 1024 * 1024)
            require(sha256(ort) == expected, f"ORT dependency differs from original AAR: {abi}")
            ort_hashes[abi] = expected
    return {"include": include, "root": dependencies, "ort_sha256": ort_hashes}


def build_abi(source: Path, ndk: Path, dependencies: dict, abi: str, jobs: int) -> None:
    build = source / f"build-lami-{abi}"
    env = dict(os.environ)
    env["SHERPA_ONNXRUNTIME_INCLUDE_DIR"] = str(dependencies["include"])
    env["SHERPA_ONNXRUNTIME_LIB_DIR"] = str(dependencies["root"] / abi)
    options = {**BUILD_OPTIONS, "ANDROID_ABI": abi,
               "CMAKE_TOOLCHAIN_FILE": str(ndk / "build/cmake/android.toolchain.cmake")}
    run(["cmake", "-S", str(source), "-B", str(build), "-G", "Ninja",
         *(f"-D{name}={value}" for name, value in options.items())], env=env)
    run(["cmake", "--build", str(build), "--target", "sherpa-onnx-jni", "--parallel", str(jobs)], env=env)


def llvm_tools(ndk: Path) -> Path:
    hosts = list((ndk / "toolchains/llvm/prebuilt").glob("*/bin"))
    require(len(hosts) == 1, "Unable to locate a unique NDK LLVM toolchain")
    return hosts[0]


def inspect_jni(source: Path, ndk: Path, abi: str, library: Path | None = None) -> tuple[Path, dict]:
    library = library or source / f"build-lami-{abi}/lib/libsherpa-onnx-jni.so"
    if not library.is_file():
        # Upstream generator layouts can place the target beside its CMakeLists.
        library = source / f"build-lami-{abi}/sherpa-onnx/jni/libsherpa-onnx-jni.so"
    require(library.is_file() and library.stat().st_size > 0, f"JNI build output is absent: {abi}")
    tools = llvm_tools(ndk)
    symbols = run([str(tools / "llvm-nm"), "-D", "--defined-only", "--format=posix", str(library)], capture=True)
    require(any(line.split()[0].split("@")[0] == MARKER for line in symbols.splitlines() if line.split()),
            f"JNI does not export the streaming capability marker: {abi}")
    program_headers = run([str(tools / "llvm-readelf"), "-lW", str(library)], capture=True)
    alignments = [int(line.split()[-1], 0) for line in program_headers.splitlines() if line.strip().startswith("LOAD ")]
    require(bool(alignments) and all(value >= 16_384 for value in alignments),
            f"JNI LOAD segments are not aligned for 16KB pages: {abi}: {alignments}")
    elf_header = run([str(tools / "llvm-readelf"), "-h", str(library)], capture=True)
    machine = re.search(r"Machine:\s*(.+)", elf_header)
    require(machine is not None, f"Cannot identify JNI architecture: {abi}")
    expected_machine = "AArch64" if abi == "arm64-v8a" else "X86-64"
    require(expected_machine.lower() in machine.group(1).lower(), f"JNI architecture does not match {abi}")
    dynamic = run([str(tools / "llvm-readelf"), "-d", str(library)], capture=True)
    needed = re.findall(r"Shared library: \[([^\]]+)\]", dynamic)
    require("libonnxruntime.so" in needed, f"JNI is not linked to the original shared ORT: {abi}")
    require(not any(name.startswith("libsherpa-onnx-") for name in needed),
            "JNI unexpectedly depends on a separate sherpa shared core/API library")
    return library, {"sha256": sha256(library), "bytes": library.stat().st_size,
                     "machine": machine.group(1), "load_segment_alignment": alignments,
                     "needed": needed, "streaming_marker": MARKER}


def stripped_jni(source: Path, ndk: Path, abi: str) -> tuple[Path, dict]:
    original, unstripped = inspect_jni(source, ndk, abi)
    output = source / f"lami-runtime-deps/stripped/{abi}/libsherpa-onnx-jni.so"
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(original, output)
    run([str(llvm_tools(ndk) / "llvm-strip"), "--strip-unneeded", str(output)])
    library, metadata = inspect_jni(source, ndk, abi, output)
    metadata["unstripped_sha256"] = unstripped["sha256"]
    metadata["unstripped_bytes"] = unstripped["bytes"]
    metadata["strip_options"] = ["--strip-unneeded"]
    cache = source / f"build-lami-{abi}/CMakeCache.txt"
    require(cache.is_file(), f"Missing CMake build provenance: {abi}")
    settings = {}
    for line in cache.read_text().splitlines():
        match = re.match(r"([^#/:][^:]*):[^=]+=(.*)", line)
        if match:
            settings[match.group(1)] = match.group(2)
    recorded = {*BUILD_OPTIONS, "ANDROID_ABI", "CMAKE_CXX_FLAGS", "CMAKE_CXX_FLAGS_RELEASE",
                "CMAKE_C_FLAGS", "CMAKE_C_FLAGS_RELEASE", "CMAKE_SHARED_LINKER_FLAGS"}
    metadata["cmake_settings"] = {name: settings[name] for name in sorted(recorded) if name in settings}
    metadata["cmake_cache_sha256"] = sha256(cache)
    return library, metadata


def package_aar(original: Path, output: Path, libraries: dict[str, Path]) -> None:
    require(original.resolve() != output.resolve(), "Refusing to overwrite the original sherpa AAR")
    output.parent.mkdir(parents=True, exist_ok=True)
    replacements = {f"jni/{abi}/libsherpa-onnx-jni.so": path for abi, path in libraries.items()}
    temporary = tempfile.NamedTemporaryFile(prefix=output.name + ".", suffix=".tmp", dir=output.parent, delete=False)
    temporary.close()
    temporary_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(original) as source, zipfile.ZipFile(temporary_path, "w") as destination:
            require(set(replacements).issubset(source.namelist()), "Original AAR lacks a requested JNI ABI")
            destination.comment = source.comment
            for entry in source.infolist():
                replacement = replacements.get(entry.filename)
                # Preserve entry names, timestamps, permissions, compression and
                # all untouched payloads. The original archive is never changed.
                with (replacement.open("rb") if replacement else source.open(entry)) as stream:
                    with destination.open(entry, "w") as target:
                        shutil.copyfileobj(stream, target, 1024 * 1024)
        with zipfile.ZipFile(original) as source, zipfile.ZipFile(temporary_path) as destination:
            require(source.namelist() == destination.namelist(), "Packaged AAR entry list changed")
            for entry in source.namelist():
                with destination.open(entry) as stream:
                    actual = sha256_stream(stream)
                if entry in replacements:
                    expected = sha256(replacements[entry])
                else:
                    with source.open(entry) as stream:
                        expected = sha256_stream(stream)
                require(actual == expected, f"Packaged AAR payload differs unexpectedly: {entry}")
        temporary_path.replace(output)
    finally:
        temporary_path.unlink(missing_ok=True)


def default_ndk() -> Path:
    configured = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT") or os.environ.get("ANDROID_NDK")
    sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
    return Path(configured) if configured else sdk / "ndk" / NDK_VERSION


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=REPO / "artifacts/supertonic-streaming-runtime/source")
    parser.add_argument("--ndk", type=Path, default=default_ndk())
    parser.add_argument("--jobs", type=int, default=12, help="Parallel workers for each sequential ABI build")
    parser.add_argument("--abis", nargs="+", default=["arm64-v8a", "x86_64"], help="Space- or comma-separated ABI names")
    parser.add_argument("--original-aar", type=Path, default=REPO / "app/libs/sherpa-onnx-1.13.6.aar")
    parser.add_argument("--output", type=Path, default=REPO / "app/libs/sherpa-onnx-1.13.6-lami-stream.aar")
    parser.add_argument("--metadata", type=Path, help="Build manifest; default OUTPUT.build.json")
    parser.add_argument("--package-only", action="store_true", help="Verify and reuse compiled JNI; do not configure, fetch or compile")
    args = parser.parse_args(argv)
    try:
        abis = list(dict.fromkeys(abi for value in args.abis for abi in value.split(",")))
        require(bool(abis) and set(abis).issubset({"arm64-v8a", "x86_64"}), "Supported streaming ABIs: arm64-v8a, x86_64")
        require(args.jobs > 0, "--jobs must be positive")
        source, ndk = args.source_dir.resolve(), args.ndk.resolve()
        require((ndk / "source.properties").is_file(), "Android NDK source.properties is absent")
        revision = re.search(r"Pkg.Revision\s*=\s*(\S+)", (ndk / "source.properties").read_text())
        require(revision is not None and revision.group(1) == NDK_VERSION, f"Use pinned Android NDK {NDK_VERSION}")
        require(args.original_aar.is_file() and sha256(args.original_aar) == ORIGINAL_AAR_SHA256,
                "Original sherpa AAR does not match the pinned upstream archive")
        prepare_source(source, args.package_only)
        dependencies = prepare_dependencies(source, args.original_aar, abis, args.package_only)
        if not args.package_only:
            for abi in abis:
                build_abi(source, ndk, dependencies, abi, args.jobs)
        libraries, native_metadata = {}, {}
        for abi in abis:
            libraries[abi], native_metadata[abi] = stripped_jni(source, ndk, abi)
        package_aar(args.original_aar, args.output, libraries)
        manifest = {"source_url": SOURCE_URL, "source_tag": SOURCE_TAG, "source_commit": SOURCE_COMMIT,
                    "patch": str(PATCH.relative_to(REPO)), "patch_sha256": sha256(PATCH),
                    "script_sha256": sha256(Path(__file__)), "ndk_version": NDK_VERSION,
                    "android_api": 34, "generator": "Ninja", "requested_build_options": BUILD_OPTIONS,
                    "onnxruntime_version": ORT_VERSION, "header_sha256": HEADERS,
                    "original_aar_sha256": ORIGINAL_AAR_SHA256, "ort_sha256": dependencies["ort_sha256"],
                    "streaming_abis": native_metadata, "output": str(args.output.resolve()),
                    "output_sha256": sha256(args.output), "output_bytes": args.output.stat().st_size,
                    "only_replaced_payloads": [f"jni/{abi}/libsherpa-onnx-jni.so" for abi in abis],
                    "unselected_abis": "retain original non-streaming JNI", "package_only": args.package_only}
        metadata = args.metadata or args.output.with_suffix(".build.json")
        metadata.parent.mkdir(parents=True, exist_ok=True)
        metadata.write_text(json.dumps(manifest, indent=2) + "\n")
        print(json.dumps({"output": str(args.output), "sha256": manifest["output_sha256"],
                          "metadata": str(metadata), "streaming_abis": abis}, indent=2))
        return 0
    except (ValueError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        print(f"Supertonic runtime build failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
