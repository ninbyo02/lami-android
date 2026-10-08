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
are still pending. No realtime or NPU performance claim is made.
