# LiteRT-LM GPU Receiver vs Chat Route Delta

## Scope

- Target files:
  - `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt`
  - `app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt`
- Related call-site context:
  - `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt`
  - `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceEngineHolder.kt`
- Constraint: investigation only. Production ChatScreen, S1-S5, and Backend.NPU paths were not changed.

## Current Crash Boundary

Observed receiver markers stop after:

1. `receiver_started`
2. `model_resolved`
3. `backend_selected`
4. `engine_create_started`

The app process then dies before `engine_create_finished`, so the failure boundary is inside one of:

```kotlin
engine = Engine(config)
engine.initialize()
```

in `LiteRtLmGpuBenchmarkReceiver.runCase`.

CPU backend reaches `report_written` and surfaces a `LiteRtLmJniException`, which means the receiver can run, catch JVM exceptions, and write reports. The GPU path is different because it terminates the process natively.

## Delta Table

| Area | Chat / LocalStreamingRunner route | GPU benchmark receiver route | Crash relevance |
| --- | --- | --- | --- |
| EngineConfig construction site | `buildLiteRtEngineConfig(...)` is the central helper. Held route calls it through `createOfficialLiteRtLmEngineInstance(...)`. Direct fallback routes call the same helper. | Constructs `EngineConfig(...)` directly inside `runCase`. | Medium. Values mostly match GPU/default, but helper side effects/tracing and future default behavior are bypassed. |
| `backend` | `PreferredBackendDryRunSetting.DEFAULT` maps to `Backend.GPU()` with result `skipped-default-engine-config`; GPU maps to `Backend.GPU()`. | `gpu` maps to `Backend.GPU()`. `default` also maps to `Backend.GPU()`. | Low for `gpu` vs Chat DEFAULT/GPU. The backend object type is effectively the same. |
| `visionBackend` | Always `Backend.GPU()` in `buildLiteRtEngineConfig`. | `gpu` and `default`: `Backend.GPU()`. `cpu`: `Backend.CPU()`. | Low for current GPU crash, because GPU route matches Chat. Still worth varying because native delegate setup may initialize multimodal backends. |
| `audioBackend` | Always `Backend.CPU()`. | Always `Backend.CPU()`. | Low. |
| `cacheDir` | `cacheDirPath` from model resolution / `buildLiteRtCacheDirPath(context)`, effectively `context.cacheDir.absolutePath`. | `appContext.cacheDir.absolutePath`. | Low. Same path shape expected. Confirm absolute string equality in artifacts if needed. |
| `maxNumTokens` | Always `null` in `buildLiteRtEngineConfig`. | `gpu`/`cpu`: current case value `32/64/128/256`. `default`: `null`. | High. Explicit small max values are the clearest EngineConfig value delta for `gpu`. |
| `maxNumImages` | Not passed by current named constructor call. The expected constructor inventory elsewhere references an optional `Integer maxNumImages`, but this code path leaves it defaulted. | Not passed. | Low. |
| Sampler config | No EngineConfig sampler field is set. Generation uses `sendMessageAsync(prompt, emptyMap())` or blocking equivalent with `emptyMap()`. | No EngineConfig sampler field is set. Generation uses `sendMessageAsync(prompt)` first, then `sendMessage(prompt)` fallback. | Low for engine-create crash. Sampler is after engine creation. |
| Model path source | Chat resolves via `resolveLocalModelResolutionOrNull(...)` or supplied `resolvedModelPath`; the held key stores `modelPath`, `backendKey`, `cacheDirPath`. | Receiver resolves explicit base64 `model_path`, legacy `model_path`, settings `getValidLocalBaseModelPathOrNull()`, then first `.litertlm` under `files/local_models`. | Medium. Same path may be used, but receiver can choose a different local model if no argument is passed. |
| Canonical path | No canonicalization in `buildLiteRtEngineConfig`; it receives the resolved path string. Chat diagnostics contain canonical basename elsewhere, but this route passes raw resolved path. | No canonicalization; passes raw resolved path. | Low. Canonicalization is not a known route delta here. |
| Engine lifetime | Primary Chat route uses `LocalInferenceEngineHolder`: Engine creation can be reused across sends. Conversation creation is deferred after held Engine storage. | Each benchmark case creates a fresh Engine, initializes it, creates a Conversation, sends, closes. First prompt/token count starts with a cold Engine. | High. Crash is at cold GPU Engine creation in a BroadcastReceiver-triggered worker, not necessarily equivalent to an already-held/reused Chat Engine. |
| Thread / component context | Chat route runs from UI coroutine flow, mostly `withContext(Dispatchers.Default)` for local execution, with holder mutex/lifecycle state. | `BroadcastReceiver.goAsync()` then single `receiverDispatcher`; each case uses another single-thread executor and `Future.get(timeout)`. | High. Native GPU initialization can be sensitive to process/component lifecycle, thread interruption, or receiver lifetime. |
| Timeout handling | Chat has GPU experimental timeout wrappers around held acquire/run in diagnostic mode and clears held Engine on timeout. | Host script times out externally; receiver case also uses `Future.get(timeout)` and `future.cancel(true)` for Java-level timeout. | Medium. Current native crash happens before timeout, but receiver's cancellation model differs. |
| Conversation create path | Held route uses reflection helper `createOfficialLiteRtLmConversation(...)`, which constructs `ConversationConfig` and invokes `createConversation(ConversationConfig)`. Direct route can call `engine.createConversation()`. | Receiver calls `engine.createConversation()` directly. | Low for current boundary because crash occurs before conversation marker. Important for later parity once engine creation passes. |
| Send path | Held route locates `sendMessageAsync(String, Map)` or `sendMessage(String, Map)` by reflection and passes `emptyMap()`. Direct route uses typed API and `emptyMap()` for direct paths. | Receiver typed streaming calls `conversation.sendMessageAsync(prompt)` with no map; fallback uses `sendMessage(prompt)`. | Low for current boundary. Important for prompt/template and sampler comparisons later. |
| Reflection path | Chat uses reflection for method discovery, fallback namespace support, tokenizer probes, close outcomes, and detailed diagnostics. | Receiver uses typed LiteRT-LM API for Engine/Conversation, plus limited reflection for finish/stop reason. | Medium. Reflection itself is not crash cause, but Chat has fallback and diagnostics around API shape changes. |
| Initialization order | Held route: create config -> `Engine(config)` -> `initialize()` -> store held engine -> conversation later. Direct Chat route: create config -> `Engine(config)` -> `initialize()` -> conversation -> send. | Receiver: resolve model -> build config -> marker -> `Engine(config)` -> `initialize()` -> conversation -> send -> close. | Medium-high. The receiver closest to Chat direct route still differs by component/thread and by `maxNumTokens`. |
| Error capture | Chat wraps helper calls in `runCatching`, writes local diagnostics on JVM exceptions, and may fallback when the preferred backend request was NPU. | Receiver catches JVM `Throwable` around `runCase`, but native crash bypasses this and kills process. | Low as cause; high as diagnostic limitation. |

## Most Suspicious Deltas Top 10

1. **Explicit `maxNumTokens` in receiver GPU variant**
   - Chat's `buildLiteRtEngineConfig` always passes `maxNumTokens = null`.
   - Receiver `gpu` passes `32/64/128/256` into `maxNumTokens`.
   - Because the crash is before `engine_create_finished`, `gpu --backend default` with `maxNumTokens=null` is the first parity check.

2. **Fresh Engine per benchmark case**
   - Chat normally uses `LocalInferenceEngineHolder` and can reuse an existing initialized Engine.
   - Receiver repeatedly cold-creates GPU Engines from a BroadcastReceiver-triggered route.
   - If Chat success used a previously created Engine, receiver is testing a colder and harsher path.

3. **BroadcastReceiver / goAsync execution context**
   - Receiver is not an Activity or foreground UI route. It enters through `onReceive`, then runs work on a static single-thread executor.
   - GPU delegate initialization may depend on process/component state or foreground scheduling differently from the Chat route.

4. **Nested executor and cancellation model**
   - Receiver dispatches `handle` on `receiverDispatcher`, then each case uses another `Executors.newSingleThreadExecutor` and `Future.get(timeout)`.
   - This differs from coroutine execution in Chat and may interact poorly with native GPU init if a previous timeout or interruption occurred.

5. **Model path source mismatch risk**
   - Chat uses the selected/resolved local model.
   - Receiver may use the settings model or first `.litertlm` under `files/local_models` if no explicit path is passed.
   - If multiple models exist, the receiver may be testing a different model than the successful Chat route.

6. **Direct EngineConfig construction bypasses helper diagnostics**
   - Receiver directly constructs `EngineConfig`, while Chat routes use `buildLiteRtEngineConfig`.
   - The values are mostly equal for GPU/default, but helper-side trace and preferred backend metadata are absent, making parity harder to prove.

7. **`default` receiver variant is not true backend-omitted default**
   - Current receiver `default` still sets `backend = Backend.GPU()`.
   - Chat DEFAULT also resolves to `Backend.GPU()`, so this is similar to current production behavior, but not a library-native omitted-backend test.

8. **Conversation creation API difference after engine creation**
   - Held Chat route uses `ConversationConfig` via reflection.
   - Receiver uses no-arg `createConversation()`.
   - Not the current crash boundary, but it will matter after engine creation succeeds.

9. **Send API difference after conversation creation**
   - Chat LiteRT-LM direct/held routes use `sendMessageAsync(prompt, emptyMap())` or `sendMessage(prompt, emptyMap())`.
   - Receiver uses one-argument typed calls.
   - Also not current boundary, but affects later benchmark parity.

10. **No holder lifecycle state in receiver**
    - Chat tracks foreground/background, idle release, model-change clearing, and diagnostics.
    - Receiver bypasses these safeguards. This is unlikely to directly crash `Engine(config)`, but it means receiver is not a faithful reproduction of the production lifecycle.

## Reproducibility Assessment

| Test | Expected result | Interpretation |
| --- | --- | --- |
| `--backend gpu --max-output-tokens 32` | Current native crash after `engine_create_started` | Confirms crash on explicit GPU and explicit max token limit. |
| `--backend default --timeout 180` | If it succeeds or reaches Java exception, `maxNumTokens` is likely involved. If it still native-crashes, the cause is broader than max token limit. | Highest priority parity check with Chat's `maxNumTokens=null`. |
| `--backend cpu --timeout 180` | Report is written with caught `LiteRtLmJniException` | Confirms receiver/report plumbing is alive and native process death is GPU-specific. |
| Explicit `--model-path` equal to Chat selected path | Should remove model selection ambiguity | If crash disappears, the receiver was selecting a different model. |
| Repeated GPU receiver run after force-stop | If first run crashes consistently, cold GPU Engine create is reproducible. If only later runs crash, repeated create/close or stale native state is suspect. | Separates cold-start crash from repeated lifecycle crash. |
| Chat route immediately after receiver crash | If Chat still works, receiver context/config is likely the trigger. If Chat also fails until force-stop/reinstall, GPU native runtime process state is contaminated. | Separates route-local failure from process-global GPU runtime instability. |

Current evidence supports: **GPU-specific native failure during cold Engine creation/initialize in receiver**, with `maxNumTokens` and receiver execution context as the strongest deltas.

## Fix Candidate Priority

1. **Add a receiver variant that exactly delegates Engine creation to `buildLiteRtEngineConfig`**
   - Use the same helper rather than duplicating `EngineConfig(...)`.
   - Keep it debug-only and receiver-only.
   - This proves whether helper parity changes native behavior.

2. **Run GPU benchmark with `maxNumTokens=null` first**
   - Promote the existing `default` variant as the first diagnostic, or add an explicit `gpu-null-max` variant.
   - If it passes, compare 32/64/128/256 only after successful null-max baseline.

3. **Require or log exact model path parity**
   - The script should prefer explicit `--model-path` for crash investigations.
   - Receiver markers should include basename, length, and ideally canonical path read-only diagnostics.

4. **Add a holder-parity debug receiver variant**
   - Receiver acquires `LocalInferenceEngineHolder` with `HeldEngineKey(modelPath, LOCAL_LITERT_BACKEND_KEY, cacheDirPath)` and `PreferredBackendDryRunSetting.DEFAULT/GPU`.
   - It should stop after acquire first, before conversation/generation.
   - This isolates "raw receiver Engine create" from "same holder path as Chat".

5. **Avoid per-case Engine recreation until one Engine can initialize**
   - First benchmark stage should create one Engine and run all max-token cases only if Engine supports configurable generation limits outside EngineConfig.
   - If max token limit is only EngineConfig-level, run variants in separate process launches to avoid repeated native GPU init in one receiver.

6. **Move GPU benchmark execution into a debug Activity or foreground service if receiver context remains crashy**
   - This tests whether BroadcastReceiver process/component state is part of the crash.
   - Keep ChatScreen untouched.

7. **Add native crash collection before further route changes**
   - Tombstone/dropbox signal, abort message, and backtrace are needed to distinguish bad config from vendor GPU delegate crash.

8. **After engine creation is stable, align conversation/send APIs**
   - Use `createConversation(ConversationConfig)` and `sendMessageAsync(prompt, emptyMap())` parity variants.
   - This is second-order because current crash occurs before conversation creation.

## Working Hypothesis

The best current hypothesis is not "Backend.GPU alone is broken", because Chat can generate with the GPU route. The receiver is likely exposing one of these narrower cases:

1. `EngineConfig.maxNumTokens` with explicit small values triggers a GPU native crash for this model/runtime.
2. Cold GPU Engine creation from a BroadcastReceiver worker thread crashes, while Chat success may involve holder-managed lifecycle or a different timing/context.
3. Receiver is resolving a different `.litertlm` than the Chat-selected model.

The next lowest-risk diagnostic is to run:

```bash
scripts/run_litert_lm_gpu_benchmark.sh --backend default --timeout 180
```

with an explicit `--model-path` matching the Chat route. If that still dies after `engine_create_started`, prioritize native crash collection and a holder-parity receiver variant.
