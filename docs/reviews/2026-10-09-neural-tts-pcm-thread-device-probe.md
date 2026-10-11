# Fixed-code Android PCM thread diagnostic

Explicit debug activity extra `lami_neural_tts_pcm_thread_probe=true`, with pipeline texts and serial baseline. Generate each sentence once; retain the exact codes. Decode with default settings for the reference, then three alternating trials of 1/2/4 threads, one warmup and two measured decodes per setting. All warmup and measured PCM arrays must match the default output bit-for-bit. Each setting reloads the model when its cache identity changes; measured calls reuse the loaded model. The decoder runs in the existing :voice_decoder process, so its global thread pool does not affect the main/CP process.

The decoder metric reports requested threads (0 means library default), model load time, PCM forward including output extraction, and process total. It does not report effective core placement or device frequency. The explicit probe repeats expensive work and is not synthesis-latency evidence. Normal decode calls retain default settings. Use scripts/voice/summarize_pcm_threads_device.py only on a complete restored/hash-verified capture.

CPU smoke APK assembled successfully with allowMissingQairt244Jni=true. No NPU result follows from this build. Device capture directory: /home/sato/project/lami-android-voice-dataset/device/pcm-threads-20261009. Fresh APK/manifest/WAV/two-probe backups and finally-based restoration are required. No duplicate Voice Lab clips are needed for identical audio.

## Fixed-shape preparation

export_dynamic_decoder.py now accepts --fixed-frames 8 or 16 and refuses to overwrite an existing output. Fixed shapes are CPU export preparation, not a QNN/NPU model. The fixed-8 portable CPU export completed (456514276 bytes); host waveform comparison is a separate check. Independent chunks must preserve decoder context and boundaries; fixed-shape export alone does not establish streaming correctness.

The actual decoder source additionally includes Attention/Transformer blocks, Conv1d, ConvTranspose1d and SnakeBeta (sin-based activation). Audit the exported operator graph and QNN partitions instead of treating it as convolution-only.
