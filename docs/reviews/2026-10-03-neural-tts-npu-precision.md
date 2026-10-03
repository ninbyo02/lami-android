# CP NPU precision and weight quantization pilot

The trained LAMI voice checkpoint was used, not the generic Base checkpoint. A new optional FP32 LiteRT export preserves the original source graph for post-export quantization; the existing FP16 export remains the default. All runs were isolated from normal chat. They use the two recorded Japanese inputs, two codec frames per utterance, 64 CP steps and 60 selected heads. The CPU and NPU paths each carry their own cache within each frame; main-model embeddings and code history are teacher-forced from the approved CPU path. No full audio was generated or listened to.

| CP backbone | File/context size | Same-draw sampled code mismatches versus captured ExecuTorch | Finding |
| --- | ---: | ---: | --- |
| FP16 LiteRT CPU control | 150.14 MiB model | 0/60 | Existing CPU control |
| FP32 LiteRT CPU control | 300.14 MiB model | 0/60 | Export preserves selected codes on these inputs |
| FP16 LiteRT SM8750 NPU, prior ordinary graph | ~150 MiB context | 28/60 | Prior reference |
| FP32 source LiteRT SM8750 NPU | 157,749,248 byte context | 29/60 | No improvement over prior NPU run |
| FP32 source then weight-only int8, LiteRT CPU | 79,527,936 byte model | 35/60 | Reject this recipe before NPU deployment |

Applying the weight-only recipe directly to the FP16 source produced an unchanged 157,433,152 byte model, so the reproducible quantization path begins with `--weight-precision fp32` and checks for a material size reduction. The FP32 source compiled into one SM8750 dispatch graph with no leftover CPU operators. This does not imply FP32 arithmetic on the NPU; its context is approximately FP16-sized, and the available compiler configuration requests relaxed FP16 precision. The measured 29/60 result alone cannot establish why the two compilations differ.

The weight-only int8 CPU pilot had maximum hidden relative L2 error 0.127 against the captured path; its FP16 control was 1.22e-5. Sampled-code mismatches indicate sensitivity but are not a direct listening score or proof of audible degradation. These two candidates do not justify normal-chat activation. Next requires representative full utterances, NPU resident execution and subjective listening alongside the 80 ms per-frame budget. Changing only the CP is insufficient while the measured main plus CP forwards exceed the budget.

Evidence: `2026-10-03-neural-tts-npu-precision-summary.json` and the previous `2026-10-03-neural-tts-npu-actual-full.json`. Generated models and per-step raw files remain outside Git.
