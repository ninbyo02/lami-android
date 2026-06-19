# NPU Rollout Sample Collection

Scope: docs/scripts only. This collection guide does not change Android
runtime, ChatScreen, Settings UI, NPU route behavior, DB schema, dev-gate
behavior, native libraries, quality gates, or suppression behavior.

## Purpose

Dev-gate removal readiness is still blocked because the rollout evidence set is
incomplete:

- Phase 8 success samples are insufficient.
- Suppression-pass evidence is insufficient.
- R1b completed-route diagnostics still need device confirmation.

This guide defines the minimum device sample set to collect after returning to
the device.

## Prompt Manifest

Generate the manifest for the collection date:

```text
scripts/create_npu_rollout_sample_manifest.sh --date 20260619
```

The minimum sample set is:

| Category | Prompt | Expected result |
| --- | --- | --- |
| `short_success` | `こんにちは` | Phase 8 success or template cleanup pass, with completed route diagnostics. |
| `medium_success` | `カレーの材料をお願いします。` | Phase 8 success, natural Japanese output, pseudo streaming text consistency. |
| `markdown_success` | `箇条書きで旅行計画を作成してください。` | Phase 8 success with Markdown executed and pseudo streaming finalized text. |
| `suppression_pass` | `template cleanup が出やすい短文` | Quality candidate fail is safely suppressed before UI / TTS / DB / Markdown / pseudo streaming. |

## Artifact Naming

Save copied NPU diagnostics / compact dump output under:

```text
artifacts/device_runs/npu_rollout_short_success_YYYYMMDD.txt
artifacts/device_runs/npu_rollout_medium_success_YYYYMMDD.txt
artifacts/device_runs/npu_rollout_markdown_success_YYYYMMDD.txt
artifacts/device_runs/npu_rollout_suppression_pass_YYYYMMDD.txt
```

Example for June 19, 2026:

```text
artifacts/device_runs/npu_rollout_short_success_20260619.txt
artifacts/device_runs/npu_rollout_medium_success_20260619.txt
artifacts/device_runs/npu_rollout_markdown_success_20260619.txt
artifacts/device_runs/npu_rollout_suppression_pass_20260619.txt
```

## Device Setup

Set the rollout dev gate and clear any explicit phase override by using phase
`0`:

```text
adb shell setprop debug.lami.npu_standard_route_dev_gate true
adb shell setprop debug.lami.npu_standard_route_phase 0
adb shell monkey -p io.github.ninbyo02.lami 1
```

Do not use:

```text
adb shell setprop debug.lami.npu_standard_route_phase ""
```

Android `setprop` does not accept an empty value in this form. Use phase `0` or
reboot the device if you need to clear a previous explicit override.

In Settings:

- Set inference backend to `NPU Experimental`.
- Leave developer phase override unset.
- TTS can be ON or OFF. One TTS ON sample is useful, but TTS OFF with
  `tts_execution_block_reason=tts_disabled` should not block rollout by itself.

## Per-Prompt Save Procedure

For each prompt:

1. Send the prompt in ChatScreen.
2. Use `NPU診断キーをコピー` or save the compact/full NPU dump.
3. Save it to the expected artifact path for the date.
4. Verify the artifact contains the Phase 8 keys and R1b completed-route keys.

R1b expected keys:

```text
npu_standard_route_selection_mode=user_facing_npu_experimental
npu_standard_route_completed_route_selected=true
npu_standard_route_effective_phase_source=completed_route_default
npu_standard_route_effective_phase=8
npu_standard_route_completed_route_family=npu_standard_route_completed
```

`selected_backend=NPU_S5` and `route_family=npu_s5` may still appear as internal
legacy compatibility evidence. Treat the completed-route keys above as the
user-facing rollout evidence.

## Sample Set Check

Before running the full monitor, check that all four artifacts exist:

```text
scripts/review_npu_rollout_sample_set.sh \
  --device-runs artifacts/device_runs \
  --date 20260619
```

Expected:

```text
NPU_ROLLOUT_SAMPLE_SET_STATUS=complete
READY_FOR_MONITOR=true
READY_FOR_DEV_GATE_READINESS=true
```

## Monitor And Readiness

After all samples are saved:

```text
scripts/review_npu_rollout_monitor.sh --device-runs artifacts/device_runs
scripts/review_npu_dev_gate_removal_readiness.sh --device-runs artifacts/device_runs
```

Expected monitor result:

```text
NPU_ROLLOUT_MONITOR_STATUS=healthy
NPU_ROLLOUT_SUCCESS_COUNT>=3
NPU_ROLLOUT_SUPPRESSION_PASS_COUNT>=1
NPU_ROLLOUT_FAILURE_COUNT=0
NPU_ROLLOUT_RISK_LEVEL=low
NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=true
```

Expected dev-gate readiness result:

```text
NPU_DEV_GATE_REMOVAL_REVIEW=ready
READY_TO_REMOVE_DEV_GATE=true
```

This still does not remove the dev gate. It only proves the evidence set is
sufficient for the next implementation review.

## Troubleshooting

If samples are missing:

```text
scripts/review_npu_rollout_sample_set.sh --device-runs artifacts/device_runs --date YYYYMMDD
```

If `NPU_ROLLOUT_RISK_LEVEL=medium`, collect missing success or suppression
samples.

If `NPU_ROLLOUT_RISK_LEVEL=high`, inspect the specific failure counters before
collecting more samples:

- `NPU_ROLLOUT_TIMEOUT_COUNT`
- `NPU_ROLLOUT_FRESH_CRASH_COUNT`
- `NPU_ROLLOUT_FALLBACK_COUNT`
- `NPU_ROLLOUT_ENGINE_CREATE_FAILURE_COUNT`
- `NPU_ROLLOUT_QUALITY_FAILURE_COUNT`

If R1b keys are absent but Phase 8 success otherwise passes, the monitor may
still classify the artifact as success. Dev-gate removal readiness should remain
blocked until R1b completed-route diagnostics are confirmed.
