# QAIRT 2.44 Lower-Level Single-Token Smoke Preflight

Result: preflight first; execution only with explicit --run and a rebuilt custom
stack artifact.

The required C++ primitive exists in LiteRT-LM:

- `DecodeConfig::CreateDefault()`
- `DecodeConfig.SetMaxOutputTokens(1)`
- `Session::RunPrefill(...)`
- `Session::RunDecode(decode_config)`

The runnable Android path must be present in the custom LiteRT-LM native stack
and the customBuildExperimentDebug wrapper. Without --run this script stops
after static checks.

This preflight did not build, install, launch the app, create `Conversation`,
create `Session`, call `generateResponse`, or generate tokens.

Artifacts:

- `preflight_config.txt`
- `litert_lm_static_hits.txt`
- `lami_lower_level_static_hits.txt`
- `safety_checks.tsv`
- `classification.md`
- `result.txt`
