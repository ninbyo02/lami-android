# QAIRT244 NPU 512 per-run isolated gate

Date: 2026-05-27

Scope: documentation and gate design only. No additional NPU execution, native
guard change, QAIRT rebuild, ChatScreen promotion, assistant-list insertion,
DB, TTS, Markdown, streaming, selectedPath=NPU persistence, release behavior,
or standard behavior change was performed.

Evidence inputs:

- sequential timeout review:
  `artifacts/qairt244_npu_512_code_timeout_root_cause_review/20260527_065926/`
- force-stop comparison:
  `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/`
- 256 baseline-candidate reference:
  `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/`

Gate decision:

- `max_output_tokens=512` is not accepted as a sequential hidden baseline.
- `max_output_tokens=512` may be reviewed as `mode=per_run_isolated` only.
- `mode=per_run_isolated` requires force-stop before and after every prompt.
- H1 and normal ChatScreen remain unchanged.
- 256 remains the hidden experimental baseline candidate.
- 1024, 2048, and 4096 remain blocked.
