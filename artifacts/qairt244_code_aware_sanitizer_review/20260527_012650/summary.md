# QAIRT244 Code-Aware Sanitizer Review

Date: 2026-05-27

Scope: code-aware sanitizer design and minimal implementation only. No
additional NPU execution, 512 retry, 1024+ expansion, native guard change,
QAIRT rebuild, ChatScreen promotion, assistant message insertion, DB, TTS,
Markdown renderer connection, streaming, selected-path NPU persistence, release
or standard behavior change, or `app/src/main/jniLibs` change was performed.

Implementation:

- `Qairt244NpuOutputSanitizer` now detects fenced code blocks.
- Code block lines preserve leading spaces, tabs, and blank lines.
- Non-code lines keep the existing template-token, prompt-echo, drift, quote,
  and repeated-line sanitizer behavior.
- If a fenced code block is opened and native output truncates before a closing
  fence, the sanitizer appends a closing fence in sanitized display text.
- Raw output remains recorded as diagnostic evidence only; adapter output still
  uses sanitized output.
- Route diagnostics now include `code_block_detected` and
  `code_fence_completed`.

Result: the 512 Python code prompt issue is classified as display sanitizer
quality, not NPU decode failure. 512 remains extended experimental and is not a
baseline candidate yet. 256 remains the hidden experimental baseline candidate.
The next runtime candidate is a separately approved bounded 512 three-prompt
comparison after this sanitizer change.
