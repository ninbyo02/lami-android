#!/usr/bin/env python3
"""Build the TTS-integrated APK only with the pinned combined GPU/NPU runtime.

No device is contacted. --verify-only checks inputs without staging or building.
"""

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[1]
NATIVE_DIRECTORY = Path("app/src/customBuildExperimentDebug/jniLibs/arm64-v8a")
APK = Path("app/build/outputs/apk/standard/debug/app-standard-debug.apk")
GENIEX_APK = Path("geniex-engine/build/outputs/apk/debug/geniex-engine-debug.apk")
GENIEX_SDK = Path("geniex-engine/build/verification/geniex-sdk.aar")
SHERPA_STREAM = Path("app/libs/sherpa-onnx-1.13.6-lami-stream.aar")
APK_ARM64 = "lib/arm64-v8a/"
AAR_ARM64 = "jni/arm64-v8a/"

# Use the same hashing/acceptance implementation as the Gradle input/APK gate.
_spec = importlib.util.spec_from_file_location(
    "tts_integrated_runtime_gate", Path(__file__).with_name("stage_standard_gpu_opencl.py"))
runtime_gate = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(runtime_gate)


def verify_inputs(native_dir, expected):
    files = {}
    for name in expected:
        if Path(name).name != name or not name.endswith(".so"):
            raise ValueError(f"Unsafe runtime manifest library name: {name}")
        file = native_dir / name
        if file.is_file():
            files[name] = file.read_bytes()
    errors = runtime_gate.validate(files, expected)
    if errors:
        raise ValueError("Combined GPU/NPU runtime verification failed: " + "; ".join(errors))
    return files


def digest_entry(archive, name):
    with archive.open(name) as entry:
        return hashlib.file_digest(entry, "sha256").digest()


def verify_geniex_packages(root):
    with (ZipFile(root / APK) as lami, ZipFile(root / SHERPA_STREAM) as sherpa,
          ZipFile(root / GENIEX_APK) as companion, ZipFile(root / GENIEX_SDK) as sdk):
        lami_entries = set(lami.namelist())
        companion_entries = set(companion.namelist())
        geniex_libs = {entry.removeprefix(AAR_ARM64) for entry in sdk.namelist()
                       if entry.startswith(AAR_ARM64) and entry.endswith(".so")}
        if not {"libnpu_jni.so", "libgeniex_plugin_llama_cpp.so", "libggml-htp-v79.so"} <= geniex_libs:
            raise ValueError("The GenieX SDK archive is missing the arm64 inference runtime")
        if any(entry.startswith(APK_ARM64 + "libgeniex") for entry in lami_entries):
            raise ValueError("GenieX native libraries must be packaged in the companion APK")
        for name in sorted(geniex_libs):
            if (APK_ARM64 + name not in companion_entries or
                    digest_entry(sdk, AAR_ARM64 + name) != digest_entry(companion, APK_ARM64 + name)):
                raise ValueError(f"GenieX companion APK is missing or modified {name}")
        for name in ("libonnxruntime.so", "libsherpa-onnx-jni.so"):
            if (APK_ARM64 + name not in lami_entries or
                    digest_entry(sherpa, AAR_ARM64 + name) != digest_entry(lami, APK_ARM64 + name)):
                raise ValueError(f"LAMI APK is missing or modified {name} from the streaming sherpa runtime")
        for name in ("libexecutorch.so", "liblami_voice_matrix.so", "liblami_kokoro_scratch.so"):
            if APK_ARM64 + name not in lami_entries:
                raise ValueError(f"LAMI APK is missing {name}")
    return len(geniex_libs)


def build_integrated(root=ROOT, native_dir=None, verify_only=False, run=None, geniex=False):
    root = Path(root).resolve(strict=True)
    target = root / NATIVE_DIRECTORY
    source = Path(native_dir).resolve(strict=True) if native_dir is not None else target
    expected = json.loads((root / "config/standard_gpu_npu_runtime.json").read_text())["libraries"]
    if not isinstance(expected, dict) or not expected:
        raise ValueError("Combined runtime manifest has no pinned libraries")
    # Read and validate every source byte before making any repository changes.
    files = verify_inputs(source, expected)
    summary = {"verified_inputs": str(source), "library_count": len(files), "verify_only": verify_only}
    if geniex and not (root / SHERPA_STREAM).is_file():
        raise ValueError("Build the Supertonic streaming AAR with scripts/build_supertonic_streaming_runtime.py first")
    if verify_only:
        return summary

    if not target.resolve().is_relative_to(root) or target.is_symlink():
        raise ValueError("Native staging directory must remain inside this repository")
    if source != target.resolve():
        target.mkdir(parents=True, exist_ok=True)
        # Stage verified snapshots, so concurrent changes to the source cannot
        # replace the bytes checked above. Never remove unrelated repository data.
        with tempfile.TemporaryDirectory(prefix=".tts-native-stage-", dir=target.parent) as folder:
            temporary = Path(folder)
            for name, data in files.items():
                (temporary / name).write_bytes(data)
            for name in files:
                os.replace(temporary / name, target / name)
    verify_inputs(target, expected)
    # The native snapshot can be hundreds of MB; Gradle does not need it in RAM.
    del files

    if run is None:
        run = subprocess.run
    # No forwarded Gradle arguments: these explicit values also override user or
    # environment project properties that could select a non-NPU smoke/trial APK.
    tasks = [":app:assembleStandardDebug", ":app:assembleStandardDebugAndroidTest"]
    if geniex:
        tasks.extend([":geniex-engine:assembleDebug", ":geniex-engine:stageGenieXSdkAar"])
    run([str(root / "gradlew"), "--no-daemon",
         "-Plami.allowMissingQairt244Jni=false",
         "-Plami.standardGpuOpenClEnabled=true",
         "-Plami.voiceIntegrationTrial=false",
         "-Plami.residentCpDiagnosticPackaging=false",
         "-Plami.standardNpuRuntimeEnabled=false",
         "-Plami.gpuIdlePrewarmDiagnostic=false",
         "-Plami.imageBenchmark=false", *tasks],
        cwd=root, check=True)
    apk = root / APK
    if not apk.is_file():
        raise ValueError(f"Integrated APK was not produced: {apk}")
    # Keep this mandatory even though strict assemble also has a verification
    # dependency. A returned Gradle success alone is not delivery evidence.
    run([sys.executable, str(root / "scripts/stage_standard_gpu_opencl.py"),
         "--apk", str(apk)], cwd=root, check=True)
    summary.update({"staged_inputs": str(target), "apk": str(apk), "final_apk_verified": True})
    if geniex:
        summary.update({"geniex_apk": str(root / GENIEX_APK),
                        "geniex_sdk_library_count": verify_geniex_packages(root)})
    return summary


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-dir", type=Path, help="Directory containing the complete pinned arm64 runtime")
    parser.add_argument("--verify-only", action="store_true", help="Check inputs without staging or running Gradle")
    parser.add_argument("--geniex", action="store_true", help="Build and verify the matching GenieX companion APK")
    args = parser.parse_args(argv)
    try:
        summary = build_integrated(native_dir=args.native_dir, verify_only=args.verify_only, geniex=args.geniex)
    except (OSError, ValueError, BadZipFile, KeyError, subprocess.CalledProcessError) as failure:
        print(f"Integrated TTS build failed: {failure}", file=sys.stderr)
        return 1
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
