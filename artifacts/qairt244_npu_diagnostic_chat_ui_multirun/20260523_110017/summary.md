# QAIRT 2.44 NPU Diagnostic Chat UI Multi-Run Stability

Artifact: `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_110017`

## Outcome

```text
classification=script-wait-bug-detected-not-final-stability-proof
run_count_requested=2
prompt=Hi
max_output_tokens=3
run1_result=success
run1_output=! How Hi
run1_elapsed_ms=964
run1_decode_elapsed_ms=64
run1_tombstone_classification=stale-tombstone-ignored
run2_result=success
run2_output=! How Hi
run2_elapsed_ms=2338
run2_decode_elapsed_ms=65
run2_tombstone_classification=stale-tombstone-ignored
timeout=false
fresh_crash=false
normal_chat_screen_connected=false
selectedPath_npu_normal_route=false
high_level_generateResponse=false
streaming=false
```

## Memory Summary

| Sample | TOTAL PSS KB | Native Heap PSS KB |
| --- | ---: | ---: |
| before | unavailable | unavailable |
| after run1 | 257015 | 75668 |
| after run2 | 939257 | 41678 |
| after 10s | 64721 | 17860 |

The two captured result files contain successful NPU outputs and stale tombstone
classification, but the runner's original wait condition accepted an earlier
`state=success` while a later `state=started` marker was still present. The
script has been corrected to wait on the last guarded marker state before a
future rerun.

No normal ChatScreen route was connected and no normal `selectedPath=npu` route
was used. No additional rerun was performed after detecting the wait bug, to
avoid exceeding the requested two-run stability scope in this turn.
