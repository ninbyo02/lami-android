# Indentation Review

Raw output status: indentation preserved.

The raw output contains normal Python indentation under function bodies,
conditional branches, loops, and `try` blocks. Examples include four-space
function bodies and deeper nested blocks under `while True`, `if`, and `try`.

Sanitized output status: indentation stripped.

The sanitized output keeps line breaks and the opening code fence, but code
lines are left-aligned:

- `"""2つの数を加算する"""` moves to column 1 after `def add(x, y):`
- `return x + y` moves to column 1
- nested bodies under `if y == 0:`, `while True:`, `try:`, and `if choice ==`
  also move to column 1

Classification: `indentation_broken_by_sanitizer`.

Likely cause: the hidden-route sanitizer/result parsing path normalizes the
output after native completion and loses leading code-block whitespace. The
native model output itself is not the indentation source of failure.
