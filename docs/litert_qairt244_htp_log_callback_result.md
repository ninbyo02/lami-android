# QAIRT 2.44 HTP Backend Log Callback Result

Date: 2026-05-22

Scope: `customBuildExperimentDebug` explicit `Engine.initialize` dry-run only.
No `Conversation`, `Session`, `generateResponse`, selected-path NPU routing,
normal UI NPU wiring, single-token smoke, or NPU inference was executed.

## Build

Build artifact:

```text
artifacts/qairt244_htp_log_callback_build/20260522_224734/
artifacts/qairt244_htp_log_callback_aligned_build/20260522_224734/
```

This build keeps:

- dispatch `DT_NEEDED [libLiteRt.so]`
- QAIRT 2.44 QNN runtime alignment
- app-private file diagnostics
- HTP backend trace marker `qairt244_htp_backend_trace_v1`

It adds:

```text
qairt244_htp_log_callback_v1
```

Build IDs:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `731b74da505bef341a184b3778d0412d` | `3ba100245ed79d45abf3c34230aee77d6aabd0b6c302a1ce8dd060b95575e7ec` |
| `libLiteRtDispatch_Qualcomm.so` | `a1b66b12e643f15a94cb34093f9efcac` | `459ceb6e3912fa72b43363c763315b2fbf5d336e744e82e4850f33967c7bbeba` |
| `liblitertlm_jni.so` | `8554bcd057031088ad9bb2100f1f8f94` | `462d69fbb71a7bb5e2aa74562959885e7d4f647fc92f4725e726039bbae57474` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `12a6ac7197aff7045fc5f5c263b35f9f` | `299769ef90f9ee4b74b357bd867545e1f48312bb8b1d97f9d16968a2be175655` |

The callback is installed through `QnnLog_create` in
`qnn_backend.cc`. It writes backend-generated QNN log messages to:

```text
/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_native_diag.txt
```

## Result

Dry-run artifact:

```text
artifacts/qairt244_htp_log_callback_dry_run/20260522_225623/
artifacts/npu_diagnostics/20260522_225623_customnpu/
```

The dry-run was executed once with:

```text
scripts/run_custom_build_stack_probe.sh --engine-dry-run
```

Result:

- `QnnLog_create` succeeded with the diagnostic callback
- backend log callback output was captured in `qairt244_native_diag.txt`
- `QnnDevice_create` still returned `14001` / `0x36b1`
- the backend log identified the concrete failure as V79 stub dependency
  resolution:

```text
Attempting to open dynamically linked so:
  .../lib/arm64/libQnnHtpV79Stub.so using absolute filename
Attempting to open dynamically linked so:
  libQnnHtpV79Stub.so using base filename
Failed in loading stub: dlopen failed: library "libcdsprpc.so" not found:
  needed by .../libQnnHtpV79Stub.so in namespace clns-9
loadRemoteSymbols failed with err 4000
Failed to create transport for device, error: 4000
Failed to load skel, error: 4000
Transport layer setup failed: 14001
QnnDevice_create done. status 0x36b1
```

Mapped in tombstone:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libQnnSystem.so`
- `libQnnHtp.so`

Present in APK but not mapped before abort:

- `libQnnHtpPrepare.so`
- `libQnnHtpV79Stub.so`
- `libQnnHtpV79Skel.so`

## Classification

`QNN_DEVICE_ERROR_INVALID_CONFIG` is the public QNN status, but the QNN backend
log clarifies that the invalid device config path fails while loading the HTP
V79 transport/stub stack. The immediate blocker is:

```text
libQnnHtpV79Stub.so -> DT_NEEDED libcdsprpc.so
```

Android linker namespace `clns-9` cannot resolve `libcdsprpc.so` for the app
process, even though `/vendor/lib64/libcdsprpc.so` exists on the device.

`LiteRtDispatchCheckRuntimeCompatibility` was not reached, and
`Engine.initialize` did not return.

## Next Step

Run a static/package experiment before changing QNN configs: determine whether
`libcdsprpc.so` can be legally and safely provided to `customBuildExperimentDebug`
from the device/SDK-supported runtime set, or whether the app must use a
vendor-accessible linker namespace/API for FastRPC. Do not change normal
flavors or run generation.
