# LiteRT QAIRT 2.44 HTP Dependency Analysis

Date: 2026-05-22

Scope: Agent B static/dependency investigation for HTP prepare, V79 skel/stub,
ADSP/CDSP, and FastRPC boundaries. No `Conversation`, `Session`,
`generateResponse`, selected-path NPU routing, or NPU inference was executed.
ADB use was read-only: `getprop`, filesystem listings, and file reads where
permitted by normal shell permissions.

Artifact:

```text
artifacts/qairt244_htp_dependency_analysis/20260522_221326/
```

Inputs:

- `artifacts/qairt244_qnn_aligned_build/20260522_215238`
- `artifacts/qairt244_qnn_aligned_dry_run/20260522_215421`
- `artifacts/npu_diagnostics/20260522_215421_customnpu`

Target libraries:

- `libQnnHtp.so`
- `libQnnHtpPrepare.so`
- `libQnnHtpV79Stub.so`
- `libQnnHtpV79Skel.so`
- `libQnnSystem.so`

## Generated Evidence

- `metadata.tsv`: ELF class, machine, size, SHA-256, Build ID, SONAME, and
  `DT_NEEDED` summary for the aligned build payload and APK-extracted libs.
- `build_id_sha_compare.tsv`: aligned build vs diagnostics APK comparison.
- `needed_edges.tsv`: raw `DT_NEEDED` edges.
- `undefined_symbol_refs.tsv`: undefined symbol lines filtered for
  DSP/FastRPC/HTP/QNN terms.
- `filtered_strings.tsv` and `strings/*_filtered.txt`: filtered `strings -a`
  output for HTP/Prepare/V79/ADSP/CDSP/FastRPC/skel/vendor/system paths.
- `readelf_dynamic/`, `readelf_headers/`, `readelf_notes/`,
  `readelf_symbols/`: raw `readelf` outputs.
- `device/`: read-only ADB collection (`getprop`, `/dev`, `/vendor`,
  `/system_ext`, firmware verinfo probes).

## Binary Identity

The aligned build payload and APK-extracted diagnostics payload are byte
identical for all five target libraries.

| Library | Build ID | SHA match |
| --- | --- | --- |
| `libQnnHtp.so` | `f2c90c1775a109e1` | true |
| `libQnnHtpPrepare.so` | `edb612e67d6d27c2` | true |
| `libQnnHtpV79Stub.so` | `10d7ad6f9195411a` | true |
| `libQnnHtpV79Skel.so` | none | true |
| `libQnnSystem.so` | `0d409cdd664b8b0a` | true |

This removes "APK did not receive the aligned QAIRT 2.44 HTP set" as a likely
cause for the current dry-run boundary.

## Static NEEDED Chain

Direct `DT_NEEDED` edges:

| Library | Static dependencies |
| --- | --- |
| `libQnnSystem.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` |
| `libQnnHtp.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` |
| `libQnnHtpPrepare.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` |
| `libQnnHtpV79Stub.so` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so`, `libcdsprpc.so` |
| `libQnnHtpV79Skel.so` | `libc++.so.1`, `libc++abi.so.1` |

Reading:

- `libQnnHtp.so` does not statically require `libQnnHtpPrepare.so`, the V79
  stub, or the V79 skel. The prepare/stub/skel path is a runtime loading path.
- `libQnnHtpPrepare.so` contains `libcdsprpc.so` strings but does not have a
  static `DT_NEEDED` edge to it.
- The only arm64 APK library with a direct static FastRPC/CDSP dependency is
  `libQnnHtpV79Stub.so` via `DT_NEEDED [libcdsprpc.so]`.
- `libQnnHtpV79Skel.so` is not an Android arm64 library. It is an ELF32
  `QUALCOMM DSP6` object with DSP-side C++ runtime dependencies.

## String Evidence

`libQnnHtp.so` contains explicit runtime-loader and connection messages:

- `PrepareLibLoader Loading %s`
- `PrepareLibLoader Unloading libQnnHtpPrepare.so`
- `First connection to QNN HTP Prepare library established!`
- `First connection to QNN stub established!`
- `Failed in loading stub: %s`
- `Failed to connect to the skel for devConfig {%d,%d,%d}`
- `Failed to get skel load status : %lu`
- `Failed to retrieve skel build id: err: %d`

`libQnnHtpV79Stub.so` contains the host-side FastRPC transport surface:

- `DspTransport.openSession qnn_open failed`
- `DspTransport.createUnsignedPD failed`
- `DspTransport.createUnsignedPD unable to load unsigned PD`
- `Invalid FastRPC mode.`
- `IDspTransport: Unable to load lib`
- `fastrpc_mmap`
- `remote_handle64_open`
- `rpcmem_alloc`

`libQnnHtpV79Skel.so` contains the DSP-side skel endpoint and symbols:

- `file:///libqnn_skel.so?qnn_skel_handle_invoke&_modver=1.0`
- `qnn_skel_handle_invoke`
- `adsp`, `cdsp`, `dspqueue_*`
- `adsp_mmap_fd_getinfo`
- `lib.ver.1.0.0.libQnnHtpV79Skel.so:1.0.0`

The libraries also contain firmware/version probe paths:

- `/firmware/verinfo/ver_info.txt`
- `/vendor/firmware_mnt/verinfo/ver_info.txt`

## Undefined Symbols

The main FastRPC/DSP unresolved symbols are concentrated in V79 artifacts:

- `libQnnHtpV79Stub.so`: `dspqueue_create`, `dspqueue_export`,
  `dspqueue_close`, `dspqueue_write`, `dspqueue_read`,
  `remote_handle64_open`, `remote_handle64_close`,
  `remote_handle64_invoke`, `remote_register_dma_handle`,
  `remote_session_control`, `rpcmem_alloc`, `rpcmem_free`,
  `fastrpc_mmap`, `fastrpc_munmap`, `rpcmem_to_fd`, and weak
  `remote_handle_control` / `rpcmem_alloc2` style entries.
- `libQnnHtpV79Skel.so`: DSP-side `dspqueue_*` and
  `adsp_mmap_fd_getinfo` unresolved entries, plus DSP C++ runtime symbols
  expected to resolve in the DSP environment.

This matches the `DT_NEEDED` reading: FastRPC linkage is not visible from
`libQnnHtp.so` itself, but appears at the V79 stub/skel boundary.

## Runtime Correlation

The aligned dry-run reached the QNN System and HTP provider boundary:

- `libQnnSystem.so` loaded with Build ID `0d409cdd664b8b0a`
- System provider `SYSTEM_QTI_AISW`, System API `1.8.0`
- `libQnnHtp.so` dlopen succeeded with Build ID `f2c90c1775a109e1`
- HTP provider `HTP_QTI_AISW`, backend ID `6`, core `2.33.0`,
  backend `5.44.0`
- `ResolveSystemApi` and `ResolveApi` returned OK
- Failure: `QnnManager::Init` returned
  `kLiteRtStatusErrorRuntimeFailure(3)` with `reason=HtpBackendInit`
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached

Mapped before abort:

- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libQnnSystem.so`
- `libQnnHtp.so`
- `liblitertlm_jni.so`
- `libllm_inference_engine_jni.so`

Not mapped before abort:

- `libQnnHtpPrepare.so`
- `libQnnHtpV79Stub.so`
- `libQnnHtpV79Skel.so`

Reading: the current failure is not a static packaging absence of the five
target QNN/HTP libraries. It occurs after QNN/HTP provider discovery and before
the process visibly maps prepare/stub/skel. The next boundary is the exact
QNN HTP backend initialization call and its returned QNN status/config detail.

## Device Read-Only Findings

ADB device:

```text
192.168.52.52:42741 device
```

Identity:

- model: `NX733J`
- product device: `PQ84A02`
- board platform: `sun`
- SoC model: `SM8750`
- kernel: `6.6.92-android15-8-g3637f4904cf5-ab13944661-4k`
- `ro.build.version.release`: `16`
- `ro.build.version.sdk`: `36`
- vendor fingerprint reports Android 15 / SDK 35 vendor build:
  `nubia/PQ84A02-UN/PQ84A02:15/AQ3A.240812.002/20260120.175553:user/release-keys`

Relevant `/dev` entries were visible:

- `/dev/fastrpc-cdsp`
- `/dev/fastrpc-cdsp-secure`
- `/dev/fastrpc-adsp-secure`
- `/dev/dma_heap/qcom,cma-secure-cdsp`
- `/dev/glink_pkt_ctrl_cdsp`
- `/dev/glink_pkt_data_cdsp`
- `/dev/remoteproc-cdsp-md`
- `/dev/remoteproc-adsp-md`
- `/dev/rdbg_cdsp`
- `/dev/rdbg_adsp`

Relevant vendor files were visible by path:

- `/vendor/lib64/libadsprpc.so`
- `/vendor/lib64/libcdsprpc.so`
- `/vendor/lib64/libmdsprpc.so`
- `/vendor/lib64/libsdsprpc.so`
- `/vendor/lib64/vendor.qti.hardware.dsp-V1-ndk.so`
- `/vendor/lib64/vendor.qti.hardware.dsp@1.0.so`
- `/vendor/etc/init/vendor.qti.cdsprpc-service.rc`
- `/vendor/etc/init/vendor.qti.adsprpc-guestos-service.rc`
- `/vendor/lib/rfsa/adsp/libQnnHtpV79Skel.so`

`/vendor/firmware_mnt` and `/vendor/dsp` were permission denied from normal
ADB shell, so firmware verinfo contents and DSP firmware image details were not
confirmed in this pass.

## Assessment

1. The aligned APK contains the expected QAIRT 2.44 QNN/HTP set, and the
   diagnostics APK extraction is byte-identical to the aligned build payload.
2. The static ELF chain is intentionally shallow until V79 stub/skel. HTP
   prepare, V79 stub, and V79 skel are runtime-loaded, not direct
   `DT_NEEDED` children of `libQnnHtp.so`.
3. The only direct arm64 FastRPC dependency in the target set is
   `libQnnHtpV79Stub.so -> libcdsprpc.so`.
4. The device exposes the expected SM8750/CDSP/FastRPC surface at a basic path
   level: `libcdsprpc.so`, FastRPC device nodes, CDSP DMA heap, and RFSA ADSP
   skel location are present.
5. The dry-run aborts before prepare/stub/skel are mapped, so the immediate
   failure is more likely in HTP backend create/config/init status handling than
   in a missing static ELF dependency.
6. If the backend init internally tries to establish transport before mappings
   become visible in the tombstone, remaining risk areas are FastRPC mode,
   signed/unsigned PD policy, skel resolution path, RFSA-vs-APK skel selection,
   and vendor firmware/driver compatibility.

## Recommended Next Step

The follow-up HTP backend trace has now identified the immediate QNN call:

```text
QnnDevice_create -> status=14001
```

Do not start with app-lib shuffling: this pass shows the aligned target files
are present and identical, and the failure happens before the runtime visibly
loads the prepare/stub/skel chain. The next step should identify QAIRT/QNN
status `14001` and capture QNN backend log callback output; only then should an
HTP/FastRPC/unsigned-PD/skel-path experiment be attempted.
