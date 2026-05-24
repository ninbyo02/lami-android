# ChatScreen DEV-only NPU blocked branch

- ChatScreen insertion: app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt, InferenceTarget.LOCAL after requestPrompt blank check and before DB/TTS/Markdown/streaming paths.
- guard: BuildConfig.CUSTOM_BUILD_EXPERIMENT && DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED
- current toggle: false
- false-toggle behavior: existing LOCAL path unchanged
- true-path target: DevOnlyNpuChatScreenBlockedBranch through reflection
- true-path result: status=BLOCKED reason=adapter_not_connected
- side-effect flags: db=false tts=false markdown=false stream=false
- selectedPath=npu: not applied
- NPU generation: not run
- Engine.initialize: not run
- RunDecode: not run
- high-level generateResponse: not changed
- standard/release impact: none expected; direct custom NPU imports were not added to main ChatScreen.
