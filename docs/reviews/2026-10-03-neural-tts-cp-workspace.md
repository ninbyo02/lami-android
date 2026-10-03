# CP workspace reuse

The arbitrary-text synthesizer previously allocated two direct KV buffers for every audio codec frame. Each buffer contains 5 × 8 × 32 × 128 floats (655,360 bytes); the pair is 1.25 MiB. A 26-frame sentence allocated 32.5 MiB of direct KV storage cumulatively, excluding tensor wrappers.

Reuse one CP Decoder for a sentence and zero both input buffers before each frame. Model invocation order, sampling seed, logits, EOS criteria and PCM decoder remain unchanged. Keep the fixed golden probe on its original allocation path as a reference. A lazily allocated 0.625 MiB zero block services both reusable buffers. The cp_reset metric measures reset work, including first allocation.

This reduces allocation churn; it does not establish a latency or peak-memory improvement. Existing pipeline, queue and response-session tests verify surrounding scheduling. Before adoption, run the fixed voice probes and compare codec codes/PCM against the original path, then repeat the device sentence latency test. Device speed, numerical parity and voice quality remain unverified for this change.

Validation: Standard Debug/Release Kotlin compilation passed; 14 focused pipeline, queue and response-session tests passed.
