# User listening review — 2026-10-15 07:02 JST

Listening samples in Voice Lab http://192.168.52.99:8088/ , group LAMI-CPU-NPU-20261015, utterance 「こんにちは。」.

User feedback (translated faithfully):
- CPU variant sounds slightly calmer.
- NPU variant is closer to the desired LAMI voice atmosphere.
- NPU intonation needs improvement.

Decision: retain BOTH source WAV files unmodified, and use the NPU style as the preferred voice-direction reference, not as a declared production-ready model. Prioritize Japanese prosody and accent / phrase contour, especially intonation of 「こんにちは。」. Do not use crude global pitch-speed shifts as a replacement for proper Japanese intonation. Keep speaker character, timbre and energy while separately testing sampling seed/temperature/top-k, phrase pronunciation controls, and decoder/audio quality against reference utterances. Listening validation is still necessary. No deployment or adoption solely on this impression.
