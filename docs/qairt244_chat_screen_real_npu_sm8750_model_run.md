# QAIRT244 ChatScreen DEV-only SM8750 NPU Model Run

## 2026-05-24 128-Token Bounded Phase

The runner default artifact now points to the 128-token custom native artifact
for the DEV-only qairt244 SM8750 internal intent route:

```text
artifacts/litert_custom_build/20260524_170102_qairt244_128token_utf8prompt
```

The internal intent dispatch uses `--ei max_output_tokens 128` and still avoids
`adb shell input text`. The expected diagnostics for single-device confirmation
are `prompt_source=internal_intent`,
`prompt_validation_mode=utf8_internal_intent`,
`native_prompt_validation_mode=utf8_internal_intent`, `utf8_allowed=true`,
`max_output_tokens=128`, `native_max_output_tokens_limit=128`,
`npu_backend=NPU`, and
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`.

The native artifact provenance is recorded in
`docs/qairt244_native_artifact_reproducibility.md`. This remains a
customBuildExperimentDebug-only bounded phase: it does not promote
`Backend.NPU`, does not add automatic fallback, does not support
generic/E4B/qcs8275 models, and does not elevate the standard UI route.

128-token single-device confirmation:

- Artifact: `artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_170917`
- Prompt: `こんにちは`
- Result: `success`
- `requested_prompt=こんにちは`, `actual_prompt=こんにちは`, `normalized_prompt=こんにちは`
- `prompt_source=internal_intent`
- `prompt_validation_mode=utf8_internal_intent`
- `native_prompt_validation_mode=utf8_internal_intent`
- `utf8_allowed=true`
- `max_output_tokens=128`
- `native_max_output_tokens_limit=128`
- `run_decode_reached=true`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `decode_elapsed_ms=3156`
- `fallback_used=false`, `timeout=false`, `fresh_crash=false`
- `ui_cleanup_wait_status=success`
- No `duplicate_run_blocked`, `Responding...`, `Stop Button`, or `応答中` marker remained.

128-token bounded Phase A stability evidence:

```text
prompt_mode=internal_intent
prompts=こんにちは, テスト, ラミィ
artifacts=artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_172255,
          artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_172346,
          artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_172425
success_rate=3/3
max_output_tokens=128
native_max_output_tokens_limit=128
prompt_match=requested/actual/normalized all matched
prompt_validation_mode=utf8_internal_intent
native_prompt_validation_mode=utf8_internal_intent
utf8_allowed=true
run_decode_reached=true
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
duplicate_run_blocked=not observed
ui_cleanup_wait_status=success
ui_residual_markers=Responding.../Stop Button/応答中 not observed
decode_elapsed_ms_range=40..3152
```

The `40 ms` lower-bound decode timing is stability evidence for a completed
bounded DEV run, not a formal generation speed value.

This is a DEV-only bounded experiment for the qairt244 SM8750 internal intent
route, not production or normal-route NPU enablement. Do not raise beyond 128
without a new bounded native guard, artifact, and single-device evidence. The
next planned work is Settings cleanup; that cleanup has not started in this
docs-only update.

## 2026-05-24 64-Token Bounded Phase

The runner default artifact now points to the 64-token custom native artifact
for the DEV-only qairt244 SM8750 internal intent route:

```text
artifacts/litert_custom_build/20260524_162218_qairt244_64token_utf8prompt
```

The internal intent dispatch uses `--ei max_output_tokens 64` and still avoids
`adb shell input text`. The expected diagnostics for single-device confirmation
are `prompt_source=internal_intent`,
`prompt_validation_mode=utf8_internal_intent`,
`native_prompt_validation_mode=utf8_internal_intent`, `utf8_allowed=true`,
`max_output_tokens=64`, `native_max_output_tokens_limit=64`,
`npu_backend=NPU`, and
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`.

The native artifact provenance is recorded in
`docs/qairt244_native_artifact_reproducibility.md`. This remains a
customBuildExperimentDebug-only bounded phase: it does not promote
`Backend.NPU`, does not add automatic fallback, does not support
generic/E4B/qcs8275 models, and does not elevate the standard UI route.

64-token single-device confirmation:

- Artifact: `artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_163448`
- Prompt: `こんにちは`
- Result: `success`
- `requested_prompt=こんにちは`, `actual_prompt=こんにちは`, `normalized_prompt=こんにちは`
- `prompt_source=internal_intent`
- `prompt_validation_mode=utf8_internal_intent`
- `native_prompt_validation_mode=utf8_internal_intent`
- `utf8_allowed=true`
- `max_output_tokens=64`
- `native_max_output_tokens_limit=64`
- `run_decode_reached=true`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `decode_elapsed_ms=1463`
- `fallback_used=false`, `timeout=false`, `fresh_crash=false`
- `ui_cleanup_wait_status=success`
- No `duplicate_run_blocked`, `Responding...`, `Stop Button`, or `応答中` marker remained.

64-token bounded Phase A stability evidence:

```text
prompt_mode=internal_intent
prompts=こんにちは, テスト, ラミィ
artifacts=artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_164341,
          artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_164421,
          artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_164450
success_rate=3/3
max_output_tokens=64
native_max_output_tokens_limit=64
prompt_match=requested/actual/normalized all matched
prompt_validation_mode=utf8_internal_intent
native_prompt_validation_mode=utf8_internal_intent
utf8_allowed=true
run_decode_reached=true
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
duplicate_run_blocked=not observed
ui_cleanup_wait_status=success
ui_residual_markers=Responding.../Stop Button/応答中 not observed
decode_elapsed_ms_range=40..1959
```

This is a DEV-only bounded experiment for the qairt244 SM8750 internal intent
route, not production or normal-route NPU enablement. The later 128-token phase
is recorded above.

## 2026-05-24 32-Token Bounded Phase

The runner default artifact now points to the 32-token custom native artifact
for the DEV-only qairt244 SM8750 internal intent route:

```text
artifacts/litert_custom_build/20260524_155121_qairt244_32token_utf8prompt
```

The internal intent dispatch uses `--ei max_output_tokens 32` and still avoids
`adb shell input text`. The expected diagnostics for single-device confirmation
and the bounded stability evidence are `prompt_source=internal_intent`,
`prompt_validation_mode=utf8_internal_intent`,
`native_prompt_validation_mode=utf8_internal_intent`, `utf8_allowed=true`,
`max_output_tokens=32`, `native_max_output_tokens_limit=32`,
`npu_backend=NPU`, and
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`.


32-token single-device confirmation:

- Artifact: `artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_160053`
- Prompt: `こんにちは`
- Result: `success`
- `requested_prompt=こんにちは`, `actual_prompt=こんにちは`, `normalized_prompt=こんにちは`
- `prompt_source=internal_intent`
- `prompt_validation_mode=utf8_internal_intent`
- `native_prompt_validation_mode=utf8_internal_intent`
- `utf8_allowed=true`
- `max_output_tokens=32`
- `native_max_output_tokens_limit=32`
- `run_decode_reached=true`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `decode_elapsed_ms=817`
- `fallback_used=false`, `timeout=false`, `fresh_crash=false`
- `ui_cleanup_wait_status=success`
- No `duplicate_run_blocked`, `Responding...`, `Stop Button`, or `応答中` marker remained.

32-token bounded Phase A stability evidence:

```text
prompt_mode=internal_intent
prompts=こんにちは, テスト, ラミィ
success_rate=3/3
max_output_tokens=32
native_max_output_tokens_limit=32
prompt_match=requested/actual/normalized all matched
prompt_validation_mode=utf8_internal_intent
native_prompt_validation_mode=utf8_internal_intent
utf8_allowed=true
run_decode_reached=true
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
duplicate_run_blocked=not observed
ui_cleanup_wait_status=success
decode_elapsed_ms_range=40..943
```

The `40 ms` lower-bound decode timing is evidence of a completed bounded
short-output run, but it is not a formal throughput or latency claim. Treat it
as potentially affected by short output, measurement granularity, cache effects,
or similar run-local factors.


## 2026-05-24 UTF-8 Internal Intent Confirmation

The UTF-8 internal prompt path is now wired through the
customBuildExperimentDebug-only non-exported receiver
`io.github.ninbyo02.lami.npu.DevQairt244PromptReceiver` with action
`io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT`. The receiver is declared
with `android:exported="false"`; the runner dispatches it from the app UID with
`adb shell run-as io.github.ninbyo02.lami.customnpu am broadcast --user 0`, so
no exported external entrypoint is added.

The runner `--prompt-mode internal_intent` path sends Intent extras instead of
`adb shell input text`. It records `requested_prompt`, `actual_prompt`,
`normalized_prompt`, `intent_dispatch_status`, `prompt_source=internal_intent`,
`prompt_validation_mode=utf8_internal_intent`,
`native_prompt_validation_mode=utf8_internal_intent`, and `utf8_allowed=true`.
The existing `ui_text` mode remains ASCII-only.

Real-device confirmation artifact:
`artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_151712`.
The run used prompt `こんにちは`, target model
`gemma-4-E2B-it_qualcomm_sm8750.litertlm`, and `max_output_tokens=16`. The
recorded result is `success` with `requested_prompt=こんにちは`,
`actual_prompt=こんにちは`, `normalized_prompt=こんにちは`,
`run_decode_reached=true`, `npu_backend=NPU`,
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`,
`decode_elapsed_ms=445`, `fallback_used=false`, `timeout=false`,
`fresh_crash=false`, and `ui_cleanup_wait_status=success`. The post-run UI
scan found no remaining `Responding...`, `Stop Button`, or `応答中` marker.

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

When the toggle is ON and the user sends from the local ChatScreen target, the app calls the qairt244 SM8750 DEV-only adapter with `max_output_tokens=64`. The model basename must still exactly match `gemma-4-E2B-it_qualcomm_sm8750.litertlm`; generic, E4B, and qcs8275 models remain rejected by the Kotlin resolver. The path does not copy or delete model files.

The DEV UI route does not fallback to GPU or CPU. On failure it inserts a non-streaming assistant message like `DEV NPU route failed: <reason>` and leaves normal local inference untouched. It does not connect TTS or streaming sentence TTS. Stop cancellation is intentionally best-effort because the guarded run is bounded to a short lower-level decode.

Success/failure diagnostics to inspect:

```text
selected_route=qairt244_sm8750_dev_npu
resolved_model_basename=gemma-4-E2B-it_qualcomm_sm8750.litertlm
required_sm8750_model_path=true
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
decode_elapsed_ms=<value>
max_output_tokens=128
fallback_used=false
```

## Safety And Diagnostics Phase

As of 2026-05-24, the DEV UI route is in a safety/diagnostics phase. It remains `customBuildExperimentDebug` scoped, defaults OFF, and is entered only when `BuildConfig.CUSTOM_BUILD_EXPERIMENT && dev_enable_qairt244_sm8750_npu_route` is true. With the toggle OFF, ChatScreen falls through to the existing local inference path; the standard local route, GPU fallback behavior, and held-official-flow are not changed by this experiment.

`max_output_tokens` is now fixed at `128` for the 128-token phase. The DEV route is a short, lower-level, non-streaming run, so Stop is best-effort and is not guaranteed to behave like normal streaming cancellation. The UI must clear its generating state on success, failure, and exception, and the DEV branch also keeps an in-process duplicate-run guard so repeated sends do not start overlapping qairt244 runs.

Failure handling intentionally does not fallback to GPU or CPU. Falling back would hide the exact NPU failure stage and could make SM8750 model validation ambiguous. Failure diagnostics should include:

```text
selected_route=qairt244_sm8750_dev_npu
failure_stage=<preflight|prompt_validation|route_gate|model_resolution|engine_or_decode|adapter_execution|timeout|native_result|ui_exception>
stop_reason=<reason>
resolved_model_basename=<value>
required_sm8750_model_path=<true|false>
fallback_used=false
```

Before raising the token cap beyond `128`, require a new bounded native guard and repeated evidence that: the exact SM8750 basename is selected, `required_sm8750_model_path=true`, `npu_backend=NPU`, `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`, no GPU/CPU fallback is detected, generating state clears after success/failure/Stop, duplicate sends are blocked, and no TTS/streaming/standard-route side effects appear.

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

As of the runner prompt-input stabilization phase, `scripts/run_qairt244_chat_screen_real_npu_sm8750_model_run.sh` accepts `--prompt <ascii-prompt>` in default `--prompt-mode ui_text` for controlled stability checks. The runner restricts this UI text path to ASCII alphanumeric plus `._-`, saves the current IME, temporarily selects an ADB/Latin IME when available, verifies the typed ChatScreen field before pressing Send, retries after KEYCODE_LANGUAGE_SWITCH when the IME still rewrites ASCII, and records `requested_prompt`, `actual_prompt`, `normalized_prompt`, `prompt_source`, `prompt_input_status`, and `prompt_input_failure_reason` in each artifact. Non-ASCII prompts such as Japanese are treated as `unsupported_non_ascii_prompt` in UI text mode and stop before NPU execution; Japanese prompt coverage belongs to `--prompt-mode internal_intent` only.

The current stability prompt set is `Hello`, `test`, and `OK`. Each run still uses `max_output_tokens=64`, exact SM8750 model selection, no model copy/delete, no fallback, and the DEV-only route.

## Non-ASCII Prompt Plan

Japanese/non-ASCII prompt input remains outside the stable ASCII UI runner. The design comparison and recommended next phase are documented in `docs/qairt244_non_ascii_prompt_plan.md`; the default UI text mode should continue to stop non-ASCII prompts before send with `unsupported_non_ascii_prompt`.

## Internal Intent Prompt Mode

The runner reserves `--prompt-mode internal_intent` for the DEV-only app entrypoint and non-ASCII prompt path. UI text mode remains ASCII-only and must continue to reject Japanese before send; internal intent is the only path allowed to carry UTF-8 prompts into the DEV qairt244 route.

The native editable-prompt artifact for this path was rebuilt as `artifacts/litert_custom_build/20260524_162218_qairt244_64token_utf8prompt`. Its `liblitertlm_jni.so` SHA-256 is `cd85bd4979cac7325148d8ad72bc0ee69cbf684d9f7e9373fab07844b5110ad6`, and the JNI build log is `artifacts/litert_custom_build/20260524_162218_qairt244_64token_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log`. The native validator records `native_prompt_validation_mode=utf8_internal_intent` and `utf8_allowed=true`, rejects empty/NUL/invalid UTF-8/over-32-code-point prompts, and keeps `max_output_tokens` bounded to `1..64`.

Reserved action:

```text
io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT
```

Planned runner invocation:

```sh
scripts/run_qairt244_chat_screen_real_npu_sm8750_model_run.sh \
  --run \
  --prompt-mode internal_intent \
  --prompt 'こんにちは'
```

Activity-style command template, pending the 担当A component name:

```sh
adb -s <device> shell am start -W \
  -a io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT \
  -n io.github.ninbyo02.lami.customnpu/<internal-entrypoint-component> \
  --es requested_prompt '<utf8-prompt>' \
  --ez dev_enable_qairt244_sm8750_npu_route true \
  --ei max_output_tokens 64
```

Broadcast-style command template, if the entrypoint is a receiver:

```sh
adb -s <device> shell am broadcast \
  -a io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT \
  -p io.github.ninbyo02.lami.customnpu \
  --es requested_prompt '<utf8-prompt>' \
  --ez dev_enable_qairt244_sm8750_npu_route true \
  --ei max_output_tokens 64
```

Artifact fields planned for `internal_intent` runs:

```text
requested_prompt=<runner requested prompt>
actual_prompt=<prompt accepted by app entrypoint>
normalized_prompt=<native normalized prompt>
prompt_source=internal_intent
intent_dispatch_status=not_started|dispatched|accepted|rejected|entrypoint_missing|timeout|failure
internal_intent_action=io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT
adb_shell_input_text_unicode=false
ui_text_ascii_only=true
native_prompt_validation_mode=utf8_internal_intent
utf8_allowed=true
```

Japanese prompts are allowed only through this internal intent flow. The runner must not use `adb shell input text` for Japanese because that path is Unicode-fragile on the target device and can fail or be rewritten before the app receives the intended prompt. Keeping UI text mode ASCII-only preserves the stable qairt244 route baseline while non-ASCII coverage gets a separate artifact contract.

## Diagnosis Display Phase

As of 2026-05-24, the ChatScreen inference stats surface keeps the qairt244 SM8750 evidence in a dedicated DEV section instead of folding it into the normal generation-speed rows. This remains DEV-only and does not enable production `Backend.NPU`, automatic fallback, generic/E4B/qcs8275 models, TTS, Markdown streaming, or the standard selected-path NPU route.

The dedicated section is shown only for `selected_route=qairt244_sm8750_dev_npu` stats and summarizes: exact model basename `gemma-4-E2B-it_qualcomm_sm8750.litertlm`, `max_output_tokens=64`, `native_max_output_tokens_limit=64`, `required_sm8750_model_path=true`, RunDecode reachability, `decode_elapsed_ms`, `npu_backend=NPU`, `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`, `fallback_used=false`, and the DEV UI cleanup status. Decode elapsed time is treated as DEV evidence, not as the normal token/s speed metric.

## Native Artifact Reproducibility

The native custom build provenance and rebuild checklist for `artifacts/litert_custom_build/20260524_162218_qairt244_64token_utf8prompt` are documented in `docs/qairt244_native_artifact_reproducibility.md`. Keep this route DEV-only until those native changes are represented as a reproducible patch or pinned external checkout.

## Promotion Review / 昇格判断

Conclusion as of 2026-05-24: the qairt244 SM8750 route remains a DEV-only experiment and is not ready for immediate promotion into the normal candidate set. The current evidence is for bounded DEV paths on one SM8750 device, and the route still depends on a custom native artifact, a single exact model basename, non-streaming lower-level decode, and a narrow runner prompt path.

Open items before promotion:

- Token budget: `max_output_tokens=128` is being validated as a bounded DEV-only phase; normal use still needs broader repeated evidence before the route can represent useful generation behavior.
- Streaming and cancellation: the route is non-streaming and Stop is best-effort, so it does not yet match normal ChatScreen streaming UX or cancellation expectations.
- Prompt coverage: UI text automation remains ASCII-only, but the DEV-only internal intent path has Japanese UTF-8 evidence for `こんにちは`.
- Native artifact reproducibility: the successful path depends on a custom native artifact, currently `artifacts/litert_custom_build/20260524_170102_qairt244_128token_utf8prompt`; the source patch, build commands, ABI contents, and packaging rules must be reproducible without staging `.so` binaries in Git.
- Runtime distribution: `liblitertlm_jni.so`, QAIRT/QNN runtime libraries, and model placement need a documented install/update story before any non-DEV candidate is exposed.
- Evidence quality: `QNN_HTP_V79_FastRPC_native_diag` is useful proof for current runs, but promotion needs a stable evidence contract that distinguishes HTP/NPU execution from CPU/GPU fallback across failures and logcat gaps.
- Thermal and memory stability: current runs are short. Promotion requires longer soak, repeated runs, post-run memory checks, and thermal observation under device load.
- Failure UX: failures currently surface as DEV messages and diagnostics. A normal candidate needs clear user-facing errors, retry behavior, and no silent fallback unless fallback is deliberately designed and reported.
- Persistence and stats: DEV diagnostics are visible, but production-facing stats, DB persistence strategy, and normal speed display integration are not settled. Decode elapsed time must not be confused with token/s until token accounting is available.
- Device/model gating: the current guard is SM8750 basename exact-match. Promotion needs device capability checks, model availability checks, and clear behavior when the exact model is absent.

Recommended promotion phases:

- Phase A: Keep the current DEV-only route. Continue using explicit toggle, exact SM8750 model guard, `max_output_tokens=128`, no fallback, and diagnostic-first artifacts.
- Phase B: Move to a hidden experimental option only after repeated bounded runs cover multiple prompts without timeout, crash, stale UI, or fallback ambiguity.
- Phase C: Expose an experimental NPU candidate only when device detection, exact model detection, native artifact provenance, and QNN/HTP evidence all pass preflight.
- Phase D: Consider `Backend.NPU` candidate promotion only after 128-token bounded runs, Japanese prompt coverage, failure UX, cleanup, memory, and thermal evidence meet the same bar as existing local inference candidates.
- Phase E: Normal user-facing settings only after packaging/reproducibility, update behavior, stats, cancellation, and support boundaries are documented and tested.

Minimum gates before leaving Phase A:

- 10 or more consecutive 128-token DEV runs across `Hello`, `test`, `OK`, and at least one non-ASCII internal intent prompt path, all with `result=success` when actually sent.
- Bounded 128-token phase passes with exact basename, `required_sm8750_model_path=true`, RunDecode reached, `npu_backend=NPU`, `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`, `fallback_used=false`, `timeout=false`, and `fresh_crash=false`.
- UI cleanup remains 100% successful: no stale `Responding...`, Stop button, or `応答中...` after success/failure.
- Native build is reproducible from source instructions, and no `.so`, `.litertlm`, `.apk`, `.aar`, `.zip`, `.tar`, or `.gz` artifacts are tracked in Git.
- Diagnostics identify failure stage, model basename, native token limit, RunDecode reachability, QNN/HTP evidence, fallback status, and cleanup status.
- Normal-route behavior remains unchanged with the DEV toggle OFF.

Next action: do not promote yet. Keep Phase A, then decide between broader native artifact reproducibility work and repeated Japanese/non-ASCII prompt evidence before increasing token count beyond 128 or exposing a hidden experimental candidate.

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
