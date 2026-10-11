# CP INT8 listening review (2026-10-06)

User review read from Voice Lab `/api/reviews` at 20:24 JST, group `lami-cp-int8-review-20261006`. These are the full candidate-token-feedback WAVs documented in the full-feedback pilot, not teacher-forced audio. Existing review statuses and comments were read without modification.

| File | Saved status | User comment |
|---|---|---|
| 01 窓と赤い箱 / FP32 | unset | 問題なし。 |
| 02 窓と赤い箱 / MLP INT8 | hold | 問題なし。おいてください、のイントネーションが気になる。 |
| 03 窓と赤い箱 / full INT8 | candidate | 問題なし。 |
| 04 午前九時半の予定 / FP32 | reject | 最後のブレスが気になる。 |
| 05 午前九時半の予定 / MLP INT8 | reject | 問題なし。ごぜんくじはん、の声が高すぎる。 |
| 06 午前九時半の予定 / full INT8 | candidate | 問題なし。 |

## Decision

Prioritize the full CP INT8 PTQ pilot for isolated Android performance evaluation. Both reviewed sentences were marked candidate without an audible problem. MLP-only INT8 had smaller later-frame numerical errors but worse subjective prosody on these sentences, so numerical/token agreement alone must not determine promotion.

This is positive listening evidence for two sentences and seed 42, not broad voice quality acceptance or approval of every phrase. Keep production playback unchanged until a device speed comparison and broader listening regression are completed. No QAT pilot is promoted by this review.

Candidate PTE SHA-256: `8d0843096887167a64610e33569cc6c15b61dfedd19ddada23ae98c205cd1d87` (79,064,960 bytes). The main transformer, decoder, heads and embeddings remain FP32. Realtime is not achieved; speeding CP alone still leaves the main transformer and 16 serial CP steps per audio frame.

Next device test: back up installed APK, use an isolated debug candidate with the approved FP32 main and selected INT8 CP, compare FP32/INT8 in alternating order with fresh and warm sessions, record first-audio latency, frame p50/p95, EOS, waveform validity and thermal state, then restore and hash-check the original APK. Give the user a duration estimate before starting. Expand listening coverage to short replies, numbers, longer sentences and multiple seeds before normal playback adoption.
