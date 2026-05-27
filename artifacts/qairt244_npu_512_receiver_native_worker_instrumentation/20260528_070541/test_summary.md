# Test Summary

Planned verification:
- `git diff --check`: passed
- `bash -n scripts/*.sh`: passed
- `./gradlew :app:compileDebugKotlin`: passed
- `./gradlew :app:compileCustomBuildExperimentDebugKotlin`: passed
- `./gradlew :app:testStandardDebugUnitTest`: passed
- `./gradlew :app:testCustomBuildExperimentDebugUnitTest`: passed
- `./gradlew :app:assembleCustomBuildExperimentDebug`: passed

Unit coverage added:
- Clean marker order -> `WORKER_COMPLETED_CLEAN`
- Throwable marker -> `WORKER_THROWABLE_CAUGHT`
- Native enter without native return/finally ->
  `NATIVE_NON_RETURN_OR_PROCESS_DEATH`
- Native return without terminal result ->
  `TERMINAL_RESULT_WRITE_MISSING`
- Terminal result without cleanup -> `CLEANUP_MISSING`
- runId mismatch reject
- stale trace reject
- side-effect flags remain false
- H1 128 pin, 256 candidate, 512 per-run isolated candidate, and 1024 block
  remain unchanged

Focused result:
- `./gradlew :app:testCustomBuildExperimentDebugUnitTest --tests io.github.ninbyo02.lami.npu.DevOnlyNpuTerminalTraceTest`
  passed.
