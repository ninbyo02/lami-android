# GPU Next Probe Plan

## Goal

Add the smallest debug-only benchmark variants needed to split:

1. failure before entering `nativeSendMessage`,
2. failure inside `nativeSendMessage` / compiled model invoke,
3. failure during `Conversation.close()` or `Engine.close()`.

No device execution is part of this document.

## Recommended Variant Order

| order | variant | purpose |
| ---: | --- | --- |
| 1 | `send-callback-gallery` | Gallery chat parity: callback send API, `Contents.of(Content.Text(prompt))`, sampler config, null modalities, maxNumTokens 4000, cacheDir null. |
| 2 | `send-callback-no-sampler` | Same as above but no sampler; isolates `ConversationConfig.samplerConfig`. |
| 3 | `send-flow-gallery-config` | Gallery Engine/Conversation config but existing Flow send; isolates callback vs Flow wrapper. |
| 4 | `send-blocking-gallery-config` | Gallery Engine/Conversation config but blocking `sendMessage(Contents, emptyMap())`; isolates async callback implementation. |
| 5 | `close-after-conversation-only` | No send; close Conversation and Engine after successful create with fresh tombstone filtering. |
| 6 | `close-after-send-failure-skip-matrix` | Compare `normal`, `skip-conversation`, and `skip-all` after the same send failure. |

## Minimal Debug Code Shape

Only `app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt` should change if implemented.

Suggested additions:

- `BenchmarkSendVariant` extra:
  - `flow-text`
  - `callback-contents`
  - `blocking-contents`
- `BenchmarkConfigVariant` extra or backend variant:
  - `gallery-chat-parity`
- markers:
  - `native_send_prepare_started`
  - `native_send_prepare_finished`
  - `native_send_call_started`
  - `native_send_call_returned`
  - `native_send_callback_first_message`
  - `native_send_callback_done`
  - `native_send_callback_error`
  - `native_send_exception`
  - `close_started`
  - `close_finished`
  - `close_exception`

The key discriminator is whether `native_send_call_returned` appears. If it appears and the error arrives through the callback, the native call was accepted and failed during compiled-model execution. If it does not appear, the failure is synchronous inside `nativeSendMessageAsync`.

## Script Changes

Only `scripts/run_litert_lm_gpu_benchmark.sh` should change if implemented.

Suggested flags:

```bash
--send-variant flow-text|callback-contents|blocking-contents
--gallery-chat-parity
--single-case
```

`--gallery-chat-parity` should force:

```text
backend_variant=gpu-null-modalities
max_output_tokens_list=4000
cache_dir_mode=null
conversation_config=sampler_64_0.95_1.0
send_variant=callback-contents
close_policy=skip-all
```

The first run should use `--single-case` and one short prompt to avoid repeated Engine creation hiding the first failure.

## Fresh Crash Filtering

The current crash collector can pick up stale tombstones. Add freshness checks before trusting native crash fields:

- tombstone timestamp must be after benchmark broadcast timestamp,
- `Cmdline:` must match `io.github.ninbyo02.lami`,
- pid should match a logcat `DEBUGGERD` or `crash_dump64` entry during the run,
- marker history must end near the crash boundary,
- otherwise mark `native_crash_fresh=false`.

Until this is implemented, prefer marker/state data over `crash_summary.md` for successful runs.

## Expected Decision Rules

| observation | conclusion |
| --- | --- |
| Gallery-parity callback send succeeds | Current blocker is receiver-specific config or overload shape, not the model/runtime itself. |
| Gallery-parity callback send fails with same line 735 error | The blocker is likely model/runtime/GPU compiled-model invocation, not prompt wrapper or sampler config. |
| Flow fails but callback succeeds | Avoid Flow wrapper for benchmark diagnostics; it changes lifecycle/error timing. |
| Blocking fails but callback succeeds | Synchronous send path has a separate native issue; keep callback as Gallery parity. |
| `skip-all` avoids SIGSEGV but normal close crashes | Treat cleanup reset as a separate native cleanup bug after send failure. |
| `conversation-only` normal close crashes with fresh tombstone | The cleanup crash is independent of send and should be reported separately. |

## Verification Commands

For docs-only work:

```bash
git diff --check
```

If debug code is implemented later:

```bash
bash -n scripts/run_litert_lm_gpu_benchmark.sh
./gradlew testStandardDebugUnitTest
git diff --check
```

Do not run device probes as part of this task.
