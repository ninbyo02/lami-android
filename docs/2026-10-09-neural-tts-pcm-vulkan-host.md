# Fixed16 PCM Vulkan feasibility (2026-10-09 JST)

## Result

ExecuTorch 1.4 fixed16 speech decoder export succeeded. The selected candidate has 30 Vulkan delegates and 13 XNNPACK delegates. The 13 convolutions left on portable CPU by Vulkan-only lowering are delegated to XNNPACK in this candidate. Remaining portable operators include reciprocal, scalar multiplication, any, indexing, and cumulative sum. Counts do not establish execution time or GPU coverage by cost.

Candidate: `speech-decoder-fixed16-vulkan.pte`, 456562820 bytes, SHA-256 `5aac6bebf8cdf35f7dd5029b74a1d9d1485b0a9113bd328b13b73438a14b0a72`.
CPU reference: fixed16 XNNPACK SHA-256 `9a458b1029bd3d515f8febfc2b6114f9a7cb1a51dffea004e82173a3124f7dff`.

## Serialization

Vulkan lowering initially failed at a Double union value containing negative infinity. Quoting non-finite floats with FlatBuffers syntax resolves the JSON parser failure without substituting finite values. An opt-in in-process serializer workaround leaves installed ExecuTorch untouched. Binary Double round-trip checked negative infinity, positive infinity, NaN, and 1.234. Model export succeeded with the workaround. This uses ExecuTorch private APIs and is version-specific.

## Android comparison

Opt-in debug AAR: `-Plami.voiceVulkan=true` selects executorch-android-vulkan 1.4.0; normal builds retain the original AAR. Its arm64 library includes both VulkanBackend and XnnpackBackend. Both initial and selected hybrid-candidate GPU debug APKs assembled successfully. Python syntax compilation and git diff whitespace checks passed.

`lami_neural_tts_gpu_decoder_probe=true` compares the same first16 codec frames against CPU4 threads in two alternating trials with one warm-up and two measured calls per backend. Both model hashes are checked. It records decoder load/forward time, maximum absolute error, RMSE, and bit equality; maximum error must remain below 0.002. Prefix numeric smoke testing does not approve listening quality or continuity of independently decoded chunks.

Device connection to `192.168.52.52:34431` failed with No route to host before backup/install/test. No phone mutation began. GPU latency and numerical accuracy are unmeasured. No speedup or real-time claim is supported yet.

Host artifacts: `/home/sato/project/lami-android-voice-dataset/qat/pcm-vulkan-20261009/`; device/build artifacts: `/home/sato/project/lami-android-voice-dataset/device/pcm-vulkan-20261009/`.

## Reproduction

Use the pinned ExecuTorch1.4 environment and Qwen3-TTS on PYTHONPATH. Export with `scripts/voice/export_dynamic_decoder.py --model MODEL --output OUTPUT --fixed-frames 16 --backend vulkan --vulkan-quote-nonfinite --vulkan-xnnpack-fallback --operator-report REPORT`. Output paths must be new.

Build with JDK21: `./gradlew :app:assembleStandardDebug -Plami.allowMissingQairt244Jni=true -Plami.voiceVulkan=true --no-daemon`. This build provides no NPU evidence. Restore and verify the original APK, manifest, WAV, and probes after eventual device testing.
