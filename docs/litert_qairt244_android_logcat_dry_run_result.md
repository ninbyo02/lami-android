# QAIRT 2.44 Android Logcat Dry-Run Result

Date: 2026-05-21

Scope: Agent B preflight, Android-log build dry-run, and post-crash artifact
classification. The one allowed explicit `Engine.initialize` dry-run was
executed once with the Android logcat build.

## Actual Android-Log Dry-Run Result

- Build artifact:
  `artifacts/qairt244_android_log_build/20260521_210911/`
- Dry-run diagnostics:
  `artifacts/npu_diagnostics/20260521_211841_customnpu/`
- Curated dry-run artifact:
  `artifacts/qairt244_android_log_dry_run/20260521_211841/`
- Dry-run command:
  `bash scripts/run_custom_build_stack_probe.sh artifacts/qairt244_android_log_build/20260521_210911 --engine-dry-run --model-path /data/user/0/io.github.ninbyo02.lami.customnpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm`
- Final stage:
  `Engine.initialize invoking method=Engine.initialize(): void`
- Process result: `SIGABRT`, process not running after probe.
- `QAIRT244_DIAG` in collected diagnostics: not found.
- `qairt244_android_log_v1` in collected diagnostics: not found.
- Additional `adb logcat -d -t 2000` check after collection: no matching
  `QAIRT244_DIAG` or `qairt244_android_log_v1` lines.

The tombstone proves the Android-log build's JNI library was on the crash path:

```text
liblitertlm_jni.so ((anonymous namespace)::DispatchDelegate::CreateDelegateKernelInterface()+464)
BuildId: 27bb6eaa5358f3c23f080cdd33023eac
```

The direct logcat marker is present in the built libraries before staging, but
not visible in logcat/dropbox/tombstone artifacts after the crash.

## Status

- Dry-run status: run exactly once.
- Current adb availability during preflight: one device was available.
- Explicitly not run: `Conversation`, `Session`, `generateResponse`,
  selectedPath=`npu`, normal UI NPU inference, or single-token smoke.
- Preliminary evidence directory:
  `artifacts/qairt244_android_log_dry_run/20260521_210109/`
- Actual evidence directory:
  `artifacts/qairt244_android_log_dry_run/20260521_211841/`

## QAIRT244_DIAG / qairt244_android_log_v1 Artifact Check

Repository artifact-name search found no existing artifact path containing
`QAIRT244_DIAG` and no path containing `qairt244_android_log_v1`.

The prior detailed dispatch logging build exists at:

`artifacts/qairt244_dispatch_logging_build/20260521_085251/`

Its `diagnostic_string_check.txt` reports that `QAIRT244_DIAG` strings are
present in `built_libs/libLiteRt.so` and
`built_libs/libLiteRtDispatch_Qualcomm.so`, but this is not the requested new
Android logcat build artifact.

## Existing CustomNPU Crash Evidence

Source diagnostics:

`artifacts/npu_diagnostics/20260521_205243_customnpu/`

Key facts from `crash_summary.md`:

- applicationId: `io.github.ninbyo02.lami.customnpu`
- runId: `1779364308222`
- device: `NX733J`
- tombstone: `/data/tombstones/tombstone_12`
- tombstone selection: `latest-tombstone-matches-app`
- process after probe: `not-running`
- signal: `signal 6 (SIGABRT), code -1 (SI_QUEUE)`
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- top app frame:
  `liblitertlm_jni.so ((anonymous namespace)::DispatchDelegate::CreateDelegateKernelInterface()+372)`
- top app frame BuildId: `30ee8163ec17e1624a25f6936a163f9e`
- abort message field: `not-found`
- register fragment evidence:
  `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- classification: `no-usable-dispatch-runtime`

## Stage / Probe / Logcat Artifacts

Preliminary copied files:

- `artifacts/qairt244_android_log_dry_run/20260521_210109/stage_file.txt`
- `artifacts/qairt244_android_log_dry_run/20260521_210109/probe_snapshot.txt`
- `artifacts/qairt244_android_log_dry_run/20260521_210109/tombstone_app_extract.txt`
- `artifacts/qairt244_android_log_dry_run/20260521_210109/loaded_libs_matrix.tsv`
- `artifacts/qairt244_android_log_dry_run/20260521_210109/logcat_filtered.txt`

Notes:

- `stage_file.txt` contains the dry-run stage sequence through
  `Engine.initialize invoking method=Engine.initialize(): void`.
- `probe_snapshot.txt` is `<missing>` in the source diagnostics; its stderr says
  `cat: files/npu_experiment_probe.txt: No such file or directory`.
- `logcat_litert_qnn_extract.txt` from the source diagnostics is empty, so the
  copied `logcat_filtered.txt` is also empty.

## Mapped Library Evidence

From `loaded_libs_matrix.tsv`:

| Library | Mapped in tombstone | Present in nativeLibraryDir/APK |
| --- | --- | --- |
| `liblitertlm_jni.so` | true | true |
| `libLiteRt.so` | false | true |
| `libLiteRtDispatch_Qualcomm.so` | false | true |
| `libQnnSystem.so` | false | true |
| `libQnnHtp.so` | false | true |
| `libQnnHtpPrepare.so` | false | true |
| `libQnnHtpV79Stub.so` | false | true |
| `libQnnHtpV79Skel.so` | false | true |
| `libLiteRtRuntimeCApi.so` | false | false |
| `libllm_inference_engine_jni.so` | true | true |

The tombstone maps therefore show the crash in `liblitertlm_jni.so` before the
LiteRT dispatch and QNN shared libraries are mapped.

## Latest CustomNPU Artifact Extraction

For the existing customnpu diagnostic set, the latest complete source artifact
is:

`artifacts/npu_diagnostics/20260521_205243_customnpu/`

To extract the same evidence from a later customnpu run without starting a new
dry-run, use the newest directory under `artifacts/npu_diagnostics/` whose name
ends in `_customnpu`, then copy or inspect these files:

- `stage_file.txt`
- `probe_snapshot.txt`
- `tombstone_app_extract.txt`
- `loaded_libs_matrix.tsv`
- `logcat_litert_qnn_extract.txt`
- `crash_summary.md`
- `native_lib_build_ids.txt`

After the authorized one-time Android logcat build dry-run, collect with:

```bash
bash scripts/collect_npu_tombstone_diagnostics_v2.sh \
  --app-id io.github.ninbyo02.lami.customnpu \
  --label qairt244_android_log \
  --run-id <RUN_ID_FROM_PROBE> \
  --output-dir artifacts/qairt244_android_log_dry_run/<timestamp>/diagnostics
```

## Classification After Actual Dry-Run

The Android-log build did not solve visibility by itself. Since the top frame is
inside the new `liblitertlm_jni.so`, this is no longer simply "old build was
installed". Remaining explanations are:

- the fatal path executes before the direct log call location despite resolving
  to `CreateDelegateKernelInterface`;
- the device logcat window misses the short-lived process lines and the buffer
  is empty by post-collection;
- the source object carrying the direct marker is present in the built library
  but a different linked implementation is executing;
- the collector's APK-lib extraction still has a standard APK path bug, so
  tombstone Build IDs are more reliable than copied `apk_libs/` for this pass.

Mapped-library evidence remains unchanged in the important way: the tombstone
maps `liblitertlm_jni.so` but not `libLiteRt.so`,
`libLiteRtDispatch_Qualcomm.so`, or the QNN/HTP libraries before abort.
