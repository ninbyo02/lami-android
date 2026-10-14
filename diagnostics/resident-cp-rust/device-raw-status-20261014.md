# SM8750 QNN deviceCreate raw error diagnosis — 2026-10-14 JST

Isolated diagnostic package io.github.ninbyo02.lami.cpdiagnostic, explicit activity only.
The bounded load/release attempt reported JNI status 5014001.
Rust diagnostic encoding is stage * 1,000,000 + (SDK status & 0xfffff).
Thus stage=5 (QNN deviceCreate) and raw SDK status=14001 (0x36B1).

In the installed official QAIRT 2.47 SDK:
- QnnCommon.h: QNN_MIN_ERROR_DEVICE = 14000.
- QnnDevice.h: QNN_DEVICE_ERROR_INVALID_CONFIG = QNN_MIN_ERROR_DEVICE + 1.
Therefore status 14001 = QNN_DEVICE_ERROR_INVALID_CONFIG.

Prior project notes for QAIRT 2.44 on this phone reported an identical
deviceCreate status after a libcdsprpc.so linker namespace failure.
That is a plausible hypothesis, not confirmation for this QAIRT 2.47 run.
A backend log callback or explicit loader diagnostics are necessary before
changing any runtime or repeating context creation.

No QNN context created, no CP steps, NPU inference, TTS audio or voice changes.
Diagnostic app process was force stopped. Existing LAMI remained untouched.
