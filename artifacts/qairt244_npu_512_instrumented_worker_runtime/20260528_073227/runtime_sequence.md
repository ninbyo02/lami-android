# Runtime sequence

- mode: `sequential_soft_reset_runtime`
- max_output_tokens: `512`
- timeout_seconds: `60`
- force_stop_between_prompts: `false`
- activity_restart_between_prompts: `false`
- process_policy: `maintain process between prompts`
- continuation_gate: `SUCCESS_CLEAN + next_prompt_allowed=true + reuse_allowed=true + runtime_reuse_allowed=true + hidden_per_run_isolated_required=false`

## Prompt 1

- prompt: `こんにちは`
- run_dir: `run_512_konnichiwa`
- lifecycle_classification: `SUCCESS_CLEAN`
- next_prompt_allowed: `true`
- reuse_allowed: `true`
- runtime_reuse_allowed: `true`
- runtime_reuse_policy: `reuse_allowed`
- hidden_per_run_isolated_required: `false`
- decision: `continue`

## Prompt 2

- prompt: `Pythonで簡単な電卓コードを書いて`
- run_dir: `run_512_python_calculator`
- lifecycle_classification: `TIMEOUT_SUSPECT`
- next_prompt_allowed: `false`
- reuse_allowed: `false`
- runtime_reuse_allowed: `false`
- runtime_reuse_policy: `per_run_isolated_required`
- hidden_per_run_isolated_required: `true`
- decision: `stop`
