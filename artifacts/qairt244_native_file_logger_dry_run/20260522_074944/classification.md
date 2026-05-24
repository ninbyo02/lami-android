# QAIRT 2.44 Native File Logger Dry-Run Classification

Date: 2026-05-22

## Inputs

- build artifact: `artifacts/qairt244_native_file_logger_build/20260522_074639/`
- diagnostics artifact: `artifacts/npu_diagnostics/20260522_074944_customnpu/`
- curated artifact: `artifacts/qairt244_native_file_logger_dry_run/20260522_074944/`
- diagnostic file: `/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_native_diag.txt`

## Result

- `qairt244_native_diag.txt` created: yes
- `nativeCreateEngine ENTRY`: reached
- `ModelAssets::Create`: success
- backend enum conversion: success
- `EngineSettings::CreateDefault`: success
- `SetLitertDispatchLibDir`: called with main native library dir
- `EngineFactory::CreateDefault`: entered, did not return
- `DispatchDelegate::Initialize`: reached
- `InitializeDispatchApi`: reached
- `LiteRtDispatchInitialize`: failed
- status: `kLiteRtStatusErrorDynamicLoading(502)`
- `LiteRtDispatchCheckRuntimeCompatibility`: not reached in this log
- QNN manager / QNN dlopen: not reached in this log
- final fatal boundary: `DispatchDelegate::CreateDelegateKernelInterface FATAL no usable dispatch runtime`

## Classification

The crash is no longer only a generic `No usable Dispatch runtime found` tombstone.
The file-backed native diagnostics show the immediate failure is:

```text
InitializeDispatchApi LiteRtDispatchInitialize failure status=kLiteRtStatusErrorDynamicLoading(502)
```

This places the current boundary inside dispatch runtime dynamic loading, before
dispatch compatibility checking and before visible QNN/HTP/skel initialization.

Most likely branch: dispatch runtime dynamic-loading/path discovery failure.

Next evidence-producing step: add file-backed diagnostics inside the lower-level
`LiteRtDispatchInitialize` implementation and dynamic loader path selection so
the exact candidate path and `dlerror` can be captured.
