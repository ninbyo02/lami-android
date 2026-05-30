# qairt244 DEV-only NPU output sanitizer

- artifact: `artifacts/qairt244_npu_output_sanitizer/20260525_015040`
- device: `192.168.52.52:34993`
- prompt: `こんにちは`
- template_mode: `gemma_it_like`
- requested_template_mode: `gemma_it_like`
- status: `success`
- result: `success`
- reasonCode: `success`
- max_output_tokens: `128`
- npu_backend: `NPU`
- npu_backend_evidence: `QNN_HTP_V79_FastRPC_native_diag`
- fallback_used: `false`
- timeout: `false`
- fresh_crash: `false`
- sanitizer_applied: `true`
- removed_template_token_count: `2`
- removed_prompt_echo: `true`
- raw_output_length: `59`
- sanitized_output_length: `24`
- ui_cleanup_wait_status: `success`

## Raw Output

```text
>こんにちは
<end_of_turn>
こんにちは！何かお手伝いできることはありますか？
<end_of_turn>
```

## Sanitized Output

```text
こんにちは！何かお手伝いできることはありますか？
```

## Result

```text
marker=qairt244_editable_prompt_smoke_v1
base_marker=qairt244_short_multitoken_smoke_v1
result=success
actual_prompt=<start_of_turn>user
こんにちは
<end_of_turn>
<start_of_turn>model
normalized_prompt=<start_of_turn>user
こんにちは
<end_of_turn>
<start_of_turn>model
prompt_source=editable_prompt
native_prompt_validation_mode=utf8_hidden_template_experiment
utf8_allowed=true
prompt_bytes=70
prompt_input_code_points=60
prompt_input_code_point_limit=128
prompt_input_limit_mode=hidden_template_experiment
native_prompt_input_code_point_limit=128
native_prompt_input_limit_mode=hidden_template_experiment
prompt_token_count=unavailable
prompt_token_count_source=not_exposed_by_lower_level_entrypoint
max_output_tokens=128
native_max_output_tokens_limit=128
conversation_created=no
generate_response=no
normal_ui_connected=no
selected_path_npu_normal_route=no
output_bytes=117
output_token_count=unavailable
output_token_count_source=not_exposed_by_RunDecode_response
elapsed_ms=2272
model_assets_elapsed_ms=0
engine_settings_elapsed_ms=0
engine_create_elapsed_ms=1327
session_create_elapsed_ms=0
prefill_elapsed_ms=72
decode_elapsed_ms=759
cleanup_elapsed_ms=111
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
detail=completed
output=>こんにちは
<end_of_turn>
こんにちは！何かお手伝いできることはありますか？
<end_of_turn>
selected_route=qairt244_sm8750_hidden_npu
resolved_model_basename=1779611766669_gemma-4-E2B-it_qualcomm_sm8750.litertlm
canonical_model_basename=gemma-4-E2B-it_qualcomm_sm8750.litertlm
timestamp_prefix_stripped=true
required_sm8750_model_path=true
requested_prompt=こんにちは
actual_prompt=こんにちは
raw_user_prompt=こんにちは
normalized_prompt=こんにちは
final_model_input=<start_of_turn>user\nこんにちは\n<end_of_turn>\n<start_of_turn>model
final_model_input_length=60
conversation_history_count=0
system_prompt_used=none
chat_template_used=gemma_it_like
template_mode=gemma_it_like
template_prefix_length=20
template_suffix_length=35
prompt_source=chat_screen
prompt_formatting_mode=hidden_prompt_template_experiment
prompt_validation_mode=utf8_hidden_template_experiment
prompt_input_code_points=60
prompt_input_code_point_limit=128
prompt_input_limit_mode=hidden_template_experiment
native_prompt_validation_mode=utf8_hidden_template_experiment
native_prompt_input_code_point_limit=128
native_prompt_input_limit_mode=hidden_template_experiment
utf8_allowed=true
max_output_tokens=128
run_decode_reached=true
fallback_used=false
timeout=false
fresh_crash=false
db=false
tts=false
markdown=false
streaming=false
selected_path_npu_saved=false
route_type=standard_hidden_chat_screen
raw_native_output=>こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？\n<end_of_turn>
raw_native_output_length=59
raw_output=>こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？\n<end_of_turn>
raw_output_length=59
sanitized_output=こんにちは！何かお手伝いできることはありますか？
sanitized_output_length=24
sanitizer_applied=true
removed_template_token_count=2
removed_prompt_echo=true
adapter_output=こんにちは！何かお手伝いできることはありますか？
adapter_output_length=24
output_token_count=unavailable
finish_reason=not_exposed_by_lower_level_entrypoint
stop_reason=
eos_detected=true
output_contains_replacement_chars=false
replacement_char_count=0
output_contains_control_chars=true
output_unicode_summary=utf16_length=59;code_point_count=59;utf8_byte_count=117;classification=unicode_decoded_string;replacement_char_count=0;control_chars=U+000Ax3;white_circle_u3007_count=0;question_mark_count=1;first_code_points=U+003E U+3053 U+3093 U+306B U+3061 U+306F U+000A U+003C U+0065 U+006E U+0064 U+005F U+006F U+0066 U+005F U+0074 U+0075 U+0072 U+006E U+003E U+000A U+3053 U+3093 U+306B U+3061 U+306F U+FF01 U+4F55 U+304B U+304A U+624B U+4F1D
quality_classification=template_artifact
output_first_200_chars=>こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？\n<end_of_turn>
output_last_200_chars=>こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？\n<end_of_turn>
markdown_mode=non_streaming_direct_insert
repair_applied=false
qairt244_chat_screen_real_npu_adapter_v1 runId=chat-real-1779641441038-f2e4a0cf-34f1-41f0-9b97-d4c05bb35405 state=success elapsed_ms=2278 result=success output=こんにちは！何かお手伝いできることはありますか？ sanitizer_applied=true removed_template_token_count=2 removed_prompt_echo=true db=false tts=false markdown=false stream=false
```

## Display Diagnostics

```text
route_type=standard_hidden_chat_screen
selected_route=qairt244_sm8750_hidden_npu
assistant_message_id=receiver_runner
success=true
reasonCode=success
raw_user_prompt=こんにちは
normalized_prompt=こんにちは
final_model_input=<start_of_turn>user\nこんにちは\n<end_of_turn>\n<start_of_turn>model
final_model_input_length=60
conversation_history_count=0
system_prompt_used=none
chat_template_used=gemma_it_like
template_mode=gemma_it_like
template_prefix_length=20
template_suffix_length=35
prompt_source=chat_screen
prompt_validation_mode=utf8_hidden_template_experiment
prompt_input_code_points=60
prompt_input_code_point_limit=128
prompt_input_limit_mode=hidden_template_experiment
native_prompt_input_code_point_limit=128
native_prompt_input_limit_mode=hidden_template_experiment
prompt_formatting_mode=hidden_prompt_template_experiment
raw_native_output=>こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？\n<end_of_turn>
raw_native_output_length=59
raw_output=>こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？\n<end_of_turn>
raw_output_length=59
sanitized_output=こんにちは！何かお手伝いできることはありますか？
sanitized_output_length=24
sanitizer_applied=true
removed_template_token_count=2
removed_prompt_echo=true
adapter_output=こんにちは！何かお手伝いできることはありますか？
adapter_output_length=24
displayed_assistant_text=こんにちは！何かお手伝いできることはありますか？
displayed_assistant_text_length=24
output_token_count=unavailable
finish_reason=not_exposed_by_lower_level_entrypoint
stop_reason=
eos_detected=true
output_contains_replacement_chars=false
replacement_char_count=0
output_contains_control_chars=true
output_unicode_summary=utf16_length=59;code_point_count=59;utf8_byte_count=117;classification=unicode_decoded_string;replacement_char_count=0;control_chars=U+000Ax3;white_circle_u3007_count=0;question_mark_count=1;first_code_points=U+003E U+3053 U+3093 U+306B U+3061 U+306F U+000A U+003C U+0065 U+006E U+0064 U+005F U+006F U+0066 U+005F U+0074 U+0075 U+0072 U+006E U+003E U+000A U+3053 U+3093 U+306B U+3061 U+306F U+FF01 U+4F55 U+304B U+304A U+624B U+4F1D
quality_classification=template_artifact
output_first_200_chars=>こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？\n<end_of_turn>
output_last_200_chars=>こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？\n<end_of_turn>
max_output_tokens=128
decode_elapsed_ms=759
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
markdown_mode=non_streaming_direct_insert
repair_applied=false
streaming=false
```
