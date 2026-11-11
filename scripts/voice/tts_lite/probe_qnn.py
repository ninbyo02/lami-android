#!/usr/bin/env python3
"""Convert and compile a static vocoder for SM8750 without executing on a phone."""
import argparse
import json
import os
import subprocess
import time
from pathlib import Path

from melo_japanese import sha256


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--sdk-root", type=Path, required=True)
    ap.add_argument("--artifacts", type=Path, required=True)
    ap.add_argument("--decoder", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--image", default="lami-tts-lite-qairt:20261028")
    args = ap.parse_args()
    root, sdk, output = args.artifacts.resolve(), args.sdk_root.resolve(), args.output.resolve()
    decoder = args.decoder.resolve()
    def mounted(path):
        return "/work/" + path.relative_to(root).as_posix()
    mounted(output)
    mounted(decoder)
    if not (sdk / "bin/x86_64-linux-clang/qnn-onnx-converter").is_file():
        ap.error("SDK root does not contain qnn-onnx-converter")
    if not decoder.is_file() or not (root / "qairt-venv/bin/python").exists():
        ap.error("Prepare the decoder ONNX file and Python 3.10 QAIRT environment first")
    output.mkdir(parents=True, exist_ok=False)
    base = [
        "docker", "run", "--rm", "--network", "none", "--user", f"{os.getuid()}:{os.getgid()}",
        "--workdir", mounted(output),
        "-e", "PYTHONPATH=/sdk/lib/python", "-e", "LD_LIBRARY_PATH=/sdk/lib/x86_64-linux-clang",
        "-e", "QNN_SDK_ROOT=/sdk", "-e", "QAIRT_SDK_ROOT=/sdk",
        "-v", f"{root}:/work", "-v", f"{sdk}:/sdk:ro",
        "--entrypoint", "/bin/bash", args.image,
    ]
    binary = "/sdk/bin/x86_64-linux-clang/"
    py = "/work/qairt-venv/bin/python"
    out = mounted(output)
    steps = [
        ("convert", [py, binary + "qnn-onnx-converter", "--input_network", mounted(decoder),
                     "--output_path", out + "/decoder.cpp", "--float_bitwidth", "16"]),
        ("model_library", [py, binary + "qnn-model-lib-generator", "-c", out + "/decoder.cpp",
                           "-b", out + "/decoder.bin", "-o", out + "/model_libs", "-t", "x86_64-linux-clang"]),
        ("htp_context", [binary + "qnn-context-binary-generator",
                         "--model", out + "/model_libs/x86_64-linux-clang/libdecoder.so",
                         "--backend", "/sdk/lib/x86_64-linux-clang/libQnnHtp.so",
                         "--binary_file", "decoder-sm8750", "--output_dir", out + "/context",
                         "--htp_socs", "sm8750", "--vtcm_override", "8"]),
    ]
    report = {
        "schema_version": 1, "scope": "host_offline_sm8750_decoder_compilation",
        "sdk": sdk.name, "image": args.image, "onnx_sha256": sha256(decoder),
        "target": "SM8750", "precision": "FP16", "vtcm_mb": 8,
        "device_execution": "unverified", "npu_latency": "unverified", "steps": [],
    }
    for name, command in steps:
        started = time.perf_counter()
        # Positional arguments to bash prevent shell interpolation of command arguments.
        with (output / (name + ".log")).open("w") as log:
            result = subprocess.run(base + ["-c", 'exec "$@"', "qnn-probe", *command], stdout=log, stderr=subprocess.STDOUT)
        report["steps"].append({"name": name, "command": command, "returncode": result.returncode,
                                "elapsed_ms": (time.perf_counter() - started) * 1000,
                                "log_sha256": sha256(output / (name + ".log"))})
        report["status"] = "blocked" if result.returncode else "in_progress"
        (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report["steps"][-1]), flush=True)
        if result.returncode:
            return result.returncode
    # --htp_socs appends the SoC name to the cache filename in QAIRT 2.46.
    context = output / "context/decoder-sm8750.SM8750.bin"
    if not context.is_file() or not context.stat().st_size:
        raise ValueError("Compiler returned success without producing a context binary")
    report.update(status="compiled_not_device_validated", context_file=context.name, context_sha256=sha256(context),
                  context_bytes=context.stat().st_size)
    (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
