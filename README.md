# LAMI Android

Android-first local AI assistant focused on Edge AI, LiteRT, offline inference, and shareable AI personalities.

## Overview

LAMI (ラミィ) is an Android app project for building a local-first AI assistant experience on mobile devices. The project focuses on Android-native chat, local inference experiments, voice interaction, developer diagnostics, and future shareable AI personality formats.

LAMI can integrate with Ollama, but it is not intended to be an Ollama-only client. Ollama is one supported backend path alongside Android local inference work based on LiteRT / MediaPipe-style local LLM APIs and future accelerator experiments.

Package name:

```text
io.github.ninbyo02.lami
```

## Features

### Implemented / Available

- Android chat UI built with Jetpack Compose
- Ollama backend integration
- Streaming response UI
- Android text-to-speech support
- Local inference integration hooks and probes
- Inference statistics and developer diagnostics
- Sprite animation state handling and debug settings

### Experimental

- LiteRT / MediaPipe local LLM flow
- Held local engine lifecycle optimization
- Tokenizer-based local inference statistics
- Sentence-based streaming TTS
- Sprite animation/debug configuration tooling
- Qualcomm QNN / NPU readiness diagnostics

### Planned

- Qualcomm QNN / NPU acceleration path
- LAMI ASR integration
- User-facing sprite character editor
- QR-based sprite/personality sharing
- Shareable local AI personality format
- Contribution guide and public project documentation cleanup

## Screenshots

Screenshots will be updated with current LAMI Android UI.

Planned screenshots:

- Chat screen
- Local inference statistics
- TTS / voice interaction
- Sprite character editor
- Developer diagnostics

<!-- TODO: Add current LAMI Android screenshots when available. -->

## Why LAMI?

LAMI is built around these project directions:

- **Local-first:** prefer on-device or user-controlled inference paths where practical.
- **Android-first:** treat Android as the primary runtime, not a thin desktop-client companion.
- **Offline-capable direction:** design features so local workflows can improve over time without assuming constant network access.
- **Edge AI experimentation:** explore LiteRT, MediaPipe-style local LLM APIs, tokenizer metrics, and mobile accelerator paths.
- **Privacy-conscious design:** reduce unnecessary data movement by keeping local inference and diagnostics in scope.
- **AI personality sharing direction:** develop sprite/personality concepts that can eventually be shared between devices.

## Architecture / Backends

### Ollama Backend

The Ollama backend is an available integration path for users who already run an Ollama-compatible model service. LAMI keeps this as one backend option rather than the whole product identity.

### Local LiteRT / MediaPipe Backend

Local inference support is experimental. The repository contains LiteRT-LM / MediaPipe-style probing, engine lifecycle work, streaming attempts, and local inference statistics plumbing. Model compatibility, runtime behavior, and performance should be treated as active development areas.

### Qualcomm QNN / NPU Path

QNN / NPU support is planned and experimental at the diagnostics level. Current work focuses on readiness checks, native library probing, fallback behavior, and documentation. Do not treat NPU acceleration as supported or enabled by default.

### ASR, TTS, and Personalities

TTS support is available. ASR integration, richer sprite editing, QR sharing, and a shareable local AI personality format are planned project directions.

## Roadmap

- [ ] Replace legacy screenshots with current LAMI Android screenshots
- [ ] Stabilize LiteRT local inference
- [ ] Improve local inference statistics
- [ ] Add QNN delegate / NPU acceleration experiments
- [ ] Add LAMI ASR integration
- [ ] Add user-facing sprite editor
- [ ] Add QR sprite/personality sharing format
- [ ] Define shareable local AI personality format
- [ ] Prepare contribution guide
- [ ] Clarify release process and public documentation

## Development

Open the project with Android Studio or use the Gradle wrapper from the repository root.

Current Android configuration:

- Application ID: `io.github.ninbyo02.lami`
- Minimum SDK: 34
- Target SDK: 35
- Compile SDK: 35
- Java compatibility: 11

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

Run unit tests:

```bash
./gradlew test
```

The repository also includes `update.sh`, a single-developer helper script for local update, build, install, and test workflows. It is optional and should be reviewed before use:

```bash
./update.sh
```

## Documentation

- `docs/ui/LAMI_STANDARD_LAYOUT.md`: LAMI UI density, spacing, and inset guidance.
- `docs/qualcomm-qnn-npu-setup.md`: current QNN / NPU setup notes and limitations.

## Attribution

LAMI is its own Android project, but parts of the repository history and notices acknowledge prior Ollama Android client work. See `NOTICE` for the current attribution details. This README intentionally describes the current LAMI direction rather than presenting the app as an Ollama-only client.

## License

This project includes a `LICENSE` file containing the Apache License, Version 2.0.

See `NOTICE` for third-party attribution, including MIT-licensed upstream material referenced there.
