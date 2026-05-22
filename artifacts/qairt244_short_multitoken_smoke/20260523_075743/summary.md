# QAIRT 2.44 Short Multi-Token Smoke Result

Artifact: `/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_smoke/20260523_075743`

## Outcome

```text
classification=executed
executed=true
result=success
output=! How Hi
elapsed_ms=1358
decode_elapsed_ms=164
tombstone_classification=stale-tombstone-ignored
prompt=Hi
max_output_tokens=3
marker=qairt244_short_multitoken_smoke_v1
custom_build_artifact=artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526
```

This artifact is preflight-first. It does not connect NPU to the normal UI,
does not call high-level `generateResponse`, and does not run generation
unless `--run` is supplied and static evidence proves `SetMaxOutputTokens(3)`.

Required next build input:

```text
custom LiteRT-LM JNI artifact containing:
- qairt244_short_multitoken_smoke_v1
- DecodeConfig.SetMaxOutputTokens(3)
```
