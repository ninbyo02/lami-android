# Test Summary

Added:

- `app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuLifecycleArtifactParser.kt`
- `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuLifecycleArtifactParserTest.kt`

Covered cases:

- expected run id with completed cleanup => `SUCCESS_CLEAN`
- stale result rejected
- state run-id mismatch rejected
- result run-id mismatch rejected
- native diag run-id mismatch rejected
- cleanup run-id mismatch rejected
- timeout => `TIMEOUT_SUSPECT`
- cleanup missing => `CLEANUP_MISSING_SUSPECT`
- missing success callback => `CLEANUP_MISSING_SUSPECT`
- missing native completed evidence => `CLEANUP_MISSING_SUSPECT`
- side-effect flags must remain false
- suspect sessions forbid reuse
- 512 sequential rejected
- 512 per-run isolated accepted only with clean evidence
- H1 128 pin maintained
- 1024 blocked

NPU/device/runtime execution was not performed.

Validation result:

- `git diff --check`: pass
- `bash -n scripts/*.sh`: pass
- `./gradlew :app:compileDebugKotlin :app:compileCustomBuildExperimentDebugKotlin :app:testStandardDebugUnitTest :app:testCustomBuildExperimentDebugUnitTest :app:assembleCustomBuildExperimentDebug`: pass
