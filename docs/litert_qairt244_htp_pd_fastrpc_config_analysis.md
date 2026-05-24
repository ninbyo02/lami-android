# QAIRT 2.44 HTP PD / FastRPC / Config Analysis

Date: 2026-05-22

Scope: static QAIRT 2.44 header/strings analysis plus rootless read-only ADB
collection. No `Conversation`, `Session`, `generateResponse`, selected-path
NPU routing, normal UI NPU wiring, single-token smoke, or NPU inference was
executed.

Artifact:

```text
artifacts/qairt244_htp_pd_fastrpc_analysis/20260522_224429/
```

## Header Findings

`QnnDevice_create` is failing with:

```text
QNN_DEVICE_ERROR_INVALID_CONFIG
```

For HTP, the relevant custom device config is
`QnnHtpDevice_CustomConfig_t`, carried through generic
`QnnDevice_Config_t` as `QNN_DEVICE_CONFIG_OPTION_CUSTOM`.

QAIRT 2.44 HTP device custom options:

| Option | Meaning |
| --- | --- |
| `QNN_HTP_DEVICE_CONFIG_OPTION_SOC` | sets `socModel` |
| `QNN_HTP_DEVICE_CONFIG_OPTION_ARCH` | sets minimum HTP arch for a device |
| `QNN_HTP_DEVICE_CONFIG_OPTION_SIGNEDPD` | enables/disables signed process domain |
| `QNN_HTP_DEVICE_CONFIG_OPTION_SECUREPD` | enables SecurePD; header says SecurePD is V81/add-on SDK only |

The current LiteRT path only supplies the SOC custom config. It does not supply
explicit ARCH or SIGNEDPD config before `QnnDevice_create`.

The QNN TFLite delegate HTP options expose `pd_session`, with documented
default:

```text
kHtpUnsignedPd
```

This suggests an isolated unsigned-PD config experiment is plausible if backend
logs confirm process-domain setup is the invalid part of the config.

## Runtime Strings

`libQnnHtpV79Stub.so` contains FastRPC and unsigned-PD messages, including:

```text
DspTransport.openSession qnn_open failed
DspTransport.createUnsignedPD unable to load unsigned PD
DspTransport.createUnsignedPD failed
createUnsignedPD unsigned PD or DSPRPC_GET_DSP_INFO not supported by HTP
createUnsignedPD unsigned PD interface not supported
Using fastrpc for execution
Effective cdsp_id is: ...
libcdsprpc.so
```

This confirms that the V79 stub is where FastRPC/CDSP and unsigned-PD transport
become concrete. In the latest dry-run, however, `libQnnHtpV79Stub.so` was not
yet mapped, so these strings are a next-boundary risk rather than the observed
call site.

## Device Read-Only Findings

Connected device:

```text
192.168.52.52:42741
```

Visible FastRPC/CDSP nodes include:

```text
/dev/fastrpc-cdsp
/dev/fastrpc-cdsp-secure
/dev/fastrpc-adsp-secure
/dev/remoteproc-cdsp-md
/dev/remoteproc-adsp-md
/dev/rdbg_cdsp
/dev/rdbg_adsp
```

Vendor FastRPC library:

```text
/vendor/lib64/libcdsprpc.so
```

The RFSA skel path probe:

```text
/vendor/lib/rfsa/adsp/libQnnHtpV79Skel.so
```

returned `Permission denied` from normal shell, matching the earlier finding
that some vendor DSP/RFSA paths are not inspectable without elevated
permissions.

## Minimal Experiment Design

The QNN backend log callback dry-run identified a stub dependency resolution
failure:

```text
libQnnHtpV79Stub.so needs libcdsprpc.so, but libcdsprpc.so is not found in
Android linker namespace clns-9
```

This deprioritizes SOC/ARCH/PD config experiments as the immediate next move.
The next experiment should focus on the FastRPC host library visibility
boundary:

1. Statistically compare the device `/vendor/lib64/libcdsprpc.so` identity and
   ABI against QAIRT expectations.
2. Determine whether `libcdsprpc.so` can be supplied safely in
   `customBuildExperimentDebug` without app/src/main changes, or whether
   Android namespace policy requires using the vendor copy through a supported
   namespace.
3. Only after `libcdsprpc.so` resolves should unsigned-PD or skel path config
   be tested, if the backend log then moves to `createUnsignedPD` or skel load
   errors.

All of these remain initialize-only experiments. They must not create
`Conversation` or `Session`, must not call `generateResponse`, and must not
wire NPU into normal UI inference.
