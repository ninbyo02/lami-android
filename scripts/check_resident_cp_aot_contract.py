#!/usr/bin/env python3
"""Reject QNN AOT graphs incompatible with Rust's current head-free CP contract."""
import argparse
import json
from pathlib import Path

def validate(info):
    graphs = info["info"]["graphs"]
    if len(graphs) != 1:
        return ["Expected exactly one graph"]
    graph = graphs[0]["info"]
    inputs = {t["info"]["name"]: t["info"]["dimensions"] for t in graph["graphInputs"]}
    outputs = {t["info"]["name"]: t["info"]["dimensions"] for t in graph["graphOutputs"]}
    required_in = {
        "serving_default_embeddings": [1, 1, 1024],
        "serving_default_input_ids": [1],
        "serving_default_mask": [1, 1, 1, 32],
    }
    required_out = {"serving_default_hidden_output": [1, 1024]}
    for layer in range(5):
        for kind in ("k", "v"):
            stem = f"serving_default_kv_cache_{kind}_{layer}"
            required_in[stem] = [1, 32, 8, 128]
            required_out[stem + "_output"] = [1, 32, 8, 128]
    problems = []
    for direction, expected, actual in (("input", required_in, inputs), ("output", required_out, outputs)):
        for name, shape in expected.items():
            if actual.get(name) != shape:
                problems.append(f"{direction} {name}: expected {shape}, got {actual.get(name)}")
        for extra in sorted(set(actual) - set(expected)):
            problems.append(f"unexpected {direction}: {extra} {actual[extra]}")
    return problems

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("metadata", type=Path)
    args = parser.parse_args()
    problems = validate(json.loads(args.metadata.read_text()))
    if problems:
        print("INCOMPATIBLE: current Rust CP driver must not load this AOT")
        print("\n".join(problems))
        return 2
    print("COMPATIBLE: graph tensor names/shapes match Rust CP contract (not a runtime/accuracy proof)")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
