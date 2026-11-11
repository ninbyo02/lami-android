# Kokoro LiteRT debug integration

This experiment adds a debug-only opt-in to the existing `LamiVoiceEngine` / 24 kHz
float PCM path. Qwen remains the default. No model is bundled into the APK.

## Runtime and playback

- Uses the public `com.google.mediapipe:tasks-audio:1.2.2` TTS API, verified against
  the published AAR. [API documentation](https://developers.google.com/edge/mediapipe/solutions/audio/text_to_speech).
- Loads a `.litertlm` model once per session, on one dedicated thread, with optional
  prewarm. CPU is the default; GPU requires a compatible graph/backend and device validation.
- Japanese locale `ja`, stock voice `jf_tebukuro` by default. Learned voice trials
  use a copied bundle whose embedded voice section is replaced; an arbitrary
  external `.bin` file can fall back to a stock voice in the native loader.
- Splits at sentence punctuation and, for long clauses, at soft boundaries (first
  clause target 24 characters, later clauses 80). Reuses `LamiSpeechPipeline` to
  prepare one clause ahead while the current clause plays through `LamiPcmPlayer`.
- Each clause's native stream is collected before that clause starts playing.
  `first_pcm_at_ns` is therefore **not** chat playback latency. Direct chunk-to-track
  streaming remains a separate improvement to measure after the basic port works.
- Stop explicitly calls native `cancel()` as well as cancelling the coroutine.
  Microphone handoff waits for old generation/playback cleanup; shutdown closes
  the resident session only after cleanup.

## Build and run

From this worktree, with the Android SDK configured consistently:

```sh
env -u ANDROID_SDK_ROOT ANDROID_HOME=/home/sato/Android/Sdk ./gradlew \
  :app:testStandardDebugUnitTest \
  --tests 'io.github.ninbyo02.lami.tts.LamiNeuralPcmVoiceEngineTest' \
  -Plami.allowMissingQairt244Jni=true

env -u ANDROID_SDK_ROOT ANDROID_HOME=/home/sato/Android/Sdk ./gradlew \
  :app:assembleStandardDebug -Plami.allowMissingQairt244Jni=true
```

The flag is the repository's existing local diagnostic build option; this build
cannot validate the separately staged Qualcomm runtime.

The ordinary debug build uses application ID `io.github.ninbyo02.lami.assistantprobe`
(check `output-metadata.json`; other diagnostic flags use other IDs).
Install the APK on an explicitly selected device, put the verified CPU bundle at
app-private `files/kokoro/kokoro_cpu.litertlm`. Use the companion
`benchmark/collect_android.py --model ... --package io.github.ninbyo02.lami.assistantprobe`
to stage it through `run-as`; creating external app directories as the shell can
leave them unreadable to the app. Then launch the probe:

```sh
adb -s SERIAL shell am start -n io.github.ninbyo02.lami.assistantprobe/io.github.ninbyo02.lami.KokoroBenchmarkActivity
```

The debug screen can synthesize/save/play, stop, select Kokoro for the next chat
engine initialization, and restore normal voice selection. Restart the app after
changing selection. The probe is protected by the shell's DUMP permission.

Automated probe extras: `text`, `text_id`, `request_id` (unique safe filename),
`model_path`, `backend` (`cpu`/`gpu`), `voice`, `prewarm` (boolean), `repeats`
(1–20), `play` (boolean, default false). A single probe repeats using one session.
Outputs are in external files `kokoro/bench/REQUEST/metrics.jsonl`,
`repeat-N.wav`, and `done.json` (`status: ok/error/cancelled`).

## Measurement boundaries

All native timing marks use `elapsedRealtimeNanos`. The first-PCM latency from
`text_ready_at_ns` includes dispatcher waiting, fingerprinting and model loading;
`generation_rtf` starts after loading. `loaded_before_request` reports actual
handle residency, rather than inferring it from request ordinal. A fresh model
handle does not establish a cold filesystem cache.

`play_return_at_ns` records the return of `AudioTrack.play()` when playback is
requested. `audible_start_at_ns` remains null. PSS values are before/after
snapshots, not peak memory or power measurements. Requested backend is recorded;
actual graph placement is unknown unless independently profiled. LLM coexecution
and filesystem cache are marked unknown by this probe. Do not derive an Android
speed ratio from host or emulator timings, or compare different text/cache/LLM
conditions.

The companion experiment directory `/home/sato/project/lami-kokoro-voice` contains
checksum-pinned model/voice artifacts, voice conversion checks, host runners and
same-text benchmark tooling. Its nine speed texts are separate from the 23
reserved quality-test sentences. Existing Voice Lab pilot scores describe audio
loss changes; they are not speed improvements or proof of a successful clone.

## Verification on 2026-11-09

- Debug APK assembled; Kokoro engine, existing pipeline and PCM contract tests:
  23 passed. Converter/patcher: 8 passed. Benchmark harness: 21 passed.
- Official API model-load, stock Japanese synthesis and AudioTrack playback
  completed on a read-only API35 x86_64 emulator. This is integration evidence,
  not Snapdragon performance evidence.
- The same source model and both learned-style copies synthesize on host CPU
  and GPU. Six exposed development texts per variant are in Voice Lab. Native
  logs confirm embedded `jf_tebukuro` and a real Radeon/WebGPU delegate for the
  host GPU runs; there is no proof of perceptual identity similarity yet.
- Host GPU exploratory warm first PCM: 79–85 ms, generation RTF 0.054–0.055
  for a 3.04-second Japanese clip. These are host results and are not a Qwen
  comparison. CPU short-text measurements and all unknown conditions are saved
  under the companion benchmark reports.
- No physical Android device was attached. SM8750 CPU/GPU latency, coexecution
  with the current LLM, peak memory/power, and same-condition Qwen speedup remain
  unmeasured. The debug opt-in is kept off by default.
