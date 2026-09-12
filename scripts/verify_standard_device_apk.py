#!/usr/bin/env python3
"""Read-only verification of the repository's complete StandardDebug NPU stack."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

REQUIRED = (
    "libLiteRt.so", "libLiteRtDispatch_Qualcomm.so",
    "libLiteRtCompilerPlugin_Qualcomm.so", "libGemmaModelConstraintProvider.so",
    "libQnnSystem.so", "libQnnHtp.so", "libQnnHtpPrepare.so",
    "libQnnHtpV79Stub.so", "libQnnHtpV79Skel.so", "libQnnDsp.so",
    "libQnnGpu.so", "liblitertlm_jni.so", "liblami_qairt244_npu_jni.so",
)
MARKER = b"qairt244_kotlin_npu_conversation_sampler_v1"


def verify(apk: Path, native_dir: Path) -> dict:
    errors = []
    libraries = []
    with zipfile.ZipFile(apk) as archive:
        for name in REQUIRED:
            entry = "lib/arm64-v8a/" + name
            staged = native_dir / name
            if entry not in archive.namelist():
                errors.append("APK missing " + name)
                continue
            if not staged.is_file():
                errors.append("Staged reference missing " + name)
                continue
            data = archive.read(entry)
            digest = hashlib.sha256(data).hexdigest()
            if digest != hashlib.sha256(staged.read_bytes()).hexdigest():
                errors.append("APK differs from staged reference: " + name)
            if name == "liblitertlm_jni.so" and MARKER not in data:
                errors.append("Kotlin NPU Conversation sampler marker missing")
            libraries.append({"name": name, "sha256": digest})
    return {"passed": not errors, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "libraries": libraries, "errors": errors,
            "scope": "Packaging only; device inference validation is still required."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--native-dir", type=Path, required=True,
                        help="Previously verified complete arm64-v8a native staging directory")
    args = parser.parse_args()
    try:
        report = verify(args.apk, args.native_dir)
    except (OSError, zipfile.BadZipFile) as error:
        report = {"passed": False, "errors": [str(error)]}
    print(json.dumps(report, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
