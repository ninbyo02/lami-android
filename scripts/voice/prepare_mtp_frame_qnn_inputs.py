"""Prepare the 16 distinct captured CP inputs of one audio frame for QNN profiling.

Inputs are teacher-forced CPU capture; QNN outputs are not fed into the next step.
"""
import argparse
import json
from pathlib import Path

import numpy as np


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--captures", type=Path, required=True)
    parser.add_argument("--case", type=int, default=0)
    parser.add_argument("--frame", type=int, default=0)
    parser.add_argument("--cache-length", type=int, choices=(16, 32), default=32)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--device-input-dir", required=True)
    args = parser.parse_args()
    if args.case < 0 or args.frame < 0:
        parser.error("case and frame must be nonnegative")
    names = ["embeddings", "input_ids", "mask"] + [
        f"kv_cache_{kind}_{layer}"
        for kind in ("k", "v") for layer in range(5)
    ]
    expected = {"embeddings": (1, 1, 1024), "input_ids": (1,),
                "mask": (1, 1, 1, 32)}
    expected.update({name: (1, 32, 8, 128) for name in names[3:]})
    args.output_dir.mkdir(parents=True, exist_ok=True)
    lines = []
    for position in range(16):
        capture = args.captures / f"case-{args.case}-frame-{args.frame}-pos-{position}.npz"
        step_dir = args.output_dir / f"pos-{position:02d}"
        step_dir.mkdir(exist_ok=True)
        with np.load(capture, allow_pickle=False) as values:
            if not set(names).issubset(values.files):
                raise ValueError(f"Missing inputs in {capture}")
            if values["input_ids"].item() != position:
                raise ValueError(f"Position mismatch in {capture}")
            for name in names:
                value = values[name]
                if value.shape != expected[name]:
                    raise ValueError(f"Unexpected {name} shape in {capture}: {value.shape}")
                if value.dtype != (np.int32 if name == "input_ids" else np.float32):
                    raise ValueError(f"Unexpected {name} dtype in {capture}: {value.dtype}")
                if name != "input_ids" and not np.isfinite(value).all():
                    raise ValueError(f"Non-finite {name} in {capture}")
                if name == "mask":
                    value = value[..., :args.cache_length]
                elif name.startswith("kv_cache_"):
                    value = value[:, :args.cache_length]
                value.tofile(step_dir / f"serving_default_{name}.raw")
        lines.append(" ".join(
            f"{args.device_input_dir}/pos-{position:02d}/serving_default_{name}.raw"
            for name in names))
    (args.output_dir / "input-list.txt").write_text("\n".join(lines) + "\n")
    print(json.dumps({"frames": 1, "steps": 16, "cache_length": args.cache_length,
                      "input_list": str(args.output_dir / "input-list.txt"),
                      "device_input_dir": args.device_input_dir}, indent=2))


if __name__ == "__main__":
    main()
