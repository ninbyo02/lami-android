# Next step toward real-time speech

PR #2712 merged into main as 2e67ec85. Its CP workspace reuse reduces allocation churn, but measured warm completion medians of 16.346 s versus 16.326 s did not establish a latency benefit.

The next target is earlier playback within a sentence: retain the complete sentence as model text input, release codec frames as generated, decode prefixes and append only the newly decoded PCM to playback. Preserve text conditioning and intonation. Begin with an explicit diagnostic using an eight-frame initial chunk (0.64 s of audio), bounded queues and cancellation that stops producer, decoder and playback together. Do not enable normal chat streaming before measuring first audio, buffer underruns and total generation cost.

This PR adds a numerical prerequisite diagnostic, not streaming playback. Two Japanese sentences (15 and 26 frames) were tested cold/warm. Twelve prefix decodes of 8/12/16/24 frames matched the corresponding full PCM samples with max absolute difference and RMS both zero, even without trimming the tail. Fifty-six prefix/tail comparisons passed. Full codec/PCM hashes matched across repeats. Original primary APK restored and SHA-256 verified. See neural-tts-prefix-decode-device.json for raw data.

The result supports progressive prefix decoding for these samples, but longer text and varied punctuation remain necessary coverage. Prefix decodes currently repeat computation over old frames, and generation is slower than playback. Therefore partial playback alone cannot guarantee continuous speech. Measure generation/decode overlap before choosing the initial playback buffer; optimize model execution in parallel, starting with the measured dominant main/CP native forward work. State-retaining decoder exports are a later candidate if repeated-prefix computation proves costly.

Reproduction: launch LamiNeuralVoiceDiagnosticActivity with lami_neural_tts_pipeline_probe="こんにちは。|今日は良い天気ですね。", lami_neural_tts_serial_baseline=true and lami_neural_tts_prefix_decode_probe=true. Provisional prefix audio is never played; normal chat behavior remains unchanged.

Validation: 24 focused tests, Standard Debug/Release Kotlin compilation and Standard Debug device assembly passed. No NPU concurrent chat or actual streaming latency test was performed.
