# Sanitizer Before/After

Before:

- Each line used `trim()` before being kept.
- This correctly removed turn/template residue in prose.
- It also stripped leading spaces inside Python code blocks.
- A truncated opening code fence was left unclosed.

After:

- The sanitizer switches into code-block mode when a line matches a Markdown
  fence such as `python`, `kotlin`, `java`, `js`, `cpp`, or a bare fence.
- In code-block mode, leading spaces, tabs, and blank lines are preserved.
- `>` prefix removal and duplicate-line suppression are not applied inside code
  blocks.
- Non-code blocks keep the prior sanitizer behavior.
- An unclosed fence is completed with a derived closing fence in sanitized
  output, with `code_fence_completed=true`.

The raw output is unchanged and remains unsuitable for UI/state/renderer
display.
