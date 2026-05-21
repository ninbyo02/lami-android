# QAIRT 2.44 Android Logcat Root Cause Update

Date: 2026-05-21

## Result

The Android-native logcat build was produced and tested once with the allowed
`customBuildExperimentDebug` explicit `Engine.initialize` dry-run.

- build artifact: `artifacts/qairt244_android_log_build/20260521_210911/`
- dry-run diagnostics: `artifacts/npu_diagnostics/20260521_211841_customnpu/`
- curated dry-run artifact:
  `artifacts/qairt244_android_log_dry_run/20260521_211841/`
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- crash: `SIGABRT`
- `QAIRT244_DIAG` in collected logcat/dropbox/tombstone artifacts: no
- `qairt244_android_log_v1` in collected logcat/dropbox/tombstone artifacts: no
- tombstone top app frame:
  `DispatchDelegate::CreateDelegateKernelInterface()+464`
- tombstone top app BuildId: `27bb6eaa5358f3c23f080cdd33023eac`

The android-log build is therefore installed and executing far enough for the
tombstone to resolve into the rebuilt JNI library. The absence of direct logcat
lines means the next boundary is earlier and narrower than expected.

## Matrix Delta

| Branch | Result |
| --- | --- |
| QAIRT244_DIAG absent | Confirmed, even with direct `__android_log_print`. |
| CreateDelegateKernelInterface entry only | Not observed in logcat, but tombstone top frame is inside this function. |
| InitializeDispatchApi reached | Not observed. |
| LiteRtDispatchGetApi reached | Not observed. |
| Compatibility check reached | Not observed. |
| QNN dlopen reached | Not observed. |

## Current Interpretation

The failure still presents as `has_dispatch_runtime_ == false` when TFLite asks
for a dispatch delegate kernel. The mapped-library matrix again shows
`liblitertlm_jni.so` mapped but not `libLiteRt.so`,
`libLiteRtDispatch_Qualcomm.so`, or the QNN/HTP libraries.

The new evidence shifts the next debugging step away from QNN/HTP path changes.
Before another path experiment, we need to prove a direct log line from a
location that is definitely earlier than `CreateDelegateKernelInterface`, or
prove that logcat collection itself misses app-native `__android_log_print`.

## Recommended Next Step

Add one earlier app-native sentinel in the already-loaded JNI path, before
`Engine.initialize` reaches LiteRT delegate creation. A good target is the JNI
entry used by `Engine.initialize` or a guaranteed LiteRT-LM engine creation
entry. Then repeat only an initialize dry-run.

Do not change QNN paths, `ADSP_LIBRARY_PATH`, normal UI NPU routing, or run
generation until direct native log visibility is proven.
