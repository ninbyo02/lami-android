# Native stage review

## Sequential 512 failure

Artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/`

- prompt 1 `こんにちは`: pre-RunDecode reached, native success,
  `decode_elapsed_ms=848`, `cleanup_elapsed_ms=124`,
  `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`,
  `Engine.close=unique_ptr_cleanup`
- prompt 2 Python code: pre-RunDecode reached with
  `before RunDecode SetMaxOutputTokens(512)`,
  `native_max_output_tokens_limit=512`, and
  `qairt244_editable_prompt_max512_v1`; no native success line, no
  `decode_elapsed_ms`, no cleanup, no `Engine.close`, no completed backend
  evidence
- prompt 3 Lami NPU: after timeout force-stop/restart, pre-RunDecode reached,
  native success, `decode_elapsed_ms=3989`, `cleanup_elapsed_ms=132`,
  `Engine.close=unique_ptr_cleanup`

## Per-run isolated 512 success

Artifact:
`artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/`

- prompt 1 `こんにちは`: `decode_elapsed_ms=835`, `cleanup_elapsed_ms=126`,
  QNN evidence, `Engine.close=unique_ptr_cleanup`
- prompt 2 Python code: `decode_elapsed_ms=12448`, `cleanup_elapsed_ms=130`,
  QNN evidence, `Engine.close=unique_ptr_cleanup`, `useful_code`
- prompt 3 Lami NPU: `decode_elapsed_ms=4359`, `cleanup_elapsed_ms=142`,
  QNN evidence, `Engine.close=unique_ptr_cleanup`

## Interpretation

The sequential Python prompt enters the correct native entrypoint and passes
the 512 guard, so this is not a guard rejection. The missing native success and
cleanup indicate either decode did not return under the bounded window, or a
native/receiver callback/state update was lost after entering decode. The
isolated success proves the same prompt can complete under 60 seconds when the
process is fresh.
