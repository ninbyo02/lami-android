# QAIRT244 NPU max_output_tokens 256 single prompt verification

- artifact: `artifacts/qairt244_npu_max_output_256_single_prompt/20260526_211046`
- baseline_reference: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`
- device: `192.168.52.52:44885`
- package: `io.github.ninbyo02.lami`
- receiver: `io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver`
- timeout_seconds_per_run: `30`
- template_mode: `gemma_it_like`
- executable_case: `sanitizer_only + max_output_tokens=256`
- run_count_policy: `single prompt, one run only`
- overall_status: `success`

## Prompts

- `こんにちは`

## Comparison

# QAIRT244 NPU max_output_tokens 256 quality/safety comparison

128 baseline reference: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`

| prompt | 128_ref_status | 128_ref_quality | 128_ref_decode_ms | 256_status | 256_quality | 256_decode_ms | 256_elapsed_ms | 256_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success
success` | `natural_japanese
natural_japanese` | 829
829 | `success` | `natural_japanese` | 701 | 2000 | 24 | `true` | `false` | `false` | `false` | `false` |

## Safety Notes

- 128 remains the adopted hidden experimental H1 display baseline unless 256 is separately accepted after this artifact review.
- The 256 run is hidden experimental compare-only and requires explicit `allow_max_output_tokens_compare=true`.
- The runner does not connect standard route, normal ChatScreen assistant list, DB, TTS, Markdown, or streaming.
- The runner does not perform retry, fallback, or multiple unbounded generations.
- Adoption requires QNN/HTP/FastRPC evidence, `fallback_used=false`, `timeout=false`, `fresh_crash=false`, artifact-free sanitized output, and no retained memory anomaly after 10 seconds.
