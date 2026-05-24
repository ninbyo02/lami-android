# QAIRT 2.44 Android QNN Path Analysis

Date: 2026-05-21

Scope: rootless/read-only path visibility review for the QAIRT 2.44 / SM8750 / V79 NPU dispatch failure. No app code was changed, no inference was run, and no dry-run was launched in this pass.

## Live Device Collection

`adb` was available on the workstation, but no device was connected at collection time:

- `artifacts/qairt244_failure_analysis/20260521_081545/device_qnn_props.txt`
- `artifacts/qairt244_failure_analysis/20260521_081545/android_qnn_paths.txt`

Both files record `NO_CONNECTED_ADB_DEVICE`. Therefore this pass could not confirm current rootless visibility for:

- `/dev` entries such as `adsprpc`, `cdsprpc`, `fastrpc`, or `dsp`
- `/vendor/lib64` and `/system_ext/lib64` QNN/HTP/DSP entries
- `/vendor/dsp`, `/vendor/lib/rfsa`, `/dsp`, or `/firmware`
- current `getprop` values for `qcom`, `adsp`, `cdsp`, `dsp`, `soc`, or `ro.vendor`

The path artifacts are still useful because they show the attempted commands and the absence of a connected device, instead of silently reusing stale device state.

## App Native Library Visibility

Existing diagnostics show that the app package/native-library side is not a simple missing-file case.

For the recent custom stack failure, `artifacts/npu_diagnostics/20260521_074641_customnpu/crash_summary.md` records:

- application id: `io.github.ninbyo02.lami.customnpu`
- `nativeLibraryDir`: `/data/app/.../io.github.ninbyo02.lami.customnpu-.../lib/arm64`
- `liblitertlm_jni.so`: present, mapped in tombstone
- `libLiteRt.so`: present, not mapped in tombstone
- `libLiteRtDispatch_Qualcomm.so`: present, not mapped in tombstone
- `libQnnSystem.so`: present, not mapped in tombstone
- `libQnnHtp.so`: present, not mapped in tombstone
- `libQnnHtpPrepare.so`: present, not mapped in tombstone
- `libQnnHtpV79Stub.so`: present, not mapped in tombstone
- `libQnnHtpV79Skel.so`: present, not mapped in tombstone

The same summary classifies the abort as `no-usable-dispatch-runtime`; register fragments are consistent with:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

For the Gallery-stack experiment, `artifacts/npu_diagnostics/20260516_210110_gallerynpu/crash_summary.md` records a different load profile:

- application id: `io.github.ninbyo02.lami.gallerynpu`
- `liblitertlm_jni.so`: present and mapped
- `libLiteRt.so`: present and mapped
- `libLiteRtDispatch_Qualcomm.so`: present and mapped
- `libQnnSystem.so`: present and mapped
- `libQnnHtp.so`: present and mapped
- `libQnnHtpPrepare.so`: present but not mapped
- `libQnnHtpV79Stub.so`: present but not mapped
- `libQnnHtpV79Skel.so`: present but not mapped

That means `nativeLibraryDir` packaging is visible to the Android linker, but the custom-stack failure aborts before the expected dispatch/QNN libraries appear in the tombstone map. The Gallery-stack experiment reaches farther into loading `libLiteRt`, dispatch, `libQnnSystem`, and `libQnnHtp`, but still does not show V79 stub/skel mapped in that tombstone.

## QAIRT 2.44 Skel/Stub Facts

QAIRT 2.44 files are present in local analysis artifacts:

- `artifacts/qairt244_sdk_check/20260517_230433/file_presence.tsv` found `qnn-platform-validator`, `libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and `lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so`.
- `artifacts/qairt244_rebuild_compare/20260517_230448/qairt_metadata/libQnnHtpV79Stub.so.txt` shows `libQnnHtpV79Stub.so` is an Android arm64 library and has `NEEDED` dependency `libcdsprpc.so`.
- `artifacts/qairt244_rebuild_compare/20260517_230448/qairt_metadata/libQnnHtpV79Skel.so.txt` shows `libQnnHtpV79Skel.so` is a 32-bit Qualcomm DSP6 shared object with SONAME `libQnnHtpV79Skel.so`.

The `libcdsprpc.so` dependency is the main static FastRPC/CDSP signal for the V79 stub path. The skel is not an arm64 app library in the normal sense; it is DSP-side payload, so app-process visibility is likely mediated by QNN/HTP loader behavior, CDSP/FastRPC availability, and any QNN skel search path rules.

## FastRPC / ADSP Evidence

Existing log/tombstone artifacts contain limited FastRPC evidence:

- Multiple tombstones include `/dev/__properties__/u:object_r:vendor_adsprpc_prop:s0`.
- The V79 stub metadata and native library summaries consistently show `libQnnHtpV79Stub.so` needs `libcdsprpc.so`.
- The Gallery APK metadata investigation notes `libcdsprpc.so` as a not-required manifest native library in the upstream Gallery APK.

No reviewed logcat artifact exposed a clear root-cause line such as a linker failure, `dlopen` failure, `ADSP_LIBRARY_PATH` failure, missing `/dev/*rpc*`, or skel load error. That absence is not proof that ADSP/CDSP is healthy; it only means the collected app logs did not surface that error.

## Validator vs App Failure

`docs/litert_qnn_qairt_coupling_investigation_plan.md` records prior device context:

- device: `NX733J`
- SoC: `SM8750`
- DSP core: Hexagon Architecture V79
- external `qnn-net-run`: available
- external `qnn-platform-validator`: passed

That external pass proves the device can expose QNN/HTP capability to the QAIRT tool environment. It does not prove the Android app process can load the same runtime generation or find DSP skel/stub assets through `Backend.NPU(nativeLibraryDir)`.

The current app failure is therefore best summarized as:

- hardware capability: likely present, based on prior external QAIRT validation
- app packaged files: present in `nativeLibraryDir` / APK
- app runtime: aborts at dispatch delegate creation with `No usable Dispatch runtime found`
- path uncertainty: current `/dev`, vendor lib, RFSA, DSP, and property state could not be refreshed because no adb device was connected

## Current Assessment

The strongest finding is that the Android app failure is not explained by simply omitting `libLiteRtDispatch_Qualcomm.so`, QNN system/HTP libs, or V79 stub/skel from the APK. Those files are present in the app-native payloads captured by diagnostics.

The open path-specific risks are:

1. `libcdsprpc.so` / CDSP FastRPC availability differs between the external QAIRT shell/tool environment and the Android app process.
2. QNN HTP skel discovery may require a search path or packaging convention not satisfied by `nativeLibraryDir` alone.
3. The app reaches a dispatch compatibility/capability gate before V79 stub/skel loading, so skel path issues may be secondary to LiteRT/dispatch/QNN generation mismatch.
4. Gallery and custom stacks load different native subsets before abort, suggesting runtime-generation compatibility remains at least as plausible as an ADSP path issue.

Next useful read-only step when the device is connected: rerun the captured rootless adb collection and compare actual `/dev/*rpc*`, `/vendor/lib64/libcdsprpc.so`, `/system_ext/lib64`, RFSA/DSP directory visibility, and `ro.vendor`/qcom/adsp/cdsp properties against the app tombstone load profile.
