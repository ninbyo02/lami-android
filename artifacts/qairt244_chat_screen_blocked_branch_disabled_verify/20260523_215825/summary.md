# ChatScreen blocked NPU branch disabled-state verification

- app: io.github.ninbyo02.lami.customnpu
- launch: io.github.ninbyo02.lami.MainActivity
- build: customBuildExperimentDebug
- toggle: DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED=false
- activity_start: success
- ChatScreen visible: yes, window dump shows normal ChatScreen prompt area and Ready status
- blocked branch fired: no
- blocked Snackbar observed: no
- adapter_not_connected observed on launch: no
- selectedPath=npu applied: no evidence
- NPU generation: not run
- Engine.initialize: not run
- RunDecode: not run
- high-level generateResponse: not changed or invoked by this verification
- DB/TTS/Markdown/streaming: no verification action triggered those paths
- note: no prompt was sent; this is a disabled-state launch verification only
