# QAIRT 2.44 Initialize Stability Result

Date: 2026-05-23

Scope: `customBuildExperimentDebug` explicit initialize-only probe. No normal UI
NPU route was changed. No `Conversation`, `Session`, `generateResponse`, or
token generation was run.

## Script

```text
scripts/run_qairt244_initialize_stability_probe.sh
```

The script stages the approved QAIRT 2.44 diagnostic stack, builds and installs
`customBuildExperimentDebug` once, then launches the probe Activity up to two
times with only:

```text
run_engine_initialize_dry_run=true
```

Each run clears app diagnostic files, calls `Engine.initialize`, calls
`Engine.close`, and collects app-private diagnostics.

## Artifact

```text
artifacts/qairt244_initialize_stability/20260523_043345/
```

The first script revision reported `crash_marker=true`, but inspection showed
the file was the normal in-progress marker updated to `completed=true`, not a
crash. The script was corrected to treat a marker as crash-suspect only when it
contains `completed=false` without a later `completed=true`.

Corrected artifact summary:

```text
artifacts/qairt244_initialize_stability/20260523_043345/corrected_summary.md
```

## Runs

| Run | Initialize | Close | Elapsed | Compatibility | QNN/HTP summary |
| --- | --- | --- | --- | --- | --- |
| 1 | success | success | 1764 ms | `kLiteRtStatusOk(0)` | `QnnDevice_create` logged `status 0x0`; V79 stub connected; FastRPC transport ran. |
| 2 | success | success | 1527 ms | `kLiteRtStatusOk(0)` | Same process reused initialized QNN state; compatibility remained OK and cleanup completed. |

Evidence:

```text
run_1/stage_file.txt: Engine.initialize returned
run_1/stage_file.txt: Engine.close returned
run_2/stage_file.txt: Engine.initialize returned
run_2/stage_file.txt: Engine.close returned
```

Both probe snapshots report:

```text
initialize returned=yes
initialize result=success
close invoked=yes
close result=success
crash suspected=false
warning=initialize-only; no Conversation; no generateResponse; not wired to app inference
```

## Classification

- initialize-only reproducibility: `2/2 success`
- `Engine.close`: `2/2 success`
- install count: `1`
- repeated install requirement: not needed
- tombstone/crash: none observed by probe state and log review
- `LiteRtDispatchCheckRuntimeCompatibility`: reached and OK
- QNN/HTP: run 1 shows V79 stub/FastRPC path active; run 2 remains compatible
  in the same app process

This confirms NPU initialization stability only. It is not an inference proof.

## Safety

Not executed:

- `Conversation`
- `Session`
- `generateResponse`
- token generation
- normal UI NPU route
