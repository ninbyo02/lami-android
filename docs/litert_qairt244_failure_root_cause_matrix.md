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
and the 2026-05-23 connected-device token timing verifier run.

## Current Boundary

- flavor: `customBuildExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.customnpu`
- runId: `1779485001728`
- diagnostic artifact: `artifacts/qairt244_token_timing_verifier/20260523_062321/`
- final stage: `done`
- returned: yes
- signal: no fresh crash evidence; diagnostics collector selected a stale older
  tombstone that does not contain the current smoke run id
- immediate native boundary:
  `uses-native-library libcdsprpc.so -> QnnDevice_create success -> LiteRtDispatchCheckRuntimeCompatibility success -> Engine.initialize success -> lower-level RunDecode maxOutputTokens=1 success -> token/timing verifier success`

The explicit smoke created only the lower-level native LiteRT-LM session needed
for decode. It did not create `Conversation`, did not create a Kotlin/public
`Session` object, did not call high-level `generateResponse`, and did not wire
normal UI inference to NPU.

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
- read-only UI skeleton
- fixed prompt display: `Hi`
- fixed cap display: `maxOutputTokens=1`
- run button disabled
- reads only app-private result/native diag files
- no `ChatScreen` inference path changes
- no `selectedPath=npu` normal route
- no high-level `generateResponse`
- no additional generation run

## Short Multi-Token Smoke Preparation

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
artifacts/qairt244_short_multitoken_smoke/20260523_071934/
```

Status:

- `customBuildExperimentDebug` app-side wrapper present
- prompt fixed to `Hi`
- requested hard cap: `maxOutputTokens=3`
- execution blocked until rebuilt native artifact proves
  `DecodeConfig.SetMaxOutputTokens(3)`
- no additional generation run
- no normal UI route
- no high-level `generateResponse`
- no fresh crash/tombstone evidence from this preflight

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
