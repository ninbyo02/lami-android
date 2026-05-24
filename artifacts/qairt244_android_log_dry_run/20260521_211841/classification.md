# QAIRT244 Android Log Dry-Run Classification

- build artifact: `artifacts/qairt244_android_log_build/20260521_210911/`
- diagnostics: `artifacts/npu_diagnostics/20260521_211841_customnpu/`
- dry-run: executed once with `--engine-dry-run`
- final stage: `Engine.initialize invoking method=Engine.initialize(): void`
- process result: SIGABRT / process not running
- `QAIRT244_DIAG`: not found in collected diagnostics
- `qairt244_android_log_v1`: not found in collected diagnostics
- tombstone top app frame: `DispatchDelegate::CreateDelegateKernelInterface()+464`
- tombstone top app BuildId: `27bb6eaa5358f3c23f080cdd33023eac`
- mapped in tombstone: `liblitertlm_jni.so`, `libllm_inference_engine_jni.so`
- not mapped in tombstone: `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`,
  `libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpPrepare.so`,
  `libQnnHtpV79Stub.so`, `libQnnHtpV79Skel.so`

## Interpretation

The android-log build was present in the crash path because the tombstone top
frame uses the new `liblitertlm_jni.so` BuildId. The direct logcat marker still
does not appear, which narrows the remaining problem to one of these:

- the abort path occurs before `__android_log_print` is reached despite the
  frame being inside `CreateDelegateKernelInterface`;
- the logcat collector misses the short-lived process logs and the device log
  buffer no longer contains them after crash collection;
- the direct-log source is compiled into the object but optimized or stripped in
  a way that keeps marker strings without executing the added call;
- a second, older dispatch delegate implementation is linked into
  `liblitertlm_jni.so` and the tombstone symbol resolves to that implementation.

Because `libLiteRt.so` and `libLiteRtDispatch_Qualcomm.so` are not mapped in the
tombstone, the dry-run still fails before separately loading the dispatch/QNN
shared objects. The most useful next step is to move one direct log call to an
even earlier, already-proven path in `LiteRtCreateDispatchDelegate` or to a
minimal JNI-side native log immediately before `Engine.initialize`, then repeat
only an initialize dry-run.
