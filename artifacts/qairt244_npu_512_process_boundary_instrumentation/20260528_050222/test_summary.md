# Test Summary

Verification:

- `DevOnlyNpuProcessBoundaryPolicyTest`
  - process present before dispatch allows dispatch and reuse.
  - process absent before dispatch blocks dispatch.
  - disappearance after cleanup is suspect and forbids reuse.
  - unknown snapshot blocks dispatch.
  - H1/256/512/1024 policy remains unchanged.

- `bash -n scripts/*.sh`: passed.
- `git diff --check`: passed.
- targeted `DevOnlyNpuProcessBoundaryPolicyTest`: passed.
- `./gradlew :app:compileDebugKotlin`: passed. Kotlin incremental cache
  failed with `NegativeArraySizeException` and fell back to non-incremental
  compilation; the task completed successfully.
- `./gradlew :app:compileCustomBuildExperimentDebugKotlin`: passed.
- `./gradlew :app:testStandardDebugUnitTest`: passed.
- `./gradlew :app:testCustomBuildExperimentDebugUnitTest`: passed.
- `./gradlew :app:assembleCustomBuildExperimentDebug`: passed.
