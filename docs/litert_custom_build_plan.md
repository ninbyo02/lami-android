# LiteRT / LiteRT-LM Custom Build Plan

## QAIRT244 Max256 Guard-Only Limited Rebuild - 2026-05-26

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max256_entrypoint_build/20260526_204155/`
- preflight artifact:
  `artifacts/qairt244_npu_max256_guard_preflight/20260526_205300/`

Result: QAIRT 2.44 limited rebuild completed for the guard-only native patch.
The patch is limited to the qairt244 editable-prompt native path and raises the
custom guard from 128 to 256. It adds the marker
`qairt244_editable_prompt_max256_v1`, records
`native_max_output_tokens_limit=256`, and emits pre-RunDecode diagnostic text
that will include `SetMaxOutputTokens(256)` when a later approved run executes.

Built `liblitertlm_jni.so` metadata:

```text
build_id=c42e4438f1b39e384ab075b9392831ca
sha256=3767332f97ffee57b635fc13e2741714c994f7a2cc94d0fde5d4fbbce9c731ba
```

No rebuilt `.so` was copied into `app/src/main/jniLibs` and no large binary is
intended for Git tracking. The lami runner preflight passed against the build
artifact, but NPU generation, `Engine.initialize`, and `RunDecode` were not
executed in this phase.

## QAIRT244 Max256 Single Runtime Verification - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_single_prompt/20260526_211046/`

A temporary standardDebug APK was assembled with the staged max256
`liblitertlm_jni.so` from the build artifact for device verification only. The
APK-contained `liblitertlm_jni.so` sha256 matched:

```text
3767332f97ffee57b635fc13e2741714c994f7a2cc94d0fde5d4fbbce9c731ba
```

The single prompt `こんにちは` succeeded at `max_output_tokens=256`.
No source `app/src/main/jniLibs` file was changed and no rebuilt binary is
tracked by Git.

## QAIRT244 Native Max Output Token Limit Investigation - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_token_limit_investigation/20260526_202629/`

The current `native_max_output_tokens_limit=128` is implemented in the custom
qairt244 editable-prompt JNI entrypoint in the external LiteRT-LM checkout. The
256 request is rejected before `DecodeConfig::SetMaxOutputTokens` and before
`RunDecode`, so the finding is classified as `custom_safety_guard_only` for the
observed failure.

No custom-build action is authorized by this investigation. A later phase may
prepare a minimal native patch that raises the guard only to 256, records the
requested/actual token limit consistently, rebuilds the custom artifact, and
then validates one prompt before any broader comparison. 4096 remains a final
target that must be reached only through 256, 512, 1024, and 2048 gates with
timeout, memory, cleanup, and quality evidence.

## QAIRT244 Max Output Tokens 256 Hidden Compare - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_quality_compare/20260526_201129/`

The Java hidden compare gate was extended only for a controlled 256-token
diagnostic request, but the native editable-prompt entrypoint still enforces
`native_max_output_tokens_limit=128`. The 256 request reached native diagnostics
and was rejected as `invalid_max_output_tokens`, producing empty sanitized
output for all three prompts.

No native code, JNI libraries, release behavior, standard route selection,
DB/TTS/Markdown/streaming path, or selected-path persistence was changed. The
current custom-build recommendation remains unchanged: keep the safe hidden
baseline at `max_output_tokens=128` unless a future native/API change explicitly
raises and validates the limit.

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

2026-05-25 Phase H1 hidden-to-UI freshness/state test update:

- `sanitizer_only + max_output_tokens=128` remains the hidden experimental
  display-quality baseline.
- Phase H1 remains pre-ChatScreen and transient-only: no normal UI promotion,
  no standard route connection, no NPU execution, no `Engine.initialize`, no
  `RunDecode`, no DB, no TTS, no Markdown, no streaming, and no
  `selectedPath=npu` persistence.
- Artifact freshness is now a pure Kotlin gate: epoch milliseconds may come
  from `artifact_timestamp_ms`, `artifact_timestamp`, `synced_at`, or
  `created_at`; only timestamps within 24 hours are fresh.
- Missing timestamps are `stale_or_unknown`, future timestamps are
  `stale_or_invalid`, and older artifacts are `stale_artifact`.
- Refresh is metadata-only and may reapply the mapper only for fresh artifacts;
  it explicitly does not run NPU, initialize an engine, or run decode.

2026-05-26 Phase H1 artifact metadata boundary update:

- The future transient UI wiring has a pure Kotlin input boundary for artifact
  key-value text, maps, and already-read file content.
- The boundary keeps only minimum gate/display fields and drops `raw_output`,
  model paths, token dumps, full native diagnostics, and unknown keys before UI
  input.
- Missing required fields, invalid booleans, and invalid numbers become
  rollback input before mapper/freshness handoff.
- Duplicate keys are fixed as last-value-wins.
- `dev_enable_npu_chatscreen_route=false` means metadata is not read and not
  parsed. `true` still requires fresh artifact metadata and the full H1 gate.
- No ChatScreen connection, NPU run, engine initialize, decode, retry,
  fallback, DB, TTS, Markdown, streaming, standard route connection, or
  selected-path persistence is introduced by this boundary.

2026-05-26 Phase H1 metadata-to-presenter integration test update:

- A pure Kotlin integration test now covers key-value metadata text through the
  artifact metadata boundary, `DevOnlyNpuPhaseH1UiInput`, presenter, and
  `DevOnlyNpuPhaseH1UiState`.
- Fresh valid baseline metadata produces a visible transient state with
  sanitized output only, `reasonCode=ok`, decode-ms text, max token text, short
  QNN HTP V79 FastRPC evidence, and short artifact path.
- Gate failures for fallback, timeout, non-natural quality classification,
  standard route connection, and DB ingress become hidden rollback states.
- `raw_output` does not propagate into UI input or UI state, and side-effect
  flags remain false.
- Toggle false continues to skip provider invocation, so metadata is not read
  or parsed.
- This remains pre-ChatScreen: no NPU run, no `Engine.initialize`, no
  `RunDecode`, no DB, no TTS, no Markdown, no streaming, no standard route
  connection, and no selected-path persistence.

2026-05-26 Phase H1 card view model contract update:

- `DevOnlyNpuPhaseH1CardViewModel` now defines the read-only display object for
  a future transient card.
- The contract maps `DevOnlyNpuPhaseH1UiState` into title, subtitle, body,
  status, reason, detail lines, warning lines, and a `DEV ONLY` badge.
- Success displays sanitized output only. Rollback/failure and hidden states
  have `body=null`.
- Detail lines include `maxOutputTokens=128`, decode time, short backend
  evidence, short artifact path, `selectedPathSaved=false`, and
  DB/TTS/Markdown/streaming false.
- Raw output, retry, persistence, TTS, Markdown, and streaming controls remain
  false, with snapshot contract tests covering success and rollback text.
- This is still not a UI implementation and does not connect ChatScreen.

2026-05-26 Phase H1 preview renderer contract update:

- `DevOnlyNpuPhaseH1PreviewRenderer` now formats the read-only card view model
  into text lines with a stable order.
- Success rendering emits badge/title, status, subtitle, sanitized output,
  reason, and detail lines.
- Rollback and hidden models render no lines because their view model
  `visible=false`.
- Raw output, `<end_of_turn>`, `<start_of_turn>`, retry, persist, TTS,
  Markdown button, and streaming indicator labels are absent from rendered
  output.
- This is still formatter/test-only: no Compose UI, no ChatScreen connection,
  no NPU run, no engine initialization, no decode, no DB, no TTS, no Markdown,
  no streaming, no standard route connection, and no selected-path persistence.

2026-05-26 Phase H1 minimal Diagnostic/DEV wiring update:

- `NpuDiagnosticChatActivity` now has a read-only Phase H1 transient preview
  section.
- The section is guarded by `dev_enable_npu_chatscreen_route`, which defaults
  false and skips metadata read/parse when false.
- When true, the section reads artifact metadata only and runs the existing
  H1 parser, presenter, card view model, and renderer.
- Fresh gate-passing metadata renders sanitized output only. Stale, rollback,
  and hidden states render no preview lines.
- The wiring records `selectedPathNpuSaved=false`,
  `standard_route_connected=false`, `normal_ui_route_connected=false`,
  `db=false`, `tts=false`, `markdown=false`, `streaming=false`, `retry=false`,
  `auto_fallback=false`, `npu_generation=false`, `engine_initialize=false`,
  and `run_decode=false`.
- This is still not a normal ChatScreen promotion and does not insert assistant
  messages.
- Still no high-level `generateResponse`, no `Conversation`, and no normal UI
  NPU connection.

2026-05-23 NPU Diagnostic Chat preparation:

- Added a `customBuildExperimentDebug`-only `NpuDiagnosticChatActivity`.
- The screen is a read-only skeleton for the latest isolated NPU smoke result
  and native diagnostics.
- The prompt field is disabled and fixed to `Hi`.
- The run button is disabled; no generation is launched by the screen.
- The screen has no normal navigation entry and does not touch `ChatScreen`.
- Safety plan:
  `docs/litert_qairt244_ui_integration_safety_plan.md`
- Design:
  `docs/litert_qairt244_npu_diagnostic_chat_design.md`

2026-05-23 NPU Diagnostic Chat read-only launch verification:

- `customBuildExperimentDebug` was assembled and installed on Nubia `NX733J`.
- `NpuDiagnosticChatActivity` was launched directly by ADB.
- Artifact:
  `artifacts/qairt244_npu_diagnostic_chat_readonly/20260523_065214/`
- UIAutomator dump confirmed the diagnostic screen was foregrounded under
  `io.github.ninbyo02.lami.customnpu`.
- Run button remained disabled.
- Prompt remained fixed to `Hi`.
- `maxOutputTokens=1`, previous verifier result, timing, and native diag
  summary were visible.
- No launch extra was provided, and no generation, `Engine.initialize`, or
  `RunDecode` was executed by this verification.
- Normal `ChatScreen` and `selectedPath=npu` routes remain disconnected.

2026-05-23 short multi-token smoke execution:

- Added `customBuildExperimentDebug` app-side skeleton for
  `qairt244_short_multitoken_smoke_v1`.
- Added preflight runner:
  `scripts/run_qairt244_short_multitoken_smoke.sh`
- Built QAIRT 2.44 short multi-token native artifact:
  `artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/`
- The native artifact proves `DecodeConfig.SetMaxOutputTokens(3)` and includes
  `qairt244_short_multitoken_smoke_v1`.
- Dispatch still preserves `DT_NEEDED [libLiteRt.so]`.
- Run artifact:
  `artifacts/qairt244_short_multitoken_smoke/20260523_075743/`
- Result: `success`, output `! How Hi`, elapsed `1358 ms`,
  decode elapsed `164 ms`.
- Tombstone classification: `stale-tombstone-ignored`; no fresh crash evidence.
- The run used only the isolated lower-level native path. It did not call
  high-level `generateResponse` or connect NPU to the normal UI route.

2026-05-23 short multi-token smoke reproducibility and artifact cleanup:

- `scripts/run_qairt244_short_multitoken_smoke.sh` now writes an artifact
  tracking policy note into every short multi-token artifact.
- Large rebuilt native binaries remain local-only. Commit only text summaries,
  Build IDs, hashes, run metadata, stale tombstone notes, and external diff
  patches.
- Previously tracked `.so` files under the short multi-token build/run
  artifacts were removed from Git tracking without deleting local files.
- Reproducibility run artifact:
  `artifacts/qairt244_short_multitoken_smoke/20260523_085004/`
- Result: `success`, output `! How Hi`, elapsed `1579 ms`,
  decode elapsed `78 ms`.
- Tombstone classification: `stale-tombstone-ignored`; no fresh crash evidence.
- Overall reproducibility: `2/2` isolated lower-level three-token NPU smoke
  success.
- Still no normal UI NPU route, no `selectedPath=npu` normal path, and no
  high-level `generateResponse`.

2026-05-23 NPU runtime memory / cleanup profile:

- Added `scripts/run_qairt244_npu_memory_cleanup_profile.sh`.
- Artifact:
  `artifacts/qairt244_npu_memory_cleanup_profile/20260523_091021/`
- The script runs exactly one isolated short multi-token smoke with
  `maxOutputTokens=3` and captures app `dumpsys meminfo` before install, after
  install, before smoke launch, immediately after result, after 3 seconds, and
  after 10 seconds.
- Result: `success`, output `! How Hi`, elapsed `1423 ms`, decode elapsed
  `84 ms`, cleanup elapsed `110 ms`.
- Native cleanup evidence includes `Engine.close=unique_ptr_cleanup` and
  `QNN_HTP_V79_FastRPC_native_diag`.
- Native Heap PSS delta at 10 seconds versus the warm-process baseline was
  `-212 KB`; TOTAL PSS delta was `+2156 KB`.
- This is a baseline only. Retained PSS from a live process or mapped
  QAIRT/QNN libraries is not treated as a leak from one sample.
- Still no normal UI NPU route, no `selectedPath=npu` normal path, no
  high-level `generateResponse`, and no streaming generation.

2026-05-23 cold-start force-stop memory cleanup profile:

- Added `scripts/run_qairt244_npu_coldstart_force_stop_profile.sh`.
- Artifact:
  `artifacts/qairt244_npu_coldstart_force_stop_profile/20260523_092801/`
- The script force-stops `io.github.ninbyo02.lami.customnpu`, verifies no app
  process remains, runs exactly one isolated short multi-token smoke, samples
  memory after smoke and after 3 seconds, force-stops again, and samples at 3
  seconds and 10 seconds after final force-stop.
- Result: `success`, output `! How Hi`, elapsed `1572 ms`, decode elapsed
  `86 ms`, cleanup elapsed `103 ms`.
- Cold-start boundary: `pid_after_force_stop=none` and
  `meminfo_after_force_stop=No process found`.
- Final cleanup boundary: no pid at 3 seconds or 10 seconds after final
  force-stop; package meminfo reported no process.
- Leak classification:
  `no_app_process_retained_after_force_stop`.
- Still no normal UI NPU route, no `selectedPath=npu` normal path, no
  high-level `generateResponse`, and no streaming generation.

2026-05-23 NPU Diagnostic Chat guarded run control:

- Added a guarded `Run 3-token smoke` control to
  `NpuDiagnosticChatActivity` in the `customBuildExperimentDebug` source set.
- The button is disabled by default and requires the explicit
  `DEV confirm isolated 3-token NPU smoke` checkbox.
- The guarded path is fixed to prompt `Hi`, `maxOutputTokens=3`, and the
  isolated lower-level `Qairt244ShortMultitokenSmoke.run(...)` wrapper.
- The Activity records a diagnostic run marker, uses a running lock to prevent
  double execution, and writes an app-side 30 second timeout marker if the run
  does not complete in time.
- Read-only verification artifact:
  `artifacts/qairt244_npu_diagnostic_chat_guarded_run/20260523_094457/`
- Verification launched the Activity and confirmed the DEV checkbox was
  unchecked and the `RUN 3-TOKEN SMOKE` button was disabled. The button was not
  clicked, so no generation ran.
- Still no normal UI NPU route, no `selectedPath=npu` normal path, no
  high-level `generateResponse`, and no streaming generation.

2026-05-23 NPU Diagnostic Chat guarded UI smoke:

- Artifact:
  `artifacts/qairt244_npu_diagnostic_chat_guarded_ui_run/20260523_100701/`
- The guarded Diagnostic Chat UI path produced `result=success`.
- Output: `! How Hi`.
- Hard cap: `max_output_tokens=3`.
- Timing: total `1090 ms`, `engine_create=883 ms`, `prefill=13 ms`,
  `decode=64 ms`, `cleanup=129 ms`.
- NPU evidence remains `QNN_HTP_V79_FastRPC_native_diag`.
- Tombstone classification: `stale-tombstone-ignored`; the collector selected
  an old `No usable Dispatch runtime found` tombstone whose body does not
  contain the current guarded UI run id.
- Still no normal UI NPU route, no `selectedPath=npu` normal path, no
  high-level `generateResponse`, and no streaming generation.

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

## QAIRT 2.44 Diagnostic Chat UI Smoke Runner

Result date: 2026-05-23

Script:

```text
scripts/run_qairt244_npu_diagnostic_chat_ui_smoke.sh
```

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_smoke/20260523_102810/
```

Result:

- `customBuildExperimentDebug` assembled and installed on Nubia
  `192.168.52.52:37859`.
- `NpuDiagnosticChatActivity` launched directly by ADB.
- The DEV checkbox and guarded run button were each tapped once by the script.
- The isolated lower-level path returned:
  `result=success`, `output=! How Hi`, `max_output_tokens=3`.
- Timing:
  `engine_create=986 ms`, `prefill=27 ms`, `decode=97 ms`,
  `cleanup=155 ms`, total `1268 ms`.
- `npu_backend=NPU` with `QNN_HTP_V79_FastRPC_native_diag` evidence.
- Tombstone classification: `stale-tombstone-ignored`; no fresh crash evidence.

This runner is diagnostic-only. It does not wire NPU into the normal chat UI,
does not set `selectedPath=npu` in the normal route, does not call high-level
`generateResponse`, and does not use streaming generation.

## QAIRT 2.44 Diagnostic Chat UI Multi-Run Attempt

Result date: 2026-05-23

Script:

```text
scripts/run_qairt244_npu_diagnostic_chat_ui_multirun.sh
```

Attempt artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_110017/
```

Captured outputs:

- run1: `result=success`, `output=! How Hi`, `decode_elapsed_ms=64`
- run2: `result=success`, `output=! How Hi`, `decode_elapsed_ms=65`
- both captured result files retained `max_output_tokens=3`
- both captured classifications were `stale-tombstone-ignored`
- memory after 10 seconds: TOTAL PSS `64721 KB`, Native Heap PSS `17860 KB`

Runner correction:

- bounds extraction now splits one-line uiautomator XML before matching nodes
- completion now waits for the last guarded UI marker state
- a trailing `state=started` marker is no longer accepted as completed

Because the first multi-run attempt exposed the host runner wait bug, it is
recorded as an attempt rather than the final multi-run stability proof. No
additional rerun was performed in this turn to stay within the requested
two-run scope.

## QAIRT 2.44 Fixed Diagnostic Chat UI Multi-Run Verification

Result date: 2026-05-23

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/
```

Result:

- run1: `result=success`, `output=! How Hi`, `decode_elapsed_ms=96`,
  final guarded marker `state=success`
- run2: `result=success`, `output=! How Hi`, `decode_elapsed_ms=70`,
  final guarded marker `state=success`
- `state=started` did not remain as the final marker for either run
- both result files retained `max_output_tokens=3`
- both captured classifications were `stale-tombstone-ignored`
- memory after 10 seconds: TOTAL PSS `78536 KB`, Native Heap PSS `20571 KB`

The fixed runner confirms the Diagnostic Chat UI can execute two bounded
guarded NPU smoke runs without fresh crash evidence and without touching the
normal chat route.

## QAIRT 2.44 Diagnostic Chat Result Viewer

Result date: 2026-05-23

Scope:

- `customBuildExperimentDebug` only
- screen:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticChatActivity.kt`
- no normal `ChatScreen` routing
- no normal `selectedPath=npu` routing
- no high-level `generateResponse`
- no NPU generation from the new Refresh action

The Diagnostic Chat screen now displays the latest fixed multi-run runner
evidence:

- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/`
- run1: `result=success`, `output=! How Hi`, `elapsed_ms=1907`,
  `decode_elapsed_ms=96`
- run2: `result=success`, `output=! How Hi`, `elapsed_ms=1661`,
  `decode_elapsed_ms=70`
- final guarded marker state: `success` for both runs
- final `state=started`: `false` for both runs
- after 10 seconds: TOTAL PSS `78536 KB`, Native Heap PSS `20571 KB`
- tombstone classification: `stale-tombstone-ignored`
- fresh crash: `false`

The new `Refresh result view` button rereads the app-private smoke result,
native diagnostic file, and optional
`qairt244_diagnostic_runner_summary.txt` key-value file. If the optional file
is absent, the screen shows the committed latest verification values above. The
Refresh action is read-only and does not execute the isolated smoke.

Sync script:

```text
scripts/sync_qairt244_npu_diagnostic_summary_to_app.sh
```

The script auto-detects the newest artifact under
`artifacts/qairt244_npu_diagnostic_chat_ui_multirun/` or
`artifacts/qairt244_npu_diagnostic_chat_ui_smoke/`, or accepts `--artifact`.
It writes the normalized summary to:

```text
/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_diagnostic_runner_summary.txt
```

The script does not launch the Activity, initialize LiteRT, run RunDecode, or
generate tokens.

Verified sync:

```text
artifacts/qairt244_npu_diagnostic_summary_sync/20260523_121424/
```

The app-private file was written successfully and read back with matching
key-value content. No Activity launch or NPU execution was performed during the
sync.

Read-only UI verification:

```text
artifacts/qairt244_npu_diagnostic_summary_readonly_verify/20260523_122946/
```

The Activity displayed the synced summary with `source=app_private_file`,
including run count, run1/run2 output and timing, final guard state,
after-10s memory summary, tombstone classification, and disabled route status.
The verification did not press the DEV checkbox or guarded run button, and did
not run Engine.initialize or RunDecode.

Freshness indicator update:

```text
artifacts/qairt244_npu_diagnostic_summary_freshness/20260523_124234/
```

The app-private summary now includes sync timestamp and source artifact age:

- `synced_at_local=2026-05-23 12:42:34 +0900`
- `source_artifact=artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243`
- `source_artifact_timestamp=20260523_114243`
- `source_artifact_age_human=59m 51s`
- `freshness_status=fresh`
- `freshness_warning=none`

The stale threshold is 24 hours. If the source artifact timestamp cannot be
parsed, the status is `unknown`; if it is older than 24 hours, the status is
`stale` and the warning states that the source artifact is older than the
freshness threshold.

## QAIRT 2.44 Diagnostic Short Prompt Guard Spec

Result date: 2026-05-23

Spec:

```text
docs/litert_qairt244_diagnostic_short_prompt_guard_spec.md
```

This is STEP 2 preparation only. No editable prompt field was enabled and no
NPU generation was run.

Validator:

```text
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticPromptValidator.kt
```

Test:

```text
app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticPromptValidatorTest.kt
```

Key requirements:

- `customBuildExperimentDebug` Diagnostic Chat only
- initial prompt: `Hi`
- maximum prompt length: 32 characters
- allowed characters: ASCII letters, digits, space, `. , ? ! ' - _`
- rejected: empty prompt, newline, control characters, emoji, non-ASCII symbols
- native path must still enforce `maxOutputTokens=3`
- timeout: 30 seconds
- DEV checkbox, explicit confirmation, and running lock required
- artifact collection and stale/fresh tombstone classification required
- no normal `ChatScreen` route, no normal `selectedPath=npu`, no high-level
  `generateResponse`, no streaming

The validator is intentionally not connected to the editable UI field or the
guarded Run button in this phase.

Prompt preview update:

- `NpuDiagnosticChatActivity` displays `Short prompt input preview`
- fixed preview value: `Hi`
- validation result: `isValid=true`, `reasonCode=ok`,
  `normalizedPrompt=Hi`
- input state: `enabled=false`
- Run button connection on default Activity launch: `false`
- guarded run requires explicit `allowGuardedNpuRun=true` intent extra
- NPU generation: not run

Editable prompt preview update:

- artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_preview/20260523_133833/`
- Activity extra: `allowEditablePromptPreview=true`
- default launch input state: `enabled=false`
- editable preview launch input state: `enabled=true`
- validator updates on text changes
- invalid text is shown as a validator reason code in the preview
  (`contains_disallowed_char` verified with `Hello/LamiHi`)
- prompt execution connection: `false`
- guarded Run button still uses fixed `Hi`
- `allowEditablePromptPreview` is independent from `allowGuardedNpuRun`
- no NPU generation, Engine.initialize, or RunDecode is run by editing,
  refreshing, or launching this preview mode

Editable prompt guarded execution plan:

- plan:
  `docs/litert_qairt244_diagnostic_editable_prompt_connection_plan.md`
- status: design review only
- new future Activity extra: `allowEditablePromptExecution=true`
- required extras for execution:
  `allowEditablePromptPreview=true`, `allowGuardedNpuRun=true`,
  `allowEditablePromptExecution=true`
- required UI state: DEV checkbox checked, validator valid, prompt length
  `<=32`, native editable prompt support present, `running=false`
- Run button disabled for invalid prompt or missing extra
- current native entrypoint is fixed to `Hi`; Android gate reports
  `native_editable_prompt_supported=false` and preflight-blocks execution
- NG input displays `reasonCode` and starts no native execution
- stale summary freshness remains a visible warning, not a hard block
- result contract must include `actual_prompt`, `normalized_prompt`,
  `prompt_source=editable_prompt`, `max_output_tokens=3`, timing, native diag,
  and stale/fresh tombstone classification
- no editable prompt connection or NPU generation was performed in this pass
- preflight runner:
  `scripts/run_qairt244_npu_diagnostic_editable_prompt_guarded_run.sh`
- preflight artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_175939/`
- observed result: `preflight_result=blocked_native_fixed_hi`,
  `run_executed=false`, `engine_initialize=false`, `run_decode=false`,
  `npu_generation=false`

Editable prompt native entrypoint recovery:

- build artifact:
  `artifacts/qairt244_editable_prompt_entrypoint_build/20260523_183705/`
- external LiteRT-LM diff:
  `artifacts/qairt244_editable_prompt_entrypoint_build/20260523_183705/metadata/litertlm_external_diff.patch`
- marker: `qairt244_editable_prompt_smoke_v1`
- native token cap evidence: `DecodeConfig.SetMaxOutputTokens(3)`
- lami wrapper:
  `Qairt244ShortMultitokenSmoke.nativeRunEditablePrompt(...)`
- runner:
  `scripts/run_qairt244_npu_diagnostic_editable_prompt_guarded_run.sh`
- preflight artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_184614/`
- guarded run artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_184901/`
- guarded run result: `actual_prompt=Hello`, `normalized_prompt=Hello`,
  `prompt_source=editable_prompt`, `max_output_tokens=3`,
  `result=success`, `output=! How अच्छे`, `fresh_crash=false`
- caveat: first UI runner execution produced two success markers in one
  Activity session; the Activity now clears DEV confirmation and leaves Run
  disabled after completion. Reverify one-shot behavior before broadening.
- one-shot reverify artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_one_shot_verify/20260523_191757/`
- one-shot result: exactly one guarded `state=success` marker,
  `duplicate_success_marker=false`, `state_started_residual=false`,
  DEV checkbox off, Run button disabled, `fresh_crash=false`

Fallback/recovery verification:

- artifact:
  `artifacts/qairt244_npu_diagnostic_fallback_recovery/20260523_193405/`
- invalid prompt: `Hello/Lami`, `reasonCode=contains_disallowed_char`,
  Run disabled
- unsupported preflight:
  `blocked_marker_missing_or_artifact_missing`, no NPU work
- timeout simulation: DEV-only path, `Engine.initialize=false`,
  `RunDecode=false`, final `state=timeout`
- after timeout/refresh: DEV checkbox off, Run disabled
- normal ChatScreen route and normal `selectedPath=npu` route remained
  disconnected

Normal ChatScreen NPU integration design:

- plan:
  `docs/litert_qairt244_chat_screen_npu_integration_plan.md`
- status: design only
- implementation: not started
- normal `ChatScreen`: still disconnected
- normal `selectedPath=npu`: still disabled
- required initial phase: `customBuildExperimentDebug` DEV hidden toggle only
- first candidate route: DEV-only adapter using the same lower-level isolated
  QAIRT 2.44 path, prompt length `<=32`, and `maxOutputTokens=3`
- first normal UI behavior: one non-streaming short run, no DB persistence, no
  TTS, no Markdown pipeline, no stop button, and no automatic GPU retry
- rollback conditions include fresh crash, duplicate marker, timeout, stale
  summary misuse, memory warning, normal setting persistence, UI freeze, and
  unclear cleanup
- this planning pass did not run NPU generation, Engine.initialize, RunDecode,
  high-level `generateResponse`, or normal UI NPU routing

DEV-only NPU route adapter boundary:

- plan:
  `docs/litert_qairt244_dev_only_npu_route_adapter_plan.md`
- status: design only
- proposed API:
  `DevOnlyNpuRouteAdapter.runOnce(prompt, maxOutputTokens=3, timeoutMs=30000)`
- proposed result:
  `success`, `output`, `reasonCode`, elapsed fields, prompt, token cap,
  backend evidence, artifact path, fresh crash flag, and timeout flag
- first candidate call site: normal `ChatScreen` `InferenceTarget.LOCAL` branch
  before user message persistence
- explicitly detached from DB, TTS, Markdown, streaming, stop button,
  high-level `generateResponse`, and normal `selectedPath=npu`
- no code was connected and no NPU generation was run

DEV-only NPU route adapter stub:

- implementation source:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/`
- test source:
  `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteAdapterTest.kt`
- current implementation: blocked/no-op
- result: `success=false`, `reasonCode=adapter_not_connected`,
  `artifactPath=null`, `backendEvidence=null`, `freshCrash=false`,
  `timeout=false`
- ChatScreen call site: none
- NPU generation, Engine.initialize, RunDecode, high-level
  `generateResponse`, normal `selectedPath=npu`, DB, TTS, Markdown,
  streaming, and stop button paths remain untouched

DEV-only NPU route gate:

- implementation:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteGate.kt`
- tests:
  `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteGateTest.kt`
- gate reasons are typed and cover flavor, hidden extras, DEV checkbox,
  validator, native marker support, running lock, and `maxOutputTokens=3`
- ChatScreen call site: none
- NPU generation, Engine.initialize, RunDecode, high-level `generateResponse`,
  normal `selectedPath=npu`, DB, TTS, Markdown, streaming, and stop button
  paths remain untouched

DEV-only NPU route planner:

- implementation:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRoutePlanner.kt`
- tests:
  `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRoutePlannerTest.kt`
- gate NG: returns `gate_blocked:<REASON>` and does not call adapter
- gate OK: calls adapter
- current default adapter is blocked, so gate OK returns
  `adapter_not_connected`
- ChatScreen call site: none
- NPU generation, Engine.initialize, RunDecode, high-level `generateResponse`,
  normal `selectedPath=npu`, DB, TTS, Markdown, streaming, and stop button
  paths remain untouched

DEV-only planner result UI boundary:

- implementation:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticChatActivity.kt`
- section: `Planner Preview (blocked)`
- planner call:
  `DevOnlyNpuRoutePlanner.runIfAllowed(gateInput, prompt="Hello",
  maxOutputTokens=3, timeoutMs=30000)`
- adapter:
  `BlockedDevOnlyNpuRouteAdapter`
- displayed result:
  `success=false`, `reasonCode=adapter_not_connected`,
  `prompt=Hello`, `maxOutputTokens=3`, `timeout=false`,
  `freshCrash=false`
- route status:
  `ChatScreen route connected=false`, `selectedPathNpuApplied=false`,
  `npuGeneration=false`, `engineInitialize=false`, `runDecode=false`
- normal ChatScreen call site: none
- NPU generation, Engine.initialize, RunDecode, high-level `generateResponse`,
  normal `selectedPath=npu`, DB, TTS, Markdown, streaming, and stop button
  paths remain untouched

DEV-only route transient result/error model:

- implementation:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteDisplayModel.kt`
- tests:
  `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteDisplayModelTest.kt`
- input:
  `DevOnlyNpuRouteResult`
- output:
  `DevOnlyNpuRouteDisplayModel`
- statuses:
  `SUCCESS`, `BLOCKED`, `TIMEOUT`, `CRASH`, `ERROR`
- blocked classification:
  `gate_blocked:<REASON>` and `adapter_not_connected`
- normal ChatScreen call site: none
- NPU generation, Engine.initialize, RunDecode, high-level `generateResponse`,
  normal `selectedPath=npu`, DB, TTS, Markdown, streaming, and stop button
  paths remain untouched

DEV-only display model UI application:

- implementation:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticChatActivity.kt`
- section:
  `Planner Preview (blocked)`
- mapper:
  `DevOnlyNpuRouteDisplayModelMapper.from(result)`
- current displayed status:
  `BLOCKED`
- current displayed reason:
  `adapter_not_connected`
- current displayed message:
  `NPU route adapter is not connected`
- normal ChatScreen call site: none
- NPU generation, Engine.initialize, RunDecode, high-level `generateResponse`,
  normal `selectedPath=npu`, DB, TTS, Markdown, streaming, and stop button
  paths remain untouched

DEV-only transient presenter:

- implementation:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuTransientPresenter.kt`
- tests:
  `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuTransientPresenterTest.kt`
- input:
  `DevOnlyNpuRouteDisplayModel`
- output:
  `DevOnlyNpuTransientUiState`
- side-effect flags:
  `shouldPersistToDb=false`, `shouldSpeakTts=false`,
  `shouldRenderMarkdown=false`, `shouldStream=false`
- applies to:
  `SUCCESS`, `BLOCKED`, `TIMEOUT`, `CRASH`, and `ERROR`
- normal ChatScreen call site: none
- NPU generation, Engine.initialize, RunDecode, high-level `generateResponse`,
  normal `selectedPath=npu`, DB, TTS, Markdown, streaming, and stop button
  paths remain untouched

DEV-only transient presenter UI application:

- implementation:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticChatActivity.kt`
- section:
  `Planner Preview (blocked)`
- flow:
  `DevOnlyNpuRouteResult -> DevOnlyNpuRouteDisplayModel ->
  DevOnlyNpuTransientUiState`
- current transient status:
  `BLOCKED`
- current reason:
  `adapter_not_connected`
- side-effect flags shown:
  `shouldPersistToDb=false`, `shouldSpeakTts=false`,
  `shouldRenderMarkdown=false`, `shouldStream=false`
- normal ChatScreen call site: none
- NPU generation, Engine.initialize, RunDecode, high-level `generateResponse`,
  normal `selectedPath=npu`, DB, TTS, Markdown, streaming, and stop button
  paths remain untouched

ChatScreen DEV-only NPU blocked branch:

- implementation:
  disabled guarded branch in
  `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt`
- custom target:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuChatScreenBlockedBranch.kt`
- tests:
  `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuChatScreenBlockedBranchTest.kt`
- location:
  line 2349, inside `InferenceTarget.LOCAL`
- placement:
  after `requestPrompt` capture and blank validation
- before:
  input clearing, chat/user message DB insert, TTS cleanup, Markdown,
  streaming, stop-button ownership, and persistent `selectedPath=npu`
- guard:
  `BuildConfig.CUSTOM_BUILD_EXPERIMENT &&
  DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED`
- current toggle:
  `DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED=false`
- false toggle behavior:
  existing LOCAL path continues unchanged
- true path:
  reflection-only call into `DevOnlyNpuChatScreenBlockedBranch`
- true path result:
  `adapter_not_connected`, transient Snackbar summary only
- imports:
  no NPU package import in main `ChatScreen`
- NPU generation, Engine.initialize, RunDecode, high-level `generateResponse`,
  normal `selectedPath=npu`, DB, TTS, Markdown, streaming, and stop button
  paths remain untouched

ChatScreen disabled-state verification:

- artifact:
  `artifacts/qairt244_chat_screen_blocked_branch_disabled_verify/20260523_215825/`
- build/install:
  `customBuildExperimentDebug`
- launched Activity:
  `io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.MainActivity`
- observed screen:
  normal ChatScreen prompt composer and Ready state
- toggle:
  `DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED=false`
- blocked branch:
  not fired
- `adapter_not_connected`:
  not shown in normal launch artifacts
- selected path:
  no `selectedPath=npu` evidence from this path
- NPU execution:
  not run; no `Engine.initialize` or `RunDecode`

ChatScreen normal send path non-invasive verification:

- artifact:
  `artifacts/qairt244_chat_screen_normal_send_noninvasive_verify/20260523_221224/`
- build/install:
  `customBuildExperimentDebug`
- launched Activity:
  `io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.MainActivity`
- prompt sent:
  no
- toggle:
  `DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED=false`
- static path:
  false toggle falls through to existing LOCAL code path
- runtime markers:
  none for `adapter_not_connected`, blocked branch, `selectedPath=npu`,
  `Engine.initialize`, `RunDecode`, QNN/HTP/FastRPC, or `generateResponse`
- selected path:
  no `selectedPath=npu` evidence from this path
- NPU execution:
  not run

DEV hidden NPU ChatScreen toggle boundary:

- artifact:
  `artifacts/qairt244_dev_hidden_npu_chatscreen_toggle_boundary/20260523_222339/`
- key:
  `dev_enable_npu_chatscreen_route`
- default:
  `false`
- Settings UI:
  `DEV: Enable NPU ChatScreen route`
- visibility:
  `customBuildExperimentDebug` DEBUG settings only
- observed UI state:
  switch `checked=false`
- ChatScreen connection:
  reads `SettingsPreferences.devEnableNpuChatScreenRouteFlow`, still guarded
  by `BuildConfig.CUSTOM_BUILD_EXPERIMENT`
- switch action in verification:
  not toggled
- route result:
  blocked branch not fired; no `adapter_not_connected` logcat marker
- selected path:
  no `selectedPath=npu` evidence
- NPU execution:
  not run
## QAIRT DEV ChatScreen Toggle Boundary Verification (2026-05-23)

- Artifact: `artifacts/qairt244_chat_screen_toggle_on_blocked_branch_verify/20260523_223850/`
- customBuildExperimentDebug now has a test-only Activity to set/read `dev_enable_npu_chatscreen_route` for verification.
- The toggle ON run confirmed the current ChatScreen branch remains blocked and transient with `adapter_not_connected`.
- The verification did not run NPU generation, `Engine.initialize`, `RunDecode`, or `Backend.NPU`.
- The toggle was restored OFF after the run.
- Next planned step: keep the blocked branch as the ChatScreen boundary while designing a later real-adapter swap behind the same gate.

## QAIRT ChatScreen Real Adapter Preflight Review (2026-05-24)

Artifact:

```text
artifacts/qairt244_chat_screen_real_adapter_preflight_rollback_review/20260524_082657/
```

This is the final preflight review before a possible customBuildExperimentDebug
real-adapter swap. No real adapter was connected.

The first real adapter run is constrained to:

- Nubia / SM8750
- prompt `Hello`
- `maxOutputTokens=3`
- one run
- timeout 30 seconds
- DB/TTS/Markdown/streaming disabled
- `selectedPath=npu` not saved
- artifact required
- toggle OFF after completion or failure

Rollback conditions include fresh crash, timeout, duplicate marker, missing
cleanup evidence, selected-path persistence, normal UI side effects, stale
artifact usage, elevated memory after 10 seconds, UI lock recovery failure, and
missing QNN/HTP/FastRPC evidence.

## QAIRT ChatScreen Real Adapter First Attempt (2026-05-24)

Artifact:

```text
artifacts/qairt244_chat_screen_real_npu_first_run/20260524_084514/
```

The customBuildExperimentDebug route was changed from the blocked adapter to
`Qairt244DevOnlyNpuRouteAdapter` and executed once from ChatScreen. It failed
safely with `model-file-not-found` before engine initialization.

Observed:

- toggle reset before run: false
- toggle ON before send: true
- toggle OFF after run: false
- prompt: `Hello`
- `maxOutputTokens=3`
- DB/TTS/Markdown/streaming: not connected
- `selectedPath=npu`: not saved
- fresh crash: false
- timeout: false

Next build step: replace the fixed model path assumption with explicit
app-private model discovery or a runner-supplied verified model path, then run
one more guarded attempt.

## QAIRT ChatScreen Model Path Resolution Attempt (2026-05-24)

Artifact:

```text
artifacts/qairt244_chat_screen_real_npu_model_path_resolution/20260524_091657/
```

The customBuildExperimentDebug ChatScreen adapter now discovers the model from
app-private `files/local_models/*.litertlm` instead of using a fixed filename.
The runner captures `run-as io.github.ninbyo02.lami.customnpu ls -l
files/local_models`, pulls the resolver artifact, and leaves normal settings
unchanged.

Observed:

- model listing contained one `.litertlm`: `1779578208133_gemma-4-E2B-it.litertlm`
- resolved path: `/data/user/0/io.github.ninbyo02.lami.customnpu/files/local_models/1779578208133_gemma-4-E2B-it.litertlm`
- checked length: `2583085056`
- prompt: `Hello`
- `maxOutputTokens=3`
- result: `failure`
- rollback reason: `TF_LITE_AUX not found in the model` during engine create
- NPU evidence: `QNN_HTP_V79_FastRPC_native_diag`
- normal ChatScreen DB/TTS/Markdown/streaming and `selectedPath=npu` remained disconnected

Next build step: replace or generate the app-private model with a QAIRT NPU
compiled LiteRT-LM artifact that contains the required `TF_LITE_AUX` data.

## DEV-only NPU Output Sanitizer Result (2026-05-25)

With the SM8750 compiled LiteRT-LM model in place, the ChatScreen DEV-only NPU
route reached native decode and returned Gemma turn-template artifacts in the
raw text. The custom build/native stack is unchanged; the app now sanitizes the
debug-route display output only.

- artifact: `artifacts/qairt244_npu_output_sanitizer/20260525_015040/`
- template mode: `gemma_it_like`
- prompt: `こんにちは`
- `maxOutputTokens=128`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- raw output preserved in `raw_output.txt`
- sanitized output preserved in `sanitized_output.txt`
- sanitized output: `こんにちは！何かお手伝いできることはありますか？`
- `removed_template_token_count=2`
- `removed_prompt_echo=true`

No native artifact was rebuilt for this change. No `.so`, `.apk`, `.aar`,
`.zip`, `.tar`, `.gz`, or `.litertlm` artifact is part of the Git change.

## QAIRT244 Turn-Stop Quality Compare - 2026-05-25

The ChatScreen DEV-only NPU route is treated as route-successful; this phase is display-quality tuning only. The comparison is documented in `docs/litert_qairt244_npu_turn_stop_quality_compare.md` and implemented by `scripts/run_qairt244_npu_turn_stop_quality_compare.sh`.

Static LiteRT-LM inspection found `native stop not exposed` for the qairt244 lower-level Android route. Runtime metadata can carry stop token ids internally, but this JNI path creates a default session config and exposes only `DecodeConfig.SetMaxOutputTokens()` for the editable-prompt run; no per-request stop sequence, stop token, EOS, or `<end_of_turn>` setter is available. Public sampler controls expose topK/topP/temperature/seed, but the qairt244 lower-level native entrypoint does not accept sampler config, and no repetition penalty API was found.

The fixed executable baseline is `enhanced_sanitizer_only_128`. `lower_max_tokens_64_sanitizer` and `lower_max_tokens_32_sanitizer` are rollback-only records, not executable adoption candidates, and `stop_sequence_end_of_turn` is recorded as `not_run/native_stop_not_exposed`. The prompts are `こんにちは`, `はじめまして`, and `こんばんは`; the executable sanitizer-only case uses `max_output_tokens=128` and a 30 second timeout.

The safe adopted baseline from the 2026-05-25 run is enhanced sanitizer-only at `max_output_tokens=128`. Lower caps are not adopted because `64` produced `empty_after_sanitize`, and `32` produced adapter failure / timeout in the comparison artifact. The required evidence remains `QNN_HTP_V79_FastRPC_native_diag`, `fallback_used=false`, sanitizer-only `timeout=false`, `fresh_crash=false`, `selected_path_npu_saved=false`, and no normal UI, DB, TTS, Markdown, or streaming connection.

## NPU Sanitizer Quality Baseline Commit - 2026-05-25

Commit baseline: `sanitizer_only + max_output_tokens=128` is the provisional
hidden experimental display-quality baseline, backed by
`artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`.

Promotion gate: `fallback_used=false`, `fresh_crash=false`, `timeout=false`,
sanitized `quality_classification=natural_japanese`, no template artifact after
sanitize, no repetition or multilingual drift after sanitize, and
`db=false`, `tts=false`, `markdown=false`, `streaming=false`.

Raw native `template_artifact` remains acceptable only as diagnostic evidence;
the displayed sanitized output must be natural Japanese. Native stop sequence /
native turn-stop is not required for this provisional baseline. Standard route
non-connection is covered by `DevOnlyNpuChatScreenBlockedBranchTest`.

The follow-up static investigation is recorded at
`artifacts/qairt244_npu_stop_api_investigation/20260525_214513/`. It found no
public Android/JNI per-run stop sequence, stop token, EOS, or `<end_of_turn>`
API for this qairt244 path, so no native stop comparison is implemented.

## NPU Hidden-To-UI Handoff Plan - 2026-05-25

The next pre-promotion design is documented in
`docs/litert_qairt244_npu_hidden_to_ui_handoff_plan.md`. It keeps
`sanitizer_only + max_output_tokens=128` as the required baseline and does not
implement normal UI promotion.

The first eligible handoff phase is H1 transient preview only: display
`sanitized_output` in a DEV-only transient UI surface, keep `raw_output` in
artifacts only, and keep DB, TTS, Markdown, streaming, selected-path NPU
persistence, and standard route connection disabled. Later phases evaluate
assistant-style temporary display, DB persistence, and TTS/Markdown/streaming
as separate gates.

Phase H1 surface details are fixed in
`docs/litert_qairt244_npu_phase_h1_transient_ui_surface.md`: ChatScreen may use
only a DEV-only transient card/banner/snackbar, outside the assistant message
list, with `sanitized_output`, status, `reasonCode`, `decode_ms`, short backend
evidence, `maxOutputTokens=128`, and short artifact path. It clears on new
input, navigation away, toggle OFF, failure/rollback, app restart, or stale
artifact; refresh may reread artifact metadata only.

Before ChatScreen wiring, Phase H1 is limited to state/display-model/presenter
tests. Those tests must prove sanitized-output-only display, raw-output
exclusion, reason-only failure display, rollback hiding, and
`shouldPersistToDb=false`, `shouldSpeakTts=false`,
`shouldRenderMarkdown=false`, `shouldStream=false`.

The artifact metadata mapper is also tested before ChatScreen wiring. It reads
hidden result key-value metadata, maps only `sanitized_output` into H1 UI input,
discards `raw_output`, and turns promotion-gate mismatches into
rollback/failure input.

## Phase H1 Transient Preview UI Capture - 2026-05-26

Supplemental artifact:
`artifacts/qairt244_phase_h1_transient_preview_ui_capture/20260526_064732`.

This artifact complements the prior Diagnostic-only wiring artifact
`artifacts/qairt244_phase_h1_transient_preview_wiring/20260526_062814` with a
representative connected-device screenshot and window dump. The capture shows
the Phase H1 section in `NpuDiagnosticChatActivity` with:

- `DEV ONLY - DEV NPU transient preview`
- `Status: SUCCESS`
- sanitized output only:
  `こんにちは！何かお手伝いできることはありますか？`
- `metadata_read=true`
- `preview_visible=true`
- raw output and template tokens absent from UI
- `selectedPathNpuSaved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`
- `npu_generation=false`, `engine_initialize=false`, `run_decode=false`

No code implementation, NPU execution, native change, model change, or normal
ChatScreen promotion was performed for this capture supplement.

## Phase H1 Read-Only Transient Card - 2026-05-26

`NpuDiagnosticChatActivity` now renders the H1 transient preview through a
dedicated read-only card in customBuildExperimentDebug.

The card is visible only for fresh, gate-passing metadata and remains hidden for
rollback, stale, hidden, or gate-failed results. It displays only the renderer
output:

- `DEV ONLY`
- `DEV NPU transient preview`
- `Status: SUCCESS`
- sanitized natural Japanese output
- `maxOutputTokens=128`
- `decode_ms`
- short backend evidence
- short artifact path
- side-effect flags false

No normal ChatScreen route, assistant message list, DB, TTS, Markdown,
streaming, selected-path NPU persistence, retry, fallback, `Engine.initialize`,
`RunDecode`, or additional NPU execution is introduced.

## Phase H1 Read-Only Card Hidden-State Regression - 2026-05-26

Connected-device artifact:
`artifacts/qairt244_phase_h1_readonly_card_hidden_state_regression/20260526_074740/`

The read-only card now has capture evidence for both visible and hidden states:

- success metadata: visible card, sanitized output only
- stale metadata: hidden card, `reasonCode=stale_artifact`
- rollback metadata: hidden card, `reasonCode=fallback_used`
- toggle false: hidden card, `metadata_read=false`

The regression did not run NPU generation and did not call `Engine.initialize`
or `RunDecode`. DB, TTS, Markdown, streaming, standard route, normal UI route,
and selected-path NPU persistence all remain disconnected.

## Phase H1 Compose Adapter Contract - 2026-05-26

Added a contract-only adapter for future Diagnostic-only Compose display:

```text
DevOnlyNpuPhaseH1CardViewModel -> DevOnlyNpuPhaseH1ComposeModel
```

The adapter maps visible success cards to `shouldShowSurface=true` with sanitized
body text only. Hidden and rollback cards map to `shouldShowSurface=false` and
`body=null`.

The contract fixes all side-effect and route flags to false:

- assistant list insertion
- DB persistence
- TTS
- Markdown
- streaming
- retry
- fallback

No ChatScreen connection, Compose UI implementation, NPU execution, native
change, release/standard change, or selected-path persistence is included.

## Phase H1 Diagnostic Preview Host Contract - 2026-05-26

Added a contract-only host state for the Diagnostic preview:

```text
DevOnlyNpuPhaseH1ComposeModel -> DevOnlyNpuPhaseH1PreviewHostState
```

The host renders text only for visible success models and hides stale, rollback,
hidden, and toggle-false models. It does not read metadata, run NPU, initialize
the engine, decode, insert assistant messages, persist DB records, speak TTS,
render Markdown, stream, retry, or fallback.

This remains a debug/customBuildExperiment contract test layer only. It does not
connect ChatScreen or implement the formal Compose UI surface.

## Phase H1 XML Card / Preview Host Consistency - 2026-05-26

Added `DevOnlyNpuPhaseH1XmlCardContract` as the pure helper shared by the
existing Diagnostic XML/read-only card and the preview host render path.

The contract tests fix:

- success XML card text equals preview host render text
- hidden, stale, rollback, and toggle-false states render empty text
- raw output and turn template tokens are not displayed
- assistant insertion, DB, TTS, Markdown, streaming, retry, fallback, metadata
  read, NPU run, engine initialize, and decode stay false

This is a test/contract alignment only. It does not run NPU and does not connect
ChatScreen, standard route, release, or the formal Compose UI.

## Phase H1 Preview Consistency Contract - 2026-05-26

Added `DevOnlyNpuPhaseH1PreviewConsistency` and snapshot tests to compare all
Diagnostic-only read-only preview outputs:

- XML card helper output
- PreviewRenderer output
- PreviewHost output
- Compose adapter render output
- Compose/host safety contract flags

The consistency contract keeps success render text aligned and keeps
hidden/rollback/stale/toggle-false states empty. It also confirms raw output,
turn tokens, assistant insertion, DB, TTS, Markdown, streaming, retry/fallback,
metadata read, NPU run, engine initialize, and decode are not introduced.
