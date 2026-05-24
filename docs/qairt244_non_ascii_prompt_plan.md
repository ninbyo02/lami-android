# QAIRT244 Non-ASCII Prompt Plan

This document covers Japanese/non-ASCII prompt input for the DEV-only qairt244 SM8750 NPU runner. It does not change the NPU route, token limit, native artifact, fallback behavior, or production Backend.NPU wiring.

## Current State

As of 2026-05-24:

- The qairt244 SM8750 DEV-only route is stable for the ASCII runner prompt set `Hello`, `test`, and `OK` at `max_output_tokens=16`.
- The runner intentionally accepts only ASCII alphanumeric plus `._-` for `--prompt`.
- Non-ASCII prompts stop before send with `unsupported_non_ascii_prompt`.
- Direct `adb shell input text こんにちは` is not safe: it produced an Android input command NPE before send.
- ASCII text can also be rewritten by the active IME, as seen when `test` became `てｓｔ`; the runner now mitigates that with IME selection, language-switch retry, and actual prompt verification before pressing Send.

The current runner should remain ASCII-only. Japanese prompt coverage should be a separate phase with a controlled input path.

## Options Compared

### Option 1: ADB Keyboard broadcast input

Use a dedicated ADB keyboard IME such as `com.android.adbkeyboard/.AdbIME` and send text through broadcast intents instead of `adb shell input text`.

Pros:

- Common Android automation pattern for Unicode text.
- Keeps the test as a black-box UI path through ChatScreen.
- Can still verify `actual_prompt` before pressing Send.

Cons:

- Requires installing/enabling a third-party or test-only IME on the device.
- Adds device setup state that must be saved/restored carefully.
- Broadcast protocol is not part of Android platform APIs and can vary by ADB keyboard implementation.
- Risky for the current guarded NPU runner because an IME setup mistake can leave the device in a modified input state.

Safety verdict: usable for a dedicated lab setup, but not the default runner path.

### Option 2: Clipboard paste

Push Japanese text to the Android clipboard, focus the ChatScreen input, then paste.

Pros:

- Avoids `adb shell input text` Unicode parsing.
- Stays close to real user UI behavior.
- Easy to verify with `actual_prompt` before send if paste succeeds.

Cons:

- `cmd clipboard` support is device/API dependent; on the current device it did not expose a useful shell command implementation.
- Clipboard privacy/permission behavior varies across Android versions and foreground states.
- Paste key events and context-menu paste are flaky in Compose/EditText automation.
- Could leave user clipboard modified unless restoration is implemented.

Safety verdict: not reliable enough as the primary plan for this device.

### Option 3: DEV-only internal prompt injection endpoint

Add a customBuildExperimentDebug-only endpoint that accepts a prompt string directly inside the app process and writes it into the same guarded DEV qairt244 route, bypassing Android IME text entry.

Pros:

- Avoids IME, clipboard, and `adb shell input text` Unicode limitations.
- Can pass exact UTF-8/Java `String` prompt to the route and record `requested_prompt`, `actual_prompt`, and normalization diagnostics.
- Can keep the normal UI route unchanged and still require explicit DEV gates.
- Best fit for native prompt validation, Japanese prompt coverage, and future 32/64-token bounded phases.

Cons:

- It is not an end-to-end UI typing test.
- Needs a small app-side DEV-only entrypoint or Activity/receiver and tests to prove it cannot run in release/standard builds.
- Must be careful not to bypass model guards, duplicate-run guards, cleanup, or no-fallback behavior.

Safety verdict: recommended for the next Japanese prompt phase.

### Option 4: Instrumentation/UIAutomator `setText`

Use an instrumentation test or UIAutomator API that sets the Compose/EditText text directly with a Unicode string.

Pros:

- More robust than shell `input text` for Unicode.
- Can remain UI-oriented and inspect screen state.
- Works well for automated test reports if test infra is already in place.

Cons:

- Requires instrumentation test packaging and runner setup.
- Still can be sensitive to Compose semantics, focus, and text-field discovery.
- Running NPU from instrumentation has additional timeout and device-state concerns.

Safety verdict: good secondary option for UI-specific coverage, separate from the shell runner.

### Option 5: Native/internal direct route without ChatScreen UI

Call the lower-level native editable-prompt path directly from a diagnostic Activity or host-side app command, without creating a ChatScreen message.

Pros:

- Smallest path for validating Japanese prompt handling in native/JNI.
- Avoids UI state, IME, clipboard, and ChatScreen cleanup concerns.

Cons:

- Does not validate ChatScreen route behavior.
- May miss DB/TTS/Markdown/streaming safety regressions in the UI branch.
- Could diverge from the actual DEV UI route if maintained separately.

Safety verdict: useful for native prompt validation only, not enough for route promotion.

## Recommendation

Keep `scripts/run_qairt244_chat_screen_real_npu_sm8750_model_run.sh` ASCII-only for now. Its job is to provide a stable, repeatable 16-token DEV NPU smoke over the visible ChatScreen path. Non-ASCII should remain `unsupported_non_ascii_prompt` and stop before send.

For Japanese prompt coverage, use a new customBuildExperimentDebug-only internal prompt entrypoint in a separate phase. The entrypoint should pass the exact prompt string to the existing qairt244 DEV-only route while preserving all current safety gates:

```text
BuildConfig.CUSTOM_BUILD_EXPERIMENT=true
explicit DEV enable flag=true
exact basename=gemma-4-E2B-it_qualcomm_sm8750.litertlm
required_sm8750_model_path=true
max_output_tokens=16
fallback_used=false
DB/TTS/Markdown/streaming disabled
selectedPath=npu not persisted
cleanup state recorded
```

The Japanese path should write a dedicated artifact namespace, for example:

```text
artifacts/qairt244_chat_screen_real_npu_sm8750_non_ascii_prompt/<timestamp>/
```

Required artifact fields:

```text
requested_prompt=<UTF-8 prompt>
actual_prompt=<string received by app route>
normalized_prompt=<native normalized prompt>
prompt_source=internal_intent
intent_dispatch_status=not_started|dispatched|accepted|rejected|entrypoint_missing|timeout|failure
prompt_input_status=not_applicable|ok|failure
prompt_input_failure_reason=<reason>
result=success|failure
max_output_tokens=16
native_max_output_tokens_limit=16
run_decode_reached=true|false
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
ui_cleanup_wait_status=success|failure
```

## Internal Intent Flow Contract

担当B側の runner/docs/artifact 契約は `--prompt-mode internal_intent` として準備する。担当A側の Kotlin entrypoint が未確定の間、runner は placeholder として artifact に契約情報と command template を記録するだけで、intent dispatch は行わない。ChatScreen、Activity、Receiver の app-side 実装は担当Aの範囲に残す。

Action name:

```text
io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT
```

Host runner mode:

```sh
scripts/run_qairt244_chat_screen_real_npu_sm8750_model_run.sh \
  --run \
  --prompt-mode internal_intent \
  --prompt 'こんにちは'
```

The `internal_intent` mode is the only planned mode for Japanese/non-ASCII prompts. The existing UI text mode remains ASCII-only and must continue to reject Japanese before send. The runner must not use `adb shell input text` for Japanese/non-ASCII prompts; that command path is reserved only for the stable ASCII UI smoke after prompt validation.

Planned Activity-style template, if 担当A implements the entrypoint as an internal Activity:

```sh
adb -s <device> shell am start -W \
  -a io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT \
  -n io.github.ninbyo02.lami.customnpu/<internal-entrypoint-component> \
  --es requested_prompt '<utf8-prompt>' \
  --ez dev_enable_qairt244_sm8750_npu_route true \
  --ei max_output_tokens 16
```

Planned broadcast-style template, if 担当A implements the entrypoint as an internal receiver:

```sh
adb -s <device> shell am broadcast \
  -a io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT \
  -p io.github.ninbyo02.lami.customnpu \
  --es requested_prompt '<utf8-prompt>' \
  --ez dev_enable_qairt244_sm8750_npu_route true \
  --ei max_output_tokens 16
```

The concrete component name, extra names beyond `requested_prompt`, and accepted/failed result file are intentionally left as a small contract surface for 担当A to finalize. Runner dispatch should be enabled only after that app-side contract exists.

Artifact additions for this flow:

```text
requested_prompt=<UTF-8 prompt from runner>
actual_prompt=<string accepted by app entrypoint>
normalized_prompt=<native normalized prompt>
prompt_source=internal_intent
intent_dispatch_status=not_started|dispatched|accepted|rejected|entrypoint_missing|timeout|failure
internal_intent_action=io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT
adb_shell_input_text_unicode=false
ui_text_ascii_only=true
```

## Minimal Next Implementation

1. Add a `customBuildExperimentDebug`-only internal Activity or receiver for non-ASCII prompt validation. Do not expose it in release or normal debug.
2. Require the explicit action name `io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT` plus `dev_enable_qairt244_sm8750_npu_route=true`.
3. Accept the prompt through an Intent extra, not through `adb shell input text`.
4. Reuse the existing resolver, exact SM8750 basename guard, duplicate-run guard, max token cap, no-fallback behavior, and cleanup path.
5. Write `requested_prompt` and `actual_prompt` before native execution; write native `normalized_prompt` from result after execution.
6. Add a separate runner script for this phase rather than overloading the current ASCII UI runner.
7. Start with one prompt, `こんにちは`, and one run only. Keep `max_output_tokens=16`.
8. Validate no standard route, DB, TTS, Markdown, streaming, selectedPath persistence, fallback, timeout, or fresh crash.

## Why Not Change The Current Runner

The current runner is now stable because it avoids the Android Unicode shell-input path and verifies the visible text field before send. Expanding it to support Japanese through IME or clipboard would make the stable ASCII smoke dependent on device input-method state again. Keeping it ASCII-only gives a reliable baseline while Japanese coverage gets its own stricter artifact and gate.

## Acceptance Criteria For The Non-ASCII Phase

- Non-ASCII prompt is delivered to the app as the exact requested Java/Kotlin string.
- Native result records `actual_prompt` and `normalized_prompt` matching the intended Japanese prompt semantics.
- `max_output_tokens=16` remains unchanged.
- RunDecode reaches NPU with `QNN_HTP_V79_FastRPC_native_diag`.
- `fallback_used=false`, `timeout=false`, `fresh_crash=false`.
- UI cleanup succeeds if the path inserts a ChatScreen message.
- Existing ASCII runner still passes `Hello`, `test`, and `OK` without changes.
- Release, standard debug, normal local inference, and Backend.NPU production paths remain unchanged.
