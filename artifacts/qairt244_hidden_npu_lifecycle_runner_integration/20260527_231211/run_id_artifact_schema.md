# Run-Id Artifact Schema

Required schema for future runner/preflight artifacts:

```text
runId=<current-run-id>
state=started|success|failure|timeout
result=success|failure
result_written_at_ms=<epoch-ms>
cleanup_elapsed_ms=<ms>
Engine.close=unique_ptr_cleanup
assistant_message_list_inserted=false
selected_path_npu_saved=false
db=false
tts=false
markdown=false
streaming=false
```

Native diag must carry either the same run id or remain tied to a run-id scoped
file name. If it includes a run id, it must match the expected run id.

Accepted file naming pattern:

```text
qairt244_hidden_npu_<runId>_state.txt
qairt244_hidden_npu_<runId>_result.txt
qairt244_hidden_npu_<runId>_native_diag.txt
qairt244_hidden_npu_<runId>_cleanup.txt
```

Any mismatch is rejected as `RUN_ID_MISMATCH_REJECTED`.
