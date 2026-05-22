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

Not executed in this commit. `adb devices` returned no connected device, and
the previous Nubia endpoint `192.168.52.52:41251` refused connection. Because
the verifier must run on the Nubia SM8750 device, the one allowed generation run
was not attempted.

Preflight result:

```text
classification=entrypoint-implemented-not-executed
executed=false
reason=Static markers are present, but --run was not requested.
```

Safety gates passed for:

- `customBuildExperimentDebug` package
- prompt fixed to `Hi`
- `maxOutputTokens=1`
- lower-level entrypoint marker
- verifier marker in the rebuilt artifact
- normal UI disconnected

## Next Command

Run exactly once after the Nubia device is visible in `adb devices`:

```bash
bash scripts/run_qairt244_lower_level_single_token_smoke.sh \
  artifacts/qairt244_token_timing_verifier_build/20260523_060634 \
  --run \
  --verifier
```

Expected artifact:

```text
artifacts/qairt244_token_timing_verifier/<timestamp>/
```

The runner keeps stale tombstone classification and writes
`stale_tombstone_note.md` for the verifier artifact.
