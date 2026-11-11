# Resident NPU continuous speech input handoff — 2026-10-15

Debug-only sixteen-step pilot now accepts an optional immutable private file
resident-cp-16-embeddings.f32 (16 rows x 1024 f32) and companion lowercase
SHA-256 sidecar, both validated before the native context is touched. It
retains the known-good fixed-row smoke path when no variable input is present.
A new row is supplied for every position 0..15, and the same native context
retains the K/V cache. No normal TTS routing, audio, or playback is enabled.

Next: capture the real position-specific embeddings from existing CPU-main,
original codec head and embedding matrices. Validate that the sixteen rows
correspond to the same codec frame, and compare final output/KV and timing
against independent qnn-net-run reference for those exact rows. Finally make
code-head sampling interactive with a resident NPU session instead of a
pre-collected row set; decoding actual speech follows this validation.
