# Terminal Trace Schema

File:
`terminal_trace_<runId>.txt`

Location on device:
`files/terminal_trace_<runId>.txt`

Runner copy:
`terminal_trace_<prompt_index>.txt`

Line format:

```text
marker=<marker> timestamp_ms=<epoch_ms> runId=<runId> thread=<thread> process_id=<pid>
```

Throwable lines also include:

```text
exception_class=<class> exception_message=<escaped_message> stacktrace=<escaped_stacktrace>
```

Rules:
- `runId` must match the runner-provided expected runId.
- Stale traces are rejected.
- Previous-run trace files must not be accepted.
- The trace is hidden diagnostic output only and is not UI output.
- Side-effect flags remain false: assistant list, selectedPath, DB, TTS,
  Markdown, and streaming.
