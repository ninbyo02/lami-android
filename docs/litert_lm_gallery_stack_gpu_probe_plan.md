# LiteRT-LM galleryStackGpuProbe plan

## Purpose

`galleryStackGpuProbe` is a DEV-only isolation flavor for checking whether LAMI can run generic `gemma-4-E2B-it.litertlm` on GPU when the model, runtime stack, and API/lifecycle conditions are aligned with Google AI Edge Gallery.

This flavor does not change `standardDebug`, CPU routing, NPU S1, fallback, or production defaults.

## Non-goals

- Do not replace individual `.so` files in `standardDebug`.
- Do not copy Edge Gallery runtime files into production.
- Do not enable GPU silently for normal users.
- Do not change CPU held-official-flow.
- Do not change NPU S1 / SM8750 native route behavior.
- Do not change fallback, DB, TTS, Markdown, or streaming behavior.

## Proposed Flavor

Name:

- `galleryStackGpuProbe`

Application id:

- Base: `io.github.ninbyo02.lami`
- Suffix: `.gallerygpuprobe`
- Result example: `io.github.ninbyo02.lami.gallerygpuprobe`

Install behavior:

- Separate APK and separate app data from `standardDebug`.
- Separate model directory.
- Separate cache directory.
- Explicit developer install target only.
- Gradle task: `./gradlew :app:installGalleryStackGpuProbeDebug`
- Build task: `./gradlew :app:assembleGalleryStackGpuProbeDebug`

Native library handling:

- Use a dedicated source set such as `app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/`.
- Treat `libLiteRt.so`, `liblitertlm_jni.so`, LiteRT dispatch/plugin libraries, model constraint provider, and related dependencies as a matched set.
- Never test a single `libLiteRt.so` or `liblitertlm_jni.so` replacement in the standard flavor.
- Stage libraries with `scripts/stage_gallery_stack_gpu_probe_native_libs.sh`.
- Default script mode is report-only. Use `--stage` to copy.
- Generated manifest: `artifacts/gallery_stack_gpu_probe/native_lib_manifest.tsv`.
- Staged `.so` files are intentionally gitignored.

```bash
scripts/stage_gallery_stack_gpu_probe_native_libs.sh
scripts/stage_gallery_stack_gpu_probe_native_libs.sh --stage
```

The script refuses to stage into `app/src/main`, `app/src/debug`, `app/src/standard`, or `app/src/standardDebug`.

## Phase 7 Implementation

Implemented pieces:

- Product flavor `galleryStackGpuProbe`.
- `applicationIdSuffix=".gallerystackgpu"`.
- `versionNameSuffix="-galleryStackGpuProbe"`.
- Debug-only variant; release variant disabled.
- Native lib source dir: `app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/`.
- DEV opt-in property: `debug.lami.gallery_stack_gpu_probe=true`.
- Probe diagnostics in compact diagnostics, `LOCAL_ROUTE_DIAG`, and developer inference stats.

The flavor remains safe when the property is false. It does not force GPU, and it does not enable thinking/speculative decoding.

## Model Policy

Preferred manual model path:

```text
/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm
```

Expected Edge Gallery model identity:

- `modelId=litert-community/gemma-4-E2B-it-litert-lm`
- `commitHash=6e5c4f1e395deb959c494953478fa5cec4b8008f`
- `sizeInBytes=2588147712`
- `accelerators=gpu,cpu`
- `visionAccelerator=gpu`
- `topK=64`
- `topP=0.95`
- `temperature=1.0`
- `maxTokens=4000`
- `maxContextLength=32000`
- `capabilities=llm_thinking,speculative_decoding`

LAMI does not bundle this model in git.

On-device diagnostics include model path, existence, and size. SHA-256 is not calculated on the UI path; use script-side hashing for large model files.

## Diagnostics

Added keys:

- `gallery_stack_probe_flavor`
- `gallery_stack_probe_enabled`
- `gallery_stack_probe_application_id`
- `gallery_stack_probe_native_stack_source`
- `gallery_stack_probe_liblitert_sha256`
- `gallery_stack_probe_liblitertlm_jni_sha256`
- `gallery_stack_probe_libs_manifest_present`
- `gallery_stack_probe_edge_gallery_model_expected`
- `gallery_stack_probe_model_path`
- `gallery_stack_probe_model_exists`
- `gallery_stack_probe_model_size_bytes`
- `gallery_stack_probe_model_sha256_if_available`
- `gallery_stack_probe_allowlist_config_applied`
- `gallery_stack_probe_runtime_stack_alignment_level`
- `gallery_stack_probe_thinking_api_available`
- `gallery_stack_probe_speculative_decoding_api_available`
- `gallery_stack_probe_allowlist_accelerators`
- `gallery_stack_probe_allowlist_vision_accelerator`
- `gallery_stack_probe_allowlist_top_k`
- `gallery_stack_probe_allowlist_top_p`
- `gallery_stack_probe_allowlist_temperature`
- `gallery_stack_probe_allowlist_max_tokens`
- `gallery_stack_probe_allowlist_max_context_length`

Alignment levels:

- `none`: neither expected model size nor Edge Gallery native stack SHA pair is observed.
- `model_only`: expected model size is observed, native stack is not aligned.
- `native_stack_staged`: Edge Gallery `libLiteRt.so` and `liblitertlm_jni.so` SHA pair is observed, expected model size is not.
- `native_stack_and_model`: both expected model size and Edge Gallery native SHA pair are observed.
- `unknown`: non-probe flavor or unavailable state.

Allowlist config is applied only when all are true:

- Flavor is `galleryStackGpuProbe`.
- Build is debug.
- `debug.lami.gallery_stack_gpu_probe=true`.
- Selected backend is GPU.

Current public API support:

- `gallery_stack_probe_thinking_api_available=false`
- `gallery_stack_probe_speculative_decoding_api_available=false`

These are not faked even though the Edge Gallery allowlist advertises those capabilities.

## Test Flow

Build/install:

```bash
./gradlew :app:assembleGalleryStackGpuProbeDebug
./gradlew :app:installGalleryStackGpuProbeDebug
```

Set DEV properties:

```bash
adb shell setprop debug.lami.gallery_stack_gpu_probe true
adb shell setprop debug.lami.compare_cpu_gpu_callback true
adb shell setprop debug.lami.gpu_generate_probe_mode raw_callback_only
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
```

Use `callback_to_ui` when verifying that raw GPU callbacks can be promoted into the visible assistant message. Use `normal_callback_streaming` when testing the candidate normal GPU streaming path:

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal_callback_streaming
```

Expected `normal_callback_streaming` success keys:

- `status=success`
- `debug_lami_gpu_generate_probe_mode=normal_callback_streaming`
- `gpu_callback_to_ui_enabled=true`
- `gpu_callback_text_promoted_to_ui=true`
- `gpu_ui_append_finished=true`
- `gpu_streaming_completion_reason=flow_completed_non_empty_response`
- `failure_stage=none`

For the guarded normal GPU route candidate, keep the probe mode as `normal` and enable the callback streaming gate:

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
```

Expected guarded normal route keys:

- `status=success`
- `debug_lami_gpu_generate_probe_mode=normal`
- `gpu_normal_route_use_callback_streaming=true`
- `gpu_callback_streaming_path_selected=true`
- `gpu_callback_streaming_path_reason=dev_gate_normal_route`
- `gpu_callback_streaming_success_count=1`
- `gpu_callback_streaming_completion_reason=flow_completed_non_empty_response`
- `gpu_callback_streaming_failure_reason=none`
- `gpu_callback_text_promoted_to_ui=true`
- `gpu_ui_append_finished=true`
- `failure_stage=none`

## StandardDebug Edge Gallery E2B Model Probe

After `galleryStackGpuProbe` succeeds, test whether the same model works in `standardDebug` with the existing standard runtime stack:

```bash
scripts/stage_edge_gallery_e2b_model_to_lami_standard.sh --dry-run
scripts/stage_edge_gallery_e2b_model_to_lami_standard.sh
./gradlew :app:installStandardDebug
adb shell monkey -p io.github.ninbyo02.lami 1
```

Select or import:

```text
/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm
```

Then set:

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
```

Expected model identity keys:

- `standard_gpu_probe_expected_edge_gallery_e2b=true`
- `standard_gpu_probe_model_size_bytes=2588147712`
- `standard_gpu_probe_model_sha256_expected=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
- `standard_gpu_probe_model_sha256_actual=device_unavailable`
- `standard_gpu_probe_runtime_stack=standardDebug`
- `standard_gpu_probe_callback_streaming_gate=true`
- `standard_gpu_probe_result_candidate=success` or `failure`

Interpretation:

- `success`: model identity was likely the main blocker for standardDebug GPU.
- `failure` with `cc:735`: runtime/native stack difference remains likely.
- success only in `galleryStackGpuProbe`: keep isolated runtime stack promotion separate from standardDebug.

The latest standardDebug probe uses the exact Edge Gallery E2B model and still fails with `runtime/executor/llm_litert_compiled_model_executor.cc:735`, while `galleryStackGpuProbe` succeeds with the same model and guarded callback streaming. That makes model identity a lower-priority explanation. The active blocker is now the runtime/native stack delta between the two APKs.

Compare the final packaged native stacks with:

```bash
./gradlew :app:assembleStandardDebug
./gradlew :app:assembleGalleryStackGpuProbeDebug
scripts/compare_standard_gallery_stack_gpu_probe_native_libs.sh
```

The script writes only diagnostic text files under:

```text
artifacts/gpu_runtime_stack_compare/
```

Primary outputs:

- `standard_debug_native_libs.tsv`
- `gallery_stack_gpu_probe_native_libs.tsv`
- `native_lib_diff.tsv`
- `needed_dependency_edges.tsv`
- `gpu_runtime_stack_classification.md`

Latest comparison result:

- `29` arm64 libraries compared.
- `9` same-name SHA-256 differences.
- `3` presence differences.
- `5` high-priority runtime candidates.
- Highest-priority matched-stack candidates: `libLiteRt.so` and `liblitertlm_jni.so`.
- Related review candidates: `libLiteRtDispatch_Qualcomm.so`, `libLiteRtCompilerPlugin_Qualcomm.so`, and `libGemmaModelConstraintProvider.so`.

## standardGpuRuntimeMinimalProbe

`gpuRuntimeAlignmentProbeDebug` の最新診断では、GPU 成功時に `libLiteRt.so` と `liblitertlm_jni.so` は loaded として見えている一方、`libLiteRtDispatch_Qualcomm.so`, `libLiteRtCompilerPlugin_Qualcomm.so`, `libGemmaModelConstraintProvider.so` は absent でも GPU callback streaming が成功している。

このため、`standardDebug` を変更せず、追加の DEV-only flavor `standardGpuRuntimeMinimalProbe` で core pair 最小 alignment を切り分ける。

Flavor:

- `standardGpuRuntimeMinimalProbe`
- Application ID: `io.github.ninbyo02.lami.gpuminimalprobe`
- Build: `./gradlew :app:assembleStandardGpuRuntimeMinimalProbeDebug`
- Install: `./gradlew :app:installStandardGpuRuntimeMinimalProbeDebug`

Native source set:

- `app/src/standardGpuRuntimeMinimalProbeDebug/jniLibs/arm64-v8a/`
- marker-only。`.so` 実体は置かない。
- Qualcomm dispatch/compiler/model constraint provider overlay は含めない。

Diagnostics:

- `minimal_runtime_probe_flavor`
- `minimal_runtime_probe_liblitert_present`
- `minimal_runtime_probe_liblitertlm_jni_present`
- `minimal_runtime_probe_runtime_stack_source`
- `minimal_runtime_probe_result_candidate`
- `minimal_runtime_probe_success_gate`
- `minimal_runtime_probe_loaded_liblitert_sha256`
- `minimal_runtime_probe_loaded_liblitertlm_jni_sha256`
- `minimal_runtime_probe_dispatch_present`
- `minimal_runtime_probe_compiler_plugin_present`
- `minimal_runtime_probe_constraint_provider_present`

Manual verification:

```bash
./gradlew :app:installStandardGpuRuntimeMinimalProbeDebug
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell monkey -p io.github.ninbyo02.lami.gpuminimalprobe 1
```

In the app:

- Model: `/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm`
- Backend: GPU
- Prompt: `こんにちは`, `カレーの材料をお願いします。`, `さっぱり系でお願いします。`

Expected interpretation:

- `minimal_runtime_probe_result_candidate=success`: `libLiteRt.so` + `liblitertlm_jni.so` core pair alignment is likely sufficient for the observed GPU success path.
- `minimal_runtime_probe_result_candidate=failure`: `gpuRuntimeAlignmentProbeDebug` still has another relevant runtime/packaging/lifecycle difference.

This flavor is a diagnostic split only. It does not promote GPU into `standardDebug`, and it does not relax the single `.so` replacement ban.

## Standard minimal runtime candidate gate

After `standardGpuRuntimeMinimalProbeDebug` succeeded, the current minimal runtime alignment hypothesis is:

- required core pair: `libLiteRt.so` + `liblitertlm_jni.so`
- not required for generic GPU success in the current evidence: `libLiteRtDispatch_Qualcomm.so`, `libLiteRtCompilerPlugin_Qualcomm.so`, `libGemmaModelConstraintProvider.so`, and QNN libraries

`standardDebug` still must not receive native stack changes by default. Android cannot safely switch packaged native
libraries with only a runtime property, so the Standard app now exposes a DEV-only diagnostic gate:

```bash
adb shell setprop debug.lami.standard_gpu_minimal_runtime_candidate true
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell monkey -p io.github.ninbyo02.lami 1
```

The gate emits:

- `standard_gpu_minimal_runtime_candidate_enabled`
- `standard_gpu_minimal_runtime_candidate_eligible`
- `standard_gpu_minimal_runtime_candidate_block_reason`
- `standard_gpu_minimal_runtime_candidate_result`
- `standard_gpu_minimal_runtime_candidate_success_gate`
- `standard_gpu_minimal_runtime_candidate_liblitert_sha256`
- `standard_gpu_minimal_runtime_candidate_liblitertlm_jni_sha256`
- `standard_gpu_minimal_runtime_candidate_dispatch_present`
- `standard_gpu_minimal_runtime_candidate_compiler_plugin_present`
- `standard_gpu_minimal_runtime_candidate_constraint_provider_present`
- `standard_gpu_minimal_runtime_candidate_runtime_stack`
- `standard_gpu_minimal_runtime_candidate_interpretation`

Expected Standard behavior before native promotion:

- The gate may be enabled.
- Candidate eligibility should remain blocked unless the loaded Standard APK actually contains the minimal core pair.
- `standardDebug` production/default behavior remains unchanged.

Rollback:

```bash
adb shell setprop debug.lami.standard_gpu_minimal_runtime_candidate false
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming false
adb shell setprop debug.lami.gpu_generate_probe_mode normal
```

Promotion checks before any Standard native stack change:

1. App restart first GPU request succeeds.
2. Holder reuse succeeds.
3. 3-5 continuous turns succeed.
4. Short, medium, and long prompts succeed.
5. Timeout/exception cleanup works.
6. CPU route remains stable.
7. NPU S1 remains gated and unchanged.
8. Rollback property/build path is documented and tested.

## Standard-like minimal runtime candidate flavor

The latest `standardDebug` device run showed the Standard diagnostic gate is
blocked because the loaded runtime is still the normal Standard stack:

- `standard_gpu_minimal_runtime_candidate_enabled=true`
- `standard_gpu_minimal_runtime_candidate_eligible=false`
- `standard_gpu_minimal_runtime_candidate_block_reason=liblitert_sha_mismatch`

This is expected. A runtime property cannot replace already packaged native
libraries, and the Standard APK must not be modified by directly mixing the
successful pair into the production/default runtime.

The next DEV-only verification flavor is:

- Flavor: `standardGpuMinimalRuntimeCandidate`
- Variant: `standardGpuMinimalRuntimeCandidateDebug`
- Application ID: `io.github.ninbyo02.lami.gpustandardminimal`
- Build: `./gradlew :app:assembleStandardGpuMinimalRuntimeCandidateDebug`
- Install: `./gradlew :app:installStandardGpuMinimalRuntimeCandidateDebug`
- Native source set: `app/src/standardGpuMinimalRuntimeCandidateDebug/jniLibs/arm64-v8a/`

This flavor keeps the Standard app UI and local route behavior, but allows local
staging of only the minimal successful runtime pair:

- `libLiteRt.so`
- `liblitertlm_jni.so`

The successful pair observed in `standardGpuRuntimeMinimalProbeDebug` is:

- `libLiteRt.so`: `31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24`
- `liblitertlm_jni.so`: `ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f`

These remain excluded from Git. Stage them locally with:

```bash
scripts/stage_standard_gpu_minimal_runtime_candidate_libs.sh --dry-run
scripts/stage_standard_gpu_minimal_runtime_candidate_libs.sh
```

The flavor explicitly does not stage or require the generic GPU path to include:

- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- `libGemmaModelConstraintProvider.so`
- QNN runtime libraries

Diagnostics:

- `standard_gpu_minimal_runtime_candidate_flavor`
- `standard_gpu_minimal_runtime_candidate_application_id`
- `standard_gpu_minimal_runtime_candidate_loaded_liblitert_sha256`
- `standard_gpu_minimal_runtime_candidate_loaded_liblitertlm_jni_sha256`
- `standard_gpu_minimal_runtime_candidate_dispatch_present`
- `standard_gpu_minimal_runtime_candidate_compiler_plugin_present`
- `standard_gpu_minimal_runtime_candidate_constraint_provider_present`
- `standard_gpu_minimal_runtime_candidate_runtime_stack_source`
- `standard_gpu_minimal_runtime_candidate_result`
- `standard_gpu_minimal_runtime_candidate_success_gate`
- `runtime_stack_alignment_interpretation`

Manual verification:

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell monkey -p io.github.ninbyo02.lami.gpustandardminimal 1
```

Use `/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm`, select GPU, and
run short, medium, and multi-turn prompts. Promotion to Standard remains blocked
until this flavor passes restart, repeated turn, long-output, failure-cleanup,
CPU regression, and NPU S1 regression checks.

### Phase 18 diagnostics: quality, speed, holder lifecycle

`standardGpuMinimalRuntimeCandidateDebug` has demonstrated GPU generation success with the minimal runtime pair. The
current blocker is quality/speed/lifecycle confidence, not basic runtime startup.

When checking output corruption, compare these stages:

- raw callback: `gpu_output_raw_callback_text_length`, `gpu_output_raw_callback_text_head`,
  `gpu_output_raw_callback_text_tail`;
- promoted streaming text: `gpu_output_promoted_text_length`, `gpu_output_promoted_text_head`,
  `gpu_output_promoted_text_tail`;
- final assistant text: `gpu_output_final_assistant_text_length`, `gpu_output_final_assistant_text_head`,
  `gpu_output_final_assistant_text_tail`;
- classification: `gpu_output_suspicious_fragment_detected`,
  `gpu_output_suspicious_fragment_reason`.

When checking speed regression, compare:

- `gpu_perf_engine_acquire_elapsed_ms`;
- `gpu_perf_engine_create_or_reuse`;
- `gpu_perf_conversation_create_elapsed_ms`;
- `gpu_perf_generate_to_first_token_ms`;
- `gpu_perf_first_to_last_callback_ms`;
- `gpu_perf_callback_total_elapsed_ms`;
- `gpu_perf_lami_visible_tokens_per_second`;
- `gpu_perf_tokenizer_count_duration_ms`;
- `gpu_perf_slow_path_detected`;
- `gpu_perf_slow_path_reason`.

When checking unexpected holder cleanup after success, inspect:

- `gpu_holder_lifecycle_event_after_success`;
- `gpu_holder_lifecycle_last_activity_state`;
- `gpu_holder_lifecycle_last_app_visibility`;
- `gpu_holder_lifecycle_clear_trigger_elapsed_ms`;
- `gpu_holder_lifecycle_clear_after_success_ms`;
- `gpu_holder_lifecycle_clear_during_active_generate`;
- `gpu_holder_lifecycle_clear_after_ui_append`;
- `gpu_holder_lifecycle_clear_reason_detail`;
- `gpu_holder_lifecycle_background_detection_source`.

For normal generation verification, keep prefill probe disabled. If prefill probe is accidentally enabled, the run may
skip normal generation by design and report `gpu_prefill_probe_blocks_normal_generate=true`. The expected verification
setting is:

```bash
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell setprop debug.lami.gpu_probe_use_held_engine false
```

Promotion remains prohibited until quality drift, slow-path classification, and holder lifecycle cleanup are understood
across restart, 3-5 continuous turns, and a long response.

### Phase 19 transient onStop and output-tail probes

`standardGpuMinimalRuntimeCandidateDebug` keeps GPU promotion DEV-only, but it now has a guarded holder protection for
the observed success-then-`onStop` case. The protection is active only in the candidate flavor while GPU callback
streaming is selected, or when explicitly enabled with:

```bash
adb shell setprop debug.lami.gpu_holder_lifecycle_defer_transient_onstop true
```

Unset means candidate-flavor default behavior. Set `false` to roll the protection back for comparison. The guard:

- defers holder close during active GPU generate;
- suppresses short transient `onStop` after GPU success/UI append;
- records `gpu_holder_lifecycle_onstop_deferred`,
  `gpu_holder_lifecycle_clear_suppressed_after_success`, and
  `gpu_holder_lifecycle_reuse_expected_next_turn`;
- still clears on confirmed background after the transient window, model/backend switch, failure cleanup, or timeout
  cleanup.

For output-tail corruption, compare:

- `gpu_output_suspicious_fragment_position`;
- `gpu_output_suspicious_fragment_tail_ratio`;
- `gpu_output_repeated_markdown_fragment_detected`;
- `gpu_output_mixed_japanese_fragment_detected`;
- `gpu_output_chunk_join_strategy`;
- `gpu_output_chunk_boundary_suspected`;
- `gpu_output_last_chunks_summary`.

Hypothesis ranking for tail drift:

1. Long-output sampling / `maxTokens=4000` remains plausible.
2. Raw GPU callback corruption points toward the minimal LiteRT-LM GPU runtime path or model/runtime interaction.
3. Clean raw callback but corrupt promoted/final text points toward chunk append/join or Markdown/UI handling.
4. Holder lifecycle primarily explains cold-load speed regression; it is not yet proven to cause output corruption.

Next opt-in experiment:

```bash
adb shell setprop debug.lami.gpu_output_quality_probe_short_max_tokens true
```

This lowers the candidate flavor GPU `maxTokens` to 256. Keep it `false` for baseline runs.

Native stack classification for the promotion decision:

| Library group | Promotion meaning |
| --- | --- |
| `libLiteRt.so` + `liblitertlm_jni.so` | Full stack alignment candidate. These are a matched core runtime pair and must not be swapped individually. |
| `libLiteRtDispatch_Qualcomm.so` / `libLiteRtCompilerPlugin_Qualcomm.so` | Review as related stack members if present or different. Do not test without the matched core pair. |
| `libGemmaModelConstraintProvider.so` | Review as a model constraint/runtime member if present or different. |
| QNN libraries | Keep inventoried, but do not treat as the primary generic GPU success requirement without new evidence. |
| Edge Gallery-derived support libraries | Review only as part of a complete isolated runtime stack. |
| Unrelated support libraries | Low priority unless NEEDED edges show direct linkage from the core runtime pair. |

Dev-only promotion plan before touching standardDebug:

1. Keep `standardDebug` unchanged.
2. Continue stability testing in `galleryStackGpuProbe`.
3. Use `native_lib_diff.tsv` and `needed_dependency_edges.tsv` to define a minimal full-stack alignment candidate.
4. Test that candidate only in a separate DEV flavor/application id.
5. Keep single `.so` replacement forbidden.
6. Keep `galleryStackGpuProbe` as the promotion candidate until repeated success, clean diagnostics, and provenance/licensing checks are complete.

## gpuRuntimeAlignmentProbe Promotion Candidate

`gpuRuntimeAlignmentProbe` is the next DEV-only flavor for staged promotion checks:

- Product flavor: `gpuRuntimeAlignmentProbe`
- Application id suffix: `.gpualignment`
- Version suffix: `-gpuRuntimeAlignmentProbe`
- Build task: `./gradlew :app:assembleGpuRuntimeAlignmentProbeDebug`
- Install task: `./gradlew :app:installGpuRuntimeAlignmentProbeDebug`
- Native lib source dir: `app/src/gpuRuntimeAlignmentProbeDebug/jniLibs/arm64-v8a/`

This flavor exists because:

- `standardDebug + Edge Gallery E2B model` still fails with `cc:735`.
- `galleryStackGpuProbe + the same model` succeeds on GPU.
- The model is therefore no longer the leading blocker.
- The likely blocker is runtime/native stack alignment.

The flavor must be treated as a full-stack candidate harness. It is not a place for individual `.so` swaps into `standardDebug`.

Runtime alignment diagnostics:

- `runtime_alignment_probe_flavor=true`
- `runtime_alignment_stack_source`
- `runtime_alignment_liblitert_sha256`
- `runtime_alignment_liblitertlm_jni_sha256`
- `runtime_alignment_dispatch_qualcomm_present`
- `runtime_alignment_compiler_plugin_qualcomm_present`
- `runtime_alignment_gemma_constraint_provider_present`
- `runtime_alignment_result_candidate`
- `runtime_alignment_success_gate`

The existing GPU callback streaming diagnostics remain required:

- `gpu_callback_streaming_path_selected`
- `gpu_callback_text_promoted_to_ui`
- `gpu_ui_append_finished`
- `gpu_streaming_completion_reason`
- `failure_stage`
- `litert_lm_error_kind`
- `gpu_litert_executor_error_file`
- `gpu_litert_executor_error_line`

Suggested commands:

```bash
./gradlew :app:installGpuRuntimeAlignmentProbeDebug
adb shell setprop debug.lami.runtime_alignment_probe true
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell monkey -p io.github.ninbyo02.lami.gpualignment 1
```

Use:

```text
/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm
```

Stability tests before any standardDebug design:

1. App restart then first GPU request.
2. Held engine reuse on the second and later turns.
3. Short prompt: `こんにちは`.
4. Medium prompt: `カレーの材料をお願いします`.
5. Long prompt: `キーマカレーの材料を詳しく`.
6. Continuous 3-5 turns.
7. Failure cleanup after a forced or natural GPU failure.
8. CPU fallback route remains explicit and unchanged.
9. NPU S1 remains gated and unchanged.

Promotion remains blocked until this flavor repeatedly reports:

- `runtime_alignment_result_candidate=success`
- `gpu_callback_streaming_path_selected=true`
- `gpu_callback_text_promoted_to_ui=true`
- `gpu_ui_append_finished=true`
- `failure_stage=none`

## Standard vs gpuRuntimeAlignmentProbe Native Stack Split

The Standard dev-gated candidate has now been tested with the same Edge Gallery E2B model and remains a failure:

- `standard_gpu_runtime_alignment_candidate_enabled=true`
- `standard_gpu_runtime_alignment_candidate_eligible=true`
- `standard_gpu_runtime_alignment_candidate_result=failure`
- `failure_stage=gpu_generate_compiled_model_invoke_failed`
- `gpu_litert_executor_error_file=runtime/executor/llm_litert_compiled_model_executor.cc`
- `gpu_litert_executor_error_line=735`

`gpuRuntimeAlignmentProbeDebug` succeeds with the same model, callback streaming, and held-engine reuse. Model identity is
therefore no longer the leading blocker. The remaining blocker is the Standard APK runtime/native stack.

Use this comparison script after both APKs are assembled:

```bash
./gradlew :app:assembleStandardDebug
./gradlew :app:assembleGpuRuntimeAlignmentProbeDebug
scripts/compare_standard_gpu_runtime_alignment_probe_native_libs.sh
```

Outputs:

- `artifacts/gpu_runtime_stack_compare/standard_debug_native_libs.tsv`
- `artifacts/gpu_runtime_stack_compare/gpu_runtime_alignment_probe_native_libs.tsv`
- `artifacts/gpu_runtime_stack_compare/standard_vs_gpu_runtime_alignment_native_lib_diff.tsv`
- `artifacts/gpu_runtime_stack_compare/standard_vs_gpu_runtime_alignment_needed_edges.tsv`
- `artifacts/gpu_runtime_stack_compare/standard_vs_gpu_runtime_alignment_stack_classification.md`
- `artifacts/gpu_runtime_stack_compare/standard_to_gpu_runtime_alignment_probe.md`

The full-stack candidate unit is:

```text
libLiteRt.so + liblitertlm_jni.so + libLiteRtDispatch_Qualcomm.so + libLiteRtCompilerPlugin_Qualcomm.so + libGemmaModelConstraintProvider.so + directly linked support libs
```

This is a classification unit, not a staging instruction. Single `.so` replacement remains prohibited.

Additional diagnostics now identify the actually loaded APK native stack for GPU routes:

- `runtime_stack_loaded_source_flavor`
- `runtime_stack_loaded_native_library_dir`
- `runtime_stack_loaded_native_stack_source`
- `runtime_stack_loaded_liblitert_present`
- `runtime_stack_loaded_liblitert_sha256`
- `runtime_stack_loaded_liblitertlm_jni_present`
- `runtime_stack_loaded_liblitertlm_jni_sha256`
- `runtime_stack_loaded_dispatch_qualcomm_present`
- `runtime_stack_loaded_dispatch_qualcomm_sha256`
- `runtime_stack_loaded_compiler_plugin_qualcomm_present`
- `runtime_stack_loaded_compiler_plugin_qualcomm_sha256`
- `runtime_stack_loaded_gemma_constraint_provider_present`
- `runtime_stack_loaded_gemma_constraint_provider_sha256`
- `runtime_stack_loaded_full_stack_candidate_unit`
- `runtime_stack_alignment_interpretation`

Expected Standard failure interpretation:

```text
runtime_stack_alignment_interpretation=standard_runtime_stack_mismatch_candidate
standard_gpu_runtime_stack_mismatch_summary=runtime_stack_mismatch_suspected
standard_gpu_runtime_stack_promotion_blocked_reason=standard_runtime_stack_not_aligned
```

Expected runtime alignment probe success interpretation:

```text
runtime_stack_alignment_interpretation=runtime_alignment_probe_stack_success
```

Rollback plan:

1. Leave `standardDebug` native stack unchanged.
2. Disable Standard candidate properties:

```bash
adb shell setprop debug.lami.standard_gpu_runtime_alignment_candidate false
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming false
adb shell setprop debug.lami.gpu_generate_probe_mode normal
```

3. Uninstall only DEV probe APKs if needed:

```bash
adb uninstall io.github.ninbyo02.lami.gpualignment
adb uninstall io.github.ninbyo02.lami.gallerystackgpu
```

Risk gates before any Standard promotion design:

- License/provenance of all staged runtime artifacts must be recorded.
- Packaging behavior must be reviewed for duplicate native libs, strip warnings, ABI splits, `extractNativeLibs`, and
  dependency graph consistency.
- Promotion must use a full matched runtime stack in a separate DEV build first. It must not be a single `.so` swap.
- CPU route, NPU S1, fallback, and Standard default GPU behavior must remain unchanged.
- Phase order is fixed: Phase 1 DEV gate, Phase 2 safety soak, Phase 3 explicit Experimental GPU UI toggle, Phase 4
  production consideration. Current state is still Phase 1 / Phase 2 diagnostic work, not promotion.

Holder reuse and cleanup diagnostics added for the runtime alignment probe:

- `gpu_alignment_holder_present_before_acquire`
- `gpu_alignment_holder_acquire_result`
- `gpu_alignment_holder_reused`
- `gpu_alignment_holder_created`
- `gpu_alignment_holder_cleared`
- `gpu_alignment_holder_clear_reason`
- `gpu_alignment_holder_close_started`
- `gpu_alignment_holder_close_finished`
- `gpu_alignment_holder_reuse_block_reason`
- `gpu_alignment_holder_model_path_changed`
- `gpu_alignment_holder_backend_changed`
- `gpu_alignment_holder_app_process_start_marker`
- `gpu_alignment_turn_index_if_available`
- `gpu_alignment_previous_turn_success`
- `gpu_alignment_previous_turn_failure_stage`

Reuse classification values:

- `reuse_ok`
- `first_turn_no_previous_holder`
- `model_path_changed`
- `backend_changed`
- `holder_cleared_after_success`
- `holder_cleared_after_failure`
- `app_process_restarted`
- `explicit_debug_no_held_engine`
- `unsupported_or_unknown`

Current stability memo:

- `runtimeAlignmentProbe` is proven only as a DEV probe path.
- Short, medium, longer, and multi-turn GPU callback streaming success has been observed manually.
- The remaining questions are holder reuse, success cleanup, and standard promotion criteria.
- The promotion candidate remains: runtime stack alignment + callback streaming + Edge Gallery E2B model + allowlist config.
- `standardDebug` promotion remains forbidden until stability gates pass.

Production promotion gates:

1. App force-stop/restart then first GPU request succeeds.
2. Same model succeeds for 3-5 continuous turns.
3. Second and later turns show `gpu_alignment_holder_reused=true` / `reuse_ok`, or a clear non-reuse reason.
4. Long output of 800+ tokens succeeds.
5. Failure after a forced or natural GPU error runs holder cleanup and the next run starts safely.
6. CPU route success is unchanged.
7. NPU S1 remains gated and unchanged.
8. `standardDebug` remains unpromoted and may continue to fail until native stack promotion is explicitly designed.

Manual model selection:

```text
/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm
```

Run:

1. CPU backend: `こんにちは`
2. GPU backend: `こんにちは`

Interpretation:

- GPU succeeds: runtime stack/model alignment likely fixed the issue.
- GPU succeeds only in `callback_to_ui` or `normal_callback_streaming`: callback streaming is the viable LAMI GPU path, but it remains DEV opt-in until repeated stability checks pass.
- GPU succeeds with `gpu_normal_route_use_callback_streaming=true`: the callback streaming implementation is viable as the guarded normal route candidate, but GPU remains disabled as a production default.
- GPU still fails at `runtime/executor/llm_litert_compiled_model_executor.cc:735`: public `Backend.GPU` or inaccessible `GPU_ARTISAN`/internal executor remains the likely blocker.
- GPU fails earlier at load/init: staged native stack is incompatible with this app packaging/dependency graph.

## Standard Phase 1 Dev Gate

The Standard app now has a DEV-only runtime alignment candidate gate. It does not stage native libraries and it is off
by default.

```bash
adb shell setprop debug.lami.standard_gpu_runtime_alignment_candidate true
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell monkey -p io.github.ninbyo02.lami 1
```

Eligibility requires GPU backend, an Edge Gallery E2B-compatible model, exact model size when available, the callback
streaming gate, and no active generation/switch block. The candidate emits:

- `standard_gpu_runtime_alignment_candidate_enabled`
- `standard_gpu_runtime_alignment_candidate_eligible`
- `standard_gpu_runtime_alignment_candidate_block_reason`
- `standard_gpu_runtime_alignment_candidate_model_size_bytes`
- `standard_gpu_runtime_alignment_candidate_model_identity_hint`
- `standard_gpu_runtime_alignment_candidate_runtime_stack`
- `standard_gpu_runtime_alignment_candidate_result`

Test prompts:

1. `こんにちは`
2. `カレーの材料をお願いします。`
3. `さっぱり系でお願いします。`
4. `分量も教えてください。`

Rollback:

```bash
adb shell setprop debug.lami.standard_gpu_runtime_alignment_candidate false
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming false
adb shell setprop debug.lami.gpu_generate_probe_mode normal
```

Promotion remains prohibited by default. Phase 2 requires repeated Standard candidate stability checks and a full-stack
runtime promotion design; single `.so` replacement remains prohibited.

## CPU Route Regression Guard

During the standard GPU minimal runtime investigation, CPU backend selection also showed
LiteRT-LM `Status Code: 13` / compiled model invoke failures on some runs. CPU had previously been recorded as the
stable route, so CPU failures are treated as regression evidence rather than as expected GPU-quality fallout.

CPU route diagnostics now use CPU-specific keys in addition to existing compatibility keys:

- `cpu_route_selected`
- `cpu_engine_config_backend`
- `cpu_generate_started`
- `cpu_generate_finished`
- `cpu_generate_failed_before_first_token`
- `cpu_generate_call_entered`
- `cpu_callback_invoked_count`
- `cpu_first_token_received`
- `cpu_generate_exception_status_code`
- `cpu_generate_exception_error_file`
- `cpu_generate_exception_error_line`
- `cpu_failure_stage`
- `cpu_failure_interpretation`
- `cpu_previous_holder_backend`

`debug.lami.cpu_route_probe=true` records the normal CPU route as a smoke probe without launching a second inference.
The holder key includes the requested/text backend, so CPU selection must not reuse a previous GPU held engine for the
same model/cache pair.

Rollback:

```bash
adb uninstall io.github.ninbyo02.lami.gallerystackgpu
adb shell setprop debug.lami.gallery_stack_gpu_probe false
```

Remove local staged libraries by deleting files under:

```text
app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/*.so
```

Do not remove the `README.md` marker file.

## Gates Before Implementation

1. Confirm Edge Gallery model identity.
   - Exact model filename.
   - File size.
   - SHA-256.
   - Download/source URL or app-data model path.
2. Confirm LAMI model identity.
   - Exact selected model path.
   - File size.
   - SHA-256.
3. Confirm runtime stack provenance.
   - Version/source of LiteRT-LM Android runtime.
   - Version/source of LiteRT core runtime.
   - Build IDs and SHA-256 for all arm64 native libraries.
4. Confirm license and redistribution constraints before staging any external runtime into a local flavor.

## Required Diagnostics

The probe flavor must expose the same local inference failure compact keys as `standardDebug`, plus:

- `gallery_stack_probe_enabled`
- `gallery_stack_probe_app_id`
- `gallery_stack_probe_runtime_stack_id`
- `gallery_stack_probe_model_sha256`
- `gallery_stack_probe_model_size_bytes`
- `gallery_stack_probe_lib_litert_sha256`
- `gallery_stack_probe_lib_litert_build_id`
- `gallery_stack_probe_lib_litertlm_jni_sha256`
- `gallery_stack_probe_lib_litertlm_jni_build_id`
- `gallery_stack_probe_backend_api_candidates`
- `gallery_stack_probe_gpu_artisan_api_available`
- `gallery_stack_probe_executor_selection_hint`
- `gallery_stack_probe_result`

## Future Implementation Steps

1. Add an isolated product flavor/application id suffix without changing `standardDebug`.
2. Add a staging script that verifies a complete runtime stack before copying anything into the flavor source set.
3. Add model import checks that refuse suspicious files and require size/SHA logging.
4. Add a single DEV-only GPU probe screen or adb-property trigger in the isolated app.
5. Compare CPU and GPU callback/generate diagnostics inside the isolated app.
6. Promote no behavior back to `standardDebug` until model identity, runtime stack identity, and API/lifecycle conditions are understood.

## Risk Assessment

| Risk | Level | Mitigation |
| --- | --- | --- |
| Mixed LiteRT / LiteRT-LM native stack ABI mismatch | High | Use a matched stack only, isolated in a separate flavor. |
| False positive from different Edge Gallery model | High | Require model size/SHA/source identity before claiming parity. |
| Native library licensing/provenance issue | High | Do not import external runtime until provenance is documented. |
| Regressing CPU or NPU paths | Low if isolated | Keep separate app id/source set and avoid shared route changes. |
| Confusing users with experimental GPU behavior | Medium | Keep the flavor DEV-only and explicit opt-in. |

## Success Criteria

The isolated flavor is useful only if it can answer one of these questions:

- Same model + same runtime stack + comparable API conditions succeeds on GPU.
- Same model + same runtime stack still fails, making device/runtime GPU compatibility more likely.
- Edge Gallery success depends on a different model or internal executor path that current LAMI public API cannot reach.

Until one of those is proven, LAMI should keep CPU as the stable generic route and keep GPU experimental.

## Standard GPU Minimal Runtime Candidate: Output Quality Matrix

`standardGpuMinimalRuntimeCandidateDebug` has reached GPU generate success with the minimal runtime pair
`libLiteRt.so` + `liblitertlm_jni.so`. The remaining blocker is no longer basic GPU execution, but output quality:
longer Japanese answers can degrade near the tail with tiny fragments, repeated markdown-like markers, or mixed
punctuation. Short prompts such as `こんにちは` can remain clean.

The current evidence also lowers `maxTokens` as the single root cause: the short max-token probe can still show tail
fragmentation. The next split is sampler behavior, callback chunk sequence, UI append/join behavior, and max-token
budget interactions.

DEV-only matrix property:

```bash
adb shell setprop debug.lami.gpu_output_quality_matrix_mode baseline
adb shell setprop debug.lami.gpu_output_quality_max_tokens 4000
```

Supported modes:

- `baseline`: Edge Gallery-like sampler and incremental callback streaming.
- `sampler_minimal`: minimal sampler config while keeping callback streaming.
- `no_sampling_acceleration`: no sampler config / no sampling acceleration candidate.
- `disable_topk_gpu_sampler_candidate`: disables the TopK GPU sampler candidate by removing sampler config.
- `collect_only`: receives callback chunks but commits the final accumulated text once, separating callback source
  corruption from incremental UI append behavior.

Supported max-token override values are `128`, `256`, `512`, `1024`, and `4000`. The older boolean
`debug.lami.gpu_output_quality_probe_short_max_tokens=true` remains available and maps to `256`, but the numeric
override should be preferred for matrix runs.

Helper script:

```bash
scripts/run_gpu_output_quality_matrix.sh --mode collect_only --max-tokens 512 --prompt "カレーの材料をお願いします。"
```

Key diagnostics:

- `gpu_output_quality_matrix_mode`
- `gpu_output_quality_sampler_mode`
- `gpu_output_quality_streaming_mode`
- `gpu_output_quality_effective_max_tokens`
- `gpu_output_quality_collect_only_enabled`
- `gpu_output_quality_ui_incremental_append_enabled`
- `gpu_output_last_chunks_summary`
- `gpu_output_chunk_length_histogram`
- `gpu_output_quality_candidate_result`
- `gpu_output_quality_failure_block_reason`
- `gpu_output_quality_recommendation`
- `gpu_output_source_corruption_stage`

Promotion remains blocked until the matrix shows stable quality across short, medium, and long prompts, with holder
reuse still working and no CPU/NPU regressions. StandardDebug default GPU remains disabled.

## Callback Source Analysis

The quality matrix can now show when corruption is already present in the raw callback stream. In that case,
`collect_only` and incremental UI append should both report:

```text
gpu_output_ui_append_changed_text=false
gpu_output_source_corruption_stage=raw_callback
gpu_output_quality_failure_block_reason=callback_source_already_suspicious
```

Additional callback metrics are emitted to distinguish normal small streaming chunks from pathological fragmentation:

- `callback_count`
- `non_empty_callback_count`
- `empty_callback_count`
- `average_chunk_length`
- `median_chunk_length`
- `p50_chunk_length`
- `p90_chunk_length`
- `p95_chunk_length`
- `one_char_chunk_count`
- `two_char_or_less_chunk_count`
- `one_char_chunk_ratio`
- `two_char_or_less_chunk_ratio`
- `longest_chunk_length`
- `shortest_non_empty_chunk_length`
- `callback_first_30_chunks`
- `callback_last_30_chunks`
- `callback_quality_classification`
- `callback_corruption_earliest_stage`

When `debug.lami.compare_cpu_gpu_callback=true` is enabled, the same prompt is sampled on CPU for callback-shape
comparison in `standardGpuMinimalRuntimeCandidateDebug` or with an explicit debug override. CPU output is diagnostics
only and is never appended to the visible assistant message. If comparison cannot run, the diagnostics must say why
rather than leaving `cpu_compare_*` as only `unavailable`.

Lifecycle keys:

- `cpu_compare_requested`
- `cpu_compare_enabled`
- `cpu_compare_started`
- `cpu_compare_finished`
- `cpu_compare_skipped_reason`
- `cpu_compare_failure_stage`
- `cpu_compare_elapsed_ms`

The summary keys are:

- `cpu_avg_chunk_length`
- `gpu_avg_chunk_length`
- `cpu_callback_count`
- `gpu_callback_count`
- `cpu_two_char_or_less_ratio`
- `gpu_two_char_or_less_ratio`
- `cpu_callback_first_30_chunks`
- `cpu_callback_last_30_chunks`
- `cpu_callback_quality_classification`
- `cpu_output_suspicious_fragment_detected`
- `callback_quality_compare_result`
- `callback_quality_compare_reason`

`callback_quality_compare_result` uses:

- `gpu_only_corrupt`
- `gpu_corrupt_cpu_unavailable`
- `cpu_and_gpu_corrupt`
- `cpu_only_corrupt`
- `both_pass`
- `comparison_unavailable`

If CPU comparison throws, times out, is skipped, or receives no callbacks, it must not be treated as `both_pass`.
Use:

- `gpu_corrupt_cpu_unavailable`: GPU output is suspicious but CPU comparison is unavailable.
- `comparison_unavailable`: GPU output is not currently suspicious, but CPU comparison did not produce valid data.

If `callback_quality_compare_result=gpu_only_corrupt` or `gpu_corrupt_cpu_unavailable`, especially with
`gpu_output_source_corruption_stage=raw_callback`, the current strongest suspect is the LiteRT-LM GPU callback / decode
stream source rather than ChatScreen append, markdown rendering, or final UI commit. Production promotion remains
blocked while `gpu_output_quality_candidate_result=quality_candidate_fail` or
`callback_quality_compare_result=gpu_only_corrupt` / `gpu_corrupt_cpu_unavailable` is reproducible.

Fragment localization keys:

- `gpu_fragmentation_score`
- `gpu_fragmentation_percentile`
- `gpu_fragmentation_head_score`
- `gpu_fragmentation_middle_score`
- `gpu_fragmentation_tail_score`
- `gpu_chunk_size_distribution`
- `gpu_chunk_length_sequence`
- `gpu_fragmentation_cluster_count`
- `gpu_fragmentation_cluster_max_length`
- `gpu_fragmentation_cluster_avg_length`

Sampler/root-cause hint:

- `gpu_sampler_root_cause_candidate=streaming_join_issue`: UI append/final commit changed the callback text.
- `gpu_sampler_root_cause_candidate=not_sampler_related`: corruption remains in no-sampler/top-k-disabled modes.
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`: raw callback is corrupt with severe/pathological
  fragmentation.
- `gpu_sampler_root_cause_candidate=callback_source_corruption`: raw callback is suspicious, but the fragmentation
  score alone is not enough to call runtime decode fragmentation.
- `gpu_sampler_root_cause_candidate=sampler_related`: only a sampler-specific run points toward sampler behavior.

After copying compact/details output from each matrix mode into `artifacts/gpu_output_quality_matrix/`, summarize:

```bash
scripts/summarize_gpu_output_quality_matrix.sh
```

### Raw callback artifact conclusion

The raw callback artifact pass confirms that the current long-output corruption is already present before LAMI
Markdown repair, UI append, or collect-only final commit:

- `debug.lami.gpu_callback_raw_passthrough=true`
- raw artifact directory: `artifacts/gpu_callback_raw_stream/`
- full sequence: `artifacts/gpu_callback_raw_stream/gpu_callback_raw_full.txt`
- accumulated final text: `artifacts/gpu_callback_raw_stream/gpu_callback_accumulated_final.txt`
- per-callback chunks: `callback_0001.txt` through `callback_0323.txt`
- raw full size: `8000` bytes
- accumulated final size: `1217` bytes
- raw full sha256: `0783f5dedad31015989ae8fa07c421a905475bf4fab48bb6b903fa854aea5752`
- accumulated final sha256: `4fefb356c89ece6120e80ad9a57c0f914c7fd9fb2d8a9875334d7ea7b2216606`

Observed raw callback tail corruption includes fragments such as:

```text
肉（鶏も肉豚肉）：30～00
玉ぎ：（40g
がい：個（30g
にじん2本約5g）
イス味料
```

Representative callback files:

- `callback_0100.txt`: `text=豚`
- `callback_0200.txt`: `text=パイ`

Matrix summary:

```text
baseline|quality_candidate_fail|0.816|1.78|0.524|0.188
collect_only|quality_candidate_fail|0.893|1.66|0.582|0.174
no_sampling_acceleration|quality_candidate_fail|0.953|1.72|0.543|0.271
ROOT_CAUSE_CANDIDATE=runtime_decode_fragmentation
```

This rules down LAMI Markdown/UI joining as the primary cause for this artifact. The active blocker is LiteRT-LM GPU
raw callback source quality, summarized in diagnostics as:

- `gpu_output_quality_summary=runtime_callback_source_corruption_suspected`
- `gpu_output_quality_gate_status=fail`
- `gpu_output_quality_promotion_blocker=true`

Promotion remains blocked when any of the following are reproducible:

- `gpu_output_quality_candidate_result=quality_candidate_fail`
- `callback_corruption_earliest_stage=raw_callback`
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`

### Raw Callback Index Analyzer

Use `scripts/analyze_gpu_callback_raw_stream.sh` after pulling or copying the raw callback artifacts. The analyzer does
not modify app behavior, model loading, runtime stack, holder lifecycle, or UI output. It only turns the captured raw
callback stream into index-level evidence for the GPU quality blocker.

```bash
scripts/analyze_gpu_callback_raw_stream.sh \
  --input artifacts/gpu_callback_raw_stream
```

or, when only the full sequence is available:

```bash
scripts/analyze_gpu_callback_raw_stream.sh \
  --full artifacts/gpu_callback_raw_stream/gpu_callback_raw_full.txt
```

Generated files:

- `artifacts/gpu_callback_raw_stream_analysis/summary.txt`
- `artifacts/gpu_callback_raw_stream_analysis/suspicious_window.txt`
- `artifacts/gpu_callback_raw_stream_analysis/chunk_metrics.tsv`

Important summary keys:

- `callback_count`
- `empty_callback_count`
- `non_empty_callback_count`
- `avg_chunk_length`
- `one_char_ratio`
- `two_char_or_less_ratio`
- `first_suspicious_callback_index`
- `first_suspicious_callback_text`
- `suspicious_window_before`
- `suspicious_window_after`
- `corruption_phase`
- `corruption_reason_candidates`
- `root_cause_hint`

The heuristic intentionally stays conservative rather than pretending to be a full Japanese semantic validator. It marks
the first suspicious callback when a recent 20-chunk window shows dense 1-2 character fragmentation, repeated empty
callbacks, dense Markdown markers, numeric/unit fragments, or known broken ingredient-list fragments such as `玉ぎ`,
`じゃも`, `にじん`, `イス味料`, `スパ粉`, or `30～4g`. The first 50 chunks are treated more leniently to avoid flagging a
healthy introduction such as `どのようなカレーにしたいですか？😊`.

`suspicious_window.txt` is the main inspection target: it prints the chunks immediately before and after
`first_suspicious_callback_index`, with the per-chunk reasons from `chunk_metrics.tsv`. This lets the investigation say
where the raw callback stream first stops looking like a normal response, instead of only saying that the final text is
bad.

Interpretation:

- `corruption_phase=none`: the sampled raw callback stream did not trip the current heuristic.
- `corruption_phase=early_header_ok_then_tail_corrupt`: the opening looks healthy, then the raw callback stream starts
  fragmenting or producing broken ingredient/numeric text.
- `corruption_phase=immediate_corruption`: the stream is suspicious from the beginning.
- `root_cause_hint=runtime_decode_fragmentation`: raw callback source quality is the likely blocker; UI append and
  Markdown repair are not the primary cause for this artifact.
- `root_cause_hint=callback_transport_fragmentation`: callbacks are pathologically tiny, but more semantic evidence is
  needed before calling decode corruption.

The analyzer strengthens the quality blocker evidence only. It is not a production promotion gate by itself, and it must
not be used to repair or rewrite GPU output.

### Edge Gallery Parity Matrix

The current comparison is no longer "model is bad" versus "model is good":

- Edge Gallery GPU with `gemma-4-E2B-it-edge-gallery.litertlm`: long Japanese output is natural.
- LAMI CPU with `gemma-4-E2B-it.litertlm` and `gemma-4-E2B-it-edge-gallery.litertlm`: long Japanese output works after
  holder identity separation.
- LAMI `standardGpuMinimalRuntimeCandidateDebug` GPU: short prompts pass, but long output can corrupt at
  `callback_corruption_earliest_stage=raw_callback`.

Therefore the active split is Edge Gallery GPU route versus LAMI GPU route. Use
`debug.lami.gpu_output_quality_matrix_mode` to run Edge Gallery parity probes in the dev-only
`standardGpuMinimalRuntimeCandidateDebug` flavor:

- `edge_gallery_parity_minimal`: Edge-Gallery-like baseline config and incremental callback streaming.
- `edge_gallery_parity_no_streaming`: collect callback chunks without incremental UI append.
- `edge_gallery_parity_collect_final`: collect-only final commit comparison.
- `edge_gallery_parity_no_holder_reuse`: force a cold held-engine acquire before this run.
- `edge_gallery_parity_cache_app_files`: use the app cache dir.
- `edge_gallery_parity_cache_null`: use null cache dir.
- `edge_gallery_parity_sampler_default`: keep the Gallery default sampler.
- `edge_gallery_parity_sampler_none`: remove the sampler config / no sampling acceleration candidate.

Manual example:

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell setprop debug.lami.gpu_output_quality_matrix_mode edge_gallery_parity_minimal
adb shell setprop debug.lami.gpu_output_quality_max_tokens 512
adb shell monkey -p io.github.ninbyo02.lami.gpustandardminimal 1
```

Prompt:

```text
カレーの材料をお願いします。
```

New keys:

- `edge_gallery_parity_mode`
- `edge_gallery_parity_engine_config_profile`
- `edge_gallery_parity_conversation_config_profile`
- `edge_gallery_parity_callback_mode`
- `edge_gallery_parity_holder_reuse`
- `edge_gallery_parity_cache_dir_mode`
- `edge_gallery_parity_sampler_present`
- `edge_gallery_parity_candidate_result`
- `edge_gallery_parity_difference_summary`

Interpretation:

- `edge_gallery_parity_candidate_result=quality_candidate_fail` keeps Standard GPU promotion blocked.
- `edge_gallery_parity_difference_summary=edge_gallery_gpu_ok_lami_gpu_raw_callback_decode_fragmentation` means the
  model and CPU route are not the leading suspects; the LAMI GPU route still differs from Edge Gallery before UI append.
- A parity mode that flips to `quality_candidate_pass` becomes the next candidate to inspect before any wider promotion.
