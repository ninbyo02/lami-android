# CP grouped attention host pilot

The opt-in `--grouped-attention` path groups adjacent query heads around each KV head for the fixed single-token export. It computes attention with the original KV heads without repeat_kv materialization. All35 INT8 weights, default XNNPACK partitioning and default exporter behavior remain unchanged. No bundle or Android selector changes.

## Captured input comparison

Two prior FP32 full-feedback captures at positions0 and15; seven alternating trials, three warmups, eight measurements, two CPU threads. Variant key32 denotes baseline cache16 and key16 grouped cache16. Neither is cache32.

| Position | Baseline ms | Grouped ms | Speed ratio |
| --- | ---: | ---: | ---: |
| 0 | 3.055 | 2.790 | 1.10x |
| 15 | 3.044 | 2.742 | 1.11x |

Hidden and both KV outputs are bit-exact for these inputs. Both full-feedback texts reach EOS at49/50 frames, and codes/WAV are byte-identical to the main128 + baselineCP16 host outputs. No duplicate Voice Lab clips are needed.

Dedicated native traces are separate runtime measurements; position15 does not show a consistent end-to-end improvement despite lower expand_copy cost. Host scheduling was not controlled. This is not device or broad voice-quality evidence.

Python compilation, isolated export (35 INT8 weights), captured-input/native profiling, two EOS feedback runs, hash comparison and diff check passed. Android selection, device output consistency and speed testing remain pending.

Artifacts: /home/sato/project/lami-android-voice-dataset/qat/cp-grouped-pilot-20261008
