# QAIRT244 Native Artifact Reproducibility

## 2026-05-24 128 Output / 128 Input Hidden Template Artifact

The standard hidden qairt244 prompt-template comparison uses a new bounded
native artifact that keeps output generation capped at `max_output_tokens=128`
and raises only the editable prompt input guard to 128 UTF-8 code points for
`hidden_template_experiment` mode. This is still hidden experimental only: it
does not promote `Backend.NPU`, does not add fallback, and does not support
generic/E4B/qcs8275 models.

Active patch:
`patches/qairt244_litertlm_utf8_128token_128input.patch`

Historical patches retained:

- `patches/qairt244_litertlm_utf8_16token.patch`
- `patches/qairt244_litertlm_utf8_32token.patch`
- `patches/qairt244_litertlm_utf8_64token.patch`
- `patches/qairt244_litertlm_utf8_128token.patch`

Build command:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_128token_128input_utf8prompt
```

Artifact:
`artifacts/litert_custom_build/20260524_215218_qairt244_128token_128input_utf8prompt`

JNI build log:
`artifacts/litert_custom_build/20260524_215218_qairt244_128token_128input_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log`

`liblitertlm_jni.so` sha256:
`4065d88c4788eaf28be140e133b7141783cad0698061c942b6942fa1fa886c2e`

The native diagnostics for this phase must report
`max_output_tokens=128`, `native_max_output_tokens_limit=128`,
`native_prompt_input_code_point_limit=128`,
`native_prompt_input_limit_mode=hidden_template_experiment`,
`utf8_allowed=true`, and
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`.

## 2026-05-24 128-Token Bounded Artifact

The qairt244 UTF-8 internal prompt route has advanced from the 64-token bounded
artifact to a 128-token bounded artifact for customBuildExperimentDebug only.
This is still not `Backend.NPU` promotion and does not add fallback or normal UI
routing. The native guard must not allow values above `128`.

Active patch:
`patches/qairt244_litertlm_utf8_128token.patch`

Historical patches retained:

- `patches/qairt244_litertlm_utf8_16token.patch`
- `patches/qairt244_litertlm_utf8_32token.patch`
- `patches/qairt244_litertlm_utf8_64token.patch`

The four patches are phase snapshots over the same DEV-only editable-prompt
route and QAIRT dispatch-linkage changes: 16-token first enabled UTF-8 internal
intent, and 32/64/128-token phases raise only the bounded native token cap. The
UTF-8 prompt validation contract is unchanged: empty prompt, NUL, invalid
UTF-8, and prompts above 32 UTF-8 code points remain rejected; non-ASCII UTF-8
remains allowed only for the internal intent route.

External LiteRT-LM checkout:
`/home/sato/project/litert-custom-build/LiteRT-LM`

Upstream HEAD:
`c87189528a758db32ead241f4fc9c64836398ee7`

Build command:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_128token_utf8prompt
```

Artifact:
`artifacts/litert_custom_build/20260524_170102_qairt244_128token_utf8prompt`

JNI build log:
`artifacts/litert_custom_build/20260524_170102_qairt244_128token_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log`

`liblitertlm_jni.so` sha256:
`c3c04b274e3b090b1962b9e32b3f9467f2bad66707676e86463be939d37396f6`

The native diagnostics for this phase must report
`max_output_tokens=128`, `native_max_output_tokens_limit=128`,
`native_prompt_validation_mode=utf8_internal_intent`, `utf8_allowed=true`, and
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`.

The 128-token artifact has bounded Phase A stability evidence in
`docs/qairt244_chat_screen_real_npu_sm8750_model_run.md`: Japanese
`--prompt-mode internal_intent` prompts `こんにちは`, `テスト`, and `ラミィ`
completed 3/3 with requested/actual/normalized equality,
`max_output_tokens=128`, `native_max_output_tokens_limit=128`,
`prompt_validation_mode=utf8_internal_intent`,
`native_prompt_validation_mode=utf8_internal_intent`, `utf8_allowed=true`,
RunDecode reached, `npu_backend=NPU`,
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`, no fallback, no
timeout, no fresh crash, no observed `duplicate_run_blocked`, successful UI
cleanup, and `decode_elapsed_ms` range `40..3152`. The `40 ms` lower-bound
decode timing is stability evidence for a completed bounded DEV run, not a
formal generation speed value. This records only the 128-token DEV-only bounded
experiment; it is not production or normal-route NPU enablement.

## 2026-05-24 64-Token Bounded Artifact

The qairt244 UTF-8 internal prompt route has advanced from the 32-token bounded
artifact to a 64-token bounded artifact for customBuildExperimentDebug only.
This is still not `Backend.NPU` promotion and does not add fallback or normal UI
routing. The native guard must not allow values above `64`.

Active patch:
`patches/qairt244_litertlm_utf8_64token.patch`

Historical patches retained:

- `patches/qairt244_litertlm_utf8_16token.patch`
- `patches/qairt244_litertlm_utf8_32token.patch`

The three patches are phase snapshots over the same DEV-only editable-prompt
route and QAIRT dispatch-linkage changes: 16-token first enabled UTF-8 internal
intent, 32-token raised only the bounded native token cap, and 64-token raises
only that cap again. The UTF-8 prompt validation contract is unchanged: empty
prompt, NUL, invalid UTF-8, and prompts above 32 UTF-8 code points remain
rejected; non-ASCII UTF-8 remains allowed only for the internal intent route.

External LiteRT-LM checkout:
`/home/sato/project/litert-custom-build/LiteRT-LM`

Upstream HEAD:
`c87189528a758db32ead241f4fc9c64836398ee7`

Build command:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_64token_utf8prompt
```

Artifact:
`artifacts/litert_custom_build/20260524_162218_qairt244_64token_utf8prompt`

JNI build log:
`artifacts/litert_custom_build/20260524_162218_qairt244_64token_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log`

`liblitertlm_jni.so` sha256:
`cd85bd4979cac7325148d8ad72bc0ee69cbf684d9f7e9373fab07844b5110ad6`

The native diagnostics for this phase must report
`max_output_tokens=64`, `native_max_output_tokens_limit=64`,
`native_prompt_validation_mode=utf8_internal_intent`, `utf8_allowed=true`, and
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`.

The 64-token artifact has bounded Phase A evidence in
`docs/qairt244_chat_screen_real_npu_sm8750_model_run.md`: Japanese
`--prompt-mode internal_intent` prompts `こんにちは`, `テスト`, and `ラミィ`
completed 3/3 with prompt requested/actual/normalized equality,
`max_output_tokens=64`, `native_max_output_tokens_limit=64`,
`prompt_validation_mode=utf8_internal_intent`,
`native_prompt_validation_mode=utf8_internal_intent`, `utf8_allowed=true`,
RunDecode reached, `npu_backend=NPU`,
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`, no fallback, no
timeout, no fresh crash, no observed `duplicate_run_blocked`, successful UI
cleanup, and `decode_elapsed_ms` range `40..1959`. This records only the
historical 64-token DEV-only bounded experiment; the later 128-token phase is
recorded above.

## 2026-05-24 32-Token Bounded Artifact

The qairt244 UTF-8 internal prompt route has advanced from the 16-token bounded
artifact to a 32-token bounded artifact for customBuildExperimentDebug only.
This is still not `Backend.NPU` promotion and does not add fallback or normal UI
routing.

Active patch:
`patches/qairt244_litertlm_utf8_32token.patch`

Historical 16-token patch retained:
`patches/qairt244_litertlm_utf8_16token.patch`

External LiteRT-LM checkout:
`/home/sato/project/litert-custom-build/LiteRT-LM`

Upstream HEAD:
`c87189528a758db32ead241f4fc9c64836398ee7`

Build command:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_32token_utf8prompt
```

Artifact:
`artifacts/litert_custom_build/20260524_155121_qairt244_32token_utf8prompt`

JNI build log:
`artifacts/litert_custom_build/20260524_155121_qairt244_32token_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log`

`liblitertlm_jni.so` sha256:
`409008af863322e43ac35ffedec39bba64a8a9bd8a4859723fbf40e277dd3781`

The native diagnostics for this phase must report
`max_output_tokens=32`, `native_max_output_tokens_limit=32`,
`native_prompt_validation_mode=utf8_internal_intent`, `utf8_allowed=true`, and
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`.

The 32-token artifact has bounded Phase A run evidence in
`docs/qairt244_chat_screen_real_npu_sm8750_model_run.md`: Japanese
`--prompt-mode internal_intent` prompts `こんにちは`, `テスト`, and `ラミィ`
completed 3/3 with prompt requested/actual/normalized equality, RunDecode
reached, `npu_backend=NPU`, no fallback, no timeout, no fresh crash, no observed
`duplicate_run_blocked`, and successful UI cleanup. This records only the
32-token DEV-only phase. The later 64-token phase requires
`patches/qairt244_litertlm_utf8_64token.patch` and its own rebuilt artifact.


## 2026-05-24 Patch Management

The qairt244 UTF-8 internal prompt native change is captured as phase-specific
patch snapshots:

```text
patches/qairt244_litertlm_utf8_16token.patch
patches/qairt244_litertlm_utf8_32token.patch
patches/qairt244_litertlm_utf8_64token.patch
patches/qairt244_litertlm_utf8_128token.patch
patches/qairt244_litertlm_utf8_128token_128input.patch
```

The patch is based on external LiteRT-LM checkout
`/home/sato/project/litert-custom-build/LiteRT-LM` at upstream HEAD
`c87189528a758db32ead241f4fc9c64836398ee7`. The current checkout is already in
the patch-applied dirty state for
`kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`.

Use the helper to classify the checkout without resetting, building, or editing
the native tree:

```bash
scripts/check_qairt244_native_patch.sh
```

Expected current result for the no-argument helper is the active 128 output /
128 input patch: `status=applied`. Historical patches can still be checked by
passing the patch path as the second argument. The equivalent manual check for
the active phase is:

```bash
git -C /home/sato/project/litert-custom-build/LiteRT-LM apply --check \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_128token_128input.patch

git -C /home/sato/project/litert-custom-build/LiteRT-LM apply --reverse --check \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_128token_128input.patch
```

For the current dirty checkout, the forward check fails because the patch is
already present, and the reverse check succeeds. A clean checkout can apply the
patch with:

```bash
git -C /home/sato/project/litert-custom-build/LiteRT-LM apply \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_128token_128input.patch
```

Rebuild command for the 16-token phase:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_16token_utf8prompt
```

Known reproduced artifact:
`artifacts/litert_custom_build/20260524_144803_qairt244_16token_utf8prompt`.
Known `liblitertlm_jni.so` sha256:
`51e9a54c7ec32daabba7a6521ed378b8ebad72c4dfcd4597d6f4b0360e3ac947`.

Record future rebuilds by storing the artifact path, native build log path, and
`sha256sum` output in this document or the run-specific docs. Do not add `.so`,
`.apk`, `.aar`, `.zip`, `.tar`, `.gz`, or `.litertlm` files to Git.

Patch management is recommended while this remains a small DEV-only experiment.
Move to a fork pin when the native change needs native code review, reuse across
machines, or promotion into a later token phase.


This document records how the DEV-only qairt244 SM8750 NPU native artifact was produced and what must be checked before rebuilding or staging it. It is documentation only; it does not promote the route out of DEV-only scope.

## Historical 16-Token Artifact

```text
artifact=artifacts/litert_custom_build/20260524_144803_qairt244_16token_utf8prompt
source_checkout=/home/sato/project/litert-custom-build/LiteRT-LM
source_head=c87189528a758db32ead241f4fc9c64836398ee7
source_describe=v0.11.0-dirty
qairt_root=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
android_ndk=/home/sato/Android/Sdk/ndk/28.2.13676358
bazel=bazelisk 7.6.1
bazel_output_base=/home/sato/project/litert-custom-build/bazel_output_base/build_20260524_144803
```

The artifact contains these built libraries under `built_libs/`:

```text
libLiteRt.so
libLiteRtDispatch_Qualcomm.so
libLiteRtCompilerPlugin_Qualcomm.so
liblitertlm_jni.so
```

`liblitertlm_jni.so` metadata from the artifact:

```text
size=55191176
sha256=51e9a54c7ec32daabba7a6521ed378b8ebad72c4dfcd4597d6f4b0360e3ac947
build_id=de60ba02d97664b4839da645162cdaa4
needed=libGemmaModelConstraintProvider.so,libdl.so,liblog.so,libandroid.so,libGLESv3.so,libEGL.so,libm.so,libc.so
```

Build result targets in the artifact all exited with `0`:

```text
@litert//litert/c:litert_runtime_c_api_so
@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so
//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni
@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so
```

## Source Changes Required

The generated artifact came from a dirty LiteRT-LM checkout. The relevant source path is:

```text
/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc
```

The qairt244 editable-prompt JNI entrypoint is:

```text
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt
```

The active 128-token change uses this native guard in that entrypoint:

```cpp
if (max_output_tokens < 1 || max_output_tokens > 128) {
  ... invalid_max_output_tokens ... native_max_output_tokens_limit=128 ...
}
...
DecodeConfig decode_config = DecodeConfig::CreateDefault();
decode_config.SetMaxOutputTokens(max_output_tokens);
```

The older 8-token staged artifact rejected `max_output_tokens=16` before RunDecode with `invalid_max_output_tokens value=16`. The active 128-token artifact keeps that DEV-only editable-prompt path bounded to `1..128`, records `native_max_output_tokens_limit=128`, and then calls `RunDecode` with `SetMaxOutputTokens(max_output_tokens)`.

The UTF-8 prompt update is limited to the DEV-only editable-prompt validator. It trims ASCII spaces, rejects empty prompts, rejects NUL, rejects invalid UTF-8, rejects prompts above 32 UTF-8 code points, and no longer rejects non-ASCII UTF-8 solely because bytes are above ASCII. The result file records `native_prompt_validation_mode=utf8_internal_intent` and `utf8_allowed=true`; diagnostic logs include the same mode on entry and prompt validation. This does not change `max_output_tokens`, the UI text runner, normal ChatScreen routing, fallback behavior, or production `Backend.NPU` wiring.

The checkout also has a `WORKSPACE` patch for LiteRT Qualcomm dispatch linkage. It inserts `dynamic_deps` into LiteRT build rules so `libLiteRtDispatch_Qualcomm.so` keeps a `DT_NEEDED` edge to `libLiteRt.so` when Android loads the dispatch library dynamically. Keep this patch with the qairt244 artifact build unless a later upstream LiteRT build no longer needs it.

## Rebuild Command

Use the lami-android build wrapper from this repository. The wrapper builds a limited target list only and does not copy outputs into app source sets.

```bash
cd /home/sato/project/lami-android
OUT_DIR=artifacts/litert_custom_build/<timestamp>_qairt244_64token_utf8prompt \
BAZEL_OUTPUT_BASE=/home/sato/project/litert-custom-build/bazel_output_base/build_<timestamp> \
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_64token_utf8prompt
```

The command observed in `build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log` for the JNI target was equivalent to:

```bash
/home/sato/.local/bin/bazelisk \
  --output_base=/home/sato/project/litert-custom-build/bazel_output_base/build_20260524_144803 \
  build \
  --repo_env=ANDROID_HOME=/home/sato/Android/Sdk \
  --repo_env=ANDROID_SDK_ROOT=/home/sato/Android/Sdk \
  --repo_env=ANDROID_NDK_HOME=/home/sato/Android/Sdk/ndk/28.2.13676358 \
  --repo_env=LITERT_QAIRT_SDK=/home/sato/project/lami-android/artifacts/litert_custom_build/20260524_144803_qairt244_16token_utf8prompt/qairt_overlay/ \
  --repo_env=HERMETIC_PYTHON_VERSION=3.12 \
  //kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni \
  --config=android_arm64
```

The wrapper also builds LiteRT C runtime, Qualcomm dispatch, and Qualcomm compiler plugin targets.

## Staging For The DEV Variant

After rebuilding, stage only into `customBuildExperimentDebug`:

```bash
cd /home/sato/project/lami-android
scripts/stage_litert_custom_build_stack_for_experiment.sh \
  artifacts/litert_custom_build/<timestamp>_qairt244_64token_utf8prompt
```

The staging script copies required libraries into:

```text
app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/
```

The runner currently uses this artifact path by default:

```text
CUSTOM_BUILD_ARTIFACT=artifacts/litert_custom_build/20260524_162218_qairt244_64token_utf8prompt
```

If a new artifact is built, either pass it to the runner with `--artifact <path>` or deliberately update the runner default in a separate reviewed change. Do not modify the standard, release, or normal debug source sets.

## Reproducibility Checklist

Before using a rebuilt artifact for qairt244 SM8750 DEV-only runs, verify:

- Source checkout path is `/home/sato/project/litert-custom-build/LiteRT-LM`.
- `git -C /home/sato/project/litert-custom-build/LiteRT-LM status --short` is captured; expected current state is dirty because `WORKSPACE` and `litertlm.cc` carry qairt244 patches.
- `litertlm.cc` contains `kQairt244EditablePromptMarker` and `nativeRunEditablePrompt`.
- Native guard is exactly bounded to `max_output_tokens < 1 || max_output_tokens > 64`.
- Result writer records `native_max_output_tokens_limit=64`.
- Result writer records `native_prompt_validation_mode=utf8_internal_intent` and `utf8_allowed=true`.
- Validator rejects empty prompt, NUL, invalid UTF-8, and prompts above 32 UTF-8 code points while allowing non-ASCII UTF-8 for the DEV internal prompt route.
- Decode path logs `before RunDecode SetMaxOutputTokens(%d)` and calls `decode_config.SetMaxOutputTokens(max_output_tokens)`.
- Build command uses Android NDK `28.2.13676358` and QAIRT SDK `2.44.0.260225`.
- `build_results.tsv` has exit code `0` for all four targets.
- `metadata/liblitertlm_jni.so.txt` records size, SHA-256, build ID, and `NEEDED` dependencies.
- `strings/built_libs` or `strings/liblitertlm_jni.so.filtered.txt` includes qairt244 markers, `invalid_max_output_tokens`, `native_prompt_validation_mode`, and `utf8_allowed` evidence.
- The staged app uses only `customBuildExperimentDebug/jniLibs/arm64-v8a`.
- `scripts/run_qairt244_chat_screen_real_npu_sm8750_model_run.sh --artifact <new-artifact> --run --prompt Hello` is run only in a separate execution-validation step, not during doc-only reproducibility review.

## UTF-8 Prompt Artifact Record

```text
artifact=artifacts/litert_custom_build/20260524_162218_qairt244_64token_utf8prompt
liblitertlm_jni_sha256=cd85bd4979cac7325148d8ad72bc0ee69cbf684d9f7e9373fab07844b5110ad6
native_build_log=artifacts/litert_custom_build/20260524_162218_qairt244_64token_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log
build_results=all four targets exited 0
validation_mode=utf8_internal_intent
utf8_allowed=true
max_output_tokens_range=1..64
```

This artifact is local evidence only. Do not stage `.so` files; pass the artifact explicitly to DEV-only runners or staging scripts when native validation of internal UTF-8 prompts is required.

## Git Safety

Never add these artifact or runtime file types to Git:

```text
*.so
*.litertlm
*.apk
*.aar
*.zip
*.tar
*.gz
```

Allowed Git changes for this reproducibility work are docs and, in a separate reviewed task, scripts that refer to an artifact path. Artifact directories under `artifacts/litert_custom_build/` are local evidence, not source. Staged native libraries in `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/` must also stay untracked.

Before committing documentation, run:

```bash
git diff --check
git diff --cached --name-only | grep -E '\.(litertlm|so|apk|aar|zip|tar|gz)$' && exit 1 || true
```

## Open Items

- The qairt244 native changes are currently local dirty changes in the external LiteRT-LM checkout, not a pinned patch file inside this repo.
- Rebuild reproducibility should be improved by exporting the `litertlm.cc` and `WORKSPACE` diffs into a small patch file or documented upstream fork reference.
- The current artifact proves `1..64`; any later higher-token phase requires another native guard change and a new artifact, not reuse of this one.
- QNN runtime library provenance is split between built LiteRT/dispatch libraries and existing device/app runtime dependencies; a release-quality packaging story is still unresolved.
