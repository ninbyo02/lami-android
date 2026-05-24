# QAIRT 2.44 QNN Runtime Alignment Result

Date: 2026-05-22

Scope: `customBuildExperimentDebug` explicit `Engine.initialize` dry-run only.
No `Conversation`, `Session`, `generateResponse`, selected-path NPU routing,
normal UI NPU wiring, single-token smoke, or NPU inference was executed.

## Build

Artifact:

```text
artifacts/qairt244_qnn_aligned_build/20260522_215238/
```

This artifact keeps the QAIRT 2.44 diagnostic LiteRT/LiteRT-LM stack from the
QNN provider trace build, including `DT_NEEDED [libLiteRt.so]` on
`libLiteRtDispatch_Qualcomm.so`, and adds a `qnn_runtime_libs/` payload sourced
from QAIRT SDK `2.44.0.260225`.

| Library | Build ID | SHA-256 | Source |
| --- | --- | --- | --- |
| `libLiteRt.so` | `731b74da505bef341a184b3778d0412d` | `3ba100245ed79d45abf3c34230aee77d6aabd0b6c302a1ce8dd060b95575e7ec` | provider trace build |
| `libLiteRtDispatch_Qualcomm.so` | `042452227c659a546d4008455d231580` | `1491a945fff9858861c5c75fa071a111dcd9870a82d92b9801c59dc7b2e9ebe8` | provider trace build |
| `liblitertlm_jni.so` | `8554bcd057031088ad9bb2100f1f8f94` | `462d69fbb71a7bb5e2aa74562959885e7d4f647fc92f4725e726039bbae57474` | provider trace build |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `e566cda2e3179428c73cdd5e33c5d702` | `3b25d9739c998b294fb92e7406edcf49ec0cc7f148fb5d67f2e9da32ab2f6583` | provider trace build |
| `libQnnSystem.so` | `0d409cdd664b8b0a` | `7e69258e1278cc9b2bb62dbc6e2a52c227a100d6505a13fd6324a87993d0bba8` | QAIRT 2.44 `lib/aarch64-android` |
| `libQnnHtp.so` | `f2c90c1775a109e1` | `090e993822564851eab1405aff171643b21e644e3f696c95c96f2732aaed813a` | QAIRT 2.44 `lib/aarch64-android` |
| `libQnnHtpPrepare.so` | `edb612e67d6d27c2` | `09b1c15c62b6875af49ffd3d841961c098b85c367f584fee370f986c62511298` | QAIRT 2.44 `lib/aarch64-android` |
| `libQnnHtpV79Stub.so` | `10d7ad6f9195411a` | `005bd3de462851ce3dde55260d7d8560d6d07dbc309f554780b1f6412e6d9df1` | QAIRT 2.44 `lib/aarch64-android` |
| `libQnnHtpV79Skel.so` | none | `41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98` | QAIRT 2.44 `lib/hexagon-v79/unsigned` |

`libQnnDsp.so` and `libQnnGpu.so` were not staged in this alignment pass.

## Dry-Run

Artifacts:

```text
artifacts/qairt244_qnn_aligned_dry_run/20260522_215421/
artifacts/npu_diagnostics/20260522_215421_customnpu/
```

Result:

- `libQnnSystem.so` loaded from the aligned QAIRT 2.44 payload
- `QnnSystemInterface_getProviders` returned `qnn_status=0`
- provider count was `1`
- selected system provider was `SYSTEM_QTI_AISW`, backend ID `0`
- QNN System API version advanced from `1.4.0` to `1.8.0`
- `ResolveSystemApi` returned OK
- `libQnnHtp.so` dlopen succeeded
- `QnnInterface_getProviders` dlsym succeeded
- HTP provider count was `1`
- selected HTP provider was `HTP_QTI_AISW`, backend ID `6`
- selected HTP provider reported core `2.33.0` and backend `5.44.0`
- `ResolveApi` returned OK
- `QnnManager::Init` then returned `kLiteRtStatusErrorRuntimeFailure(3)` with
  `reason=HtpBackendInit`
- `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and
  `libQnnHtpV79Skel.so` were not mapped before abort
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached
- `Engine.initialize` did not return
- process aborted at `DispatchDelegate::CreateDelegateKernelInterface`

Key trace excerpt:

```text
system_provider[0] ... name=SYSTEM_QTI_AISW backend_id=0 system=1.8.0
ResolveSystemApi RETURN OK
backend dlopen success path=libQnnHtp.so
provider[0] ... name=HTP_QTI_AISW backend_id=6 core=2.33.0 backend=5.44.0
ResolveApi RETURN OK
QnnManager::Init returning status=kLiteRtStatusErrorRuntimeFailure(3) reason=HtpBackendInit
```

## Mapped Libraries

Mapped in tombstone:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libQnnSystem.so`
- `libQnnHtp.so`
- `libllm_inference_engine_jni.so`

Not mapped before abort:

- `libQnnHtpPrepare.so`
- `libQnnHtpV79Stub.so`
- `libQnnHtpV79Skel.so`

## Classification

The QNN runtime generation mismatch is fixed for the System provider boundary.
The current boundary is now HTP backend initialization:

```text
QnnManager::Init -> HtpBackendInit -> kLiteRtStatusErrorRuntimeFailure(3)
```

This occurs after QNN System and HTP provider API selection, but before
`LiteRtDispatchCheckRuntimeCompatibility`.

## Next Step

Add focused file logging inside the HTP backend initialization path around the
exact QNN backend init call and its returned QNN status/error details. Only
after that should ADSP/FastRPC/skel path changes be attempted.
