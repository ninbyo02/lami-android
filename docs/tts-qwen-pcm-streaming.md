# Qwen PCM streaming in the comparison build

Explicitly selecting LAMI/Qwen3-TTS uses PCM produced during code generation and
can start before final PCM decoding completes. Short utterances or uncertain
slow-decoder estimates can use the complete available reserve. The compatibility `createOrNull`
diagnostic entry point retains its existing trial-package/opt-in policy.

The first decodable prefix contains two codec frames, or 160 ms of audio. Later
prefixes arrive every eight frames. One decoder operates at a time, and one PCM
chunk may wait for the player. Live mode preserves the first prefix with a
receive handshake, then retains only the newest waiting cumulative prefix.
Dropping an obsolete waiting prefix loses no codec frames: its successor contains
them all. This avoids unnecessary repeated decoder work when generation is
faster than decoding. Full diagnostic parity replay keeps the original ordered
prefix transport.

Live Qwen playback now uses an adaptive startup reserve instead of playing the
first 160 ms immediately. Its target starts at 640 ms of audio and grows to at
most 2,400 ms from observed PCM arrival gaps plus 160 ms of margin. Codec-prefix
generation cadence is measured separately: producing new codec audio more than
15% slower than its playback duration requests the 2,400 ms target. This prevents
a decoder catching up in a short burst from hiding slower upstream generation.
The target only grows during one utterance and resets on the next. For slow
codec generation, an additional safety gate now waits for codec EOS, which makes
the final length known. It estimates the remaining cumulative decoder work from
warm measurements, including both an in-flight prefix and the final prefix when
needed, with 20% plus 160 ms margin. The cold first forward is excluded. An
overrunning estimate waits for actual decode completion rather than assuming
zero remaining work. The gate is re-evaluated every 20 ms even without a new PCM
chunk. Actual reserve may grow past the normal 2,400 ms target while this gate
waits; the 30-second PCM memory bound still applies. Short utterances are
released at normal EOS without waiting for audio that will never arrive.
Failed EOS and cancellation remain failures.

The startup reserve is submitted before `AudioTrack.play()` and the track's
capacity is at least that reserve. The collector preserves every PCM sample and
reports each original chunk once. It does not add silence, stretch speech or
pause playback to suppress underrun counts. Android and Supertonic keep their
existing startup behavior. More startup buffering trades a later first sound
for fewer mid-speech gaps. The estimate is not a guarantee against unexpected
scheduler stalls or device route changes after playback begins.

After full bundle hash verification, a generation-owned async child now preloads
the decoder while text preparation and codec generation continue. The first
real decode joins that preload. Failure is reported once and ordinary decoder
loading can retry; stopping joins the preload's binding cleanup. Models are
never preloaded before the verification hook succeeds.

The decoder returns a cumulative waveform. Before submitting its new suffix,
`appendedPcm` checks all previously submitted samples. Generic and diagnostic
paths require exact equality. Live playback of the pinned decoder permits only
bounded float32 drift: maximum absolute error at most `1e-5` and RMS at most
`1e-6`. Both checks must pass. Each comparison uses the canonical samples
actually sent to playback; tolerated per-step differences cannot accumulate by
silently replacing that canonical prefix. A larger change fails the request
and stops the coroutine tree. The final natural-EOS prefix is decoded once, including
a remainder shorter than the normal eight-frame interval. A final frame count
equal to the last emitted count does not repeat that prefix. Duplicate or
decreasing intermediate frame counts, or truncated EOS, are rejected.

## Causal decoder boundary

Live streaming is restricted to `speech-decoder-dynamic-et14.pte` SHA-256
`05288ee2c763f80067139019ca66915bad150de705acc940200735f653c1b7c5`, with two to
256 frames, 1,920 samples/frame and 24 kHz mono output. The normal synthesis
session verifies the actual model hashes before emitting the first prefix.
Replacing a decoder manifest entry cannot opt an arbitrary decoder into live
partial playback.

The audited upstream implementation is
`qwen_tts/core/tokenizer_12hz/modeling_qwen3_tts_tokenizer_v2.py` in the local
Qwen3-TTS source checkout. Its decoder uses left-padded causal convolutions,
causal attention masks and causal transposed convolutions with the extra right
tail removed. Its normalizations operate per time step. The exporter in
`scripts/voice/export_dynamic_decoder.py` preserves that decoder and replaces
only the integer padding-size calculation. Existing prefix tests in
`docs/reviews/2026-10-03-neural-tts-prefix-decode*.json` found exact equality for
their tested prefixes; those reports cover a limited corpus and are not new
device measurements.

A new decoder-only x86_64 Android test exposed a finite-precision difference
between 24- and 32-frame inference of the same causal code prefix. The first
16 frames matched exactly; 15,098 later samples changed, with maximum absolute
error `3.7848949432373047e-6` and RMS `1.788268561204451e-7`. No difference
exceeded `1e-5`. This small change is consistent with shape-dependent numeric
kernels, rather than a large future-context boundary change; the causal source
audit does not imply bit-exact floating-point execution for every shape. The
test evidence, original-array hashes and compressed changed sample pairs are
stored in `app/src/test/resources/voice/qwen-prefix24-32-roundoff.*`.

Two additional decoder-only comparisons of the same deterministic code pattern
on that emulator, 32→64 and 64→128 frames, had zero maximum absolute error and
zero RMS over the entire earlier prefix. Their evidence directories were
`qwen-stream-prefix-a10a7c95-1dd9-4faa-8980-c21ad0dc31bd` and
`qwen-stream-prefix-c4af73c4-1bbe-4cc2-9864-310e757cc6b1`. These are limited
shape/input observations; they do not establish bit equality for every generated
utterance or CPU backend.

The live tolerance is scoped to the pinned causal artifact. A replacement PTE
does not inherit it. Regressions exercise the actual captured sample pairs,
excessive peak error, excessive RMS error, nonfinite values and cumulative drift.
Any failing live comparison captures both complete PCM arrays, current codec
codes and per-frame numeric statistics under app-private
`files/diagnostics/qwen-stream-prefix-<id>` before returning the error.

## Timing and cancellation

Typed source timestamps distinguish generation start, PCM chunks, playback
start, final PCM production and playback completion. `GENERATION_COMPLETED`
means all final PCM has been decoded, rather than only reaching codec EOS.
`stage=stream_eos` separately records the latter. The playback-start event is
the AudioTrack play-return boundary, not microphone-measured acoustic onset.

Stopping cancels generation, decoding and playback together. The speech pipeline
waits for child cleanup before microphone handoff or switching engines. Native
main-model computation cannot be interrupted inside one forward call; cleanup
waits for it to return. Decoder IPC cancellation releases its isolated service
binding through the existing decoder lifecycle.

Normal Qwen EOS now signals `AudioTrack.stop()` after every final write is
accepted. In MODE_STREAM this drains queued PCM; it is separate from a user
cancel, which pauses/flushes immediately. The stop-before-drain convention is
also used by [Media3's AudioTrack output](https://raw.githubusercontent.com/androidx/media/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioTrackAudioOutput.java).
Public head/timestamp tracking resets or becomes unavailable after stop, so a
monotonic frame clock retains the stop-time queued frames and a valid pre-stop
presentation timestamp, plus a drain guard, before releasing the track. The
[Android stop contract](https://developer.android.com/reference/android/media/AudioTrack#stop())
and [Media3 frame tracking](https://raw.githubusercontent.com/androidx/media/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioTrackPositionTracker.java)
are the reference. This is playback timing, not microphone-measured acoustic
completion. Cancellation during drain flushes the remaining tail.

The debug trace now samples submitted/head frames and native underrun counts
during writes, chunk waiting, source EOS and drain completion. Native deltas
are preserved; no constant is subtracted and no underflow is relabeled as zero.

This remains cumulative decoding, rather than a stateful incremental decoder.
Coalescing bounds stale work but does not eliminate repeated prefix computation.
Existing device measurements had underruns, and playback now exposes the count;
continuous real-time playback or a new physical-phone speedup is not established
by enabling this path. The fake-engine tests verify early playback, exactly-once
tails, prefix integrity, bounded transport and cancellation joining independently
of model speed.

## Startup reserve validation, 2026-11-10

The old and updated APKs were each run after booting/rebooting the same API 36.1
x86_64 AVD, with the same supplied models and one sentence containing no split
boundary: `こんにちはラミィがお手伝いします`.

| Preview metric | Before | After |
| --- | ---: | ---: |
| First PCM | 17,266 ms | 14,714 ms |
| Playback-start notification | 17,302 ms | 20,831 ms |
| Final PCM generation complete | 22,359 ms | 22,369 ms |
| Playback complete | 22,866 ms | 23,857 ms |
| AudioTrack underruns | 4 | 1 |

The updated run reserved 61,440 samples (2,560 ms of audio) before starting;
its measured reserve wait after first PCM was 6,116 ms. It still started
1,538 ms before final PCM generation completed. Both runs returned the same
38 codec frames, 72,960 PCM samples and identical codec and PCM SHA256 values.
Thus the change in this comparison did not alter the generated waveform.

This is one controlled virtual-device pair, not an acoustic listening test or a
physical-phone performance result. The
[Android underrun counter](https://developer.android.com/reference/android/media/AudioTrack#getUnderrunCount())
reports buffer underflows and potential glitches, not a count of audible cuts.
One reported underrun remained at that stage, before the subsequent EOS fix and
remaining-work gate. Artifacts, APK hashes, exact conditions and logs for that
historical pair are in `artifacts/qwen-smooth-streaming-20261110/`.

## Normal EOS and real starvation

A real AudioTrack test with a complete 600 ms waveform reproduced one native
underflow after normal drain in the previous implementation: the source-EOS
snapshot had all 14,400 samples submitted and raw count zero, while the final
head reached 14,400 with raw count one. The explicit-EOS path returned the same
sample count with raw count zero and waited for the queued tail before release.
A second test intentionally delayed the next chunk for 700 ms after a 160 ms
chunk. That genuine gap still produced a nonzero native count before EOS, and
the final stats preserved it. This verifies the counter has not been hidden by
subtracting the normal-ending observation.

The new unit tests cover remaining-work prediction, EOS wakeups, frame-clock
draining, stop-time head reset, cancellation/failed-EOS flushing and preserving
real native underflow increments. Logs and real-model cold/warm/longer-sentence
checks are saved in `artifacts/qwen-eos-continuity-20261110/`. These remain
virtual-device measurements; there is no physical-phone listening result.
