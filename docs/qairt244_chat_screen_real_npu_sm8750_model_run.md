# QAIRT244 ChatScreen DEV-only SM8750 Model Guard

This DEV-only guard applies only to the `customBuildExperimentDebug` ChatScreen NPU route. It does not enable the standard NPU path, GPU fallback, held official flow, DB persistence, TTS, Markdown, streaming, or normal local inference.

Allowed runtime model basename:

```text
gemma-4-E2B-it_qualcomm_sm8750.litertlm
```

Kotlin guard:

- `Qairt244ModelPathResolver` scans app-private `files/local_models` for `.litertlm` files.
- Only filenames containing `qualcomm_sm8750` are execution candidates.
- Filenames containing `qcs8275` are rejected.
- Generic/standard `gemma-4-E2B-it.litertlm`, timestamped generic E2B, E4B, and other generic `.litertlm` files are candidate-excluded.
- Zero candidates returns `model_file_not_found`; two or more candidates returns `model_file_ambiguous`.
- `isRequiredSm8750ModelPath(path)` returns true only when the basename exactly equals `gemma-4-E2B-it_qualcomm_sm8750.litertlm`.
- `Qairt244DevOnlyNpuRouteAdapter` rechecks the resolved path before Engine creation or RunDecode and stops with `model_file_not_required_sm8750` if the basename is not exact.

Runner guard:

- `scripts/run_qairt244_chat_screen_real_npu_sm8750_model_run.sh` lists app-private `files/local_models` before any DEV-only run.
- The script permits the run only when the single `qualcomm_sm8750` candidate basename exactly matches `gemma-4-E2B-it_qualcomm_sm8750.litertlm`.
- Generic, qcs8275, standard E2B/E4B, missing, or multiple `qualcomm_sm8750` candidates stop before ChatScreen launch.
- The runner writes `model_files_listing.txt`, `model_basenames.txt`, `sm8750_model_candidates.txt`, `sm8750_model_preflight.txt`, and `resolved_model_path.txt` under `artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/<timestamp>/`.
- `runtime_marker_scan.txt` scans `logcat_tail.txt`, `native_diag.txt`, `result.txt`, and `summary.md`, prefixing each marker with the source filename so NPU evidence remains visible even when logcat has no QNN/HTP lines.
- The runner does not copy or delete model files. If the SM8750 model is already in app-private `files/local_models`, no Download copy is needed.

## 2026-05-24 DEV-only SM8750 Success Evidence

Artifact: `artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_102125/`

```text
route_commit=bae63d76
scan_improvement_commit=388cd4bf
device=192.168.52.52:37859
model=gemma-4-E2B-it_qualcomm_sm8750.litertlm
prompt=Hello
max_output_tokens=3
result=success
run_decode_reached=true
decode_elapsed_ms=88
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_gpu_cpu_detected=false
normal_ui_route_connected=false
scope=DEV-only route success, not production enablement
```

This confirms one bounded `--run` success through the ChatScreen DEV-only NPU route using the exact SM8750 model. The normal UI/local inference route remains disconnected from NPU and this result is not a standard-path rollout.

## DEV UI Experiment Route

The ChatScreen UI route remains experimental and DEV-only. In `customBuildExperimentDebug`, Settings exposes `DEV: SM8750 NPU実験` using preference key `dev_enable_qairt244_sm8750_npu_route`; the default is always OFF and the toggle is automatically cleared after a guarded attempt. This is separate from the standard local inference route and is not a production NPU enablement.

When the toggle is ON and the user sends from the local ChatScreen target, the app calls the qairt244 SM8750 DEV-only adapter with `max_output_tokens=16`. The model basename must still exactly match `gemma-4-E2B-it_qualcomm_sm8750.litertlm`; generic, E4B, and qcs8275 models remain rejected by the Kotlin resolver. The path does not copy or delete model files.

The DEV UI route does not fallback to GPU or CPU. On failure it inserts a non-streaming assistant message like `DEV NPU route failed: <reason>` and leaves normal local inference untouched. It does not connect TTS or streaming sentence TTS. Stop cancellation is intentionally best-effort because the guarded run is bounded to a short lower-level decode.

Success/failure diagnostics to inspect:

```text
selected_route=qairt244_sm8750_dev_npu
resolved_model_basename=gemma-4-E2B-it_qualcomm_sm8750.litertlm
required_sm8750_model_path=true
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
decode_elapsed_ms=<value>
max_output_tokens=16
fallback_used=false
```

## Safety And Diagnostics Phase

As of 2026-05-24, the DEV UI route is in a safety/diagnostics phase. It remains `customBuildExperimentDebug` scoped, defaults OFF, and is entered only when `BuildConfig.CUSTOM_BUILD_EXPERIMENT && dev_enable_qairt244_sm8750_npu_route` is true. With the toggle OFF, ChatScreen falls through to the existing local inference path; the standard local route, GPU fallback behavior, and held-official-flow are not changed by this experiment.

`max_output_tokens` is now fixed at `16` for the 16 token phase. The DEV route is a short, lower-level, non-streaming run, so Stop is best-effort and is not guaranteed to behave like normal streaming cancellation. The UI must clear its generating state on success, failure, and exception, and the DEV branch also keeps an in-process duplicate-run guard so repeated sends do not start overlapping qairt244 runs.

Failure handling intentionally does not fallback to GPU or CPU. Falling back would hide the exact NPU failure stage and could make SM8750 model validation ambiguous. Failure diagnostics should include:

```text
selected_route=qairt244_sm8750_dev_npu
failure_stage=<preflight|prompt_validation|route_gate|model_resolution|engine_or_decode|adapter_execution|timeout|native_result|ui_exception>
stop_reason=<reason>
resolved_model_basename=<value>
required_sm8750_model_path=<true|false>
fallback_used=false
```

Before raising the token cap beyond `16`, require repeated evidence that: the exact SM8750 basename is selected, `required_sm8750_model_path=true`, `npu_backend=NPU`, `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`, no GPU/CPU fallback is detected, generating state clears after success/failure/Stop, duplicate sends are blocked, and no TTS/streaming/standard-route side effects appear.

## 16 Token Phase

The current bounded DEV-only step raises only the qairt244 SM8750 experiment route from `max_output_tokens=8` to `max_output_tokens=16`. This remains DEV-only and does not enable `Backend.NPU`, automatic fallback, generic/E4B/qcs8275 models, TTS, Markdown streaming, or the standard selected-path NPU route.

The 16 token phase is acceptable only if one guarded `Hello` run records: exact basename `gemma-4-E2B-it_qualcomm_sm8750.litertlm`, `required_sm8750_model_path=true`, `max_output_tokens=16`, RunDecode reached, `npu_backend=NPU`, `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`, `fallback_used=false`, `fresh_crash=false`, `timeout=false`, no `duplicate_run_blocked`, and the ChatScreen loading state clears after completion.

Initial 16 token attempt on 2026-05-24 stopped before RunDecode in `artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_114330` because the staged native editable-prompt artifact still rejected 16 with `invalid_max_output_tokens value=16`. The native DEV guard was then changed from exact `8` to the bounded range `1..16`, rebuilt as `artifacts/litert_custom_build/20260524_114833_qairt244_16token`, and restaged for `customBuildExperimentDebug`.

16 token run evidence on 2026-05-24:

```text
commit_under_test=75bec3bd + local 16-token changes
custom_build_artifact=artifacts/litert_custom_build/20260524_114833_qairt244_16token
artifact=artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_115432
result=success
actual_prompt=Hello
normalized_prompt=Hello
output=! How अच्छे? (How are you?)
resolved_model_basename=gemma-4-E2B-it_qualcomm_sm8750.litertlm
required_sm8750_model_path=true
max_output_tokens=16
native_max_output_tokens_limit=16
run_decode=before RunDecode SetMaxOutputTokens(16)
decode_elapsed_ms=400
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
fresh_crash=false
timeout=false
duplicate_run_blocked=false
ui_cleanup_wait_status=success
responding_stop_stale_ui=false
rollback_condition_hit=false
```

## Runner Prompt Input Stability

As of the runner prompt-input stabilization phase, `scripts/run_qairt244_chat_screen_real_npu_sm8750_model_run.sh` accepts `--prompt <ascii-prompt>` for controlled stability checks. The runner restricts this path to ASCII alphanumeric plus `._-`, saves the current IME, temporarily selects an ADB/Latin IME when available, verifies the typed ChatScreen field before pressing Send, retries after KEYCODE_LANGUAGE_SWITCH when the IME still rewrites ASCII, and records `requested_prompt`, `actual_prompt`, `prompt_input_status`, and `prompt_input_failure_reason` in each artifact. Non-ASCII prompts such as Japanese are treated as `unsupported_non_ascii_prompt` and stop before NPU execution; Japanese prompt coverage is a separate phase.

The current stability prompt set is `Hello`, `test`, and `OK`. Each run still uses `max_output_tokens=16`, exact SM8750 model selection, no model copy/delete, no fallback, and the DEV-only route.

## Diagnosis Display Phase

As of 2026-05-24, the ChatScreen inference stats surface keeps the qairt244 SM8750 evidence in a dedicated DEV section instead of folding it into the normal generation-speed rows. This remains DEV-only and does not enable production `Backend.NPU`, automatic fallback, generic/E4B/qcs8275 models, TTS, Markdown streaming, or the standard selected-path NPU route.

The dedicated section is shown only for `selected_route=qairt244_sm8750_dev_npu` stats and summarizes: exact model basename `gemma-4-E2B-it_qualcomm_sm8750.litertlm`, `max_output_tokens=16`, `native_max_output_tokens_limit=16`, `required_sm8750_model_path=true`, RunDecode reachability, `decode_elapsed_ms`, `npu_backend=NPU`, `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`, `fallback_used=false`, and the DEV UI cleanup status. Decode elapsed time is treated as DEV evidence, not as the normal token/s speed metric.

## 8 Token Phase

The next bounded DEV-only step raises only the qairt244 SM8750 experiment route from `max_output_tokens=3` to `max_output_tokens=8`. This is not a production NPU rollout and still does not enable `Backend.NPU`, automatic fallback, generic/E4B/qcs8275 models, TTS, Markdown streaming, or the standard selected-path NPU route.

The 8 token phase is considered acceptable only if one guarded `Hello` run records: exact basename `gemma-4-E2B-it_qualcomm_sm8750.litertlm`, `required_sm8750_model_path=true`, `max_output_tokens=8`, RunDecode reached, `npu_backend=NPU`, `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`, `fallback_used=false`, `fresh_crash=false`, `timeout=false`, no `duplicate_run_blocked`, and the ChatScreen loading state clears after completion.

8 token run evidence on 2026-05-24:

```text
commit_under_test=1509df12 + local 8-token changes
artifact=artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_112050
custom_build_artifact=artifacts/qairt244_editable_prompt_entrypoint_build/20260524_8token
result=success
actual_prompt=Hello
normalized_prompt=Hello
output=! How अच्छे? (How are you
resolved_model_basename=gemma-4-E2B-it_qualcomm_sm8750.litertlm
required_sm8750_model_path=true
max_output_tokens=8
run_decode=before RunDecode SetMaxOutputTokens(8)
decode_elapsed_ms=238
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
fresh_crash=false
timeout=false
duplicate_run_blocked=false
rollback_condition_hit=false
```

The immediate post-run UI dump still showed a stale `Responding...`/Stop state, so the 8-token change also adds DEV-only cleanup with `viewModel.resetUiState()` in the ChatScreen route `finally` block. That cleanup was build-tested after the run; it was not re-run on device to preserve the one-run constraint for this phase.
