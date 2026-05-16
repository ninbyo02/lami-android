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
bash scripts/run_qairt244_rebuild_compare.sh
```

The wrapper will refuse to run if the QAIRT 2.44 path is missing or resolves to
the known QAIRT 2.46 overlay. If the exact SDK is present, it calls:

```bash
bash scripts/build_litert_custom_artifacts.sh \
  ~/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244
```

The build helper will write:

```text
artifacts/litert_custom_build/<timestamp>_qairt244/
```

It will create a per-run overlay:

```text
artifacts/litert_custom_build/<timestamp>_qairt244/qairt_overlay/qairt/2.44.0.260225
```

This avoids modifying the existing 2.46 overlay.

The wrapper will write the compare summary to:

```text
artifacts/qairt244_rebuild_compare/<timestamp>/
```

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

## Expected Outcomes

### 1. QAIRT 2.44 artifacts differ from the 2.46-overlay build

This is the most useful outcome. It means the exact SDK affected at least one
native output and an isolated insertion test may be worth preparing after static
review.

Do not insert automatically. First compare:

- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRt.so`
- `liblitertlm_jni.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- QNN/HTP library metadata

### 2. QAIRT 2.44 artifacts are identical to the previous 2.46-overlay build

This weakens the SDK-header mismatch hypothesis for the built LiteRT artifacts.
The next question becomes runtime QNN library packaging/capability or model
schema compatibility.

Do not run `Engine.initialize` until the identical-output result is documented.

### 3. Build fails due to SDK mismatch or missing files

Record the build failure under `artifacts/qairt244_rebuild_compare/<timestamp>/`
and do not proceed to insertion. The failure itself becomes evidence for the
maintainer issue.

### 4. Build succeeds but still requires isolated insertion

A successful exact build is not runtime proof. It only unlocks a later
debug-only isolated insertion phase. That later phase must still run only:

- `Backend.NPU(String)` instantiate
- `EngineConfig` dry-build
- explicit opt-in `Engine.initialize` dry-run

It must not run `Conversation`, `Session`, or `generateResponse`.

## Isolated Insertion Gate

Proceed only if all are true:

- QAIRT 2.44 exact SDK was used, not a symlink to QAIRT 2.46.
- limited build targets succeeded.
- static compare has no obvious missing `NEEDED` libraries.
- built JNI, LiteRT, dispatch, compiler plugin, and Gemma constraint provider
  are generation-consistent.
- insertion is limited to an isolated debug flavor.

Do not proceed if:

- the SDK path is missing or resolves to the 2.46 overlay.
- `liblitertlm_jni.so` fails to build.
- `libGemmaModelConstraintProvider.so` is missing.
- static compare shows unresolved runtime dependencies.
- the next step would affect `standardDebug`, `npuExperimentDebug`, or
  `galleryStackExperimentDebug`.

## Current Decision

Do not proceed to isolated insertion. Exact QAIRT 2.44 is missing, so the next action is acquisition, not app testing.

No app integration, `Engine.initialize`, NPU inference, `Conversation`, `Session`, `generateResponse`, or `selectedPath=npu` was performed for this compare step.
