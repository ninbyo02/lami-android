# NPU Standard Route S1 to S5 Completion Report

Date: 2026-06-01

Scope: documentation only. This report summarizes the current NPU standard
route promotion state and the boundary for the next investigation phase. It
does not change implementation code, native code, model files, `Backend.NPU`
persistence, QAIRT/QNN settings, or fallback policy.

## 1. Current Status

The NPU standard route has reached S5 on device validation.

Confirmed stages:

- S1 response display
- S2 DB save
- S3 Markdown integration
- S4-A pseudo streaming
- S5 TTS
- collapsible DEV diagnostics for the NPU standard route

Representative device diagnostics:

```text
route_type=standard_chat_screen_s5_npu_tts
db=true
tts=true
markdown=true
streaming=true
conversation_history_saved=true
run_decode_reached=true
fallback_used=false
timeout=false
fresh_crash=false
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
```

Important boundary: this proves the standard ChatScreen route can reach the
real NPU-backed diagnostic path and complete S1 through S5 behavior. It does
not prove that the app has fully applied or persisted `Backend.NPU` as the
main production backend. The current evidence level is
`QNN_HTP_V79_FastRPC_native_diag`.

## 2. Phase Specifications

| Phase | Purpose | Main behavior | Side effects |
| --- | --- | --- | --- |
| S1 display | Display the NPU response in ChatScreen | Single NPU decode result is sanitized and shown | `db=false`, `markdown=false`, `streaming=false`, `tts=false` |
| S2 DB save | Save successful NPU response to conversation history | User and final assistant rows are saved only after S1 quality gates pass | `db=true`, `conversation_history_saved=true` |
| S3 Markdown | Use the normal Markdown display/finalization path | Final assistant text is Markdown-finalized before display/save | `markdown=true`, `streaming=false`, `tts=false` |
| S4-A pseudo streaming | Stage the known final text into UI chunks | NPU decode is still one-shot; chunks are UI-only cumulative text | `streaming=true`, real token streaming is not connected |
| S5 TTS | Speak the final assistant text | TTS uses the final saved/displayed assistant text, not diagnostics or pseudo chunks | `tts=true` only in the S5/FULL phase |

## 3. Route Types

| Phase | route_type |
| --- | --- |
| S1 display | `standard_chat_screen_s1_npu_display_only` |
| S2 DB save | `standard_chat_screen_s2_npu_db_save` |
| S3 Markdown | `standard_chat_screen_s3_markdown` |
| S4-A pseudo streaming | `standard_chat_screen_s4a_npu_pseudo_streaming` |
| S5 TTS | `standard_chat_screen_s5_npu_tts` |

## 4. Device Validation Result

The current S5 device run confirms the standard route can report:

- `route_type=standard_chat_screen_s5_npu_tts`
- `db=true`
- `conversation_history_saved=true`
- `markdown=true`
- `streaming=true`
- `tts=true`
- `run_decode_reached=true`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`

This result is sufficient to treat S1 to S5 UI integration as complete for the
current standard-route promotion work. It should not be used as a claim that
`Backend.NPU` is fully attached or persisted through the production backend
selection path.

## 5. S2 Stability Runner Summary

The S2 DB stability runner was used to validate the DB-save gate before S3 to
S5 promotion.

Runner safety state:

- single-step execution is the default;
- batch execution requires an explicit option;
- prompt selection is 1-based;
- each prompt gets its own state and report fragments;
- success, failure, unsafe, and timeout cases generate md/csv fragments;
- failure, timeout, unsafe result, or ANR suspicion stops progression;
- receiver work avoids synchronous heavy decode in `onReceive`;
- receiver execution uses `goAsync()` plus background work;
- prompt-to-prompt sleep and device responsiveness checks are part of the adb
  runner policy.

S2 validation focus:

- successful natural Japanese responses can save to DB;
- DB save reports `conversation_history_saved=true`;
- fallback, timeout, and fresh crash remain false for accepted success cases;
- raw role contamination is blocked from DB save;
- mixed-language failures are not saved;
- user and assistant messages are not double-saved;
- report artifacts include per-prompt state fields and md/csv fragments.

The S2 runner result was the gate for moving from DB-only behavior to S3
Markdown, S4-A pseudo streaming, and S5 TTS.

## 6. Quality Gates

### Raw Role Contamination

Outputs containing raw role contamination are classified with the existing
role-contamination path and are not eligible for DB save. This gate remains in
place for S2 through S5.

Examples of blocked contamination categories include raw `User:` /
`Assistant:` style role text and Japanese role-label contamination where the
output looks like leaked conversation structure rather than assistant content.

### Mixed Language Handling

The mixed-language classifier remains a save gate. Outputs dominated by
non-Japanese scripts or English-main content remain `mixed_language` and are
not saved as S2/S3/S4-A/S5 success.

The classifier was relaxed for natural Japanese text containing Latin proper
nouns or technical terms. Natural Japanese responses containing terms such as
`Python`, `Android`, `GPU`, `CPU`, `NPU`, `Google`, `OpenAI`, `ChatGPT`,
`LiteRT`, `Qualcomm`, or `GitHub` can still be classified as
`natural_japanese` when Japanese text is the main body.

### Japanese Internal Space Normalization

The sanitizer/report path normalizes unnecessary half-width spaces between
Japanese characters in `sanitized_output`.

Examples:

- `承 知いたしました。` -> `承知いたしました。`
- `短くまとめ る` -> `短くまとめる`
- `日 本 語` -> `日本語`

Spaces between Latin terms are preserved:

- `Google AI` stays unchanged
- `NPU backend` stays unchanged

The normalization applies to final sanitized output used by the standard route
and to S2 stability report md/csv output. `raw_output` and `raw_native_output`
are not rewritten.

## 7. DEV Diagnostics UI

The NPU standard route DEV diagnostics are now collapsible below the normal
assistant response.

UI behavior:

- initial state is collapsed;
- collapsed label: `▶ DEV診断を表示`;
- expanded label: `▼ DEV診断を隠す`;
- red diagnostic text is shown only when expanded;
- copy controls are preserved when expanded:
  - input copy;
  - output copy;
  - diagnostic copy;
  - route/S4-A diagnostic copy where applicable.

The normal assistant body, Markdown display, pseudo streaming display, and TTS
behavior are separate from this diagnostic UI. The diagnostic text content
itself, including `route_type`, `db`, `markdown`, `streaming`, and `tts`, is
not changed by the collapse UI.

## 8. Not Yet Changed

The following areas are intentionally not completed by the S1 to S5 promotion:

- full `Backend.NPU` application through the production backend path;
- `Backend.NPU` persistence as the selected backend;
- QAIRT/QNN setting changes;
- QNN backend configuration changes;
- fallback policy changes;
- real token streaming from the NPU runtime;
- S4-B true streaming;
- native model/runtime stack changes.

Current NPU evidence remains:

```text
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
```

Do not describe the current state as fully `Backend.NPU` applied.

## 9. Next Phase

The next phase should focus on whether and how the standard route can move
from diagnostic NPU evidence to a more explicit backend application path.

Recommended investigation topics:

- `Backend.NPU` attach behavior;
- `EngineConfig` NPU configuration surface;
- LiteRT-LM Java/Kotlin API surface for backend selection;
- native/runtime API inventory needed to prove backend attachment;
- compatibility between current QAIRT/QNN artifacts and the app runtime;
- rollback conditions if explicit backend attachment destabilizes decode;
- diagnostics that distinguish "QNN/HTP evidence observed" from
  "`Backend.NPU` explicitly attached".

Parallel future UI/runtime topic:

- S4-B true streaming investigation, only if LiteRT-LM exposes a safe real
  token/chunk callback path for the selected model/runtime stack.

S4-B should remain separate from S4-A. S4-A is pseudo streaming based on a
known final string; S4-B would require real streaming support from the runtime.
