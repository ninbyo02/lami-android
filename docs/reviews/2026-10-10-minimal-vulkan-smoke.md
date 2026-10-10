# Minimal Vulkan Android smoke (2026-10-10)

Device: NX733J/SM8750, Android 16. Minimal ExecuTorch 1.4 Vulkan Add+ReLU model: input shape 1x4x8x8, 1,744 bytes, SHA-256 `7634310c67114768c623ab2ebd6a40ed3d21e901eefefce845a6716619c84551`.

Debug-only decoder-process probe, with original APK and voice manifest backed up before install. One successful device invocation reported `metric=minimal_vulkan load_ms=75 forward_ms=5 sum=768.0 pid=28027`, followed by `status=complete`. Host reference sum was 768.0. This establishes basic Vulkan runtime functionality only, not PCM decoder performance, stability over repeated trials, or audio quality.

Original APK and voice manifest were restored and their SHA-256 hashes verified identical. Probe model removed. Local raw evidence and original APK backup reside under `/home/sato/project/lami-android-voice-dataset/device/minimal-vulkan-20261010-070424/` and are intentionally not committed.

Next: isolate mixed Vulkan/XNNPACK initialization and narrow the full fixed16 PCM decoder loadMethod SIGABRT. Keep all Vulkan paths debug-only and default TTS unchanged.
