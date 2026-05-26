# QAIRT244 NPU max_output_tokens 512 code prompt bounded retry

- artifact: `artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116`
- baseline_reference: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`
- device: `192.168.52.52:42067`
- package: `io.github.ninbyo02.lami`
- receiver: `io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver`
- timeout_seconds_per_run: `60`
- template_mode: `gemma_it_like`
- executable_case: `sanitizer_only + max_output_tokens=512`
- run_count_policy: `single approved Python prompt, one run only`
- overall_status: `success`

## Prompts

- `Pythonで簡単な電卓コードを書いて`

## Comparison

# QAIRT244 NPU max_output_tokens 512 quality/safety comparison

256 hidden experimental candidate reference: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`

| prompt | 256_ref_status | 256_ref_quality | 256_ref_decode_ms | 512_status | 512_quality | 512_decode_ms | 512_elapsed_ms | 512_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `Pythonで簡単な電卓コードを書いて` | `success` | `useful_code` | 7351 | `success` | `useful_code` | 11600 | 14000 | 1089 | `true` | `false` | `false` | `false` | `false` |

## Safety Notes

- 128 remains the adopted hidden experimental H1 display baseline unless 512 is separately accepted after this artifact review.
- The 512 run is hidden experimental compare-only and requires explicit `allow_max_output_tokens_compare=true`.
- The runner does not connect standard route, normal ChatScreen assistant list, DB, TTS, Markdown, or streaming.
- The runner does not perform retry, fallback, or multiple unbounded generations.
- Adoption requires QNN/HTP/FastRPC evidence, `fallback_used=false`, `timeout=false`, `fresh_crash=false`, artifact-free sanitized output, and no retained memory anomaly after 10 seconds.
