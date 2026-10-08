# CP head offline packed4 diagnostic

This opt-in debug path directly maps verified packed4 files instead of creating
120 MiB of direct buffer copies and converting heads on every request.
The row-major default and previous in-memory packing control remain available.
Fixed-input benchmarking is rejected in this mode because it requires a
row-major control. Embedding row access is also rejected for packed heads.

`scripts/voice/prepack_cp_heads.py ROOT NEW_OUTPUT` checks the manifest source
hashes and exact sizes, writes 15 isolated heads, and verifies the inverse
transformation against every source byte. It writes a manifest overlay; merge
its sha256 entries and cp_head_prepacked4 flag into a diagnostic bundle only.
The existing bundle verifier validates each selected packed file before mmap.

## Host evidence

All 15 real CP heads (125,829,120 bytes) passed inverse bit equality.
The attached JSON records source and packed hashes. This verifies layout,
not Android output or performance. Device feedback and restoration testing
completed; see the device JSON and evidence below. No realtime or NPU performance claim is made.

## Android device evidence

Same CPU smoke APK, grouped main cache128 and grouped CP cache16 throughout;
in-memory packed4 / direct-mapped packed4 / direct-mapped / in-memory order,
two Japanese texts per run. All eight requests completed and both code and
PCM hashes matched the previously reviewed device clips. No duplicate Voice
Lab clips are added. Original APK, manifest, WAV and both probes were restored
and hash verified; all temporary candidate files were removed.

Direct mapping has no cp_heads_pack calls and avoids the 125,829,120-byte
packing allocation by construction. This is not an RSS measurement. Baseline
packing times and frame timings are recorded in the JSON. Device frequency,
cache state and thermals were not controlled, so overall speed differences
must not be interpreted as an isolated causal improvement. Codec generation
still exceeds the 80 ms/frame budget, with PCM decoding outside that timing.
Default row-major mode remains unchanged; this is a verified diagnostic path.

Measured in-memory packing: 146, 267, 83, 253 ms/request. Direct mapping: no packing calls. Frame p50: baseline 115, 150, 111, 110 ms; direct 106, 112, 104, 106 ms. CP head weighted means: 1.0384 versus 0.8105 ms/call, with the uncontrolled conditions above.
