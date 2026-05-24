# QAIRT 2.44 NPU Cold-Start Force-Stop Memory Profile

Artifact: `/home/sato/project/lami-android/artifacts/qairt244_npu_coldstart_force_stop_profile/20260523_092801`

## Smoke Outcome

```text
result=success
output=! How Hi
prompt=Hi
max_output_tokens=3
elapsed_ms=1572
prefill_elapsed_ms=28
decode_elapsed_ms=86
cleanup_elapsed_ms=103
tombstone_classification=stale-tombstone-ignored
```

## Cold-Start / Force-Stop State

```text
pid_before_force_stop=31094
pid_after_force_stop=none
pid_after_final_force_stop_3s=none
pid_after_final_force_stop_10s=none
meminfo_after_final_force_stop_3s_no_process=true
meminfo_after_final_force_stop_10s_no_process=true
cold_start=true
force_stop_cleanup=pass
leak_classification=no_app_process_retained_after_force_stop
```

The smoke launch was cold-started when `pid_after_force_stop` was empty.

## Memory Summary

```text
after_smoke_total_pss_kb=133138
after_smoke_native_heap_pss_kb=14297
after_3s_total_pss_kb=133691
after_3s_native_heap_pss_kb=14359
after_final_force_stop_3s_total_pss_kb=NA
after_final_force_stop_10s_total_pss_kb=NA
```

See `memory_summary.tsv` and `memory_delta.tsv`.

## Cleanup Interpretation

If final force-stop samples show no pid and meminfo reports no process, app PSS
is considered reclaimed for this one-run baseline. This does not prove absence
of all native leaks across repeated runs, but it is the expected cleanup
boundary for the diagnostic process.

## Safety

This wrapper called the existing isolated short multi-token smoke exactly once.
It did not connect NPU to the normal UI, did not set `selectedPath=npu`, did
not call high-level `generateResponse`, did not stream, and did not exceed
`maxOutputTokens=3`.
