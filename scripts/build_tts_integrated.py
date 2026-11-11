#!/usr/bin/env python3
"""Build the TTS-integrated APK only with the pinned combined GPU/NPU runtime.

No device is contacted. --verify-only checks inputs without staging or building.
"""

import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
NATIVE_DIRECTORY = Path("app/src/customBuildExperimentDebug/jniLibs/arm64-v8a")
APK = Path("app/build/outputs/apk/standard/debug/app-standard-debug.apk")

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


def build_integrated(root=ROOT, native_dir=None, verify_only=False, run=None):
    root = Path(root).resolve(strict=True)
    target = root / NATIVE_DIRECTORY
    source = Path(native_dir).resolve(strict=True) if native_dir is not None else target
    expected = json.loads((root / "config/standard_gpu_npu_runtime.json").read_text())["libraries"]
    if not isinstance(expected, dict) or not expected:
        raise ValueError("Combined runtime manifest has no pinned libraries")
    # Read and validate every source byte before making any repository changes.
    files = verify_inputs(source, expected)
    summary = {"verified_inputs": str(source), "library_count": len(files), "verify_only": verify_only}
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
    run([str(root / "gradlew"), "--no-daemon",
         "-Plami.allowMissingQairt244Jni=false",
         "-Plami.standardGpuOpenClEnabled=true",
         "-Plami.voiceIntegrationTrial=false",
         "-Plami.residentCpDiagnosticPackaging=false",
         "-Plami.standardNpuRuntimeEnabled=false",
         "-Plami.gpuIdlePrewarmDiagnostic=false",
         "-Plami.imageBenchmark=false",
         ":app:assembleStandardDebug", ":app:assembleStandardDebugAndroidTest"],
        cwd=root, check=True)
    apk = root / APK
    if not apk.is_file():
        raise ValueError(f"Integrated APK was not produced: {apk}")
    # Keep this mandatory even though strict assemble also has a verification
    # dependency. A returned Gradle success alone is not delivery evidence.
    run([sys.executable, str(root / "scripts/stage_standard_gpu_opencl.py"),
         "--apk", str(apk)], cwd=root, check=True)
    summary.update({"staged_inputs": str(target), "apk": str(apk), "final_apk_verified": True})
    return summary


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-dir", type=Path, help="Directory containing the complete pinned arm64 runtime")
    parser.add_argument("--verify-only", action="store_true", help="Check inputs without staging or running Gradle")
    args = parser.parse_args(argv)
    try:
        summary = build_integrated(native_dir=args.native_dir, verify_only=args.verify_only)
    except (OSError, ValueError, subprocess.CalledProcessError) as failure:
        print(f"Integrated TTS build failed: {failure}", file=sys.stderr)
        return 1
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
