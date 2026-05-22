# QAIRT 2.44 Short Multi-Token Smoke Result

Date: 2026-05-23

Scope: `customBuildExperimentDebug` short multi-token smoke preflight. No NPU
generation was run in this commit.

## Artifact

```text
artifacts/qairt244_short_multitoken_smoke/20260523_071934/
```

## Outcome

```text
classification=preflight-blocked-native-artifact-required
executed=false
prompt=Hi
max_output_tokens=3
marker=qairt244_short_multitoken_smoke_v1
custom_build_artifact=none
```

The lami app-side wrapper exists and is scoped to
`customBuildExperimentDebug`, but execution is blocked because there is no
rebuilt LiteRT-LM native artifact proving:

```text
DecodeConfig.SetMaxOutputTokens(3)
qairt244_short_multitoken_smoke_v1
```

## Safety Check Summary

Passed:

- `customBuildExperimentDebug` app id target
- normal UI disconnected
- prompt fixed to `Hi`
- requested hard cap is `3`
- timeout configured to `30` seconds
- app-side wrapper marker present

Blocked:

- no static `SetMaxOutputTokens(3)` evidence in current external source/artifact
- no rebuilt short multi-token custom artifact supplied
- no artifact native marker evidence
- no `--run` request

## Execution Status

Not executed. Therefore:

- no additional `Engine.initialize`
- no additional `RunDecode`
- no high-level `generateResponse`
- no normal UI route
- no crash/tombstone from this preflight

## Next Step

Patch the external LiteRT-LM JNI entrypoint to add a separate
`qairt244_short_multitoken_smoke_v1` function that calls
`DecodeConfig.SetMaxOutputTokens(3)`, rebuild the QAIRT 2.44 custom stack, then
rerun the script once with `--artifact <new-build> --run`.
