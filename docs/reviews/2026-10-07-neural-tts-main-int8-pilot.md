# Main transformer INT8 pilot and device listening decision

The four actual SM8750 clips were reviewed in Voice Lab. Both full CP INT8 clips were marked candidate with comment 「問題なし。」. FP32 sentence 1 was also candidate; FP32 sentence 2 was rejected with 「最後のノイズが気になる。」. The exact reviews are retained in the adjacent listening JSON. This accepts the two INT8 samples for the next experimental baseline, not broad voice quality or sustained realtime acceptance.

PR #2732 passed both Standard debug and Standard release CI and merged into main at `00d03b40`.

Main forward currently costs about 98–109 ms per call on the phone. Full CP INT8 still requires 16 serial calls at about 4.75 ms each. The overall target remains 50–60 ms per 80 ms audio frame; both costs must be reduced. The next isolated experiment exports all 196 main transformer linear weights with dynamic per-channel INT8 using the existing XNNPACK PT2E path. FP32 embeddings, heads, cache interface and audio decoder remain the same. No production model or device installation is changed.

`export_main_cache.py --precision int8` requires an isolated filename, refuses existing output, rejects QAT state, checks the expected INT8 constant count and writes a hash/size report. Existing FP32/INT4 options retain their behavior. `check_cp_full_feedback.py --main-candidate` allows the exported main to feed the actual CP and subsequent main steps through EOS, while still verifying the original bundle. Candidate hashes are recorded separately from baseline bundle hashes.

Validation must include successful export, EOS and finite bounded PCM, comparison against FP32-main with identical INT8 CP, listening review, and later a backed-up phone A/B test. Export completion alone is not speed or quality evidence. Reducing serial CP calls or accelerating main on NPU remains necessary if the combined budget is still exceeded.

Initial default XNNPACK partition lowering failed with `Invalid partition, found dependency cycles` after export. The INT8 pilot now uses explicit per-op partitioning; FP32 and INT4 retain default partitioning. Success of this fallback is still pending until the PTE is produced and executed.

Completed checks: Python compilation and `git diff --check`; actual CLI refusal tests for existing output, production filename, and QAT state all passed with the original sentinel output unchanged. Host forward timing includes prefill and method invocation input construction, excludes cache clone, and is not a device timing result.
