# QAIRT244 NPU max_output_tokens=512 code prompt timeout review

Source artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_compare/20260527_003429/`

Scope: docs/artifact/log/runner review only. No additional NPU generation,
512 retry, 1024+ expansion, native guard change, QAIRT rebuild, ChatScreen
promotion, DB, TTS, Markdown, streaming, selectedPath save, release/standard
change, or `app/src/main/jniLibs` change was performed.

## Finding

Classification: `C. native_hang_or_no_callback`, with `D. cleanup_unknown`.

The Python calculator prompt entered the hidden receiver and native
editable-prompt path. Native diagnostics reached:

```text
before RunPrefill
before RunDecode SetMaxOutputTokens(512) native_max_output_tokens_limit=512
```

No native `success`, `cleanup_elapsed_ms`, or `Engine.close` line was captured
for that prompt. The runner waited for the app-side state file for the bounded
30 second timeout, then force-stopped the app. No completed receiver result,
raw output, sanitized output, backend completion evidence, or cleanup evidence
was available for the Python prompt.

## Decision

512 is not a hidden baseline candidate. Keep 256 as the hidden experimental
candidate and keep H1 pinned to the 128 display baseline. Do not proceed to
1024 until a separately approved 512 code-prompt retry or full 512 three-prompt
comparison passes all gates.
