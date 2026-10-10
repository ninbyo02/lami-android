# Fixed16 GPU device attempt (2026-10-09 evening JST)

Two isolated runs on NX733J/SM8750 reached the CPU4 fixed16 reference and warm measurements, but failed while loading the hybrid Vulkan model. No GPU forward measurement completed and request1 was not reached. This is a blocked feasibility experiment, not evidence of GPU performance or quality.

CPU4 measured forward times: first run905/911ms (median908ms), retry1086/1128ms (median1107ms). All four measured CPU outputs exactly matched their run reference. Only one text prefix was measured; these are partial observations, not a complete controlled performance comparison. Different run conditions are not pooled.

Android exit-info records decoder PID19949 and21352 as APP CRASH(NATIVE), status6. Scoped data_app_native_crash records both SIGABRT with abort message `ptr`; stack includes fbjni getJavaExceptionForCppException, translatePendingCppExceptionToJavaException, libexecutorch, and Module.loadMethod. The underlying C++ exception is still unknown. Native crash is not catchable through the existing Java failure response; the UI observes process disconnection. logcat returned empty, so crash records were recovered through dumpsys dropbox. No GPU shader/operator cause has been established.

The second APK adds persistent recording of Java decoder error responses, useful for failures that reach the response path; this native abort does not. GPU opt-in remains debug-only, and normal AAR/model selection remains unchanged.

Each run used a fresh backup, checked all four staged model hashes, and restored the original APK, manifest, WAV, and two probe reports with exact hashes. All candidate files were removed and temporary files cleaned; both restore reports have no errors.

Next: export a minimal fixed-shape Vulkan model to isolate runtime initialization/exception handling from the full30-partition graph, then narrow failing delegate/operator groups. Do not promote this decoder or claim acceleration before successful forwards, numeric checks, and listening review.

Evidence paths and partial measurements are in the adjacent JSON report. Raw crash logs remain on the development host and are not committed.
