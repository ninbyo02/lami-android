# Sanitizer Impact Review

Positive sanitizer effects:

- removed the leading template-like `>` artifact
- preserved the opening `python` code fence
- left no `<start_of_turn>` or `<end_of_turn>` markers in sanitized output
- no severe repetition was observed
- no multilingual drift was observed beyond normal Japanese explanation plus
  Python code

Negative sanitizer/display effects:

- stripped leading spaces from Python code lines
- produced a sanitized code block that is not executable Python as displayed
- did not repair the unclosed code fence caused by output truncation

Classification: `markdown_display_risk` and
`indentation_broken_by_sanitizer`.

The hidden route records `markdown=false` and does not route output through the
normal Markdown display path. A future UI-facing 512 gate needs a code-aware
display sanitizer or a derived preview step that preserves indentation inside
fenced code blocks and records whether code-fence repair was applied.
