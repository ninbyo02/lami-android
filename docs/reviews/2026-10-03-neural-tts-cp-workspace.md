# CP workspace reuse

The arbitrary-text synthesizer previously allocated two direct KV buffers for every audio codec frame. Each buffer contains 5 × 8 × 32 × 128 floats (655,360 bytes); the pair is 1.25 MiB. A 26-frame sentence allocated 32.5 MiB of direct KV storage cumulatively, excluding tensor wrappers.

Reuse one CP Decoder for a sentence and zero both input buffers before each frame. Model invocation order, sampling seed, logits, EOS criteria and PCM decoder remain unchanged. Keep the fixed golden probe on its original allocation path as a reference. A lazily allocated 0.625 MiB zero block services both reusable buffers. The cp_reset metric measures reset work, including first allocation.

This reduces allocation churn; it does not establish a latency or peak-memory improvement. Existing pipeline, queue and response-session tests verify surrounding scheduling. Device comparison is now complete: seven two-sentence runs (14 utterances), with matching codec codes and raw PCM SHA-256 across both modes. Warm completion medians were 16,346 ms for allocation baseline and 16,326 ms for reuse; codec medians were 8,996 ms and 8,969 ms. These differences do not establish a material latency benefit. Peak memory and concurrent NPU chat remain untested. See the device-comparison JSON for raw metrics.

Validation: Standard Debug/Release Kotlin compilation passed; 14 focused pipeline, queue and response-session tests passed.

Diagnostic reproduction: launch LamiNeuralVoiceDiagnosticActivity with lami_neural_tts_pipeline_probe="こんにちは。|今日は良い天気ですね。", lami_neural_tts_serial_baseline=true, and lami_neural_tts_cp_allocation_baseline=true (original allocation) or false (reuse). Both hashes are calculated in the same test APK. Restored the current primary APK after testing and verified its SHA-256.

Updated validation: Standard Debug/Release Kotlin compilation and 20 focused tests (including seeded codec sampling) passed. Standard Debug device APK assembled successfully. Main/CP native forward and CP matrix heads account for most work; workspace allocation is not the dominant latency bottleneck in this test.
