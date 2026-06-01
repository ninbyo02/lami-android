# Lami vs Google AI Edge Gallery NPU backend delta

Date: 2026-06-02

Scope: static/local investigation only. I did not change production ChatScreen,
S1-S5, fallback settings, QAIRT/QNN settings, native library staging, or any app
code/config. I only wrote this report.

## Inputs checked

- Lami workspace: `/home/sato/project/lami-android`
- Lami aligned probe APK artifact:
  `app/build/outputs/apk/galleryAlignedNpuProbe/debug/app-galleryAlignedNpuProbe-debug.apk`
- Existing Gallery/Lami delta artifact:
  `artifacts/gallery_lami_initialization_delta/20260602_064953/`
- Existing Gallery native stack reference:
  `artifacts/gallery_dispatch_requirements/20260516_210635/gallery_stack/`
- Existing API/runtime docs:
  `docs/backend_npu_gallery_lami_initialization_delta.md`,
  `docs/litertlm_gallery_java_api_surface_mismatch.md`,
  `docs/backend_npu_runtime_stack_mismatch_investigation.md`,
  `docs/litert_qairt244_qnn_runtime_alignment_result.md`,
  `docs/litert_qairt244_qnn_status_14001.md`

## Lami evidence by area

| Area | Lami file/line evidence | Gallery delta or unknown | SIGABRT relevance |
| --- | --- | --- | --- |
| Flavor/dependency alignment | `app/build.gradle.kts:40-45` sets `galleryStackExperiment` and `galleryAlignedNpuProbe` to LiteRT-LM `0.11.0`. `app/build.gradle.kts:96-117` defines `.gallerynpu` / `.galleryprobe` with `GALLERY_STACK_EXPERIMENT=true`. | Existing API doc says Gallery JNI descriptor matches Maven `0.11.0`, and the earlier `0.10.0` CheckJNI mismatch is no longer the active failure for aligned variants. | Reduces likelihood that current `SIGABRT` is Java/native `nativeCreateEngine` descriptor mismatch. |
| Source set and native packaging | `app/build.gradle.kts:165-173` wires both Gallery experiment variants to `src/npuExperimentDebug/java`, `src/npuExperimentDebug/AndroidManifest.xml`, and their own `jniLibs`. | Gallery app source layout is not in this repo. Existing artifact compares extracted Gallery stack to Lami APK. | Lami probe code is shared, while native payload differs by source set. Packaging correctness is central. |
| Manifest base | `app/src/main/AndroidManifest.xml:5-18` has normal app attributes and optional `uses-native-library` for `libvndksupport.so` and `libOpenCL.so`. | Current `20260602_064953` Gallery manifest tree is unavailable because the Gallery APK path was missing when the artifact was generated (`<apk-or-aapt-missing>`). Prior docs identify Gallery APK package as `com.google.ai.edge.gallery`, but no current line-level manifest comparison is available. | If Gallery declares vendor/native library visibility beyond Lami's `libvndksupport.so` / `libOpenCL.so`, that could affect FastRPC/CDSP namespace resolution before or during QNN HTP init. |
| Debug probe manifest | `app/src/npuExperimentDebug/AndroidManifest.xml:8-22` adds only exported probe receiver/activity. | No evidence that Gallery has equivalent probe components; these are Lami-only diagnostics. | Probe components should not affect native init except process/component launch context. |
| Merged Lami manifest | `artifacts/gallery_lami_initialization_delta/20260602_064953/lami/AndroidManifest.xmltree.txt` shows package `io.github.ninbyo02.lami.galleryprobe`, `extractNativeLibs=true`, app debuggable, probe receiver/activity, `QairtNpuProbeActivity` and `QnnDirectProbeActivity` in isolated processes, plus optional OpenCL entries. | Gallery merged manifest is unknown in this artifact. | `extractNativeLibs=true` means `applicationInfo.nativeLibraryDir` should be a real extracted directory, matching `Backend.NPU(String nativeLibraryDir)` expectations. Unknown Gallery manifest remains a gap. |
| Assets | Lami APK asset list contains only `assets/sprite_default_settings.json`. The Gallery asset list in the current artifact is empty because Gallery APK files were not available. | Unknown whether Gallery has runtime config/model metadata assets relevant to NPU. | Lower probability unless LiteRT-LM or Gallery wrapper reads app assets outside the `.litertlm` model. Needs re-run with Gallery APK available. |
| Native stack core | `docs/backend_npu_gallery_lami_initialization_delta.md:22-35` and `artifacts/gallery_lami_initialization_delta/20260602_064953/native_lib_delta.tsv` show SHA matches between Gallery reference and Lami aligned APK for `liblitertlm_jni.so`, `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, `libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpPrepare.so`, `libQnnHtpV79Skel.so`, `libQnnHtpV79Stub.so`, and `libllm_inference_engine_jni.so`. | The Gallery side was sourced from `gallery_stack` fallback, not the missing Gallery APK in this run. | Strong evidence that the obvious mixed-stack cause from `npuExperiment` is fixed for `galleryAlignedNpuProbe`. |
| Extra native libraries in Lami APK | `artifacts/gallery_lami_initialization_delta/20260602_064953/lami/native_lib_paths.txt` includes extra Lami packaged libs such as `libLiteRtClGlAccelerator.so`, `libQnnDsp.so`, multiple non-V79 HTP skel/stub libs, `libQnnGpu.so`, `libQnnTFLiteDelegate.so`, `libqnn_delegate_jni.so`, and `libqnn_direct_probe_debug.so`. | Current Gallery APK native inventory is unavailable. The selected core stack matches, but full APK library inventory does not have a valid Gallery comparison in this artifact. | Extra libraries are not a direct mismatch for same-name core libs, but they could alter dlopen search outcomes if QNN probes optional backends or if linker namespace visibility differs. |
| Production EngineConfig path | `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt:4442-4489` builds `EngineConfig(modelPath, backend, visionBackend, audioBackend, maxNumTokens=null, cacheDir=cacheDirPath)`. `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt:4455-4457` maps NPU requests to disabled GPU fallback. | Gallery's exact Java call site is not present locally. Existing API artifact says Gallery/Maven `0.11.0` constructor shape is `EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)`. | Production ChatScreen is intentionally not applying `Backend.NPU`; current NPU crash comes from explicit dev probe, not normal fallback path. |
| Dev Engine.initialize probe | `app/src/npuExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuExperimentProbeActivity.kt:58-96` records run id, phase, model path, variant, cacheDir/max token/image variant, canonical path, and selected nativeLibraryDir. `AcceleratorProbe.kt:1233-1342` gates initialize-only dry-run on explicit opt-in, readable SM8750 model, dispatch presence, successful Backend.NPU and EngineConfig dry-build. | Gallery app behavior around cacheDir/model path/maxNumTokens/maxNumImages is unknown. Existing docs added Gallery-like probe variants. | If all core native libs match, remaining controllable Java-side differences are `cacheDir`, optional max fields, model path spelling, and nativeLibraryDir argument. |
| Dev EngineConfig construction | `AcceleratorProbe.kt:1390-1512` creates `Backend.NPU`, selects an `EngineConfig` constructor, builds args, creates `Engine`, then invokes `Engine.initialize` only. `AcceleratorProbe.kt:1970-2150` maps constructor args and supports variants including `gallery-like-cache`, `gallery-like-max128`, `gallery-like-all`, data/data path, and canonical path. | Existing `docs/backend_npu_gallery_lami_initialization_delta.md:37-45` lists those variants, but no successful variant is documented there. | Repeated SIGABRT across variants would point away from simple Java optional-argument shape and toward native runtime/platform/model constraints. |
| Model path/cacheDir in production resolution | `ChatScreen.kt:5928` uses `context.cacheDir.absolutePath`; `ChatScreen.kt:5977-5992` resolves a readable `.litertlm` path and stores the same cacheDir. | Gallery may copy/download models to a different private location or use `/data/data` vs `/data/user/0` spelling. | Model path spelling has explicit probe variants, but still remains a safe axis because native code may pass paths to lower layers verbatim. |
| Known lower native boundary | `docs/litert_qairt244_qnn_runtime_alignment_result.md` says aligned QNN System/HTP provider selection reached `QnnManager::Init`, then failed at `HtpBackendInit`; `docs/litert_qairt244_qnn_status_14001.md` maps later detail to `libcdsprpc.so` not found from `libQnnHtpV79Stub.so` in an Android linker namespace. | This was from QAIRT/custom diagnostic work, not necessarily the exact Gallery aligned APK. | Highly relevant: if Gallery works on the same device, its manifest/process/linker namespace or installed native layout may provide CDSP/FastRPC visibility that Lami lacks. |

## Current hypotheses

1. **Not the old Java/native descriptor mismatch.** The aligned variants use LiteRT-LM `0.11.0` and existing API evidence says Gallery JNI and Maven `0.11.0` share the `nativeCreateEngine` descriptor.

2. **Not an obvious core native stack SHA mismatch.** The selected Gallery core libraries match Lami aligned APK by SHA in the existing delta artifact. The remaining issue is likely outside the matched library bytes, or in how Android exposes them at runtime.

3. **Manifest/native namespace gap remains open.** Lami's manifest declares optional `libvndksupport.so` and `libOpenCL.so`, but no line-level current Gallery manifest is available. Prior QNN diagnostics found `libcdsprpc.so` unresolved from the HTP V79 stub path. That makes vendor/FastRPC linker namespace visibility a leading candidate.

4. **Full APK native inventory may still differ.** Lami packages extra QNN/DSP/GPU/delegate libraries beyond the matched Gallery core set. They may be benign, but without a current Gallery APK native inventory, this is not closed.

5. **Model/runtime compatibility is still plausible.** The SM8750 `.litertlm` model contains QNN partitions per the existing runtime mismatch report. Even with matching core libs, model schema, SOC detection, VTCM/platform config, signed/secure PD, or FastRPC transport setup can abort during `Engine.initialize`.

6. **Java-side `EngineConfig` optional values are lower priority but not eliminated.** The dev probe already supports cacheDir/max/path variants; if no variant changes the abort boundary, the failure is below `EngineConfig` shape.

## Safe non-destructive tests next

1. Re-run `scripts/investigate_gallery_lami_initialization_delta.sh` with a valid `--gallery-apk` so Gallery manifest, assets, full native inventory, and `extractNativeLibs` are actually captured instead of `<apk-or-aapt-missing>`.

2. Add no code: inspect the already built Lami APK and Gallery APK with `aapt dump xmltree` and `zipinfo -1`, then compare only manifest native-library declarations, meta-data, process declarations, `extractNativeLibs`, and `lib/arm64-v8a` inventories.

3. Use the existing explicit opt-in probe only, with no ChatScreen/S1-S5 path: run `scripts/run_backend_npu_attach_probe.sh` across the existing `gallery-like-cache`, `gallery-like-max128`, `gallery-like-all`, `gallery-like-data-data-path`, and `gallery-like-canonical-path` variants, collecting stage files/tombstones.

4. For any SIGABRT, collect only diagnostics via `scripts/collect_npu_tombstone_diagnostics_v2.sh`; compare mapped libraries and abort message to determine whether the boundary is still dispatch runtime usability, HTP backend init, or CDSP/FastRPC loading.

5. If Gallery APK is runnable on the same device, run a read-only comparison of Gallery logcat/tombstone around successful model load or failure, focusing on `nativeLibraryDir`, mapped `libQnn*`, `libcdsprpc.so`, `QnnDevice_create`, `QnnManager::Init`, and `LiteRtDispatchCheckRuntimeCompatibility`. Do not copy Gallery libs or change Lami settings.

## Conclusion

For `galleryAlignedNpuProbe`, the available evidence closes the two earlier
large deltas: Java/native `0.10.0` descriptor mismatch and selected core native
stack mismatch. The highest-value unresolved deltas are now Android manifest /
linker namespace visibility, full APK native inventory, and runtime platform
conditions around QNN HTP / FastRPC. The missing current Gallery APK metadata is
the main static evidence gap.
