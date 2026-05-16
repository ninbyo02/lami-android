# LiteRT Custom Build QAIRT 2.44 Compare

Date: 2026-05-17

## Status

```text
blocked-awaiting-qairt244
```

QAIRT `2.44.0.260225` is not currently installed as a real SDK on this machine.

The path below exists, but it is only an investigation overlay pointing to QAIRT `2.46.0.260424`:

```text
/home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225
```

Because the exact QAIRT `2.44.0.260225` payload is not available, no exact-match rebuild was performed and no new native artifacts were produced.

Local search artifact:

```text
artifacts/qairt_244_exact_match/20260517_013958/local_search.txt
```

Acquisition notes:

```text
docs/qairt_244_acquisition_notes.md
```

## Previous Build Under Comparison

Previous custom build:

```text
artifacts/litert_custom_build/20260516_235244/
```

That build used a QAIRT overlay where LiteRT's expected path:

```text
qairt/2.44.0.260225
```

resolved to local QAIRT:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

Previous built native stack:

| Library | Build ID |
| --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` |
| `libLiteRtDispatch_Qualcomm.so` | `e999216e6d32c2f38702cd8538299e7d` |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `9053b81d7cbccdc3b5460c5e7395e293` |
| `libGemmaModelConstraintProvider.so` | `f9e5e73e668032550042319e43012011` |

Runtime result from isolated `customBuildExperimentDebug`:

```text
Engine.initialize -> SIGABRT
DispatchDelegate::CreateDelegateKernelInterface()+312
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

## Exact QAIRT 2.44 Build Plan

Once the SDK exists at:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

run:

```bash
bash scripts/build_litert_custom_artifacts.sh \
  ~/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244
```

The updated build script will write:

```text
artifacts/litert_custom_build/<timestamp>_qairt244/
```

It will create a per-run overlay:

```text
artifacts/litert_custom_build/<timestamp>_qairt244/qairt_overlay/qairt/2.44.0.260225
```

This avoids modifying the existing 2.46 overlay.

## Compare Criteria After Build

When the exact build exists, compare against:

1. previous custom build: `artifacts/litert_custom_build/20260516_235244/`
2. new QAIRT 2.44 build: `artifacts/litert_custom_build/<timestamp>_qairt244/`
3. Gallery SM8750 stack
4. local QAIRT 2.46
5. `customBuildExperimentDebug` APK packaged QNN libraries

Required checks:

- Build ID
- SHA-256
- file size
- SONAME
- `NEEDED`
- `LiteRtDispatchGetApi`
- `LiteRtDispatchCheckRuntimeCompatibility`
- QNN API/version/capability strings
- V79/SM8750 strings
- ADSP/LD path strings

## Current Decision

Do not proceed to isolated insertion. Exact QAIRT 2.44 is missing, so the next action is acquisition, not app testing.

No app integration, `Engine.initialize`, NPU inference, `Conversation`, `Session`, `generateResponse`, or `selectedPath=npu` was performed for this compare step.
