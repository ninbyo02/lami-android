# QAIRT244 NPU 512 Code Prompt Repeated Timeout Root Cause Review

Date: 2026-05-27

Scope: artifact/log/runner/docs review only. No additional NPU execution, 512
rerun, 1024+ expansion, native guard change, QAIRT rebuild, ChatScreen
promotion, assistant message insertion, DB, TTS, Markdown, streaming,
selected-path NPU persistence, release/standard behavior change, or
`app/src/main/jniLibs` change was performed.

Reviewed artifacts:

- code-aware sequential rerun:
  `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/`
- isolated 512 code retry:
  `artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116/`
- 256 three-prompt baseline candidate:
  `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/`

Classification:

- primary: `sequential_decode_timeout`
- secondary: `code_prompt_decode_too_long_under_three_prompt_runner`
- possible contributing factors: `cleanup_dependency_between_runs`,
  `thermal_or_resource_slowdown_possible`
- not supported as primary: `runner_wait_condition_too_strict`

Finding: the 512 Python code prompt is unstable. It completed once in isolation
with `timeout_seconds=60`, `decode_ms=11600`, QNN evidence, and cleanup
evidence, but timed out when run second in a three-prompt sequence. In the
timeout case, native diagnostics reached
`before RunDecode SetMaxOutputTokens(512)`, but no native success line,
cleanup timing, `Engine.close`, receiver success, raw output, or sanitized
output was produced before the bounded timeout.

Decision: 512 is not a hidden baseline candidate. 256 remains the hidden
experimental baseline candidate. 1024, 2048, and 4096 remain blocked.
