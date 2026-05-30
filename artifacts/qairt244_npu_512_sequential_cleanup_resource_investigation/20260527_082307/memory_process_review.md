# Memory and process review

## Sequential 512

After prompt 1, the process remains alive:

```text
pid=467
TOTAL PSS=300966 KB
Native Heap=84560 KB
Activities=1
```

The Python prompt times out. The runner force-stops the app on timeout, and
the after-python meminfo reports no process. Prompt 3 then starts a new
process and succeeds:

```text
pid=1692
after_lami_npu_short TOTAL PSS=299653 KB
after_lami_npu_short Native Heap=82556 KB
after_10s TOTAL PSS=275865 KB
after_10s Native Heap=53864 KB
```

This is not a retained-memory rollback because memory drops after 10 seconds,
but it shows that sequential prompt 2 starts after a warm prompt-1 process
with roughly 300 MB PSS and 84 MB native heap.

## Per-run isolated 512

Before every prompt, meminfo reports no process after pre-run force-stop.
After every prompt, the runner force-stops again and after-10s meminfo reports:

```text
No process found for: io.github.ninbyo02.lami
```

The code prompt succeeds in this mode with:

```text
decode_elapsed_ms=12448
cleanup_elapsed_ms=130
Engine.close=unique_ptr_cleanup
```

## Interpretation

The evidence points to process/runtime inheritance rather than a persistent
global memory leak. The process is alive between sequential prompts and removed
between isolated prompts. That makes warm-process runtime state, activity state,
or native/QNN resource reuse the most useful next boundary to test.
