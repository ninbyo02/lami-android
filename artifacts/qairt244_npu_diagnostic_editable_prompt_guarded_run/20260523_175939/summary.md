# QAIRT 2.44 NPU Diagnostic Editable Prompt Guarded Run

Artifact: `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_175939`

## Outcome

```text
requested_prompt=Hi
run_executed=false
result=not_run
output=not_run
editable_prompt_native_supported=false
kotlin_supportsEditablePromptExecution=false
native_short_multitoken_prompt=fixed_hi
preflight_result=blocked_native_fixed_hi
run_button_should_be_clicked=false
engine_initialize=false
run_decode=false
npu_generation=false
qairt244_editable_prompt_preview_v1
input_enabled=true
editable_prompt_preview=true
editable_prompt_execution_extra=true
native_editable_prompt_supported=false
value=Hi
isValid=true
reasonCode=ok
normalizedPrompt=Hi
message=OK
prompt_execution_connected=false
prompt_source=fixed_hi
run_button_uses_fixed_prompt=Hi
run_button_connected=false
max_output_tokens=3
npu_generation=false
engine_initialize=false
run_decode=false
timeout=false
fresh_crash=false
normal_chatscreen_connected=false
selected_path_npu_normal_route=false
```

## Classification

The current native short multi-token entrypoint is fixed to prompt `Hi`; editable prompt execution is therefore preflight-blocked. RUN was not tapped and no NPU generation was started.
