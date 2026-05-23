# QAIRT NPU Editable Prompt One-Shot Hardening Verify

Artifact: `artifacts/qairt244_npu_diagnostic_editable_prompt_one_shot_verify/20260523_191757`
Source runner artifact: `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_191615`

```text
result=success
actual_prompt=Hello
normalized_prompt=Hello
prompt_source=editable_prompt
max_output_tokens=3
output=! How अच्छे
timeout=false
fresh_crash=false
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
final_guard_marker=qairt244_diagnostic_chat_guarded_run_v1 runId=diag-chat-1779531390630-9fd02ef5-822b-410f-b1c4-55631a78c061 state=success elapsed_ms=1038 result=qairt244_editable_prompt_smoke_v1 runId=diag-chat-1779531390630-9fd02ef5-822b-410f-b1c4-55631a78c061 result=success actual_prompt=Hello normalized_prompt=Hello output=! How अच्छे
guard_success_marker_count=1
guard_started_marker_count=0
duplicate_success_marker=false
state_started_residual=false
dev_checkbox_off=true
run_button_disabled=true
after_10s_total_pss_kb=155714
after_10s_native_heap_kb=24597
normal_chatscreen_connected=false
selected_path_npu_normal_route=false
high_level_generateResponse=false
```

## Classification

One-shot hardening passed: the final guarded marker is `state=success`, no
second success marker was added, no residual `state=started` marker remains,
the DEV checkbox is off, and the Run button is disabled after completion.
