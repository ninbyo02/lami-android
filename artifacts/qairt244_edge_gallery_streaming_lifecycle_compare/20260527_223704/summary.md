# QAIRT244 Edge Gallery Streaming Lifecycle Compare

Date: 2026-05-27

Scope: static investigation and documentation only. No Lami code was changed,
no NPU run was started, no native code was changed, no QAIRT or LiteRT-LM
rebuild was performed, and no 1024/2048/4096 expansion was attempted.

Inputs:

- Google AI Edge Gallery checkout:
  `/home/sato/project/google-ai-edge-gallery`
- LiteRT-LM checkout:
  `/home/sato/project/litert-custom-build/LiteRT-LM`
- Lami hidden QAIRT244 artifacts and docs through commit
  `1614a9b332a46178cfdcd7ff34858aa46aa81e1d`

Conclusion:

Edge Gallery should not be copied wholesale into Lami. The useful design
signals are narrower: a per-turn lifecycle wrapper, fully separated callback
and state identifiers, and an explicit close/cancel/cleanup wait contract for
hidden NPU diagnostics. Gallery's streaming renderer and normal chat message
pipeline remain out of scope.

Recommended next design axis:

1. Hidden NPU route session lifecycle wrapper.
2. Per-turn callback/state/result/native-diag id separation.
3. Per-turn engine/session close wait with bounded timeout and suspect-session
   classification when cleanup is missing.

Policy held:

- 256 remains the hidden experimental baseline candidate.
- 512 remains hidden `hidden_per_run_isolated_512` only.
- Sequential 512 and Activity-restart-only 512 remain rollback modes.
- H1 remains pinned to `sanitizer_only + max_output_tokens=128`.
- 1024/2048/4096 remain blocked.
- No normal ChatScreen, assistant-list, DB, TTS, Markdown renderer, streaming,
  or selectedPath=NPU persistence is authorized.
