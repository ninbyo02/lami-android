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

When the toggle is ON and the user sends from the local ChatScreen target, the app calls the qairt244 SM8750 DEV-only adapter with `max_output_tokens=3`. The model basename must still exactly match `gemma-4-E2B-it_qualcomm_sm8750.litertlm`; generic, E4B, and qcs8275 models remain rejected by the Kotlin resolver. The path does not copy or delete model files.

The DEV UI route does not fallback to GPU or CPU. On failure it inserts a non-streaming assistant message like `DEV NPU route failed: <reason>` and leaves normal local inference untouched. It does not connect TTS or streaming sentence TTS. Stop cancellation is intentionally best-effort because the guarded run is bounded to a short lower-level decode.

Success/failure diagnostics to inspect:

```text
selected_route=qairt244_sm8750_dev_npu
resolved_model_basename=gemma-4-E2B-it_qualcomm_sm8750.litertlm
required_sm8750_model_path=true
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
decode_elapsed_ms=<value>
max_output_tokens=3
fallback_used=false
```
