# Isolated vs Sequential Compare

| Scenario | Prompt position | Timeout | Result | Decode / elapsed | Output | Cleanup | Backend evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 256 three-prompt | second | 30s | success, `useful_code` | `decode_ms=7351`, `elapsed_ms=9000` | non-empty code output | completed | `QNN_HTP_V79_FastRPC_native_diag` |
| 512 isolated bounded retry | only prompt | 60s | success, `useful_code` | `decode_ms=11600`, `elapsed_ms=14000` | non-empty code output | `cleanup_elapsed_ms=142`, `Engine.close=unique_ptr_cleanup` | `QNN_HTP_V79_FastRPC_native_diag` |
| 512 code-aware three-prompt | second | 60s | timeout | `decode_ms=unavailable`, `elapsed_ms=70000` | no completed raw/sanitized output | unavailable | pre-RunDecode evidence only |

Interpretation:

- 256 remains stable for the code prompt.
- 512 can complete in an isolated bounded run.
- 512 is not stable in the current sequential three-prompt runner.
- The code-aware sanitizer cannot help when the native decode does not return a
  completed output.

The meaningful difference is not sanitizer behavior; it is run context:
isolated single prompt versus second prompt in a warm sequential run.
