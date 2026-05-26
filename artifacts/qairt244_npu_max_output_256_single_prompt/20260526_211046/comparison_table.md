# QAIRT244 NPU max_output_tokens 256 quality/safety comparison

128 baseline reference: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`

| prompt | 128_ref_status | 128_ref_quality | 128_ref_decode_ms | 256_status | 256_quality | 256_decode_ms | 256_elapsed_ms | 256_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success
success` | `natural_japanese
natural_japanese` | 829
829 | `success` | `natural_japanese` | 701 | 2000 | 24 | `true` | `false` | `false` | `false` | `false` |
