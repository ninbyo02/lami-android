# DEV NPU ChatScreen Toggle ON Blocked Branch Verify

- timestamp: 20260523_223850
- package: io.github.ninbyo02.lami.customnpu
- build variant: customBuildExperimentDebug
- toggle key: dev_enable_npu_chatscreen_route
- toggle helper: customBuildExperimentDebug-only DevNpuChatScreenToggleActivity
- prompt used: Hello

## Toggle State

- precondition reset: toggle_state_before.txt shows requested_enabled=false and after=false.
- ON step: toggle_state_after_on.txt shows before=false and after=true.
- OFF recovery: toggle_state_after_off.txt shows before=true and after=false.
- final toggle state: false

## ChatScreen Result

- ChatScreen DEV-only blocked branch fired: yes.
- UI evidence: screenshot_after.png shows transient Snackbar text:
  - DEV NPU blocked
  - status=BLOCKED
  - reason=adapter_not_connected
  - db=false
  - tts=false
- real NPU adapter connected: false
- selectedPath=npu applied: false
- Engine.initialize executed: false
- RunDecode executed: false
- NPU generation executed: false
- DB/TTS/Markdown/streaming connected: false

## Runtime Marker Scan

- runtime_marker_scan.txt is empty after the blocked-branch run.
- No Engine.initialize, RunDecode, Backend.NPU, QNN, HTP, FastRPC, QAIRT244, or selectedPath=npu runtime markers were found.

## Notes

- The test helper Activity only reads/writes the DEV hidden toggle and writes a state file.
- It does not call the planner, adapter, NPU, Engine.initialize, or RunDecode.
- The prompt remains in the input field after the blocked branch because the branch returns before input clearing and before DB/message insert.
