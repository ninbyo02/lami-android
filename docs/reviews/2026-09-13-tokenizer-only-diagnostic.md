# Tokenizer-only JNI diagnostic review (2026-09-13)

## Decision and scope

Add an opt-in comparison to investigate avoiding MediaPipe inference-engine initialization for post-response token recount. This is a diagnostic prototype, not adoption of a new authoritative counter. It follows [the feasibility investigation](https://github.com/ninbyo02/lami-android/pull/2605).

Normal builds keep the existing recount and displayed statistics. `lami.tokenizerOnlyDiagnostic` defaults to false. Only an explicitly enabled `standardDebug` build receives the extra native library and license assets. Release uses a no-op hook and has the flag false, including when the property is passed. Server usage/statistics, inference routes, held engines, TTS and stop controls are unchanged. This candidate only reads SentencePiece sections from supported local `.litertlm` files; it is not a universal server tokenizer.

The comparison runs after the existing authoritative count inside the existing serialized recount coordinator, using the original effective prompt and raw response. Candidate counts are logged and never substituted into the authoritative snapshot. Thus a diagnostic build still initializes the old counter and adds comparison overhead; it does not yet improve end-user latency.

## Isolation and failure behavior

- A separate `liblami_tokenizer_only.so` statically embeds pinned SentencePiece v0.2.1 (`31646a467d2051eb904e0b45de3a73e91fe1c1e3`). No AAR/runtime upgrade or replacement.
- Only one JNI symbol is exported; all other symbols are hidden/local. Android dependencies are only liblog, libm, libdl and libc, with static C++ and 16 KiB ELF LOAD alignment.
- The processor is scoped to a single JNI call and destroyed on return or C++ exception. No native handle or resident processor cache is retained.
- The bounded Kotlin reader supports container schema 1.5.0 and exactly one SentencePiece section. It reads at most 1 MiB header and 16 MiB tokenizer, never the model-weight sections; it validates offsets, section overlap and field bounds. Unsupported/HF-only containers fail closed.
- Text uses standard UTF-8 byte arrays, including embedded NUL; Kotlin limits each string to 1 Mi UTF-16 code units, JNI limits each encoded buffer to 4 MiB.
- Recoverable exceptions and missing JNI become diagnostic failure states. Coroutine cancellation remains cancellation. Native counting is synchronous and cannot be interrupted mid-call; the coordinator remains occupied until it returns.
- This is an in-process native library. Fatal native faults and process-wide OOM are not contained by the exception wrapper. Native isolation reduces runtime coupling but is not a process sandbox.
- The diagnostic log tag is `LamiTokenizerOnly`; it logs status, tokenizer section SHA-256, count pairs and durations, not paths or conversation text.

## Reproduce (Linux build host)

Use Java 21, CMake/Ninja, readelf/nm, a clean SentencePiece checkout at the pin above and Android NDK 28.2.13676358. No native dependency is downloaded by the default Gradle build.

```sh
python3 scripts/build_tokenizer_only.py --sentencepiece-source /absolute/sentencepiece --output "$PWD/build/tokenizer-host"
python3 scripts/build_tokenizer_only.py --sentencepiece-source /absolute/sentencepiece --output "$PWD/build/tokenizer-android" --ndk /absolute/ndk/28.2.13676358
./gradlew :app:assembleStandardDebug -Plami.tokenizerOnlyDiagnostic=true -Plami.tokenizerOnlyArtifactDir="$PWD/build/tokenizer-android"
```

Artifact verification checks the pinned source revision, ABI, staged library checksum, exclusive library file set and license. The build bundles upstream dependency notices. Keep host and Android output directories separate.

The host harness can use either extracted SentencePiece bytes or full containers. For full containers it invokes the actual compiled Kotlin reader:

```sh
python3 scripts/test_tokenizer_only_host.py --library "$PWD/build/tokenizer-host/jniLibs/host/liblami_tokenizer_only.so" --kotlin-classes "$PWD/app/build/tmp/kotlin-classes/standardRelease" --kotlin-stdlib /absolute/kotlin-stdlib-2.0.21.jar --java-home /absolute/java-21 --output "$PWD/build/tokenizer-host/tests" /absolute/gemma-4-E2B-it.litertlm /absolute/gemma-4-E2B-it_qualcomm_sm8750.litertlm
```

## Validation

- StandardDebug 1,819 and StandardRelease 1,282 unit tests: 3,101 total, zero failures/errors/skips. Both lint tasks passed.
- Host JNI plus the actual Kotlin container reader: 11 corpus cases passed for each of two full containers. Empty text, Japanese, Markdown, mixed scripts/emoji, embedded NUL, whitespace, special-token-looking text, code and long text are covered. Each model also passed three invalid-model/oversize/recovery cycles.
- Host corpus counts are pinned reference values; they are not an Android MediaPipe parity result.
- APK checks passed: the diagnostic contains the tokenizer library and licenses, default/Release do not; all 39 existing native libraries are byte-identical between the default and diagnostic APKs. Incremental input tracking was corrected after initial packaging checks detected a missing diagnostic library.
- Android arm64 library built with one exported JNI entry and no inference/shared C++ runtime dependency. Artifact hashes and APK packaging checks are recorded in the companion JSON.
- Kotlin tests/lint and packaging checks use `-Plami.allowMissingQairt244Jni=true` in this clean checkout. These are non-NPU smoke artifacts, not daily-use device APKs or GPU/NPU promotion evidence. No device installation was performed.

## Remaining adoption gates

Compare the old MediaPipe counter and candidate in the same device build with the same actual model file and original strings; test GPU/NPU long → short → stop → resend and TTS, cancellation, failure recovery, model changes, and count disagreement. Give a device-test duration estimate before starting that work. Measure recount latency and peak memory on Android before claiming any improvement.

The PC GPU model is 2,588,147,712 bytes, while the previously reported installed GPU model was 2,583,085,056 bytes. They must not be treated as the same artifact. The PC NPU model is 3,016,294,400 bytes. Both PC tokenizer sections are 4,689,013 bytes with SHA-256 `e594c8a90eb08d8bda498ff4747977dc827ae0c3c56b5c0d41a605a22d02ef03`; matching size alone does not prove phone model identity.

Only after those gates should a separate change consider adopting the count-only provider with a verified fallback. Server-reported usage should remain authoritative for server models; matching local tokenizer assets and server-side templates would require separate compatibility evidence.
