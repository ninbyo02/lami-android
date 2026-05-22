# NPU Engine.initialize crash summary

- applicationId: `io.github.ninbyo02.lami.customnpu`
- label: `customnpu-short-multitoken`
- runId: `1779493804631`
- tombstone selection: `latest-tombstone-matches-app`
- device: `NX733J`
- final stage: `1779478486470 runId=1779478487993_2 Engine.initialize invoking method=Engine.initialize(): void`
- process alive after probe: `26465`
- process line: `Cmdline: io.github.ninbyo02.lami.customnpu`
- signal: `signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------`
- abort message: `not-found`
- likely abort/register/log text: `register-fragments: Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
- classification: `no-usable-dispatch-runtime`
- confidence: `medium`
- recommended next action: Report upstream with Gallery stack Build IDs; next compare exact Gallery source/tag or dispatch runtime compatibility.

## Backtrace Summary

```text
      #00 pc 000000000007128c  /apex/com.android.runtime/lib64/bionic/libc.so (abort+160) (BuildId: abd5b78d2ef1ec5229362b83244a7f5a)
      #01 pc 000000000122d18c  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so ((anonymous namespace)::DispatchDelegate::CreateDelegateKernelInterface()+544) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #02 pc 0000000001239950  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::(anonymous namespace)::CreateDelegateKernelRegistration(tflite::SimpleOpaqueDelegateInterface*)::$_1::__invoke(void*, TfLiteOpaqueContext*, char const*, unsigned long)+36) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #03 pc 00000000014d73d4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::AddNodeWithParameters(std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, char const*, unsigned long, void*, TfLiteRegistration const*, int*)+1240) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #04 pc 00000000014d6ca8  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ReplaceNodeSubsetsWithDelegateKernels(TfLiteRegistration, TfLiteIntArray const*, TfLiteDelegate*)+996) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #05 pc 00000000014d68b4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ReplaceNodeSubsetsWithDelegateKernels(TfLiteContext*, TfLiteRegistration, TfLiteIntArray const*, TfLiteDelegate*)+48) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #06 pc 000000000126c1f8  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (TfLiteOpaqueContextReplaceNodeSubsetsWithDelegateKernels+36) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #07 pc 000000000123985c  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::(anonymous namespace)::DelegatePrepare(TfLiteOpaqueContext*, TfLiteDelegate*, void*)+320) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #08 pc 00000000014dc6f0  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ModifyGraphWithDelegateImpl(TfLiteDelegate*)+500) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #09 pc 00000000014dd594  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ModifyGraphWithDelegate(TfLiteDelegate*)+12) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #10 pc 00000000014cce20  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::impl::Interpreter::ModifyGraphWithDelegateImpl(TfLiteDelegate*)+80) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #11 pc 00000000011fa7b4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (LiteRtCompiledModelT::Create(LiteRtEnvironmentT*, LiteRtModelT*, LiteRtOptionsT*)+1068) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #12 pc 00000000011f5548  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (LiteRtCreateCompiledModel+96) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #13 pc 0000000000d386b8  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::CompiledModel::Create(litert::Environment&, LiteRtModelT*, litert::Options&)+156) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #14 pc 0000000000da18ac  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::LlmLiteRtNpuCompiledModelExecutor::CreateForModelHasPerLayerEmbedding(litert::lm::LlmExecutorSettings const&, litert::lm::ModelResources&, litert::Environment&, litert::Model const*, litert::lm::LlmLiteRtNpuCompiledModelExecutor::LogitsQuantizationParams)+140) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #15 pc 0000000000da1704  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::LlmLiteRtNpuCompiledModelExecutor::Create(litert::lm::LlmExecutorSettings const&, litert::lm::ModelResources&, litert::Environment&)+744) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #16 pc 0000000000d4dc38  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::CreateLlmLiteRtCompiledModelExecutor(litert::lm::LlmExecutorSettings, litert::Environment&, litert::lm::ModelResources&)+296) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #17 pc 0000000000d1dacc  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Engine, std::__ndk1::default_delete<litert::lm::Engine>>> absl::lts_20260107::internal_any_invocable::LocalInvoker<false, absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Engine, std::__ndk1::default_delete<litert::lm::Engine>>>, litert::lm::(anonymous namespace)::$_0&, litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>>(absl::lts_20260107::internal_any_invocable::TypeErasedState*, absl::lts_20260107::internal_any_invocable::ForwardedParameter<litert::lm::EngineSettings>::type, absl::lts_20260107::internal_any_invocable::ForwardedParameter<std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>>::type)+1432) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #18 pc 00000000009b29f4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::EngineFactory::Create(litert::lm::EngineFactory::EngineType, litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+112) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #19 pc 00000000009ade3c  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::EngineFactory::CreateDefault(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+440) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #20 pc 00000000009ad3bc  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+2752) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #21 pc 00000000002c2100  /apex/com.android.art/lib64/libart.so (art_quick_generic_jni_trampoline+144) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #22 pc 00000000002aad94  /apex/com.android.art/lib64/libart.so (art_quick_invoke_stub+612) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #23 pc 00000000002707ac  /apex/com.android.art/lib64/libart.so (art::ArtMethod::Invoke(art::Thread*, unsigned int*, unsigned int, art::JValue*, char const*)+220) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #24 pc 000000000051cd84  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<true>(art::ArtMethod*, art::Thread*, art::ShadowFrame&, art::Instruction const*, unsigned short, bool, art::JValue*)+2008) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #25 pc 0000000000492ab0  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>(art::interpreter::SwitchImplContext*)+8480) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #26 pc 00000000003d12b8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #27 pc 0000000000ffea98  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/base.apk (com.google.ai.edge.litertlm.Engine.initialize+0)
      #28 pc 00000000003d0f18  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute(art::Thread*, art::CodeItemDataAccessor const&, art::ShadowFrame&, art::JValue, bool, bool) (.__uniq.112435418011751916792819755956732575238.llvm.4073957672844042480)+364) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #29 pc 00000000003d064c  /apex/com.android.art/lib64/libart.so (artQuickToInterpreterBridge+1020) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #30 pc 00000000002c2238  /apex/com.android.art/lib64/libart.so (art_quick_to_interpreter_bridge+88) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #31 pc 00000000002aad94  /apex/com.android.art/lib64/libart.so (art_quick_invoke_stub+612) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #32 pc 00000000002a1320  /apex/com.android.art/lib64/libart.so (_jobject* art::InvokeMethod<(art::PointerSize)8>(art::ScopedObjectAccessAlreadyRunnable const&, _jobject*, _jobject*, _jobject*, unsigned long)+936) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #33 pc 00000000005ac718  /apex/com.android.art/lib64/libart.so (art::Method_invoke(_JNIEnv*, _jobject*, _jobject*, _jobjectArray*) (.__uniq.165753521025965369065708152063621506277)+32) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #34 pc 00000000000f6070  [anon_shmem:dalvik-jit-code-cache] (offset 0x2000000) (art_jni_trampoline+144)
      #35 pc 00000000002aad94  /apex/com.android.art/lib64/libart.so (art_quick_invoke_stub+612) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #36 pc 0000000000586548  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>(art::ArtMethod*, art::Thread*, art::ShadowFrame&, art::Instruction const*, unsigned short, bool, art::JValue*)+2688) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #37 pc 0000000000490f5c  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>(art::interpreter::SwitchImplContext*)+1484) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #38 pc 00000000003d12b8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #39 pc 00000000000a0870  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/base.apk (offset 0x502e000) (io.github.ninbyo02.lami.ui.screens.home.AcceleratorProbe.invokeEngineInitializeOperation+0)
      #40 pc 00000000003d1e84  /apex/com.android.art/lib64/libart.so (art::interpreter::ArtInterpreterToInterpreterBridge(art::Thread*, art::CodeItemDataAccessor const&, art::ShadowFrame*, art::JValue*)+448) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #41 pc 000000000051cbac  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<true>(art::ArtMethod*, art::Thread*, art::ShadowFrame&, art::Instruction const*, unsigned short, bool, art::JValue*)+1536) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #42 pc 0000000000492ab0  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>(art::interpreter::SwitchImplContext*)+8480) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #43 pc 00000000003d12b8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #44 pc 000000000009e988  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/base.apk (offset 0x502e000) (io.github.ninbyo02.lami.ui.screens.home.AcceleratorProbe.probeEngineInitializeDryRunSafely+0)
```

## Loaded Libs Summary

```text
      #01 pc 000000000122d18c  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so ((anonymous namespace)::DispatchDelegate::CreateDelegateKernelInterface()+544) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #02 pc 0000000001239950  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::(anonymous namespace)::CreateDelegateKernelRegistration(tflite::SimpleOpaqueDelegateInterface*)::$_1::__invoke(void*, TfLiteOpaqueContext*, char const*, unsigned long)+36) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #03 pc 00000000014d73d4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::AddNodeWithParameters(std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, char const*, unsigned long, void*, TfLiteRegistration const*, int*)+1240) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #04 pc 00000000014d6ca8  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ReplaceNodeSubsetsWithDelegateKernels(TfLiteRegistration, TfLiteIntArray const*, TfLiteDelegate*)+996) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #05 pc 00000000014d68b4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ReplaceNodeSubsetsWithDelegateKernels(TfLiteContext*, TfLiteRegistration, TfLiteIntArray const*, TfLiteDelegate*)+48) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #06 pc 000000000126c1f8  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (TfLiteOpaqueContextReplaceNodeSubsetsWithDelegateKernels+36) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #07 pc 000000000123985c  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::(anonymous namespace)::DelegatePrepare(TfLiteOpaqueContext*, TfLiteDelegate*, void*)+320) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #08 pc 00000000014dc6f0  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ModifyGraphWithDelegateImpl(TfLiteDelegate*)+500) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #09 pc 00000000014dd594  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ModifyGraphWithDelegate(TfLiteDelegate*)+12) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #10 pc 00000000014cce20  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (tflite::impl::Interpreter::ModifyGraphWithDelegateImpl(TfLiteDelegate*)+80) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #11 pc 00000000011fa7b4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (LiteRtCompiledModelT::Create(LiteRtEnvironmentT*, LiteRtModelT*, LiteRtOptionsT*)+1068) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #12 pc 00000000011f5548  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (LiteRtCreateCompiledModel+96) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #13 pc 0000000000d386b8  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::CompiledModel::Create(litert::Environment&, LiteRtModelT*, litert::Options&)+156) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #14 pc 0000000000da18ac  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::LlmLiteRtNpuCompiledModelExecutor::CreateForModelHasPerLayerEmbedding(litert::lm::LlmExecutorSettings const&, litert::lm::ModelResources&, litert::Environment&, litert::Model const*, litert::lm::LlmLiteRtNpuCompiledModelExecutor::LogitsQuantizationParams)+140) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #15 pc 0000000000da1704  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::LlmLiteRtNpuCompiledModelExecutor::Create(litert::lm::LlmExecutorSettings const&, litert::lm::ModelResources&, litert::Environment&)+744) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #16 pc 0000000000d4dc38  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::CreateLlmLiteRtCompiledModelExecutor(litert::lm::LlmExecutorSettings, litert::Environment&, litert::lm::ModelResources&)+296) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #17 pc 0000000000d1dacc  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Engine, std::__ndk1::default_delete<litert::lm::Engine>>> absl::lts_20260107::internal_any_invocable::LocalInvoker<false, absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Engine, std::__ndk1::default_delete<litert::lm::Engine>>>, litert::lm::(anonymous namespace)::$_0&, litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>>(absl::lts_20260107::internal_any_invocable::TypeErasedState*, absl::lts_20260107::internal_any_invocable::ForwardedParameter<litert::lm::EngineSettings>::type, absl::lts_20260107::internal_any_invocable::ForwardedParameter<std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>>::type)+1432) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #18 pc 00000000009b29f4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::EngineFactory::Create(litert::lm::EngineFactory::EngineType, litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+112) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #19 pc 00000000009ade3c  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::EngineFactory::CreateDefault(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+440) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #20 pc 00000000009ad3bc  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+2752) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
    0000006d'682c1000-0000006d'6838ffff r--         0     cf000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libQnnHtp.so (BuildId: f2c90c1775a109e1) (load bias 0x4000)
    0000006d'68393000-0000006d'68560fff r-x     ce000    1ce000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libQnnHtp.so (BuildId: f2c90c1775a109e1) (load bias 0x4000)
    0000006d'68564000-0000006d'6856efff r--    29b000      b000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libQnnHtp.so (BuildId: f2c90c1775a109e1) (load bias 0x4000)
    0000006d'68572000-0000006d'68572fff rw-    2a5000      1000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libQnnHtp.so (BuildId: f2c90c1775a109e1) (load bias 0x4000)
    0000006d'6ae3b000-0000006d'6af01fff r--         0     c7000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libQnnSystem.so (BuildId: 0d409cdd664b8b0a) (load bias 0x4000)
    0000006d'6af05000-0000006d'6b106fff r-x     c6000    202000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libQnnSystem.so (BuildId: 0d409cdd664b8b0a) (load bias 0x4000)
    0000006d'6b10a000-0000006d'6b11afff r--    2c7000     11000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libQnnSystem.so (BuildId: 0d409cdd664b8b0a) (load bias 0x4000)
    0000006d'6b11e000-0000006d'6b11efff rw-    2d7000      1000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libQnnSystem.so (BuildId: 0d409cdd664b8b0a) (load bias 0x4000)
    0000006d'6c45f000-0000006d'6c972fff r-x         0    514000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libLiteRt.so (BuildId: 731b74da505bef341a184b3778d0412d)
    0000006d'6c973000-0000006d'6c97ffff r--    514000      d000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libLiteRt.so (BuildId: 731b74da505bef341a184b3778d0412d)
    0000006d'6c983000-0000006d'6c989fff rw-    524000      7000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libLiteRt.so (BuildId: 731b74da505bef341a184b3778d0412d)
    0000006d'b764e000-0000006d'b8de5fff r-x         0   1798000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
    0000006d'b8de6000-0000006d'b8ecdfff r--   1798000     e8000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
    0000006d'b8ece000-0000006d'b8ee7fff rw-   187f000     1a000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
    0000006d'bba2e000-0000006d'bd298fff r-x         0   186b000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libllm_inference_engine_jni.so (BuildId: 2f6f9104344966674bf6587935d27cc8)
    0000006d'bd29a000-0000006d'bd351fff r--   186c000     b8000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libllm_inference_engine_jni.so (BuildId: 2f6f9104344966674bf6587935d27cc8)
    0000006d'bd355000-0000006d'bd363fff rw-   1923000      f000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libllm_inference_engine_jni.so (BuildId: 2f6f9104344966674bf6587935d27cc8)
    0000006d'bda8d000-0000006d'bdb30fff r-x         0     a4000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libLiteRtDispatch_Qualcomm.so (BuildId: a1b66b12e643f15a94cb34093f9efcac)
    0000006d'bdb31000-0000006d'bdb37fff r--     a4000      7000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libLiteRtDispatch_Qualcomm.so (BuildId: a1b66b12e643f15a94cb34093f9efcac)
    0000006d'bdb39000-0000006d'bdb39fff rw-     ac000      1000  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/libLiteRtDispatch_Qualcomm.so (BuildId: a1b66b12e643f15a94cb34093f9efcac)
      #03 pc 00000000017631b8  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (operator new(unsigned long)+28) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #04 pc 00000000011a6360  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (google::protobuf::MessageLite* google::protobuf::internal::MessageCreator::New<google::protobuf::MessageLite>(google::protobuf::MessageLite const*, google::protobuf::MessageLite const*, google::protobuf::Arena*) const+60) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #05 pc 000000000119c0c8  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (google::protobuf::internal::TcParser::AddMessage(google::protobuf::internal::TcParseTableBase const*, google::protobuf::internal::RepeatedPtrFieldBase&)+188) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #06 pc 000000000119d050  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (google::protobuf::internal::TcParser::FastMtR1(google::protobuf::MessageLite*, char const*, google::protobuf::internal::ParseContext*, google::protobuf::internal::TcFieldData, google::protobuf::internal::TcParseTableBase const*, unsigned long)+68) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #07 pc 00000000011adbb4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (bool google::protobuf::internal::MergeFromImpl<false>(std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>, google::protobuf::MessageLite*, google::protobuf::internal::TcParseTableBase const*, google::protobuf::MessageLite::ParseFlags)+56) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #08 pc 00000000011aed88  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (google::protobuf::MessageLite::ParseFromArray(void const*, int)+16) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #09 pc 00000000010edba0  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (sentencepiece::SentencePieceProcessor::LoadFromSerializedProto(std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+52) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #10 pc 00000000010e4924  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::SentencePieceTokenizer::CreateFromBuffer(std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+28) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #11 pc 0000000000db05bc  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::ModelResourcesLitertLm::GetTokenizer()+60) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #12 pc 0000000000d1e934  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (litert::lm::(anonymous namespace)::EngineImpl::Create(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)::$_0::operator()() const+56) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #13 pc 0000000000d1eed4  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (std::__ndk1::__async_assoc_state<absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Tokenizer, std::__ndk1::default_delete<litert::lm::Tokenizer>>>, std::__ndk1::__async_func<litert::lm::(anonymous namespace)::EngineImpl::Create(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)::$_0>>::__execute()+16) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
      #14 pc 0000000000d1f134  /data/app/~~8U8kH4ycaxO3ZXP74xJjTw==/io.github.ninbyo02.lami.customnpu-11bnsl7aHBOSdspsEkxtyw==/lib/arm64/liblitertlm_jni.so (void* std::__ndk1::__thread_proxy[abi:ne190000]<std::__ndk1::tuple<std::__ndk1::unique_ptr<std::__ndk1::__thread_struct, std::__ndk1::default_delete<std::__ndk1::__thread_struct>>, void (std::__ndk1::__async_assoc_state<absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Tokenizer, std::__ndk1::default_delete<litert::lm::Tokenizer>>>, std::__ndk1::__async_func<litert::lm::(anonymous namespace)::EngineImpl::Create(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)::$_0>>::*)(), std::__ndk1::__async_assoc_state<absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Tokenizer, std::__ndk1::default_delete<litert::lm::Tokenizer>>>, std::__ndk1::__async_func<litert::lm::(anonymous namespace)::EngineImpl::Create(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)::$_0>>*>>(void*)+56) (BuildId: 8554bcd057031088ad9bb2100f1f8f94)
```

## Loaded Libs Matrix

# Loaded native library summary

- applicationId: `io.github.ninbyo02.lami.customnpu`

| Library | Mapped in tombstone | Present in nativeLibraryDir/APK | Build ID |
| --- | --- | --- | --- |
| `liblitertlm_jni.so` | true | true | `bb6f8924e466e7039a1f54d7170a2eb2` |
| `libLiteRt.so` | true | true | `a03032ad1eeefda446478aea308c2ed0` |
| `libLiteRtDispatch_Qualcomm.so` | true | true | `283f860170c8b970f14db885eab73a95` |
| `libQnnSystem.so` | true | true | `0d409cdd664b8b0a` |
| `libQnnHtp.so` | true | true | `f2c90c1775a109e1` |
| `libQnnHtpPrepare.so` | false | true | `edb612e67d6d27c2` |
| `libQnnHtpV79Stub.so` | false | true | `10d7ad6f9195411a` |
| `libQnnHtpV79Skel.so` | false | true | `` |
| `libLiteRtRuntimeCApi.so` | false | false | `-` |
| `libllm_inference_engine_jni.so` | true | true | `2f6f9104344966674bf6587935d27cc8` |


## Abort Text Candidates

```text
## Direct strings

```text
/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_smoke/20260523_085004/diagnostics/tombstone_app_extract.txt:QnnHtp.so (BuildId: f2c90c1775a109e1) (load bias 0x4000)
/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_smoke/20260523_085004/diagnostics/tombstone_app_extract.txt:QnnSystem.so (BuildId: 0d409cdd664b8b0a) (load bias 0x4000)
/home/sato/project/lami-android/artifacts/qairt244_short_multitoken_smoke/20260523_085004/diagnostics/tombstone_app_extract.txt:adsprpc_prop:s0
```

## Register ASCII fragments

```text
x4	64656c696146205d	] Failed
x5	64656c696146205d	] Failed
x6	64656c696146205d	] Failed
x7	61657263206f7420	 to crea
x12	69746e7572206863	ch runti
x13	646e756f6620656d	me found
x14	6b2065746167656c	legate k
x15	4e203a6c656e7265	ernel: N
x6	75687469672e6f69	io.githu
x7	6f79626e696e2e62	b.ninbyo
x13	75706e6d6f747375	ustomnpu
x18	0000006e53564000	.@VSn...
x18	0000006e52488000	..HRn...
x25	0000006eb16a3868	h8j.n...
x18	0000006e4d48c000	..HMn...
x23	0000006e4475a800	..uDn...
x24	0000006e4475a880	..uDn...
x27	0000006e44662000	. fDn...
x28	0000006e4465e000	..eDn...
x29	0000006e4475a3a0	..uDn...
x23	0000006ee4463048	H0F.n...
x0	000000704e549560	`.TNp...
x7	73a9c37469636170	pacit..s
x11	020000704e549560	`.TNp...
x21	020000704e549550	P.TNp...
x22	000000704e549560	`.TNp...
x28	000000704e549550	P.TNp...
```

## Scored candidates

- No usable Dispatch runtime found: 1
- Failed to create a dispatch delegate kernel: 1
- Failed to initialize Dispatch API: 0
- insufficient capabilities: 0
- LiteRtDispatchCheckRuntimeCompatibility: 0
- libLiteRtRuntimeCApi: 0
- QNN path / ADSP path: 1
```

## Missing/Error Strings

```text
```

## Native Library Metadata

```text
applicationId=io.github.ninbyo02.lami.customnpu
nativeLibraryDir=/data/app/~~GOtnUucBIYeW6o_iPI2D3A==/io.github.ninbyo02.lami.customnpu-wck9yMbIRma8Z2cyHzfrIw==/lib/arm64
localApk=app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk
library	present	size	sha256	build_id	soname	needed	source
liblitertlm_jni.so	true	55172880	32b58ee5b5c16e181897d10cd392fff0695c562bf4b578968387f20d6a80ff0a	bb6f8924e466e7039a1f54d7170a2eb2		libGemmaModelConstraintProvider.so,libdl.so,liblog.so,libandroid.so,libGLESv3.so,libEGL.so,libm.so,libc.so	apk
libLiteRt.so	true	5405080	84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553	a03032ad1eeefda446478aea308c2ed0	libLiteRt.so	libdl.so,libGLESv3.so,libEGL.so,libm.so,liblog.so,libc.so	apk
libLiteRtDispatch_Qualcomm.so	true	691184	7d3d37cb13cf88fc679ea8d07d271865db36e2f6f6eab80e3a1d02783000c34f	283f860170c8b970f14db885eab73a95	libLiteRtDispatch_Qualcomm.so	libLiteRt.so,libandroid.so,liblog.so,libdl.so,libc.so,libm.so	apk
libQnnSystem.so	true	2983560	7e69258e1278cc9b2bb62dbc6e2a52c227a100d6505a13fd6324a87993d0bba8	0d409cdd664b8b0a	libQnnSystem.so	libc.so,libm.so,libdl.so,liblog.so	apk
libQnnHtp.so	true	2778176	090e993822564851eab1405aff171643b21e644e3f696c95c96f2732aaed813a	f2c90c1775a109e1	libQnnHtp.so	libc.so,libm.so,libdl.so,liblog.so	apk
libQnnHtpPrepare.so	true	85539184	09b1c15c62b6875af49ffd3d841961c098b85c367f584fee370f986c62511298	edb612e67d6d27c2	libQnnHtpPrepare.so	libc.so,libm.so,libdl.so,liblog.so	apk
libQnnHtpV79Stub.so	true	679168	005bd3de462851ce3dde55260d7d8560d6d07dbc309f554780b1f6412e6d9df1	10d7ad6f9195411a	libQnnHtpV79Stub.so	libc.so,libm.so,libdl.so,liblog.so,libcdsprpc.so	apk
libQnnHtpV79Skel.so	true	10975268	41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98		libQnnHtpV79Skel.so	libc++.so.1,libc++abi.so.1	apk
libLiteRtRuntimeCApi.so	false	-	-	-	-	-	missing
libllm_inference_engine_jni.so	true	26422184	51b27a87a4723172b2661d68c03de01d73aaea71909c9dd2b2c8d1ef149ca1f8	2f6f9104344966674bf6587935d27cc8	libllm_inference_engine_jni.so	libandroid.so,libGLESv2.so,libEGL.so,libz.so,libGLESv3.so,libdl.so,libm.so,liblog.so,libc.so	apk
```
