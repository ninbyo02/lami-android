# LiteRT-LM GPU Context Benchmark
Budget semantics: input + output context / KV-cache capacity, not an output-only limit.
Legacy max_output_tokens fields are requested context values. Use resolved_engine_max_num_tokens for the variant configuration; it is not proof that initialization succeeded.
Generated tokens are reported only by output_tokens when runtime measurement is available; callback emits are not token counts.

- timestamp: `controlled_accelerator_4096_null_1789208660`
- route_type: `litert_lm_gpu_benchmark`
- backend: `GPU`
- requested_run_count: `1`
- completed_run_count: `1`
- success_count: `1`
- failure_count: `0`
- timeout_count: `0`
- fallback_count: `0`
- model_path_source: `auto`
- generic_fallback_model_configured: `true`
- backend_variants: `gallery-chat-parity`
- backends: `GPU`
- close_policies: `normal`
- phases: `send-message`
- requested_context_tokens_lists: `4096`
- timeout_ms: `45000`
- fallback_setting_changed: `false`
- backend_npu_touched: `false`
- qairt_qnn_touched: `false`
- send_api_variants: `gallery_contents_callback`
- conversation_config_used_values: `true`
- contents_api_used_values: `true`

| backend_variant | backend | model_path_source | generic_fallback_model_configured | close_policy | phase | prompt | requested_context_tokens | requested_context_tokens_list | status | reason | engine_create_ms | conversation_create_ms | first_token_ms | ttft_ms | decode_ms | total_ms | output_tokens | tokens_per_second | timeout | fallback_used | intentionally_leaked_for_diagnostic | fresh_crash | send_api_variant | sampler_top_k | sampler_top_p | sampler_temperature | conversation_config_used | contents_api_used |
| --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- |
| gallery-chat-parity | GPU | auto | true | normal | send-message | こんにちは | 4096 | 4096 | success | completed | 12805 | 10 | 150 | 150 | 1057 | 13881 |  |  | false | false | false | false | gallery_contents_callback | 64 | 0.95 | 1.0 | true | true |

## Case 1

- route_type: `litert_lm_gpu_benchmark`
- backend: `GPU`
- backend_variant: `gallery-chat-parity`
- close_policy: `normal`
- phase: `send-message`
- prompt: `こんにちは`
- requested_context_tokens: `4096`
- resolved_engine_max_num_tokens: `4096`
- max_output_tokens (legacy context alias): `4096`
- max_output_tokens_list: `4096`
- model_path_source: `auto`
- generic_fallback_model_configured: `true`
- model_path: `/data/user/0/io.github.ninbyo02.lami.gpucontrolled/files/cases/controlled_accelerator_4096_null_1789208660/model.litertlm`
- model_exists: `true`
- model_length: `2583085056`
- finish_reason: `unknown`
- stop_reason: `unknown`
- status: `success`
- reason: `completed`
- send_exception_class: `none`
- send_exception_message: `none`
- send_exception_cause_chain: `none`
- send_api_variant: `gallery_contents_callback`
- sampler_top_k: `64`
- sampler_top_p: `0.95`
- sampler_temperature: `1.0`
- conversation_config_used: `true`
- contents_api_used: `true`
- measured_prefill_tokens: `unavailable`
- prefill_token_source: `unavailable`
- output_token_source: `unavailable`
- emit_count: `12`
- nonempty_emit_count: `12`
- raw_output_length: `27`
- sanitized_output_length: `27`
- first_nonempty_emit_ms: `150`
- flow_exception_type: `none`
- finish_reason_available: `false`
- stop_reason_available: `false`
- callback_on_message_count: `12`
- callback_on_done_count: `1`
- callback_on_error_count: `0`
- chunk_type_length_summary: `types_lengths:[Text:5, Text:1, Text:2, Text:2, Text:1, Text:1, Text:3, Text:3, Text:4, Text:1, Text:1, Text:3]`
- flow_partial_raw_output: ``
- flow_partial_emit_count: `0`
- flow_partial_nonempty_emit_count: `0`
- flow_partial_first_nonempty_ms: `unavailable`
- intentionally_leaked_for_diagnostic: `false`
- fallback_used: `false`
- timeout: `false`
- fresh_crash: `false`

### raw_output

```text
こんにちは！何かお手伝いできることはありますか？ 😊
```

### sanitized_output

```text
こんにちは！何かお手伝いできることはありますか？ 😊
```

