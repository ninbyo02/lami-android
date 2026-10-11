# Standard GPU recovery on NX733J

The normal `io.github.ninbyo02.lami` StandardDebug package now generates with the combined OpenCL/NPU runtime. The user confirmed uninstalling the normal package because it appeared to duplicate a diagnostic app. Reinstalled the normal package, copied the existing generic GPU and SM8750 NPU models, and restored previous model/backend preferences. Chat history was not imported. The diagnostic package was retained.

## Changes and cause boundaries

The preceding controlled comparison isolated the ClGl/OpenCL provider selection as the compiled-model invocation failure boundary. This patch stages the validated OpenCL provider while preserving all 13 pinned NPU/LiteRT components, checks their hashes before packaging and in the final APK, excludes competing arm64 providers, and enables callback routing only for the verified arm64 runtime. It does not claim a proved internal driver/ABI root cause. The missing optional preinvoke marker remains separate from model invocation failure.

The first UI qualification also exposed repeated native-library hashing and marker scanning in diagnostic construction. Cache unchanged installed-library results by path, size and modification time, bound the cache to 32 entries, and retry unavailable hash results. This avoids repeated full library reads during streaming. Other ABIs retain their existing route.

## Normal-screen device qualification

Device: NX733J / SM8750 / arm64. Generic GPU model: 2,583,085,056 bytes. NPU model: 3,016,294,400 bytes. Full sequence source: `64d500ba`; final arm64 guard source: `bd3f5ed6`. Machine-readable results and both APK hashes are adjacent in the JSON report.

| Step | GPU | NPU |
|---|---|---|
| Greeting | 11 characters / 18.377 s | 11 characters / 0.567 s |
| Long answer | 334 characters / 39.369 s | 751 characters / 11.405 s |
| Short answer | 1 character / 3.830 s | 1 character / 0.089 s |
| Stop during generation | CANCELLED, stable | CANCELLED, stable |
| Resend | 6 characters / 10.128 s | 9 characters / 0.141 s |

Times are stored generationTimeMs, not backend-only decode measurements. Automatic TTS used queued speech. Playback was active before the playback-stop check; both playback and generation stops had no active audio at 2 s and 8 s, with no later speech requests/accepts/starts in that window. GPU cancellation occurred before text arrived; NPU cancellation occurred after 91 characters. Tests restored backend settings, keyboard, screen timeout, and returned to ChatGPT.

The initial GPU greeting/long/short statistics explicitly reported GPU, callback streaming and no fallback. Post-stop resend reported successful GPU official-flow routing. The final APK including the arm64 guard passed an additional GPU greeting/TTS check (11 characters / 10.662 s); installed APK SHA-256 matched the built artifact.

## Limits and next work

This restores usable generation, not GPU performance parity with NPU. The 600-character request produced only 334 characters; requested length compliance remains unresolved. Before diagnostic caching, the isolated-package long test was still generating after the roughly 90-second polling window; that was a harness timeout, not a recorded model exception. Package/history and run conditions differed, so these runs are not a controlled cache speed benchmark.

GPU and NPU suites used the same APK but restarted the app between backend changes. Live backend switching, Release builds, sustained thermal/power measurements and other devices remain unqualified. Next investigate warm-engine reuse and the different post-cancel official-flow route, measure prefill/decode/UI timing separately, then test live GPU/NPU switching.

## Build verification

Initial full JVM suites: 1,784 StandardDebug + 1,247 StandardRelease tests, zero failures. Python manifest checks: 3 tests passed. Final focused JVM checks: 3 route tests + 3 native-file cache tests passed. Strict StandardDebug build and final packaged native hashes passed. CI smoke builds with allowMissingQairt244Jni are not the validated device APK.
