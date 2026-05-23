# QAIRT NPU Diagnostic Fallback Recovery

Artifact: `artifacts/qairt244_npu_diagnostic_fallback_recovery/20260523_193405`

```text
invalid_prompt=contains_disallowed_char
invalid_run_button_enabled=false
unsupported_preflight=blocked_marker_missing_or_artifact_missing
timeout_simulated=true
timeout_engine_initialize=false
timeout_run_decode=false
timeout_run_button_enabled=false
timeout_dev_checkbox_checked=false
recovery_run_button_enabled=false
recovery_dev_checkbox_checked=false
fresh_crash=false
normal_chatscreen_connected=false
selected_path_npu_normal_route=false
high_level_generateResponse=false
```

## Classification

Fallback/recovery checks passed for Diagnostic Chat-only scope. Invalid prompt
and unsupported preflight do not start NPU work. Timeout is simulated through a
DEV extra and does not call Engine.initialize or RunDecode. After timeout and
refresh, the DEV checkbox remains off and the Run button remains disabled.
