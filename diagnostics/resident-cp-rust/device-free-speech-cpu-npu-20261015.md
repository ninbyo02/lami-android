# Real free-running CPU/NPU speech pilot — 2026-10-15 06:53 JST

Text: こんにちは。 Both use prepared-hai-android-v1, Qwen3 cute TTS bundle, original codec heads and embeddings, fixed seed, CPU main model, dynamic ExecuTorch speech decoder, and CPU or SM8750 QAIRT 2.47 NPU CP backbone. EOS was required before WAV release. Artifacts were created in separate dated directories under /home/sato/lami-tts-daemon; the production LAMI app and models were NOT modified.

CPU CP: completed, EOS, 15 codec frames, 28,800 PCM samples at 24kHz, 1.20 s WAV, generation elapsed 5.464 s (host CPU). Audio WAV SHA256 bb4edc83d9224482d1bf5c2828dfdf4b58c78ccc37c9d2cf1a41b5d2a086cd39.

NPU CP: completed, EOS, 14 codec frames, 26,880 PCM samples at 24kHz, 1.12 s WAV, generation elapsed 132.984 s. Audio WAV SHA256 b13ddca8f7db2a1cb91c725271177081422f91508e82531aae1fc396eaf0e5d7.

The code rows diverged in the first codec frame. No listening judgement has been performed. The NPU pipeline reloads the QNN AOT context each step and transfers data with ADB; 132.984 s is a *diagnostic round-trip* time and NOT a resident-driver realtime speed benchmark. The CPU number is host time; they are not apples-to-apples latency comparisons. Next listen/review the two WAVs and measure resident JNI end-to-end without repeated reloads and transfers. Neither variant is automatically deployed or promoted.

Previously this script depended on a missing `main_program` JSON key. The model bundle explicitly hashes `stateless-28-int4-cache256-et14.pte`, so the diagnostic selects that pinned file directly. This is a pilot-only compatibility repair, not a change to normal LAMI inference.
