# Gate definition

## Required mode

```text
max_output_tokens=512
mode=per_run_isolated
execution_isolation=force_stop_before_after_each_prompt
timeout_seconds_per_run<=60
run_count_policy=approved_prompts_once_each
```

This gate is intentionally separate from the sequential 512 baseline gate.
Sequential execution is not accepted for 512 from the current evidence set.

## Required pass signals

- each prompt must be force-stopped before and after execution
- `RunDecode` must be reached
- `before RunDecode SetMaxOutputTokens(512)` must be present
- `native_max_output_tokens_limit=512` must be present
- `qairt244_editable_prompt_max512_v1` must be present
- `timeout=false`
- `fresh_crash=false`
- `fallback_used=false`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `Engine.close=unique_ptr_cleanup`
- cleanup evidence must be present for every completed run
- memory after 10 seconds must show no process after force-stop, or no high
  retained memory if the process is still present
- code-aware sanitizer must be enabled
- sanitized output must not contain `<start_of_turn>` or `<end_of_turn>`
- Python code prompt must classify as `useful_code`
- Python code indentation must be preserved
- Python code fence must be closed or safely completed
- `selected_path_npu_saved=false`
- `assistant_message_list_inserted=false`
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`

## Rollback conditions

- sequential 512 execution used as a baseline replacement
- any prompt timeout
- missing cleanup or `Engine.close` evidence
- memory high retained after 10 seconds
- code indentation broken
- unclosed or unsafe code fence
- fresh crash
- fallback used
- missing QNN/HTP/FastRPC evidence
- selectedPath=NPU saved
- assistant message list insertion
- DB, TTS, Markdown, or streaming ingress
- max output tokens above 512

## Promotion boundary

Passing this gate allows only hidden `per_run_isolated` evaluation for 512. It
does not promote 512 to H1, normal ChatScreen, release behavior, standard
behavior, or a general sequential baseline.
