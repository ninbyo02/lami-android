# Native Stage Review

## Sequential 512 code-aware timeout

Native diagnostics for the Python prompt reached:

- `ENTRY prompt_source=editable_prompt max_output_tokens=512`
- prompt validation `reason=ok`
- `before ModelAssets::Create`
- `before EngineSettings::CreateDefault`
- `before EngineFactory::CreateDefault`
- `before CreateSession`
- `before RunPrefill`
- `before RunDecode SetMaxOutputTokens(512)`

Missing after timeout:

- native `success` line
- `decode_elapsed_ms`
- `cleanup_elapsed_ms`
- `Engine.close=unique_ptr_cleanup`
- completed `npu_backend_evidence`
- completed raw output
- completed sanitized output

## Isolated 512 code retry

The isolated retry reached the same pre-RunDecode stage and then completed:

- `decode_elapsed_ms=11600`
- `cleanup_elapsed_ms=142`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `Engine.close=unique_ptr_cleanup`

Interpretation: the repeated timeout occurs after pre-RunDecode in the
sequential run. The artifact cannot distinguish a very long decode from a lost
native callback after decode; no partial output evidence was written.
