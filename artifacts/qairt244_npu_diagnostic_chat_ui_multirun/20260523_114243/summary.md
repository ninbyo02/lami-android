# QAIRT 2.44 NPU Diagnostic Chat UI Multi-Run Stability

Artifact: `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243`

## Outcome

```text
run_count=2
prompt=Hi
max_output_tokens=3
run1_result=success
run1_output=! How Hi
run1_elapsed_ms=1907
run1_decode_elapsed_ms=96
run1_last_guard_marker_state=success
run1_state_started_final=false
run1_tombstone_classification=stale-tombstone-ignored
run2_result=success
run2_output=! How Hi
run2_elapsed_ms=1661
run2_decode_elapsed_ms=70
run2_last_guard_marker_state=success
run2_state_started_final=false
run2_tombstone_classification=stale-tombstone-ignored
timeout=false
fresh_crash=false
button_double_run=false
running_state_released=true
normal_chat_screen_connected=false
selectedPath_npu_normal_route=false
high_level_generateResponse=false
streaming=false
```

## Memory Summary

| Sample | TOTAL PSS KB | Native Heap PSS KB |
| --- | ---: | ---: |
| before | 53135 | 6 |
| after run1 | 583312 | 72093 |
| after run2 | 142283 | 43051 |
| after 10s | 78536 | 20571 |

Deltas versus `before`:

```text
after_run1_total_pss_delta_kb=+530177
after_run2_total_pss_delta_kb=+89148
after_10s_total_pss_delta_kb=+25401
after_run1_native_heap_pss_delta_kb=+72087
after_run2_native_heap_pss_delta_kb=+43045
after_10s_native_heap_pss_delta_kb=+20565
```

This is a two-run diagnostic-only UI stability check. The script taps the DEV checkbox once, taps the guarded run button once per run, waits between runs, and does not touch the normal ChatScreen route.

The `after_10s` sample is below both immediate post-run samples for TOTAL PSS and Native Heap PSS. This is a bounded two-run baseline, so it is not used to claim or exclude a leak by itself.
