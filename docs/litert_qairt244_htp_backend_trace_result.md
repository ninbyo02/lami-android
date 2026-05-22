# QAIRT 2.44 HTP Backend Trace Result

Date: 2026-05-22

Scope: `customBuildExperimentDebug` explicit `Engine.initialize` dry-run only.
No `Conversation`, `Session`, `generateResponse`, selected-path NPU routing,
normal UI NPU wiring, single-token smoke, or NPU inference was executed.

## Build

Artifacts:

```text
artifacts/qairt244_htp_backend_trace_build/20260522_221916/
artifacts/qairt244_htp_backend_trace_aligned_build/20260522_222215/
```

The build keeps the already required dispatch `DT_NEEDED [libLiteRt.so]` edge,
keeps the QAIRT 2.44 QNN runtime alignment, and adds file-backed diagnostics
with marker:

```text
qairt244_htp_backend_trace_v1
```

Trace locations were added in the LiteRT Qualcomm backend sources:

- `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/core/backends/qnn_backend.cc`
- `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/core/backends/htp_backend.cc`

The added logging records QNN log/backend/device handle creation, HTP platform
info, selected SoC info, HTP device config pointers/counts, and returned QNN
status/error codes. It does not change control flow or convert failures into
success.

## Build IDs

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `731b74da505bef341a184b3778d0412d` | `3ba100245ed79d45abf3c34230aee77d6aabd0b6c302a1ce8dd060b95575e7ec` |
| `libLiteRtDispatch_Qualcomm.so` | `0c2d0cd5de405e586e0373c39a3e8d11` | `f67a83fe659992bdbaf843f376e9ad46b528f5aa9c29f1edfebd7c8d5e4363bb` |
| `liblitertlm_jni.so` | `8554bcd057031088ad9bb2100f1f8f94` | `462d69fbb71a7bb5e2aa74562959885e7d4f647fc92f4725e726039bbae57474` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `8ec93d51e66b86f4aea720c486fdd459` | `d63f97886e415e7bb2061d70b6dea604cfc81a52213c1e285159f6814201ea3a` |
| `libQnnSystem.so` | `0d409cdd664b8b0a` | `7e69258e1278cc9b2bb62dbc6e2a52c227a100d6505a13fd6324a87993d0bba8` |
| `libQnnHtp.so` | `f2c90c1775a109e1` | `090e993822564851eab1405aff171643b21e644e3f696c95c96f2732aaed813a` |
| `libQnnHtpPrepare.so` | `edb612e67d6d27c2` | `09b1c15c62b6875af49ffd3d841961c098b85c367f584fee370f986c62511298` |
| `libQnnHtpV79Stub.so` | `10d7ad6f9195411a` | `005bd3de462851ce3dde55260d7d8560d6d07dbc309f554780b1f6412e6d9df1` |
| `libQnnHtpV79Skel.so` | none | `41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98` |

## Dry-Run

Artifacts:

```text
artifacts/qairt244_htp_backend_trace_dry_run/20260522_222434/
artifacts/npu_diagnostics/20260522_222434_customnpu/
```

Result:

- QNN System API remained aligned at `1.8.0`
- `ResolveSystemApi` returned OK
- `libQnnHtp.so` dlopen succeeded
- HTP provider `HTP_QTI_AISW` was selected
- HTP provider reported core `2.33.0` and backend `5.44.0`
- `ResolveApi` returned OK
- `HtpBackend::Init` was reached
- `QnnLog_create` was skipped because log level was off
- `QnnBackend_create` succeeded and returned handle `0x1`
- `QnnDevice_getPlatformInfo` succeeded and reported one hardware device
- runtime SoC detection selected `SM8750`
- selected DSP architecture was `79`
- selected VTCM was `8` MB
- `QnnDevice_create` failed
- raw `QnnDevice_create` status was `14001`
- `QNN_GET_ERROR_CODE(QnnDevice_create)` was also `14001`
- `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and
  `libQnnHtpV79Skel.so` were not mapped before abort
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached
- `Engine.initialize` did not return

Key trace:

```text
HtpBackend::Init ENTRY ... htp_performance_mode=2 soc_info_present=0
QnnBackend::CreateBackendHandle QnnBackend_create success handle=0x1
HtpBackend::CreateDevicePlatformInfo success ... num_hw_devices=1
HtpBackend::Init online soc_model_raw=69 found=1 selected=SM8750 model=69 dsp_arch=79 vtcm_mb=8
HtpBackend::Init before CreateDeviceHandle device_config_count=2 custom_count=1 platform_count=0 null_terminated=1
QnnBackend::CreateDeviceHandle QnnDevice_create failed status=14001 error_code=14001 handle=0x0
HtpBackend::Init CreateDeviceHandle failed selected_soc=SM8750 model=69 dsp_arch=79
```

## Mapped Libraries

Mapped in tombstone:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libQnnSystem.so`
- `libQnnHtp.so`
- `libllm_inference_engine_jni.so`

Present in APK but not mapped before abort:

- `libQnnHtpPrepare.so`
- `libQnnHtpV79Stub.so`
- `libQnnHtpV79Skel.so`

## Classification

The current boundary is no longer dispatch `dlopen`, QNN System provider
selection, HTP provider selection, or backend API resolution. It is the first
HTP device creation call:

```text
HtpBackend::Init -> QnnDevice_create -> status=14001
```

This happens before the process visibly loads the prepare/stub/skel chain and
before `LiteRtDispatchCheckRuntimeCompatibility`.

## Next Step

Do not shuffle app libraries as the next move. The immediate evidence points to
`QnnDevice_create` inputs or device/transport policy. The next single experiment
should enable or capture QNN backend log callback output and identify QNN
status `14001` for QAIRT 2.44 HTP. If that maps to transport or domain setup,
then test the minimal customBuildExperimentDebug-only HTP/FastRPC setting such
as unsigned-PD or skel path handling.
