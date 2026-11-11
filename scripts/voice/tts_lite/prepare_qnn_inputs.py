#!/usr/bin/env python3
"""Pack ONNX probes into the compiled QNN tensor layout and native precision."""
import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort

from melo_japanese import sha256


def pack_native(source, tensor):
    dtypes = {0x0216: np.dtype("<f2"), 0x0232: np.dtype("<f4")}
    if tensor["data_type"] not in dtypes:
        raise ValueError("Unsupported native QNN tensor type")
    order = tensor.get("permute_order_to_src", list(range(source.ndim)))
    if sorted(order) != list(range(source.ndim)):
        raise ValueError("Invalid QNN source permutation")
    native = np.ascontiguousarray(source.transpose(np.argsort(order)), dtype=dtypes[tensor["data_type"]])
    if list(native.shape) != tensor["dims"] or not np.isfinite(native).all():
        raise ValueError("Native QNN tensor dimensions or values are invalid")
    restored = native.transpose(order).astype(np.float32)
    if restored.shape != source.shape:
        raise ValueError("Native input roundtrip changed the source shape")
    return native, restored


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--onnx-dir", type=Path, required=True)
    ap.add_argument("--compiled-dir", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    exported = json.loads((args.onnx_dir / "report.json").read_text())
    compiled = json.loads((args.compiled_dir / "report.json").read_text())
    model_path = args.onnx_dir / "decoder.onnx"
    if sha256(model_path) != exported["onnx_sha256"] or compiled["onnx_sha256"] != exported["onnx_sha256"]:
        raise ValueError("ONNX export and compiled model provenance do not match")
    tensors = json.loads((args.compiled_dir / "decoder_net.json").read_text())["graph"]["tensors"]
    inputs = {row["name"]: row["shape"] for row in exported["inputs"]}
    if {name for name, row in tensors.items() if row["type"] == 0} != set(inputs):
        raise ValueError("Compiled graph input names differ from ONNX")
    args.output.mkdir(parents=True, exist_ok=False)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    session = ort.InferenceSession(str(model_path), sess_options=options, providers=["CPUExecutionProvider"])
    rows, native_lines = [], []
    for index, line in enumerate((args.onnx_dir / "input_list.txt").read_text().splitlines()):
        paths = dict(item.split(":=", 1) for item in line.split())
        if set(paths) != set(inputs):
            raise ValueError("Probe input list has missing or extra inputs")
        source_feeds, restored_feeds, packed_fields, observations = {}, {}, [], []
        for name, shape in inputs.items():
            source = np.fromfile(paths[name], dtype=np.float32).reshape(shape)
            native, restored = pack_native(source, tensors[name])
            destination = args.output / f"probe-{index}-{name}.raw"
            native.tofile(destination)
            # Validate serialized bytes as the native model will receive them.
            unpacked = np.fromfile(destination, dtype=native.dtype).reshape(native.shape)
            order = tensors[name].get("permute_order_to_src", list(range(source.ndim)))
            if not np.array_equal(unpacked.transpose(order).astype(np.float32), restored):
                raise ValueError("Native file serialization roundtrip failed")
            source_feeds[name], restored_feeds[name] = source, restored
            packed_fields.append(name + ":=" + str(destination.resolve()))
            observations.append({"name": name, "source_shape": shape, "native_shape": list(native.shape),
                                 "data_type": tensors[name]["data_type"], "bytes": destination.stat().st_size,
                                 "sha256": sha256(destination),
                                 "input_rounding_max_error": float(np.max(np.abs(source - restored)))})
        original = session.run(None, source_feeds)[0]
        rounded = session.run(None, restored_feeds)[0]
        rows.append({"probe": index, "inputs": observations,
                     "onnx_output_error_from_input_rounding": float(np.max(np.abs(original - rounded)))})
        native_lines.append(" ".join(packed_fields))
    (args.output / "input_list_native.txt").write_text("\n".join(native_lines) + "\n")
    report = {"schema_version": 1, "scope": "compiled_qnn_input_layout_and_precision_preparation",
              "onnx_sha256": exported["onnx_sha256"], "context_sha256": compiled["context_sha256"],
              "model_metadata_sha256": sha256(args.compiled_dir / "decoder_net.json"),
              "qnn_net_run_input_flag": "--use_native_input_files", "probes": rows,
              "native_input_list_sha256": sha256(args.output / "input_list_native.txt"),
              "device_execution": "unverified",
              "notes": ["The compiler changes latent NCT layout into NTC; source ONNX raw files cannot be used unchanged.",
                        "Files use the compiled tensor's native precision; use --use_native_input_files with this list.",
                        "The ONNX check isolates input rounding only; HTP arithmetic and FP16 weights are not simulated."]}
    (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
