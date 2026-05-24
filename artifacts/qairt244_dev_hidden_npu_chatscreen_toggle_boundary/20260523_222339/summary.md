# DEV hidden NPU ChatScreen toggle boundary

- app: io.github.ninbyo02.lami.customnpu
- build: customBuildExperimentDebug
- toggle key: dev_enable_npu_chatscreen_route
- default: false
- Settings UI: visible in customBuildExperimentDebug DEBUG settings
- Settings UI switch state: checked=false in window dump
- ChatScreen guard source: SettingsPreferences.devEnableNpuChatScreenRouteFlow
- guard default: false, gated by BuildConfig.CUSTOM_BUILD_EXPERIMENT
- blocked branch fired: no
- adapter_not_connected observed: no in logcat
- selectedPath=npu applied: no evidence
- NPU generation: not run
- Engine.initialize: not run
- RunDecode: not run
- high-level generateResponse: not changed or invoked
- note: this verification did not toggle the switch and did not send a prompt
