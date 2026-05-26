# QAIRT244 NPU max_output_tokens 256 quality/safety comparison

128 baseline reference: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`

| prompt | 128_ref_status | 128_ref_quality | 128_ref_decode_ms | 256_status | 256_quality | 256_decode_ms | 256_elapsed_ms | 256_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success` | `natural_japanese` | 829 | `failure` | `empty_after_sanitize` | -1 | 1000 | 0 | `true` | `false` | `false` | `false` | `false` |
| `Pythonで簡単な電卓コードを書いて` | `not_in_128_reference` | `not_in_128_reference` |  | `failure` | `empty_after_sanitize` | -1 | 0 | 0 | `true` | `false` | `false` | `false` | `false` |
| `ラミィのNPU推論について短く説明して` | `not_in_128_reference` | `not_in_128_reference` |  | `failure` | `empty_after_sanitize` | -1 | 0 | 0 | `true` | `false` | `false` | `false` | `false` |
