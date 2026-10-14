# QAIRT 2.47 QNN context creation success — 2026-10-14 JST

Target NX733J Snapdragon SM8750. Isolated cpdiagnostic package and separate
cp_context_probe process. A pinned QAIRT 2.47 unsigned Hexagon V79 skeleton
was copied into the diagnostic application's private files directory, then
SHA-256 and size checked before setting ADSP_LIBRARY_PATH in that process only.
The diagnostic also searched normal vendor DSP directories. No vendor or
system partition modification.

Pinned skeleton SHA-256: dd8987c17928e53877e51ce5739c550d3e74393bd5b1b5ccf311326eddb08612.
Diagnostic activity returned:
QNN_CONTEXT_OPEN_OK; QNN_STEPS_NOT_EXECUTED
SDK callback showed transport run status=0 and shared buffer status=0,
unlike previous QNN_TRANSPORT_CONFIG crc32 failure.

This is real device context-load evidence, NOT a 16-step inference, accuracy,
TTS latency or audio-generation success. An explicit short run with exact
tensor validation, timeouts and isolation is the next phase.
