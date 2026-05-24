# NPU Engine.initialize crash summary

- applicationId: `io.github.ninbyo02.lami.customnpu`
- label: `customnpu`
- runId: `1779367764194`
- tombstone selection: `latest-tombstone-matches-app`
- device: `NX733J`
- final stage: `1779367799349 runId=1779367764194 Engine.initialize invoking method=Engine.initialize(): void`
- process alive after probe: `not-running`
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
      #01 pc 000000000122b1d0  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so ((anonymous namespace)::DispatchDelegate::CreateDelegateKernelInterface()+464) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #02 pc 0000000001237360  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::(anonymous namespace)::CreateDelegateKernelRegistration(tflite::SimpleOpaqueDelegateInterface*)::$_1::__invoke(void*, TfLiteOpaqueContext*, char const*, unsigned long)+36) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #03 pc 00000000014d4de4  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::AddNodeWithParameters(std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, char const*, unsigned long, void*, TfLiteRegistration const*, int*)+1240) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #04 pc 00000000014d46b8  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ReplaceNodeSubsetsWithDelegateKernels(TfLiteRegistration, TfLiteIntArray const*, TfLiteDelegate*)+996) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #05 pc 00000000014d42c4  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ReplaceNodeSubsetsWithDelegateKernels(TfLiteContext*, TfLiteRegistration, TfLiteIntArray const*, TfLiteDelegate*)+48) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #06 pc 0000000001269c08  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (TfLiteOpaqueContextReplaceNodeSubsetsWithDelegateKernels+36) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #07 pc 000000000123726c  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::(anonymous namespace)::DelegatePrepare(TfLiteOpaqueContext*, TfLiteDelegate*, void*)+320) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #08 pc 00000000014da100  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ModifyGraphWithDelegateImpl(TfLiteDelegate*)+500) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #09 pc 00000000014dafa4  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ModifyGraphWithDelegate(TfLiteDelegate*)+12) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #10 pc 00000000014ca830  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::impl::Interpreter::ModifyGraphWithDelegateImpl(TfLiteDelegate*)+80) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #11 pc 00000000011f88f4  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (LiteRtCompiledModelT::Create(LiteRtEnvironmentT*, LiteRtModelT*, LiteRtOptionsT*)+1068) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #12 pc 00000000011f3688  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (LiteRtCreateCompiledModel+96) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #13 pc 0000000000d367fc  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::CompiledModel::Create(litert::Environment&, LiteRtModelT*, litert::Options&)+156) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #14 pc 0000000000d9f9e8  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::LlmLiteRtNpuCompiledModelExecutor::CreateForModelHasPerLayerEmbedding(litert::lm::LlmExecutorSettings const&, litert::lm::ModelResources&, litert::Environment&, litert::Model const*, litert::lm::LlmLiteRtNpuCompiledModelExecutor::LogitsQuantizationParams)+140) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #15 pc 0000000000d9f840  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::LlmLiteRtNpuCompiledModelExecutor::Create(litert::lm::LlmExecutorSettings const&, litert::lm::ModelResources&, litert::Environment&)+744) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #16 pc 0000000000d4bd74  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::CreateLlmLiteRtCompiledModelExecutor(litert::lm::LlmExecutorSettings, litert::Environment&, litert::lm::ModelResources&)+296) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #17 pc 0000000000d1bc10  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Engine, std::__ndk1::default_delete<litert::lm::Engine>>> absl::lts_20260107::internal_any_invocable::LocalInvoker<false, absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Engine, std::__ndk1::default_delete<litert::lm::Engine>>>, litert::lm::(anonymous namespace)::$_0&, litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>>(absl::lts_20260107::internal_any_invocable::TypeErasedState*, absl::lts_20260107::internal_any_invocable::ForwardedParameter<litert::lm::EngineSettings>::type, absl::lts_20260107::internal_any_invocable::ForwardedParameter<std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>>::type)+1432) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #18 pc 00000000009b0b60  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::EngineFactory::Create(litert::lm::EngineFactory::EngineType, litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+112) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #19 pc 00000000009abfa0  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::EngineFactory::CreateDefault(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+440) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #20 pc 00000000009ab674  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1992) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #21 pc 00000000002c2100  /apex/com.android.art/lib64/libart.so (art_quick_generic_jni_trampoline+144) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #22 pc 00000000002aad94  /apex/com.android.art/lib64/libart.so (art_quick_invoke_stub+612) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #23 pc 00000000002707ac  /apex/com.android.art/lib64/libart.so (art::ArtMethod::Invoke(art::Thread*, unsigned int*, unsigned int, art::JValue*, char const*)+220) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #24 pc 000000000051cd84  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<true>(art::ArtMethod*, art::Thread*, art::ShadowFrame&, art::Instruction const*, unsigned short, bool, art::JValue*)+2008) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #25 pc 0000000000492ab0  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>(art::interpreter::SwitchImplContext*)+8480) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #26 pc 00000000003d12b8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #27 pc 0000000000ffea98  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/base.apk (com.google.ai.edge.litertlm.Engine.initialize+0)
      #28 pc 00000000003d0f18  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute(art::Thread*, art::CodeItemDataAccessor const&, art::ShadowFrame&, art::JValue, bool, bool) (.__uniq.112435418011751916792819755956732575238.llvm.4073957672844042480)+364) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #29 pc 00000000003d064c  /apex/com.android.art/lib64/libart.so (artQuickToInterpreterBridge+1020) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #30 pc 00000000002c2238  /apex/com.android.art/lib64/libart.so (art_quick_to_interpreter_bridge+88) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #31 pc 00000000002aad94  /apex/com.android.art/lib64/libart.so (art_quick_invoke_stub+612) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #32 pc 00000000002a1320  /apex/com.android.art/lib64/libart.so (_jobject* art::InvokeMethod<(art::PointerSize)8>(art::ScopedObjectAccessAlreadyRunnable const&, _jobject*, _jobject*, _jobject*, unsigned long)+936) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #33 pc 00000000005ac718  /apex/com.android.art/lib64/libart.so (art::Method_invoke(_JNIEnv*, _jobject*, _jobject*, _jobjectArray*) (.__uniq.165753521025965369065708152063621506277)+32) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #34 pc 00000000000f41e0  [anon_shmem:dalvik-jit-code-cache] (offset 0x2000000) (art_jni_trampoline+144)
      #35 pc 00000000002aad94  /apex/com.android.art/lib64/libart.so (art_quick_invoke_stub+612) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #36 pc 0000000000586548  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>(art::ArtMethod*, art::Thread*, art::ShadowFrame&, art::Instruction const*, unsigned short, bool, art::JValue*)+2688) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #37 pc 0000000000490f5c  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>(art::interpreter::SwitchImplContext*)+1484) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #38 pc 00000000003d12b8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #39 pc 00000000000a0b98  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/base.apk (offset 0x8093000) (io.github.ninbyo02.lami.ui.screens.home.AcceleratorProbe.invokeEngineInitializeOperation+0)
      #40 pc 00000000003d1e84  /apex/com.android.art/lib64/libart.so (art::interpreter::ArtInterpreterToInterpreterBridge(art::Thread*, art::CodeItemDataAccessor const&, art::ShadowFrame*, art::JValue*)+448) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #41 pc 000000000051cbac  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<true>(art::ArtMethod*, art::Thread*, art::ShadowFrame&, art::Instruction const*, unsigned short, bool, art::JValue*)+1536) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #42 pc 0000000000492ab0  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>(art::interpreter::SwitchImplContext*)+8480) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #43 pc 00000000003d12b8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: 7087b2f2160bfbf3335d54ba9779e325)
      #44 pc 000000000009ecb0  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/base.apk (offset 0x8093000) (io.github.ninbyo02.lami.ui.screens.home.AcceleratorProbe.probeEngineInitializeDryRunSafely+0)
```

## Loaded Libs Summary

```text
      #01 pc 000000000122b1d0  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so ((anonymous namespace)::DispatchDelegate::CreateDelegateKernelInterface()+464) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #02 pc 0000000001237360  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::(anonymous namespace)::CreateDelegateKernelRegistration(tflite::SimpleOpaqueDelegateInterface*)::$_1::__invoke(void*, TfLiteOpaqueContext*, char const*, unsigned long)+36) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #03 pc 00000000014d4de4  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::AddNodeWithParameters(std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, std::__ndk1::vector<int, std::__ndk1::allocator<int>> const&, char const*, unsigned long, void*, TfLiteRegistration const*, int*)+1240) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #04 pc 00000000014d46b8  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ReplaceNodeSubsetsWithDelegateKernels(TfLiteRegistration, TfLiteIntArray const*, TfLiteDelegate*)+996) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #05 pc 00000000014d42c4  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ReplaceNodeSubsetsWithDelegateKernels(TfLiteContext*, TfLiteRegistration, TfLiteIntArray const*, TfLiteDelegate*)+48) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #06 pc 0000000001269c08  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (TfLiteOpaqueContextReplaceNodeSubsetsWithDelegateKernels+36) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #07 pc 000000000123726c  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::(anonymous namespace)::DelegatePrepare(TfLiteOpaqueContext*, TfLiteDelegate*, void*)+320) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #08 pc 00000000014da100  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ModifyGraphWithDelegateImpl(TfLiteDelegate*)+500) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #09 pc 00000000014dafa4  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::Subgraph::ModifyGraphWithDelegate(TfLiteDelegate*)+12) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #10 pc 00000000014ca830  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (tflite::impl::Interpreter::ModifyGraphWithDelegateImpl(TfLiteDelegate*)+80) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #11 pc 00000000011f88f4  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (LiteRtCompiledModelT::Create(LiteRtEnvironmentT*, LiteRtModelT*, LiteRtOptionsT*)+1068) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #12 pc 00000000011f3688  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (LiteRtCreateCompiledModel+96) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #13 pc 0000000000d367fc  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::CompiledModel::Create(litert::Environment&, LiteRtModelT*, litert::Options&)+156) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #14 pc 0000000000d9f9e8  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::LlmLiteRtNpuCompiledModelExecutor::CreateForModelHasPerLayerEmbedding(litert::lm::LlmExecutorSettings const&, litert::lm::ModelResources&, litert::Environment&, litert::Model const*, litert::lm::LlmLiteRtNpuCompiledModelExecutor::LogitsQuantizationParams)+140) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #15 pc 0000000000d9f840  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::LlmLiteRtNpuCompiledModelExecutor::Create(litert::lm::LlmExecutorSettings const&, litert::lm::ModelResources&, litert::Environment&)+744) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #16 pc 0000000000d4bd74  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::CreateLlmLiteRtCompiledModelExecutor(litert::lm::LlmExecutorSettings, litert::Environment&, litert::lm::ModelResources&)+296) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #17 pc 0000000000d1bc10  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Engine, std::__ndk1::default_delete<litert::lm::Engine>>> absl::lts_20260107::internal_any_invocable::LocalInvoker<false, absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Engine, std::__ndk1::default_delete<litert::lm::Engine>>>, litert::lm::(anonymous namespace)::$_0&, litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>>(absl::lts_20260107::internal_any_invocable::TypeErasedState*, absl::lts_20260107::internal_any_invocable::ForwardedParameter<litert::lm::EngineSettings>::type, absl::lts_20260107::internal_any_invocable::ForwardedParameter<std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>>::type)+1432) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #18 pc 00000000009b0b60  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::EngineFactory::Create(litert::lm::EngineFactory::EngineType, litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+112) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #19 pc 00000000009abfa0  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::EngineFactory::CreateDefault(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+440) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #20 pc 00000000009ab674  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1992) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
    0000006d'b2a22000-0000006d'b41b6fff r-x         0   1795000  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
    0000006d'b41b7000-0000006d'b429efff r--   1795000     e8000  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
    0000006d'b429f000-0000006d'b42b8fff rw-   187c000     1a000  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
    0000006d'b6664000-0000006d'b7ecefff r-x         0   186b000  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/libllm_inference_engine_jni.so (BuildId: 2f6f9104344966674bf6587935d27cc8)
    0000006d'b7ed0000-0000006d'b7f87fff r--   186c000     b8000  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/libllm_inference_engine_jni.so (BuildId: 2f6f9104344966674bf6587935d27cc8)
    0000006d'b7f8b000-0000006d'b7f99fff rw-   1923000      f000  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/libllm_inference_engine_jni.so (BuildId: 2f6f9104344966674bf6587935d27cc8)
      #06 pc 00000000017609f0  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (operator new(unsigned long)+28) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #07 pc 00000000011a44a0  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (google::protobuf::MessageLite* google::protobuf::internal::MessageCreator::New<google::protobuf::MessageLite>(google::protobuf::MessageLite const*, google::protobuf::MessageLite const*, google::protobuf::Arena*) const+60) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #08 pc 000000000119a208  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (google::protobuf::internal::TcParser::AddMessage(google::protobuf::internal::TcParseTableBase const*, google::protobuf::internal::RepeatedPtrFieldBase&)+188) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #09 pc 000000000119b190  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (google::protobuf::internal::TcParser::FastMtR1(google::protobuf::MessageLite*, char const*, google::protobuf::internal::ParseContext*, google::protobuf::internal::TcFieldData, google::protobuf::internal::TcParseTableBase const*, unsigned long)+68) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #10 pc 00000000011abcf4  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (bool google::protobuf::internal::MergeFromImpl<false>(std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>, google::protobuf::MessageLite*, google::protobuf::internal::TcParseTableBase const*, google::protobuf::MessageLite::ParseFlags)+56) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #11 pc 00000000011acec8  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (google::protobuf::MessageLite::ParseFromArray(void const*, int)+16) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #12 pc 00000000010ebce0  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (sentencepiece::SentencePieceProcessor::LoadFromSerializedProto(std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+52) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #13 pc 00000000010e2a64  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::SentencePieceTokenizer::CreateFromBuffer(std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)+28) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #14 pc 0000000000dae6f8  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::ModelResourcesLitertLm::GetTokenizer()+60) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #15 pc 0000000000d1ca78  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (litert::lm::(anonymous namespace)::EngineImpl::Create(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)::$_0::operator()() const+56) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #16 pc 0000000000d1d018  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (std::__ndk1::__async_assoc_state<absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Tokenizer, std::__ndk1::default_delete<litert::lm::Tokenizer>>>, std::__ndk1::__async_func<litert::lm::(anonymous namespace)::EngineImpl::Create(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)::$_0>>::__execute()+16) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
      #17 pc 0000000000d1d278  /data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64/liblitertlm_jni.so (void* std::__ndk1::__thread_proxy[abi:ne190000]<std::__ndk1::tuple<std::__ndk1::unique_ptr<std::__ndk1::__thread_struct, std::__ndk1::default_delete<std::__ndk1::__thread_struct>>, void (std::__ndk1::__async_assoc_state<absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Tokenizer, std::__ndk1::default_delete<litert::lm::Tokenizer>>>, std::__ndk1::__async_func<litert::lm::(anonymous namespace)::EngineImpl::Create(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)::$_0>>::*)(), std::__ndk1::__async_assoc_state<absl::lts_20260107::StatusOr<std::__ndk1::unique_ptr<litert::lm::Tokenizer, std::__ndk1::default_delete<litert::lm::Tokenizer>>>, std::__ndk1::__async_func<litert::lm::(anonymous namespace)::EngineImpl::Create(litert::lm::EngineSettings, std::__ndk1::basic_string_view<char, std::__ndk1::char_traits<char>>)::$_0>>*>>(void*)+56) (BuildId: 8faff14dc850b7fb1986a300ac465fa4)
```

## Loaded Libs Matrix

# Loaded native library summary

- applicationId: `io.github.ninbyo02.lami.customnpu`

| Library | Mapped in tombstone | Present in nativeLibraryDir/APK | Build ID |
| --- | --- | --- | --- |
| `liblitertlm_jni.so` | true | true | `c2c27170ba409dbd0bc01820fa738580` |
| `libLiteRt.so` | false | true | `80fa0688ac32301185275c903cec97bd` |
| `libLiteRtDispatch_Qualcomm.so` | false | true | `-` |
| `libQnnSystem.so` | false | true | `94d63184c6b1f968` |
| `libQnnHtp.so` | false | true | `e227353d86be672b` |
| `libQnnHtpPrepare.so` | false | true | `9ae62cf17f972404` |
| `libQnnHtpV79Stub.so` | false | true | `c079c75e0fd8ee92` |
| `libQnnHtpV79Skel.so` | false | true | `` |
| `libLiteRtRuntimeCApi.so` | false | false | `-` |
| `libllm_inference_engine_jni.so` | true | true | `2f6f9104344966674bf6587935d27cc8` |


## Abort Text Candidates

```text
## Direct strings

```text
artifacts/npu_diagnostics/20260521_215004_customnpu/tombstone_app_extract.txt:adsprpc_prop:s0
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
x18	0000006e53318000	..1Sn...
x18	0000006e4f6dc000	..mOn...
x18	0000006e4c688000	..hLn...
x18	0000006e45384000	.@8En...
x23	0000006e44720800	..rDn...
x24	0000006e44720880	..rDn...
x27	0000006e44628000	..bDn...
x28	0000006e44624000	.@bDn...
x29	0000006e447203a0	..rDn...
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
nativeLibraryDir=/data/app/~~yWR38LM1jYwU1uGimFTIWw==/io.github.ninbyo02.lami.customnpu-xHcUATKYEt2LMhIT52ZPFw==/lib/arm64
localApk=app/build/outputs/apk/standard/debug/app-standard-debug.apk
library	present	size	sha256	build_id	soname	needed	source
liblitertlm_jni.so	true	55158808	2971f268c7f8944527f4fb59a4cf9d38af2570af63f59ebbfa34b413e8fab45f	c2c27170ba409dbd0bc01820fa738580	liblitertlm_jni.so	libLiteRt.so,libandroid.so,libz.so,libGLESv2.so,libEGL.so,libdl.so,libGLESv3.so,libm.so,liblog.so,libc.so	device
libLiteRt.so	true	5421464	1abbc4d2a61b8631af6d9ba8bb6ef9ac5e0fef75fa2e608e6fd13a0b9768944d	80fa0688ac32301185275c903cec97bd	libLiteRt.so	libdl.so,libGLESv3.so,libEGL.so,libm.so,liblog.so,libc.so	device
libLiteRtDispatch_Qualcomm.so	true	707568	ec12f96959b543782d906afc5cc2caa888dc3b29ea2403ff175088d88acdf093	-	-	-	device
libQnnSystem.so	true	2497928	3bf3cf0841fccd8f14482a520d6c6f21f52e496de10253fa212d84eb06439994	94d63184c6b1f968	libQnnSystem.so	libc.so,libm.so,libdl.so,liblog.so	device
libQnnHtp.so	true	2194248	3e5592d4a7361082f958aa1534f1d3adb29639a74dd5d47a014a4ce37e9fd927	e227353d86be672b	libQnnHtp.so	libc.so,libm.so,libdl.so,liblog.so	device
libQnnHtpPrepare.so	true	52389312	b178fcb21b68062e7b7aa7a0531a65f194ecc6dcaba9ad9b0b4ef8d54bced21b	9ae62cf17f972404	libQnnHtpPrepare.so	libc.so,libm.so,libdl.so,liblog.so	device
libQnnHtpV79Stub.so	true	477480	610d69e78e9a26e9e6b706dcebc9a199fbb058403dce36b67749715095f68166	c079c75e0fd8ee92	libQnnHtpV79Stub.so	libc.so,libm.so,libdl.so,liblog.so,libcdsprpc.so	device
libQnnHtpV79Skel.so	true	13773096	5590d6b34efdaef561155b77bc734a1a1e560767c180df9aba2dbceeb7ad28d1		libQnnHtpV79Skel.so	libc++.so.1,libc++abi.so.1	device
libLiteRtRuntimeCApi.so	false	-	-	-	-	-	missing
libllm_inference_engine_jni.so	true	26422184	51b27a87a4723172b2661d68c03de01d73aaea71909c9dd2b2c8d1ef149ca1f8	2f6f9104344966674bf6587935d27cc8	libllm_inference_engine_jni.so	libandroid.so,libGLESv2.so,libEGL.so,libz.so,libGLESv3.so,libdl.so,libm.so,liblog.so,libc.so	device
```
