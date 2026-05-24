# QAIRT 2.44 dlopen Trace Dry-Run Attempt

Date: 2026-05-22

## Result

No `Engine.initialize` dry-run was executed.

The probe script built `customBuildExperimentDebug` and passed the flavor
leakage checks, but skipped install/probe because no adb device was connected.

```text
List of devices attached
```

The one allowed connected-device dry-run for this build remains unused.

## Build Under Test

```text
artifacts/qairt244_dlopen_trace_build/20260522_083658/
```

## Stage Artifact

```text
artifacts/litert_custom_build_stage/20260522_083818/
```

## Classification

`no-adb-device-connected`

Next step: with the Nubia device connected, run the existing command once:

```bash
bash scripts/run_custom_build_stack_probe.sh \
  artifacts/qairt244_dlopen_trace_build/20260522_083658 \
  --engine-dry-run \
  --model-path /data/user/0/io.github.ninbyo02.lami.customnpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm
```
