# QAIRT244 NPU max_output_tokens 256 quality/safety compare

- artifact: `artifacts/qairt244_npu_max_output_256_quality_compare/20260526_201129`
- baseline_reference: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`
- device: `192.168.52.52:33443`
- package: `io.github.ninbyo02.lami`
- receiver: `io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver`
- timeout_seconds_per_run: `30`
- template_mode: `gemma_it_like`
- executable_case: `sanitizer_only + max_output_tokens=256`
- run_count_policy: `one run per prompt only`
- overall_status: `failure`

## Prompts

- `こんにちは`
- `Pythonで簡単な電卓コードを書いて`
- `ラミィのNPU推論について短く説明して`

## Comparison

# QAIRT244 NPU max_output_tokens 256 quality/safety comparison

128 baseline reference: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`

| prompt | 128_ref_status | 128_ref_quality | 128_ref_decode_ms | 256_status | 256_quality | 256_decode_ms | 256_elapsed_ms | 256_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success` | `natural_japanese` | 829 | `failure` | `empty_after_sanitize` | -1 | 1000 | 0 | `true` | `false` | `false` | `false` | `false` |
| `Pythonで簡単な電卓コードを書いて` | `not_in_128_reference` | `not_in_128_reference` |  | `failure` | `empty_after_sanitize` | -1 | 0 | 0 | `true` | `false` | `false` | `false` | `false` |
| `ラミィのNPU推論について短く説明して` | `not_in_128_reference` | `not_in_128_reference` |  | `failure` | `empty_after_sanitize` | -1 | 0 | 0 | `true` | `false` | `false` | `false` | `false` |

## Result

`max_output_tokens=256` is not a baseline candidate in this run.

The Java hidden compare path accepted the compare-only request, but the lower
native editable-prompt entrypoint rejected it with:

```text
invalid_max_output_tokens value=256 native_max_output_tokens_limit=128
```

All three 256 prompt attempts therefore returned empty sanitized output with
`quality_classification=empty_after_sanitize`. This is a rollback condition.
No timeout, fresh crash, fallback, DB/TTS/Markdown/streaming ingress, selected
path persistence, or normal UI route connection was recorded.

Memory after 10 seconds was recorded in `meminfo_after_10s.txt`; because native
decode was rejected before generation, the memory sample is diagnostic only and
does not support adopting 256.

## Safety Notes

- 128 remains the adopted hidden experimental H1 display baseline unless 256 is separately accepted after this artifact review.
- The 256 run is hidden experimental compare-only and requires explicit `allow_max_output_tokens_compare=true`.
- The runner does not connect standard route, normal ChatScreen assistant list, DB, TTS, Markdown, or streaming.
- The runner does not perform retry, fallback, or multiple unbounded generations.
- Adoption requires QNN/HTP/FastRPC evidence, `fallback_used=false`, `timeout=false`, `fresh_crash=false`, artifact-free sanitized output, and no retained memory anomaly after 10 seconds.
