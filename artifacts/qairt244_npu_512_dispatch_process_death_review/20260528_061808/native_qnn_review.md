# Native / QNN Review

Prompt 2 native diagnostics reached:

```text
before RunDecode SetMaxOutputTokens(512) native_max_output_tokens_limit=512 max_output_tokens_limit_marker=qairt244_editable_prompt_max512_v1
```

Prompt 2 did not produce:

- native completed result
- QNN/HTP/FastRPC completed backend evidence
- raw output
- sanitized output
- cleanup elapsed time
- `Engine.close=unique_ptr_cleanup`

There is no saved tombstone, visible `SIGSEGV`, `SIGABRT`, or QNN fatal line
in the reviewed artifacts. The evidence supports a native-worker/process exit
inside or immediately after the pre-RunDecode path, but does not prove a
specific QNN abort.

Conclusion: `native_abort_without_tombstone` remains possible, but the safer
classification is receiver/native-worker process exit with insufficient
terminal instrumentation.
