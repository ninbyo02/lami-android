# Test Summary

Added:

- `app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuLifecycleSummary.kt`
- `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuLifecycleSummaryIntegrationTest.kt`

Extended:

- `DevOnlyNpuLifecycleArtifactParserTest` now rejects unscoped artifact file
  names.

Covered:

- summary shows `SUCCESS_CLEAN`
- timeout maps to `TIMEOUT_SUSPECT`
- cleanup missing maps to `CLEANUP_MISSING_SUSPECT`
- stale result rejected
- run-id mismatch rejected
- suspect session sets `reuse_allowed=false`
- suspect session sets `hidden_per_run_isolated_required=true`
- side-effect flags false are required
- H1 remains pinned to 128
- 256 hidden baseline candidate remains distinct
- 512 sequential remains rollback
- 512 per-run isolated remains the only 512 candidate
- 1024 remains blocked

NPU/device/runtime execution was not performed.

Validation result:

- `git diff --check`: pass
- `bash -n scripts/*.sh`: pass
- `./gradlew :app:compileDebugKotlin :app:compileCustomBuildExperimentDebugKotlin :app:testStandardDebugUnitTest :app:testCustomBuildExperimentDebugUnitTest :app:assembleCustomBuildExperimentDebug`: pass
