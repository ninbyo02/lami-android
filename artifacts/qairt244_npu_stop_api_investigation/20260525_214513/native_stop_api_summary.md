# QAIRT244 NPU Stop API Investigation

Date: 2026-05-25

Scope: read-only static investigation of the external LiteRT-LM and LiteRT
trees. No native source, model file, app JNI library, or standard route code was
changed.

## Inputs

- LiteRT-LM: `/home/sato/project/litert-custom-build/LiteRT-LM`
- LiteRT: `/home/sato/project/litert-custom-build/LiteRT`
- Primary JNI file:
  `/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`
- Raw search artifact: `native_stop_api_rg.txt`

## Findings

- Public Android/JNI decode control does not expose a per-run stop sequence,
  stop token, EOS token, or `<end_of_turn>` setter.
- `DecodeConfig` exposes `SetConstraint()` and `SetMaxOutputTokens()`, but no
  stop sequence or sampler controls.
- The Android public `nativeRunDecode(sessionPointer)` path takes only the
  session pointer and calls decode with default config.
- The qairt244 editable native entrypoints create default session config and
  set only `DecodeConfig.SetMaxOutputTokens(...)` before `RunDecode(...)`.
- Stop tokens exist internally through model/session metadata and token ID
  lists, but this is not a clean Android-exposed per-run API.
- Stop detection can consume token ID stop sequences internally once available,
  but exposing `<end_of_turn>` safely would require additional native-only
  token string or token ID mapping.
- Kotlin `SamplerConfig` exposes topK, topP, temperature, and seed at session
  creation. The inspected qairt244 lower-level editable native path does not
  expose these as per-run comparison controls.
- No repetition penalty API was found in the inspected LiteRT-LM runtime,
  Kotlin, JNI, or sampler proto paths.
- LiteRT core search found profiler stop APIs only; those are unrelated to LLM
  stop sequence behavior.

## Decision

Do not implement native stop sequence / stop token comparison now.

`sanitizer_only + max_output_tokens=128` remains the hidden experimental
display-quality baseline. Native stop sequence or native turn-stop should be
reconsidered only if a public/static Android or qairt244 JNI API is exposed for
per-run stop sequence, stop token, EOS, or `<end_of_turn>` control without
changing normal UI, standard route, model selection, or DB/TTS/Markdown/
streaming boundaries.

## Runner Impact

No additional NPU run is required for this investigation. The existing
turn-stop quality runner should continue to record `stop_sequence_end_of_turn`
as `not_run/native_stop_not_exposed`.
