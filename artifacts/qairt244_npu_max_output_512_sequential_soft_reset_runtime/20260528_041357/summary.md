# QAIRT244 NPU max_output_tokens 512 sequential soft-reset runtime

- artifact: `artifacts/qairt244_npu_max_output_512_sequential_soft_reset_runtime/20260528_041357`
- baseline_reference: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`
- device: `192.168.52.52:34543`
- package: `io.github.ninbyo02.lami`
- receiver: `io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver`
- timeout_seconds_per_run: `60`
- template_mode: `gemma_it_like`
- executable_case: `sanitizer_only + max_output_tokens=512`
- sanitizer: `code-aware indentation/fence preservation`
- run_count_policy: `three approved prompts, one run per prompt only; stop immediately on suspect/rejected lifecycle gate`
- activity_restart_between_prompts: `false`
- force_stop_between_prompts: `false`
- process_policy: `maintain process; lifecycle summary gate only`
- overall_status: `failure`
- result_classification: `runtime_policy_stopped_sequence`
- sequence_stopped: `true`
- stopped_at_prompt: `3`
- stop_reason: `classification_TIMEOUT_SUSPECT`

## Prompts

- `こんにちは`
- `Pythonで簡単な電卓コードを書いて`
- `ラミィのNPU推論について短く説明して`

## Comparison

# QAIRT244 NPU max_output_tokens 512 quality/safety comparison

256 hidden experimental candidate reference: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`

| prompt | 256_ref_status | 256_ref_quality | 256_ref_decode_ms | 512_status | 512_quality | code_indent | code_fence | 512_decode_ms | 512_elapsed_ms | 512_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success` | `natural_japanese` | 884 | `success` | `natural_japanese` | `not_applicable` | `not_applicable` | 858 | 3000 | 24 | `true` | `false` | `false` | `false` | `false` |
| `Pythonで簡単な電卓コードを書いて` | `success` | `useful_code` | 7351 | `success` | `useful_code` | `true` | `true` | 13358 | 16000 | 1477 | `true` | `false` | `false` | `false` | `false` |
| `ラミィのNPU推論について短く説明して` | `success` | `natural_japanese` | 4110 | `timeout` | `timeout` | `not_applicable` | `not_applicable` | unavailable | 60000 | unavailable | `false` | `unavailable` | `true` | `unavailable` | `unavailable` |

## Lifecycle Gate

# Lifecycle gate results

| prompt_index | prompt | lifecycle_classification | next_prompt_allowed | reuse_allowed | runtime_reuse_allowed | runtime_reuse_policy | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | suspect_session | stop_reason | run_dir |
| ---: | --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 1 | `こんにちは` | `SUCCESS_CLEAN` | `true` | `true` | `true` | `reuse_allowed` | `false` | `122` | `true` | `false` | `none` | `run_512_konnichiwa` |
| 2 | `Pythonで簡単な電卓コードを書いて` | `SUCCESS_CLEAN` | `true` | `true` | `true` | `reuse_allowed` | `false` | `137` | `true` | `false` | `none` | `run_512_python_calculator` |
| 3 | `ラミィのNPU推論について短く説明して` | `TIMEOUT_SUSPECT` | `false` | `false` | `false` | `per_run_isolated_required` | `true` | `missing` | `false` | `true` | `classification_TIMEOUT_SUSPECT` | `run_512_lami_npu_short` |

## Safety Notes

- H1 remains pinned to max_output_tokens=128.
- 256 remains the hidden experimental baseline candidate.
- The 512 run is hidden experimental runtime-policy validation only and is not a sequential baseline promotion.
- 512 remains hidden_per_run_isolated_512 only unless a later review explicitly changes the policy.
- 1024/2048/4096 remain blocked.
- The runner does not connect standard route, normal ChatScreen assistant list, DB, TTS, Markdown, or streaming.
- The runner does not perform retry, fallback, or multiple unbounded generations.
- Sequential continuation requires `SUCCESS_CLEAN`, `next_prompt_allowed=true`, `reuse_allowed=true`, `runtime_reuse_allowed=true`, and `hidden_per_run_isolated_required=false`.
