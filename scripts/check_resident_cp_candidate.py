#!/usr/bin/env python3
"""Host-only fail-closed guard for the candidate QNN CP context and metadata."""
import argparse
import hashlib
import json
from pathlib import Path
from check_resident_cp_aot_contract import validate

EXPECTED_SHA256 = "1ebaf036a376c886fe135afbf5f87dfb651be2804dc8675f1035be9ca7a92136"

def check(context: Path, metadata: Path, expected_hash: str = EXPECTED_SHA256):
    issues = []
    try:
        info = json.loads(metadata.read_text())
        issues.extend(validate(info))
    except (OSError, ValueError, KeyError, TypeError, IndexError) as e:
        issues.append(f"Cannot read valid metadata: {e}")
    try:
        digest = hashlib.sha256()
        with context.open("rb") as stream:
            while block := stream.read(1024 * 1024):
                digest.update(block)
        actual = digest.hexdigest()
        if actual != expected_hash:
            issues.append(f"Unexpected context hash: {actual}")
    except OSError as e:
        issues.append(f"Cannot read context: {e}")
    return issues

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("context", type=Path)
    parser.add_argument("metadata", type=Path)
    args = parser.parse_args()
    issues = check(args.context, args.metadata)
    if issues:
        print("REJECTED: " + "; ".join(issues))
        return 2
    print("CANDIDATE_ACCEPTED: SHA-256 and graph contract match (not NPU runtime/precision proof)")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
