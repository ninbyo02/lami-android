# QAIRT 2.44 QNN Provider Trace Result

Date: 2026-05-22

Scope: `customBuildExperimentDebug` explicit `Engine.initialize` dry-run only.
No `Conversation`, `Session`, `generateResponse`, selected-path NPU routing,
normal UI NPU wiring, single-token smoke, or NPU inference was executed.

## Build

Artifact:

```text
artifacts/qairt244_qnn_provider_trace_build/20260522_212620/
```

Build IDs:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `731b74da505bef341a184b3778d0412d` | `3ba100245ed79d45abf3c34230aee77d6aabd0b6c302a1ce8dd060b95575e7ec` |
| `libLiteRtDispatch_Qualcomm.so` | `042452227c659a546d4008455d231580` | `1491a945fff9858861c5c75fa071a111dcd9870a82d92b9801c59dc7b2e9ebe8` |
| `liblitertlm_jni.so` | `8554bcd057031088ad9bb2100f1f8f94` | `462d69fbb71a7bb5e2aa74562959885e7d4f647fc92f4725e726039bbae57474` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `e566cda2e3179428c73cdd5e33c5d702` | `3b25d9739c998b294fb92e7406edcf49ec0cc7f148fb5d67f2e9da32ab2f6583` |

`readelf -d` confirmed that `libLiteRtDispatch_Qualcomm.so` still has:

```text
NEEDED Shared library: [libLiteRt.so]
```

The provider trace marker `qairt244_qnn_provider_trace_v1` is present in the
rebuilt dispatch library.

## Dry-Run

Artifacts:

```text
artifacts/qairt244_qnn_provider_trace_dry_run/20260522_212949/
artifacts/npu_diagnostics/20260522_212949_customnpu/
```

Result:

- dispatch `dlopen` succeeded
- `LiteRtDispatchGetApi` dlsym succeeded
- dispatch API version `0.1.0` was accepted
- `libQnnSystem.so` dlopen succeeded
- `QnnSystemInterface_getProviders` dlsym succeeded
- `QnnSystemInterface_getProviders` returned `qnn_status=0`
- provider count was `1`
- selected system provider:
  - name: `SYSTEM_QTI_AISW`
  - backend ID: `0`
  - system API version: `1.4.0`
- LiteRT expected QNN System API minimum was `1.8.0`
- `ResolveSystemApi` returned `kLiteRtStatusErrorDynamicLoading(502)` with
  `reason=system_minor actual=4 expected_min=8`
- `QnnManager::Init` returned `kLiteRtStatusErrorDynamicLoading(502)` because
  `ResolveSystemApi` failed
- `libQnnHtp.so` was not reached
- `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and
  `libQnnHtpV79Skel.so` were not reached
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached
- `Engine.initialize` did not return
- process aborted at `DispatchDelegate::CreateDelegateKernelInterface`

Key trace excerpt:

```text
system_provider[0] ptr=... name=SYSTEM_QTI_AISW backend_id=0 system=1.4.0
ResolveSystemApi provider0 selected ... system=1.4.0 expected_system=1.8.0
ResolveSystemApi returning kLiteRtStatusErrorDynamicLoading reason=system_minor actual=4 expected_min=8
QnnManager::Init returning status=kLiteRtStatusErrorDynamicLoading(502) reason=ResolveSystemApi
```

## Mapped Libraries

Mapped in tombstone:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libQnnSystem.so`
- `libllm_inference_engine_jni.so`

Not mapped before abort:

- `libQnnHtp.so`
- `libQnnHtpPrepare.so`
- `libQnnHtpV79Stub.so`
- `libQnnHtpV79Skel.so`

## Static Dependency Cross-Check

Static dependency artifacts:

```text
artifacts/qairt244_qnn_dependency_analysis/20260522_212110/
artifacts/qairt244_qnn_dependency_analysis/20260522_212949/
```

Docs:

```text
docs/litert_qairt244_qnn_dependency_chain.md
```

Key static findings:

- final custom APK contains all target QNN/HTP files
- `libQnnHtp.so` does not statically `DT_NEEDED`
  `libQnnHtpPrepare.so`, V79 stub, or V79 skel
- `libQnnHtpV79Stub.so` has `DT_NEEDED [libcdsprpc.so]`, which is external to
  the APK
- the executed custom APK's `libQnnSystem.so` Build ID is
  `94d63184c6b1f968`
- QAIRT 2.44 SDK and Gallery `libQnnSystem.so` Build ID is
  `0d409cdd664b8b0a`

## Classification

The immediate failure is now a QNN System API generation mismatch:

```text
actual system API: 1.4.0
expected minimum: 1.8.0
```

This occurs before HTP backend loading, before V79 stub/skel loading, and before
dispatch runtime compatibility checking.

## Next Step

Use the QAIRT 2.44 SDK/Gallery-matching QNN runtime set as the next isolated
customBuildExperimentDebug diagnostic baseline, or rebuild/stage the custom
stack so `libQnnSystem.so` and companion QNN/HTP libraries are generation
consistent with the dispatch headers that require QNN System API `1.8.0`.
