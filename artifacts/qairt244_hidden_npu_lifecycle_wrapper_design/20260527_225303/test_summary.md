# Test Summary

Added test file:

- `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuLifecycleWrapperTest.kt`

Covered cases:

- run-id scoped files include the current run id
- matching callback/state/result/native-diag/cleanup run ids accept clean run
- stale result is rejected
- native diag run-id mismatch is rejected
- result run-id mismatch is rejected
- cleanup evidence classifies success as clean
- failure with cleanup evidence is clean
- missing cleanup is suspect
- missing `Engine.close=unique_ptr_cleanup` is suspect
- timeout is suspect and forbids session reuse
- side-effect flags false are required
- H1 remains pinned to 128
- 256 and 512 policy constants remain fixed
- sequential 512 remains non-baseline
- per-run isolated 512 remains the only 512 candidate

NPU/device/runtime execution was not performed by these tests.

Validation result:

- `git diff --check`: pass
- `bash -n scripts/*.sh`: pass
- `./gradlew :app:compileDebugKotlin :app:compileCustomBuildExperimentDebugKotlin :app:testStandardDebugUnitTest :app:testCustomBuildExperimentDebugUnitTest :app:assembleCustomBuildExperimentDebug`: pass
