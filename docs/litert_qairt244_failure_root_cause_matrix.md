# QAIRT 2.44 NPU Dispatch Failure Root Cause Matrix

Date: 2026-05-21

Scope: coordinator synthesis of the tombstone/runtime mapping, Android QNN path
review, LiteRT source trace, CLI proof planning, model schema probe, the
2026-05-21 Android-native logcat dry-run, the JNI sentinel dry-run, the
2026-05-22 app-owned JNI logcat smoke, the 2026-05-22 native file logger
dry-run, the dlopen trace build, the dispatch symbol-resolution experiments,
the QNN provider trace dry-run, the QNN runtime alignment dry-run, the HTP
backend trace dry-run, the QNN backend log callback dry-run, and the
libcdsprpc manifest visibility dry-run, the 2026-05-23 initialize stability
probe, the single-token smoke implementation-prep pass, two lower-level
single-token smoke executions, token timing verifier implementation preflight,
the 2026-05-23 connected-device token timing verifier run, two isolated short
multi-token smoke executions, the first NPU runtime memory cleanup profile, and
the cold-start force-stop cleanup profile, and the guarded editable prompt
Diagnostic Chat native smoke recovery.

## Current Boundary

- flavor: `customBuildExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.customnpu`
- runId: `1779496082843`
- diagnostic artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_184901/`
- final stage: `done`
- returned: yes
- signal: no fresh crash evidence; diagnostics collector selected a stale older
  tombstone that does not contain the current smoke run id
- immediate native boundary:
  `uses-native-library libcdsprpc.so -> QnnDevice_create success -> LiteRtDispatchCheckRuntimeCompatibility success -> Engine.initialize success -> lower-level RunDecode maxOutputTokens=1 success -> token/timing verifier success -> lower-level RunDecode maxOutputTokens=3 success 2/2 -> memory cleanup baseline collected -> cold-start force-stop cleanup passes -> Diagnostic Chat editable prompt guarded RunDecode maxOutputTokens=3 success`

The explicit smoke created only the lower-level native LiteRT-LM session needed
for decode. It did not create `Conversation`, did not create a Kotlin/public
`Session` object, did not call high-level `generateResponse`, and did not wire
normal UI inference to NPU.

## Editable Prompt Guarded Smoke Update

Artifacts:

```text
artifacts/qairt244_editable_prompt_entrypoint_build/20260523_183705/
artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_184614/
artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_184901/
```

Result:

- native marker: `qairt244_editable_prompt_smoke_v1`
- native cap: `DecodeConfig.SetMaxOutputTokens(3)`
- `native_editable_prompt_supported=true`
- `prompt_execution_connected=true` in preflight
- guarded prompt: `Hello`
- normalized prompt: `Hello`
- `prompt_source=editable_prompt`
- `result=success`
- output: `! How अच्छे`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- fresh crash evidence: none

Runner caveat: the first guarded UI execution recorded two successful guarded
run markers in one Activity session. The Activity was hardened to clear DEV
confirmation and keep the Run button disabled after completion. Treat this as a
UI runner one-shot lock issue to reverify before proceeding to fallback/recovery
STEP 3.

One-shot hardening reverify:

```text
artifacts/qairt244_npu_diagnostic_editable_prompt_one_shot_verify/20260523_191757/
```

Result:

- `result=success`
- `actual_prompt=Hello`
- `normalized_prompt=Hello`
- `max_output_tokens=3`
- final guard marker: `state=success`
- guarded success marker count: `1`
- residual `state=started`: `false`
- duplicate success marker: `false`
- DEV checkbox off: `true`
- Run button disabled: `true`
- fresh crash: `false`

Boundary update: Diagnostic Chat editable prompt execution is proven for one
bounded prompt with one-shot hardening. Next boundary is Diagnostic Chat-only
fallback / timeout / recovery; normal ChatScreen NPU integration remains a
separate design gate.

## Diagnostic Fallback / Recovery Update

Artifact:

```text
artifacts/qairt244_npu_diagnostic_fallback_recovery/20260523_193405/
```

Result:

- invalid prompt `Hello/Lami`: validator rejected with
  `contains_disallowed_char`
- invalid prompt Run button: disabled
- unsupported native marker/artifact preflight: blocked before NPU work
- timeout simulation: native Engine and RunDecode not called
- timeout UI recovery: DEV checkbox off, Run disabled
- refresh after timeout: Run remained disabled
- fresh crash evidence: none
- normal ChatScreen route: disconnected
- normal `selectedPath=npu` route: disabled

Boundary update: Diagnostic Chat fallback/recovery behavior is verified for
invalid input, unsupported native preflight, and timeout simulation. The next
step is normal ChatScreen integration design only, not implementation.

## Normal ChatScreen NPU Integration Design

Documented in:

```text
docs/litert_qairt244_chat_screen_npu_integration_plan.md
```

Current interpretation:

- QAIRT 2.44 NPU root causes are resolved for bounded Diagnostic Chat smoke.
- Diagnostic Chat failure, timeout, and recovery behavior is verified.
- Normal ChatScreen remains unconnected.
- Normal `selectedPath=npu` remains unused and disabled.

Future normal ChatScreen NPU work is gated by:

- `customBuildExperimentDebug` only
- DEV hidden toggle
- Nubia / SM8750 check
- QAIRT 2.44 aligned runtime stack
- `libcdsprpc.so` manifest visibility
- native marker `qairt244_editable_prompt_smoke_v1`
- fixed `maxOutputTokens=3` evidence
- fresh Diagnostic Chat success summary
- fallback/recovery artifact pass
- one-shot hardening pass
- memory cleanup pass

Rollback triggers include fresh crash, duplicate marker, residual
`state=started`, timeout, stale summary misuse, memory warning, UI freeze,
normal setting persistence of `selectedPath=npu`, or unclear cleanup.

No code path was connected and no NPU generation was run in this design pass.

## DEV-Only NPU Route Adapter Boundary

Documented in:

```text
docs/litert_qairt244_dev_only_npu_route_adapter_plan.md
```

The next implementation boundary is not direct `ChatScreen` NPU wiring. It is
a `customBuildExperimentDebug`-only adapter API that can return structured
success/failure without touching normal message persistence. The first
candidate call site is before the normal local branch inserts the user message.

The adapter must remain detached from:

- normal `selectedPath=npu`
- DB persistence
- TTS
- Markdown
- streaming partials
- stop button ownership
- high-level `generateResponse`
- automatic GPU/CPU fallback

No implementation or NPU run was performed for this boundary definition.

## libcdsprpc Manifest Visibility Update

Artifact:

```text
artifacts/qairt244_libcdsprpc_manifest_experiment/20260522_231302/
```

Result:

- `customBuildExperimentDebug` now uses a dedicated manifest.
- The dedicated manifest declares
  `<uses-native-library android:name="libcdsprpc.so" android:required="false" />`.
- APK manifest and installed package dumps confirm `libcdsprpc.so` as an
  optional native library.
- `libcdsprpc.so` was not packaged in the APK.
- `QnnDevice_create` now succeeds with `status 0x0`.
- `LiteRtDispatchCheckRuntimeCompatibility` is reached and returns
  `kLiteRtStatusOk(0)`.
- `Engine.initialize` returns successfully in the explicit dry-run path.

Classification: the previous `QNN_DEVICE_ERROR_INVALID_CONFIG` / `14001`
boundary was caused by app namespace visibility of the FastRPC host dependency
`libcdsprpc.so`, not by SM8750/V79 model metadata or QNN System API mismatch.

## Initialize Stability Update

Artifact:

```text
artifacts/qairt244_initialize_stability/20260523_043345/
```

Result:

- one install, two Activity launches
- `Engine.initialize` returned successfully in both runs
- `Engine.close` returned successfully in both runs
- run 1 elapsed: `1764 ms`
- run 2 elapsed: `1527 ms`
- `LiteRtDispatchCheckRuntimeCompatibility` remained `kLiteRtStatusOk(0)`
- no `Conversation`, `Session`, `generateResponse`, token generation, or normal
  UI NPU route was used

The first script revision misread the app crash marker file. The file contained
a normal `completed=true` update, not a crash. The script was corrected to flag
crash only when `completed=false` has no later `completed=true`.

## Single-Token Smoke Prep Update

The single-token smoke was not executed. The inspected Kotlin/JNI app surface
does not expose a hard `maxOutputTokens=1` decode cap:

- `Session.runDecode()` calls JNI `nativeRunDecode(handle)` with no
  `DecodeConfig`.
- `Conversation` / `sendMessage*` / `generateContent*` can generate but are not
  the safe first path because a one-token hard cap is not visible.
- Lower-level C++ does expose `DecodeConfig.SetMaxOutputTokens(1)`.

The prepared script `scripts/run_qairt244_single_token_smoke.sh` is therefore a
blocking preflight and records `maxOutputTokens=1-not-guaranteed` instead of
launching the app.

## Lower-Level Single-Token Smoke Preflight Update

Artifact:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_052224/
```

Result:

- `classification=entrypoint-implemented-not-executed`
- LiteRT-LM C++ has `DecodeConfig.SetMaxOutputTokens(1)` capability.
- A new isolated `customBuildExperimentDebug` wrapper and native JNI symbol now
  statically call `SetMaxOutputTokens(1)`.
- Build artifact:
  `artifacts/qairt244_single_token_entrypoint_build/20260523_052106/`
- The preflight did not install, launch the app, create `Conversation`, call
  `generateResponse`, or generate tokens.

Next boundary: run exactly one lower-level smoke with `--run` if approved. This
will create a lower-level session by design, but still avoids `Conversation`,
`generateResponse`, and normal UI routing.

## Lower-Level Single-Token Smoke Execution Update

Artifact:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_053258/
```

Result:

- `classification=executed`
- `result=success`
- prompt: `Hi`
- hard cap: `max_output_tokens=1`
- elapsed: `1115 ms`
- output: `!`
- native diag reached `before RunDecode SetMaxOutputTokens(1)`
- native diag ended with `success output_candidates=1 output_bytes=1`
- timeout: `false`
- process remained alive after the smoke
- no normal UI route was used

The diagnostics collector selected an older initialize tombstone whose run id
does not match the smoke run. That tombstone is treated as stale; the smoke
result file and native diag are the primary evidence for this run.

## Lower-Level Single-Token Smoke Reproducibility Update

Artifact:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_055024/
```

Result:

- `classification=executed`
- `result=success`
- prompt: `Hi`
- hard cap: `max_output_tokens=1`
- elapsed: `907 ms`
- output: `!`
- native diag reached `before RunDecode SetMaxOutputTokens(1)`
- native diag ended with `success output_candidates=1 output_bytes=1`
- timeout: `false`
- process remained alive after the smoke
- tombstone classification: `stale-tombstone-ignored`
- no normal UI route was used

Reproducibility classification: the isolated lower-level NPU one-token smoke has
now succeeded `2/2` with the same output and no fresh crash evidence.

## Token Timing Verifier Update

Build artifact:

```text
artifacts/qairt244_token_timing_verifier_build/20260523_060634/
```

Preflight artifact:

```text
artifacts/qairt244_token_timing_verifier/20260523_061525/
```

Result:

- verifier marker: `qairt244_token_timing_verifier_v1`
- execution artifact:
  `artifacts/qairt244_token_timing_verifier/20260523_062321/`
- `result=success`
- prompt: `Hi`
- hard cap: `max_output_tokens=1`
- output: `!`
- total elapsed: `1053 ms`
- engine create elapsed: `905 ms`
- session create elapsed: `0 ms`
- prefill elapsed: `13 ms`
- decode elapsed: `22 ms`
- cleanup elapsed: `111 ms`
- prompt bytes: `2`
- output bytes: `1`
- token counts are recorded as `unavailable` with explicit source strings:
  the lower-level entrypoint does not expose tokenizer counts and `RunDecode`
  returns text, not a decoded token count
- NPU backend evidence remains `QNN_HTP_V79_FastRPC_native_diag`
- native diag includes `QnnDevice_create status 0x0`, V79 stub connection,
  FastRPC transport success, `QnnContext_createFromBinary`, and graph DSP arch
  `79`
- tombstone classification: `stale-tombstone-ignored`
- no fresh crash evidence

## NPU Diagnostic Chat Preparation

Files:

```text
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticChatActivity.kt
docs/litert_qairt244_npu_diagnostic_chat_design.md
docs/litert_qairt244_ui_integration_safety_plan.md
```

Status:

- `customBuildExperimentDebug` only
- diagnostic UI skeleton with guarded run control
- fixed prompt display: `Hi`
- fixed cap display: `maxOutputTokens=3`
- `Run 3-token smoke` button defaults disabled
- explicit `DEV confirm isolated 3-token NPU smoke` checkbox required before
  the button can be enabled
- running lock and app-side 30 second timeout marker added
- reads app-private short multi-token result/native diag files
- guarded path calls only isolated lower-level
  `Qairt244ShortMultitokenSmoke.run(...)`
- no `ChatScreen` inference path changes
- no `selectedPath=npu` normal route
- no high-level `generateResponse`
- guarded button read-only verification did not click the run button
- no additional generation run

Guarded run control verification:

- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_guarded_run/20260523_094457/`
- Activity launch succeeded on `io.github.ninbyo02.lami.customnpu`
- checkbox visible and unchecked
- `RUN 3-TOKEN SMOKE` visible with `enabled=false`
- normal ChatScreen NPU route disabled control visible

Guarded UI smoke verification:

- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_guarded_ui_run/20260523_100701/`
- result: `success`
- output: `! How Hi`
- hard cap: `max_output_tokens=3`
- elapsed: `1090 ms`
- decode elapsed: `64 ms`
- NPU backend evidence: `QNN_HTP_V79_FastRPC_native_diag`
- tombstone classification: `stale-tombstone-ignored`
- no normal UI route
- no `selectedPath=npu` normal route
- no high-level `generateResponse`

## Short Multi-Token Smoke Execution

Files:

```text
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmokeActivity.kt
scripts/run_qairt244_short_multitoken_smoke.sh
docs/litert_qairt244_short_multitoken_smoke_plan.md
docs/litert_qairt244_short_multitoken_smoke_result.md
```

Artifact:

```text
build: artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/
run:   artifacts/qairt244_short_multitoken_smoke/20260523_075743/
rerun: artifacts/qairt244_short_multitoken_smoke/20260523_085004/
```

Status:

- `customBuildExperimentDebug` app-side wrapper present
- prompt fixed to `Hi`
- native artifact proves `DecodeConfig.SetMaxOutputTokens(3)`
- dispatch preserves `DT_NEEDED [libLiteRt.so]`
- result: `success`
- output: `! How Hi`
- elapsed: `1358 ms`
- decode elapsed: `164 ms`
- tombstone classification: `stale-tombstone-ignored`
- no normal UI route
- no high-level `generateResponse`
- no fresh crash/tombstone evidence from this run

Reproducibility rerun:

- result: `success`
- output: `! How Hi`
- elapsed: `1579 ms`
- decode elapsed: `78 ms`
- tombstone classification: `stale-tombstone-ignored`
- overall: `2/2` isolated lower-level three-token NPU smoke success

Artifact cleanup:

- large rebuilt and APK-extracted native binaries under the short multi-token
  artifacts were removed from Git tracking without deleting local files
- future short multi-token smoke artifacts write a local-only binary policy note
- commit text evidence only: summaries, result/native diag, stale tombstone
  notes, Build IDs, hashes, and external diff patches

## NPU Runtime Memory Cleanup Profile

Artifact:

```text
artifacts/qairt244_npu_memory_cleanup_profile/20260523_091021/
```

Status:

- one isolated short multi-token smoke was run inside the memory profiler
- result: `success`
- output: `! How Hi`
- hard cap: `max_output_tokens=3`
- elapsed: `1423 ms`
- decode elapsed: `84 ms`
- cleanup elapsed: `110 ms`
- Native Heap PSS at 10 seconds was `212 KB` below the warm-process baseline
- TOTAL PSS at 10 seconds was `2156 KB` above the warm-process baseline
- cleanup evidence: `Engine.close=unique_ptr_cleanup` and
  `QNN_HTP_V79_FastRPC_native_diag`
- tombstone classification: `stale-tombstone-ignored`
- no leak is asserted from this single baseline
- no normal UI route, no `selectedPath=npu` normal path, no high-level
  `generateResponse`, and no streaming generation

## Cold-Start Force-Stop Cleanup Profile

Artifact:

```text
artifacts/qairt244_npu_coldstart_force_stop_profile/20260523_092801/
```

Status:

- pre-run `force-stop` removed the prior app process
- `pid_after_force_stop=none`
- `dumpsys meminfo io.github.ninbyo02.lami.customnpu` after pre-run
  force-stop returned `No process found`
- one isolated short multi-token smoke was run
- result: `success`
- output: `! How Hi`
- hard cap: `max_output_tokens=3`
- elapsed: `1572 ms`
- decode elapsed: `86 ms`
- cleanup elapsed: `103 ms`
- after smoke: TOTAL PSS `133138 KB`, Native Heap PSS `14297 KB`
- after 3 seconds: TOTAL PSS `133691 KB`, Native Heap PSS `14359 KB`
- final force-stop removed the app process
- force-stop 3s and 10s samples both had no pid and package meminfo reported
  no process
- leak classification: `no_app_process_retained_after_force_stop`
- tombstone classification: `stale-tombstone-ignored`
- no normal UI route, no `selectedPath=npu` normal path, no high-level
  `generateResponse`, and no streaming generation

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

## Native File Logger Update

Build artifact:

```text
artifacts/qairt244_native_file_logger_build/20260522_074639/
```

Dry-run diagnostics:

```text
artifacts/npu_diagnostics/20260522_074944_customnpu/
```

Curated dry-run artifact:

```text
artifacts/qairt244_native_file_logger_dry_run/20260522_074944/
```

Result:

- app-private file logger created `qairt244_native_diag.txt`
- `nativeCreateEngine` was reached
- `ModelAssets::Create` succeeded
- backend enum conversion succeeded
- `EngineSettings::CreateDefault` succeeded
- `SetLitertDispatchLibDir` was called with the app native library directory
- `EngineFactory::CreateDefault` was entered and did not return
- `DispatchDelegate::Initialize` was reached
- `InitializeDispatchApi` was reached
- `LiteRtDispatchInitialize` failed with
  `kLiteRtStatusErrorDynamicLoading(502)`
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached in this log
- QNN manager / QNN `dlopen` was not reached in this log
- `CreateDelegateKernelInterface` then aborted because
  `has_dispatch_runtime_ == false`

Interpretation: the immediate failure is now known to be dispatch runtime
dynamic loading inside `LiteRtDispatchInitialize`, before compatibility checking
and before visible QNN/HTP/skel initialization.

## dlopen Trace Build Update

Build artifact:

```text
artifacts/qairt244_dlopen_trace_build/20260522_083658/
```

The lower-level dynamic loader trace was added with marker
`qairt244_dlopen_trace_v1` at:

```text
litert/runtime/dispatch/litert_dispatch.cc
litert/cc/internal/litert_shared_library.cc
```

It records dispatch library directory propagation, selected candidate path,
raw `dlopen`, raw `dlerror`, `dlsym("LiteRtDispatchGetApi")`, and the dispatch
API version if reached.

The connected-device dry-run was not executed because no adb device was
connected:

```text
List of devices attached
```

Attempt artifact:

```text
artifacts/qairt244_dlopen_trace_dry_run/20260522_083818_no_device/
```

That original no-device run was superseded by the connected-device symbol
resolution experiments below.

## Symbol Resolution Experiment Update

Docs:

```text
docs/litert_qairt244_symbol_resolution_experiment.md
```

RTLD_GLOBAL build and dry-run:

```text
artifacts/qairt244_rtld_global_build/20260522_210118/
artifacts/qairt244_rtld_global_dry_run/20260522_210355/
artifacts/npu_diagnostics/20260522_210355_customnpu/
```

Result:

- sibling `libLiteRt.so` preload with `RTLD_NOW | RTLD_GLOBAL` succeeded
- dispatch `dlopen` with `RTLD_NOW | RTLD_GLOBAL` still failed with unresolved
  `LiteRtGetEnvironmentOptions`
- `LiteRtDispatchGetApi`, compatibility check, and QNN loading were not reached

DT_NEEDED build and dry-run:

```text
artifacts/qairt244_dispatch_needed_build/20260522_210902/
artifacts/qairt244_dispatch_needed_dry_run/20260522_211136/
artifacts/npu_diagnostics/20260522_211136_customnpu/
```

Result:

- dispatch was rebuilt with `DT_NEEDED [libLiteRt.so]`
- dispatch `dlopen` succeeded
- `dlsym("LiteRtDispatchGetApi")` succeeded
- `LiteRtDispatchGetApi` returned API version `0.1.0`
- dispatch runtime version was accepted
- dispatch vendor initialization began
- `libQnnSystem.so` `dlopen` succeeded
- `dlsym("QnnSystemInterface_getProviders")` succeeded
- dispatch vendor initialization returned
  `kLiteRtStatusErrorDynamicLoading(502)`
- `LiteRtDispatchCheckRuntimeCompatibility` was still not reached
- `libQnnHtp.so`, `libQnnHtpPrepare.so`, and V79 stub/skel were still not mapped

Interpretation: the initial unresolved LiteRT symbol boundary is fixed by a real
`DT_NEEDED` edge from Qualcomm dispatch to `libLiteRt.so`. `RTLD_GLOBAL`
preload alone is not sufficient in this Android app path. The new immediate
boundary is Qualcomm dispatch/QNN System provider initialization, before HTP
backend and compatibility checking.

## QNN Provider Trace Update

Docs:

```text
docs/litert_qairt244_qnn_provider_trace_result.md
docs/litert_qairt244_qnn_dependency_chain.md
```

Build and dry-run artifacts:

```text
artifacts/qairt244_qnn_provider_trace_build/20260522_212620/
artifacts/qairt244_qnn_provider_trace_dry_run/20260522_212949/
artifacts/npu_diagnostics/20260522_212949_customnpu/
```

Result:

- `QnnSystemInterface_getProviders` returned `qnn_status=0`
- provider count was `1`
- selected provider was `SYSTEM_QTI_AISW`, backend ID `0`
- detected QNN System API version was `1.4.0`
- LiteRT expected QNN System API minimum was `1.8.0`
- `ResolveSystemApi` returned `kLiteRtStatusErrorDynamicLoading(502)` with
  `reason=system_minor actual=4 expected_min=8`
- `libQnnHtp.so`, `libQnnHtpPrepare.so`, and V79 stub/skel were not reached
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached

Interpretation: after fixing the missing dispatch `DT_NEEDED [libLiteRt.so]`
edge, the next concrete failure is a QNN System API version mismatch in
`libQnnSystem.so`. The failure occurs before HTP backend loading or capability
checking.

## QNN Runtime Alignment Update

Docs:

```text
docs/litert_qairt244_qnn_runtime_alignment_result.md
```

Build and dry-run artifacts:

```text
artifacts/qairt244_qnn_aligned_build/20260522_215238/
artifacts/qairt244_qnn_aligned_dry_run/20260522_215421/
artifacts/npu_diagnostics/20260522_215421_customnpu/
```

Result:

- customBuildExperimentDebug staged QAIRT 2.44 SDK `libQnnSystem.so`,
  `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and V79 skel
- QNN System API advanced from `1.4.0` to `1.8.0`
- `ResolveSystemApi` returned OK
- `libQnnHtp.so` dlopen succeeded
- HTP provider `HTP_QTI_AISW` was selected with core `2.33.0` and backend
  `5.44.0`
- `ResolveApi` returned OK
- `QnnManager::Init` now returns `kLiteRtStatusErrorRuntimeFailure(3)` with
  `reason=HtpBackendInit`
- `libQnnHtpPrepare.so`, V79 stub, V79 skel, and
  `LiteRtDispatchCheckRuntimeCompatibility` are still not reached

Interpretation: the QNN System generation mismatch was a real blocking issue
and is now resolved by QAIRT 2.44 runtime alignment. The current boundary is HTP
backend initialization, before prepare/stub/skel loading is visible and before
LiteRT dispatch compatibility checking.

## HTP Backend Trace Update

Docs:

```text
docs/litert_qairt244_htp_backend_trace_result.md
docs/litert_qairt244_htp_dependency_analysis.md
```

Build and dry-run artifacts:

```text
artifacts/qairt244_htp_backend_trace_aligned_build/20260522_222215/
artifacts/qairt244_htp_backend_trace_dry_run/20260522_222434/
artifacts/npu_diagnostics/20260522_222434_customnpu/
```

Result:

- `HtpBackend::Init` was reached
- `QnnBackend_create` succeeded and returned handle `0x1`
- `QnnDevice_getPlatformInfo` succeeded
- runtime SoC detection selected `SM8750`, DSP architecture `79`, and
  VTCM `8` MB
- `QnnDevice_create` failed
- raw `QnnDevice_create` status was `14001`
- `QNN_GET_ERROR_CODE(QnnDevice_create)` was `14001`
- `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and
  `libQnnHtpV79Skel.so` were still not mapped before abort
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached
- the top-level process still aborted at
  `DispatchDelegate::CreateDelegateKernelInterface`

Dependency analysis result:

- aligned build and APK-extracted QNN/HTP libraries are byte-identical
- `libQnnHtp.so` does not statically `DT_NEEDED` prepare/stub/skel; that path
  is runtime-loaded
- direct FastRPC dependency appears at
  `libQnnHtpV79Stub.so -> libcdsprpc.so`
- the device exposes SM8750/CDSP/FastRPC surface and vendor `libcdsprpc.so`
  under rootless read-only inspection

Interpretation: the current immediate failure is specifically
`HtpBackend::Init -> QnnDevice_create -> 14001`, before prepare/stub/skel are
visibly loaded and before LiteRT dispatch compatibility checking.

## QNN Backend Log Callback Update

Docs:

```text
docs/litert_qairt244_qnn_status_14001.md
docs/litert_qairt244_htp_log_callback_result.md
docs/litert_qairt244_htp_pd_fastrpc_config_analysis.md
```

Build and dry-run artifacts:

```text
artifacts/qairt244_htp_log_callback_aligned_build/20260522_224734/
artifacts/qairt244_htp_log_callback_dry_run/20260522_225623/
artifacts/npu_diagnostics/20260522_225623_customnpu/
```

Result:

- `14001` is `QNN_DEVICE_ERROR_INVALID_CONFIG`
- QNN backend log callback was captured with marker
  `qairt244_htp_log_callback_v1`
- `QnnDevice_create` attempted to load `libQnnHtpV79Stub.so`
- the absolute and basename stub load attempts both failed because
  `libcdsprpc.so` was not found in Android linker namespace `clns-9`
- QNN logged `loadRemoteSymbols failed with err 4000`
- QNN logged transport creation and skel load failures
- QNN then returned `QnnDevice_create` status `0x36b1` / `14001`
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached
- `Engine.initialize` did not return

Interpretation: the immediate blocker is no longer an opaque config mismatch.
It is Android app namespace resolution of the FastRPC host dependency
`libcdsprpc.so` needed by `libQnnHtpV79Stub.so`.

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
| H1. SM8750/V79 dispatch capability mismatch | With `libcdsprpc.so` declared as an optional native library, `LiteRtDispatchCheckRuntimeCompatibility` is reached and returns `kLiteRtStatusOk(0)`. | low for initialize | Keep as a later inference/runtime risk, not an initialize blocker. |
| H2. Android app nativeLibraryDir QNN/HTP search problem | `SetLitertDispatchLibDir` propagates the app native library directory correctly. Dispatch, QNN System, QNN HTP, and V79 stub now load far enough for initialize to return. | low | Keep current nativeLibraryDir propagation unchanged. |
| H3. ADSP_LIBRARY_PATH / FastRPC / skel-stub path problem | Previous backend log showed `libQnnHtpV79Stub.so` failed because `libcdsprpc.so` was not visible in namespace `clns-9`. Adding `uses-native-library libcdsprpc.so` fixes that boundary and `QnnDevice_create` returns `0`. | resolved for initialize | Keep the manifest declaration isolated to `customBuildExperimentDebug`; do not stage vendor `libcdsprpc.so` unless a future device lacks manifest visibility. |
| H4. Qualcomm SM8750 model/runtime schema mismatch | Model carries QAIRT 2.44, SM8750, V79, and dispatch/QNN partition markers. Initialize, compatibility, and one lower-level one-token decode now succeed. | low for current smoke | Defer deeper schema questions until a broader isolated prompt/decode verifier needs it. |
| H5. Dispatch runtime registration / capability check failure | `DT_NEEDED [libLiteRt.so]`, QAIRT 2.44 QNN runtime alignment, QNN backend init, compatibility check, and lower-level one-token decode now all succeed in the explicit path. | resolved for current smoke | Preserve the diagnostic baseline and do not wire NPU into normal UI yet. |
| H6. CLI litert_lm_main works while Android app fails | Android app initialize-only path now succeeds. Existing upstream CLI is still unsafe because it creates a `Conversation` and sends a prompt. | lower priority | CLI proof remains useful only if implemented as initialize-only and non-generating. |

## Ranked Next Moves

1. Preserve the `customBuildExperimentDebug`-only
   `uses-native-library libcdsprpc.so` manifest declaration.
2. Treat explicit `Engine.initialize` as proven for this diagnostic path only;
   do not connect NPU to normal UI inference yet.
3. Keep QAIRT 2.44 QNN runtime alignment and the dispatch `DT_NEEDED
   [libLiteRt.so]` fix as required baseline conditions.
4. If the next phase needs app execution, keep it isolated from the normal UI
   and improve live tombstone filtering before expanding beyond one token.
5. Keep the fallback device `libcdsprpc.so` staging script for investigation
   only; do not commit or redistribute pulled vendor libraries.

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

The strongest current classification is now resolved for initialize:

```text
customBuildExperimentDebug manifest -> uses-native-library libcdsprpc.so
-> V79 stub can resolve libcdsprpc.so -> QnnDevice_create status=0
-> LiteRtDispatchCheckRuntimeCompatibility status=kLiteRtStatusOk(0)
-> Engine.initialize returned
```

Earlier blockers are now understood and bypassed in the diagnostic baseline:

- dispatch needed a real `DT_NEEDED [libLiteRt.so]`
- packaged QNN System had to be aligned to QAIRT 2.44 so System API reached
  `1.8.0`
- `libQnnHtp.so` now loads and the HTP API resolves
- the app manifest must declare optional `libcdsprpc.so` for FastRPC host
  dependency visibility

The previous `QNN_DEVICE_ERROR_INVALID_CONFIG` / `14001` was the public QNN
error surfaced from the missing FastRPC host dependency visibility. With
manifest visibility fixed, it is no longer the current boundary.

The model appears aligned with QAIRT 2.44 and SM8750/V79, so model generation
mismatch is not the lead hypothesis at this boundary.

## Automated Diagnostic Chat UI Smoke

Result date: 2026-05-23

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_smoke/20260523_102810/
```

Result:

- `scripts/run_qairt244_npu_diagnostic_chat_ui_smoke.sh` selected the
  non-emulator Nubia device and ran the guarded Diagnostic Chat UI path.
- The DEV checkbox was tapped once and `RUN 3-TOKEN SMOKE` was tapped once.
- The isolated lower-level path returned:
  `result=success`, `output=! How Hi`, `max_output_tokens=3`.
- `npu_backend=NPU` with `QNN_HTP_V79_FastRPC_native_diag` evidence.
- Tombstone classification: `stale-tombstone-ignored`; no current-run id was
  present in the selected old tombstone/dropbox body.
- The normal `ChatScreen` route remains disconnected, and the normal
  `selectedPath=npu` route remains unused.

Matrix update:

| Hypothesis | UI runner evidence | Current status |
| --- | --- | --- |
| H1. SM8750/V79 dispatch capability mismatch | Reproducible 3-token Diagnostic Chat UI smoke reaches RunDecode with V79/FastRPC evidence. | Low for bounded smoke. |
| H2. Android namespace/path issue | `libcdsprpc.so` manifest visibility plus QAIRT 2.44 aligned libs remain sufficient in UI-driven diagnostic path. | Resolved for diagnostic path. |
| H3. ADSP/FastRPC/skel issue | Native diag reports QNN/HTP/V79/FastRPC evidence during the UI run. | Resolved for bounded smoke. |
| H4. Model schema mismatch | Same SM8750/V79 model produces bounded 3-token output. | Low for current scope. |
| H5. Dispatch registration/check failure | Compatibility and decode path remain functional through UI diagnostic runner. | Resolved for current scope. |
| H6. CLI vs Android app difference | Android app diagnostic UI can run the isolated lower-level path. | Lower priority. |

## Diagnostic Chat UI Multi-Run Attempt

Result date: 2026-05-23

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_110017/
```

Evidence:

- run1 captured `result=success`, `output=! How Hi`,
  `max_output_tokens=3`, `npu_backend=NPU`
- run2 captured `result=success`, `output=! How Hi`,
  `max_output_tokens=3`, `npu_backend=NPU`
- both runs captured `QNN_HTP_V79_FastRPC_native_diag`
- both tombstone checks classified stale selected tombstones, not fresh crashes
- after 10 seconds: TOTAL PSS `64721 KB`, Native Heap PSS `17860 KB`

Host runner caveat:

- the first multi-run script stopped on an earlier `state=success` marker while
  a later guarded UI marker still had `state=started`
- the script now waits on the last guarded UI marker state
- this attempt is recorded as useful evidence but not the final multi-run
  stability proof

Root cause status does not regress: the bounded NPU decode path still works and
normal UI integration remains intentionally disconnected.

## Fixed Diagnostic Chat UI Multi-Run Verification

Result date: 2026-05-23

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/
```

Evidence:

- run1 final guarded marker: `state=success`
- run2 final guarded marker: `state=success`
- no final `state=started` marker remained in either result file
- run1/run2 both returned `result=success`, `output=! How Hi`,
  `max_output_tokens=3`, and `npu_backend=NPU`
- both runs captured `QNN_HTP_V79_FastRPC_native_diag`
- both tombstone checks remained `stale-tombstone-ignored`
- after 10 seconds: TOTAL PSS `78536 KB`, Native Heap PSS `20571 KB`

Matrix update: H1-H5 remain resolved or low-risk for this bounded diagnostic
path. The evidence still does not authorize wiring NPU into the normal
`ChatScreen`; it only verifies the isolated Diagnostic Chat runner.

## Diagnostic Chat Result Viewer Update

Result date: 2026-05-23

The `customBuildExperimentDebug` Diagnostic Chat screen now shows the latest
fixed runner result in a read-only `Latest runner` section:

- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/`
- run1 and run2 result/output/timing
- final guarded marker state
- final `state=started` status
- after-10s TOTAL PSS and Native Heap PSS
- stale/fresh tombstone classification
- fresh crash status

The same screen also shows route guard status:

- normal `ChatScreen` route disabled
- normal `selectedPath=npu` route disabled
- high-level `generateResponse=false`
- streaming disabled
- `Refresh result view` does not run NPU

The latest runner summary can now be synced from host artifacts into:

```text
/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_diagnostic_runner_summary.txt
```

using:

```text
scripts/sync_qairt244_npu_diagnostic_summary_to_app.sh
```

The sync path copies existing artifact evidence only. It records
`npu_generation=not_run`, `engine_initialize=not_run`, `run_decode=not_run`,
and `activity_launch=not_run`.

Freshness indicator update:

- artifact:
  `artifacts/qairt244_npu_diagnostic_summary_freshness/20260523_124234/`
- threshold: 24 hours
- observed source artifact age: `59m 51s`
- observed status: `fresh`
- observed warning: `none`
- no NPU generation, Engine.initialize, or RunDecode was executed for this
  verification

Short prompt DEV guard spec:

- spec:
  `docs/litert_qairt244_diagnostic_short_prompt_guard_spec.md`
- status: design only
- editable input: not enabled
- preview-only validation UI: enabled for fixed `Hi`
- preview input state: disabled
- Run button connection on default Activity launch: false
- guarded run intent extra required: true
- NPU generation: not run
- Engine.initialize: not run
- RunDecode: not run
- normal `ChatScreen`: not connected
- normal `selectedPath=npu`: not used
- hard future cap: `maxOutputTokens=3`
- prompt limit: 32 ASCII-only characters for the first editable phase

Editable prompt preview:

- artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_preview/20260523_133833/`
- Activity extra: `allowEditablePromptPreview=true`
- default input state: disabled
- extra-enabled input state: enabled
- validation preview: connected to `NpuDiagnosticPromptValidator`
- OK preview: `Hi`, `reasonCode=ok`
- NG preview: `Hello/LamiHi`, `reasonCode=contains_disallowed_char`
- prompt execution connection: false
- guarded Run button input source: fixed `Hi`
- `allowEditablePromptPreview` does not imply `allowGuardedNpuRun`
- NPU generation, Engine.initialize, RunDecode, high-level `generateResponse`,
  normal `ChatScreen`, and normal `selectedPath=npu` remain unused

Editable prompt connection design:

- plan:
  `docs/litert_qairt244_diagnostic_editable_prompt_connection_plan.md`
- status: design review only
- future connection requires:
  `allowEditablePromptPreview=true`, `allowGuardedNpuRun=true`,
  `allowEditablePromptExecution=true`, DEV checkbox checked, valid prompt,
  `running=false`
- Run button enable condition: all gates pass and native
  `maxOutputTokens=3` remains fixed and native editable prompt support exists
- NG input behavior: Run disabled, reasonCode displayed, no native execution
- current native state: fixed `prompt=Hi`, so
  `native_editable_prompt_supported=false` and editable execution is
  preflight-blocked
- stale freshness behavior: warning visible, not a hard block
- artifact target:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/<timestamp>/`
- preflight artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_175939/`
- observed preflight: `blocked_native_fixed_hi`, no Engine.initialize, no
  RunDecode, no NPU generation

Root-cause interpretation is unchanged: QAIRT 2.44 NPU is proven for bounded
isolated Diagnostic Chat smoke runs, while normal chat integration remains a
separate future gate.

ChatScreen DEV-only blocked branch:

- location:
  `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt:2349`
- current gate:
  `BuildConfig.CUSTOM_BUILD_EXPERIMENT && devEnableNpuChatScreenRoute`
- setting key:
  `dev_enable_npu_chatscreen_route`
- default:
  `false`
- false toggle behavior:
  existing LOCAL path unchanged
- true path:
  `customBuildExperimentDebug` reflection target only
- true path result:
  `adapter_not_connected`
- side effects:
  DB/TTS/Markdown/streaming/stop-button/selectedPath all disabled
- real NPU adapter:
  not connected
- Engine.initialize / RunDecode / high-level generateResponse:
  not executed by this branch

DEV hidden toggle boundary:

- artifact:
  `artifacts/qairt244_dev_hidden_npu_chatscreen_toggle_boundary/20260523_222339/`
- Settings UI:
  visible only in customBuildExperimentDebug DEBUG settings
- observed switch:
  `checked=false`
- switch toggled:
  no
- classification:
  UI/settings boundary only; no backend behavior change

## No-Run Confirmation

This coordinator pass did not run:

- `generateResponse`
- prompt generation
- `Conversation` creation
- `Session` creation
- `selectedPath=npu`
- normal UI `Backend.NPU` wiring
- single-token smoke test
- more than the single allowed native file logger `Engine.initialize` dry-run
- unsafe native library replacement
## ChatScreen DEV Toggle Boundary (2026-05-23)

| Area | Result | Evidence | Status |
| --- | --- | --- | --- |
| Hidden toggle default/recovery | OFF after verification | `toggle_state_before.txt`, `toggle_state_after_off.txt` | OK |
| Toggle ON branch | Blocked branch fired | `screenshot_after.png` | OK |
| Adapter result | `adapter_not_connected` transient display | `summary.md`, screenshot | Expected |
| DB/TTS/Markdown/streaming | Not connected | blocked branch side-effect flags and prompt not cleared | OK |
| `selectedPath=npu` | Not applied | toggle state files and runtime marker scan | OK |
| NPU execution | Not executed | empty `runtime_marker_scan.txt` for Engine/RunDecode/QNN/HTP markers | OK |
| Final state | Toggle restored OFF | `toggle_state_after_off.txt` | OK |

## Real Adapter Preflight Rollback Matrix (2026-05-24)

| Condition | Required action | Reason |
| --- | --- | --- |
| Fresh crash | Roll back toggle OFF | Avoid repeating unstable native path |
| Timeout | Roll back toggle OFF | Prevent UI lock or runaway inference |
| Duplicate success marker | Roll back and inspect artifact | Ambiguous one-shot accounting |
| Missing close/cleanup evidence | Roll back | Resource state unknown |
| `selectedPath=npu` saved/applied | Roll back and fix settings path | NPU must not become normal route state |
| DB/TTS/Markdown/streaming receives output | Roll back | Initial integration must remain transient |
| Toggle fails to return OFF | Roll back manually and block next run | DEV route must be one-shot recoverable |
| Success with side-effect flags true | Roll back | Presenter/adapter contract violated |
| Stale artifact/summary used | Do not run | Execution evidence must be fresh |
| After-10s memory materially elevated | Roll back and profile | Possible cleanup regression |
| UI freeze/running lock unclear | Roll back | User-facing recovery unproven |
| Missing QNN/HTP/FastRPC evidence | Treat as failure | Backend identity unproven |

The 2026-05-24 review is docs and static grep only. Real adapter connection,
NPU generation, `Engine.initialize`, and `RunDecode` were not executed.

## ChatScreen Real Adapter First Attempt (2026-05-24)

| Stage | Result | Evidence | Status |
| --- | --- | --- | --- |
| DEV toggle reset | `after=false` | `toggle_state_before.txt` | OK |
| DEV toggle ON | `after=true` | `toggle_state_after_on.txt` | OK |
| ChatScreen branch | Real adapter path entered | `qairt244_chat_screen_real_npu_adapter_v1` marker | OK |
| Prompt | `Hello` | `result.txt` | OK |
| max output tokens | `3` | `result.txt` / native marker preflight | OK |
| Model path | fixed path missing | `detail=model-file-not-found` | Rollback |
| Engine.initialize | not reached | `native_diag.txt` | Safe stop |
| RunDecode | not reached | `native_diag.txt` | Safe stop |
| DB/TTS/Markdown/streaming | not connected | route marker side-effect flags | OK |
| selectedPath=npu | not saved | summary / toggle state | OK |
| Fresh crash | false | `stale_tombstone_note.md` | OK |
| Toggle recovery | `after=false` | `toggle_state_after_off.txt` | OK |

Classification: `rollback-model-file-not-found`. The next investigation is
model path discovery for the ChatScreen adapter, not QNN/HTP runtime behavior.

## ChatScreen Model Path Resolution Attempt (2026-05-24)

| Stage | Result | Evidence | Status |
| --- | --- | --- | --- |
| DEV toggle reset | `after=false` | `toggle_state_before.txt` | OK |
| DEV toggle ON | `after=true` | `toggle_state_after_on.txt` | OK |
| Model listing | one `.litertlm` file | `model_files_listing.txt` | OK |
| Model resolver | selected one readable non-empty path | `resolved_model_path.txt` | OK |
| Prompt | `Hello` | `result.txt` | OK |
| max output tokens | `3` | `result.txt` / native marker preflight | OK |
| Engine create | failed, `TF_LITE_AUX not found in the model` | `result.txt` / `native_diag.txt` | Rollback |
| NPU evidence | `QNN_HTP_V79_FastRPC_native_diag` | `result.txt` / `native_diag.txt` | Present |
| DB/TTS/Markdown/streaming | not connected | route marker side-effect flags | OK |
| selectedPath=npu | not saved | resolver artifact / summary | OK |
| Fresh crash | false | `stale_tombstone_note.md` | OK |
| Toggle recovery | `after=false` | `toggle_state_after_off.txt` | OK |

Classification: `rollback-model-missing-tf-lite-aux`. The previous
`rollback-model-file-not-found` root cause is closed for this device state; the
current root cause is an app-private model file that is readable but not in the
compiled NPU format expected by the QAIRT LiteRT-LM executor.

## DEV-only NPU Output Artifact Leakage (2026-05-25)

| Stage | Result | Evidence | Status |
| --- | --- | --- | --- |
| Native decode | reached | `run_decode_reached=true` | OK |
| NPU backend | `NPU` | `QNN_HTP_V79_FastRPC_native_diag` | OK |
| Raw output | prompt echo and Gemma turn artifacts | `raw_output.txt` | Needs cleanup |
| Sanitizer | removed template artifacts and leading prompt echo | `sanitized_output.txt` | OK |
| Display output | natural Japanese sentence | `result.txt` | OK |
| Fallback | `false` | `result.txt` | OK |
| Timeout/fresh crash | `false` / `false` | `summary.md` | OK |
| DB/TTS/Markdown/streaming | disconnected | route marker / `result.txt` | OK |
| selectedPath=npu | not saved | `result.txt` | OK |

Artifact:

```text
artifacts/qairt244_npu_output_sanitizer/20260525_015040/
```

Classification: `dev-only-output-template-artifact-sanitized`. The remaining
NPU route is healthy; the observed issue was display-quality leakage from Gemma
turn markers (`<end_of_turn>` and related role markers), not a QNN fallback,
timeout, crash, or model-selection regression.

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
