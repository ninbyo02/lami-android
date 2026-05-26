# QAIRT244 NPU max_output_tokens 256 three-prompt hidden comparison

128 baseline reference: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`

| prompt | 128_ref_status | 128_ref_quality | 128_ref_decode_ms | 256_status | 256_quality | 256_decode_ms | 256_elapsed_ms | sanitized_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |
| --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |
| `こんにちは` | `success` | `natural_japanese` | 829 | `success` | `natural_japanese` | 884 | 3000 | 24 | `true` | `false` | `false` | `false` | `false` |
| `Pythonで簡単な電卓コードを書いて` | `not_in_128_reference` | `not_in_128_reference` |  | `success` | `useful_code` | 7351 | 9000 | 507 | `true` | `false` | `false` | `false` | `false` |
| `ラミィのNPU推論について短く説明して` | `not_in_128_reference` | `not_in_128_reference` |  | `success` | `natural_japanese` | 4110 | 6000 | 251 | `true` | `false` | `false` | `false` | `false` |

Side-effect flags for all 256 rows: `standard_route_connected=false`,
`normal_ui_route_connected=false`, `assistant_message_list_inserted=false`,
`db=false`, `tts=false`, `markdown=false`, `streaming=false`.

Memory after 10s: `TOTAL PSS=224993 KB`, `Native Heap=34500 KB`.
