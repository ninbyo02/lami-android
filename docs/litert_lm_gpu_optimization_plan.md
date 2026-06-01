# LiteRT-LM GPU Optimization Plan

Date: 2026-06-02

Scope: `standard` flavor, existing local inference GPU fallback / GPU route.
This is an investigation plan plus a non-invasive probe. It does not change
`Backend.NPU`, QAIRT/QNN configuration, fallback policy, model files, S1-S5
DB/Markdown/TTS/pseudo-streaming integration, or production ChatScreen
behavior.

## Current Route Reading

The practical GPU route is the local LiteRT-LM route in `ChatScreen` and
`LocalStreamingRunner`.

Observed from source:

- `standardDebug` uses LiteRT-LM Android `0.11.0`.
- `standard` has `QUALCOMM_DISPATCH_EXPERIMENT=false` and
  `NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED=false`.
- `PreferredBackendDryRunSetting.fromStorage()` maps stored `NPU` and
  `QUALCOMM_QNN_NPU` back to `GPU`.
- `buildLiteRtEngineConfig()` applies:
  - requested `GPU`: `Backend.GPU()` for text backend;
  - requested `DEFAULT`: applied label `DEFAULT`, but text backend is still
    `Backend.GPU()`;
  - vision backend: `Backend.GPU()`;
  - audio backend: `Backend.CPU()`;
  - requested NPU: disabled fallback to `Backend.GPU()`.
- The direct official route logs
  `backend=text=GPU vision=GPU audio=CPU`.
- The older reflection fallback currently applies optional MediaPipe builder
  values such as `setMaxOutputTokens`, `setTopK`, `setTemperature`, and
  `setRandomSeed` when those methods exist. That path is fallback-oriented and
  should not be treated as the primary production GPU path until the trace
  proves it was selected.

Working conclusion: the current practical route is GPU for text/vision and CPU
for audio, with NPU explicitly kept out of the standard production path.

## Probe Added

Added:

```bash
scripts/run_litert_lm_gpu_route_probe.sh
```

Default run:

```bash
scripts/run_litert_lm_gpu_route_probe.sh --seconds 60
```

The probe launches the standard app, waits while an operator sends a normal
local GPU prompt, then collects:

- `source_inventory.md`
- `probe_matrix.tsv`
- `package_dump.txt`
- `meminfo_before.txt`
- `meminfo_after.txt`
- `proc_meminfo_before.txt`
- `proc_meminfo_after.txt`
- `logcat_tail.txt`
- `local_reflection_trace.log` when readable via `run-as`
- `summary.md`

Safety boundaries:

- no hidden NPU receiver broadcast;
- no `RunDecode`;
- no preference writes;
- no backend change;
- no QAIRT/QNN library staging;
- no fallback policy change.

## Metrics To Capture

### 1. Actual Backend Configuration

Use source evidence plus runtime trace:

- `UPSTREAM official-direct backend=text=GPU vision=GPU audio=CPU`
- `UPSTREAM preferred-backend hook-reached=true ... applied=GPU`
- `EngineConfig` construction evidence from `source_inventory.md`
- package dump native library visibility, especially absence/presence of QNN
  dispatch libs in standard app runtime

Acceptance: classify the run as `gpu-official`, `gpu-reflection-fallback`, or
`unknown`.

### 2. Current tokens/sec

Primary source:

- `LocalInferenceMeasuredTokenSnapshot.tokensPerSecond`
- tokenizer recount path when `tokenCountMode=mediapipe_tokenizer_recount` or
  `tokenizer_recount`

Fallback source:

- output tokens divided by generation time;
- chars/sec only as secondary evidence when token count is unavailable.

### 3. First Response Time

Use:

- `localTraceFirstResponseElapsedRealtimeMs - localTraceStartElapsedRealtimeMs`
- `timeToFirstTokenMs` from backend/token snapshot when present

For blocking fallback, classify as "first complete response" rather than true
TTFT.

### 4. Model Load Time

Use:

- `wallClockLoadDurationNs`
- `modelLoadDurationNs`
- trace markers from `engine-create`, `engine-initialize-start`, and
  `engine-initialize-success`

Separate cold start from held-engine reuse. Cold-start numbers must include
whether the app process was force-stopped before measurement.

### 5. max_output_tokens 32/64/128/256

Current state: no safe standard GPU matrix runner is wired yet.

Required next hook:

- add a debug-only, standard-flavor GPU matrix entry point that accepts
  `max_output_tokens` without changing normal ChatScreen defaults;
- run one prompt per value: `32`, `64`, `128`, `256`;
- record success, timeout, first response time, total time, output tokens,
  tokens/sec, memory delta, and selected route.

Do not reuse the hidden NPU receiver for this measurement because it proves a
different runtime path.

### 6. Prompt Template Difference

Compare at least:

- current ChatScreen prompt composition;
- raw user prompt;
- Gemma-it-like short template;
- code-aware template for one code prompt.

Record output quality separately from speed. Template quality regressions must
not be hidden by faster decode.

### 7. temperature / top_k / top_p Effectiveness

Current source reading:

- reflection fallback tries `temperature=0.0`, `topK=1`, and seed `1`;
- official held-engine route creates `SamplerConfig` with constructor values
  `topK=10`, `topP=0.95`, `temperature=0.8` when that constructor is
  available for tokenizer/session probing;
- not yet proven that these sampler values control the actual production
  generation path.

Required test:

- deterministic pair: `temperature=0.0`, `top_k=1`;
- default pair: `temperature=0.8`, `top_k=10`, `top_p=0.95`;
- repeat each prompt twice and diff output. If outputs do not change and trace
  does not show sampler application, classify sampling control as unproven.

### 8. Memory Usage

Use probe artifacts:

- `dumpsys meminfo` before and after;
- app total PSS and native heap delta;
- system low-memory state from `/proc/meminfo` and Android logcat.

Track cold-start and warm held-engine separately.

### 9. Android OS Kill Mitigation

Investigate without changing policy first:

- app process killed by `lowmemorykiller`, `ActivityManager`, or tombstone;
- held engine lifecycle events;
- memory after TTS and after local generation;
- whether model load pushes native PSS above stable device margin.

Potential mitigations after measurement:

- release held engine when app goes background and native PSS crosses a
  threshold;
- keep TTS release policy separate from GPU engine release policy;
- add a user-visible retry only after a process restart, not after a normal
  decode error;
- cap GPU max output by device memory tier.

### 10. NPU Diagnostic Route Separation

Keep these as separate evidence classes:

- GPU route: `Backend.GPU`, `official-direct`, `held-official-flow`,
  `local_reflection_trace`, no `RunDecode`.
- NPU diagnostic route: `QNN_HTP_V79_FastRPC_native_diag`, hidden receiver,
  `RunDecode`, standard S1-S5 diagnostic artifacts.

Do not mix the S1-S5 NPU diagnostic success rate into GPU tokens/sec or load
time baselines.

## Proposed Investigation Sequence

1. Run the new observation probe on a normal standard GPU prompt.
2. Classify route selection from trace: held official flow, official blocking,
   reflection fallback, or failure.
3. Extract baseline tokens/sec, first response time, model load time, and memory
   delta from the artifacts.
4. Add a debug-only standard GPU matrix hook for max output and sampler
   parameters.
5. Run the 32/64/128/256 matrix.
6. Run template and sampler A/B tests.
7. Decide production tuning only after stability and memory results are known.

## Optimization Candidates After Baseline

- Prefer held-engine reuse when foreground and memory is stable.
- Avoid repeated `Engine.initialize()` on consecutive prompts.
- Keep `Backend.GPU()` explicit for standard GPU measurements.
- Make max output adaptive only if 128/256 materially degrade TTFT, memory, or
  OS kill risk.
- Avoid changing prompt template until quality and latency are both measured.
- Keep NPU diagnostic route as a separate research path.

## Verification

Required after this docs/probe step:

```bash
./gradlew testStandardDebugUnitTest
git diff --check
```
