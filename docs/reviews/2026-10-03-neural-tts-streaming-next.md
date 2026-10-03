# Overlapping neural speech diagnostic

Current normal chat prepares and decodes a whole sentence before starting audio. The explicit debug streaming probe now produces cumulative code prefixes every eight frames, decodes them in the isolated decoder process, and appends only newly decoded PCM to one MODE_STREAM AudioTrack. The sentence text, seed and models remain the same. Code and PCM channels each hold at most one pending item. No partial audio is enabled for ordinary chat.

Each new prefix must preserve the previous PCM exactly before new samples are submitted. Natural EOS is required for probe success; failures or cancellation stop the coroutine tree and release the track. Already played diagnostic audio is provisional. The decoder keeps its existing bounded 45-second idle cache; cancellation during a native decode uses its existing binding release path. Nonblocking AudioTrack writes poll cancellation. After initial prebuffering, the restart threshold becomes one frame so a final remainder shorter than eight frames can resume after an underrun.

## Device results

Two Japanese texts (15 and 26 codec frames) were tested with one cold primer and two warm repetitions per mode in the same APK. All ten complete utterances had matching codec/PCM SHA-256. Cumulative prefix stability and final full-decode comparison passed. First-sentence AudioTrack start delay was 4.994–5.916 s for whole-clip playback and 2.951–3.250 s for streaming. Second-sentence start delays were 8.147–11.591 s and 3.219–3.539 s respectively. These are observed intervals, not a general performance guarantee or microphone onset measurement.

Streaming had one underrun for each 15-frame sentence and three for each 26-frame sentence. First-sentence playback occupied 2.170–2.329 s for 1.200 s of PCM; second occupied 5.220–5.374 s for 2.080 s of PCM. Earlier playback is demonstrated; continuous real-time speech is not. Prioritize the measured main/CP native-forward compute bottleneck before enabling normal chat. A larger initial buffer is a later tradeoff to measure, rather than presenting interrupted audio as a completed feature. Full post-playback comparison adds diagnostic cost.

Stopping through the existing exported diagnostic activity stop intent during playback cancelled the probe within 303 ms in this test, and its foreground service disappeared. The current APK was restored and its SHA-256 matched. Evidence: 2026-10-03-neural-tts-streaming-device.json.

## Reproduction and validation

Launch LamiNeuralVoiceDiagnosticActivity with lami_neural_tts_pipeline_probe="こんにちは。|今日は良い天気ですね。", lami_neural_tts_serial_baseline=true, and lami_neural_tts_streaming_probe=true for streaming or false for baseline. Stop through a fresh diagnostic activity intent with lami_neural_tts_stop_probe=true. Prefix-only numerical comparison is still available through lami_neural_tts_prefix_decode_probe=true.

Standard Debug device assembly, Standard Release Kotlin compile and 27 focused tests passed, covering prefix slicing, PCM difference, suffix assembly, duplicate/changed-prefix rejection, seeded sampling and existing queue scheduling. Short final remainders (seven and two frames) completed on device. Concurrent NPU chat, longer sentences and subjective listening remain untested.
