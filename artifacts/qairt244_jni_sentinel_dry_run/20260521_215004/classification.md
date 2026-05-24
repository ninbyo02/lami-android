# QAIRT244 JNI Sentinel Dry-Run Classification

- build artifact: `artifacts/qairt244_jni_sentinel_build/20260521_214511/`
- diagnostics artifact: `artifacts/npu_diagnostics/20260521_215004_customnpu/`
- curated artifact: `artifacts/qairt244_jni_sentinel_dry_run/20260521_215004/`
- dry-run count in this pass: 1
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- `Engine.initialize` returned: no
- crash: `SIGABRT`
- abort text: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- `QAIRT244_SENTINEL`: not captured
- `qairt244_jni_entry_v1`: not captured
- `QAIRT244_DIAG`: not captured
- tombstone top app frame:
  `DispatchDelegate::CreateDelegateKernelInterface()+464`
- tombstone app BuildId:
  `8faff14dc850b7fb1986a300ac465fa4`
- nativeCreateEngine frame:
  `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1992`
- mapped-lib TSV caveat: the collector's `present_in_native_library_dir`
  Build ID column can reflect the wrong local APK/native extraction path. The
  tombstone BuildId above is authoritative for the active crash path.

## Classification

`QAIRT244_SENTINEL` was not captured, but the tombstone proves the new sentinel
`liblitertlm_jni.so` was installed and the process reached the rebuilt
`nativeCreateEngine` frame. This argues against "JNI entry was not reached" and
instead points to one of:

- app-native `__android_log_print` lines are not being captured by the current
  collector/logcat window,
- app-native log lines are being dropped/suppressed before collection, or
- the relevant logging path is optimized or linked in a way that does not emit
  visible logcat lines despite `liblog.so` being needed.

The next useful step is not another QNN path experiment. First make the
collector prove native log visibility with an independent app-owned JNI smoke
logger that does not initialize LiteRT or NPU, then return to dispatch logging.
