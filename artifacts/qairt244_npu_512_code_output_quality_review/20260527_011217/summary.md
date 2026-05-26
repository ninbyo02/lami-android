# QAIRT244 NPU 512 Code Output Quality Review

Date: 2026-05-27

Source artifact:
`artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116/`

Scope: artifact-only review. No additional NPU execution, 512 retry, 1024+
expansion, native guard change, QAIRT rebuild, ChatScreen promotion, assistant
message insertion, DB, TTS, Markdown route, streaming route, or selected-path
NPU persistence was performed.

The bounded retry proved the Python calculator prompt can complete at
`max_output_tokens=512` under `timeout_seconds=60`:

- result classification: `success_but_slow`
- prompt: `Pythonで簡単な電卓コードを書いて`
- `quality_classification=useful_code`
- `timeout=false`, `fresh_crash=false`, `fallback_used=false`
- `RunDecode reached=true`
- `decode_ms=11600`, `elapsed_ms=14000`
- QNN evidence: `QNN_HTP_V79_FastRPC_native_diag`
- cleanup evidence: `cleanup_elapsed_ms=142`,
  `Engine.close=unique_ptr_cleanup`
- side-effect flags: selected-path, standard route, normal UI route,
  assistant-list insertion, DB, TTS, Markdown, and streaming all false

Quality review decision: the run is NPU-safety successful, but it is not a 512
baseline candidate. The raw output preserves Python indentation, but the
sanitized output loses indentation inside the code block. Both raw and
sanitized outputs end with an unclosed Python code fence and a mid-statement
tail (`elif choice == '`), consistent with token-limit truncation after normal
native completion.

512 remains extended experimental. 256 remains the hidden experimental
baseline candidate. 1024 remains blocked until the code display quality gate is
designed and a separately approved bounded 512 three-prompt comparison passes.
