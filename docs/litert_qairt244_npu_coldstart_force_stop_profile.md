# QAIRT 2.44 NPU Cold-Start Force-Stop Memory Profile

Date: 2026-05-23

Scope: `customBuildExperimentDebug` only. This is Android runtime memory,
native heap, and process cleanup profiling around one isolated QAIRT NPU
short multi-token smoke. It is not Lami short-term or long-term memory work.

## Artifact

```text
artifacts/qairt244_npu_coldstart_force_stop_profile/20260523_092801/
```

Script:

```text
scripts/run_qairt244_npu_coldstart_force_stop_profile.sh
```

Nested smoke artifact:

```text
artifacts/qairt244_short_multitoken_smoke/20260523_092803/
```

## Smoke Outcome

```text
result=success
prompt=Hi
max_output_tokens=3
output=! How Hi
elapsed_ms=1572
prefill_elapsed_ms=28
decode_elapsed_ms=86
cleanup_elapsed_ms=103
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
tombstone_classification=stale-tombstone-ignored
```

The run used the existing isolated lower-level short multi-token smoke path. It
did not connect NPU to the normal UI, did not set `selectedPath=npu`, did not
call high-level `generateResponse`, did not stream, and did not exceed
`maxOutputTokens=3`.

## Cold-Start State

```text
pid_before_force_stop=31094
pid_after_force_stop=none
meminfo_after_force_stop=No process found
cold_start=true
```

The diagnostic package had a previous process before the initial `force-stop`.
After `force-stop`, `pidof` returned no process and `dumpsys meminfo` returned
`No process found`, so the smoke launch was a cold process start.

## Memory Samples

```text
sample                         total_pss_kb  native_heap_pss_kb  dalvik_heap_pss_kb  meminfo_no_process
after_force_stop               NA            NA                  NA                  true
after_smoke                    133138        14297               1235                false
after_3s                       133691        14359               1052                false
after_final_force_stop_3s      NA            NA                  NA                  true
after_final_force_stop_10s     NA            NA                  NA                  true
```

Because the pre-run and post-run force-stop samples have no process, app PSS
cannot be expressed as a numeric delta at those points. The expected cleanup
boundary is process removal:

```text
pid_after_final_force_stop_3s=none
pid_after_final_force_stop_10s=none
meminfo_after_final_force_stop_3s_no_process=true
meminfo_after_final_force_stop_10s_no_process=true
force_stop_cleanup=pass
```

## Cleanup Evidence

Native diagnostics reached:

```text
before RunDecode SetMaxOutputTokens(3)
success ... cleanup_elapsed_ms=103
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
Engine.close=unique_ptr_cleanup
```

The selected tombstone was stale and did not contain the current run id:

```text
stale-tombstone-ignored
```

## Interpretation

This run supports process-level cleanup:

```text
leak_classification=no_app_process_retained_after_force_stop
```

No app process remained after final force-stop, and package meminfo reported no
process at both 3 seconds and 10 seconds after force-stop. This does not prove
absence of vendor driver, DSP, or system-service retention outside the app
process, and it does not replace repeated-run growth testing.

## Next Step

Keep this as the cold-start force-stop baseline. If more confidence is needed,
the next diagnostic should be a separately approved repeated cold-start loop
that records whether per-run peak PSS grows across iterations, still isolated
from the normal chat UI.
