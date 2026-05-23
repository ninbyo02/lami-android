# QAIRT 2.44 NPU Diagnostic Editable Prompt Preview

Timestamp: 20260523_133833
Package: io.github.ninbyo02.lami.customnpu
Activity: io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity

## Result

- default_launch_input_enabled=false
- editable_extra=allowEditablePromptPreview=true
- editable_launch_input_enabled=true
- default_validator=isValid=true, reasonCode=ok, normalizedPrompt=Hi
- editable_ok_validator=isValid=true, reasonCode=ok, normalizedPrompt=Hi
- editable_ng_validator=isValid=false, reasonCode=contains_disallowed_char, value=Hello/LamiHi
- prompt_execution_connected=false
- run_button_uses_fixed_prompt=Hi
- run_button_connected=false
- npu_generation=false
- engine_initialize=false
- run_decode=false
- normal_chatscreen_connected=false
- selected_path_npu_normal_route=false

## Evidence

- default_prompt_preview_state.txt: app-private preview mirror after default launch.
- editable_prompt_preview_state.txt: app-private preview mirror after extra launch.
- editable_prompt_preview_ng_state.txt: app-private preview mirror after entering a disallowed slash character.
- screenshot_default.png: default launch screenshot shows input_enabled=false and disabled guarded run controls.
- screenshot_editable.png: editable preview screenshot shows input_enabled=true and contains_disallowed_char after invalid preview input.
- window_default.xml / window_editable.xml: XML mirror of captured app-private preview state. uiautomator dump returned `could not get idle state` on this device, so the app-private state file and screenshots are the primary evidence.
- logcat_tail.txt: no Engine.initialize, RunDecode, QNN/HTP/FastRPC, or guarded run marker evidence found during this read-only preview verification.

## Not Run

- NPU generation
- Engine.initialize
- RunDecode
- high-level generateResponse
- normal ChatScreen NPU route
- selectedPath=npu normal route
