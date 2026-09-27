# Tokenizer-only device comparison and adoption decision

Date: 2026-09-13. PR #2606 was squash-merged as `acf61a81c30b94e5330cb363a42bd3c1a376dca7` before testing. Both merge-commit workflow runs completed successfully: [34730456952](https://github.com/ninbyo02/lami-android/actions/runs/34730456952), [34730456926](https://github.com/ninbyo02/lami-android/actions/runs/34730456926).

## Decision

Proceed with a separate, guarded adoption change for post-response GPU token recount on the validated SentencePiece model. The candidate reproduces the old counter on the device and substantially reduces the measured counting operation. This evidence change does not replace production counts or enable the diagnostic by default.

Keep successful native NPU, untested CPU inference and server usage behavior unchanged initially. NPU success currently stores `estimated_code_points` and does not call the MediaPipe post-response comparison hook. Reading the NPU model's tokenizer successfully is not proof that native NPU generation statistics can be replaced. Plain prompt-text counts also do not establish the full chat-template/prefill context token count.

## Same-device app comparison

NX733J/SM8750, existing installed models, original effective prompt and raw generated text. The log-enabled GPU sequence compared both counters within the same diagnostic APK after each successful response. All five input/output pairs matched; two additional short turns during the overlap test matched (7 comparisons total, zero mismatches).

| GPU case | Input/output tokens | Existing recount | Standalone comparison, including read/hash/load/count/free |
|---|---:|---:|---:|
| Greeting | 11 / 5 | 1,422 ms | 147 ms |
| Long, 835 displayed characters | 16 / 455 | 1,785 ms | 223 ms |
| Short | 11 / 1 | 1,807 ms | 239 ms |
| Resend after stop | 10 / 2 | 1,596 ms | 231 ms |
| Repeated short | 11 / 1 | 1 ms (existing result cache) | 252 ms |

The current diagnostic executes the existing counter first and then the candidate. It adds work and postpones the metadata update until comparison completes; it does not yet reduce user-visible response latency. These sequential device samples are not a controlled thermal/energy benchmark. Preserve the existing result cache before invoking a new provider: a cached count is faster than reloading the standalone tokenizer.

The initial GPU sequence completed successfully but device property `log.tag=S` suppressed comparison logs. Its database records show the old exact recount completed. Do not count that sequence as candidate-parity evidence. Specific diagnostic log tags were temporarily enabled for the overlap test and full GPU repeat, then restored. No application change was needed to obtain logs.

## Actual model identity and isolated Android JNI test

The GPU container is 2,583,085,056 bytes; the NPU container is 3,016,294,400 bytes. The GPU container remains different in size from the PC fixture. Both actual phone containers contain a 4,689,013-byte SentencePiece section with SHA-256 `e594c8a90eb08d8bda498ff4747977dc827ae0c3c56b5c0d41a605a22d02ef03`.

An isolated Android `app_process` under the existing app UID loaded the installed diagnostic JNI and the APK's real Kotlin container reader. Each phone model passed all 11 pinned corpus cases: empty text, Japanese, Markdown/newlines, mixed scripts/emoji, embedded NUL, whitespace, special-token-looking strings, code and long text. Each also passed three invalid-model/oversized-text/recovery cycles. These are corpus-reference checks, separate from the app's MediaPipe comparisons above.

Isolated JNI call time was 176–289 ms including a sampling-thread join, excluding the separately recorded container-read time. Native allocated-heap sampling at 1 ms intervals showed a maximum increase of approximately 66.2 MiB, with allocation returning near its pre-call level after each invocation. This excludes Java byte arrays and is neither process RSS/PSS nor a guaranteed instantaneous peak. The former MediaPipe memory cost was not remeasured in this isolated harness; do not claim a precise whole-app memory reduction from these data.

Probe source: `native/tokenizer_only/tests/android/StandaloneSentencePieceJni.java`. Compile against Android 36 with Java 17 bytecode, dex with D8 (min API 34), and put the dex JAR first in `CLASSPATH`, followed by the installed diagnostic APK. Invoke under `run-as io.github.ninbyo02.lami` using `app_process / io.github.ninbyo02.lami.ui.screens.home.StandaloneSentencePieceJni LIBRARY_PATH CORPUS_TSV MODEL_PATH...`. The corpus TSV is base64 UTF-8 text plus tab-separated expected count, generated from `native/tokenizer_only/tests/cases.json`. Use a read-only dex file in app code_cache. The APK supplies Kotlin dependencies and the container reader. The probe class intentionally has the JNI bridge's exact name and must never be packaged into the application.

## Functional checks and restoration

- GPU twice and native NPU once: greeting, long, short, generation stop and resend passed. GPU held-engine reuse and result-cache reuse passed.
- Playback stop and generation stop: no active audio at 2 and 8 seconds; no late speech request/accept/start in the observation windows.
- Concurrent send: second submission completed at elapsed 2,417,247,750 ms, before first recount finished at 2,417,248,838 ms. Both replies finished, received exact later counts, and the second reused GPU without fallback.
- Normal and diagnostic APKs built from the merged source have 43 byte-identical pre-existing native libraries; only the tokenizer library is added. Compared with the older #2603 APK, two debug helper libraries contain changed build paths, while their `.text` machine-code sections are identical. All inference library payloads match the older package.
- All 142 pre-existing database rows remained byte-for-field equivalent; final count is 180, with test additions only. SQLite integrity checks passed. Datastore preferences, IME, screen timeout and temporary log properties were restored.
- The normal, diagnostic-disabled merged APK was installed and its SHA-256 checked on-device: `c4a09f8f4b07cb218b18bd0d92cf4a9a6ff57fa81c72e6b64bf6b74332800295`. It contains no tokenizer-only library/license assets. It launched successfully; ChatGPT was restored to the foreground.
- Android package replacement cleared the probe's code_cache before explicit cleanup. Cleanup was adjusted to accept an already absent directory, and restoration completed. No model or conversation was removed.

Diagnostic APK SHA-256: `153e89982b8f1544befb7ef50f50c2e8fcfa0b6726383e3b8779cffac7964c6a`.

## Next implementation boundary

1. Reuse cached exact results first. On cache miss, use the standalone provider only for the validated GPU recount path and recognized container/tokenizer compatibility.
2. For unsupported models, unavailable native library or recoverable parse/count failure, execute the existing MediaPipe fallback. Preserve cancellation, the one-running/one-waiting coordinator, original-text inputs and metadata-update identity checks.
3. Record which provider produced counts and its duration. Do not label candidate text counts as native generation-token measurements.
4. Do not retain a native processor/engine just to optimize repeated counts. Keep native invocation scoped to one call.
5. Validate the provider-switching implementation on-device, including forced fallback, model changes and cancellation during counting. The present comparison prototype does not test those not-yet-implemented switching behaviors. Expand CPU/NPU support only with separate evidence.

Companion JSON contains machine-readable timings, sampled allocations, hashes and restoration results. Full UI/database backups remain private in the build-host test workspace; they are not committed here. No additional full JVM suite was rerun for this evidence-only change; the merged CI and actual device builds/tests above are the validation basis.
