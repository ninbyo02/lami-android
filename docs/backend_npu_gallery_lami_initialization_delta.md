# Gallery vs Lami Backend.NPU initialization delta

- generated_at: `20260602_064953`
- gallery_apk: `/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk`
- gallery_lib_dir: `/home/sato/project/lami-android/artifacts/gallery_dispatch_requirements/20260516_210635/gallery_stack`
- lami_apk: `/home/sato/project/lami-android/app/build/outputs/apk/galleryAlignedNpuProbe/debug/app-galleryAlignedNpuProbe-debug.apk`
- scope: static investigation only; no install, no Engine.initialize, no library replacement

## Difference table

| Area | Gallery evidence | Lami galleryAlignedNpuProbe evidence | Next risk to test |
| --- | --- | --- | --- |
| AndroidManifest.xml | `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/gallery/manifest_key_lines.txt` | `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/lami/manifest_key_lines.txt` | compare `extractNativeLibs`, package namespace, app attributes |
| permissions/meta-data/uses-library | `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/gallery/manifest_key_lines.txt` | `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/lami/manifest_key_lines.txt` | detect Gallery-only manifest contract or service/provider setup |
| assets/config files | `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/gallery/assets.txt` | `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/lami/assets.txt` | detect Gallery-only runtime config or model metadata assets |
| native libs | `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/gallery/native_lib_paths.txt` | `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/lami/native_lib_paths.txt` | verify all runtime libs are present as a stack, not partial swaps |
| Engine.initialize call shape | `Backend.NPU(String nativeLibraryDir); EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String); Engine.initialize()` from dex surface artifacts | same API shape through reflection probe | vary cacheDir, maxNumTokens, maxNumImages, model path spelling |
| optional libs | `gallery=false,lami=false`; `gallery=false,lami=false` | see `native_lib_delta.tsv` | if Gallery APK is unavailable this is based on extracted artifact dir only |

## Native library delta

```text
library	gallery_exists	gallery_source	gallery_build_id	gallery_sha256	lami_exists	lami_source	lami_build_id	lami_sha256	sha256_match
liblitertlm_jni.so	true	gallery-lib-dir	76e4dccd9c5f9cba468d9cae7becfec0	607c4af2d405ff53a2a01415b47e202594b4e0dcce7f08f270bdfa7dd900c6d7	true	lami-apk	76e4dccd9c5f9cba468d9cae7becfec0	607c4af2d405ff53a2a01415b47e202594b4e0dcce7f08f270bdfa7dd900c6d7	true
libLiteRt.so	true	gallery-lib-dir	869121bd7f4b0b77fa581218117a5c14	146f699ef6822a1e1f9489101a9dc5733e3788643396cab4fc768063cfde346c	true	lami-apk	869121bd7f4b0b77fa581218117a5c14	146f699ef6822a1e1f9489101a9dc5733e3788643396cab4fc768063cfde346c	true
libLiteRtDispatch_Qualcomm.so	true	gallery-lib-dir	643ad77b8ac2f54bd1b61e4133c77b3a	92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777	true	lami-apk	643ad77b8ac2f54bd1b61e4133c77b3a	92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777	true
libLiteRtCompilerPlugin_Qualcomm.so	false	missing	-	-	false	missing	-	-	false
libQnnHtp.so	true	gallery-lib-dir	f2c90c1775a109e1	090e993822564851eab1405aff171643b21e644e3f696c95c96f2732aaed813a	true	lami-apk	f2c90c1775a109e1	090e993822564851eab1405aff171643b21e644e3f696c95c96f2732aaed813a	true
libQnnSystem.so	true	gallery-lib-dir	0d409cdd664b8b0a	7e69258e1278cc9b2bb62dbc6e2a52c227a100d6505a13fd6324a87993d0bba8	true	lami-apk	0d409cdd664b8b0a	7e69258e1278cc9b2bb62dbc6e2a52c227a100d6505a13fd6324a87993d0bba8	true
libQnnHtpPrepare.so	true	gallery-lib-dir	9ae62cf17f972404	b178fcb21b68062e7b7aa7a0531a65f194ecc6dcaba9ad9b0b4ef8d54bced21b	true	lami-apk	9ae62cf17f972404	b178fcb21b68062e7b7aa7a0531a65f194ecc6dcaba9ad9b0b4ef8d54bced21b	true
libQnnHtpV79Skel.so	true	gallery-lib-dir		41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98	true	lami-apk		41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98	true
libQnnHtpV79Stub.so	true	gallery-lib-dir	10d7ad6f9195411a	005bd3de462851ce3dde55260d7d8560d6d07dbc309f554780b1f6412e6d9df1	true	lami-apk	10d7ad6f9195411a	005bd3de462851ce3dde55260d7d8560d6d07dbc309f554780b1f6412e6d9df1	true
libGemmaModelConstraintProvider.so	false	missing	-	-	false	missing	-	-	false
libllm_inference_engine_jni.so	true	gallery-lib-dir	2f6f9104344966674bf6587935d27cc8	51b27a87a4723172b2661d68c03de01d73aaea71909c9dd2b2c8d1ef149ca1f8	true	lami-apk	2f6f9104344966674bf6587935d27cc8	51b27a87a4723172b2661d68c03de01d73aaea71909c9dd2b2c8d1ef149ca1f8	true
```

## Added probe variants

| Variant | cacheDir | maxNumTokens | maxNumImages | modelPath handling |
| --- | --- | --- | --- | --- |
| `gallery-like-cache` | `context.cacheDir` | `null` | `null` | unchanged |
| `gallery-like-max128` | `null` | `128` | `null` | unchanged |
| `gallery-like-all` | `context.cacheDir` | `128` | `1` | unchanged |
| `gallery-like-data-data-path` | `null` | `null` | `null` | `/data/user/0/<pkg>/...` rewritten to `/data/data/<pkg>/...` inside app |
| `gallery-like-canonical-path` | `null` | `null` | `null` | app uses `File(modelPath).canonicalPath` |

## Files

- `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/native_lib_delta.tsv`
- `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/gallery/AndroidManifest.xmltree.txt`
- `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/lami/AndroidManifest.xmltree.txt`
- `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/gallery/apk_files.txt`
- `/home/sato/project/lami-android/artifacts/gallery_lami_initialization_delta/20260602_064953/lami/apk_files.txt`
