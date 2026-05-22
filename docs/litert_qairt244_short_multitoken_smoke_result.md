# QAIRT 2.44 Short Multi-Token Smoke Result

Date: 2026-05-23

Scope: `customBuildExperimentDebug` isolated lower-level short multi-token
smoke. This is not normal UI integration.

## Artifact

```text
build: artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/
run:   artifacts/qairt244_short_multitoken_smoke/20260523_075743/
rerun: artifacts/qairt244_short_multitoken_smoke/20260523_085004/
```

## Outcome

```text
classification=executed
executed=true
result=success
prompt=Hi
max_output_tokens=3
marker=qairt244_short_multitoken_smoke_v1
output=! How Hi
elapsed_ms=1358
decode_elapsed_ms=164
timeout=false
tombstone_classification=stale-tombstone-ignored
```

Reproducibility rerun:

```text
classification=executed
executed=true
result=success
prompt=Hi
max_output_tokens=3
marker=qairt244_short_multitoken_smoke_v1
output=! How Hi
elapsed_ms=1579
decode_elapsed_ms=78
timeout=false
tombstone_classification=stale-tombstone-ignored
```

Reproducibility classification: the isolated lower-level NPU three-token smoke
has now succeeded `2/2` with the same output and no fresh crash evidence.

The rebuilt LiteRT-LM JNI artifact contains both required static markers:

```text
DecodeConfig.SetMaxOutputTokens(3)
qairt244_short_multitoken_smoke_v1
```

The dispatch artifact also preserves:

```text
DT_NEEDED [libLiteRt.so]
```

which is required for Android symbol resolution of dispatch references to
`LiteRtGetEnvironmentOptions`.

## Artifact Tracking Policy

Large native artifacts are local-only. The 2026-05-23 reproducibility cleanup
removes rebuilt `.so` files and APK-extracted `.so` files from Git tracking and
keeps only text evidence in commits:

- `summary.md`
- `result.txt`
- `native_diag.txt`
- stale tombstone notes
- Build IDs / sha256 / metadata
- external diff patches

The local build artifact remains usable for reruns at:

```text
artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/
```

## Safety Check Summary

Passed:

- `customBuildExperimentDebug` app id target
- normal UI disconnected
- prompt fixed to `Hi`
- requested hard cap is `3`
- timeout configured to `30` seconds
- app-side wrapper marker present
- artifact marker present in `liblitertlm_jni.so`
- `SetMaxOutputTokens(3)` present in artifact metadata
- explicit `--run` was supplied once

Not used:

- high-level `generateResponse`
- normal `ChatScreen` route
- `selectedPath=npu` normal path
- streaming generation
- app chat DB / TTS / Markdown

## Timing

```text
model_assets_elapsed_ms=0
engine_settings_elapsed_ms=0
engine_create_elapsed_ms=1072
session_create_elapsed_ms=0
prefill_elapsed_ms=18
decode_elapsed_ms=164
cleanup_elapsed_ms=102
elapsed_ms=1358
```

Reproducibility rerun timing:

```text
model_assets_elapsed_ms=0
engine_settings_elapsed_ms=0
engine_create_elapsed_ms=1376
session_create_elapsed_ms=0
prefill_elapsed_ms=28
decode_elapsed_ms=78
cleanup_elapsed_ms=95
elapsed_ms=1579
```

Token counts remain unavailable from this lower-level entrypoint. The result
records byte counts instead:

```text
prompt_bytes=2
output_bytes=8
prompt_token_count=unavailable
output_token_count=unavailable
```

## NPU Evidence

The successful result was produced by the isolated NPU backend path:

```text
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
```

The diagnostic artifact contains the app-packaged native stack:

```text
liblitertlm_jni.so bb6f8924e466e7039a1f54d7170a2eb2
libLiteRt.so a03032ad1eeefda446478aea308c2ed0
libLiteRtDispatch_Qualcomm.so 283f860170c8b970f14db885eab73a95
libQnnSystem.so 0d409cdd664b8b0a
libQnnHtp.so f2c90c1775a109e1
```

## Tombstone Classification

The collector selected `/data/tombstones/tombstone_22`, but it does not contain
the current smoke run id for both short multi-token runs. The current
result/native diag files contain the current marker and the process was still
alive after each smoke:

```text
classification=stale-tombstone-ignored
fresh_crash=false
```

## Execution Status

Executed exactly once. The smoke did:

- create the lower-level native session required for this isolated smoke
- call `RunPrefill`
- call `RunDecode` once with `DecodeConfig.SetMaxOutputTokens(3)`
- clean up session/engine via `unique_ptr` reset

The smoke did not:

- call high-level `generateResponse`
- create `Conversation`
- use the normal UI route
- connect NPU to `ChatScreen`

## Next Step

Keep the path isolated and add one more reproducibility run only if explicitly
approved. Do not enable any Diagnostic Chat run button or normal UI NPU route
from a single short multi-token success.
