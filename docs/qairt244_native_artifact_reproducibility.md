# QAIRT244 Native Artifact Reproducibility

This document records how the DEV-only qairt244 SM8750 NPU native artifact was produced and what must be checked before rebuilding or staging it. It is documentation only; it does not promote the route out of DEV-only scope.

## Current Artifact

```text
artifact=artifacts/litert_custom_build/20260524_114833_qairt244_16token
source_checkout=/home/sato/project/litert-custom-build/LiteRT-LM
source_head=c87189528a758db32ead241f4fc9c64836398ee7
source_describe=v0.11.0-dirty
qairt_root=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
android_ndk=/home/sato/Android/Sdk/ndk/28.2.13676358
bazel=bazelisk 7.6.1
bazel_output_base=/home/sato/project/litert-custom-build/bazel_output_base/build_20260524_114833
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
size=55190752
sha256=950bb727c25d60d14be2e147d263ca7ecdc6fe7bcfaeb78c848ce7609be50d9f
build_id=59a86c5209025c3b9b6ea3b22d2a39b8
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

The 16-token change is the native guard in that entrypoint:

```cpp
if (max_output_tokens < 1 || max_output_tokens > 16) {
  ... invalid_max_output_tokens ... native_max_output_tokens_limit=16 ...
}
...
DecodeConfig decode_config = DecodeConfig::CreateDefault();
decode_config.SetMaxOutputTokens(max_output_tokens);
```

The older 8-token staged artifact rejected `max_output_tokens=16` before RunDecode with `invalid_max_output_tokens value=16`. The current artifact changes that DEV-only editable-prompt path from an exact/smaller limit to the bounded range `1..16`, records `native_max_output_tokens_limit=16`, and then calls `RunDecode` with `SetMaxOutputTokens(max_output_tokens)`.

The checkout also has a `WORKSPACE` patch for LiteRT Qualcomm dispatch linkage. It inserts `dynamic_deps` into LiteRT build rules so `libLiteRtDispatch_Qualcomm.so` keeps a `DT_NEEDED` edge to `libLiteRt.so` when Android loads the dispatch library dynamically. Keep this patch with the qairt244 artifact build unless a later upstream LiteRT build no longer needs it.

## Rebuild Command

Use the lami-android build wrapper from this repository. The wrapper builds a limited target list only and does not copy outputs into app source sets.

```bash
cd /home/sato/project/lami-android
OUT_DIR=artifacts/litert_custom_build/<timestamp>_qairt244_16token \
BAZEL_OUTPUT_BASE=/home/sato/project/litert-custom-build/bazel_output_base/build_<timestamp> \
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_16token
```

The command observed in `build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log` for the JNI target was equivalent to:

```bash
/home/sato/.local/bin/bazelisk \
  --output_base=/home/sato/project/litert-custom-build/bazel_output_base/build_20260524_114833 \
  build \
  --repo_env=ANDROID_HOME=/home/sato/Android/Sdk \
  --repo_env=ANDROID_SDK_ROOT=/home/sato/Android/Sdk \
  --repo_env=ANDROID_NDK_HOME=/home/sato/Android/Sdk/ndk/28.2.13676358 \
  --repo_env=LITERT_QAIRT_SDK=/home/sato/project/lami-android/artifacts/litert_custom_build/20260524_114833_qairt244_16token/qairt_overlay/ \
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
  artifacts/litert_custom_build/<timestamp>_qairt244_16token
```

The staging script copies required libraries into:

```text
app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/
```

The runner currently uses this artifact path by default:

```text
CUSTOM_BUILD_ARTIFACT=artifacts/litert_custom_build/20260524_114833_qairt244_16token
```

If a new artifact is built, either pass it to the runner with `--artifact <path>` or deliberately update the runner default in a separate reviewed change. Do not modify the standard, release, or normal debug source sets.

## Reproducibility Checklist

Before using a rebuilt artifact for qairt244 SM8750 DEV-only runs, verify:

- Source checkout path is `/home/sato/project/litert-custom-build/LiteRT-LM`.
- `git -C /home/sato/project/litert-custom-build/LiteRT-LM status --short` is captured; expected current state is dirty because `WORKSPACE` and `litertlm.cc` carry qairt244 patches.
- `litertlm.cc` contains `kQairt244EditablePromptMarker` and `nativeRunEditablePrompt`.
- Native guard is exactly bounded to `max_output_tokens < 1 || max_output_tokens > 16`.
- Result writer records `native_max_output_tokens_limit=16`.
- Decode path logs `before RunDecode SetMaxOutputTokens(%d)` and calls `decode_config.SetMaxOutputTokens(max_output_tokens)`.
- Build command uses Android NDK `28.2.13676358` and QAIRT SDK `2.44.0.260225`.
- `build_results.tsv` has exit code `0` for all four targets.
- `metadata/liblitertlm_jni.so.txt` records size, SHA-256, build ID, and `NEEDED` dependencies.
- `strings/built_libs` or `strings/liblitertlm_jni.so.filtered.txt` includes qairt244 markers and `invalid_max_output_tokens` evidence.
- The staged app uses only `customBuildExperimentDebug/jniLibs/arm64-v8a`.
- `scripts/run_qairt244_chat_screen_real_npu_sm8750_model_run.sh --artifact <new-artifact> --run --prompt Hello` is run only in a separate execution-validation step, not during doc-only reproducibility review.

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
- The current artifact proves `1..16`; 32/64-token phases require another native guard change and a new artifact, not reuse of this one.
- QNN runtime library provenance is split between built LiteRT/dispatch libraries and existing device/app runtime dependencies; a release-quality packaging story is still unresolved.
