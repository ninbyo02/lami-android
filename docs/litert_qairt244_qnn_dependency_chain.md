# LiteRT QAIRT 2.44 QNN Dependency Chain

Date: 2026-05-22

Scope: static analysis only. No APK install, adb execution, `Conversation`,
`Session`, `generateResponse`, or NPU inference path was run.

Artifact:

```text
artifacts/qairt244_qnn_dependency_analysis/20260522_212110/
artifacts/qairt244_qnn_dependency_analysis/20260522_212949/
```

The second artifact includes the QNN provider trace dry-run APK extraction from
`artifacts/npu_diagnostics/20260522_212949_customnpu/apk_libs`.

Inputs:

- APK/merged native libs from `galleryStackExperimentDebug`,
  `customBuildExperimentDebug`, and `npuExperimentDebug`.
- QAIRT SDK 2.44 from
  `/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225`.
- Target libs:
  `libLiteRtDispatch_Qualcomm.so`, `libQnnSystem.so`, `libQnnHtp.so`,
  `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`,
  `libQnnHtpV79Skel.so`.

## Generated Files

- `metadata.tsv`: ELF class, machine, Build ID, SHA-256, source path.
- `needed_edges.tsv` / `needed_chain.tsv`: `readelf -d` `DT_NEEDED`.
- `undefined_symbols_summary.tsv`: `readelf -Ws` undefined-symbol counts.
- `apk_presence.tsv`: APK `lib/arm64-v8a` presence.
- `jniLibs_presence.tsv`: checked-in flavor `jniLibs` presence.
- `build_id_diff.tsv`: Gallery/custom/npu/SDK Build ID comparison.
- `basename_strings.tsv`: filtered `.so` basename strings.
- `path_strings_significant.tsv`: filtered absolute/path-like strings.
- `readelf_dynamic/*.txt` and `readelf_symbols/*.txt`: raw readelf output.

## APK Presence

All six target libs are present in these APKs:

- `app-galleryStackExperiment-debug.apk`
- `app-customBuildExperiment-debug.apk`
- `app-npuExperiment-debug.apk`

`app-standard-debug.apk` contains the five QNN/HTP target libs but not
`libLiteRtDispatch_Qualcomm.so`.

Checked-in `jniLibs` differs from final APK content:

- Gallery source `jniLibs` has dispatch, `QnnSystem`, `QnnHtp`, V79 stub, and
  V79 skel, but not `libQnnHtpPrepare.so`; merged/APK output does include it.
- custom/npu source `jniLibs` only has `libLiteRtDispatch_Qualcomm.so` among
  the target set; merged/APK output includes the QNN/HTP libs from other inputs.

## NEEDED Chain

Direct `DT_NEEDED` edges:

| Root | Key NEEDED result |
| --- | --- |
| `libLiteRtDispatch_Qualcomm.so` | Needs `libLiteRt.so`, `libandroid.so`, `liblog.so`, `libdl.so`, `libc.so`, `libm.so`. `libLiteRt.so` is present beside dispatch in Gallery/custom merged libs, but not beside npu dispatch. |
| `libQnnSystem.so` | Needs only Android/system libs: `libc.so`, `libm.so`, `libdl.so`, `liblog.so`. |
| `libQnnHtp.so` | Needs only Android/system libs: `libc.so`, `libm.so`, `libdl.so`, `liblog.so`. No static `DT_NEEDED` edge to `libQnnHtpPrepare.so` or V79 stub/skel. |
| `libQnnHtpPrepare.so` | Needs only Android/system libs: `libc.so`, `libm.so`, `libdl.so`, `liblog.so`. |
| `libQnnHtpV79Stub.so` | Needs Android/system libs plus `libcdsprpc.so`, which is external to the APK/native-lib directories checked here. |
| `libQnnHtpV79Skel.so` | ELF32 Qualcomm DSP6 object; needs `libc++.so.1` and `libc++abi.so.1`, external to the Android arm64 app lib set. |

Static reading: HTP prepare/stub/skel are not pulled in by ELF dependency from
`libQnnHtp.so`; they are likely runtime-loaded by QNN/HTP code. The only direct
FastRPC/CDSP dependency in `DT_NEEDED` is the V79 stub's `libcdsprpc.so`.

## Undefined Symbols

Important counts from `undefined_symbols_summary.tsv`:

| Source | Library | Relevant UND summary |
| --- | --- | --- |
| gallery/npu | `libLiteRtDispatch_Qualcomm.so` | 139 total UND, 13 LiteRT UND. |
| custom | `libLiteRtDispatch_Qualcomm.so` | 125 total UND, 9 LiteRT UND. |
| all | `libQnnHtpV79Stub.so` | 6 `cdsprpc`/FastRPC-related UND. |
| Gallery/SDK | `libQnnHtpV79Skel.so` | 358 total UND, 177 C++-style UND. |
| custom/npu | `libQnnHtpV79Skel.so` | 330 total UND, 164 C++-style UND. |

The QNN system/HTP/prepare arm64 libs have no QNN or LiteRT undefined-symbol
count in this pass; their unresolved entries are mostly C/C++/Android runtime
symbols.

## Build ID Comparison

| Library | Gallery | Custom | NPU | SDK 2.44 | Reading |
| --- | --- | --- | --- | --- | --- |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | `042452227c659a546d4008455d231580` | `643ad77b8ac2f54bd1b61e4133c77b3a` | missing | custom dispatch differs; npu uses Gallery dispatch. |
| `libQnnSystem.so` | `0d409cdd664b8b0a` | `94d63184c6b1f968` | `94d63184c6b1f968` | `0d409cdd664b8b0a` | Gallery matches SDK; custom/npu differ. |
| `libQnnHtp.so` | `f2c90c1775a109e1` | `e227353d86be672b` | `e227353d86be672b` | `f2c90c1775a109e1` | Gallery matches SDK; custom/npu differ. |
| `libQnnHtpPrepare.so` | `9ae62cf17f972404` | `9ae62cf17f972404` | `9ae62cf17f972404` | `edb612e67d6d27c2` | APKs share a prepare lib that differs from SDK 2.44. |
| `libQnnHtpV79Stub.so` | `10d7ad6f9195411a` | `c079c75e0fd8ee92` | `c079c75e0fd8ee92` | `10d7ad6f9195411a` | Gallery matches SDK; custom/npu differ. |
| `libQnnHtpV79Skel.so` | none | none | none | none | No Build ID note; SHA shows Gallery equals SDK, custom equals npu. |

SHA-256 confirms the same pattern for V79 skel:

- Gallery/SDK skel SHA:
  `41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98`
- custom/npu skel SHA:
  `5590d6b34efdaef561155b77bc734a1a1e560767c180df9aba2dbceeb7ad28d1`

## Strings Findings

Relevant basename strings:

- Dispatch contains `libQnnSystem.so`, `libQnnHtp.so`, `libQnnDsp.so`,
  `libQnnIr.so`, and `libQnnSaver.so` strings, but only `libLiteRt.so` and
  Android/system libs appear as `DT_NEEDED`.
- `libQnnHtp.so` contains `PrepareLibLoader` / `libQnnHtpPrepare.so` strings,
  consistent with dynamic prepare-library loading.
- `libQnnHtpV79Stub.so` contains `libcdsprpc.so`; it also has
  `DT_NEEDED [libcdsprpc.so]`.
- SDK `libQnnHtpPrepare.so` contains `libUbwcD.so` and `libcdsprpc.so` strings,
  but these are not `DT_NEEDED` edges in this pass.

Relevant path-like strings:

- Dispatch: `/data/local/tmp/dumped_tensors/`.
- QNN System/HTP: `/firmware/verinfo/ver_info.txt`,
  `/vendor/firmware_mnt/verinfo/ver_info.txt`, and SoC probe paths under
  `/sys/devices/...`.
- V79 skel: `file:///libqnn_skel.so?qnn_skel_handle_invoke&_modver=1.0`.
- Many QAISW build/source paths are embedded, especially in
  `libQnnHtpPrepare.so` and V79 skel.

## Assessment

The APKs are not missing the target QNN/HTP files. The static dependency risk is
instead a mixed-generation runtime and dynamic-loader boundary:

1. Gallery APK output combines Gallery/SDK-matching `QnnSystem`, `QnnHtp`, and
   V79 stub/skel with a `libQnnHtpPrepare.so` that differs from SDK 2.44.
2. custom/npu APK output uses a different QNN System/HTP/V79 generation than
   Gallery/SDK 2.44, while still sharing the same `libQnnHtpPrepare.so` as
   Gallery.
3. `libQnnHtpV79Stub.so` depends on external `libcdsprpc.so`; no APK checked
   here packages that dependency.
4. `libQnnHtp.so` does not statically require prepare/stub/skel, so missing or
   incompatible dynamic loading would not be visible from `DT_NEEDED` alone.
5. npu merged dispatch has `DT_NEEDED [libLiteRt.so]` but no sibling arm64
   `libLiteRt.so` in the checked merged-native directory, unlike Gallery/custom.

## Runtime Correlation

The QNN provider trace dry-run selected custom APK `libQnnSystem.so` Build ID
`94d63184c6b1f968` and reported:

```text
SYSTEM_QTI_AISW system=1.4.0
expected_system=1.8.0
reason=system_minor actual=4 expected_min=8
```

This matches the static Build ID mismatch: the executed custom/npu QNN System
library differs from the QAIRT 2.44 SDK/Gallery `libQnnSystem.so`
(`0d409cdd664b8b0a`), while the rebuilt dispatch expects the newer QNN System
API.

## Runtime Alignment Correlation

The QAIRT 2.44 runtime alignment dry-run staged the SDK/Gallery matching
QNN/HTP set into only `customBuildExperimentDebug`:

```text
artifacts/qairt244_qnn_aligned_build/20260522_215238/
artifacts/qairt244_qnn_aligned_dry_run/20260522_215421/
```

Runtime result:

- `libQnnSystem.so` Build ID `0d409cdd664b8b0a` reported System API `1.8.0`
- `libQnnHtp.so` Build ID `f2c90c1775a109e1` loaded successfully
- HTP provider `HTP_QTI_AISW` reported core `2.33.0`, backend `5.44.0`
- `ResolveSystemApi` and `ResolveApi` returned OK
- the new boundary is `HtpBackendInit` returning
  `kLiteRtStatusErrorRuntimeFailure(3)`
- `libQnnHtpPrepare.so`, V79 stub, and V79 skel were present but not mapped
  before the abort

This confirms the previous QNN System generation mismatch was real and that the
next investigation should focus on the HTP backend initialization call rather
than generic packaging absence.
