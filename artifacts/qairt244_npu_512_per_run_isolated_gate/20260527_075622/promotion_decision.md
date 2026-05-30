# Promotion decision

512 per-run isolated mode is gateable but not promoted beyond hidden
experimental scope.

Allowed after this gate, if separately approved:

- hidden QAIRT244 route only
- `max_output_tokens=512`
- `mode=per_run_isolated`
- force-stop before and after every prompt
- code-aware sanitizer required
- artifact review required before any broader use

Still blocked:

- sequential 512 baseline
- H1 display baseline
- normal ChatScreen
- assistant message list insertion
- DB persistence
- TTS
- Markdown renderer connection
- streaming
- selectedPath=NPU persistence
- release or standard behavior changes
- 1024, 2048, and 4096 expansion

Baseline state:

- 128 remains the H1/display baseline.
- 256 remains the hidden experimental baseline candidate.
- 512 is `extended experimental / per-run isolated candidate`.
