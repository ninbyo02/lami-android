# Neural voice diagnostic: 2026-10-01

The exact phrase `はい。` now has an Android diagnostic path through the
28-layer INT4 talker, FP32 code predictor, speech decoder and PCM AudioTrack.
This is a first-utterance integration, **not arbitrary-text neural speech in chat**.
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
- Arbitrary-text tokenizer and prepared embedding generation on Android.
- A decoder supporting variable output duration and EOS.
- Optimized native matrix heads and measured time to first audio.
- Neural `LamiVoiceEngine.speak()` integration and queue/replacement behavior.

No large PTE, context or tensor artifacts are tracked. The original experimental
working tree and unrelated QAIRT/model changes are preserved.
