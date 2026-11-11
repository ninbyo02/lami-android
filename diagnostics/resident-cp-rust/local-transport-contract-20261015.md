# Draft contract: isolated resident CP local transport (no server enabled)

The host's CPU main-model and codec sampler need to exchange 1024 FP32 embeddings and hidden states with an Android debug-only resident-QNN context. This change defines only a pure Python wire-format codec, **not** a listening socket, Android service, ADB command, or production API.

Binary framing: fixed little-endian header (magic LCP1, opcode uint8, frame uint8, position uint16, payload_len uint32), followed by at most 4096 payload bytes; RESET has zero bytes; STEP/RESULT exactly 4096 bytes (1024 FP32); ERROR at most 256 bytes. Valid frame indices 0..255; CP positions 0..15. All dimensions must fail closed before native execution. Disallow NaN/Inf at the Android/native boundary. A future session handler must enforce monotonic step positions per frame, reset KV exactly once per frame, finite values, single outstanding request, upper time/byte limits, and release in finally.

A future transport must only be exposed through an explicitly enabled isolated debug package with a loopback-only binding or ADB-local forwarding, a fresh per-session secret, no background discovery, no external network listener, and an opt-in activation lifetime. It must never ship in normal LAMI. Never transfer QNN KV buffers or reload QNN per step. Observe weekday 08:00–19:30 JST ADB restriction.

Validated now: pure Python protocol encode/decode tests. Not yet validated: Kotlin protocol implementation, Android session management, connection authentication, end-to-end multi-frame voice, EOS, WAV or real-time latency.
