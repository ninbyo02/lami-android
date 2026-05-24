# LiteRT Custom Build QAIRT 2.44 Compare

Date: 2026-05-17

## Status

```text
qairt244-initialize-invoked-sigabrt-no-usable-dispatch-runtime
```

QAIRT `2.44.0.260225` is now installed as a real SDK on this machine:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

The limited exact qairt244 rebuild succeeded:

```text
artifacts/litert_custom_build/20260517_230448_qairt244/
```

The qairt244 stack was staged and packaged only into `customBuildExperimentDebug`.
The latest staging artifact is:

```text
artifacts/litert_custom_build_stage/20260521_015803/
```

Packaging and install succeeded. First initialize-only dry-run attempt
`runId=1779296283194` was skipped by the stale expected Build ID guard:

```text
custom-stack-build-id-mismatch
```

At that point, `Engine.initialize` had not yet been invoked for the qairt244
expected stack. The next run was limited to the explicit opt-in
`Engine.initialize` dry-run candidate. It still did not create
`Conversation`/`Session`, call
`generateResponse`, set `selectedPath=npu`, or wire `Backend.NPU` into the
normal UI inference path.

Update after expected ID refresh:

- stage artifact: `artifacts/litert_custom_build_stage/20260521_074601/`
- dry-run runId: `1779317161924`
- diagnostics artifact: `artifacts/npu_diagnostics/20260521_074641_customnpu/`
- `Engine.initialize` invoked: yes
- `Engine.initialize` returned: no
- signal: `SIGABRT`
- classification: `no-usable-dispatch-runtime`
- likely abort/register text: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- `Conversation`, `Session`, `generateResponse`, `selectedPath=npu`, and normal UI NPU inference remained unused.

Initial 2026-05-17 state: the path below existed, but it was only an
investigation overlay pointing to QAIRT `2.46.0.260424`:

```text
/home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225
```

At that time, the exact QAIRT `2.44.0.260225` payload was not available, so no
exact-match rebuild had been performed and no qairt244 native artifacts had
been produced.

Local search artifact:

```text
artifacts/qairt_244_exact_match/20260517_013958/local_search.txt
```

Acquisition notes:

```text
docs/qairt_244_acquisition_notes.md
```

## Current QAIRT 2.44 Build

Current exact qairt244 custom build:

```text
artifacts/litert_custom_build/20260517_230448_qairt244/
```

Current qairt244 native stack:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` |
| `libLiteRtDispatch_Qualcomm.so` | `a8006da3bd9b4fdf5b7131f8d864b6ee` | `00c26484621ab42bea6e3bee0d7e908451a428cf19cbd1ebfecf4ccee79e1739` |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` | `310e37ff7cf770c24d636bbb0f9647a0d59dd893ba0c2530acdfc06569704230` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `443391d4c4348191230b67a3ab8a6037` | `c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c` |
| `libGemmaModelConstraintProvider.so` | `f9e5e73e668032550042319e43012011` | `45ca57e55d52976e5d2dadfc0e874499fc0671c169a28077772c25264f9d81f6` |

## QAIRT 2.44 Initialize-Only Dry-Run Result

Result date: 2026-05-21

The expected Build ID guard passed far enough to invoke `Engine.initialize`.
The process then aborted during dispatch delegate creation.

| Field | Value |
| --- | --- |
| Run ID | `1779317161924` |
| Stage artifact | `artifacts/litert_custom_build_stage/20260521_074601/` |
| Diagnostics artifact | `artifacts/npu_diagnostics/20260521_074641_customnpu/` |
| Device tombstone | `/data/tombstones/tombstone_11` |
| Final stage | `Engine.initialize invoking method=Engine.initialize(): void` |
| `Engine.initialize` invoked | yes |
| `Engine.initialize` returned | no |
| Signal | `SIGABRT` |
| Classification | `no-usable-dispatch-runtime` |
| Main text | `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found` |

No generation path was exercised: no `Conversation`, no `Session`, no
`generateResponse`, no `selectedPath=npu`, and no normal UI NPU inference.

## Previous 2.46-Overlay Build Under Comparison

Previous 2.46-overlay custom build:

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

Status: completed for the limited rebuild.

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

Isolated insertion has now proceeded only for `customBuildExperimentDebug`
after exact QAIRT 2.44 acquisition and limited rebuild. The remaining next
action is the explicit opt-in initialize-only dry-run; app generation and normal
UI NPU inference remain out of scope.

No app integration, `Engine.initialize`, NPU inference, `Conversation`, `Session`, `generateResponse`, or `selectedPath=npu` was performed for this compare step.
