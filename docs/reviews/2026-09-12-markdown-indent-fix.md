# Reimplementation of #2446: preserve Python indentation

The prior PR only adjusted fixed offsets. Investigation found an earlier comment-normalization pass trimming every code line, so correct nested Python was already damaged before indentation repair.

This change preserves leading whitespace through comment post-processing and the code-after-comment path. Existing event-loop and QUIT-suite indentation remains unchanged, including tabs and intentional dedents. Missing representative suites are indented relative to their actual parent rather than absolute columns 8/12. Nested comments retain their indentation; English comment words are not concatenated by Japanese whitespace normalization.

The eight new regressions exercise valid top-level/two-space/tab/nested/commented suites, intentional dedents, representative damaged for/while suites, idempotence, and unfenced/non-Python text. The existing 152 MarkdownCodeRepair tests also pass (160 total).

This remains the existing heuristic repair engine, not a general Python parser. These changes address the reproduced indentation paths; they do not certify every possible Python program or alter the EDGE_GALLERY_COMPAT mode.

Original report and exact reproduction: PR #2588. No old header UI or unrelated Draft code is included.
