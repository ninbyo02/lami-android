# LiteRT-LM Qualcomm dispatch runtime compatibility analysis

Date: 2026-05-16

Related commit before this investigation: `eaf009bb Collect LiteRT NPU crash diagnostics`

Artifacts:

- Crash diagnostics: `artifacts/npu_diagnostics/20260516_100056/`
- Runtime comparison: `artifacts/litert_runtime_compatibility/20260516_101919/`
- Matrix: `docs/litert_lm_runtime_version_matrix.md`

This document narrows the cause of the `npuExperimentDebug` Engine.initialize dry-run SIGABRT. It does not propose enabling NPU inference in the app. `Conversation`, `Session`, and `generateResponse` remain forbidden for this phase.

## Observed failure

The isolated dry-run reached:

- model file exists: true
- model length: `3016294400`
- `Backend.NPU(String nativeLibraryDir)`: created
- `EngineConfig(modelPath, Backend.NPU, ...)`: created
- `Engine(EngineConfig)`: returned
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- process: not running after invocation
- signal: `SIGABRT`

The tombstone stack includes:

- `liblitertlm_jni.so`
- `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine`
- `com.google.ai.edge.litertlm.Engine.initialize`
- `AcceleratorProbe.invokeEngineInitializeOperation`

The tombstone does not expose a clean `Abort message:` line, but register fragments and strings in `liblitertlm_jni.so` point to:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

## Key evidence

### Not missing dispatch

`npuExperimentDebug` contains and expands:

- `libLiteRtDispatch_Qualcomm.so`
- SHA-256: `92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777`
- Build ID: `643ad77b8ac2f54bd1b61e4133c77b3a`
- export: `LiteRtDispatchGetApi@@VERS_1.0`
- NEEDED: `libLiteRt.so`, `libandroid.so`, `liblog.so`, `libdl.so`, `libc.so`, `libm.so`

The app nativeLibraryDir also contains Lami's `libLiteRt.so`, `liblitertlm_jni.so`, and QNN runtime libs including V79 skel/stub.

### Runtime generation mismatch

| Component | Gallery SM8750 | Lami npuExperimentDebug | Assessment |
| --- | --- | --- | --- |
| `libLiteRtDispatch_Qualcomm.so` | Build ID `643ad77b8ac2f54bd1b61e4133c77b3a` | same | Dispatch was copied exactly from Gallery SM8750 into the isolated flavor. |
| `libLiteRt.so` | Build ID `869121bd7f4b0b77fa581218117a5c14` | `80fa0688ac32301185275c903cec97bd` | different |
| `liblitertlm_jni.so` | Build ID `76e4dccd9c5f9cba468d9cae7becfec0` | `c2c27170ba409dbd0bc01820fa738580` | different |
| `libQnnSystem.so` | Build ID `0d409cdd664b8b0a` | `94d63184c6b1f968` | different |
| `libQnnHtp.so` | Build ID `f2c90c1775a109e1` | `e227353d86be672b` | different |
| `libQnnHtpV79Stub.so` | Build ID `10d7ad6f9195411a` | `c079c75e0fd8ee92` | different |
| `libQnnHtpV79Skel.so` | no GNU Build ID | no GNU Build ID, different SHA-256 | different |

The dispatch runtime itself is present and loadable enough to be mapped, but the LiteRT-LM engine does not accept it as a usable runtime.

### Dispatch/capability checks present in binaries

`libLiteRtDispatch_Qualcomm.so` contains checks for:

- Qualcomm dispatch API version and QNN API version.
- LiteRT API older-than-dispatch warnings.
- QNN system/backend/library version mismatch and unsupported-version errors.
- context binary SDK compatibility.

Lami `libLiteRt.so` contains:

- `LiteRtDispatchGetApiVersion@@VERS_1.0`
- `LiteRtDispatchGetCapabilities@@VERS_1.0`
- `Dispatch API capabilities: %d`
- `Dispatch API graph interface not found`
- `Failed to initialize Dispatch API: %s`
- `get_capabilities not found`

Lami `liblitertlm_jni.so` contains:

- `Dispatch API has insufficient capabilities: %d`
- `Failed to get Dispatch API capabilities: %d`
- `Found Dispatch API with an unsupported version`
- `LiteRtDispatchGetApi`

The failure is therefore consistent with dispatch discovery/capability negotiation rather than plain file absence.

## Tombstone offset notes

The relevant tombstone is `artifacts/npu_diagnostics/20260516_100056/tombstone_latest.txt`.

Top LiteRT-LM frames:

```text
#01 pc 00000000007dfd60 liblitertlm_jni.so
#02 pc 00000000007e7200 liblitertlm_jni.so
#03 pc 0000000000d88c34 liblitertlm_jni.so
#04 pc 0000000000d88698 liblitertlm_jni.so
#05 pc 0000000000d882d0 liblitertlm_jni.so
#06 pc 00000000007ee5e0 liblitertlm_jni.so
#07 pc 00000000007e714c liblitertlm_jni.so
#08 pc 0000000000d8d3e4 liblitertlm_jni.so
#09 pc 0000000000d8e2c8 liblitertlm_jni.so
#10 pc 0000000000d823a8 liblitertlm_jni.so
#11 pc 00000000007c3318 liblitertlm_jni.so
#12 pc 00000000007bf43c liblitertlm_jni.so
#13 pc 00000000006e3f20 liblitertlm_jni.so
#14 pc 00000000006ed594 liblitertlm_jni.so
#15 pc 00000000006ed3f4 liblitertlm_jni.so
#16 pc 00000000006e12f4 liblitertlm_jni.so
#17 pc 00000000006dece0 liblitertlm_jni.so
#18 pc 00000000004b429c liblitertlm_jni.so
#19 pc 00000000004b0420 liblitertlm_jni.so
#20 pc 00000000004afd60 liblitertlm_jni.so Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1652
```

`addr2line` can only resolve exported or nearby stripped symbols. It confirms the native JNI entry around `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine`, but source lines are unavailable without matching symbols/debug files for Build ID `c2c27170ba409dbd0bc01820fa738580`.

## Root cause ranking

1. `libLiteRtDispatch_Qualcomm.so` and Lami `libLiteRt.so` / `liblitertlm_jni.so` dispatch API/capability mismatch.
   Evidence: dispatch exists, symbol exists, `libLiteRt` and `liblitertlm_jni` Build IDs differ from Gallery, and the abort is `No usable Dispatch runtime found`.

2. QNN runtime generation mismatch.
   Evidence: Gallery and Lami QNN libs differ. The dispatch runtime contains QNN system/backend version checks. Lami uses `qnn-runtime:2.34.0`; Gallery SM8750 ships a different QNN set.

3. Dispatch runtime search or capability path issue.
   Evidence: app nativeLibraryDir has dispatch, but `libLiteRt` might reject it during `LiteRtDispatchGetApi` / capability discovery. This is less likely than generation mismatch because the staged dispatch file is present and exact.

4. HTP skel/stub or ADSP search path issue.
   Evidence: V79 skel/stub are present in the app payload and an external QAIRT tree is available, but the dispatch runtime contains HTP/QNN path-sensitive checks. This remains possible if QNN expects a different ADSP search layout.

5. Qualcomm SM8750 model/runtime schema mismatch.
   Evidence: model exists and has the expected size; the failure occurs during dispatch delegate creation before any generated response. Schema mismatch is still possible but lower-ranked until dispatch/QNN generation alignment is resolved.

6. Missing LiteRT runtime C API library.
   Evidence: `libLiteRt.so` is present and exports dispatch API support symbols. This is currently unlikely.

## Known issue alignment

This matches the class of reports where `litertlm-android` exposes `Backend.NPU`, but the required Qualcomm dispatch runtime is not bundled or is not compatible with the Maven AAR native stack. It also matches the risk reported for standalone/public HEAD dispatch builds: a dispatch `.so` can export the expected symbol but still fail due to API layout, capability, or generation mismatch.

The Lami result is slightly different from the pure missing-file case: Lami has a dispatch `.so`, but it is from Gallery SM8750 and paired with Lami Maven `litertlm-android:0.11.0` native libs. The failure therefore points to "present but not usable", not "missing".

## Independent build decision

Do not build `dispatch_api_so` yet.

Reasons:

- The failure already occurs with a real Gallery SM8750 dispatch runtime present.
- Lami's `libLiteRt.so` and `liblitertlm_jni.so` are from Maven `litertlm-android:0.11.0`, not Gallery's native stack.
- Public HEAD or arbitrary-source dispatch builds may reproduce or worsen dispatch API layout mismatch.
- A useful build would need the exact LiteRT/LiteRT-LM source generation matching Build ID `80fa0688ac32301185275c903cec97bd` / `c2c27170ba409dbd0bc01820fa738580`, or a full matched native stack in a completely isolated experiment.

## Recommended next actions

1. Identify the LiteRT/LiteRT-LM source tag or internal build generation corresponding to Maven `litertlm-android:0.11.0` Build IDs:
   - `libLiteRt.so`: `80fa0688ac32301185275c903cec97bd`
   - `liblitertlm_jni.so`: `c2c27170ba409dbd0bc01820fa738580`

2. Search for a same-generation Qualcomm dispatch runtime for that Maven native stack. Prefer an official AAR/sample/APK over a standalone build.

3. If testing Gallery native libs as a matched stack, do it only in a new isolated debug flavor or separate app id. Do not replace Lami standard/debug native libs in place.

4. Report the issue upstream with:
   - SoC/device: NX733J / SM8750 / SDK 36
   - model: `gemma-4-E2B-it_qualcomm_sm8750.litertlm`
   - tombstone frames and Build IDs
   - dispatch SHA/build ID
   - evidence that dispatch is present but `Engine.initialize` aborts with `No usable Dispatch runtime found`

5. Keep normal Lami inference on GPU:
   - `selectedPath=gpu`
   - `QNN/NPU attempted=no`
   - no `Conversation` / `Session` / `generateResponse` in the NPU experiment path.

## npuExperimentDebug Maven 0.10.0 split result

Experiment date: 2026-05-16

Change under test:

- `standardDebug` remains on `litertlm-android:0.11.0`.
- `npuExperimentDebug` resolves only `litertlm-android:0.10.0`.
- The staged Gallery SM8750 `libLiteRtDispatch_Qualcomm.so` remains only in the isolated `npuExperimentDebug` native payload.
- No native libraries were manually replaced and no dispatch library was built.

Resolved dependency evidence:

```text
artifact_dir=artifacts/litertlm_flavor_dependencies/20260516_182950
standardDebugRuntimeClasspath has litertlm-android:0.11.0: yes
standardDebugRuntimeClasspath selects litertlm-android:0.10.0: no
npuExperimentDebugRuntimeClasspath has litertlm-android:0.10.0: yes
npuExperimentDebugRuntimeClasspath selects litertlm-android:0.11.0: no
standardReleaseRuntimeClasspath has litertlm-android:0.10.0: yes
overall: expected-split
```

Native Build IDs after switching `npuExperimentDebug` to Maven `0.10.0`:

| Component | Build ID | Note |
| --- | --- | --- |
| `liblitertlm_jni.so` | `ecacedccf835d7674c95bd40186d0fde` | Maven `litertlm-android:0.10.0` payload |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | Gallery SM8750 dispatch payload |
| `libLiteRt.so` | not packaged | Public Maven `0.10.0` does not include this Gallery SM8750 library |

Gallery SM8750 comparison:

| Component | Gallery SM8750 Build ID | npuExperimentDebug after split | Assessment |
| --- | --- | --- | --- |
| `liblitertlm_jni.so` | `76e4dccd9c5f9cba468d9cae7becfec0` | `ecacedccf835d7674c95bd40186d0fde` | different |
| `libLiteRt.so` | `869121bd7f4b0b77fa581218117a5c14` | not packaged | missing from Maven `0.10.0` payload |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | `643ad77b8ac2f54bd1b61e4133c77b3a` | same dispatch file |

Engine.initialize dry-run evidence:

```text
artifacts/npu_diagnostics/20260516_182607/
runId=1778923550718
model file exists true
model file length 3016294400
Backend.NPU created class=com.google.ai.edge.litertlm.Backend$NPU
EngineConfig created class=com.google.ai.edge.litertlm.EngineConfig
Engine constructor returned resultClass=com.google.ai.edge.litertlm.Engine
Engine.initialize invoking method=Engine.initialize(): void
pid after probe: <not-running>
crash suspected: true
```

Tombstone summary:

```text
signal 6 (SIGABRT)
#20 pc 00000000006f6e2c liblitertlm_jni.so
    Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1516
    BuildId: ecacedccf835d7674c95bd40186d0fde
```

The latest tombstone does not contain a clear `Abort message:` line. The observable change is the native generation in the crash stack: it now uses Maven `0.10.0` `liblitertlm_jni.so` (`ecaced...`) instead of Maven `0.11.0` (`c2c...`). The process still aborts during `Engine.initialize` after the `Engine(EngineConfig)` constructor returns.

Updated root cause assessment:

1. Public Maven `litertlm-android:0.10.0` is not the same native stack as Gallery SM8750. It changes `liblitertlm_jni.so`, but still does not match Gallery's Build ID and does not package Gallery's `libLiteRt.so`.
2. The exact Gallery dispatch runtime alone remains insufficient. The dispatch Build ID matches Gallery, but the paired LiteRT-LM native payload does not.
3. If the abort reason is still `No usable Dispatch runtime found`, the best explanation remains dispatch/runtime capability negotiation mismatch, not Java/Kotlin API wiring.
4. A same-generation runtime stack is still required before judging the Qualcomm SM8750 model itself.

Updated next actions:

- Do not enable NPU inference from this state.
- Do not copy Gallery `libLiteRt.so` or QNN libraries into standard/debug.
- If testing a matched Gallery-style stack, create a more isolated debug flavor or separate app id and compare the entire native payload as a set.
- If building dispatch from source, first identify the exact LiteRT/LiteRT-LM generation that produced the desired `liblitertlm_jni.so`; public HEAD remains risky.

## Independent build decision after Gallery stack classification

Gallery SM8750 classification artifact:

- `artifacts/gallery_native_stack_plan/20260516_183915/`
- plan doc: `docs/litert_gallery_native_stack_experiment_plan.md`

The Gallery SM8750 APK contains a small, distinct native stack:

- required candidates: `liblitertlm_jni.so`, `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`
- QNN runtime candidates: `libQnnSystem.so`, `libQnnHtp.so`
- HTP candidates: `libQnnHtpV79Skel.so`, `libQnnHtpV79Stub.so`
- no `libLiteRtRuntimeCApi.so` in `lib/arm64-v8a`
- no `libQnnHtpPrepare.so` in the APK arm64 payload

This reinforces that independent `dispatch_api_so` builds are still premature. There are still three safer steps before source builds:

1. Create a fully isolated `galleryStackExperimentDebug` flavor with a separate app id.
2. Stage Gallery SM8750 native libraries only inside that flavor and verify package/nativeLibraryDir detection without inference.
3. Run the existing guarded probes in order: `Backend.NPU(String)` instantiate, `EngineConfig` dry-build, then explicit opt-in `Engine.initialize` dry-run.

Only if that isolated Gallery stack still reports `No usable Dispatch runtime found` should the next investigation move to exact source/tag identification or upstream reporting. Public HEAD standalone dispatch builds remain non-recommended because the failure mode is consistent with runtime generation/capability mismatch, not simply a missing `.so` file.

## galleryStackExperimentDebug app-id specific crash result

Collection date: 2026-05-16

Artifact:

- `artifacts/npu_diagnostics/20260516_195739_gallerynpu/`

The collector was run with:

```bash
bash scripts/collect_npu_tombstone_diagnostics.sh \
  --app-id io.github.ninbyo02.lami.gallerynpu \
  --label gallerynpu
```

The selected tombstone matches the exact app id:

```text
Cmdline: io.github.ninbyo02.lami.gallerynpu
tombstone selection: latest-tombstone-matches-app
```

This is a different crash signature from the earlier `npuExperimentDebug` Maven/native-stack mismatch.

Observed state:

```text
final stage: Engine.initialize invoking method=Engine.initialize(): void
process alive after probe: not-running
signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0000000000ffffe0
abort message: not-found
likely abort/register/log text: not-found
```

Backtrace summary:

```text
#00 libc.so (__strlen_aarch64+240)
#01 libart.so art::JavaVMExt::JniAbort
#05 libart.so CheckJNI::GetStringCharsInternal
#06 liblitertlm_jni.so Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+816
#13 base.apk com.google.ai.edge.litertlm.Engine.initialize
#25 base.apk AcceleratorProbe.invokeEngineInitializeOperation
```

Native library state in `io.github.ninbyo02.lami.gallerynpu`:

| Library | Present | Build ID |
| --- | --- | --- |
| `liblitertlm_jni.so` | true | `76e4dccd9c5f9cba468d9cae7becfec0` |
| `libLiteRt.so` | true | `869121bd7f4b0b77fa581218117a5c14` |
| `libLiteRtDispatch_Qualcomm.so` | true | `643ad77b8ac2f54bd1b61e4133c77b3a` |
| `libQnnSystem.so` | true | `0d409cdd664b8b0a` |
| `libQnnHtp.so` | true | `f2c90c1775a109e1` |
| `libQnnHtpPrepare.so` | true | `9ae62cf17f972404` |
| `libQnnHtpV79Stub.so` | true | `10d7ad6f9195411a` |
| `libQnnHtpV79Skel.so` | true | no GNU Build ID |
| `libLiteRtRuntimeCApi.so` | false | missing |

The tombstone mapping excerpt shows `liblitertlm_jni.so` and `libllm_inference_engine_jni.so`. It does not show mapped `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, or QNN libraries in the extracted top crash block. The file-level metadata still confirms those libraries are present in the isolated app nativeLibraryDir.

Classification:

- `unknown-native-abort`
- confidence: `medium`

Reasoning:

- Gallery stack native libraries are present in the isolated app nativeLibraryDir.
- The latest app-id matched tombstone does not show `No usable Dispatch runtime found`, `Failed to initialize Dispatch API`, or `insufficient capabilities`.
- The top failure path is ART CheckJNI `GetStringCharsInternal` called from `LiteRtLmJni_nativeCreateEngine+816`.
- This points to a Java/Kotlin API or JNI argument-generation mismatch between the public Maven classes used by Lami and the Gallery SM8750 `liblitertlm_jni.so`, rather than a proven dispatch runtime capability failure.

Updated recommendation:

1. Do not build `dispatch_api_so` yet.
2. Do not add more native libraries or change standard/npuExperiment.
3. Identify the exact Gallery Java/Kotlin source or AAR generation that matches Gallery `liblitertlm_jni.so` Build ID `76e4dccd9c5f9cba468d9cae7becfec0`.
4. If another experiment is needed, isolate it under a separate app id and verify `EngineConfig` constructor/JNI argument layout before calling `Engine.initialize`.
