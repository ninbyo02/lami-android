# QAIRT 2.44 QNN Device Status 14001

Date: 2026-05-22

Scope: QAIRT 2.44 header/source analysis plus a customBuildExperimentDebug
diagnostic build. No `Conversation`, `Session`, `generateResponse`,
selected-path NPU routing, normal UI NPU wiring, single-token smoke, or NPU
inference was executed.

## Status Meaning

`QnnDevice_create` returned raw status `14001`, and
`QNN_GET_ERROR_CODE(status)` also returned `14001`.

QAIRT 2.44 headers define:

```text
QNN_MIN_ERROR_DEVICE = 14000
QNN_DEVICE_ERROR_INVALID_CONFIG = QNN_MIN_ERROR_DEVICE + 1
QNN_GET_ERROR_CODE(errorHandle) = errorHandle & 0xffff
```

So the observed status is:

```text
QNN_DEVICE_ERROR_INVALID_CONFIG
```

The `QnnDevice_create` API documents this as one or more configuration values
being invalid.

Evidence artifact:

```text
artifacts/qairt244_qnn_status_14001_analysis/20260522_224429/
```

## Relevant Config Surface

`QnnDevice_create` accepts a null-terminated array of `QnnDevice_Config_t`
pointers. The generic device options are:

- `QNN_DEVICE_CONFIG_OPTION_CUSTOM`
- `QNN_DEVICE_CONFIG_OPTION_PLATFORM_INFO`

For HTP, `QNN_DEVICE_CONFIG_OPTION_CUSTOM` points at
`QnnHtpDevice_CustomConfig_t`. QAIRT 2.44 HTP custom config options are:

- `QNN_HTP_DEVICE_CONFIG_OPTION_SOC`
- `QNN_HTP_DEVICE_CONFIG_OPTION_ARCH`
- `QNN_HTP_DEVICE_CONFIG_OPTION_SIGNEDPD`
- `QNN_HTP_DEVICE_CONFIG_OPTION_SECUREPD`

The current LiteRT HTP path passes only the SOC custom config:

```text
QNN_HTP_DEVICE_CONFIG_OPTION_SOC -> socModel=69
```

The previous trace confirmed that online platform info also reported
`soc_model_raw=69`, which LiteRT mapped to:

```text
SM8750, dsp_arch=79, vtcm_mb=8
```

## Backend Log Callback

The QAIRT 2.44 log callback signature is:

```cpp
typedef void (*QnnLog_Callback_t)(
    const char* fmt,
    QnnLog_Level_t level,
    uint64_t timestamp,
    va_list args);
```

`QnnLog_create(callback, maxLogLevel, &logger)` creates the logger. The callback
may be called from multiple threads, so the diagnostic callback is a
namespace-scope function and opens/appends/closes the app-private file per call.

Diagnostic marker:

```text
qairt244_htp_log_callback_v1
```

Implementation location:

```text
/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/core/backends/qnn_backend.cc
```

The diagnostic build forces a QNN backend log handle at `QNN_LOG_LEVEL_DEBUG`
for this custom stack because the observed app path had `log_level=off`, which
previously skipped `QnnLog_create` entirely. This is a diagnostic-only source
change for the rebuilt artifact and does not alter the app's standard runtime
flavors.

## Dry-Run Classification

Dry-run artifact:

```text
artifacts/qairt244_htp_log_callback_dry_run/20260522_225623/
```

The backend log did contain the more specific invalid config reason:

```text
Failed in loading stub: dlopen failed: library "libcdsprpc.so" not found:
needed by .../libQnnHtpV79Stub.so in namespace clns-9
```

QNN then reported:

```text
Transport layer setup failed: 14001
Failed to parse platform config: 14001
QnnDevice_create done. status 0x36b1
```

So `14001` is still formally `QNN_DEVICE_ERROR_INVALID_CONFIG`, but the
underlying invalid config path is the HTP V79 stub / FastRPC transport setup
failing because `libcdsprpc.so` is not resolvable in the app's Android linker
namespace.
