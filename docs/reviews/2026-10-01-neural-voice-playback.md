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
