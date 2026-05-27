# QAIRT244 NPU max_output_tokens 512 quality/safety comparison

256 hidden experimental candidate reference: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`

| prompt | 256_ref_status | 256_ref_quality | 256_ref_decode_ms | 512_status | 512_quality | code_indent | code_fence | 512_decode_ms | 512_elapsed_ms | 512_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success` | `natural_japanese` | 884 | `success` | `natural_japanese` | `not_applicable` | `not_applicable` | 858 | 3000 | 24 | `true` | `false` | `false` | `false` | `false` |
| `Pythonで簡単な電卓コードを書いて` | `success` | `useful_code` | 7351 | `success` | `useful_code` | `true` | `true` | 13358 | 16000 | 1477 | `true` | `false` | `false` | `false` | `false` |
| `ラミィのNPU推論について短く説明して` | `success` | `natural_japanese` | 4110 | `timeout` | `timeout` | `not_applicable` | `not_applicable` | unavailable | 60000 | unavailable | `false` | `unavailable` | `true` | `unavailable` | `unavailable` |
