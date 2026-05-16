# Google AI Edge issue posting checklist

Date: 2026-05-16

## Recommended target

Main issue repo:

- `google-ai-edge/LiteRT-LM`

Reason:

- The public API under test is LiteRT-LM Android: `Backend.NPU(nativeLibraryDir)`, `EngineConfig`, `Engine`, and `Engine.initialize()`.
- The user-visible failure is an `Engine.initialize()` process abort.
- LiteRT-LM currently has adjacent Android / Gemma 4 / QNN / SM8750 issues, including QNN version mismatch and Gemma 4 device reports.
- The requested maintainer answer is about the supported LiteRT-LM Android artifact/native stack combination.

Cross-link candidates:

- `google-ai-edge/LiteRT`
  - Use if maintainers confirm the root cause is LiteRT dispatch runtime compatibility, dispatch API capabilities, or Qualcomm runtime distribution.
- `google-ai-edge/gallery`
  - Use only as a secondary reference because the native stack source is Gallery SM8750 APK. The failing API path is not Gallery UI behavior.

Do not open three duplicate issues initially. Open one detailed LiteRT-LM issue, then cross-link or move only if maintainers ask.

## Title candidates

1. `[Android][SM8750][Backend.NPU] Engine.initialize SIGABRT: No usable Dispatch runtime found with Gallery native stack`
2. `[LiteRT-LM][Android][Qualcomm SM8750] Backend.NPU Engine.initialize aborts despite mapped Gallery dispatch runtime`
3. `[Android][LiteRT-LM][QNN] Engine.initialize SIGABRT on SM8750 with libLiteRtDispatch_Qualcomm.so loaded`
4. `[SM8750][Gemma 4 E2B Qualcomm] Backend.NPU(nativeLibraryDir) fails in Engine.initialize with no usable dispatch runtime`
5. `[LiteRT-LM Android] Need supported Qualcomm dispatch runtime/artifact guidance for SM8750 Backend.NPU`

Recommended title:

`[Android][SM8750][Backend.NPU] Engine.initialize SIGABRT: No usable Dispatch runtime found with Gallery native stack`

## Labels

Use labels only if available in the target repo. Do not invent labels if GitHub UI does not offer them.

`google-ai-edge/LiteRT-LM` candidates:

- `type:bug`
- `type:support`
- `Android`
- `LiteRT-LM`
- `Backend.NPU`
- `Qualcomm`
- `QNN`
- `SM8750`
- `dispatch-runtime`
- `crash`

`google-ai-edge/LiteRT` candidates:

- `type:bug`
- `type:support`
- `Android`
- `LiteRT`
- `NPU`
- `Qualcomm`
- `QNN`
- `dispatch`
- `SM8750`

`google-ai-edge/gallery` candidates:

- `type:bug`
- `type:support`
- `Android`
- `Gallery`
- `Gemma 4`
- `Qualcomm`
- `SM8750`

## Attachments

Must attach or paste:

- `docs/google_ai_edge_issue_body_litertlm_sm8750_npu.md`
- `docs/google_ai_edge_issue_short_summary.md`
- `artifacts/npu_issue_bundle/<timestamp>_light.zip`

Must include in the issue body:

- device / SoC / Android SDK
- model filename and size
- LiteRT-LM dependency split
- native library Build IDs
- Java/native descriptor mismatch was fixed
- current `SIGABRT` frame and register-fragment error
- exact maintainer questions

Optional attach:

- full `crash_summary.md`
- `loaded_libs_matrix.tsv`
- `abort_text_candidates.txt`
- `requirements_summary.md`
- `litertlm_gallery_java_api_surface_mismatch.md`

Too large for initial GitHub issue:

- full `artifacts/npu_issue_bundle/20260516_212934.zip` at about 193MB
- complete untrimmed `logcat_all_tail.txt`
- full tombstone/dropbox dumps if GitHub rejects size
- full APKs
- native `.so` binaries
- QNN libraries

Maybe gist/paste:

- top 40 backtrace
- focused logcat extract
- static strings excerpts around `LiteRtDispatchCheckRuntimeCompatibility`, `No usable Dispatch runtime found`, `ADSP_LIBRARY_PATH`, and QNN version messages

Maybe external upload only if requested:

- full tombstone
- full dropbox dump
- full artifact bundle
- APK-derived native library metadata

## Privacy and redaction

Mask or review:

- local paths under `/home/sato`
- unnecessary absolute workstation paths
- unrelated logcat lines
- device serial numbers if present
- network IPs if not relevant

Keep:

- Android app-private model path
- package names
- Build IDs
- model filename
- SoC/device properties
- tombstone frame offsets

## Posting notes

Before posting:

- Use the issue body file, not the longer Japanese/English report file.
- Put the recommended title in the GitHub title field.
- Attach the light bundle first.
- Mention that the full 193MB bundle is available if maintainers need it.
- Do not attach native `.so` files unless maintainers explicitly request them and licensing permits.

Expected maintainer follow-up questions:

- exact model source/download channel
- exact Gallery APK source
- whether Gallery itself runs the same model on the same device
- whether app nativeLibraryDir contains the QNN skel/stub files
- whether ADSP path is configured by Gallery but not the test app
- whether the dispatch runtime is intended for third-party apps
- whether the Maven artifact version matches the Gallery native stack
- whether the crash reproduces with a minimal official sample

## Questions to ask before custom build

Ask maintainers:

- Which source tag/commit matches `liblitertlm_jni.so` Build ID `76e4dccd9c5f9cba468d9cae7becfec0`?
- Which source tag/commit matches `libLiteRt.so` Build ID `869121bd7f4b0b77fa581218117a5c14`?
- Which source tag/commit and QNN SDK version should build `libLiteRtDispatch_Qualcomm.so` Build ID-compatible output?
- Is `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so` sufficient for LiteRT-LM Android, or must `liblitertlm_jni.so` / `libLiteRt.so` / dispatch be built as one matched stack?
- Are Android app environment settings required for `libQnnHtpV79Stub.so` / `libQnnHtpV79Skel.so` discovery?

## Final safety check

Do not mention any NPU generation result. None was run.

The experiment stops at explicit `Engine.initialize()` dry-run. `Conversation`, `Session`, and `generateResponse` were not called.
