# Position-specific genuine codec embeddings on resident QNN (2026-10-15 07:10 JST)

Goal: advance from repeating one synthetic embedding to position-dependent inputs
from real "こんにちは。" voice code generation in an isolated debug-only process.

On CPU pilot, the first 16 calls to cp_step() were captured unmodified from
CPU-main and original codec head + embedding flow. The CPU pilot still generated
the exact same original 15-frame EOS-complete WAV (SHA256
bb4edc83d9224482d1bf5c2828dfdf4b58c78ccc37c9d2cf1a41b5d2a086cd39).
16x1024 Float32 bytes 65,536, input SHA256
e19030ca5b95cb5ff0cf46f95cdd72ddf5d845420b5628c192f9e489ed183b94.
The new debug-only Activity validates both byte count and sidecar SHA-256
before running, with the old fixed-input probe fallback preserved.

SM8750 real device receipt:
status=SIXTEEN_STEPS_RETURNED; positions_completed=16;
total_step_ns=232089587 (mean 14.5056ms per step);
context_load_ns=345768125; kv_reset_ns=925052;
last_output_sha256=181525dd3a753b80a972e9817f0dd99a8495f9d4dee3771cb92115b1e409178c;
native_stats=1,16,24,1,0,122129323,200823489;
reference_checked=false; audio_played=false.

This PROVES only an isolated resident 16-step variable-row CP computation
with cached K/V and one context load. It does NOT yet demonstrate a live
CPU-main -> NPU hidden -> head sampling -> next embedding closed loop,
accurate output vs independent QNN for these rows, audio decoding, or actual
end-to-end generation / first-audio latency. Existing normal Android LAMI,
trained voice and other GPU workflows remain untouched.
