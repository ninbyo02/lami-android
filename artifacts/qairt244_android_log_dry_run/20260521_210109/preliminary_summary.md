# QAIRT244 Android Log Dry-Run Preliminary Summary

- Created: 2026-05-21 21:01:09 JST
- Dry-run: not run
- Reason: coordinator has not provided `artifacts/qairt244_android_log_build/<timestamp>`
- ADB read-only check: `192.168.52.52:42233	device`
- Existing source diagnostics:
  `artifacts/npu_diagnostics/20260521_205243_customnpu/`
- Previous dispatch logging build:
  `artifacts/qairt244_dispatch_logging_build/20260521_085251/`

## Evidence Copied

- `stage_file.txt`
- `probe_snapshot.txt`
- `tombstone_app_extract.txt`
- `loaded_libs_matrix.tsv`
- `logcat_filtered.txt`

## Tombstone Evidence

- runId: `1779364308222`
- tombstone: `/data/tombstones/tombstone_12`
- signal: `SIGABRT`
- top app frame:
  `liblitertlm_jni.so ((anonymous namespace)::DispatchDelegate::CreateDelegateKernelInterface()+372)`
- BuildId: `30ee8163ec17e1624a25f6936a163f9e`
- final stage:
  `Engine.initialize invoking method=Engine.initialize(): void`
- register fragment text:
  `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- classification: `no-usable-dispatch-runtime`

## Artifact Absence

No existing artifact path named with `QAIRT244_DIAG` or
`qairt244_android_log_v1` was found.
