# QAIRT 2.44 JNI Sentinel Logcat Result

Date: 2026-05-21

## Change

Added an Android-native sentinel at the LiteRT-LM JNI engine creation entry:

```text
/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc
Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine
```

The sentinel uses:

```text
__android_log_vprint(ANDROID_LOG_ERROR, "QAIRT244_SENTINEL", ...)
```

It also writes the same message to `stderr` and flushes. The marker string is:

```text
qairt244_jni_entry_v1
```

The first log is emitted before any `GetStringUTFChars` call. It only reports
pointer-null state and primitive values, avoiding jstring content reads at the
entry boundary.

Additional sentinels were added before:

- `model_path` `GetStringUTFChars`
- `ModelAssets::Create`
- `backend` `GetStringUTFChars`
- `EngineSettings::CreateDefault` failure
- `EngineFactory::CreateDefault`
- `EngineFactory::CreateDefault` failure/success

## Build

Artifact:

```text
artifacts/qairt244_jni_sentinel_build/20260521_214511/
```

Built library metadata:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `2ab5deef60fa7b8ce78a5e4f4aae5d82` | `1abbc4d2a61b8631af6d9ba8bb6ef9ac5e0fef75fa2e608e6fd13a0b9768944d` |
| `libLiteRtDispatch_Qualcomm.so` | `e249453cf79d19c37af2b2019fea71f1` | `ec12f96959b543782d906afc5cc2caa888dc3b29ea2403ff175088d88acdf093` |
| `liblitertlm_jni.so` | `8faff14dc850b7fb1986a300ac465fa4` | `2971f268c7f8944527f4fb59a4cf9d38af2570af63f59ebbfa34b413e8fab45f` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `696d69bb8a9de9988bc5a24efec61a2e` | `22ce807533dc659c3f482f6943f2a8b7311869e0a2c61ab8629d15bcaf3d496d` |

`liblitertlm_jni.so` contains both `QAIRT244_SENTINEL` and
`qairt244_jni_entry_v1`. It also has `liblog.so` in `NEEDED`.

## Dry-Run

Executed exactly one allowed `customBuildExperimentDebug` explicit
`Engine.initialize` dry-run:

```bash
bash scripts/run_custom_build_stack_probe.sh \
  artifacts/qairt244_jni_sentinel_build/20260521_214511 \
  --engine-dry-run \
  --model-path /data/user/0/io.github.ninbyo02.lami.customnpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm
```

Diagnostics:

```text
artifacts/npu_diagnostics/20260521_215004_customnpu/
```

Curated result:

```text
artifacts/qairt244_jni_sentinel_dry_run/20260521_215004/
```

Final stage:

```text
Engine.initialize invoking method=Engine.initialize(): void
```

Result:

- `Engine.initialize` returned: no
- crash: `SIGABRT`
- abort text: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- `QAIRT244_SENTINEL`: not captured
- `qairt244_jni_entry_v1`: not captured
- `QAIRT244_DIAG`: not captured

## Tombstone

Top app frame:

```text
liblitertlm_jni.so
DispatchDelegate::CreateDelegateKernelInterface()+464
BuildId: 8faff14dc850b7fb1986a300ac465fa4
```

The same tombstone includes:

```text
Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1992
BuildId: 8faff14dc850b7fb1986a300ac465fa4
```

Mapped-library result remains:

| Library | Tombstone mapping |
| --- | --- |
| `liblitertlm_jni.so` | mapped |
| `libLiteRt.so` | not mapped |
| `libLiteRtDispatch_Qualcomm.so` | not mapped |
| `libQnnSystem.so` | not mapped |
| `libQnnHtp.so` | not mapped |
| `libQnnHtpPrepare.so` | not mapped |
| `libQnnHtpV79Stub.so` | not mapped |
| `libQnnHtpV79Skel.so` | not mapped |

## Classification

The sentinel absence no longer means the JNI entry was not reached. The
tombstone proves the sentinel build was installed and execution reached the
rebuilt `nativeCreateEngine` frame. The current blocker is native log visibility
or capture, not a missing JNI entry.

Next action: prove native logcat capture with a minimal app-owned JNI sentinel
that does not touch LiteRT, dispatch, QNN, NPU, `Engine.initialize`,
`Conversation`, `Session`, or generation. After native log visibility is
confirmed, return to the dispatch/QNN logging boundary.
