"""Compile an isolated LiteRT MTP pilot and verify its dispatch structure.

Use a matching Qualcomm host SDK in LD_LIBRARY_PATH. NPU compile success does
not establish device inference, numerical parity, or speech quality.
"""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    from ai_edge_litert.aot.aot_compile import aot_compile
    from ai_edge_litert.aot.vendors.qualcomm.target import Target, SocModel
    from ai_edge_litert import schema_py_generated as schema
    from flatbuffers import flexbuffers

    result = aot_compile(str(args.model), output_dir=args.output_dir,
                         target=Target(SocModel.SM8750), keep_going=False)
    if result.failed_backends or len(result.models_with_backend) != 1:
        raise RuntimeError(f"NPU compilation did not produce one model: {result}")
    artifact = Path(result.models_with_backend[0][1].path)
    data = artifact.read_bytes()
    model = schema.Model.GetRootAsModel(data, 0)
    if model.SubgraphsLength() != 1 or model.Subgraphs(0).OperatorsLength() != 1:
        raise RuntimeError("Expected one fully delegated MTP graph")
    op = model.Subgraphs(0).Operators(0)
    code = model.OperatorCodes(op.OpcodeIndex())
    if code.CustomCode() != b"DISPATCH_OP":
        raise RuntimeError("Expected NPU DISPATCH_OP")
    options = flexbuffers.Loads(bytearray(op.CustomOptionsAsNumpy()))
    offset, size = options["bytecode_offset"], options["bytecode_size"]
    if offset < 0 or size <= 0 or offset + size > len(data):
        raise RuntimeError("Invalid embedded context range")
    context = args.output_dir / "mtp-qnn-context.bin"
    context.write_bytes(data[offset:offset + size])
    report = {"status": "sm8750_compiled_not_device_tested",
              "artifact": str(artifact), "artifact_bytes": len(data),
              "artifact_sha256": hashlib.sha256(data).hexdigest(),
              "dispatch_ops": 1, "remaining_cpu_ops": 0,
              "context": str(context), "context_bytes": size,
              "graph_name": options["name"]}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
