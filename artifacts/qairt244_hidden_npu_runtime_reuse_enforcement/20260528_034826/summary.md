# Hidden NPU Runtime Reuse Enforcement

Artifact: `artifacts/qairt244_hidden_npu_runtime_reuse_enforcement/20260528_034826/`

Scope: runtime policy, preflight enforcement, tests, and docs only. No NPU
execution, adb, RunDecode, native change, QAIRT rebuild, ChatScreen promotion,
assistant-list insertion, DB, TTS, Markdown renderer, streaming renderer, or
selectedPath=NPU persistence was performed.

Result: lifecycle classification now drives runtime reuse policy. Only
`SUCCESS_CLEAN` opens `next_prompt_allowed=true`. `TIMEOUT_SUSPECT`,
`CLEANUP_MISSING_SUSPECT`, stale result rejection, run-id mismatch rejection,
and other non-success classifications close the next prompt gate and require
hidden per-run isolated handling.

Policy remains unchanged: H1 is pinned to `max_output_tokens=128`, 256 remains
the hidden experimental baseline candidate, 512 remains
`hidden_per_run_isolated_512` only, sequential/Activity-restart-only 512 remain
rollback, and 1024/2048/4096 remain blocked.

Verification:

- `git diff --check`: passed
- `bash -n scripts/*.sh`: passed
- `./gradlew :app:compileDebugKotlin :app:compileCustomBuildExperimentDebugKotlin :app:testStandardDebugUnitTest :app:testCustomBuildExperimentDebugUnitTest :app:assembleCustomBuildExperimentDebug`: passed
