# LAMI Android

[日本語版 README](README_ja.md)

Android-first local AI assistant platform focused on Edge AI, LiteRT, local inference, expressive sprite characters, and future shareable AI personalities.

## Overview

LAMI (ラミィ) is an Android app project for building a local-first AI assistant experience on mobile devices. The project focuses on Android-native chat, local inference experiments, expressive character UI, voice interaction, developer diagnostics, and future shareable AI personality formats.

LAMI can integrate with Ollama, but it is not intended to be an Ollama-only client. Ollama is one supported backend path alongside Android local inference work based on LiteRT / MediaPipe-style local LLM APIs and future accelerator experiments.

Package name:

```text
io.github.ninbyo02.lami
```

## Project Scope

LAMI is not only an Ollama UI. The project is an Android-native local AI platform direction for experimenting with Edge AI workflows, local inference runtimes, character-oriented interaction, and mobile-first diagnostics.

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

## Current Focus

- LiteRT local inference experiments
- Android Edge AI workflows
- Sprite state system and character feedback
- Streaming UX for chat, local inference, and TTS
- Local inference diagnostics and runtime visibility
- Future QNN / NPU research direction

## Why LAMI?

LAMI is built around these project directions:

- **Local-first:** prefer on-device or user-controlled inference paths where practical.
- **Android-first:** treat Android as the primary runtime, not a thin desktop-client companion.
- **Character-oriented UI:** keep expressive sprite characters and assistant personality as part of the product direction.
- **Experimental Edge AI direction:** explore LiteRT, MediaPipe-style local LLM APIs, tokenizer metrics, and mobile accelerator paths.
- **Privacy-conscious direction:** reduce unnecessary data movement by keeping local inference and diagnostics in scope.
- **Future AI personality sharing:** develop sprite/personality concepts that can eventually be shared between devices.

## Design Philosophy

LAMI favors small, understandable pieces over a single opaque AI feature. The project keeps Android-native UX, local-first workflows, character-oriented interaction, and Edge AI experimentation visible in the architecture. Offline-capable workflows, privacy-conscious behavior, expressive sprite characters, and shareable AI personalities are directions that guide the design, not claims that every workflow is finished today.

## Why Sprite Characters?

Sprite characters give LAMI a lightweight way to make assistant state readable on Android. They can express idle, thinking, speaking, error, and future personality states without requiring a heavy avatar stack. The goal is an emotionally readable interaction model that stays practical for mobile UI and can later support shareable sprite/personality formats.

## Edge AI / Local Inference

### Current

- Ollama backend support
- Android local inference experiments
- LiteRT / MediaPipe exploration
- Streaming response UI
- Inference diagnostics and developer stats

### Future / Experimental

- Qualcomm QNN delegate research
- NPU acceleration experiments
- Local ASR integration
- Shareable AI personalities

These areas are active research and integration work. Do not assume QNN/NPU acceleration, offline-only operation, or stable local inference behavior from this README alone.

## Research Status

| Area | Status |
|---|---|
| Ollama backend | Available |
| LiteRT integration | Experimental |
| Local inference diagnostics | Active development |
| Streaming TTS | Experimental |
| QNN delegate | Research |
| ASR integration | Planned |
| Sprite personality sharing | Planned |

## Non-Goals / Current Limitations

- Local inference support is still experimental and may vary by model, runtime, and device.
- Device compatibility may vary, especially for Edge AI and accelerator-related experiments.
- QNN / NPU work is research-stage and should not be treated as generally supported.
- Full offline workflows are still evolving.
- The README describes project direction and current integration work, not a finished stable release.

## Architecture Overview

```text
Android UI (Jetpack Compose, sprite character UI, TTS)
  |
  v
Backend abstraction and runtime selection
  |
  +-- Ollama backend (available)
  |
  +-- LiteRT / MediaPipe local inference path (experimental)
  |
  +-- Future local inference backends (planned / experimental)
        |
        v
      Future QNN / NPU acceleration direction (planned / experimental)
```

The current architecture keeps backend work separated from the Android UI so that Ollama integration, local inference experiments, diagnostics, and future accelerator paths can evolve without making the app an Ollama-only client.

## Architecture / Backends

### Ollama Backend

The Ollama backend is an available integration path for users who already run an Ollama-compatible model service. LAMI keeps this as one backend option rather than the whole product identity.

### Local LiteRT / MediaPipe Backend

Local inference support is experimental. The repository contains LiteRT-LM / MediaPipe-style probing, engine lifecycle work, streaming attempts, and local inference statistics plumbing. Model compatibility, runtime behavior, and performance should be treated as active development areas.

### Qualcomm QNN / NPU Path

QNN / NPU support is planned and experimental at the diagnostics level. Current work focuses on readiness checks, native library probing, fallback behavior, and documentation. Do not treat NPU acceleration as supported or enabled by default.

### ASR, TTS, and Personalities

TTS support is available. ASR integration, richer sprite editing, QR sharing, and a shareable local AI personality format are planned project directions.

## Supported / Tested Devices

| Device | Status | Notes |
|---|---|---|
| Nubia Z70S Ultra | Experimental | Snapdragon Edge AI experiments |
| Android Emulator | Supported | Development and testing |

Device reports are welcome, especially for Android local inference, LiteRT / MediaPipe behavior, and accelerator diagnostics.

## Screenshots

Screenshots will be updated with current LAMI Android UI.

Planned screenshots:

- Chat screen
- Local inference statistics
- TTS / voice interaction
- Sprite character editor
- Developer diagnostics

<!-- TODO: Add current LAMI Android screenshots when available. -->

## Project Direction

- Android-native AI experience
- Local-first AI workflows
- Character + AI integration
- Edge AI and local inference experiments
- Shareable personality direction
- Developer diagnostics for mobile AI runtimes

## Future Directions

- Local ASR integration (planned)
- QNN delegate research (research)
- Shareable sprite personalities (planned)
- QR-based sharing format (planned)
- Multi-backend local inference (experimental direction)
- More expressive sprite states (planned)
- Local memory systems (planned / research)

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

The repository also includes `update.sh`, a single-developer helper script for local update, build, install, test, and publish workflows. It is optional and should be reviewed before use:

```bash
./update.sh
./update.sh publish -m "docs: update README"
```

## Documentation

- `docs/ui/LAMI_STANDARD_LAYOUT.md`: LAMI UI density, spacing, and inset guidance.
- `docs/qualcomm-qnn-npu-setup.md`: current QNN / NPU setup notes and limitations.

## Community

Issues, discussions, feature requests, and device reports are welcome.

Useful report topics include:

- Android device and OS version
- Ollama backend behavior
- LiteRT / MediaPipe local inference behavior
- TTS / ASR expectations
- QNN / NPU diagnostic results
- Sprite character and personality sharing ideas
- Device compatibility reports
- Edge AI experiment notes
- Bug reports and performance diagnostics
- Sprite and UX ideas

## Project Maturity

LAMI is currently an actively evolving experimental project. Architecture, local inference workflows, diagnostics, and character systems may change over time as Android Edge AI tooling and device behavior become clearer.

## Attribution

LAMI is its own Android project, but parts of the repository history and notices acknowledge prior Ollama Android client work. See `NOTICE` for the current attribution details. This README intentionally describes the current LAMI direction rather than presenting the app as an Ollama-only client.

## License

This project includes a `LICENSE` file containing the Apache License, Version 2.0.

See `NOTICE` for third-party attribution, including MIT-licensed upstream material referenced there.

<!--
Suggested GitHub Topics:
edge-ai, local-ai, local-llm, litert, mediapipe, android-ai,
on-device-ai, ai-assistant, sprite-animation, local-inference,
offline-ai, character-ai
-->
