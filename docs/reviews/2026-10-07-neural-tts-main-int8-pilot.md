# Main transformer INT8 pilot and device listening decision

The four actual SM8750 clips were reviewed in Voice Lab. Both full CP INT8 clips were marked candidate with comment 「問題なし。」. FP32 sentence 1 was also candidate; FP32 sentence 2 was rejected with 「最後のノイズが気になる。」. The exact reviews are retained in the adjacent listening JSON. This accepts the two INT8 samples for the next experimental baseline, not broad voice quality or sustained realtime acceptance.

PR #2732 passed both Standard debug and Standard release CI and merged into main at `00d03b40`.

Main forward currently costs about 98–109 ms per call on the phone. Full CP INT8 still requires 16 serial calls at about 4.75 ms each. The overall target remains 50–60 ms per 80 ms audio frame; both costs must be reduced. The next isolated experiment exports all 196 main transformer linear weights with dynamic per-channel INT8 using the existing XNNPACK PT2E path. FP32 embeddings, heads, cache interface and audio decoder remain the same. No production model or device installation is changed.

`export_main_cache.py --precision int8` requires an isolated filename, refuses existing output, rejects QAT state, checks the expected INT8 constant count and writes a hash/size report. Existing FP32/INT4 options retain their behavior. `check_cp_full_feedback.py --main-candidate` allows the exported main to feed the actual CP and subsequent main steps through EOS, while still verifying the original bundle. Candidate hashes are recorded separately from baseline bundle hashes.

Validation must include successful export, EOS and finite bounded PCM, comparison against FP32-main with identical INT8 CP, listening review, and later a backed-up phone A/B test. Export completion alone is not speed or quality evidence. Reducing serial CP calls or accelerating main on NPU remains necessary if the combined budget is still exceeded.

Initial default XNNPACK partition lowering failed with `Invalid partition, found dependency cycles` after export. An all-operator per-op lowering was stopped after several minutes of sustained compiler work without a PTE. The INT8 pilot now uses per-op partitioning limited to LinearConfig, BMMConfig and SoftmaxConfig to bound delegate count, with other operations handled by ExecuTorch; FP32 and INT4 retain default partitioning. This scoped fallback successfully produced and executed the PTE.

Completed checks: Python compilation and `git diff --check`; actual CLI refusal tests for existing output, production filename, and QAT state all passed with the original sentinel output unchanged. Host forward timing includes prefill and method invocation input construction, excludes cache clone, and is not a device timing result.

## Completed host result

All 196 INT8 constants were verified. The scoped model is 442,873,216 bytes, SHA256 `6a00b9912080e057db86fe29e0c67bb3a8fa6c0851c8f5daea04175578d5c338`. Both models, using identical full INT8 CP and actual candidate feedback, reach EOS and produce finite bounded 24 kHz PCM for both sentences. FP32 main generates 49/45 audio frames; INT8 main generates 49/50. Codec outputs differ, so voice quality must be reviewed.

The single sequential host run observes main forward mean 86.079 ms (FP32) versus 79.821 ms (INT8), with p50 85.256/79.604 ms and p95 89.106/81.785 ms. This modest difference does not establish stable speedup: the runs have different trajectories and call counts, include prefill, and are not controlled device A/B measurements. CP mean is 3.248/3.163 ms. These measurements do not meet or prove the total realtime frame target. Main INT8 is an executable candidate, not a production promotion.

Four PC-generated clips are published and HTTP hash-verified in Voice Lab group `lami-main-int8-review-20261007`, ordered main FP32 then main INT8 for each sentence, with CP full INT8 in both. The UI explicitly labels them PC generation and preserves all previous groups/reviews. Listening and device performance remain pending. Raw artifacts and logs are under the voice dataset `qat/main-int8-pilot-20261007` directory; model binaries are not committed.

Next: review the four clips, profile main runtime/delegate overhead and cache work, then perform a backed-up phone comparison if the candidate is acoustically acceptable. CP still has 16 serial steps, so main INT8 alone cannot guarantee the 50–60 ms total budget.
