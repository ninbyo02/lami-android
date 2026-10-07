# Main grouped attention pilot (2026-10-08)

Add opt-in `--grouped-attention` to the isolated INT8 main exporter. For fixed single-token decode, reshape query heads into K/V-head groups and multiply directly against the existing K/V cache, avoiding repeated K/V heads. Original weights, cache updates, mask, scaling, FP32 softmax and output layout remain unchanged. Default export behavior is unchanged; this flag does not update Android bundle selection.

Candidate configuration: INT8 dynamic per-channel weights, cache128, existing limited Linear/BMM/Softmax/Permute XNNPACK partitioning. Verification compares the same configuration without grouped attention at positions 0, 32 and 127, then runs both complete feedback texts with ordinary CP cache16. Exact output comparisons are required before considering Android diagnostics. Host CPU performance does not establish device performance.

Status: export and checks in progress. No speedup, audio equivalence or realtime acceptance claimed yet. Host artifacts: `/home/sato/project/lami-android-voice-dataset/qat/main-grouped-pilot-20261008/`.

Static validation: Python syntax compilation and `git diff --check` passed. Android default and runtime are unchanged.
