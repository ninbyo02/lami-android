# Neural voice diagnostic: 2026-10-01

The exact phrase `はい。` now has an Android diagnostic path through the
28-layer INT4 talker, FP32 code predictor, speech decoder and PCM AudioTrack.
This is a fixed-length golden integration probe. It does not prove EOS or complete
utterance generation, and does not enable neural speech in chat.
The prepared embeddings belong only to this phrase. The decoder artifact accepts
exactly 31 frames; early EOS/special tokens or a changed code sequence reject playback.

## Host evidence

`2026-10-01-neural-voice-host.json` records 496/496 matching generated codes,
59,520 finite samples at 24 kHz (2.48 seconds), and a peak below 1.
Host matrix heads use the same sequential float32 accumulation as Kotlin.
Initial mismatches came from computing rotary positions in double precision;
the prepared bundle now stores the original float32 rotary values.
The result does not verify Android kernels, perceived voice quality, latency,
AudioTrack output, or on-device memory requirements.

## Lifecycle and display

ExecuTorch and the neural diagnostic classes are debug-only; release builds use
a no-op entrypoint and do not include the ExecuTorch dependency.

The explicit debug intent runs inference on a worker dispatcher and is cancelled
with the activity. Cancellation is checked between native calls; an in-flight
native forward cannot be interrupted. Model and matrix hashes are validated.
PCM playback handles partial writes, completion and cancellation, and releases
AudioTrack in all cases. NaN, infinity, clipping, silence and oversized PCM are rejected.

A per-invocation atomic gate closes when the NPU provider returns, so a UI update
posted earlier cannot restore a transient answer after the DB-backed reply is finalized.
Existing generation, chat and stop checks remain in place. This fix needs an actual
startup/first-response regression test on the phone.

## Run after the phone becomes available

Export with the ExecuTorch 1.4 Python environment:

```bash
python scripts/voice/export_prepared_hai.py --model MODEL_DIR --artifacts PTE_DIR --output BUNDLE_DIR
python scripts/voice/validate_prepared_hai.py BUNDLE_DIR --report HOST_REPORT.json
```

Stage the bundle under the debug app's private files directory:
`local_models/lami_tts/prepared_hai/`.
Start the debug activity with boolean extra `lami_neural_tts_hai_probe=true`.
Read `files/neural_tts_hai_probe.txt` using `adb shell run-as io.github.ninbyo02.lami`.
Expect synthesis success followed by playback started and complete.
Budget up to 10 minutes for the diagnostic; Kotlin scalar matrix heads are a
correctness implementation with unmeasured phone performance.

Then cold-start without the diagnostic extra and test standard Android TTS,
queued sentences, replacement, stop, chat switching and the first NPU response.
Check that the assistant text appears once both before and after persistence.

## Remaining work

- Actual Android inference/PCM/audio verification and text-display regression.
- Resolve the RC1 model failure to emit EOS before the bounded cache is full.
- Optimized native matrix heads and measured time to first audio.
- Neural `LamiVoiceEngine.speak()` integration and queue/replacement behavior.

No large PTE, context or tensor artifacts are tracked. The original experimental
working tree and unrelated QAIRT/model changes are preserved.

## Arbitrary-text diagnostic added before device testing

An opt-in debug text probe now supplies the model's own Qwen byte BPE tokenizer
and a memory-mapped FP32 text projection table. It uses the original CustomVoice
non-streaming layout, Japanese language tag and `lami_cute` speaker. Twenty texts
cover Japanese, numbers, mixed scripts, whitespace, emoji and decomposed kana.
Tokenizer IDs match the model tokenizer; input embeddings are checked against
captured original-model inputs. NFC normalization is required by that tokenizer.

The main INT4 cache export supports 256 positions. The dynamic FP32 speech decoder
supports 2–256 frames; nine lengths (2, 3, 7, 16, 31, 32, 64, 128, 256) match the
untouched eager decoder with maximum absolute error below 4.4e-5. See
`2026-10-01-variable-voice-decoder.json`. The export uses integer causal-convolution
padding and the maximum-length example so ExecuTorch reserves sufficient storage.

Arbitrary generation uses seeded top-k sampling (Java Random seed 42, top-k 50,
temperature 0.9 and main-codec repetition penalty 1.05), permits only codec IDs
0–2047 plus EOS, and rejects playback if EOS is absent at the cache limit.
These settings follow the original defaults but do not reproduce PyTorch's RNG.
A host validator uses the same Java random stream and scalar FP32 accumulation.
Missing EOS is a failed synthesis, not a successful truncated utterance.

The bundle is about 1.92 GB, including a 622 MB projected text table. This is a
correctness prototype, with phone memory and latency unmeasured. Native head
optimization and ordinary chat queue integration remain future work.

To reproduce the text bundle in the ExecuTorch 1.4 environment:

```bash
python scripts/voice/export_text_frontend.py --help
python scripts/voice/export_main_cache.py --help
python scripts/voice/export_dynamic_decoder.py --help
python scripts/voice/build_text_voice_bundle.py BUNDLE_DIR
python scripts/voice/validate_dynamic_decoder.py --help
python scripts/voice/validate_text_voice.py --root BUNDLE_DIR --model MODEL_DIR --report REPORT.json
```

Keep the fixed probe extra and additionally set the string intent extra
`lami_neural_tts_text_probe` to select arbitrary text. Standard launches continue
to use Android TTS. The PR remains draft pending the model termination issue and
actual-phone audio/startup/duplicate-text regression.

### End-to-end host results

`2026-10-01-arbitrary-text-voice-host.json` records complete EOS-terminated output
for `こんにちは。` (15 frames, 1.20 seconds) and `好きな色は赤です。` (26 frames,
2.08 seconds), with finite PCM in range and nonsilent peaks. The complete projected
inputs differ from original-model inputs by at most 2.39e-7.
`はい。` exhausts the 242-frame budget without EOS; audio is withheld. Thus the
three-case suite is **blocked_missing_eos**, not passed. Greedy decoding also
failed to terminate that phrase. Perceived quality has not been checked.

`2026-10-01-reference-voice-eos.json` records the same missing-EOS failure in the
original FP32 RC1 API: Auto greedy, Japanese greedy and Japanese sampled all
reach 256 tokens without EOS for `はい。`. This rules out a defect unique to the
INT4 export or Android frontend as the sole cause, but does not establish the
root cause. The fixed 31-frame probe is only a numerical integration reference.

## Evening physical-device validation

The release artifacts were located intact under
`/home/sato/project/lami-android-voice-dataset/releases/prepared-hai-android-v1`.
Re-export and Base-model download are not prerequisites for this probe.
All 35 fixed-bundle SHA-256 entries passed before installation. The existing
Standard Debug APK installed on NX733J / Android 16 at `192.168.52.52:43063`,
and the complete fixed/text bundle was copied into the private app files.

The MainActivity diagnostic started but was terminated by a simultaneous
LiteRT-LM NPU engine initialization SIGABRT. DropBox backtrace identifies
`LiteRtLmJni_nativeCreateEngine` and
`NpuKotlinConversationProductRoute.createNpuEngineWithBoundedRetry`, not
ExecuTorch, as the aborting path. Two MainActivity attempts reproduced this.
A debug-only `LamiNeuralVoiceDiagnosticActivity` now provides a voice-only
entrypoint without constructing chat navigation or the NPU startup pipeline.
Its report is displayed on-screen; cancellation remains Activity-scoped.
Device synthesis and audible playback are still pending this isolated test.

The user listened to the LAMI v3-03 Clone reference in Voice Lab and confirmed
that it sounds normal. This approval concerns the reference WAV, not synthesized
RC1/INT4 phone output.

### Isolated device result and fixes

Activity-scoped testing was cancelled when leaving the screen. The voice-only
entrypoint now starts an explicit foreground debug service with a notification
Stop action, START_NOT_STICKY, and the existing ten-minute generation bound.
The activity exposes arbitrary text input, speak and stop controls.

The arbitrary-text attempt exposed Android's unsupported
`Pattern.UNICODE_CHARACTER_CLASS` flag (DropBox ExceptionInInitializerError).
The tokenizer now spells out Unicode White_Space and uses no unsupported flag.
The twenty existing tokenizer/embedding fixtures passed after this change.
Android itself then successfully tokenized and generated `こんにちは。`.

The initial phone generation produced EOS after 76 frames and 145,920 finite
PCM samples at 24 kHz, in 220,757 ms. Playback was rejected because the code
required STATE_INITIALIZED before writing a MODE_STATIC AudioTrack; this state
is legitimately STATE_NO_STATIC_DATA before the first write. The player now
rejects STATE_UNINITIALIZED before writing and checks STATE_INITIALIZED after.

Debug native matrix heads preserve sequential FP32 multiplication/accumulation
with FMA and fast-math disabled. A bit-exact device self-test compares three
native head rows against Kotlin before any actual inference. Native heads reduced
the identical 76-frame run to 49,457 ms (4.46x overall); codec frames typically
fell from 2.5 seconds to 0.4 seconds. The phone recorded synthesis=success,
playback=started, playback=complete. The generated WAV was copied to Voice Lab
under `lami-android-device-tts/hello-device-20261001.wav` for listening review.
Actual generated voice quality is not yet user-approved. The host greeting was
15 frames, while phone generation was 76; termination alone does not prove quality.

Fixed `はい。` generated all 496 codes but failed the strict host equality gate.
The latest diagnostic retains phone codes and reports mismatch counts for further
investigation. Ordinary chat still uses Android TTS. Measured phone latency and
the missing-EOS cases remain blockers to normal chat promotion.

Rebuild the explicit voice smoke artifact with JDK 21 and
`-Plami.allowMissingQairt244Jni=true`. This is not NPU validation: the source
worktree has no canonical custom JNI inputs. Existing main-tree libraries differ
from the checked-in canonical GPU/NPU hashes and were not used for promotion.

The native fixed probe completed in about eleven seconds but still recorded
414/496 different codes. The first temporal difference is frame 0, codec group 3
(phone 636 versus host 1613). This is not resolved by native head arithmetic.
The phone codes and comparison report are archived under the local ignored
`artifacts/voice-device-20261001/` directory for further analysis.
The second arbitrary sentence (`好きな色は赤です。`) also generates substantially
more frames than its host reference; do not equate PCM/EOS success with faithful
pronunciation or promote it into ordinary chat before listening/termination review.

The red-color sentence exhausted all 239 available frames after approximately
104 seconds without EOS; its incomplete audio was correctly withheld.

The foreground-service stop action was exercised during codec generation; the
report recorded status=cancelled and no service remained. Final debug build and
lint passed; all eight frontend/sampler test methods passed (20 frontend cases).

## Tail noise and real-time feasibility follow-up

The user reports noise after the greeting. The 6.08-second device WAV contains initial speech energy around 0–1 seconds, a nearly silent interval around 1–2 seconds, then renewed energy from 2 seconds onward. This is present in the WAV itself; EOS/model inference remains unresolved. No fixed-duration clipping or silence-based truncation was adopted, because that could remove valid longer speech.

A four-thread main/CP trial completed in 43,514 ms versus the earlier 49,457 ms; its WAV SHA-256 was exactly unchanged. A subsequent four-thread main/CP/PCM trial took 52,753 ms and produced the same WAV. These sequential trials do not establish a stable speedup. Greedy main/CP selection produced 83 frames (6.64 seconds), taking 52,586 ms, and did not correct excessive duration. All experimental thread and sampling changes were reverted.

The existing codec loop needs roughly 0.4 seconds per 0.08 seconds of audio; preparation and final waveform decoding each cost roughly 8 seconds. Starting playback earlier requires chunked generation/decoding, but uninterrupted real-time playback additionally requires a substantial inference speedup. The present implementation is not real-time. Next engineering gates are device/host intermediate numerical parity and reliable EOS, then cache-copy reduction, accelerator or smaller-model evaluation, and chunk-boundary audio validation.

Raw reports and waveform energy analysis are retained in the ignored artifacts/voice-device-20261001 directory.

## Main decoder isolation and FP32 comparison

Opt-in fixed-probe tracing shows main prefill errors rise from less than 0.00021 at positions 0–4 to 0.0559 at position 5 and 1.106 at position 6. CP group 2 then selects a different token. Feeding the host final main hidden into the device CP restores all 15 first-frame CP argmax tokens, with maximum logit error below 0.00010. This isolates the main decoder as the upstream source; it does not prove a single faulty kernel or establish full-sequence parity. Replay is diagnostic-only and cannot release audio. Trace and replay flags were removed from the phone after the experiment.

The exporter now supports original-checkpoint FP32, and the text bundle can select a main_program that must be a simple filename present in the SHA-256 manifest. Existing INT4 remains the default for existing manifests. A 1,762,616,320-byte FP32 main program was exported, validated, and installed in the phone's debug bundle. The original text manifest is backed up in ignored device artifacts. No PTE is committed.

On device, FP32 greeting reaches EOS at 15 frames (1.2 seconds), synthesizes in 20,570 ms, and plays completely. Its final 0.2 seconds are silent. The user confirms tail noise is gone, with a possible small voice change. The color sentence reaches EOS at 23 frames (1.84 seconds), synthesizes in 29,509 ms, and plays completely. The user confirms no noise but imperfect intonation. A comma variant reaches 35 frames (2.8 seconds), synthesizes in 34,941 ms; intonation review is pending. Hiragana was also explored before the user clarified that the concern was intonation, not pronunciation; no input rewrite was adopted.

Host FP32 reaches EOS for greeting and color but fails to reach EOS for hai within 242 frames. This remains a diagnostic comparison, not an arbitrary-text completion claim or production chat integration. Voice Lab exposes greeting and the two color phrasings for listening comparison. Build, focused tokenizer/sampler regression, and lint passed; real-time synthesis and voice/prosody stability remain unresolved.

The user subsequently confirms voice quality and intonation are restored after the comma comparison, and requests a slightly brighter tone. An exclamation variant generated 28 frames (2.24 seconds) in 26,427 ms and played completely; it is exposed as a separate comparison candidate in Voice Lab. Brightness is awaiting user review; no global punctuation rewriting or pitch change was applied.

The user approved the brighter exclamation comparison: no remaining perceived voice unnaturalness. Preserve this listening baseline while evaluating latency changes. This approval covers the tested comparison, not every arbitrary sentence.

## Bounded model reuse and latency breakdown

Debug arbitrary-text synthesis serializes access to one manifest-identified module session. Main, CP and PCM modules are loaded lazily, reused for consecutive utterances, and closed after 45 seconds idle. Every utterance still validates bundle SHA-256 entries and creates fresh decoder KV tensors, token history and sampler. Manifest/root changes invalidate the session; synthesis errors and cancellation close it. No warm session is added to normal chat or release code.

The approved bright color sentence generated in 28,193 ms cold and 25,148 ms warm; all three warm model-load metrics are zero. After idle expiry the modules reload and generation took 17,352 ms. All three WAVs exactly match the approved baseline SHA-256. Overall device latency clearly varies, so the single cold/warm pair is not a stable 11% throughput improvement claim. The warm run spends 5,935 ms validating hashes, 1,015 ms preparing text/tables, 4,010 ms on prefill, 12,531 ms on codec generation, and 1,599 ms converting PCM. Generation still does not keep pace with 2.24 seconds of speech.

Device cancellation during codec generation succeeds. The next utterance loads all three modules anew, completes playback, and produces the exact approved WAV. Idle expiry, cancellation recovery, focused tokenizer/sampler regression, standard debug build, lint, diff checks and native-binary guard pass. This completes latency measurement and bounded model reuse; chunked playback, faster codec inference and the short hai EOS issue remain open.

## Decoder output-copy reduction

Detailed work timings show 46 main forwards cost 4,761 ms, but their output cache extraction/reallocation separately costs 3,959 ms. The 448 CP output copies cost 456 ms. Decoder inputs now have their own direct FloatBuffers and persistent Tensor wrappers; output tensors copy directly into those owned buffers, with exact element-count checks and buffer position resets. Every utterance and CP frame still receives newly zero-initialized buffers. No output buffer is retained as an input alias, and floating-point arithmetic is unchanged.

Main output copies drop to 164 ms cold / 171 ms warm (same 46 calls), and CP copies to 87 ms (same 448 calls). Native matrix heads were measured rather than changed: about 1.44 seconds total in the baseline. Cold synthesis of the approved bright sentence measured 27,455 ms before and 23,376 ms after; warm synthesis measured 19,614 ms. All bright WAVs exactly match the user-approved baseline. Greeting WAV also matches its FP32 baseline, and all fixed INT4 codes match the previous device codes (the pre-existing golden-reference gate still fails).

Cancellation during generation and subsequent recovery succeed with all three models reloaded and the exact approved WAV; recovery synthesis measured 11,752 ms. Device scheduling/temperature/load affect total elapsed time, so report the isolated copy reduction as the strongest evidence rather than treating the best total as a stable throughput guarantee. Focused tests, standard debug build, lint and diff checks pass. Real-time synthesis and the hai EOS issue remain open.

## Bounded candidate selection

The seeded sampler now maintains only top-k candidates in primitive reusable arrays instead of allocating/scoring/sorting every candidate pair. A worst-first heap followed by heap sort preserves descending Double score and ascending token ordering, including signed zero. Repetition-penalty arithmetic, temperature division, ordered weight summation and one RNG draw per call are unchanged. Allowed codec sets are created once per utterance instead of once per CP step. All allowed values still receive validity checks.

An independent full-sort reference matches 800 sequences across changing allowed sets, tied scores, signed zero, repetition penalties and top-k sizes 1/2/50/80. Device cold/warm bright speech and greeting WAVs exactly match their approved baselines. Warm generation measured 18,738 ms; codec time was 8,345 ms versus 9,422 ms in the earlier warm buffer-reuse run. The new isolated CP sampling metric reports 128 ms across 420 calls. Total elapsed time remains variable and is not a stable hardware throughput guarantee. Standard debug build, six sampler tests, three frontend tests, lint, diff checks and native-binary guard pass.

Further large costs are main/CP execution and per-utterance bundle hashing; neither model numerical precision nor verification was weakened. Real-time generation and the hai EOS limitation remain open.


## CPU thread-count experiment after PC restart

PC, ADB, and Voice Lab connectivity recovered. The previous commit `22fff1fb` passed all five GitHub check runs. A temporary debug-only selector compared ExecuTorch CPU thread counts on the FP32 bundle. The approved bright phrase completed with default/2/4 threads in 15,536/25,134/15,141 ms respectively; all three WAVs matched the approved SHA-256 exactly. These sequential measurements do not establish a stable improvement.

The subsequent one-thread trial stopped making progress inside PCM forward. A fresh-process default retry also stopped at PCM forward; the cause has not been established. Both trials were interrupted, the selector source was reverted, and the device selector file removed. No thread-count change is adopted. The prior build is restored and playback recovery is checked separately. Structured completed-trial metrics are in `2026-10-01-neural-voice-cpu-threads.json`; incomplete reports remain in ignored device artifacts.

Recovery: rebuilding and reinstalling the unmodified module loader restored normal synthesis and playback (22,141 ms, 53,760 samples). The recovered WAV matched the approved bright sample exactly (`58c9684266c899b91d5ba2a7ece3ba1786f6e76334705d828812ead3894022b9`). The diagnostic thread flag is absent. Build and lint passed for the experimental source; the reverted stable source build also passed. Only experiment documentation is committed.


## Bounded tokenizer reuse

The immutable vocabulary, merge ranks and special-token table are now retained in the existing serialized, manifest-bound module session for at most 45 seconds of inactivity. All asset SHA-256 checks still run for every utterance before the tokenizer is reused. Utterance text context, matrices, decoder KV buffers, random sampler and token history remain fresh. Session expiry, manifest changes and exceptions/cancellation discard the tokenizer with the models.

On the approved bright phrase, first tokenizer load took 1,542 ms and total text preparation 1,564 ms; immediate reuse took 0 ms and text preparation 23 ms. Cold/warm synthesis completed in 23,077/18,157 ms. This sequential total-time difference includes module reuse and device variability; only the eliminated tokenizer load is directly attributable to this change. Bright and greeting WAVs matched their approved hashes exactly; greeting preparation took 4 ms.

A temporary trailing newline in vocab.json was rejected by full SHA-256 validation while the tokenizer was cached, with no playback. The original file was restored; the next utterance reloaded the tokenizer (1,159 ms) and produced the approved WAV. After 47 idle seconds, tokenizer reload was confirmed (287 ms), but PCM forward stopped making progress after model reload. The incomplete trial was interrupted. This resembles the prior thread experiment's decoder stall; it does not establish the root cause or a tokenizer regression. A fresh app restart completed synthesis and playback in 15,680 ms with the exact approved bright WAV. Native decoder reload stability remains a blocker for production and merge.

Standard debug build, lint, six sampler tests and three frontend tests passed. No native binaries are tracked. Detailed metrics and the incomplete-trial limitation are recorded in `2026-10-01-neural-voice-tokenizer-reuse.json`.


## Decoder reload investigation: unresolved

A temporary dedicated OS thread kept text-path module construction, inference and idle destruction on the same executor. Initial/warm/greeting and two expiry/rewarm cycles (seven completed utterances) produced exact approved WAVs. The third expiry trial again stopped progressing in PCM forward. Thread affinity alone therefore did not resolve the problem, and its Android source change was reverted. Native debuggerd capture was blocked by the stock device's root requirement. No precise native root cause is claimed.

A new opt-in `--backend portable` decoder export omits the XNNPACK partitioner; the exporter default remains XNNPACK. Bounded host validation against the original PyTorch decoder passed for 2 and 3 frames, with maximum absolute errors 1.53e-6 and 9.98e-7. However, portable execution took 9,230 ms for 0.16 seconds of PCM and 13,740 ms for 0.24 seconds of PCM on the PC. This is a diagnostic comparison, not a viable real-time replacement. The initial nine-case validation was interrupted after successful 2/3/7-frame cases because of the slow portable kernels; no full-range parity claim is made and the portable asset was never installed on Android. The validator now accepts explicit `--frames` and records native execution time.

The stable Android source (bounded tokenizer reuse, unmodified native loader) was rebuilt/reinstalled. Restart recovery completed synthesis/playback in 20,726 ms and matched the approved bright WAV exactly. The APK remains on that stable source. Only diagnostic scripts and evidence are committed. Android debug build/lint passed for the thread experiment, restored debug build passed, Python compilation and git diff checks passed. Decoder reload stability remains a production/merge blocker. Full metrics are in `2026-10-01-neural-voice-decoder-reload.json`.


## Fresh PCM decoder process: reload-path mitigation

Debug arbitrary-text synthesis now binds a private, non-exported service in `:voice_decoder`. Each utterance starts a fresh native PCM runtime and unbinding exits only that named decoder process. The main application retains its serialized, 45-second main/CP/tokenizer session. Full per-utterance bundle hashes, original codec sampling, natural EOS checks and PCM validation remain intact. Request/output files in private cache avoid Binder transaction limits and are removed in finally. Process disconnection and binding errors fail the request; a 60-second decoder timeout and coroutine cancellation unbind the worker even if native forward blocks. Fixed strict-parity diagnostics and normal chat TTS routes are unchanged.

This avoids repeatedly unloading and rebuilding the PCM native runtime in the same process. It is a mitigation of the observed failure path, not a proven explanation of the underlying native bug. The official 1.4.1 release notes list wheel-path and quantized PReLU fixes rather than a matching reload-hang fix, so no unrelated dependency upgrade was adopted.

Ten lifecycle utterances passed, including greeting, three 47-second idle/reload cycles, rewarm after each cycle, and recovery after stopping during PCM forward. Every successful bright/greeting WAV exactly matched its approved SHA-256. Each decode used a different worker PID, and workers/request files were gone after completion. Native-forward stop reached the cancelled report in 219 ms in this trial, produced no playback, and the next utterance succeeded with the approved WAV. A PC reboot interrupted the external test driver after cycle two; persistent evidence was retained and the driver resumed to complete cycle three and stop/recovery. A final rebuilt/reinstalled APK smoke passed with exact approved bright WAV and process/file cleanup.

Process startup/transport added roughly 0.3–0.7 seconds beyond model load and native forward in completed measurements. A decoder model load is now paid on every utterance; this is a reliability tradeoff, not a real-time speedup claim. Standard debug build, lint, six sampler tests, three frontend tests, Python compilation, native-binary and diff checks passed. The reproducible `scripts/voice/check_device_decoder_lifecycle.py` supports checkpoint resume and smoke runs. Structured evidence is in `2026-10-01-neural-voice-isolated-decoder.json`. Short-text EOS, general listening quality, normal chat integration and real-time generation remain merge blockers.
