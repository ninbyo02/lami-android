# QAIRT 2.47 backend callback attempt — 2026-10-14 JST

A bounded isolated cpdiagnostic app probe was run on NX733J SM8750.
Official QNN logCreate callback was added and passed to backendCreate/deviceCreate.
Android arm64 compilation succeeded, host Rust ffi test suite passed (17).
The device continued to report status=5014001, mapping to deviceCreate
QNN_DEVICE_ERROR_INVALID_CONFIG (14001). QNN callback messages were not observed
in the captured logcat query. No context was created and no CP step executed.

This does not establish that the RPC namespace is the cause. Before further
device retries, inspect SDK callback setup and capture diagnostics through
a bounded in-app receipt rather than depending solely on Android logcat.
Ordinary LAMI app/model/audio and other jobs were left unchanged.
