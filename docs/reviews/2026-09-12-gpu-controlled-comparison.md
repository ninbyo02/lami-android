# Controlled GPU native-stack comparison — 2026-09-12

## Conclusion

Under identical app code, model content, explicit backend/modalities, sampler and send API, StandardDebug's native-library set fails GPU invocation while the installed GPU candidate's complete native set produces a correct Japanese greeting. The successful set also works at total context 1024, with null cache or a fresh dedicated cache. This narrows the reported short-prompt failure to the native-stack boundary; it does not identify a single defective library or qualify long output.

## Isolation and evidence

An isolated package `io.github.ninbyo02.lami.gpucontrolled` was built from PR #2597 code with debug receiver context/cache overrides. Its standard APK and repackaged GPU candidate APK have byte-identical non-native ZIP entry contents, including DEX, manifest and resources; only arm64 native contents and APK signing/container metadata differ. Both signed APKs passed apksigner verification. Native set and artifact hashes are in the accompanying manifest.

The actual current user model (2,583,085,056 bytes, SHA-256 ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42) was copied into the new diagnostic package. Each condition used a fresh process and separate directory/model copy to avoid prior adjacent compilation caches. Model setup identity was verified. Prompt: こんにちは. All cases used Backend.GPU, null vision/audio, topK=64, topP=0.95, temperature=1.0 and typed Contents callback API.

| Native set | Actual context | Cache argument | Result | First nonempty callback |
| --- | ---: | --- | --- | ---: |
| Current standard | 4096 | null | Failure: status 13, compiled-model invoke, zero text callbacks | unavailable |
| GPU candidate | 4096 | null | Success, 12 text callbacks, onDone=1, onError=0 | 232 ms |
| GPU candidate | 1024 | null | Success, 12 text callbacks, onDone=1, onError=0 | 123 ms |
| GPU candidate | 1024 | new dedicated directory | Success, 12 text callbacks, onDone=1, onError=0 | 185 ms |

Each condition has one valid run. These are callback timings rather than product UI TTFT or throughput benchmarks. All successful replies were natural Japanese greetings. Actual context/cache are taken from `engine_create_started` markers immediately before EngineConfig construction. The report formatter recomputes the old parity default and incorrectly prints 4096 for the overridden 1024 cases; it is not the source for the context column above.

## Native differences

Different binaries: libLiteRt.so, liblitertlm_jni.so and a diagnostic persistent-holder stub. The standard set includes libLiteRtClGlAccelerator.so and Qualcomm/QNN/NPU components. The candidate instead includes libLiteRtOpenClAccelerator.so and omits those Qualcomm components. libGemmaModelConstraintProvider.so content matches. This is a whole-stack comparison, not proof that replacing an individual accelerator or JNI is sufficient or safe for NPU.

## Excluded setup attempts and reporting corrections

Initial hard-link setup was denied and replaced with ordinary copies. First repack incorrectly compressed resources.arsc; Android rejected it before execution, then ZIP metadata preservation/alignment/signing was corrected. New-package stopped-state broadcasts produced no receiver marker and were excluded; valid runs launched the app first.

The host script initially expected the old report title and mislabeled completed current reports as host deadlines. Final classification above comes from recovered terminal reports and matching marker timestamps, not the initial host log summary. The archived runner now matches route metadata instead. The earlier PR #2598 two 1024 cases were rechecked: their terminal report files are still absent, so those remain incomplete observations, not evidence that 1024 is inherently broken.

## Scope and next work

The standard user's installed APK, backend/model preferences and chat history were not changed. The diagnostic process was stopped and ChatGPT foregrounded. NPU was not run in the isolated candidate, which intentionally omits its required native components. No production runtime fix or context increase was made.

Next isolate the GPU implementation/accelerator ABI within coherent runtime builds, then qualify a common runtime for GPU and NPU or assess process-separated runtimes. Do not directly promote this GPU-only native set into the working NPU APK. Repeat normal ChatScreen streaming, stop/recovery and long output before product promotion.

Diagnostic source snapshot (not a production merge candidate): 68e49f11b3f842940c7f7cf92129f8662e3bebcf on branch `diagnostic/gpu-controlled-comparison`.
