# Fence Completion Review

Condition:

- opening fence present
- code block detected
- closing fence absent at end of sanitized stream

Action:

- append a derived closing fence to sanitized output
- set `code_fence_completed=true`
- keep raw output unchanged

This is a display sanitizer repair for token-limit truncation. It does not
claim the model completed the code semantically, and it does not change native
output evidence.
