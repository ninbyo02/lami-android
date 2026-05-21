# LiteRT Custom Build Static Summary

- Output: `/home/sato/project/lami-android/artifacts/qairt244_android_log_build/20260521_210911`
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
path=/home/sato/project/lami-android/artifacts/qairt244_android_log_build/20260521_210911/built_libs/libLiteRt.so
label=built
present=true
size=5421464
sha256=1abbc4d2a61b8631af6d9ba8bb6ef9ac5e0fef75fa2e608e6fd13a0b9768944d
build_id=2ab5deef60fa7b8ce78a5e4f4aae5d82
soname=libLiteRt.so
needed=libdl.so,libGLESv3.so,libEGL.so,libm.so,liblog.so,libc.so
/home/sato/project/lami-android/artifacts/qairt244_android_log_build/20260521_210911/built_libs/libLiteRt.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[md5/uuid]=2ab5deef60fa7b8ce78a5e4f4aae5d82, stripped
```

- `LiteRtDispatchGetApi` export: yes

### `libLiteRtCompilerPlugin_Qualcomm.so`

```text
path=/home/sato/project/lami-android/artifacts/qairt244_android_log_build/20260521_210911/built_libs/libLiteRtCompilerPlugin_Qualcomm.so
label=built
present=true
size=1018704
sha256=22ce807533dc659c3f482f6943f2a8b7311869e0a2c61ab8629d15bcaf3d496d
build_id=696d69bb8a9de9988bc5a24efec61a2e
soname=libLiteRtCompilerPlugin_Qualcomm.so
needed=libandroid.so,liblog.so,libdl.so,libc.so,libm.so
/home/sato/project/lami-android/artifacts/qairt244_android_log_build/20260521_210911/built_libs/libLiteRtCompilerPlugin_Qualcomm.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[md5/uuid]=696d69bb8a9de9988bc5a24efec61a2e, stripped
```

- `LiteRtDispatchGetApi` export: no

### `libLiteRtDispatch_Qualcomm.so`

```text
path=/home/sato/project/lami-android/artifacts/qairt244_android_log_build/20260521_210911/built_libs/libLiteRtDispatch_Qualcomm.so
label=built
present=true
size=707568
sha256=ec12f96959b543782d906afc5cc2caa888dc3b29ea2403ff175088d88acdf093
build_id=e249453cf79d19c37af2b2019fea71f1
soname=libLiteRtDispatch_Qualcomm.so
needed=libandroid.so,liblog.so,libdl.so,libc.so,libm.so
/home/sato/project/lami-android/artifacts/qairt244_android_log_build/20260521_210911/built_libs/libLiteRtDispatch_Qualcomm.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[md5/uuid]=e249453cf79d19c37af2b2019fea71f1, stripped
```

- `LiteRtDispatchGetApi` export: yes

### `liblitertlm_jni.so`

```text
path=/home/sato/project/lami-android/artifacts/qairt244_android_log_build/20260521_210911/built_libs/liblitertlm_jni.so
label=built
present=true
size=55158400
sha256=2dd403c7706080499473f4cc21217ccb62494372ba7e8b89a2c56b30aff9b77d
build_id=27bb6eaa5358f3c23f080cdd33023eac
soname=
needed=libGemmaModelConstraintProvider.so,libdl.so,liblog.so,libandroid.so,libGLESv3.so,libEGL.so,libm.so,libc.so
/home/sato/project/lami-android/artifacts/qairt244_android_log_build/20260521_210911/built_libs/liblitertlm_jni.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[md5/uuid]=27bb6eaa5358f3c23f080cdd33023eac, with debug_info, not stripped
```

- `LiteRtDispatchGetApi` export: yes

## Static compare matrix

```text
source	library	present	size	sha256	build_id	soname	needed
built	libLiteRt.so	true	5421464	1abbc4d2a61b8631af6d9ba8bb6ef9ac5e0fef75fa2e608e6fd13a0b9768944d	2ab5deef60fa7b8ce78a5e4f4aae5d82	libLiteRt.so	libdl.so,libGLESv3.so,libEGL.so,libm.so,liblog.so,libc.so
built	libLiteRtRuntimeCApi.so	false	-	-	-	-	-
built	libLiteRtDispatch_Qualcomm.so	true	707568	ec12f96959b543782d906afc5cc2caa888dc3b29ea2403ff175088d88acdf093	e249453cf79d19c37af2b2019fea71f1	libLiteRtDispatch_Qualcomm.so	libandroid.so,liblog.so,libdl.so,libc.so,libm.so
built	liblitertlm_jni.so	true	55158400	2dd403c7706080499473f4cc21217ccb62494372ba7e8b89a2c56b30aff9b77d	27bb6eaa5358f3c23f080cdd33023eac		libGemmaModelConstraintProvider.so,libdl.so,liblog.so,libandroid.so,libGLESv3.so,libEGL.so,libm.so,libc.so
built	libLiteRtCompilerPlugin_Qualcomm.so	true	1018704	22ce807533dc659c3f482f6943f2a8b7311869e0a2c61ab8629d15bcaf3d496d	696d69bb8a9de9988bc5a24efec61a2e	libLiteRtCompilerPlugin_Qualcomm.so	libandroid.so,liblog.so,libdl.so,libc.so,libm.so
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
