# QAIRT 2.44 NPU Memory Cleanup Profile

Artifact: `/home/sato/project/lami-android/artifacts/qairt244_npu_memory_cleanup_profile/20260523_091021`

## Smoke Outcome

```text
result=success
output=! How Hi
prompt=Hi
max_output_tokens=3
elapsed_ms=1423
decode_elapsed_ms=84
cleanup_elapsed_ms=110
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
tombstone_classification=stale-tombstone-ignored
```

## Memory Samples

Samples captured:

- before install / launch
- after install
- smoke before Activity launch
- immediately after smoke result file appeared
- 3 seconds after smoke
- 10 seconds after smoke

See `memory_summary.tsv` and `memory_delta.tsv`.

## Cleanup Evidence

Native diagnostics are summarized in `native_diag_tail.txt`. This first memory
profile is a baseline only; retained PSS from mapped QAIRT/QNN libraries or a
still-alive process is not treated as a leak by itself.

## Safety

This script used only the isolated short multi-token smoke Activity. It did not
connect NPU to the normal UI, did not set `selectedPath=npu` on the normal
route, did not call high-level `generateResponse`, and did not use streaming.
