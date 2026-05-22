# QAIRT 2.44 Token Timing Verifier Result

Date: 2026-05-23

Scope: `customBuildExperimentDebug` isolated lower-level NPU verifier. The
normal UI path remains disconnected.

## Implementation Status

The token timing verifier native instrumentation was added to the external
LiteRT-LM checkout and rebuilt as a QAIRT 2.44 custom stack.

Verifier build artifact:

```text
artifacts/qairt244_token_timing_verifier_build/20260523_060634/
```

Preflight artifact:

```text
artifacts/qairt244_token_timing_verifier/20260523_061525/
```

The composed verifier artifact keeps the previous known-good baseline for:

- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so` with `DT_NEEDED [libLiteRt.so]`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- QAIRT 2.44 QNN runtime libs

Only `liblitertlm_jni.so` was replaced by the rebuilt JNI library containing:

```text
qairt244_token_timing_verifier_v1
```

External LiteRT-LM patch snapshot:

```text
artifacts/qairt244_token_timing_verifier_build/20260523_060634/metadata/litertlm_external_status.txt
artifacts/qairt244_token_timing_verifier_build/20260523_060634/metadata/litertlm_external_diff.patch
```

## Recorded Fields

The native result writer now records:

- `prompt=Hi`
- `prompt_bytes=2`
- `prompt_token_count=unavailable`
- `prompt_token_count_source=not_exposed_by_lower_level_entrypoint`
- `max_output_tokens=1`
- `output_bytes`
- `output_token_count=unavailable`
- `output_token_count_source=not_exposed_by_RunDecode_response`
- `elapsed_ms`
- `model_assets_elapsed_ms`
- `engine_settings_elapsed_ms`
- `engine_create_elapsed_ms`
- `session_create_elapsed_ms`
- `prefill_elapsed_ms`
- `decode_elapsed_ms`
- `cleanup_elapsed_ms`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`

Token counts are explicitly marked unavailable because the lower-level
entrypoint does not expose a tokenizer count API and `RunDecode` returns text,
not a decoded token count. No token count is inferred from bytes or text.

## Execution Status

Executed exactly once on the Nubia SM8750 device after it was visible in
`adb devices` as `NX733J`.

Execution artifact:

```text
artifacts/qairt244_token_timing_verifier/20260523_062321/
```

Run result:

```text
classification=executed
executed=true
marker=qairt244_lower_level_single_token_smoke_v1
verifier_marker=qairt244_token_timing_verifier_v1
result=success
prompt=Hi
prompt_bytes=2
prompt_token_count=unavailable
prompt_token_count_source=not_exposed_by_lower_level_entrypoint
max_output_tokens=1
output_bytes=1
output_token_count=unavailable
output_token_count_source=not_exposed_by_RunDecode_response
elapsed_ms=1053
model_assets_elapsed_ms=0
engine_settings_elapsed_ms=0
engine_create_elapsed_ms=905
session_create_elapsed_ms=0
prefill_elapsed_ms=13
decode_elapsed_ms=22
cleanup_elapsed_ms=111
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
output=!
```

The app-private native diagnostics for the same run show the QNN/HTP/V79 path:

- `QnnDevice_create done. device = 0x1. status 0x0`
- `First connection to QNN stub established`
- `Effective cdsp_id is: 3`
- `transport run [status = 0]`
- `QnnContext_createFromBinary started`
- `DSP ARCH graph is set to 79`

Tombstone freshness:

```text
artifacts/qairt244_token_timing_verifier/20260523_062321/stale_tombstone_note.md
```

Classification: `stale-tombstone-ignored`. The collector selected an older
SIGABRT tombstone that does not contain the current run id. The current app
private result and native diag report success, and the process remained alive
after the verifier run.

Safety gates confirmed:

- `customBuildExperimentDebug` package
- prompt fixed to `Hi`
- `maxOutputTokens=1`
- lower-level entrypoint marker
- verifier marker in the rebuilt artifact
- normal UI disconnected
- no high-level `generateResponse`
- no normal UI NPU route

## Next Step

Keep the NPU path isolated. The next useful step is a separate, explicitly
approved verifier for a slightly richer decode boundary or memory/cleanup
profile before any normal UI wiring is considered.
