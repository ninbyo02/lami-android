# QAIRT 2.44 Lower-Level Single-Token Smoke

Artifact: `/home/sato/project/lami-android/artifacts/qairt244_token_timing_verifier/20260523_062321`

Build artifact: `artifacts/qairt244_token_timing_verifier_build/20260523_060634`

## Outcome

```text
classification=executed
run_id=1779485001728
result=success
prompt=Hi
max_output_tokens=1
elapsed_ms=1053
output=!
prompt_bytes=2
prompt_token_count=unavailable
output_bytes=1
output_token_count=unavailable
engine_create_elapsed_ms=905
session_create_elapsed_ms=0
prefill_elapsed_ms=13
decode_elapsed_ms=22
cleanup_elapsed_ms=111
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
timeout=false;waited_seconds=2
tombstone_classification=stale-tombstone-ignored
```

Native diag is expected to include:

```text
before RunDecode SetMaxOutputTokens(1)
success output_candidates=1 output_bytes=...
```

Tombstone freshness note:

```text
/home/sato/project/lami-android/artifacts/qairt244_token_timing_verifier/20260523_062321/stale_tombstone_note.md
```

## Safety

- `customBuildExperimentDebug` only
- prompt fixed to `Hi`
- max output tokens fixed to `1`
- no normal UI NPU route
- no `Conversation`
- no Kotlin/public `Session` object
- no high-level `generateResponse`
