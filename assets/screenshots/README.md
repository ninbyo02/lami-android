# README Screenshots

This directory contains screenshots used by the project README files.

The images should show the real LAMI Android app UI. Avoid marketing mockups, old Ollama Client screenshots, or screenshots that imply unfinished features are stable.

## Current Files

- `hero-chat.jpg` - Chat UI with the LAMI sprite and a short conversation.
- `sprite-state.jpg` - Sprite state / animation settings.
- `sprite-editor.jpg` - Sprite character editor.
- `streaming-tts.jpg` - Responding / talking state used for streaming and TTS direction.
- `local-inference-stats.jpg` - Local inference statistics sheet.
- `litert-experimental.jpg` - Experimental local inference flow.

## Capture Notes

Recommended capture size is a portrait Android screenshot around 720 to 1440 px wide. PNG is preferred when available, but device JPEG screenshots are acceptable when they are the original captured output.

Example:

```bash
adb exec-out screencap -p > assets/screenshots/hero-chat.png
```

If Codex or Termux is running on the same Android device, `adb screencap` may capture the terminal instead of LAMI. In that case, use the device screenshot shortcut and then move the image into this directory.

## Update Checklist

- Use current LAMI Android UI only.
- Do not use legacy Ollama Client screenshots.
- Do not add broken README image links.
- Do not imply QNN / NPU acceleration is complete.
- Do not imply fully offline workflows are complete.
- Keep captions factual and mark experimental areas clearly.
