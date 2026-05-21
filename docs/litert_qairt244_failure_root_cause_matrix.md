# QAIRT 2.44 NPU Dispatch Failure Root Cause Matrix

Date: 2026-05-21

Scope: coordinator synthesis of the tombstone/runtime mapping, Android QNN path
review, LiteRT source trace, CLI proof planning, model schema probe, the
2026-05-21 Android-native logcat dry-run, the JNI sentinel dry-run, and the
2026-05-22 app-owned JNI logcat smoke.

## Current Failure Boundary

- flavor: `customBuildExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.customnpu`
- runId: `1779317161924`
- diagnostic artifact: `artifacts/npu_diagnostics/20260521_074641_customnpu/`
- tombstone: `/data/tombstones/tombstone_11`
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- returned: no
- signal: `SIGABRT`
- reconstructed text: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`

The dry-run stopped at the allowed initialize boundary. It did not create a
`Conversation` or `Session`, did not call `generateResponse`, did not wire
normal UI inference to NPU, and did not run a single-token smoke test.

## Android Logcat Dry-Run Update

Artifact:

```text
artifacts/qairt244_android_log_build/20260521_210911/
```

Dry-run diagnostics:

```text
artifacts/npu_diagnostics/20260521_211841_customnpu/
```

Result:

- executed exactly one `customBuildExperimentDebug` explicit
  `Engine.initialize` dry-run
- final stage remained `Engine.initialize invoking`
- tombstone top app frame:
  `DispatchDelegate::CreateDelegateKernelInterface()+464`
- tombstone top app BuildId:
  `27bb6eaa5358f3c23f080cdd33023eac`
- no `QAIRT244_DIAG` or `qairt244_android_log_v1` lines were found in collected
  logcat/dropbox/tombstone artifacts
- `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, and QNN/HTP libraries were
  still not mapped in the tombstone

## JNI Sentinel Dry-Run Update

Artifact:

```text
artifacts/qairt244_jni_sentinel_build/20260521_214511/
```

Dry-run diagnostics:

```text
artifacts/npu_diagnostics/20260521_215004_customnpu/
```

Result:

- executed exactly one `customBuildExperimentDebug` explicit
  `Engine.initialize` dry-run
- final stage remained `Engine.initialize invoking`
- tombstone top app frame:
  `DispatchDelegate::CreateDelegateKernelInterface()+464`
- tombstone app BuildId:
  `8faff14dc850b7fb1986a300ac465fa4`
- `nativeCreateEngine` is present in the tombstone:
  `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1992`
- no `QAIRT244_SENTINEL`, `qairt244_jni_entry_v1`, `QAIRT244_DIAG`, or
  `qairt244_android_log_v1` lines were found in collected artifacts
- `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, and QNN/HTP libraries were
  still not mapped in the tombstone

Interpretation: the JNI entry is reached, but app-native log lines are still not
visible in the current collected artifacts.

## App-Owned JNI Logcat Smoke Update

Artifact:

```text
artifacts/qairt244_app_jni_smoke/20260522_071945/
```

Result:

- `customBuildExperimentDebug` APK contains `liblami_qairt244_smoke.so`
- Activity extra `run_app_jni_smoke=true` executed only the smoke path
- no `Backend.NPU`, `Engine.initialize`, LiteRT engine, `Conversation`,
  `Session`, or generation path was used
- app-private `qairt244_app_jni_smoke.txt` contains the native marker
  `qairt244_app_jni_smoke_v1`
- `adb logcat -b all -d -v time` did not contain `QAIRT244_SMOKE` or the marker
- classification: `native-executed-logcat-missing`

Interpretation: the missing `QAIRT244_SENTINEL` / `QAIRT244_DIAG` evidence is
now primarily a logcat capture/visibility problem. The absence of those tags can
no longer be used to infer that the LiteRT-LM logging locations were not reached.

## Cross-Agent Findings

| Agent | Output | Key result |
| --- | --- | --- |
| A | `docs/litert_qairt244_tombstone_runtime_mapping.md` | `liblitertlm_jni.so` and `libGemmaModelConstraintProvider.so` were mapped; Qualcomm dispatch and QNN/HTP libraries were present in metadata but not mapped before abort. |
| B | `docs/litert_qairt244_android_qnn_path_analysis.md` | No adb device was connected for live rootless collection. Existing artifacts show app packaging is not a simple missing-file case, but app-process QNN/CDSP path state remains unrefreshed. |
| C | `docs/litert_dispatch_capability_source_trace.md` | The fatal is emitted when `InitializeDispatchApi()` fails and `has_dispatch_runtime_` becomes false; `.so` presence and `LiteRtDispatchGetApi` export are not sufficient. |
| D | `docs/litert_lm_main_npu_cli_proof_plan.md` | `//runtime/engine:litert_lm_main` exists, but upstream CLI creates a `Conversation` and sends a prompt, so it must not be executed for this task. |
| E | `docs/litert_qualcomm_sm8750_model_schema_probe.md` | Local model starts with `LITERTLM` and contains `DISPATCH_OP`, `qnn_partition_*`, `soc_type=SM8750`, `min_arch=79`, and QAIRT `v2.44.0.260225143659` markers. |

## Hypothesis Matrix

| Hypothesis | Evidence | Confidence | Next action |
| --- | --- | --- | --- |
| H1. SM8750/V79 dispatch capability mismatch | Source trace shows SM8750 maps to V79, but dispatch usability depends on QNN manager, API versions, HTP device creation, and capability bits. Model declares `soc_type=SM8750` and `min_arch=79`. | medium | Add source logging around dispatch API initialization or get upstream guidance on expected SM8750/V79 capability checks. |
| H2. Android app nativeLibraryDir QNN/HTP search problem | qairt244 APK metadata contains dispatch/QNN/HTP/V79 libs, but tombstone does not map them before abort. Android-log and JNI-sentinel dry-runs did not expose visible native logs. App-owned JNI smoke proves native code can execute while logcat capture misses the tag. | medium | Prefer file-backed dispatch diagnostics or fix logcat capture before QNN path changes. |
| H3. ADSP_LIBRARY_PATH / FastRPC / skel-stub path problem | V79 stub depends on `libcdsprpc.so`; source mutates `ADSP_LIBRARY_PATH`; tombstones contain `vendor_adsprpc_prop`. No direct missing skel/FastRPC log was captured, and qairt244 aborts before V79 stub/skel mapping. | medium-low | Refresh rootless device collection and inspect logs for skel/CDSP failures. Do not change app packaging until a path-specific failure is visible. |
| H4. Qualcomm SM8750 model/runtime schema mismatch | Model directly carries QAIRT 2.44, SM8750, V79, and dispatch/QNN partition markers. That argues against a generic or wrong-SoC model, but context binary compatibility can still fail later. | low-medium | Defer deeper schema decode until dispatch API initialization logs show runtime accepted and invocation context creation is reached. |
| H5. Dispatch runtime registration / capability check failure | Strong source evidence: `No usable Dispatch runtime found` is the generic fatal after dispatch API init failure. The JNI-sentinel dry-run proves `nativeCreateEngine` is reached in the rebuilt JNI library. App-owned JNI smoke shows logcat misses even a trivial native tag, so lack of `QAIRT244_DIAG` is not reliable reachability evidence. | high | Add file-backed dispatch diagnostics or repair logcat capture, then inspect dispatch init status. |
| H6. CLI litert_lm_main works while Android app fails | Not tested. Existing upstream CLI is unsafe because it creates a `Conversation` and sends a prompt. CLI could later isolate linker namespace and explicit `LD_LIBRARY_PATH`/`ADSP_LIBRARY_PATH`. | unknown | First create an initialize-only CLI target that cannot generate, then build/query Android arm64 with explicit SDK/NDK setup. |

## Ranked Next Moves

1. Add file-backed diagnostics at the same LiteRT-LM JNI and dispatch
   boundaries, or repair the device logcat capture path first. The app-owned
   JNI smoke executed and wrote its marker to a file, but logcat still missed
   `QAIRT244_SMOKE`.
2. Fix the diagnostics collector's local APK metadata path so customnpu native
   metadata is not mixed with `standardDebug` APK extraction.
3. Design an isolated ADSP/QNN path dry-run only after native logcat capture is
   proven and later dispatch logs reach QNN path setup.
   Do not do this speculatively before the missing initialization log is known.
4. Implement the non-generating C++ initialize-only CLI target only after the
   Android dry-run logs are inspected.
   Do not execute upstream `litert_lm_main`.
5. Prepare an upstream issue update with the exact QAIRT 2.44 rebuild result and
   the model's `v2.44.0.260225143659` marker.

## Dispatch Logging Update

Result date: 2026-05-21

Artifact:

```text
artifacts/qairt244_dispatch_logging_build/20260521_085251/
```

The logging build adds `QAIRT244_DIAG` lines for dispatch library discovery,
dispatch `dlopen`/`dlsym`, `LiteRtDispatchGetApi`,
`LiteRtDispatchCheckRuntimeCompatibility`, Qualcomm `QnnManager::Create`,
QNN library loading, `ADSP_LIBRARY_PATH`, HTP init, device context creation, and
`has_dispatch_runtime` transitions.

The dry-run was not executed because no adb device was connected, so H1-H6
confidence does not change yet. The next evidence-producing step is the single
allowed connected-device dry-run with this artifact.

## Most Likely Cause

The strongest current classification is dispatch runtime initialization failure
inside the Qualcomm/LiteRT dispatch path, before a usable dispatch runtime is
registered for delegate kernel creation.

The most likely underlying causes are either:

- QNN/HTP runtime/provider/API/device setup failing inside
  `QnnManager::Create` or HTP backend initialization, or
- Android app namespace/path state preventing the dispatch runtime from loading
  the QNN pieces it needs, with the specific status hidden by the current fatal.

The model appears aligned with QAIRT 2.44 and SM8750/V79, so model generation
mismatch is no longer the lead hypothesis unless later logs show context binary
loading is actually reached.

## No-Run Confirmation

This coordinator pass did not run:

- `generateResponse`
- prompt generation
- `Conversation` creation
- `Session` creation
- `selectedPath=npu`
- normal UI `Backend.NPU` wiring
- single-token smoke test
- more than the single allowed JNI-sentinel `Engine.initialize` dry-run
- unsafe native library replacement
