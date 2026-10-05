# CP dynamic INT8 pilot

The latest SM8750 head optimization retained identical codec/PCM output, but its
complete frame medians remained 268–277 ms for the second probe text. Main and
CP forward calls dominate. This experiment tests CP compression independently
of Android playback.

The new exporter reproduces the existing five-layer, cache32 FP32 CP graph and
optionally applies XNNPACK PT2E dynamic signed INT8 activations and per-channel
signed INT8 weights to all 35 linear layers. The embeddings, heads, normalization,
attention and cache interfaces remain FP32. Models are isolated files and the
exporter refuses the production CP filename or an existing output.

Using the ExecuTorch 1.4 environment, the FP32 control reproduced the current
FP32 program exactly on all 64 captured inputs, including rolling caches. This
establishes that the observed INT8 differences are due to quantization rather
than a different wrapper/checkpoint.

| Host measurement | Existing FP32 CP | INT8 CP |
| --- | ---: | ---: |
| Program bytes | 314,730,112 | 79,064,960 |
| Median native execution plus Python bridge per step | 8.522 ms | 3.841 ms |
| Teacher-cache selected-code mismatches | 0/60 | 48/60 |
| Rolling-cache selected-code mismatches | 0/60 | 45/60 |
| Teacher-cache top-1 mismatches | 0/60 | 7/60 |
| Rolling-cache top-1 mismatches | 0/60 | 8/60 |
| Maximum relative hidden-vector L2, teacher caches | 0 | 0.17588 |
| Maximum relative hidden-vector L2, rolling caches | 0 | 0.14073 |

Timing used seven trials over 64 inputs, alternating execution order. The
PyTorch thread limit was set to two; ExecuTorch runtime defaults were used.
The host had other workloads. This is approximately 2.22x faster CP
execution and 75% less model storage on this host, not a phone or realtime claim.

The inputs cover only two Japanese texts and two frames per text. Rolling
cache mode carries candidate K/V outputs between positions, but still supplies
teacher embeddings. No candidate-token feedback, full sentence synthesis or
listening review was performed. Different sampled tokens do not alone prove
bad audio; these results do show that lossless voice/code preservation has not
been established. Do not replace the approved production CP with this PTQ pilot.

The next model experiment should use a larger teacher corpus with a held-out
split for quantization-aware training or distillation, then full token-feedback
rollout and listening review. No such training has started in this experiment.
Main-model acceleration and fewer serial CP predictions remain necessary
because improving CP alone cannot meet the complete 50–60 ms frame target.

## Reproduction

Use the existing environment that contains ExecuTorch 1.4, torchao, Qwen3-TTS
and NumPy. Keep model artifacts outside git.

```sh
python scripts/voice/export_cp_cache.py --model "$VOICE_MODEL" --precision int8 \
  --output "$PILOT_DIR/cp-int8-cache32.pte" --report "$PILOT_DIR/export.json"
python scripts/voice/check_cp_cache_pilot.py --captures "$CAPTURES" \
  --candidate "$PILOT_DIR/cp-int8-cache32.pte" --report "$PILOT_DIR/check.json"
```

Repeat with `--precision fp32` and a distinct filename to validate the exporter.
Capture manifests must originate from `scripts/voice/capture_mtp_voice_inputs.py`.
The checker verifies the production program hash, reproduces every captured
baseline hidden vector, and checks selected codes using the original random
draws before reporting candidate divergence.
