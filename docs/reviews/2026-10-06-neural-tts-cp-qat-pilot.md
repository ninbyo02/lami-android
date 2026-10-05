# Bounded CP INT8 QAT/distillation pilot

This is the first actual CP INT8 training experiment following the PTQ pilot.
It does not change Android playback or establish realtime synthesis.

## Data and training

The checked-in corpus fixes 16 training texts and four separate evaluation
texts. Each text supplies two teacher-generated codec frames, yielding 512
available training inputs and 128 evaluation inputs. Capture ids and NFC
normalized texts must be unique. Every NPZ has a hash; training verifies
those hashes and disjoint train/eval ids and texts.

The student uses the same five-layer CP backbone with XNNPACK PT2E dynamic
INT8 fake quantization on all 35 linears. Loss combines normalized hidden
MSE and teacher-head KL at temperature two. Heads and embeddings are fixed.
AdamW uses no weight decay; gradient norms are clipped to one. Both trials
start independently from the approved source checkpoint with seed 42.
The 80-step trial samples 80 distinct training records, not a full pass over
512 inputs. The 20-step trial samples 20 distinct records.

Teacher embeddings and caches are supplied during training. Evaluation after
ExecuTorch conversion uses both teacher caches and rolling candidate caches.
Rolling-cache evaluation still uses teacher embeddings, so it is not a
candidate-token-feedback rollout.

## Converted model results on four evaluation texts

There are 128 CP steps and 120 selected-head comparisons per model.

| Metric | PTQ, no training | QAT, 80 steps / LR 1e-5 | QAT, 20 steps / LR 1e-7 |
| --- | ---: | ---: | ---: |
| Teacher-cache mean relative hidden L2 | 0.06830 | 0.38473 | 0.06673 |
| Rolling-cache mean relative hidden L2 | 0.08365 | 0.49583 | 0.08345 |
| Teacher-cache maximum relative L2 | 0.17588 | 1.69841 | 0.11803 |
| Rolling-cache maximum relative L2 | 0.14073 | 1.79022 | 0.16123 |
| Teacher-cache sampled-code differences | 85/120 | 103/120 | 87/120 |
| Rolling-cache sampled-code differences | 90/120 | 112/120 | 85/120 |
| Teacher-cache top-1 differences | 18/120 | 62/120 | 16/120 |
| Rolling-cache top-1 differences | 21/120 | 75/120 | 20/120 |

The first trial regressed substantially and is rejected. The lower-rate trial
avoids that regression but produces mixed results: slightly lower mean errors
and fewer rolling sampled-code differences, while teacher sampled-code
differences and the worst rolling hidden error worsen. Neither model is
promoted. Both are about 79 MB and retain all 35 INT8 linears.

The evaluation split informed the second trial's hyperparameter choice;
it is a validation set, not an untouched final acceptance test. No full
sentence generation, listening review or device timing was performed.
Host timing trials are recorded for audit, but concurrent host workloads and
training prevent using them as normalized device/realtime projections.

## Reproduction and next gate

Use the ExecuTorch 1.4 environment and isolated output directories:

```sh
python scripts/voice/capture_mtp_voice_inputs.py --root "$VOICE_BUNDLE" \
  --texts-jsonl scripts/voice/cp_qat_pilot_texts.jsonl --frames 2 \
  --output-dir "$CAPTURES"
python scripts/voice/train_cp_qat_pilot.py --model "$VOICE_MODEL" \
  --captures "$CAPTURES" --output-dir "$QAT_PILOT" \
  --steps 20 --learning-rate 1e-7
python scripts/voice/check_cp_cache_pilot.py --captures "$CAPTURES" \
  --split eval --candidate "$QAT_PILOT/cp-qat-int8-cache32.pte" \
  --report "$QAT_PILOT/converted-validation.json"
```

Models, capture files, raw validation reports and checkpoints are retained
outside git in the voice-dataset project's
`qat/cp-int8-qat-pilot-20261006` directory. Git contains the training metadata,
aggregate validation results and hashes of the full raw reports.

A useful next training experiment needs more sentence/frame coverage,
calibration or layer-selective quantization, a separate acceptance test set,
and actual token-feedback synthesis/listening. Simply increasing this tiny
trial's steps is not justified by its results. Main acceleration and fewer
serial CP predictions remain necessary for the complete 50–60 ms frame target.
