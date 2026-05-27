# QAIRT244 NPU max_output_tokens 512 activity-restart-only hidden comparison

- artifact: `artifacts/qairt244_npu_max_output_512_activity_restart_compare/20260527_213930`
- baseline_reference: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`
- device: `192.168.52.52:34543`
- package: `io.github.ninbyo02.lami`
- receiver: `io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver`
- timeout_seconds_per_run: `60`
- template_mode: `gemma_it_like`
- executable_case: `sanitizer_only + max_output_tokens=512`
- sanitizer: `code-aware indentation/fence preservation`
- run_count_policy: `three approved prompts, one run per prompt only`
- execution_isolation: `Activity finish/relaunch between prompts; process force-stop prohibited`
- overall_status: `failure`

## Prompts

- `こんにちは`
- `Pythonで簡単な電卓コードを書いて`
- `ラミィのNPU推論について短く説明して`

## Comparison

# QAIRT244 NPU max_output_tokens 512 quality/safety comparison

256 hidden experimental candidate reference: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`

| prompt | 256_ref_status | 256_ref_quality | 256_ref_decode_ms | 512_status | 512_quality | code_indent | code_fence | 512_decode_ms | 512_elapsed_ms | 512_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success` | `natural_japanese` | 884 | `success` | `natural_japanese` | `not_applicable` | `not_applicable` | 834 | 2000 | 24 | `true` | `false` | `false` | `false` | `false` |
| `Pythonで簡単な電卓コードを書いて` | `success` | `useful_code` | 7351 | `timeout` | `timeout` | `false` | `false` | unavailable | 70000 | unavailable | `true` | `unavailable` | `true` | `unavailable` | `unavailable` |
| `ラミィのNPU推論について短く説明して` | `success` | `natural_japanese` | 4110 | `success` | `natural_japanese` | `not_applicable` | `not_applicable` | 4393 | 6000 | 251 | `true` | `false` | `false` | `false` | `false` |

## Safety Notes

- 128 remains the adopted hidden experimental H1 display baseline unless 512 is separately accepted after this artifact review.
- The 512 run is hidden experimental compare-only and requires explicit `allow_max_output_tokens_compare=true`.
- The runner uses Activity restart only and does not issue app process force-stop.
- The runner does not connect standard route, normal ChatScreen assistant list, DB, TTS, Markdown, or streaming.
- The runner does not perform retry, fallback, or multiple unbounded generations.
- Adoption requires QNN/HTP/FastRPC evidence, `fallback_used=false`, `timeout=false`, `fresh_crash=false`, artifact-free sanitized output, code indentation/fence pass for the code prompt, cleanup evidence, and no retained memory anomaly after 10 seconds.
