# Test Summary

- `./gradlew :app:testCustomBuildExperimentDebugUnitTest --tests '*DevOnlyNpuSequentialSoftResetGateTest'`: passed
- `git diff --check`: passed before final staging
- `bash -n scripts/*.sh`: passed before final staging
- `./gradlew :app:compileDebugKotlin :app:compileCustomBuildExperimentDebugKotlin :app:testStandardDebugUnitTest :app:testCustomBuildExperimentDebugUnitTest :app:assembleCustomBuildExperimentDebug`: passed
