# QAIRT 2.44 Short Multi-Token Smoke Result

Artifact: `/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_smoke/20260523_085004`

## Outcome

```text
classification=executed
executed=true
result=success
output=! How Hi
elapsed_ms=1579
decode_elapsed_ms=78
tombstone_classification=stale-tombstone-ignored
prompt=Hi
max_output_tokens=3
marker=qairt244_short_multitoken_smoke_v1
custom_build_artifact=artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526
```

This artifact is preflight-first. It does not connect NPU to the normal UI,
does not call high-level `generateResponse`, and does not run generation
unless `--run` is supplied and static evidence proves `SetMaxOutputTokens(3)`.

## Artifact Tracking Policy

Large rebuilt native binaries are local-only and must not be committed:

- `built_libs/*.so`
- `qnn_runtime_libs/*.so`
- `reference_libs/**/*.so`
- `diagnostics/apk_libs/*.so`

Commit only text evidence such as summaries, Build IDs, hashes, run metadata,
and external diff patches.

Required next build input:

```text
custom LiteRT-LM JNI artifact containing:
- qairt244_short_multitoken_smoke_v1
- DecodeConfig.SetMaxOutputTokens(3)
```
