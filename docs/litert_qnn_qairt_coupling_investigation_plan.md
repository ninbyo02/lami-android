# LiteRT QNN/QAIRT Coupling Investigation Plan

Date: 2026-05-17

## Current Conclusion

`customBuildExperimentDebug` stages a same-source/tag custom LiteRT-LM native stack in an isolated application id:

```text
io.github.ninbyo02.lami.customnpu
```

The stack includes:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- `libGemmaModelConstraintProvider.so`

The explicit opt-in `Engine.initialize` dry-run reached:

- `Backend.NPU(String)`: success
- `EngineConfig`: success
- `Engine(EngineConfig)`: returned
- `Engine.initialize`: invoked, did not return

Crash artifact:

```text
artifacts/npu_diagnostics/20260517_005032_customnpu/
```

The tombstone shows `SIGABRT` with the top native frame:

```text
liblitertlm_jni.so / DispatchDelegate::CreateDelegateKernelInterface()+312
```

Register fragments are consistent with:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

This means the failure is not explained by:

- missing `libLiteRtDispatch_Qualcomm.so`
- missing `libLiteRt.so`
- Java/native descriptor mismatch
- `EngineConfig` wiring failure
- `Engine(EngineConfig)` constructor failure

No NPU inference, `Conversation`, `Session`, `generateResponse`, token generation, or `selectedPath=npu` was used.

## Hypotheses

### H1: QAIRT 2.44 Expected vs 2.46 Built/Runtime Mismatch

The LiteRT source pinned by LiteRT-LM `v0.11.0` expects QAIRT `2.44.0.260225`, while the local build used an overlay to QAIRT `2.46.0.260424`.

This can produce binaries that compile and link but fail runtime dispatch compatibility or QNN capability checks.

### H2: SM8750/V79 Dispatch Capability Mismatch

The dispatch runtime may not advertise the exact capabilities required by LiteRT-LM for the SM8750 compiled model path, or LiteRT-LM may reject the runtime during dispatch delegate kernel creation.

Signals to look for:

- `LiteRtDispatchCheckRuntimeCompatibility`
- insufficient capability strings
- dispatch API version/layout checks
- SM8750/V79-specific capability strings

### H3: QNN/HTP Skel/Stub Path or ADSP Search Path Issue

External QAIRT validation passed on the device, but an Android app process has different library search constraints. QNN HTP skel/stub discovery may require app packaging, DSP search path, or loader behavior not currently satisfied.

Signals to look for:

- `ADSP_LIBRARY_PATH`
- `LD_LIBRARY_PATH`
- `libQnnHtpV79Stub.so`
- `libQnnHtpV79Skel.so`
- `libcdsprpc.so`
- linker errors
- QNN manager initialization logs

### H4: Model/Runtime Schema Mismatch

The Qualcomm SM8750 `.litertlm` model may require a runtime/compiler generation different from the built LiteRT/LiteRT-LM stack or the Gallery payload.

Signals to look for:

- model metadata requiring a different dispatch/compiler version
- schema/version strings in model or runtime
- model compiled for a different QNN or LiteRT generation

### H5: App Sandbox/nativeLibraryDir Discovery Limitation

The app passes `nativeLibraryDir` to `Backend.NPU`, but some runtime components may still search outside that directory or use assumptions valid in Gallery but not in Lami.

Signals to look for:

- paths hardcoded or inferred from Gallery package
- runtime loading from `LD_LIBRARY_PATH`
- compiler plugin search paths
- QNN skel/stub search outside app private native library dir

## Evidence So Far

### Built Stack

| Library | Build ID |
| --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` |
| `libLiteRtDispatch_Qualcomm.so` | `e999216e6d32c2f38702cd8538299e7d` |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `9053b81d7cbccdc3b5460c5e7395e293` |
| `libGemmaModelConstraintProvider.so` | `f9e5e73e668032550042319e43012011` |

### Device And External QAIRT

- Device: Nubia Z70S Ultra / `NX733J`
- SoC: `SM8750`
- Hardware: `qcom`
- DSP core: Hexagon Architecture V79
- External `qnn-net-run`: available
- External `qnn-platform-validator`: passed
- External QNN SDK: `2.46.0.260424`

External QAIRT success proves device capability, but it does not prove LiteRT-LM app-process compatibility.

### App Runtime Result

The app reaches dispatch delegate creation and then aborts with evidence consistent with:

```text
No usable Dispatch runtime found
```

This points most strongly to runtime/capability compatibility rather than a simple missing-file condition.

## Proposed Safe Experiments

### E1: Collect Exact QNN Version and Capability Strings

Scope:

- built `libLiteRtDispatch_Qualcomm.so`
- built `libLiteRt.so`
- built `libLiteRtCompilerPlugin_Qualcomm.so`
- available QNN libs
- Gallery reference libs

Goal:

- identify expected QAIRT version strings
- identify dispatch capability strings
- identify SM8750/V79 strings
- compare built, Gallery, and device-side QNN assumptions

Safety:

- static analysis only
- no app launch
- no build

Initial static collection helper:

```bash
bash scripts/plan_qairt_coupling_investigation.sh
```

Initial artifact:

```text
artifacts/qairt_coupling_investigation/20260517_010628/
```

### E2: Compare Built QAIRT Overlay Libs vs Device QAIRT vs Gallery Libs

Scope:

- QAIRT overlay: `2.46.0.260424`
- LiteRT source expected: `2.44.0.260225`
- Gallery QNN libs
- current app-packaged QNN libs

Goal:

- determine whether the built stack is coupled to a QAIRT generation not present in the app
- identify exact QNN Build IDs and version strings

Safety:

- file metadata and strings only
- no copying into app

### E3: Attempt CLI `litert_lm_main --backend=npu` Planning

Scope:

- build/query whether a host/device CLI path exists for the same source/tag
- if buildable later, run outside Lami app first

Goal:

- separate Android app packaging/search-path issues from LiteRT-LM model/runtime issues

Safety:

- planning first
- no app integration
- no Lami UI path

### E4: Build With Exact QAIRT 2.44.0.260225 if Obtainable

Scope:

- rebuild the same limited targets with the exact QAIRT version expected by LiteRT source

Goal:

- test whether QAIRT 2.44 vs 2.46 is the cause of runtime dispatch rejection

Safety:

- build to `artifacts/` only
- static compare before any insertion
- isolated flavor only if later approved

### E5: Build With Source/Ref Matching QAIRT 2.46.0.260424

Scope:

- find LiteRT source commit or tag that expects QAIRT `2.46.0.260424`

Goal:

- produce a generation-matched stack for the locally available QNN SDK/device runtime

Safety:

- query/cquery first
- build later only with explicit approval

### E6: Investigate ADSP/LD Path Feasibility in Android App

Scope:

- determine whether QNN HTP skel/stub discovery requires environment variables or app-specific loader hints

Goal:

- identify whether `ADSP_LIBRARY_PATH`, linker namespace, or nativeLibraryDir search behavior blocks QNN runtime startup

Safety:

- no path mutation in normal app
- no global environment changes
- no standard flavor changes

### E7: Inspect Model Metadata/Schema

Scope:

- `gemma-4-E2B-it_qualcomm_sm8750.litertlm`

Goal:

- identify required runtime/compiler/model schema versions
- identify whether it is tied to Gallery-specific runtime payload

Safety:

- read-only model inspection
- no inference

## Prioritization

Recommended order:

1. E1 static strings/capability inventory
2. E2 QNN/QAIRT version matrix
3. E7 model metadata/schema inspection
4. E6 ADSP/path feasibility review
5. E3 CLI planning/query
6. E4 exact QAIRT `2.44.0.260225` rebuild if obtainable
7. E5 source/ref matching QAIRT `2.46.0.260424`

## Safety Rules

- no normal UI NPU path
- no `selectedPath=npu`
- no `Conversation`
- no `Session`
- no `generateResponse`
- no token generation until `Engine.initialize` returns successfully in an isolated flavor
- no `standardDebug`, `npuExperimentDebug`, or `galleryStackExperimentDebug` behavior changes
- no native library addition/removal/replacement without a dedicated isolated experiment
- no QNN SDK libraries copied into app source sets without explicit approval

## Static Coupling Pass Result

Result date: 2026-05-17

Script:

```bash
bash scripts/analyze_qairt_qnn_coupling.sh
```

Artifact:

```text
artifacts/qairt_qnn_coupling/20260517_012057/
```

Scope:

- no build
- no install
- no app launch
- no `Engine.initialize`
- no NPU inference
- no native library staging

Findings:

- `customBuildExperimentDebug` packages the built LiteRT stack plus QNN/HTP libraries, but the packaged QNN libraries are a third generation distinct from both Gallery SM8750 and local QAIRT 2.46.
- Tombstone mapping from the latest customnpu crash shows `liblitertlm_jni.so`, `libGemmaModelConstraintProvider.so`, and `libllm_inference_engine_jni.so`; it does not show `libLiteRtDispatch_Qualcomm.so` or QNN libraries mapped in the extracted map lines.
- built and Gallery dispatch libraries both contain `SM8750`, `ADSP_LIBRARY_PATH`, `LD_LIBRARY_PATH`, and QNN version mismatch/check strings.
- No direct evidence points to `libLiteRtRuntimeCApi.so` as the primary missing dependency.

Detailed findings:

```text
docs/litert_qnn_qairt_coupling_findings.md
```

Revised hypothesis ranking:

1. QAIRT/QNN version coupling mismatch.
2. SM8750/V79 dispatch capability mismatch.
3. QNN/HTP skel/stub path issue.
4. model/runtime schema mismatch.
5. app sandbox/nativeLibraryDir discovery limitation.

Recommended next safe work:

1. obtain exact QAIRT `2.44.0.260225` or find a LiteRT source/ref aligned to QAIRT `2.46.0.260424`;
2. build only into `artifacts/` and static-compare before any insertion;
3. consider an isolated QNN-libs alignment experiment only after licensing/reuse review;
4. keep all runtime checks at `Engine.initialize` dry-run until initialization succeeds.

## QAIRT 2.44 Search Result

Result date: 2026-05-17

Search artifact:

```text
artifacts/qairt_244_exact_match/20260517_013958/local_search.txt
```

Finding:

- exact QAIRT `2.44.0.260225` was not found locally.
- the only matching directory is the existing build overlay path, which points to QAIRT `2.46.0.260424`.

Status:

```text
blocked-awaiting-qairt244
```

Prepared docs:

- `docs/qairt_244_acquisition_notes.md`
- `docs/litert_custom_build_qairt244_compare.md`

Prepared script option:

```bash
bash scripts/build_litert_custom_artifacts.sh \
  ~/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244
```

No exact-match rebuild was performed.

## QAIRT 2.46 Source/Ref Search Result

Result date: 2026-05-17

Artifact:

```text
artifacts/litert_qairt246_ref_search/20260517_062055/
```

Result:

- no exact QAIRT `2.46.0.260424` source/ref evidence was found in public LiteRT QAIRT metadata
- public LiteRT `origin/main` still expects QAIRT `2.44.0.260225`
- public LiteRT-LM `origin/main` pins LiteRT `d865fd82cd7fe6752908b3a0836895461c305679`
- that pinned LiteRT ref also expects QAIRT `2.44.0.260225`
- query/cquery for a QAIRT 2.46 candidate was skipped because no candidate ref was identified

Docs:

- `docs/litert_qairt246_source_ref_candidates.md`
- `docs/litert_qairt246_ref_search_results.md`

Investigation impact:

QAIRT 2.46 remains a possible runtime generation on the device, but not a
source-matched build path with the public refs currently inspected. Do not build
another QAIRT 2.46 overlay stack unless maintainers identify the matching
LiteRT/LiteRT-LM source ref.
