# QAIRT 2.44 App-Owned JNI Logcat Smoke

Date: 2026-05-22

## Purpose

This smoke test checks whether `customBuildExperimentDebug` can capture
app-owned native `__android_log_print` output without touching LiteRT, QAIRT,
QNN, `Backend.NPU`, or `Engine.initialize`.

## Implementation

Kotlin entry:

```text
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244AppJniSmoke.kt
```

Native source:

```text
app/src/customBuildExperimentDebug/cpp/lami_qairt244_smoke.cpp
```

Native library:

```text
liblami_qairt244_smoke.so
```

The library is built by the customBuildExperimentDebug-only Gradle task:

```text
buildQairt244AppJniSmokeCustomBuildExperimentDebugJni
```

It is written only under:

```text
app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/
```

It is not added to `app/src/main/jniLibs` and is not connected to standard,
npuExperiment, galleryStackExperiment, or release variants.

The native function emits:

```text
__android_log_print(ANDROID_LOG_ERROR, "QAIRT244_SMOKE", ...)
```

Marker:

```text
qairt244_app_jni_smoke_v1
```

## Activity Path

`NpuExperimentProbeActivity` now handles:

```text
run_app_jni_smoke=true
```

When this extra is present, the Activity executes only the app-owned JNI smoke,
writes `files/qairt244_app_jni_smoke.txt`, and returns before
`AcceleratorProbe.captureSnapshot`. That means the smoke path does not create
`Backend.NPU`, does not call `Engine.initialize`, and does not enter any
LiteRT-LM engine path.

## Script

```text
scripts/run_qairt244_app_jni_smoke.sh
```

The script:

1. assembles `customBuildExperimentDebug`
2. installs `customBuildExperimentDebug` directly with Gradle
3. clears logcat
4. starts the probe Activity with `run_app_jni_smoke=true`
5. collects `files/qairt244_app_jni_smoke.txt`
6. collects `adb logcat -b all -d -v time`
7. extracts `QAIRT244_SMOKE` / `qairt244_app_jni_smoke_v1`

It does not call `./update.sh update`.

## Result

Artifact:

```text
artifacts/qairt244_app_jni_smoke/20260522_071945/
```

Summary:

| Check | Result |
| --- | --- |
| APK contains `liblami_qairt244_smoke.so` | yes |
| `files/qairt244_app_jni_smoke.txt` exists | yes |
| native entry recorded in smoke file | yes |
| `QAIRT244_SMOKE` in logcat artifact | no |
| classification | `native-executed-logcat-missing` |

Smoke file:

```text
qairt244_app_jni_smoke_v1 native entry pid=24115 tid=24115 epochMs=1779401992001 runId=1779401985752 outputPathNull=0
```

The script collected 66 logcat lines after `adb logcat -b all -d -v time`, but
none contained `QAIRT244_SMOKE` or `qairt244_app_jni_smoke_v1`.

## Classification

The native app-owned JNI function executed successfully and wrote its marker to
the app-private smoke file. The same marker was not present in direct logcat
collection.

This points to a logcat capture/filter/timing/device logging issue, not a
LiteRT-LM-specific logging location issue. The previous absence of
`QAIRT244_SENTINEL` and `QAIRT244_DIAG` should therefore be interpreted with
this collector/logcat caveat.

## Next Step

Use file-backed diagnostics for the next LiteRT-LM dispatch boundary, or first
fix the logcat capture path by proving a known Java `Log.e` and native
`__android_log_print` tag can be read from the device after Activity completion.
