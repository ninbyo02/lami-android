# NPU short output investigation

## Current observation

NPU Standard Route S1 reaches the intended QNN / HTP path and returns success:

- `route_type=standard_chat_screen_s5_npu_tts`
- `standard_route_connected=true`
- `run_decode_reached=true`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `fresh_crash=false`
- `status=success`
- `reason=success`
- `requested_max_output_tokens=32`
- `effective_max_output_tokens=32`
- `max_output_tokens=32`

The observed output is still very short:

- `input_prompt=こんにちは`
- `raw_output= こんにちは。`
- `sanitized_output=こんにちは。`
- `npu_s1_output_tokens=6`
- `npu_s1_token_count_mode=estimated_code_points`

`max_output_tokens=32` is an upper bound. It does not guarantee that generation continues until 32 tokens.

## Investigation hypotheses

- EOS may be emitted early by the model or runtime.
- LiteRT-LM / model runtime finish reason may not be exposed through the current app layer.
- `prompt_tail_variant=raw_dialog_tail_variant_c` may naturally encourage a short answer.
- Stop condition, EOS, and finished callback may currently collapse into the same success path.
- `npu_s1_output_tokens=6` is currently `estimated_code_points`; it is not tokenizer-grounded.
- The NPU path can be healthy while the model chooses a short completion.

## Added DEV telemetry

The NPU Standard Route S1 DEV diagnostics now include a short-output telemetry section:

```text
[DEV診断: NPU S1 short output telemetry]
finish_reason=unavailable
stop_reason=unavailable
eos_detected=unavailable
raw_finish_status=not_exposed
generation_end_reason_source=not_exposed
tokenizer_output_tokens=unavailable
tokenizer_input_tokens=unavailable
tokenizer_total_tokens=unavailable
output_token_count_source=estimated_code_points_not_tokenizer
prompt_token_count_source=code_points
final_input_length_chars=...
final_input_tail_chars=...
final_input_tail_preview=...
model_reported_output_tokens=unavailable
model_reported_input_tokens=unavailable
stop_sequence_matched=unavailable
stop_sequence_value=unavailable
max_output_tokens_reached=false
```

Unavailable fields are intentionally explicit. They identify the telemetry still needed from LiteRT-LM / QNN / tokenizer integration without implying that the app can currently observe it.

## Additional telemetry wanted

- runtime `finish_reason`
- runtime `stop_reason`
- whether EOS was detected
- raw finish status from the generation callback
- tokenizer output token count
- prompt token count
- total token count
- final input tail used by the runtime
- whether max output tokens was reached by tokenizer count
- whether a configured stop sequence matched

## Do not change during this investigation

- prompt template
- sanitizer
- stop sequence
- max token setting
- generation config
- NPU / GPU fallback policy
- safety guard conditions

## NPU S1 20-run DEV test

The DEV diagnostics include a `NPU S1 20回連続テスト` button. It runs a diagnostic-only sequential runner with:

- prompt: `こんにちは`
- run count: `20`
- max output tokens: current S1 setting, `32`
- TTS: off
- chat history / DB save: not used
- normal chat streaming / markdown path: not used

Each run records output, timing, short-output telemetry, and App/System memory before, after, and five seconds after the run. The five-second memory value is intended to avoid over-reading immediate post-dispose accounting, especially for QNN / LiteRT / mmap or shared native resources.

The runner stops early if any safety condition appears:

- `low_memory=true`
- `fallback_used=true`
- `fresh_crash=true`
- `timeout=true`
- `safety_guard_triggered=true`
- `run_decode_reached=false`
- `status != success`
- the run is cancelled
- system memory is near threshold
- a run takes abnormally long

## What to look for

- `all_outputs_same`: true means every completed run produced the same normalized output.
- `most_common_output`: if this remains `こんにちは。`, continue short-output finish reason investigation.
- `npu_s1_output_tokens` and `npu_s1_token_count_mode`: confirm whether the count is still estimated code points.
- `finish_reason` / `stop_reason`: currently `unavailable` until runtime exposure is added.
- `memory_recovery_5s_total_pss_mb`
- `memory_recovery_5s_native_heap_pss_mb`
- `memory_recovery_5s_system_available_memory_mb`
- `memory_growth_suspected`

If the output is stable and the five-second memory values return near baseline, the current evidence points away from a simple app-side malloc leak. If memory keeps growing, compare the copied DEV diagnostics with:

```shell
adb shell dumpsys meminfo io.github.ninbyo02.lami
```

QNN / NPU dedicated memory is not directly separated by the standard Android APIs used in these diagnostics.
