# Code Quality Classification

Primary classifications:

- `indentation_broken_by_sanitizer`
- `code_fence_unclosed_due_to_truncation`
- `output_truncated_by_token_limit`
- `markdown_display_risk`

Not selected:

- `code_quality_ok`: not selected because sanitized display text is not valid
  Python due to stripped indentation and an unclosed code fence.
- `indentation_broken_in_raw`: not selected because raw output preserves nested
  Python indentation.
- `code_fence_missing`: not selected because both raw and sanitized outputs
  retain the opening `python` code fence.
- `unknown`: not selected; native completion, cleanup, output lengths, and raw
  versus sanitized comparison are sufficient to classify the issue.

Safety quality remains acceptable for the bounded retry record:
`useful_code`, no timeout, no fresh crash, no fallback, QNN evidence present,
cleanup present, and no side-effect ingress. Display quality does not pass the
baseline gate.
