# CP limited copy-delegation pilot: rejected

Reproduce a separate all-INT8 cache16 pilot with `export_cp_cache.py --precision int8 --capacity 16 --int8-permute-delegate`. The new flag uses per-op Linear/BMM/Softmax/Permute configurations, requires all-INT8, and preserves the original default partitioner. It does not update a bundle or Android model selection.

Measured the same two previously captured full-feedback inputs at CP positions 0 and 15, seven alternating trials, three warmups and eight measured forwards per trial, two CPU threads. Dedicated native traces are separate from untraced Python timing. In the JSON, variant key 32 means the baseline cache16 model and key 16 means the copy-delegated cache16 candidate; neither model has cache32.

At position15 baseline median trial mean was 2.731ms and candidate 3.349ms, approximately 23% slower. The candidate exposes pow/sigmoid/mul and other operations to native kernels rather than retaining broader default delegation. Hidden/KV differences are small but nonzero. Two full-feedback texts reach EOS at 52/50 frames versus baseline 49/50, so sample trajectories are not preserved. Do not promote this model or perform Android speed testing of it.

This rejects the limited-partition approach for CP; it does not show that moving Permute alone within the original partition would fail. Future work should preserve existing fused partitions or change cache update semantics, with explicit alias/output-lifetime validation. Native JNI output copying is small in device traces, so eliminating only that copy cannot close the realtime gap.

Validation: exporter completed with exactly35 INT8 weights, fixed captured-input comparison and native trace completed, full feedback reached EOS, Python syntax and diff whitespace passed. No voice-quality or realtime claim. Artifact directory: /home/sato/project/lami-android-voice-dataset/qat/cp-copy-pilot-20261008/.
