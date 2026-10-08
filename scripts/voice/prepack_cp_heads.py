"""Create isolated, bit-preserving CP packed4 heads and a manifest overlay.

Copy output heads into a diagnostic bundle and merge the overlay sha256 entries
and cp_head_prepacked4 flag into its manifest. Original files remain unchanged.
"""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np


def prepack(root: Path, output: Path):
    manifest = json.loads((root / "voice-text-bundle.json").read_text())
    output.mkdir(parents=True, exist_ok=False)
    hashes = {}
    report = []
    for index in range(15):
        name = f"cp.head.{index}.f32"
        source = (root / name).read_bytes()
        if len(source) != 2048 * 1024 * 4:
            raise ValueError(f"Invalid tensor size: {name}")
        if hashlib.sha256(source).hexdigest() != manifest["sha256"][name]:
            raise ValueError(f"Source hash mismatch: {name}")
        words = np.frombuffer(source, dtype="<u4").reshape(512, 4, 1024)
        packed = words.transpose(0, 2, 1).copy()
        if packed.transpose(0, 2, 1).copy().tobytes() != source:
            raise ValueError(f"Inverse mismatch: {name}")
        target = f"cp.head.{index}.packed4.f32"
        data = packed.tobytes()
        (output / target).write_bytes(data)
        hashes[target] = hashlib.sha256(data).hexdigest()
        report.append({"source": name, "source_sha256": manifest["sha256"][name],
                       "target": target, "sha256": hashes[target],
                       "bytes": len(data), "inverse_bit_exact": True})
    overlay = {"cp_head_prepacked4": True, "sha256": hashes}
    (output / "manifest-overlay.json").write_text(json.dumps(overlay, indent=2) + "\n")
    (output / "prepack-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"heads": len(report), "bytes": sum(x["bytes"] for x in report),
                      "inverse_bit_exact": True, "output": str(output)}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    prepack(args.root, args.output)
