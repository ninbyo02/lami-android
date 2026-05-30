# Native cleanup review

## Prompt 1

`native_diag_1.txt` records:

- `before RunDecode SetMaxOutputTokens(512)`
- `native_max_output_tokens_limit=512`
- `max_output_tokens_limit_marker=qairt244_editable_prompt_max512_v1`
- `decode_elapsed_ms=858`
- `cleanup_elapsed_ms=122`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `Engine.close=unique_ptr_cleanup`

Lifecycle classification: `SUCCESS_CLEAN`

## Prompt 2

`native_diag_2.txt` records:

- `before RunDecode SetMaxOutputTokens(512)`
- `native_max_output_tokens_limit=512`
- `max_output_tokens_limit_marker=qairt244_editable_prompt_max512_v1`
- `decode_elapsed_ms=13358`
- `cleanup_elapsed_ms=137`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `Engine.close=unique_ptr_cleanup`

Lifecycle classification: `SUCCESS_CLEAN`

## Prompt 3

`native_diag_3.txt` contains no native diagnostic payload. The pulled file
reports that `files/qairt244_native_diag.txt` did not exist. Prompt 3 therefore
has no `RunDecode`, backend, cleanup, or `Engine.close` evidence.

## Conclusion

Prompt 2 completed native decode and cleanup cleanly. The process disappearance
occurred after clean native completion and before or during the prompt 3
attempt. Current artifacts do not show native cleanup directly causing process
exit.
