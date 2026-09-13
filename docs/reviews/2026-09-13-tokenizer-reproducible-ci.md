# Tokenizer-only reproducible build and CI

Date: 2026-09-13

## Scope

PR #2608 adopted the validated standalone SentencePiece provider for explicit successful GPU post-response recounts. This follow-up makes its optional native artifact reproducible and verifiable on a clean hosted runner. It does not change runtime routing, counts, fallback, cancellation, NPU, CPU, server or Release behavior.

## Reproducibility boundary

- Pin SentencePiece to commit `31646a467d2051eb904e0b45de3a73e91fe1c1e3`.
- Pin CMake 3.28.3, Ninja 1.11.1.1 and Android NDK 28.2.13676358.
- Normalize source/build paths and set `SOURCE_DATE_EPOCH`, locale and timezone.
- Build twice from independent clean source paths and require byte-identical distributable artifacts: manifest, arm64 JNI library and notices.
- Keep the native binary out of Git; CI builds and verifies it from source.

## CI gates

The dedicated workflow verifies the real host JNI with a deterministic synthetic SentencePiece fixture in raw-model and LiteRT-LM-container forms, including 11 text cases and three malformed/oversized/recovery cycles per form. It also verifies the Android ELF architecture, 16 KiB segment alignment, one exported JNI symbol and absence of LiteRT, QNN and shared C++ runtime dependencies.

Four APK modes are checked sequentially: enabled, disabled, re-enabled after the same Gradle workspace, and Release. Enabled Debug must contain the exact library and license files. Disabled Debug and Release must exclude them. Existing native libraries must remain byte-identical between enabled and disabled Debug packages.

## Local clean-harness result

The JDK 21 run completed successfully in 287 seconds. Both Android builds produced SHA-256 `3cfa7b29cb9cc376a44c82a44876b94b61bc52190c299c10c94690bd21adb522`. The enabled/disabled Debug packages each retained 43 existing native libraries; Release excluded the tokenizer artifact. Host JNI checks passed for both fixture formats.

The synthetic fixture proves build and integration behavior only. The actual Gemma tokenizer parity and same-device performance evidence remain in PR #2607; this CI does not broaden the validated tokenizer fingerprint or supported runtime routes.
