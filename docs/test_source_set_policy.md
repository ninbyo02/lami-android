# Test Source Set Policy

This document defines where tests should live before pre-release cleanup.

## Source sets

| Source set | Purpose |
|---|---|
| app/src/test | Release-critical pure JVM unit tests for standard app behavior. Keep this fast and readable. |
| app/src/androidTest | Device/instrumented tests for standard UX, persistence, navigation, and Android framework behavior. |
| app/src/testCustomBuildExperimentDebug | Custom NPU / QAIRT / debug native route tests. These are valuable but should not inflate the default release-critical unit-test surface. |
| app/src/testStandardDebug | Standard-debug-only tests, only when behavior depends on the debug source set. |
| app/src/debug | Debug-only receivers, probes, and manual diagnostics. Do not rely on these for release behavior. |
| app/src/testDisabled | Not allowed as a long-term holding area. Revive tests into an active source set or delete/rewrite them. |

## Rules

1. app/src/test should contain stable contracts for user-facing standard behavior.
2. Debug/probe/NPU experiment tests should live in a matching debug or custom experiment source set.
3. Do not add new tests to app/src/testDisabled.
4. Android Studio template tests should be removed once real coverage exists.
5. Release preparation must verify that debug/probe code is not packaged in standardRelease.

## Examples

Keep in app/src/test:

- Markdown repair and rendering contracts
- Chat repository and title behavior
- Settings preference parsing and defaults
- TTS text formatting
- Navigation route contracts

Move out of default app/src/test when practical:

- QAIRT / QNN / NPU probe diagnostics
- DevOnly route matrix tests
- custom native holder smoke/stability tests
- GPU benchmark receiver parity tests

## Pre-release checks

Run:

    ./gradlew :app:testStandardDebugUnitTest
    ./gradlew :app:testCustomBuildExperimentDebugUnitTest
    ./gradlew :app:assembleStandardRelease
    ./gradlew :app:lintStandardRelease

Scan default unit tests for misplaced debug/probe tests:

    find app/src/test/java -name '*Test.kt' | grep -E 'DevOnly|Probe|Smoke|Diagnostics|Qairt244|Qnn|Gpu|Jni|TrueEngine|PersistentHolder' && echo 'debug/probe tests still in app/src/test' || true
