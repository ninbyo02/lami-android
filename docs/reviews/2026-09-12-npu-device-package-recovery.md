# NPU failure after animation benchmarking: packaging incident

## Cause

The assistant left an explicit non-NPU UI smoke APK installed under the normal `io.github.ninbyo02.lami` application ID after animation measurements. That was an operational error. The smoke build used `-Plami.allowMissingQairt244Jni=true` in an isolated worktree without the untracked native staging files. This flag is intentionally supported for CI/UI work; it does not produce an NPU-ready daily-use APK.

The installed APK SHA-256 (`f0ef8fd38fcd15b2f2ac47e16a61a0fdb5d8662aeef74211e6cd6183fbb014bb`) exactly matches the candidate Debug APK retained by the idle-timeout measurement. It lacks `libLiteRtDispatch_Qualcomm.so`, `libLiteRtCompilerPlugin_Qualcomm.so`, `libGemmaModelConstraintProvider.so`, and `liblami_qairt244_npu_jni.so`. Its stock-named `liblitertlm_jni.so` also lacks the required `qairt244_kotlin_npu_conversation_sampler_v1` marker. The LiteRT/QNN libraries that are present differ from the complete locally staged runtime. This is an incomplete/replaced NPU runtime, not merely one missing optional diagnostic library.

## Reproduction and restoration

The normal product route was exercised by the existing `NpuKotlinConversationDurabilityReceiver`, with the same explicit SM8750 model path before and after installation. It directly invokes `NpuKotlinConversationProductRoute`, whose Engine configuration explicitly requests `Backend.NPU`; this diagnostic does not run CPU/GPU fallback or write chat messages/TTS output.

Before restoration, the process died before returning the first answer. Android ApplicationExitInfo recorded `APP CRASH(NATIVE)`, status 6, at 2026-09-12 17:49:30 JST. Earlier native-crash records at 17:32–17:35 also exist. The diagnostic observer expired at its 180-second limit; this is an observer timeout after process death, not evidence that inference ran for three minutes. Logcat capture was empty and the exit trace was null, so the exact native abort site is not available.

A clean isolated worktree at merged main `a10eb872c3d43179154f8230dac7f6e121a75838` was built with all 13 existing native staging files (excluding the separate smoke-only JNI), without the missing-JNI allowance:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleStandardDebug --console=plain
```

The strict build passed in 1m 30s, including JNI-symbol and Kotlin Conversation marker gates. All 13 packaged native libraries match the previously staged files byte-for-byte. Recovery APK SHA-256: `642490ca07f3d3023e06825c73e235d5dd94319b474aa8bd45776452aa07252e`.

After installing the complete runtime:

- 18/18 product-route turns succeeded; all 18 arithmetic answers matched.
- Engine reuse: 15 turns. Conversation reuse: 13 turns.
- Brief background/foreground lifecycle notification, chat switch, background-expiry notification, and low-memory notification checks all passed. These are diagnostic lifecycle calls, not independent physical OS memory-pressure tests.
- No newer native-crash entry appeared. The final exit entry is the deliberate force-stop used to restore preferences.
- All preference files were restored byte-for-byte, screen timeout restored, and foreground returned to ChatGPT. The installed APK hash was verified after restoration. Existing models and chats were retained.

Device comparison ran 08:49:26–08:52:55 UTC (17:49:26–17:52:55 JST). The diagnostic uses the synchronous product path; full chat UI streaming, TTS, long generation, and independent QNN/HTP trace attestation were not revalidated here. Success of these short product-route turns does not imply every NPU workload is stable.

## Prevention

The animation optimization source remains in the recovery build. Future animation measurements must retain the complete native runtime when using the normal application ID, or use a separate disposable application ID. Before returning a daily-use APK to this device, verify the actual archive against the known complete staged runtime:

```bash
python3 scripts/verify_standard_device_apk.py \
  app/build/outputs/apk/standard/debug/app-standard-debug.apk \
  --native-dir app/src/customBuildExperimentDebug/jniLibs/arm64-v8a
```

This read-only checker rejects missing libraries, reference hash mismatches, and the missing Kotlin NPU sampler marker. It is a pre-install verification command, not a universal hook around arbitrary `adb install`. It intentionally does not disable CI's non-NPU smoke build support. Packaging validation does not replace on-device inference validation.

The actual faulty APK was rejected (exit 1); the recovered APK passed (exit 0). No application source changes or inference-policy changes were needed. Raw APKs, private preference backups and device logs remain outside git; the accompanying evidence JSON includes hashes, packaging errors, and synthetic diagnostic results.
