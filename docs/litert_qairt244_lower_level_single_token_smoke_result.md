# QAIRT 2.44 Lower-Level Single-Token Smoke Result

Date: 2026-05-23

Scope: two explicit `customBuildExperimentDebug` lower-level NPU smoke runs
using the isolated JNI entrypoint. This did not connect NPU to the normal UI
path.

## Result

First execution artifact:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_053258/
```

Reproducibility execution artifact:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_055024/
```

Build artifact:

```text
artifacts/qairt244_single_token_entrypoint_build/20260523_052106/
```

External LiteRT-LM patch snapshot:

```text
artifacts/qairt244_single_token_entrypoint_build/20260523_052106/litertlm_external_status.txt
artifacts/qairt244_single_token_entrypoint_build/20260523_052106/litertlm_external_diff.patch
```

First outcome:

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

Second outcome:

```text
classification=executed
executed=true
marker=qairt244_lower_level_single_token_smoke_v1
result=success
prompt=Hi
max_output_tokens=1
elapsed_ms=907
output=!
```

Both native diagnostic files confirm the hard cap path. The second run shows:

```text
before RunDecode SetMaxOutputTokens(1)
success output_candidates=1 output_bytes=1 elapsed_ms=907 Engine.close=unique_ptr_cleanup
```

## Native IDs

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `731b74da505bef341a184b3778d0412d` | `3ba100245ed79d45abf3c34230aee77d6aabd0b6c302a1ce8dd060b95575e7ec` |
| `libLiteRtDispatch_Qualcomm.so` | `a1b66b12e643f15a94cb34093f9efcac` | `459ceb6e3912fa72b43363c763315b2fbf5d336e744e82e4850f33967c7bbeba` |
| `liblitertlm_jni.so` | `a5f78bc1fb6839abead290eebd139860` | `b5a74c656c99830fbc8f0888f71643078d71c2957712eef2dfff06d991ad286b` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `12a6ac7197aff7045fc5f5c263b35f9f` | `299769ef90f9ee4b74b357bd867545e1f48312bb8b1d97f9d16968a2be175655` |

The dispatch library keeps `DT_NEEDED [libLiteRt.so]`, and the
`customBuildExperimentDebug` manifest keeps optional
`uses-native-library libcdsprpc.so`.

## Execution Path

Entrypoint:

```text
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244LowerLevelSingleTokenSmoke_nativeRun
```

Path:

```text
ModelAssets::Create
EngineSettings::CreateDefault(NPU)
EngineFactory::CreateDefault
Engine::CreateSession
Session::RunPrefill("Hi")
DecodeConfig::CreateDefault()
DecodeConfig.SetMaxOutputTokens(1)
Session::RunDecode(decode_config)
unique_ptr cleanup
```

This creates the lower-level native session required by LiteRT-LM generation.
It does not create a Kotlin/public `Session` object and does not create a
`Conversation`.

## Safety Outcome

- `maxOutputTokens=1`: confirmed statically and in native diag.
- prompt: `Hi`
- timeout: `false`
- wait time: `2` seconds
- result file: created
- output: `!`
- high-level `generateResponse`: not used
- normal UI route: not used
- app DB/TTS/Markdown/chat UI: not used
- process alive after first smoke: `18212`
- process alive after second smoke: `22071`
- `Engine.close`: represented by native `unique_ptr` cleanup after successful
  decode

The runner now writes run metadata and classifies collector-selected tombstones
against the current smoke run id. In the second run, the collector again
selected an older initialize tombstone, but the raw tombstone did not contain
the current smoke run id `1779483024756`; the app-private result did contain
that run id and reported success. The tombstone is therefore classified as
stale and ignored for the smoke outcome. See:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_055024/stale_tombstone_note.md
```

Classification: lower-level one-token NPU smoke succeeded twice consecutively;
no fresh crash evidence; no normal UI NPU wiring.

## Next Step

Do not connect this to the normal UI yet. A similarly isolated verifier that
records token accounting/backend timing has been implemented and rebuilt, but
its one allowed execution is pending a connected Nubia device:

```text
artifacts/qairt244_token_timing_verifier_build/20260523_060634/
```
