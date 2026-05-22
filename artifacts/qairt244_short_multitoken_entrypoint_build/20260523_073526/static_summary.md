# LiteRT Custom Build Static Summary

- Output: `/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526`
- Build executed: limited explicit targets only
- App integration: `no`
- Engine.initialize rerun: `no`

## Target results

```text
@litert//litert/c:litert_runtime_c_api_so	0
@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so	0
//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni	0
@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so	0
```

## Built libraries

```text
libLiteRt.so
libLiteRtCompilerPlugin_Qualcomm.so
libLiteRtDispatch_Qualcomm.so
liblitertlm_jni.so
```

## Built library metadata

### `libLiteRt.so`

```text
path=/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/built_libs/libLiteRt.so
label=built
present=true
size=5405080
sha256=84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553
build_id=a03032ad1eeefda446478aea308c2ed0
soname=libLiteRt.so
needed=libdl.so,libGLESv3.so,libEGL.so,libm.so,liblog.so,libc.so
/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/built_libs/libLiteRt.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[md5/uuid]=a03032ad1eeefda446478aea308c2ed0, stripped
```

- `LiteRtDispatchGetApi` export: yes

### `libLiteRtCompilerPlugin_Qualcomm.so`

```text
path=/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/built_libs/libLiteRtCompilerPlugin_Qualcomm.so
label=built
present=true
size=1002320
sha256=c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c
build_id=443391d4c4348191230b67a3ab8a6037
soname=libLiteRtCompilerPlugin_Qualcomm.so
needed=libandroid.so,liblog.so,libdl.so,libc.so,libm.so
/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/built_libs/libLiteRtCompilerPlugin_Qualcomm.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[md5/uuid]=443391d4c4348191230b67a3ab8a6037, stripped
```

- `LiteRtDispatchGetApi` export: no

### `libLiteRtDispatch_Qualcomm.so`

```text
path=/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/built_libs/libLiteRtDispatch_Qualcomm.so
label=built
present=true
size=691184
sha256=7d3d37cb13cf88fc679ea8d07d271865db36e2f6f6eab80e3a1d02783000c34f
build_id=283f860170c8b970f14db885eab73a95
soname=libLiteRtDispatch_Qualcomm.so
needed=libLiteRt.so,libandroid.so,liblog.so,libdl.so,libc.so,libm.so
/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/built_libs/libLiteRtDispatch_Qualcomm.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[md5/uuid]=283f860170c8b970f14db885eab73a95, stripped
```

- `LiteRtDispatchGetApi` export: yes

### `liblitertlm_jni.so`

```text
path=/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/built_libs/liblitertlm_jni.so
label=built
present=true
size=55172880
sha256=32b58ee5b5c16e181897d10cd392fff0695c562bf4b578968387f20d6a80ff0a
build_id=bb6f8924e466e7039a1f54d7170a2eb2
soname=
needed=libGemmaModelConstraintProvider.so,libdl.so,liblog.so,libandroid.so,libGLESv3.so,libEGL.so,libm.so,libc.so
/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526/built_libs/liblitertlm_jni.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[md5/uuid]=bb6f8924e466e7039a1f54d7170a2eb2, with debug_info, not stripped
```

- `LiteRtDispatchGetApi` export: yes

## Static compare matrix

```text
source	library	present	size	sha256	build_id	soname	needed
built	libLiteRt.so	true	5405080	84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553	a03032ad1eeefda446478aea308c2ed0	libLiteRt.so	libdl.so,libGLESv3.so,libEGL.so,libm.so,liblog.so,libc.so
built	libLiteRtRuntimeCApi.so	false	-	-	-	-	-
built	libLiteRtDispatch_Qualcomm.so	true	691184	7d3d37cb13cf88fc679ea8d07d271865db36e2f6f6eab80e3a1d02783000c34f	283f860170c8b970f14db885eab73a95	libLiteRtDispatch_Qualcomm.so	libLiteRt.so,libandroid.so,liblog.so,libdl.so,libc.so,libm.so
built	liblitertlm_jni.so	true	55172880	32b58ee5b5c16e181897d10cd392fff0695c562bf4b578968387f20d6a80ff0a	bb6f8924e466e7039a1f54d7170a2eb2		libGemmaModelConstraintProvider.so,libdl.so,liblog.so,libandroid.so,libGLESv3.so,libEGL.so,libm.so,libc.so
built	libLiteRtCompilerPlugin_Qualcomm.so	true	1002320	c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c	443391d4c4348191230b67a3ab8a6037	libLiteRtCompilerPlugin_Qualcomm.so	libandroid.so,liblog.so,libdl.so,libc.so,libm.so
built	libQnnSystem.so	false	-	-	-	-	-
built	libQnnHtp.so	false	-	-	-	-	-
built	libQnnHtpPrepare.so	false	-	-	-	-	-
built	libQnnHtpV79Stub.so	false	-	-	-	-	-
built	libQnnHtpV79Skel.so	false	-	-	-	-	-
built	libllm_inference_engine_jni.so	false	-	-	-	-	-
gallery-sm8750	libLiteRt.so	false	-	-	-	-	-
gallery-sm8750	libLiteRtRuntimeCApi.so	false	-	-	-	-	-
gallery-sm8750	libLiteRtDispatch_Qualcomm.so	false	-	-	-	-	-
gallery-sm8750	liblitertlm_jni.so	false	-	-	-	-	-
gallery-sm8750	libLiteRtCompilerPlugin_Qualcomm.so	false	-	-	-	-	-
gallery-sm8750	libQnnSystem.so	false	-	-	-	-	-
gallery-sm8750	libQnnHtp.so	false	-	-	-	-	-
gallery-sm8750	libQnnHtpPrepare.so	false	-	-	-	-	-
gallery-sm8750	libQnnHtpV79Stub.so	false	-	-	-	-	-
gallery-sm8750	libQnnHtpV79Skel.so	false	-	-	-	-	-
gallery-sm8750	libllm_inference_engine_jni.so	false	-	-	-	-	-
galleryStackExperimentDebug	libLiteRt.so	true	4964616	146f699ef6822a1e1f9489101a9dc5733e3788643396cab4fc768063cfde346c	869121bd7f4b0b77fa581218117a5c14	libLiteRt.so	libdl.so,libGLESv3.so,libEGL.so,libm.so,liblog.so,libc.so
galleryStackExperimentDebug	libLiteRtRuntimeCApi.so	false	-	-	-	-	-
galleryStackExperimentDebug	libLiteRtDispatch_Qualcomm.so	true	446088	92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777	643ad77b8ac2f54bd1b61e4133c77b3a	libLiteRtDispatch_Qualcomm.so	libLiteRt.so,libandroid.so,liblog.so,libdl.so,libc.so,libm.so
galleryStackExperimentDebug	liblitertlm_jni.so	true	19063832	607c4af2d405ff53a2a01415b47e202594b4e0dcce7f08f270bdfa7dd900c6d7	76e4dccd9c5f9cba468d9cae7becfec0	liblitertlm_jni.so	libdl.so,libm.so,libEGL.so,libGLESv2.so,libGLESv3.so,libandroid.so,liblog.so,libc.so
galleryStackExperimentDebug	libLiteRtCompilerPlugin_Qualcomm.so	false	-	-	-	-	-
galleryStackExperimentDebug	libQnnSystem.so	true	2983560	7e69258e1278cc9b2bb62dbc6e2a52c227a100d6505a13fd6324a87993d0bba8	0d409cdd664b8b0a	libQnnSystem.so	libc.so,libm.so,libdl.so,liblog.so
galleryStackExperimentDebug	libQnnHtp.so	true	2778176	090e993822564851eab1405aff171643b21e644e3f696c95c96f2732aaed813a	f2c90c1775a109e1	libQnnHtp.so	libc.so,libm.so,libdl.so,liblog.so
galleryStackExperimentDebug	libQnnHtpPrepare.so	true	52389312	b178fcb21b68062e7b7aa7a0531a65f194ecc6dcaba9ad9b0b4ef8d54bced21b	9ae62cf17f972404	libQnnHtpPrepare.so	libc.so,libm.so,libdl.so,liblog.so
galleryStackExperimentDebug	libQnnHtpV79Stub.so	true	679168	005bd3de462851ce3dde55260d7d8560d6d07dbc309f554780b1f6412e6d9df1	10d7ad6f9195411a	libQnnHtpV79Stub.so	libc.so,libm.so,libdl.so,liblog.so,libcdsprpc.so
galleryStackExperimentDebug	libQnnHtpV79Skel.so	true	10975268	41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98		libQnnHtpV79Skel.so	libc++.so.1,libc++abi.so.1
galleryStackExperimentDebug	libllm_inference_engine_jni.so	true	26422184	51b27a87a4723172b2661d68c03de01d73aaea71909c9dd2b2c8d1ef149ca1f8	2f6f9104344966674bf6587935d27cc8	libllm_inference_engine_jni.so	libandroid.so,libGLESv2.so,libEGL.so,libz.so,libGLESv3.so,libdl.so,libm.so,liblog.so,libc.so
maven-litertlm-0.11.0	libLiteRt.so	true	5046960	31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24	80fa0688ac32301185275c903cec97bd	libLiteRt.so	libdl.so,libGLESv3.so,libEGL.so,libm.so,liblog.so,libc.so
maven-litertlm-0.11.0	libLiteRtRuntimeCApi.so	false	-	-	-	-	-
maven-litertlm-0.11.0	libLiteRtDispatch_Qualcomm.so	false	-	-	-	-	-
maven-litertlm-0.11.0	liblitertlm_jni.so	true	15370288	ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f	c2c27170ba409dbd0bc01820fa738580	liblitertlm_jni.so	libLiteRt.so,libandroid.so,libz.so,libGLESv2.so,libEGL.so,libdl.so,libGLESv3.so,libm.so,liblog.so,libc.so
maven-litertlm-0.11.0	libLiteRtCompilerPlugin_Qualcomm.so	false	-	-	-	-	-
maven-litertlm-0.11.0	libQnnSystem.so	false	-	-	-	-	-
maven-litertlm-0.11.0	libQnnHtp.so	false	-	-	-	-	-
maven-litertlm-0.11.0	libQnnHtpPrepare.so	false	-	-	-	-	-
maven-litertlm-0.11.0	libQnnHtpV79Stub.so	false	-	-	-	-	-
maven-litertlm-0.11.0	libQnnHtpV79Skel.so	false	-	-	-	-	-
maven-litertlm-0.11.0	libllm_inference_engine_jni.so	false	-	-	-	-	-
```
