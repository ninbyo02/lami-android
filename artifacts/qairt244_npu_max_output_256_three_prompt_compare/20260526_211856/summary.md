# QAIRT244 NPU max_output_tokens 256 three-prompt hidden comparison

- artifact: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`
- baseline_reference: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`
- device: `192.168.52.52:44885`
- package: `io.github.ninbyo02.lami`
- receiver: `io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver`
- timeout_seconds_per_run: `30`
- template_mode: `gemma_it_like`
- executable_case: `sanitizer_only + max_output_tokens=256`
- run_count_policy: `one run per prompt only`
- overall_status: `success`

## Prompts

- `こんにちは`
- `Pythonで簡単な電卓コードを書いて`
- `ラミィのNPU推論について短く説明して`

## Comparison

128 baseline reference: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`

| prompt | 256_status | 256_quality | 256_decode_ms | 256_elapsed_ms | sanitized_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success` | `natural_japanese` | 884 | 3000 | 24 | `true` | `false` | `false` | `false` | `false` |
| `Pythonで簡単な電卓コードを書いて` | `success` | `useful_code` | 7351 | 9000 | 507 | `true` | `false` | `false` | `false` | `false` |
| `ラミィのNPU推論について短く説明して` | `success` | `natural_japanese` | 4110 | 6000 | 251 | `true` | `false` | `false` | `false` | `false` |

Memory after the final 10-second cool-down was not retained high:
`TOTAL PSS=224993 KB`, `Native Heap=34500 KB`.

## Safety Notes

- 128 remains the adopted hidden experimental H1 display baseline unless 256 is separately accepted after this artifact review.
- The 256 run is hidden experimental compare-only and requires explicit `allow_max_output_tokens_compare=true`.
- The runner does not connect standard route, normal ChatScreen assistant list, DB, TTS, Markdown, or streaming.
- The runner does not perform retry, fallback, or multiple unbounded generations.
- Adoption requires QNN/HTP/FastRPC evidence, `fallback_used=false`, `timeout=false`, `fresh_crash=false`, artifact-free sanitized output, and no retained memory anomaly after 10 seconds.
