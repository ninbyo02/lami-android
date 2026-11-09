# Supertonic 3 / Kokoro comparison

This debug branch adds Supertonic 3 to the existing local TTS experiment. It reuses
`LamiVoiceEngine`, the one-clause-ahead pipeline and AudioTrack playback. The
shared implementation is now `LamiNeuralPcmVoiceEngine`; Qwen remains the normal
selection unless the user explicitly opts into one of the experimental engines.

## Verified interface and assets

The existing `sherpa-onnx-1.13.6.aar` contains the real Supertonic classes and JNI.
No new Android dependency is required. The adapter supplies a concrete callback
class with the typed boxed-return `invoke(float[]): Integer` ABI required by
this JNI; Kotlin 2.3 invokedynamic lambdas cannot be passed directly. The selected int8 bundle is pinned to
`csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11` revision
`cca5a0e6c96e1d2c720986bf7e75fcc81dee3ae4` (145,295,768 bytes in seven required
files). Canonical bundle hash:
`6197735e54412143817dde72a3fb0a5eaa6884360bc94c3afb2950a913dfc201`.

`voice.bin` speaker slices were compared byte-for-byte against the original ten
preset JSON files: sid 0..4 = F1..F5, sid 5..9 = M1..M5. The debug screen offers
F1..F5, and a 1..30 denoising-step setting (default 5). Generation uses Japanese
`ja`, speed 1, seed 42 and zero added interchunk silence. These are stock voices;
the public local package does not include a reference-audio voice encoder.

Native output is **44,100 Hz**. The adapter uses a 128-tap Blackman-windowed sinc
filter with anti-aliasing to convert it to the existing 24,000 Hz mono float PCM
contract. Tests cover DC/duration, speech-band gain and rejection of 12.1, 12.5 and 18 kHz
source tones above the target Nyquist frequency. Resampling time and any clipped
samples are recorded separately.

## Debug comparison screen

Build using the SDK setup in [the Kokoro experiment](kokoro-litert.md). The
ordinary debug application ID is `io.github.ninbyo02.lami.assistantprobe`.

```sh
adb -s SERIAL shell am start \
  -n io.github.ninbyo02.lami.assistantprobe/io.github.ninbyo02.lami.KokoroBenchmarkActivity \
  --es engine supertonic3
```

The screen switches between Kokoro and Supertonic 3, synthesizes/saves/plays,
selects a stock female voice and inference steps, stops, and opts the selected
engine into chat on the next initialization. Selecting either disables the other
experiment. Restoring normal selection disables both. Model files are external
to the APK, in app-private `files/supertonic3` (all seven files), staged through
`adb run-as` by the companion collector. Never create app-owned external model
folders as the shell user.

Automated extras add `engine=supertonic3`, `model_dir`, integer `sid`, `steps`,
`threads` to the existing text/text_id/request_id/repeats/prewarm/play contract.
Output is external files `supertonic3/bench/REQUEST/{metrics.jsonl,repeat-N.wav,done.json}`.
Every repeat in a run uses the same loaded model. The model hash covers canonical
sorted filename/size/SHA256 JSON, matching the pinned host manifest.

## Timing and interruption limits

The native first callback is 44.1 kHz and is not necessarily the full utterance.
`native_synthesis_completed_at_ns` records the complete generated waveform;
`lami_pcm_ready_at_ns` and `synthesis_completed_at_ns` include 24 kHz conversion.
`first_pcm_at_ns` is not acoustic onset. `play_return_at_ns` is only the return
from `AudioTrack.play()`. Compare matching text and complete-PCM boundaries when
comparing this backend with Kokoro; the diagnostic multi-sentence path differs
from normal chat's short initial clause.

Sherpa 1.13.6 Supertonic has **no native cancel API**, and its chunk callback return
value is ignored. Stop cancels playback and discards the cancelled synthesis
result, but microphone handoff and close wait for the current native generation
to finish. Release is serialized after generation; calling release/free while
native generation runs would delete its raw pointer unsafely. Do not promise
instant inference interruption. Short-clause latency must be measured on the
actual target phone.

## Evidence and license scope

The companion `/home/sato/project/lami-supertonic-voice` holds immutable source
snapshots, model hashes, five-voice Japanese listening WAVs, benchmark tooling
and notices. The Voice Lab adds the same six exposed development sentences; the
23 reserved final quality texts remain unused.

Code and weights have different terms. Sample code is MIT; original Supertonic 3
weights use OpenRAIL-M. The converted bundle's MIT notice originates from copying
the sample-code license and does not replace the original weight terms. Notices
are preserved in the companion research directory. The official source archive
states that development/support and hosted Voice Builder services have ended;
the archived local files remain usable for this offline comparison.

Primary sources: [archived implementation](https://github.com/supertone-oss-archive/supertonic),
[sherpa Supertonic API](https://k2-fsa.github.io/sherpa/onnx/tts/supertonic.html),
[original model license](https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE).
No physical-device speedup, Qwen speedup or voice-clone quality is established by
host/emulator results.

## Validation

The debug APK builds with the existing dependencies. The common engine/pipeline/PCM
contract, resampler and JNI callback ABI regression suites pass 28 tests. Japanese
Supertonic F1 synthesis and AudioTrack playback pass twice on an API35 x86_64
emulator; its canonical bundle hash matches the host manifest. This is integration
evidence and establishes no physical Snapdragon speed claim.

Voice Lab contains six matching exposed texts across 12 variants (72 audio files):
original Qwen reference, six Kokoro experiments and five Supertonic3 female presets.
The new 30 audio files use native 44.1 kHz, five inference steps and seed 42. Their
values are audition samples; perceptual preference and LAMI voice resemblance are
unreviewed.
