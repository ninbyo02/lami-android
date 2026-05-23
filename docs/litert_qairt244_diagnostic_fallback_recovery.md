# QAIRT 2.44 NPU Diagnostic Fallback Recovery

Date: 2026-05-23

Scope: Diagnostic Chat only, `customBuildExperimentDebug`.

Normal `ChatScreen`, normal `selectedPath=npu`, high-level
`generateResponse`, streaming generation, and release changes remain out of
scope.

## Artifact

```text
artifacts/qairt244_npu_diagnostic_fallback_recovery/20260523_193405/
```

## Cases

### Case 1: Invalid Prompt

Prompt:

```text
Hello/Lami
```

Result:

- validator: `isValid=false`
- reason: `contains_disallowed_char`
- `prompt_execution_connected=false`
- `run_button_connected=false`
- Run button: disabled
- `engine_initialize=false`
- `run_decode=false`
- normal ChatScreen route: disconnected
- normal `selectedPath=npu` route: disabled

### Case 2: Native Unsupported Preflight

The unsupported case is represented as a missing marker or missing artifact
preflight block.

Result:

- `native_editable_prompt_supported=false`
- `preflight_result=blocked_marker_missing_or_artifact_missing`
- `engine_initialize=false`
- `run_decode=false`
- `npu_generation=false`

### Case 3: Timeout Simulation

Timeout is simulated through a DEV-only Activity extra:

```text
simulateEditablePromptTimeout=true
diagnosticTimeoutMs=1000
```

This path intentionally does not call native Engine or RunDecode.

Observed result:

- `state=started`
- `state=timeout_simulation_native_not_called`
- `state=timeout timeout_ms=1000`
- `engine_initialize=false`
- `run_decode=false`
- Run button after timeout: disabled
- DEV checkbox after timeout: off

### Case 4: Recovery After Failure

After timeout simulation, the Activity was refreshed.

Result:

- Refresh completed
- Run button remained disabled
- DEV checkbox remained off
- normal ChatScreen route remained disconnected
- normal `selectedPath=npu` route remained disabled

## Classification

Fallback/recovery checks passed for Diagnostic Chat-only scope. Invalid prompt
and native unsupported preflight do not start NPU work. Timeout simulation
returns the UI to a non-running state without calling Engine.initialize or
RunDecode.

## Next Boundary

The next step is to design normal ChatScreen integration constraints. Do not
connect normal UI NPU execution until a separate design review fixes backend
selection, fallback behavior, message persistence, and user-visible recovery.
