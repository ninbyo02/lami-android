# LiteRT-LM Qualcomm NPU Engine.initialize Crash Analysis

This note tracks the `npuExperimentDebug` Engine.initialize dry-run crash. It is
diagnostic-only: no Conversation, Session, prompt evaluation, token generation, or
normal inference wiring is involved.

## Scope

- Flavor: `npuExperimentDebug`
- Backend path under test: `EngineConfig.backend = Backend.NPU(nativeLibraryDir)`
- Entry point: `NpuExperimentProbeActivity`
- Explicit opt-in only: `run_engine_initialize_dry_run=true`
- Model: Qualcomm SM8750 `.litertlm`
- Still forbidden:
  - `Conversation` construction
  - `Session` construction
  - `generateResponse`
  - `selectedPath=npu`
  - standard flavor changes
  - replacing `libLiteRt.so`, `liblitertlm_jni.so`, or QNN libraries
  - building a new `dispatch_api_so`

## Latest Run

- Execution time: 2026-05-16 09:58 JST
- Commit: `3ac07801 Collect LiteRT NPU crash diagnostics`
- Device: Nubia Z70S Ultra / NX733J
- SoC: SM8750
- SDK: 36
- runId: `1778893060805`
- Model path:
  `/data/user/0/io.github.ninbyo02.lami.npu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm`
- Model file:
  - exists: `true`
  - length: `3016294400`
  - canRead: `true`
- Artifacts:
  - primary: `artifacts/npu_diagnostics/20260516_100056/`
  - initial dry-run auto-collection: `artifacts/npu_diagnostics/20260516_095816/`
  - explicit collector run before native-dir fallback fix: `artifacts/npu_diagnostics/20260516_095852/`

## Stage Evidence

The previous dry-run split Engine construction and initialization:

- `Backend.NPU(String)`: success
- `EngineConfig(...)`: success
- `Engine(EngineConfig)`: returned
- `Engine.initialize()`: invoked
- `Engine.initialize()`: did not return

This means the current crash is not in `Backend.NPU(String)`, not in
`EngineConfig` construction, and not in the Java/Kotlin Engine constructor. It occurs
after entering `Engine.initialize()`, inside the LiteRT-LM native engine creation path.

## Tombstone Summary

- process: `io.github.ninbyo02.lami.npu`
- tombstone: `/data/tombstones/tombstone_05`
- artifact copy: `artifacts/npu_diagnostics/20260516_100056/tombstone_latest.txt`
- timestamp: `2026-05-16 09:58:10.551900911+0900`
- ABI: `arm64`
- pid/tid: `6730 / 6730`
- signal: `signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------`
- explicit `Abort message:` line: not present in tombstone
- register ASCII fragments include:
  - `] Failed`
  - ` to crea`
  - `ch runti`
  - `me found`
  - `legate k`
  - `ernel: N`
- matching `liblitertlm_jni.so` strings:
  - `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
  - `Unsupported dispatch runtime version`
  - `Dispatch API has insufficient capabilities: %d`
  - `Failed to initialize Dispatch API: %s`

The most likely abort text is therefore:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

The message is reconstructed from tombstone register fragments plus exact strings in
`liblitertlm_jni.so`; Android did not emit an `Abort message:` line.

Top native/managed frames:

```text
#00 libc.so abort+160
#01 liblitertlm_jni.so +0x07dfd60
#02 liblitertlm_jni.so +0x07e7200
#03 liblitertlm_jni.so +0x0d88c34
#04 liblitertlm_jni.so +0x0d88698
#05 liblitertlm_jni.so +0x0d882d0
#06 liblitertlm_jni.so +0x07ee5e0
#07 liblitertlm_jni.so +0x07e714c
#08 liblitertlm_jni.so +0x0d8d3e4
#09 liblitertlm_jni.so +0x0d8e2c8
#10 liblitertlm_jni.so +0x0d823a8
#11 liblitertlm_jni.so +0x07c3318
#12 liblitertlm_jni.so +0x07bf43c
#13 liblitertlm_jni.so +0x06e3f20
#14 liblitertlm_jni.so +0x06ed594
#15 liblitertlm_jni.so +0x06ed3f4
#16 liblitertlm_jni.so +0x06e12f4
#17 liblitertlm_jni.so +0x06dece0
#18 liblitertlm_jni.so +0x04b429c
#19 liblitertlm_jni.so +0x04b0420
#20 liblitertlm_jni.so Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1652
#27 base.apk com.google.ai.edge.litertlm.Engine.initialize+0
#39 base.apk AcceleratorProbe.invokeEngineInitializeOperation+0
#44 base.apk AcceleratorProbe.probeEngineInitializeDryRunSafely+0
```

## Native Library Build IDs

Known values before the latest collection:

| Library | Build ID | Source |
| --- | --- | --- |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | Gallery SM8750 APK staged only in `npuExperimentDebug` |
| `libLiteRt.so` | `80fa0688ac32301185275c903cec97bd` | Lami dependency APK native libs |
| `liblitertlm_jni.so` | `c2c27170ba409dbd0bc01820fa738580` | Lami dependency APK native libs |
| `libQnnSystem.so` | `94d63184c6b1f968` | APK native libs |
| `libQnnHtp.so` | `e227353d86be672b` | APK native libs |
| `libQnnHtpPrepare.so` | `9ae62cf17f972404` | APK native libs |
| `libQnnHtpV79Stub.so` | `c079c75e0fd8ee92` | APK native libs |

The Gallery SM8750 APK used for the dispatch source had different LiteRT-LM native
build IDs:

| Gallery Library | Gallery Build ID |
| --- | --- |
| `libLiteRt.so` | `869121bd7f4b0b77fa581218117a5c14` |
| `liblitertlm_jni.so` | `76e4dccd9c5f9cba468d9cae7becfec0` |

That mismatch remains the primary ABI risk.

## Not Root Cause

Current evidence rules out these items:

- model file missing: previous dry-run found the SM8750 model and read its size
- dispatch runtime missing: runtime is present in `nativeLibraryDir` with the expected SHA-256
- `Backend.NPU(String)` constructor: instantiate-only probe succeeds
- `EngineConfig` wiring: config-only dry-build succeeds and `getBackend()` returns `Backend.NPU`
- Engine constructor: the separated stage log shows `Engine constructor returned`

## Suspected Root Cause

The dry-run no longer fails because the dispatch runtime is absent from
`nativeLibraryDir`; the tombstone memory map shows `libLiteRtDispatch_Qualcomm.so`
loaded with Build ID `643ad77b8ac2f54bd1b61e4133c77b3a`.

The likely failure is that LiteRT-LM can load or discover the Gallery dispatch runtime
but cannot use it as a valid dispatch delegate runtime for this Maven LiteRT-LM native
stack. The strongest current root-cause bucket is dispatch runtime usability/ABI/API
incompatibility:

1. Gallery `libLiteRtDispatch_Qualcomm.so` Build ID differs from the Gallery
   `libLiteRt.so` / `liblitertlm_jni.so` generation, while Lami uses different
   `libLiteRt.so` and `liblitertlm_jni.so` Build IDs.
2. `liblitertlm_jni.so` contains exact failure strings for `No usable Dispatch runtime
   found`, unsupported dispatch runtime version, insufficient capabilities, and
   dispatch API initialization failure.
3. The crash occurs inside `Engine.initialize()` / `nativeCreateEngine`, after
   `Backend.NPU(String)`, `EngineConfig`, and `Engine(EngineConfig)` have all succeeded.

QNN/HTP missing-file evidence is weaker in this run because the APK/native dir contains
`libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnHtpV79Skel.so`, and
`libQnnHtpV79Stub.so`. QNN runtime mismatch remains possible, but the visible abort
evidence points first to the LiteRT dispatch delegate selection/initialization layer.

## Independent Build Decision

Do not build `dispatch_api_so` yet.

If the tombstone abort message clearly indicates dispatch API layout/version mismatch,
Lami needs a dispatch runtime built from the same LiteRT generation as the Maven
LiteRT-LM AAR. Building public LiteRT HEAD independently remains risky because the
dispatch API struct layout may not match `liblitertlm_jni.so`.

The current evidence is specifically `No usable Dispatch runtime found` after the
dispatch library is present and loaded. That means the next check should determine why
the runtime is not usable: dispatch API version/layout, capability set, or LiteRT-LM
generation mismatch. A public HEAD `dispatch_api_so` build could make that worse if it
does not match the Maven `liblitertlm_jni.so` dispatch API layout.

## Next Actions

1. Look for a dispatch runtime produced by the same Google AI Edge/LiteRT-LM release as
   the Maven AAR that provides `liblitertlm_jni.so`.
2. Compare exported dispatch symbols and capability/version strings between Gallery
   dispatch and the expected LiteRT-LM AAR generation.
3. If possible, obtain symbolized `liblitertlm_jni.so` or matching source revision for
   Build ID `c2c27170ba409dbd0bc01820fa738580` to resolve frames `#01`-`#20`.
4. Only after version/capability evidence is clear, decide whether an independent
   `dispatch_api_so` build is justified.
