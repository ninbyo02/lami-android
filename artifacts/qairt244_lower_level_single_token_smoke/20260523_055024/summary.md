# QAIRT 2.44 Lower-Level Single-Token Smoke

Artifact: `/home/sato/project/lami-android/artifacts/qairt244_lower_level_single_token_smoke/20260523_055024`

Build artifact: `artifacts/qairt244_single_token_entrypoint_build/20260523_052106`

## Outcome

```text
classification=executed
run_id=1779483024756
result=success
prompt=Hi
max_output_tokens=1
elapsed_ms=907
output=!
timeout=false;waited_seconds=2
tombstone_classification=stale-tombstone-ignored
```

Native diag is expected to include:

```text
before RunDecode SetMaxOutputTokens(1)
success output_candidates=1 output_bytes=...
```

Actual native diag:

```text
before RunDecode SetMaxOutputTokens(1)
success output_candidates=1 output_bytes=1 elapsed_ms=907 Engine.close=unique_ptr_cleanup
```

Tombstone freshness note:

```text
/home/sato/project/lami-android/artifacts/qairt244_lower_level_single_token_smoke/20260523_055024/stale_tombstone_note.md
```

## Safety

- `customBuildExperimentDebug` only
- prompt fixed to `Hi`
- max output tokens fixed to `1`
- no normal UI NPU route
- no `Conversation`
- no Kotlin/public `Session` object
- no high-level `generateResponse`
