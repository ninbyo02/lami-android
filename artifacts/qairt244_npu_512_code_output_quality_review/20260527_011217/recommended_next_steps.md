# Recommended Next Steps

Do not promote 512 to hidden baseline, H1, or normal ChatScreen from this
artifact.

Before another 512 baseline decision:

1. Design a code-output display quality gate that preserves indentation inside
   fenced code blocks.
2. Add a bounded code-fence repair policy for truncated code output. Raw output
   must remain unchanged; repaired output should be marked as derived display
   text.
3. Require the gate to report: opening fence present, closing fence present or
   repaired, indentation preserved, truncation detected, and code display status.
4. Run a separately approved bounded 512 three-prompt comparison only after the
   display-quality gate exists.
5. Keep 256 as the hidden experimental baseline candidate and keep 512 as
   extended experimental until the full gate passes.

1024, 2048, and 4096 remain blocked.
