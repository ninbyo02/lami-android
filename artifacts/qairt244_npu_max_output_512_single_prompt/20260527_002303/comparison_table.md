# QAIRT244 NPU max_output_tokens 512 quality/safety comparison

256 hidden experimental candidate reference: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`

| prompt | 256_ref_status | 256_ref_quality | 256_ref_decode_ms | 512_status | 512_quality | 512_decode_ms | 512_elapsed_ms | 512_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success` | `natural_japanese` | 884 | `success` | `natural_japanese` | 822 | 3000 | 24 | `true` | `false` | `false` | `false` | `false` |
