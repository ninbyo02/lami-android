# QAIRT 2.44 NPU Memory Cleanup Profile

Date: 2026-05-23

Scope: `customBuildExperimentDebug` isolated lower-level short multi-token
smoke. This measures Android runtime memory and native heap behavior around one
NPU run. It is not Lami short-term or long-term memory work.

## Artifact

```text
artifacts/qairt244_npu_memory_cleanup_profile/20260523_091021/
```

The profiling script is:

```text
scripts/run_qairt244_npu_memory_cleanup_profile.sh
```

## Smoke Outcome

```text
result=success
prompt=Hi
max_output_tokens=3
output=! How Hi
elapsed_ms=1423
prefill_elapsed_ms=13
decode_elapsed_ms=84
cleanup_elapsed_ms=110
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
tombstone_classification=stale-tombstone-ignored
```

The run used only the isolated lower-level smoke Activity. It did not connect
NPU to the normal UI, did not set `selectedPath=npu` on the normal route, did
not call high-level `generateResponse`, and did not use streaming.

## Memory Samples

`after_install` and `smoke_before` are `NA` because the app process was not
running after install and before explicit smoke launch. A previous customnpu
process existed at the `before` sample, so this is a warm-process baseline, not
a cold boot baseline.

```text
sample        total_pss_kb  native_heap_pss_kb  native_dirty_kb  dalvik_pss_kb  code_pss_kb  stack_pss_kb  graphics_pss_kb
before       130698        14115               14104            928            96704        520           420
after        136005        13805               13784            1236           98968        516           420
after_3s     133062        13911               13900            1052           98640        524           420
after_10s    132854        13903               13892            1000           98596        524           420
```

Key deltas versus `before`:

```text
metric                        after   after_10s
TOTAL PSS                     +5307   +2156 KB
Native Heap PSS                -310    -212 KB
Native Heap Private Dirty      -320    -212 KB
Dalvik Heap PSS                +308     +72 KB
```

## Cleanup Evidence

Native diagnostics reached:

```text
before RunDecode SetMaxOutputTokens(3)
success ... cleanup_elapsed_ms=110
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
Engine.close=unique_ptr_cleanup
```

The diagnostics collector selected an older tombstone, but it did not contain
the current run id. The current result/native diag files contain the current run
marker and the process remained alive, so it is classified as:

```text
stale-tombstone-ignored
```

## Interpretation

This single baseline run does not show an obvious native heap growth signal:
Native Heap PSS at `after_10s` was `212 KB` lower than the warm-process
`before` sample.

TOTAL PSS remained `2156 KB` above `before` after 10 seconds. This is not
classified as a leak from one run because the process stayed alive and mapped
QAIRT/LiteRT/QNN libraries can remain resident. A leak claim needs repeated
profile runs with a cold baseline or controlled process lifecycle.

## Next Step

Keep this as the baseline profile. If more confidence is needed, run a separate
approved repeated-profile test that measures a cold start and then force-stops
the diagnostic process after artifact collection. Do not connect the NPU path to
the normal chat UI from this memory result alone.

## Cold-Start Force-Stop Follow-Up

Follow-up artifact:

```text
artifacts/qairt244_npu_coldstart_force_stop_profile/20260523_092801/
```

Result:

```text
result=success
output=! How Hi
max_output_tokens=3
pid_after_force_stop=none
pid_after_final_force_stop_3s=none
pid_after_final_force_stop_10s=none
meminfo_after_final_force_stop_3s_no_process=true
meminfo_after_final_force_stop_10s_no_process=true
leak_classification=no_app_process_retained_after_force_stop
```

This confirms the app process is removed after final `force-stop`; package PSS
is not present because `dumpsys meminfo` reports no process.

## Diagnostic Chat UI Multi-Run Attempt

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_110017/
```

Memory samples from the attempt:

```text
sample          total_pss_kb  native_heap_pss_kb
before         unavailable   unavailable
after_run1     257015        75668
after_run2     939257        41678
after_10s      64721         17860
```

The `before` sample is unavailable because there was no package process at that
point. The `after_10s` sample dropped to `64721 KB` TOTAL PSS and `17860 KB`
Native Heap PSS. This attempt is not treated as the final multi-run stability
baseline because the first runner version stopped on an earlier success marker
while the UI still showed a later `state=started` marker. The script has been
fixed before any future approved rerun.

## Fixed Diagnostic Chat UI Multi-Run Verification

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/
```

Memory samples:

```text
sample          total_pss_kb  native_heap_pss_kb
before         53135         6
after_run1     583312        72093
after_run2     142283        43051
after_10s      78536         20571
```

Deltas versus `before`:

```text
after_run1_total_pss_delta_kb=+530177
after_run2_total_pss_delta_kb=+89148
after_10s_total_pss_delta_kb=+25401
after_run1_native_heap_pss_delta_kb=+72087
after_run2_native_heap_pss_delta_kb=+43045
after_10s_native_heap_pss_delta_kb=+20565
```

The `after_10s` sample is lower than both immediate post-run samples, so this
two-run verification does not show monotonic growth across the observed window.
It is still a bounded baseline, not a leak proof.
