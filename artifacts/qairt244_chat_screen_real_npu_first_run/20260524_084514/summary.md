# QAIRT ChatScreen DEV-only Real NPU First Run

Artifact: `artifacts/qairt244_chat_screen_real_npu_first_run/20260524_084514`

```text
executed=true
wait_status=1
result=failure
actual_prompt=Hello
normalized_prompt=Hello
output=not_run
max_output_tokens=3
timeout=false
fresh_crash=false
npu_evidence=missing_for_this_failed_run
side_effect_flags_false=true
db=false
tts=false
markdown=false
streaming=false
selected_path_npu_saved=false
rollback_condition_hit=true
rollback_reason=model-file-not-found
```

## Toggle

- before/reset: `false`
- after_on: `true`
- after_off: `false`

## Preflight

```text
custom_build_artifact=artifacts/qairt244_editable_prompt_entrypoint_build/20260523_183705
artifact_present=true
native_marker=qairt244_editable_prompt_smoke_v1
native_marker_present=true
set_max_output_tokens_3_evidence=true
route_marker=qairt244_chat_screen_real_npu_adapter_v1
route_code_present=true
prompt=Hello
max_output_tokens=3
run_requested=true
db=false
tts=false
markdown=false
streaming=false
selected_path_npu_persistent=false
```

## Failure Classification

- classification: `rollback-model-file-not-found`
- native detail: `model-file-not-found`
- expected path used by adapter: `/data/user/0/io.github.ninbyo02.lami.customnpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm`
- observed app-private model file: see `model_probe.txt`
- `Engine.initialize`: not reached
- `RunDecode`: not reached
- QNN/HTP/FastRPC evidence for this run: missing in `native_diag.txt`
- toggle recovery: OFF confirmed
- normal ChatScreen side effects: not connected
