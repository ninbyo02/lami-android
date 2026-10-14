# QAIRT 2.47 HTP transport failure — 2026-10-14 JST

Target: isolated cpdiagnostic app on nubia NX733J / SM8750, Android 16.
A bounded 8 KiB QNN SDK log receipt was added to C and retrieved via JNI from
the diagnostic app's private file. The probe did not run inference or audio.

Observed milestones:
- QnnBackend_create completed successfully.
- First connection to QNN HTP V79 stub established.
- Remote functions loaded; effective CDSP domain ID 3.
- QNN_TRANSPORT_CONFIG crc32 failed in cpu, crcRX received 0.
- DspTransport.transport_config failed: rpc 0x0000000b, cfg 0x00000000.
- Failed to create transport instance: 1002.
- Transport layer setup failed: 14001 / QNN_DEVICE_ERROR_INVALID_CONFIG.
- QNN contextCreateFromBinary and graph execution were NOT reached.

Implication: the earlier hypothesis of a simple missing libcdsprpc.so is not
supported by these logs; the failure occurs after stub connection and CDSP
identification. Inspect matching QAIRT 2.47 DSP skeleton deployment and
device firmware/transport compatibility, without copying private Android
libraries, modifying vendor partitions, disabling security or guessing transport
configuration. The exact root cause within DSP transport remains open.

Do not repeatedly retry the same binary/configuration. Preserve existing
LAMI application, voice, stored WAVs and other processes.
