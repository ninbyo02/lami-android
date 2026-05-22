# LiteRT / LiteRT-LM Custom Build Plan

Date: 2026-05-16

This plan prepares a custom build path without building or installing any native artifact yet.

## Goal

Find or produce a Qualcomm dispatch/runtime stack that is generation-compatible with the LiteRT-LM Android Java/JNI/runtime stack used by Lami on SM8750.

The current failure is not a missing file. `libLiteRtDispatch_Qualcomm.so` is present and mapped, but `Engine.initialize()` aborts with evidence consistent with:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

## Phase 0: No-Build Investigation

Status: in progress / mostly complete.

Tasks:

- Map source tags and commits.
- Inventory Bazel targets.
- Snapshot local environment readiness.
- Prepare issue report and artifact bundle.

Important prepared issue artifacts:

- Issue body: `docs/google_ai_edge_issue_body_litertlm_sm8750_npu.md`
- Light artifact bundle: `artifacts/npu_issue_bundle/20260516_213710_light.zip`

Official maintainer guidance is recommended before investing in a custom runtime stack, but this plan also prepares a safe local path if the user chooses to continue.

## Phase 1: Source Checkout Only

Allowed:

- Clone or update source repositories.
- Checkout candidate tags/commits.
- Inspect build files.
- Do not build.

Initial source candidates:

- `google-ai-edge/LiteRT-LM` tag `v0.11.0` (`c87189528a758db32ead241f4fc9c64836398ee7`)
- `google-ai-edge/LiteRT` commit `47615eb6eaec25e8dfcd1aba922c560a57cba0a2` pinned by LiteRT-LM `v0.11.0`
- `google-ai-edge/gallery` tag `1.0.12` (`302f7e463b19f45f51825f4ec2fd30309366cb06`)

## Phase 2: Dry Build Query

Allowed only after Bazel/Bazelisk and Android NDK are configured:

- `bazel query`
- `bazel cquery`
- target visibility and dependency graph checks

No native artifact copy, no app integration.

Query first:

```bash
bazel query '@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so'
bazel query '@litert//litert/c:litert_runtime_c_api_so'
bazel query '@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so'
bazel query '//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni'
```

## Phase 3: Build Isolated Artifact

Only after source/tag confidence is acceptable.

Output location:

```text
artifacts/litert_custom_build/<timestamp>/
```

Do not install into the app automatically.

First build candidates:

1. `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so`
2. `@litert//litert/c:litert_runtime_c_api_so`
3. `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni`

If the issue is dispatch API layout mismatch, building dispatch alone may still fail. Prefer building enough of the matched stack for static comparison before any app test.

## Phase 4: Static Compare

Compare built artifacts against:

- Gallery SM8750 native stack.
- Maven `litertlm-android:0.11.0`.
- Current `galleryStackExperimentDebug` APK.

Required checks:

- SHA-256.
- GNU Build ID.
- `NEEDED`.
- SONAME.
- exported symbols.
- undefined symbols.
- `LiteRtDispatchGetApi`.
- `LiteRtDispatchCheckRuntimeCompatibility`.
- capability/version strings.
- QNN SDK version strings.

## Phase 5: Isolated galleryStackExperiment Replacement

Only after manual approval.

Allowed only in:

```text
app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a/
```

Forbidden:

- `app/src/main/jniLibs`
- `standardDebug`
- existing normal inference path

Validation remains:

- `Backend.NPU(String)` instantiate.
- `EngineConfig` dry-build.
- explicit opt-in `Engine.initialize` dry-run only.
- no `Conversation`.
- no `Session`.
- no `generateResponse`.

Updated direction after same-source/tag stack completion:

- use a new `customBuildExperimentDebug` flavor instead of replacing `galleryStackExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.customnpu`
- stage the built stack as a unit:
  - `liblitertlm_jni.so`
  - `libLiteRt.so`
  - `libLiteRtDispatch_Qualcomm.so`
  - `libLiteRtCompilerPlugin_Qualcomm.so`
  - `libGemmaModelConstraintProvider.so`
- do not copy QNN SDK libraries from QAIRT
- run only explicit opt-in `Engine.initialize` dry-run

## Phase 6: If Engine.initialize Succeeds

Do not immediately wire NPU into the app.

Next phase would be designing a fully isolated single-token smoke test:

- separate applicationId/flavor
- explicit opt-in
- model path provided by intent/script
- no normal UI selectedPath change
- no held engine reuse

## Current Recommendation

Current status update, 2026-05-21:

- QAIRT 2.44 exact limited rebuild was completed.
- Android-native dispatch/QNN logging build was completed.
- JNI-entry sentinel build was completed:
  `artifacts/qairt244_jni_sentinel_build/20260521_214511/`
- the allowed initialize-only dry-run still aborts at `Engine.initialize`
  without returning:
  `artifacts/npu_diagnostics/20260521_215004_customnpu/`
- tombstone proves the sentinel `liblitertlm_jni.so` is installed and
  `nativeCreateEngine` is on the stack, but `QAIRT244_SENTINEL` is still not
  captured

Next recommendation:

1. Use file-backed native diagnostics rather than logcat for this device.
2. Instrument lower-level `LiteRtDispatchInitialize` / dynamic loader path
   selection to capture candidate paths and `dlerror`.
3. Only after dispatch dynamic loading reaches QNN/HTP code, evaluate
   ADSP/QNN path changes.

Current status update, 2026-05-22:

- app-owned JNI smoke proved native code executes and can write app-private
  files while `__android_log_print` tags are not captured
- native file logger build was completed:
  `artifacts/qairt244_native_file_logger_build/20260522_074639/`
- the allowed initialize-only dry-run still aborts at `Engine.initialize`
  without returning:
  `artifacts/npu_diagnostics/20260522_074944_customnpu/`
- the app-private file logger reached `nativeCreateEngine`,
  `ModelAssets::Create`, `EngineSettings::CreateDefault`,
  `SetLitertDispatchLibDir`, `EngineFactory::CreateDefault`,
  `DispatchDelegate::Initialize`, and `InitializeDispatchApi`
- the first concrete native failure is
  `LiteRtDispatchInitialize failure status=kLiteRtStatusErrorDynamicLoading(502)`

Updated next recommendation:

Add file-backed diagnostics inside the lower-level dispatch dynamic loader to
capture the exact library candidate path and `dlerror`. Do not run generation,
Conversation, Session, single-token smoke, or speculative ADSP/QNN path changes
until that evidence is collected.

Current status update, 2026-05-22 dlopen trace:

- lower-level dispatch dynamic loading diagnostics were added:
  `qairt244_dlopen_trace_v1`
- build artifact:
  `artifacts/qairt244_dlopen_trace_build/20260522_083658/`
- custom probe now sets and clears the customnpu-only linker debug property:
  `debug.ld.app.io.github.ninbyo02.lami.customnpu=dlerror,dlopen,dlsym`
- the connected-device dry-run was not executed because no adb device was
  connected
- attempt artifact:
  `artifacts/qairt244_dlopen_trace_dry_run/20260522_083818_no_device/`

Updated next recommendation:

Superseded by connected-device symbol-resolution, QNN runtime alignment, and
HTP backend trace runs.

Current status update, 2026-05-22 libcdsprpc manifest visibility:

- adding
  `<uses-native-library android:name="libcdsprpc.so" android:required="false" />`
  to a `customBuildExperimentDebug`-only manifest makes the vendor FastRPC host
  library visible to the app linker namespace
- `libcdsprpc.so` is not packaged or redistributed by the APK
- `QnnDevice_create` now succeeds
- `LiteRtDispatchCheckRuntimeCompatibility` returns `kLiteRtStatusOk(0)`
- explicit initialize-only dry-run returns successfully and closes the engine

Current artifact:

```text
artifacts/qairt244_libcdsprpc_manifest_experiment/20260522_231302/
```

Updated next recommendation:

1. Keep the `uses-native-library libcdsprpc.so` declaration isolated to
   `customBuildExperimentDebug`.
2. Treat `Engine.initialize` as proven only for the explicit dry-run path.
3. Do not connect `Backend.NPU` to normal UI inference yet.
4. Next, design a separate initialize-only or CLI proof plan for capability and
   runtime stability before any generation test.

2026-05-23 initialize stability update:

- `scripts/run_qairt244_initialize_stability_probe.sh` was added for
  customBuildExperimentDebug-only repeated initialize/close checks.
- Artifact:
  `artifacts/qairt244_initialize_stability/20260523_043345/`
- Results: `Engine.initialize` returned successfully `2/2`; `Engine.close`
  returned successfully `2/2`; the APK was installed once and the Activity was
  launched twice.
- No `Conversation`, `Session`, `generateResponse`, or NPU inference was run.
- The next phase remains a separately approved single-token smoke design, not
  normal UI wiring.

2026-05-23 single-token smoke prep:

- `scripts/run_qairt244_single_token_smoke.sh` was added as a blocking preflight
  script.
- Current classification: `maxOutputTokens=1-not-guaranteed`.
- The current Kotlin/JNI app surface does not expose a hard one-token decode
  cap; future implementation should use a customBuildExperimentDebug-only
  lower-level JNI or CLI path that calls `DecodeConfig.SetMaxOutputTokens(1)`.
- No `Conversation`, `Session`, `generateResponse`, or token generation was run.

2026-05-23 lower-level single-token smoke preflight:

- `scripts/run_qairt244_lower_level_single_token_smoke.sh` checks the exact
  lower-level requirement.
- Artifact:
  `artifacts/qairt244_lower_level_single_token_smoke/20260523_052224/`
- Classification: `entrypoint-implemented-not-executed`.
- LiteRT-LM C++ has the required `DecodeConfig.SetMaxOutputTokens(1)` primitive.
- A custom `liblitertlm_jni.so` entrypoint and `customBuildExperimentDebug`
  wrapper are wired for explicit `runLowerLevelSingleTokenSmoke=true` only.
- Build artifact:
  `artifacts/qairt244_single_token_entrypoint_build/20260523_052106/`
- No install, app launch, `Conversation`, `generateResponse`, or token
  generation was run.

2026-05-23 lower-level single-token smoke execution:

- `scripts/run_qairt244_lower_level_single_token_smoke.sh` was run once with
  `--run`.
- Execution artifact:
  `artifacts/qairt244_lower_level_single_token_smoke/20260523_053258/`
- Result: `success`.
- Prompt: `Hi`.
- Hard cap: `max_output_tokens=1`.
- Output: `!`.
- Elapsed: `1115 ms`.
- Native diag confirms `before RunDecode SetMaxOutputTokens(1)` and
  `success output_candidates=1 output_bytes=1`.
- The run used the lower-level native session required for decode, but did not
  create a `Conversation`, did not call high-level `generateResponse`, and did
  not connect NPU to the normal UI.
- The diagnostics collector selected an older initialize tombstone; the smoke
  run's result file and native diag show success and the process remained
  alive.

2026-05-23 lower-level single-token smoke reproducibility:

- `scripts/run_qairt244_lower_level_single_token_smoke.sh` was updated to write
  run metadata and classify collector-selected tombstones as stale when they do
  not contain the current smoke run id.
- The smoke was run exactly once more with `--run`.
- Execution artifact:
  `artifacts/qairt244_lower_level_single_token_smoke/20260523_055024/`
- Result: `success`.
- Prompt: `Hi`.
- Hard cap: `max_output_tokens=1`.
- Output: `!`.
- Elapsed: `907 ms`.
- Native diag confirms `before RunDecode SetMaxOutputTokens(1)` and
  `success output_candidates=1 output_bytes=1`.
- Tombstone classification: `stale-tombstone-ignored`.
- Overall reproducibility: `2/2` lower-level one-token NPU smoke success.
- Still no `Conversation`, no high-level `generateResponse`, and no normal UI
  NPU connection.

2026-05-23 token timing verifier implementation:

- External LiteRT-LM JNI instrumentation was updated with marker
  `qairt244_token_timing_verifier_v1`.
- Verifier build artifact:
  `artifacts/qairt244_token_timing_verifier_build/20260523_060634/`
- Preflight artifact:
  `artifacts/qairt244_token_timing_verifier/20260523_061525/`
- The verifier result writer records prompt/output bytes, explicit unavailable
  token count sources, stage timings, and NPU backend evidence.
- The verifier was not executed because `adb devices` returned no connected
  Nubia device and the previous TCP endpoint refused connection.
- Next run remains exactly one isolated verifier run with `--run --verifier`;
  still no normal UI NPU connection.

2026-05-23 token timing verifier execution:

- `scripts/run_qairt244_lower_level_single_token_smoke.sh` was run exactly once
  with `--run --verifier` after the Nubia `NX733J` device was visible in
  `adb devices`.
- Execution artifact:
  `artifacts/qairt244_token_timing_verifier/20260523_062321/`
- Result: `success`.
- Prompt: `Hi`.
- Hard cap: `max_output_tokens=1`.
- Output: `!`.
- Total elapsed: `1053 ms`.
- Timings: `engine_create=905 ms`, `session_create=0 ms`,
  `prefill=13 ms`, `decode=22 ms`, `cleanup=111 ms`.
- Token counts are explicitly `unavailable`; the verifier records the source
  strings and does not infer counts from bytes or text.
- Native diagnostics show QNN HTP V79 FastRPC execution evidence.
- Tombstone classification: `stale-tombstone-ignored`; no fresh crash evidence.
- Still no high-level `generateResponse`, no `Conversation`, and no normal UI
  NPU connection.

Previous status update, 2026-05-22 HTP backend trace:

- dispatch `dlopen` works only after keeping a real
  `DT_NEEDED [libLiteRt.so]` edge on `libLiteRtDispatch_Qualcomm.so`
- QAIRT 2.44 QNN runtime alignment is required; it advances QNN System API to
  `1.8.0`
- `libQnnHtp.so` now loads and HTP provider API resolution succeeds
- HTP backend initialization reaches `QnnBackend_create`, which succeeds
- SoC detection selects `SM8750`, DSP arch `79`, and VTCM `8` MB
- the current immediate failure is:

```text
HtpBackend::Init -> QnnDevice_create -> status=14001
```

Current build/dry-run artifacts:

```text
artifacts/qairt244_htp_backend_trace_aligned_build/20260522_222215/
artifacts/qairt244_htp_backend_trace_dry_run/20260522_222434/
artifacts/npu_diagnostics/20260522_222434_customnpu/
```

Updated next recommendation:

1. Treat `QnnDevice_create` status `14001` as
   `QNN_DEVICE_ERROR_INVALID_CONFIG`; backend logs now show this is caused by
   `libQnnHtpV79Stub.so` failing to resolve `libcdsprpc.so` in Android linker
   namespace `clns-9`.
2. Design the next customBuildExperimentDebug-only experiment around
   `libcdsprpc.so` visibility or supported vendor namespace access.
3. Only after the V79 stub loads should unsigned-PD or skel path config be
   changed.
4. Continue to avoid `Conversation`, `Session`, `generateResponse`, normal UI
   NPU wiring, and single-token smoke until `Engine.initialize` returns.

Original pre-build guidance:

1. Ask maintainers which source tag/native artifact generation matches Gallery SM8750 and `litertlm-android:0.11.0`.
2. Install/configure Bazel/Bazelisk, Android NDK, and QAIRT/QNN SDK paths.
3. Run query/cquery only.
4. Build only into `artifacts/`, then static-compare before any app staging.

## Query/Cquery Phase Result

Result date: 2026-05-16

Artifact:

```text
artifacts/litert_custom_build_query/20260516_225450/
```

Environment now has:

- Bazelisk `v1.29.0`
- Bazel `7.6.1` selected by LiteRT-LM `.bazelversion`
- Android NDK `28.2.13676358` / r28c
- LiteRT-LM checkout `v0.11.0` at `/home/sato/project/litert-custom-build/LiteRT-LM`
- LiteRT checkout at pinned commit `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`

All targeted query/cquery commands succeeded:

```text
query_litertlm_jni                       0
query_qualcomm_dispatch                  0
query_litert_c                           0
query_qualcomm_compiler                  0
cquery_dispatch_android_arm64            0
cquery_litert_runtime_android_arm64      0
cquery_litertlm_jni_android_arm64        0
```

Visible and Android-arm64-queryable targets include:

- `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so`
- `@litert//litert/c:litert_runtime_c_api_so`
- `@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so`
- `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni`

The next phase can move to build only with explicit approval. Build output must go to an isolated `artifacts/litert_custom_build/<timestamp>/` directory, never directly into app source sets.

Recommended build order if approved:

1. `@litert//litert/c:litert_runtime_c_api_so`
2. `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so`
3. `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni`
4. static compare against Gallery SM8750 and Maven artifacts

Do not stage any result into `galleryStackExperimentDebug` until static compare is complete.

## Custom Build Experiment Phase

Implemented entry points:

- stage: `scripts/stage_litert_custom_build_stack_for_experiment.sh`
- run/probe: `scripts/run_custom_build_stack_probe.sh`
- docs: `docs/litert_custom_build_insertion_experiment.md`

Next decision depends on the dry-run classification:

- if initialize succeeds: design isolated single-token smoke test, still no normal UI NPU path
- if `No usable Dispatch runtime found` continues: investigate QAIRT version/model schema/dispatch capability
- if `UnsatisfiedLinkError`: add only the missing same-stack dependency after review
- if QNN/ADSP path appears: design path-specific experiment without touching standard flavor

Dry-run result on 2026-05-17:

- artifact: `artifacts/npu_diagnostics/20260517_005032_customnpu/`
- `Backend.NPU(String)`: success
- `EngineConfig`: success
- `Engine(EngineConfig)`: returned
- `Engine.initialize`: did not return
- signal: `SIGABRT`
- register fragments: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- classification: `no-usable-dispatch-runtime`
- confidence: `medium`

The next phase is not single-token smoke testing. The custom stack still fails before generation can be considered. Recommended next work:

1. Compare QAIRT/QNN version assumptions between the built stack, model, device runtime, and Gallery payload.
2. Inspect dispatch/runtime capability checks around `DispatchDelegate::CreateDelegateKernelInterface`.
3. Ask upstream maintainers whether additional QNN/HTP runtime setup or model/runtime pairing is required for SM8750.
4. Keep all further experiments in `customBuildExperimentDebug` or a new isolated flavor.

## QNN/QAIRT Coupling Static Pass

Result date: 2026-05-17

Artifact:

```text
artifacts/qairt_qnn_coupling/20260517_012057/
```

Detailed findings:

```text
docs/litert_qnn_qairt_coupling_findings.md
```

The static pass found that `customBuildExperimentDebug` packages QNN/HTP libraries, but those QNN libraries do not match either Gallery SM8750 Build IDs or the local QAIRT 2.46 Build IDs. The latest tombstone does not show the dispatch or QNN libraries mapped before abort; only `liblitertlm_jni.so`, `libGemmaModelConstraintProvider.so`, and `libllm_inference_engine_jni.so` are visible in the extracted map lines.

Next build-oriented options:

1. get exact QAIRT `2.44.0.260225`, rebuild the same limited targets, and static-compare;
2. identify a LiteRT source/ref that expects QAIRT `2.46.0.260424`, then query/build only into `artifacts/`;
3. prepare a separate, explicitly approved QNN-libs alignment experiment for `customBuildExperimentDebug`;
4. investigate a same-source `litert_lm_main` CLI path before any generation smoke test in Lami.

Do not proceed to single-token smoke until `Engine.initialize` returns successfully in an isolated flavor.

## QAIRT 2.44 Exact-Match Rebuild Gate

Result dates: 2026-05-17, updated 2026-05-21

Local search artifact:

```text
artifacts/qairt_244_exact_match/20260517_013958/local_search.txt
```

Initial 2026-05-17 state: the exact QAIRT `2.44.0.260225` SDK was not
installed. The existing path:

```text
/home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225
```

was a symlink to QAIRT `2.46.0.260424`, so it was not an exact-match input.

Initial status:

```text
blocked-awaiting-qairt244
```

Current status:

```text
qairt244-initialize-invoked-sigabrt-no-usable-dispatch-runtime
```

Update 2026-05-21:

- Exact QAIRT `2.44.0.260225` was acquired through QPM and installed at `/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225`.
- The limited qairt244 rebuild succeeded: `artifacts/litert_custom_build/20260517_230448_qairt244/`.
- The qairt244 native stack was staged only into `customBuildExperimentDebug`: `artifacts/litert_custom_build_stage/20260521_015803/`.
- `customBuildExperimentDebug` APK packaging and install succeeded.
- First initialize-only dry-run attempt `runId=1779296283194` was skipped with `custom-stack-build-id-mismatch` because expected Build IDs still pointed at the previous 2.46-overlay dispatch/compiler outputs.
- `Engine.initialize` was not invoked. No `Conversation`, `Session`, `generateResponse`, `selectedPath=npu`, normal UI `Backend.NPU` wiring, or single-token smoke test was run.

Current qairt244 expected custom stack:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` |
| `libLiteRtDispatch_Qualcomm.so` | `a8006da3bd9b4fdf5b7131f8d864b6ee` | `00c26484621ab42bea6e3bee0d7e908451a428cf19cbd1ebfecf4ccee79e1739` |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` | `310e37ff7cf770c24d636bbb0f9647a0d59dd893ba0c2530acdfc06569704230` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `443391d4c4348191230b67a3ab8a6037` | `c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c` |
| `libGemmaModelConstraintProvider.so` | `f9e5e73e668032550042319e43012011` | `45ca57e55d52976e5d2dadfc0e874499fc0671c169a28077772c25264f9d81f6` |

Next run will execute only the explicit opt-in `Engine.initialize` dry-run
candidate in `customBuildExperimentDebug`.

Dry-run update 2026-05-21:

- stage artifact: `artifacts/litert_custom_build_stage/20260521_074601/`
- runId: `1779317161924`
- diagnostics artifact: `artifacts/npu_diagnostics/20260521_074641_customnpu/`
- device tombstone: `/data/tombstones/tombstone_11`
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- `Engine.initialize` invoked: yes
- `Engine.initialize` returned: no
- signal: `SIGABRT`
- classification: `no-usable-dispatch-runtime`
- evidence text: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- no `Conversation`, `Session`, `generateResponse`, `selectedPath=npu`, normal UI NPU inference, or single-token smoke test was run.

The build script used for the exact SDK was:

```bash
bash scripts/build_litert_custom_artifacts.sh \
  ~/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244
```

Actual qairt244 output:

```text
artifacts/litert_custom_build/20260517_230448_qairt244/
```

After the expected Build ID guard update, the isolated
`customBuildExperimentDebug` explicit opt-in `Engine.initialize` dry-run reached
the initialize call and reproduced `No usable Dispatch runtime found`.

### Post-Failure Root Cause Split

Result date: 2026-05-21

Coordinator artifact:

```text
artifacts/qairt244_failure_analysis/20260521_081545/
```

Docs:

- `docs/litert_qairt244_tombstone_runtime_mapping.md`
- `docs/litert_qairt244_android_qnn_path_analysis.md`
- `docs/litert_dispatch_capability_source_trace.md`
- `docs/litert_lm_main_npu_cli_proof_plan.md`
- `docs/litert_qualcomm_sm8750_model_schema_probe.md`
- `docs/litert_qairt244_failure_root_cause_matrix.md`

Findings:

- qairt244 `customBuildExperimentDebug` mapped `liblitertlm_jni.so` and
  `libGemmaModelConstraintProvider.so` before abort, but did not map
  `libLiteRtDispatch_Qualcomm.so` or QNN/HTP libraries in the tombstone.
- The model contains `LITERTLM`, `DISPATCH_OP`, `qnn_partition_*`,
  `soc_type=SM8750`, `min_arch=79`, and `v2.44.0.260225143659` markers.
- Source trace shows the observed fatal is emitted after
  `InitializeDispatchApi()` fails and `has_dispatch_runtime_` is set false.
- Upstream `//runtime/engine:litert_lm_main` exists but is not safe to execute
  for this task because it creates a `Conversation` and sends a prompt.

Current next recommendation:

1. Add high-signal dispatch/QNN initialization logging and rebuild only the
   isolated qairt244 custom stack.
2. Refresh rootless device QNN/CDSP path collection when adb is connected.
3. Do not run CLI NPU proof until an initialize-only, non-generating CLI target
   exists.

### Acquisition Workflow Prepared

Probe artifact:

```text
artifacts/qairt244_acquisition/20260517_074537/
```

Result:

- QPM / Qualcomm Software Center CLI was not found locally
- `/opt/qcom/aistack/qairt/` was not present
- exact QAIRT `2.44.0.260225` was not found
- only QAIRT `2.46.0.260424` is currently installed

Prepared helpers:

```bash
bash scripts/check_qairt244_sdk.sh /path/to/qairt/2.44.0.260225
bash scripts/stage_qairt244_sdk_from_download.sh ~/Downloads/v2.44.0.260225.zip
bash scripts/run_qairt244_rebuild_compare.sh
```

The first two helpers are acquisition/staging only. They do not build LiteRT, do
not stage app native libraries, and do not run `Engine.initialize`.

## QAIRT 2.46 Source/Ref Search Gate

Result date: 2026-05-17

Artifact:

```text
artifacts/litert_qairt246_ref_search/20260517_062055/
```

Docs:

- `docs/litert_qairt246_source_ref_candidates.md`
- `docs/litert_qairt246_ref_search_results.md`

Result:

- local QAIRT `2.46.0.260424` exists
- public LiteRT `origin/main` still advertises QAIRT `2.44.0.260225`
- public LiteRT-LM `origin/main` pins LiteRT `d865fd82cd7fe6752908b3a0836895461c305679`
- that pinned LiteRT ref also advertises QAIRT `2.44.0.260225`
- no exact `2.46.0.260424`, `260424`, or `260424121129` evidence was found in bounded public LiteRT metadata refs
- query/cquery was not run because no QAIRT 2.46 source candidate was identified

Build decision:

Do not build another QAIRT 2.46 overlay stack as the next primary path. The
current public evidence favors exact QAIRT `2.44.0.260225` acquisition or
maintainer guidance for a QAIRT 2.46 source/ref.

## Limited Build Phase Result

Result date: 2026-05-16

Artifact:

```text
artifacts/litert_custom_build/20260516_232646/
```

Limited build targets were executed only for the approved Android arm64 targets:

```text
@litert//litert/c:litert_runtime_c_api_so                         success
@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so          success
//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni          failed
@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so    success
```

Built artifacts:

- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`

Not produced:

- `liblitertlm_jni.so`
- `libLiteRtRuntimeCApi.so`

`litertlm_jni` failed because `libGemmaModelConstraintProvider.so` in the source checkout is a Git LFS pointer rather than an ELF binary. This must be fixed before a source-matched LiteRT-LM JNI can be built.

Static comparison is recorded in:

- `docs/litert_custom_build_static_compare.md`
- `docs/litert_custom_build_results.md`

Next phase options:

1. Fetch/resolve the Git LFS prebuilt needed by `litertlm_jni`, then retry only the same `litertlm_jni` target.
2. If manual runtime testing is approved before JNI is available, test built `libLiteRt.so` and built `libLiteRtDispatch_Qualcomm.so` only in an isolated flavor. This is still risky because the JNI remains Gallery/Maven-derived.
3. Do not replace dispatch alone in `standardDebug`, `npuExperimentDebug`, or release.
4. Do not proceed to `Conversation` or `generateResponse` until an isolated `Engine.initialize` result is clean.

## JNI Build Completion Result

Result date: 2026-05-16

Artifacts:

```text
artifacts/litert_custom_build/20260516_235244/
artifacts/litert_custom_build_lfs/20260516_235237/
```

Git LFS was resolved only for LiteRT-LM Android arm64 prebuilts. After that, all limited Android arm64 targets succeeded:

```text
@litert//litert/c:litert_runtime_c_api_so                         success
@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so          success
//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni          success
@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so    success
```

The same source/tag build now has:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`

Future isolated insertion must also include the resolved `libGemmaModelConstraintProvider.so`, because the built JNI declares it in `NEEDED`.

Next recommended phase, only after explicit approval:

1. create a one-shot isolated staging script for the built stack
2. stage only into a debug-only experimental flavor/applicationId
3. run detection and `Backend.NPU` instantiate first
4. run explicit opt-in `Engine.initialize` dry-run only
5. do not run `Conversation`, `Session`, or `generateResponse`

## QAIRT 2.44 Symbol Resolution Experiment Result

Result date: 2026-05-22

Docs:

- `docs/litert_qairt244_symbol_resolution_experiment.md`

Artifacts:

```text
artifacts/qairt244_rtld_global_build/20260522_210118/
artifacts/qairt244_rtld_global_dry_run/20260522_210355/
artifacts/qairt244_dispatch_needed_build/20260522_210902/
artifacts/qairt244_dispatch_needed_dry_run/20260522_211136/
```

Result:

- `libLiteRtDispatch_Qualcomm.so` from the custom QAIRT 2.44 build originally
  lacked `DT_NEEDED [libLiteRt.so]`.
- preloading sibling `libLiteRt.so` with `RTLD_NOW | RTLD_GLOBAL` did not allow
  Android linker resolution of dispatch's undefined `LiteRtGetEnvironmentOptions`.
- adding `dynamic_deps = ["//litert/c:litert_runtime_c_api_so"]` to the
  Qualcomm dispatch shared library experiment produced `DT_NEEDED [libLiteRt.so]`.
- with that NEEDED edge, dispatch `dlopen`, `LiteRtDispatchGetApi`, and dispatch
  API version acceptance succeeded.
- the next failure is inside dispatch vendor initialization after
  `libQnnSystem.so` and `QnnSystemInterface_getProviders` load successfully.

Next recommended phase:

1. keep the NEEDED build as the next diagnostic baseline
2. add focused file logging around Qualcomm QNN System provider enumeration and
   backend selection
3. continue to avoid `Conversation`, `Session`, `generateResponse`, normal UI
   NPU wiring, and single-token smoke tests

## QAIRT 2.44 QNN Provider Trace Result

Result date: 2026-05-22

Docs:

- `docs/litert_qairt244_qnn_provider_trace_result.md`
- `docs/litert_qairt244_qnn_dependency_chain.md`

Artifacts:

```text
artifacts/qairt244_qnn_provider_trace_build/20260522_212620/
artifacts/qairt244_qnn_provider_trace_dry_run/20260522_212949/
artifacts/qairt244_qnn_dependency_analysis/20260522_212110/
artifacts/qairt244_qnn_dependency_analysis/20260522_212949/
```

Result:

- dispatch `dlopen`, `LiteRtDispatchGetApi`, and API version acceptance still
  succeed with `DT_NEEDED [libLiteRt.so]`
- `libQnnSystem.so` loads and `QnnSystemInterface_getProviders` succeeds
- provider count is `1`
- selected provider is `SYSTEM_QTI_AISW`
- detected QNN System API is `1.4.0`
- LiteRT expects QNN System API minimum `1.8.0`
- initialization fails before `libQnnHtp.so`, prepare, V79 stub/skel, or
  `LiteRtDispatchCheckRuntimeCompatibility`

Next recommended phase:

1. keep the dispatch `DT_NEEDED [libLiteRt.so]` fix
2. stage a generation-consistent QNN runtime set matching QAIRT 2.44/Gallery
   before making ADSP/FastRPC changes
3. repeat only the explicit `Engine.initialize` dry-run

## QAIRT 2.44 QNN Runtime Alignment Result

Result date: 2026-05-22

Docs:

- `docs/litert_qairt244_qnn_runtime_alignment_result.md`

Artifacts:

```text
artifacts/qairt244_qnn_aligned_build/20260522_215238/
artifacts/qairt244_qnn_aligned_dry_run/20260522_215421/
artifacts/npu_diagnostics/20260522_215421_customnpu/
```

Result:

- staged QAIRT 2.44 SDK `libQnnSystem.so`, `libQnnHtp.so`,
  `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and V79 skel into
  `customBuildExperimentDebug` only
- QNN System provider now reports API `1.8.0`
- `ResolveSystemApi` succeeds
- `libQnnHtp.so` loads successfully
- HTP provider `HTP_QTI_AISW` reports core `2.33.0`, backend `5.44.0`
- `ResolveApi` succeeds
- initialization now fails at `HtpBackendInit` with
  `kLiteRtStatusErrorRuntimeFailure(3)`
- `libQnnHtpPrepare.so`, V79 stub, V79 skel, and
  `LiteRtDispatchCheckRuntimeCompatibility` are still not reached

Next recommended phase:

1. keep the QAIRT 2.44 QNN runtime alignment
2. add focused file logging around the exact HTP backend initialization call and
   its QNN status/error return
3. only after that, test ADSP/FastRPC/skel path changes
