# QAIRT 2.44 Lower-Level Single-Token Smoke

Artifact: `artifacts/qairt244_lower_level_single_token_smoke/20260523_053258/`

Build artifact: `artifacts/qairt244_single_token_entrypoint_build/20260523_052106/`

## Outcome

```text
classification=executed
executed=true
marker=qairt244_lower_level_single_token_smoke_v1
result=success
prompt=Hi
max_output_tokens=1
elapsed_ms=1115
output=!
```

Native diag confirms:

```text
before RunDecode SetMaxOutputTokens(1)
success output_candidates=1 output_bytes=1 elapsed_ms=1115 Engine.close=unique_ptr_cleanup
```

Timeout:

```text
timeout=false
waited_seconds=2
```

The process remained alive after the smoke. The diagnostics collector selected
an older initialize tombstone whose run id does not match this smoke run; see
`diagnostics/stale_tombstone_note.md`.

## Safety

- `customBuildExperimentDebug` only
- prompt fixed to `Hi`
- max output tokens fixed to `1`
- no normal UI NPU route
- no `Conversation`
- no Kotlin/public `Session` object
- no high-level `generateResponse`
