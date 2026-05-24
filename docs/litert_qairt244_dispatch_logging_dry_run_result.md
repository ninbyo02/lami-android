# QAIRT 2.44 Dispatch Logging Dry-Run Result

Date: 2026-05-21

Scope: planning and preflight collection for the one allowed explicit `Engine.initialize` dry-run after Agent A provides a new detailed logging build artifact. This document is currently a template/preflight note, not a completed dry-run result.

## Current Status

- Dry-run status: not run.
- Reason: no adb device was connected after the detailed logging build was
  produced.
- Detailed logging build artifact:
  `artifacts/qairt244_dispatch_logging_build/20260521_085251/`
- Device status at preflight: no connected adb device.
- Preflight artifacts:
  - `artifacts/qairt244_dispatch_logging_dry_run/20260521_085251/preflight_device_props.txt`
  - `artifacts/qairt244_dispatch_logging_dry_run/20260521_085251/preflight_device_paths.txt`

No `Engine.initialize`, `Conversation`, `Session`, `generateResponse`, selected
`npu` path, normal UI NPU wiring, single-token smoke, root command, or
`app/src/main/jniLibs` change was performed in this pass.

The one allowed detailed-logging `Engine.initialize` dry-run remains unused.

## Detailed Logging Artifact

The available logging stack is:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `04b7b85497a519e131777b55e6c9b456` | `794fd19a7067ba73abee4a8e6b4afbc805b1915106319799298ccd021df3307c` |
| `libLiteRtDispatch_Qualcomm.so` | `50f4dbc09b133acb5973747555f06bc1` | `30c3401b5df9d6e1b87517a6b89882f952e8d3790acade21ebf8931993f95f24` |
| `liblitertlm_jni.so` | `30ee8163ec17e1624a25f6936a163f9e` | `4d4302eac72ad3421eb22a96bf810b91cc0f4a9b8cc45e39a9eaf531b33e9c10` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `da4a7a69d0a36ad68a6dd10e6c183d62` | `2d3cc83e50e6522ff4d6423c02caa9c9a349b0fcd0933edfe60d326e83d701ea` |

`strings` verification found `QAIRT244_DIAG` markers in `libLiteRt.so` and
`libLiteRtDispatch_Qualcomm.so`.

## Script Review

`scripts/run_custom_build_stack_probe.sh` is the preferred coordinator entry point for the custom QAIRT stack once the logging APK/build artifact exists. Relevant behavior:

- Stages a supplied custom build artifact directory through `scripts/stage_litert_custom_build_stack_for_experiment.sh`.
- Builds `:app:assembleCustomBuildExperimentDebug`.
- Checks the packaged APK native stack and leakage into other flavors.
- Installs `customBuildExperimentDebug` only if an adb device is connected.
- Starts `NpuExperimentProbeActivity` with `run_engine_initialize_dry_run=false` by default.
- Runs the explicit `Engine.initialize` dry-run only with `--engine-dry-run`.
- Uses app id `io.github.ninbyo02.lami.customnpu`.
- Writes/reads:
  - `files/npu_experiment_probe.txt`
  - `files/npu_engine_initialize_dry_run.txt`
  - `files/npu_engine_initialize_crash_marker.txt`
  - `files/npu_engine_initialize_last_stage.txt`
- Rejects dry-run results when custom stack Build IDs do not match the expected QAIRT 2.44 stack.

`scripts/collect_npu_tombstone_diagnostics_v2.sh` is the better post-run collector for mapped-library analysis. Relevant behavior:

- Accepts `--app-id`, `--label`, `--run-id`, and `--output-dir`.
- Captures device props, package dump, logcat tail, dropbox, tombstone context, stage files, and probe snapshot.
- Builds `native_lib_build_ids.txt`, `loaded_libs_matrix.tsv`, `loaded_libs_summary.md`, `abort_text_candidates.txt`, and `crash_summary.md`.
- Classifies failures including `no-usable-dispatch-runtime`, `insufficient-capabilities`, `runtime-c-api-missing`, `qnn-path-problem`, and `model-runtime-schema-mismatch`.

## Planned One-Time Dry-Run

Prerequisites:

- Agent A provides the detailed logging build artifact path.
- An adb device is connected and visible as `device`.
- The target SM8750 LiteRT-LM model is present in the app files or staged to `/data/local/tmp/gemma-4-E2B-it_qualcomm_sm8750.litertlm`.
- Coordinator explicitly authorizes this agent to execute the dry-run after the artifact path is known.

Planned command shape:

```bash
bash scripts/run_custom_build_stack_probe.sh \
  artifacts/qairt244_dispatch_logging_build/20260521_085251 \
  --engine-dry-run
```

Optional model override if the coordinator provides one:

```bash
bash scripts/run_custom_build_stack_probe.sh \
  artifacts/qairt244_dispatch_logging_build/20260521_085251 \
  --engine-dry-run \
  --model-path <APP_VISIBLE_MODEL_PATH>
```

If a crash or missing snapshot is suspected, collect diagnostics with:

```bash
bash scripts/collect_npu_tombstone_diagnostics_v2.sh \
  --app-id io.github.ninbyo02.lami.customnpu \
  --label qairt244_dispatch_logging \
  --run-id <RUN_ID_FROM_PROBE> \
  --output-dir artifacts/qairt244_dispatch_logging_dry_run/20260521_085251/diagnostics
```

## APK Lib Checklist

Fill this from `unzip -l <apk>` and `native_lib_build_ids.txt` after the logging APK exists.

| Library | Expected in APK | Present in APK | Build ID | SHA-256 | Notes |
| --- | --- | --- | --- | --- | --- |
| `liblitertlm_jni.so` | yes | TBD | TBD | TBD | LiteRT-LM JNI entrypoint |
| `libLiteRt.so` | yes | TBD | TBD | TBD | LiteRT runtime |
| `libLiteRtDispatch_Qualcomm.so` | yes | TBD | TBD | TBD | Qualcomm dispatch runtime |
| `libLiteRtCompilerPlugin_Qualcomm.so` | yes | TBD | TBD | TBD | Custom stack compiler plugin |
| `libGemmaModelConstraintProvider.so` | yes | TBD | TBD | TBD | Custom stack Gemma provider |
| `libQnnSystem.so` | yes | TBD | TBD | TBD | QNN system |
| `libQnnHtp.so` | yes | TBD | TBD | TBD | QNN HTP backend |
| `libQnnHtpPrepare.so` | yes | TBD | TBD | TBD | QNN HTP prepare |
| `libQnnHtpV79Stub.so` | yes | TBD | TBD | TBD | V79 arm64 stub; depends on `libcdsprpc.so` |
| `libQnnHtpV79Skel.so` | yes | TBD | TBD | TBD | DSP-side payload packaged for loader visibility |
| `libLiteRtRuntimeCApi.so` | unknown | TBD | TBD | TBD | Track if runtime generation expects it |
| `libllm_inference_engine_jni.so` | unknown | TBD | TBD | TBD | Track if packaged by current flavor |

## Mapped-Libs Evaluation Fields

Fill this from `loaded_libs_matrix.tsv`, tombstone maps, logcat, and `crash_summary.md` after the dry-run.

| Field | Value |
| --- | --- |
| Run ID | TBD |
| Logging artifact path | TBD |
| APK path | TBD |
| Application ID | `io.github.ninbyo02.lami.customnpu` |
| Device model | TBD |
| SoC / DSP props | TBD |
| Process alive after probe | TBD |
| Final stage before completion/crash | TBD |
| Crash marker completed | TBD |
| Tombstone/dropbox selected | TBD |
| Abort message | TBD |
| Likely abort/register/log text | TBD |
| Classification | TBD |
| `liblitertlm_jni.so` mapped | TBD |
| `libLiteRt.so` mapped | TBD |
| `libLiteRtDispatch_Qualcomm.so` mapped | TBD |
| `libLiteRtCompilerPlugin_Qualcomm.so` mapped | TBD |
| `libGemmaModelConstraintProvider.so` mapped | TBD |
| `libQnnSystem.so` mapped | TBD |
| `libQnnHtp.so` mapped | TBD |
| `libQnnHtpPrepare.so` mapped | TBD |
| `libQnnHtpV79Stub.so` mapped | TBD |
| `libQnnHtpV79Skel.so` mapped | TBD |
| `libLiteRtRuntimeCApi.so` mapped or missing-error | TBD |
| `/dev/*rpc*` visibility | TBD |
| `/vendor/lib64` QNN/HTP/DSP visibility | TBD |
| `/system_ext/lib64` QNN/HTP/DSP visibility | TBD |
| `/vendor/dsp`, `/vendor/lib/rfsa`, `/dsp`, `/firmware` visibility | TBD |

## Result Placeholder

Final result is pending. The coordinator should replace this section after the authorized dry-run with:

- exact command executed
- logging artifact path
- run id
- APK native library checklist
- mapped-library matrix
- crash/no-crash outcome
- dispatch/QNN/ADSP path conclusion
- whether the new detailed logs explain the previous `No usable Dispatch runtime found` failure
