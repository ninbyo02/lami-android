# Validated GPU standalone token recount

## Scope and decision

The preceding device comparison is recorded in PR #2607 and `2026-09-13-tokenizer-only-device-validation.md`. This change adopts the standalone SentencePiece counts for explicit GPU post-response recount when the optional verified native artifact is packaged. GPU generation, TTS, cancellation and metadata ownership remain with their existing components. NPU, NPU fallback, CPU, servers and Release retain their previous counting paths.

This is a StandardDebug rollout, not a claim that every distributed build now uses the standalone counter. A clean CI checkout with no supplied native artifact uses the legacy path. The diagnostic comparison mode continues to publish legacy counts and compares the candidate separately.

## Selection and failure behavior

1. Reuse the existing exact MediaPipe count cache if available.
2. For a request whose requested and applied backends are both GPU, try the standalone count cache, then read the model's bounded SentencePiece section.
3. Require the device-validated tokenizer SHA-256 `e594c8a90eb08d8bda498ff4747977dc827ae0c3c56b5c0d41a605a22d02ef03` before entering native code.
4. Count the original input/output UTF-8 text, free the native processor within the same JNI call, and verify that file identity metadata did not change while counting.
5. Publish the counts with `sentencepiece_tokenizer_recount` and provider `standalone_sentencepiece`. Timing/UI recognizes this as a tokenizer result.
6. Unsupported tokenizer, read/format error, missing JNI, invalid counts or model replacement return to the existing MediaPipe path. Cancellation propagates. A failed standalone result is never cached.

The new LRU holds at most 16 pairs of counts and model/text identity keys, not raw text, model bytes or native handles. The existing legacy cache remains bounded independently. File identity follows the existing path/size/mtime convention; it is not intended to detect a same-size replacement with deliberately preserved timestamps.

## Build controls

Build the native artifact using the existing pinned tokenizer build script. Supply its verified output directory:

```sh
./gradlew :app:assembleStandardDebug \
  -Plami.tokenizerOnlyArtifactDir=/absolute/path/to/tokenizer-android
```

`-Plami.tokenizerOnlyGpuEnabled=false` disables adoption and packaging unless comparison is requested. `-Plami.tokenizerOnlyDiagnostic=true` selects the previous comparison-only mode. `-Plami.tokenizerOnlyForceFallback=true` bypasses standalone counting in an adoption build, allowing the real fallback path to be exercised on-device. These flags cannot enable Release adoption.

## Validation

Date: 2026-09-13. Device: NX733J / SM8750, the same installed models as #2607. Implementation commit `0df3aefa`; merged-main build commit `0929939b`.

- All 3,121 unit tests passed (1,829 Debug + 1,292 Release), including disabled/forced paths, cache invalidation, unrecognized tokenizer, missing JNI recovery, invalid results, model replacement during counting, cancellation and explicit GPU eligibility. Both lint tasks passed.
- The adoption APK completed GPU greeting, long, short, generation stop, resend and repeated short. Input/output pairs remained 11/5, 16/455, 11/1, 10/2 and 11/1. Uncached recount durations were 260, 216, 251 and 203 ms; repeated short was a 1 ms cache hit. The database recorded `sentencepiece_tokenizer_recount` and `standalone_sentencepiece`.
- NPU completed greeting, long, short, stop and resend, retaining `estimated_code_points`. GPU/NPU playback and generation stops had no late speech restart during the 2/8-second observations.
- Immediate resend with the fast counter completed and reused the GPU engine. The first count had already finished before the next submission, so this sequence is not evidence of simultaneous count/generate execution.
- Forced fallback completed the same GPU sequence with the same exact input/output pairs. Uncached MediaPipe recount took 1,197, 1,846, 1,626 and 1,563 ms; repeated short took 2 ms from cache. Provenance recorded `mediapipe` and `forced-fallback` on uncached turns.
- Forced-fallback overlap was established: next submission completed at elapsed 2,419,820,230 ms, before the first recount finished at 2,419,821,179 ms. Both responses completed with exact counts, and the second reused the GPU engine.
- Navigating to settings after a completed response interrupted the pending count publication: response text/status stayed completed with estimated statistics. Returning to chat and sending another request successfully produced a new exact fallback count. Unit tests separately cover cancellation propagation and model-file changes during the standalone invocation.
- The first fallback run stopped at a harness assertion requiring an early `speak_accepted` line. The bounded trace had retained later TTS requests but omitted that earlier event; the audio snapshot showed active speech, and the final trace showed completion followed by the next accepted/start event. The retry accepted either an explicit playback event or active speech plus TTS requests; the complete retry passed. No app/TTS change was made to address this harness issue.
- Candidate and fallback APKs contain the dedicated native library/license. Disabling adoption after an enabled build removed both from the APK; Release also excludes both. All 43 pre-existing native libraries match the prior normal package byte-for-byte.

These are serial device samples, not a controlled thermal/energy study. The improvement applies to work after response completion; no additional inference-latency claim is made. Precise whole-app memory savings were not measured. No native processor is retained between calls, consistent with the isolated allocation evidence in #2607.

Final committed-build smoke and restoration results are recorded in the companion JSON.


## Final installation and restoration

The APK built at `0929939b` passed two final standalone-count GPU turns and engine reuse. Its 44 native payloads, including the new tokenizer, match the fully tested candidate APK. It remains installed and its on-device SHA-256 was verified: `4b650fafbfadb5d8f3d0a19d327935d4abce83c48071eaaacb48138496229db7`.

All 180 pre-existing database rows are unchanged; the final 234 rows include test additions. SQLite integrity is `ok`. Datastore preferences, IME, screen timeout and diagnostic log properties were restored; ChatGPT is in the foreground. Private database/UI artifacts remain in the build-host test workspace and are not committed. The follow-up evidence commit only changes this report/JSON; app source remains the device-tested implementation.
