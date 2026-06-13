# LiteRT-LM GPU / Edge Gallery comparison

## 目的

Genericモデル `gemma-4-E2B-it.litertlm` を LAMI の GPU backend で実行したとき、60秒 watchdog timeout になる原因を、Google AI Edge Gallery の Android 実装と比較して切り分ける。最新診断では `Engine.initialize` / conversation 作成 / generate 開始までは到達しており、現在の主観測は generate 開始後 first token 前 timeout である。

## LAMI の実機症状

- CPU: `selected_backend=CPU`, `route_family=local_cpu`, Genericモデルで成功。
- Automatic: 当面 CPU 優先で成功。
- GPU: `selected_backend=GPU`, `route_family=local_gpu`, `failure_stage=gpu_watchdog_timeout`。
- 初期診断では `engine_create` 停止に見えたが、追加診断後の `gpu_no_sampling_acceleration` 実機結果では `engine_initialize_finished=true`、`conversation_create_finished=true`、`generate_started=true`、`first_token_received=false`。
- 現在は `gpu_timeout_stage=generate_before_first_token` として分類し、token生成が遅いだけなのか、generate 開始後 first token 前で GPU runtime / compiled model 側が停止しているのかを切り分ける。
- 同一端末の Edge Gallery 観察では `Gemma-4-E2B-it` + `Accelerator: GPU` で "Model on GPU" 表示、`こんにちは` に約1.6秒で応答しており、LAMI 固有差分の可能性が高い。

## Edge Gallery 側の確認結果

参照:

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt`
- `Android/src/app/src/main/AndroidManifest.xml`
- `Android/src/app/build.gradle.kts`
- `Android/src/gradle/libs.versions.toml`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/Model.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/Config.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/Consts.kt`

Gallery の LLM chat helper は、モデル設定の `ACCELERATOR` から backend を選び、GPU の場合は LiteRT-LM の `Backend.GPU()` を `EngineConfig.backend` に渡す。NPU/TPU の場合は `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` を使う。会話作成は NPU 以外で `SamplerConfig` を設定する。

Gallery の `EngineConfig` の主な指定:

- `modelPath = model.getPath(context)`
- `backend = preferredBackend`
- `visionBackend = Backend.GPU()` 相当。ただし画像対応時のみ。
- `audioBackend = Backend.CPU()` 相当。ただし音声対応時のみ。
- `maxNumTokens = maxTokens`
- `cacheDir = context.getExternalFilesDir(null)?.absolutePath` は `/data/local/tmp` のモデルだけ。それ以外は `null`。

Gallery の初期化順:

1. `val engine = Engine(engineConfig)`
2. `engine.initialize()`
3. `engine.createConversation(ConversationConfig(...))`
4. `conversation.sendMessageAsync(Contents.of(...), MessageCallback, extraContext)`

Gallery の既定 LLM config:

- `DEFAULT_MAX_TOKEN=1024`
- `DEFAULT_TOPK=64`
- `DEFAULT_TOPP=0.95`
- `DEFAULT_TEMPERATURE=1.0`
- thinking default: false
- speculative decoding default: false unless model capability and user setting enable it

Gallery の Manifest は GPU/OpenCL 関連で以下を optional 宣言している。

- `libvndksupport.so`
- `libOpenCL.so`
- 追加で `libcdsprpc.so`, `libedgetpu_litert.so` も optional。

Gallery の Gradle は `com.google.ai.edge.litertlm:litertlm-android:0.11.0` に加え、TensorFlow Lite / Play services TFLite GPU 系 dependency も持つ。ただし LLM chat helper の Generic LiteRT-LM GPU 経路は `Backend.GPU()` と `EngineConfig` が中心であり、LAMI 側の timeout はこの `Engine` 作成中に発生している。

## LAMI 側との差分

### Manifest

LAMI の main Manifest には既に以下が optional 宣言済み。

- `libvndksupport.so`
- `libOpenCL.so`

そのため、今回の比較だけでは `libOpenCL.so` / `libvndksupport.so` 不足を原因とは判断しない。`libcdsprpc.so` は debug flavor 側に限定されており、今回の Generic GPU timeout の主因候補からは外す。

### Gradle

LAMI は `com.google.ai.edge.litertlm:litertlm-android` を flavor ごとに導入している。Gallery のような `play-services-tflite-gpu` dependency は持たないが、今回の failure は LiteRT-LM `EngineConfig.backend = Backend.GPU()` の `Engine` 作成中に起きているため、すぐに dependency 追加で解決できるとは判断しない。

### EngineConfig

LAMI の Generic LiteRT-LM 公式経路は `LocalStreamingRunner.buildLiteRtEngineConfig()` で `EngineConfig` を作る。

今回の変更後:

- `Automatic(DEFAULT)`: `Backend.CPU()` を使う CPU 優先。
- `CPU`: `Backend.CPU()`。
- `GPU`: `Backend.GPU()`。DEV診断目的の Experimental / 非推奨。ただし EngineConfig は `edge_gallery_like` profile を適用。

`edge_gallery_like` profile:

- `gpu_compatibility_mode=edge_gallery_like`
- `gpu_engine_config_profile=edge_gallery_like_text_only`
- `backend=Backend.GPU()`
- `visionBackend=null`
- `audioBackend=null`
- `maxNumTokens=1024`
- `cacheDir=null` for app-private / external files model paths
- `/data/local/tmp` model path の場合だけ渡された cacheDir を維持
- `ConversationConfig` は非NPU Gallery 相当の `SamplerConfig(topK=64, topP=0.95, temperature=1.0)` を使う
- `thinking=false`
- `speculativeDecoding=false`

Gallery はモデルが `/data/local/tmp` のときだけ外部 files dir を cacheDir に指定し、それ以外は `null`。変更前 LAMI は常に app cache dir を渡していたため、`edge_gallery_like` profile では通常 model path の `cacheDir=null` へ寄せた。ただし今回の症状は `Engine` 作成完了前の 60秒 timeout であり、cacheDir だけを原因とは断定しない。

### 1対1差分表

| 項目 | Edge Gallery | LAMI 変更前 | LAMI `edge_gallery_like` |
| --- | --- | --- | --- |
| text backend | `Backend.GPU()` | `Backend.GPU()` | `Backend.GPU()` |
| `Engine.initialize()` | 明示呼び出し | 明示呼び出し済み | 維持 |
| text-only `visionBackend` | `null` | `Backend.GPU()` | `null` |
| text-only `audioBackend` | `null` | `Backend.CPU()` | `null` |
| `maxNumTokens` | `1024` 既定 | `null` | `1024` |
| `cacheDir` | 通常 model path では `null` | `context.cacheDir` | 通常 model path では `null` |
| `ConversationConfig` | `SamplerConfig(64, 0.95, 1.0)` | no-arg default | `SamplerConfig(64, 0.95, 1.0)` |
| thinking | default false | 未指定 | false として診断 |
| speculative decoding | default false | 未指定 | false として診断 |
| model path | external files dir under normalized model/version | user selected file path | user selected file path |
| dependency | `litertlm-android:0.11.0` + TFLite GPU deps | `litertlm-android` flavor deps | 変更なし |

### fallback / UI制限

Gallery 側の helper では `Engine` / `Conversation` 作成例外を `onDone(error)` に返すが、GPU非対応端末の明示 allowlist / denylist や自動 CPU fallback は確認できなかった。

LAMI では GPU を Settings 上で `Experimental / 非推奨` と明示し、GPU watchdog timeout 時に `guard_recommendation=switch_to_cpu_or_npu` を出す。今回さらに、GPU 明示選択時だけ `edge_gallery_like` EngineConfig / ConversationConfig profile を適用する。

## 原因候補ランキング

1. LAMI の EngineConfig 差分。
   - 変更前 LAMI は text-only でも `visionBackend=Backend.GPU()` と `audioBackend=Backend.CPU()` を常時指定し、`cacheDir=context.cacheDir`、`maxNumTokens=null` だった。
   - Gallery は text-only では vision/audio backend を `null` にし、通常 model path では `cacheDir=null`、`maxNumTokens=1024`。
   - この差分は `Engine(engineConfig)` の中で GPU delegate / model initialization を変える可能性があるため最優先。
2. model path / mmap / storage 差分。
   - Gallery は external files dir 配下の normalized model/version/file path。
   - LAMI は user selected file path。ファイル権限、mmap、scoped storage の扱いが GPU backend に影響する可能性がある。
3. dependency / native packaging 差分。
   - Gallery は TFLite GPU dependency も持つ。LAMI は LiteRT-LM dependency が中心。
   - ただし LAMI の timeout は LiteRT-LM `Backend.GPU()` の `Engine` 作成中なので、dependency 追加だけで直る根拠はまだ弱い。
4. ConversationConfig/SamplerConfig 差分。
   - 最新診断では `gpu_no_sampling_acceleration` でも `generate_before_first_token` で timeout しているため、TopK / GPU sampler 初期化だけが主因である可能性は下がる。
   - ただし first token 前の generate path に sampler 設定が影響する可能性は残るため、診断 key は維持する。
5. 端末の GPU/OpenCL 実装と LiteRT-LM GPU backend の相性問題。
   - `libOpenCL.so` は見えている前提でも、SM8750 / Android 16 / vendor GPU driver の組み合わせで `Engine` 作成が長時間戻らない可能性がある。
6. Generic `gemma-4-E2B-it.litertlm` の GPU generate first token 前処理が 60秒を超える、または戻らない。
   - 最新診断では `generate_started=true` かつ `first_token_received=false` なので、Engine 初期化だけでなく compiled model / GPU runtime の first token 前処理を疑う。
7. Manifest 差分。
   - OpenCL/vndksupport は LAMI に既にあるため、優先度は低い。

## 現時点の運用判断

- Genericモデルの安定確認は CPU を基準にする。
- Automatic は当面 CPU 優先として扱う。
- GPU は DEV診断目的の Experimental / 非推奨として残す。
- GPU 明示選択時は `edge_gallery_like` compatibility mode を試す。
- GPU timeout 時は `gpu_watchdog_timeout_ms`, `gpu_watchdog_mode`, `gpu_timeout_stage`, `gpu_watchdog_failure_stage`, `gpu_timeout_checkpoint`, `generate_call_started_at_elapsed_ms`, `generate_before_first_token_elapsed_ms`, `gpu_generate_before_first_token_timeout_suspected`, `gpu_compatibility_mode`, `gpu_engine_config_profile`, `gpu_cache_dir_mode`, `gpu_edge_gallery_diff_applied`, `guard_recommendation=switch_to_cpu_or_npu` を確認する。
- NPU S1 native / JNI / QAIRT overlay は今回の調査対象外であり、変更しない。

## 次の調査候補

- `gpu_max_tokens_32` で first token 前 timeout が変わるか確認する。
- `gpu_cache_dir_app_files` と `gpu_cache_dir_null` で compiled model / cache dir 条件差分を比較する。
- まだ timeout する場合は、Gallery と同じ external files dir 配下へ model file を配置して model path / mmap 差分を潰す。
- まだ timeout する場合は、Gallery の TFLite GPU dependency 差分を standardDebug だけで追加検証する。
- GPU watchdog timeout 後に native callback が遅れて戻るか、現在の stale callback 診断で観察する。

## APK native library diagnostics

`gemma-4-E2B-it.litertlm` が CPU では成功し、GPU だけ 60秒 timeout する場合は、まず standardDebug APK に最終的に入った `lib/arm64-v8a` の実体を確認する。最新診断では generate 開始後 first token 前まで進むため、native library 整合性に加えて compiled model / GPU runtime の first token 前処理も比較対象にする。

追加した診断:

- `scripts/dump_standard_debug_apk_native_libs.sh`
- `scripts/compare_edge_gallery_lami_apk_native_libs.sh`
- `scripts/compare_litert_gpu_accelerator_strings.sh`
- Gradle task: `:app:dumpStandardDebugApkNativeLibs`
- Gradle task: `:app:compareStandardDebugApkNativeLibsWithEdgeGallery`

standardDebug APK の最終 native lib dump:

```bash
./gradlew :app:assembleStandardDebug
scripts/dump_standard_debug_apk_native_libs.sh
```

または Gradle task として:

```bash
./gradlew :app:dumpStandardDebugApkNativeLibs
```

Edge Gallery APK と LAMI standardDebug APK の比較:

```bash
./gradlew :app:assembleStandardDebug
scripts/compare_edge_gallery_lami_apk_native_libs.sh /path/to/edge-gallery.apk
```

または:

```bash
./gradlew :app:compareStandardDebugApkNativeLibsWithEdgeGallery -PedgeGalleryApk=/path/to/edge-gallery.apk
```

`dump_standard_debug_apk_native_libs.sh` は以下を出力する。

- APK 内 `lib/arm64-v8a/*.so` の一覧
- 重点確認対象 `.so` の有無、size、sha256、build id、NEEDED
- final APK entry と SHA が一致する source candidate
- `origin_bucket`

重点確認対象:

- `libLiteRt.so`
- `liblitertlm_jni.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- `libQnnSystem.so`
- `libQnnGpu.so`
- `libQnnHtp.so`
- `libQnnHtpPrepare.so`
- `libQnnHtpV79Stub.so`
- `libQnnHtpV79Skel.so`
- `libQnnDsp.so`
- `libGemmaModelConstraintProvider.so`

`origin_bucket` の読み方:

- `qairt244_standard_debug_overlay`: `standardDebug` hidden experiment の overlay 入力または `src/customBuildExperimentDebug/jniLibs` と final APK が SHA 一致。Gradle の重複 native lib 警告があっても、この APK entry は overlay 側が勝っている。
- `aar_dependency`: Gradle cache 内の AAR payload と final APK が SHA 一致。AAR 側が最終APKに入っている。
- `app_main_jniLibs`: `app/src/main/jniLibs` と SHA 一致。
- `intermediate_only`: merged/stripped intermediate には一致するが、入力候補を特定できない。
- `unknown`: local candidate / AAR candidate と SHA 一致しない。cache が未展開、別 source set、または調査対象外の入力経由の可能性がある。

この診断は APK の中身だけを見る。NPU S1 native 実行経路、CPU held-official-flow、fallback、GPU timeout 本体の挙動は変更しない。

## GPU accelerator strings comparison

Edge Gallery の GPU accelerator は、少なくとも観測した APK では独立した以下の `.so` としては同梱されていない。

- `libLiteRtGpuAccelerator.so`
- `libLiteRtOpenClAccelerator.so`
- `libLiteRtVulkanAccelerator.so`
- `libLiteRtWebGpuAccelerator.so`

一方で Edge Gallery の `split_config.arm64_v8a.apk` 内の `libLiteRt.so` と `liblitertlm_jni.so` には `Statically linked GPU accelerator registered` が見える。つまり、GPU accelerator は独立 `.so` ではなく LiteRT / LiteRT-LM JNI stack に静的リンクされている可能性が高い。

この前提では、LAMI standardDebug に入っている `libQnnGpu.so` は Edge Gallery GPU 成功経路の直接根拠にはならない。`libQnnGpu.so` は QNN runtime payload の一部として APK に入っているが、Generic LiteRT-LM `Backend.GPU()` の 60秒 `engine_create` timeout 調査では主対象から外し、まず `libLiteRt.so` と `liblitertlm_jni.so` の差分を主対象にする。

重点比較:

```bash
./gradlew :app:assembleStandardDebug
scripts/compare_litert_gpu_accelerator_strings.sh /path/to/split_config.arm64_v8a.apk
```

第2引数に LAMI APK または `lib/arm64-v8a` ディレクトリを渡すこともできる。

```bash
scripts/compare_litert_gpu_accelerator_strings.sh /path/to/split_config.arm64_v8a.apk /path/to/app-standard-debug.apk
```

このスクリプトは `libLiteRt.so` と `liblitertlm_jni.so` について以下を出す。

- size / sha256 / build id
- GPU accelerator 重点文字列の有無
- GPU accelerator 重点文字列の filtered diff
- `strings` 上の周辺行
- `nm -D` / `readelf -Ws` ベースの GPU/OpenCL/Vulkan/WebGPU/delegate/accelerator 関連 symbol diff

重点文字列:

- `Statically linked GPU accelerator registered`
- `Dynamically loaded GPU accelerator`
- `LiteRT GpuEnvironment`
- `OpenGL-OpenCL shared context`
- `OpenCL`
- `Vulkan`
- `WebGPU`
- `gpu_options`
- `convert_weights_on_gpu`
- `hint_fully_delegated_to_single_delegate`
- `libLiteRtGpuAccelerator`
- `libLiteRtOpenClAccelerator`
- `libLiteRtVulkanAccelerator`
- `libLiteRtWebGpuAccelerator`

`libLiteRt.so` と `liblitertlm_jni.so` は ABI / symbol / registration / model constraint provider の結合が強い。片方だけを Edge Gallery 版へ差し替える検証は禁止する。差し替え検証を行う場合でも、まず比較結果から同一バージョン・同一依存セットとして扱えるか確認し、別 flavor または別 APK で isolated に行う。standardDebug の本経路や NPU S1 を巻き込んだ単体差し替えはしない。

## GPU initialize diagnostics

Edge Gallery と LAMI の両方で以下の GPU accelerator 関連文字列 / symbol が確認できる場合、LAMI 側の timeout は「GPU accelerator が欠落している」問題ではなく、LiteRT / LiteRT-LM JNI stack の初期化条件、EngineConfig、ConversationConfig、SamplerConfig、cacheDir、compiled model / delegate 初期化差分として扱う。

確認済みの代表例:

- `Statically linked GPU accelerator registered`
- `Dynamically loaded GPU accelerator`
- `Created LiteRT GpuEnvironment`
- `Created default OpenGL-OpenCL shared context`
- `libLiteRtGpuAccelerator.so`
- `libLiteRtOpenClAccelerator.so`
- `libLiteRtVulkanAccelerator.so`
- `libLiteRtWebGpuAccelerator.so`
- `LiteRtStaticLinkedAcceleratorGpuDef`

LAMI の `Local inference failure compact` / `LOCAL_ROUTE_DIAG` には、GPU timeout 直前の状態を追うために以下を追加する。

- `gpu_experiment_mode`
- `gpu_experiment_modes_available`
- `gpu_engine_config_model_path`
- `gpu_engine_config_cache_dir`
- `gpu_engine_config_backend`
- `gpu_engine_config_vision_backend`
- `gpu_engine_config_audio_backend`
- `gpu_engine_config_max_tokens`
- `gpu_engine_config_build_started`
- `gpu_engine_config_build_finished`
- `gpu_engine_constructor_started`
- `gpu_engine_constructor_finished`
- `gpu_engine_initialize_started`
- `gpu_engine_initialize_finished`
- `gpu_engine_initialize_call_state`
- `gpu_timeout_checkpoint`
- `gpu_sampler_config_enabled`
- `gpu_sampler_config_top_k`
- `gpu_sampler_config_top_p`
- `gpu_sampler_config_temperature`
- `gpu_sampler_acceleration_policy`
- `gpu_conversation_config_sampler_present`
- `gpu_options_configured`
- `gpu_options_source`

`gpu_timeout_checkpoint` の読み方:

- `engine_config_build`: `EngineConfig` 生成中。
- `engine_constructor`: `Engine(engineConfig)` 作成中。
- `engine_initialize`: `Engine.initialize()` 呼び出し中。
- `conversation_create`: `createConversation` 中。
- `generate_started`: 生成開始後。`first_token_received=false` の場合は `gpu_timeout_stage=generate_before_first_token` として扱う。

最新の `gpu_no_sampling_acceleration` 実機結果では、`engine_initialize_finished=true`、
`conversation_create_finished=true`、`generate_started=true` まで到達し、`first_token_received=false` のまま
60 秒 watchdog timeout になった。つまり現時点の主観測は `Engine.initialize` 停止ではなく、
`generate_before_first_token` 停止である。

`gpu_no_sampling_acceleration`, `gpu_cache_dir_app_files`, `gpu_max_tokens_32` はいずれも NG。
sampler / cache dir / max tokens 単独原因説は弱くなった。次の焦点は、generate 内部の
prefill / first token 前 GPU graph execution / compiled graph / LiteRT-LM GPU delegate 内部である。

first token 前 timeout の比較用 key:

- `gpu_timeout_stage`
- `gpu_watchdog_failure_stage`
- `generate_call_started_at_elapsed_ms`
- `first_token_received_at_elapsed_ms`
- `generate_before_first_token_elapsed_ms`
- `gpu_generate_before_first_token_timeout_suspected`
- `gpu_last_known_stage`
- `gpu_timeout_checkpoint`

## DEV-only GPU experiment modes

デフォルトは引き続き `edge_gallery_like`。明示しない限り production / 通常挙動は変えない。

standardDebug の DEV 検証だけで、Android system property または JVM property / env から以下を選べる。

- `edge_gallery_like`
- `gpu_sampler_only_minimal`
- `gpu_no_sampling_acceleration`
- `gpu_disable_topk_gpu_sampler_candidate`
- `gpu_cache_dir_null`
- `gpu_cache_dir_app_files`
- `gpu_max_tokens_32`

Android 実機での指定例:

```bash
adb shell setprop debug.lami.gpu_experiment_mode gpu_no_sampling_acceleration
```

検証後は未指定へ戻す。

```bash
adb shell setprop debug.lami.gpu_experiment_mode ""
```

次の優先実験:

1. `gpu_max_tokens_32`
   - 目的: `maxNumTokens=1024` が first token 前 timeout に関係するかを切り分ける。
   - 期待する比較点: `gpu_engine_config_max_tokens=32`、`gpu_timeout_stage`、`generate_before_first_token_elapsed_ms`。
2. `gpu_cache_dir_app_files`
   - 目的: `cacheDir=null` が compiled model / GPU runtime の初期化または生成開始後挙動に悪影響を出していないか確認する。
   - 期待する比較点: `gpu_engine_config_cache_dir_present=true`、`gpu_cache_dir_mode=forced_app_cache_dir`。
3. `gpu_cache_dir_null`
   - 目的: `edge_gallery_like` の null cache と明示 null cache で診断 key が一致するか確認する。
   - `gpu_cache_dir_app_files` と対で比較する。

## DEV-only GPU prefill probe

通常チャットの 60 秒 watchdog を毎回待たずに、明示 opt-in の短い probe で GPU prefill / first token 前停止を切り分ける。
production 既定挙動、CPU held-official-flow、NPU S1、fallback には入れない。

有効化:

```bash
adb shell setprop debug.lami.gpu_prefill_probe true
adb shell setprop debug.lami.gpu_prefill_probe_prompt hi
adb shell setprop debug.lami.gpu_prefill_probe_max_tokens 1
adb shell setprop debug.lami.gpu_prefill_probe_sampler none
adb shell setprop debug.lami.gpu_prefill_probe_cache_dir null
```

比較候補:

- prompt: `こんにちは`, `hi`, `.`
- max tokens: `1`, `4`, `32`
- sampler: `none`, `gallery`
- cache dir: `null`, `app_cache`

probe は GPU 明示選択時だけ、現在選択中の `.litertlm` model path で実行する。既定 timeout は 15 秒。
結果は DEV trace と `files/dev_diagnostics/gpu_prefill_probe_latest.txt` に残る。

重要: probe は診断専用で、通常チャット generate と競合させない。`debug.lami.gpu_prefill_probe=true`
のときは probe 専用 Engine/Conversation で短い generate を試し、その turn の通常 GPU generate はスキップする。
probe timeout 後は held engine を recreate 要求し、続けて同じ turn で通常 GPU 生成しない。

出力 key:

- `probe_enabled`
- `probe_run_started`, `probe_run_finished`, `probe_run_timed_out`
- `probe_skipped_normal_generate`
- `probe_isolated_engine_used`, `probe_shared_engine_used`
- `probe_prompt_variant`
- `probe_prompt_length_chars`
- `probe_max_tokens`
- `probe_sampler_enabled`
- `probe_cache_dir_mode`
- `probe_engine_config_started`, `probe_engine_config_finished`
- `probe_engine_initialize_started`, `probe_engine_initialize_finished`
- `probe_conversation_create_started`, `probe_conversation_create_finished`
- `probe_generate_started`
- `probe_first_token_received`
- `probe_generate_before_first_token_elapsed_ms`
- `probe_timeout_stage`
- `probe_failure_stage`
- `probe_exception_class`, `probe_exception_message`
- `probe_exception_cause_class`, `probe_exception_cause_message`
- `probe_exception_root_cause_class`, `probe_exception_root_cause_message`
- `probe_exception_chain`
- `probe_reflection_target_exception_class`, `probe_reflection_target_exception_message`
- `probe_reflection_target_exception_root_cause_class`, `probe_reflection_target_exception_root_cause_message`
- `probe_result_text_length`, `probe_result_text_head`
- `probe_stale_callback_ignored`
- `probe_cleanup_started`, `probe_cleanup_finished`, `probe_cleanup_result`
- `probe_invalidated_held_engine`
- `probe_normal_generate_blocked_reason`
- `previous_invocation_still_processing_detected`
- `probe_used_held_engine`
- `probe_held_engine_present_before`
- `probe_held_engine_invalidated_after`
- `normal_gpu_last_known_stage`
- `normal_gpu_can_initialize_with_held_engine_hint`
- `isolated_gpu_engine_initialize_failed_hint`
- `probe_elapsed_ms`

読み方:

- `probe_generate_started=true` かつ `probe_first_token_received=false`、`probe_timeout_stage=generate_before_first_token`:
  最小入力でも first token 前で止まる。prompt 長・通常チャット UI ではなく GPU prefill / graph execution 側を疑う。
- `probe_first_token_received=true`:
  最小入力では GPU first token が返る。通常チャットとの差分として prompt 長、conversation state、UI側の 60秒 watchdog 条件を比較する。
- `probe_exception_class` が `none` 以外:
  timeout ではなく Java/Kotlin 例外経路。InvocationTargetException の場合は root cause も通常の Local inference failure compact と合わせて見る。
- `probe_engine_initialize_started=true` かつ `probe_engine_initialize_finished=false`、
  `probe_failure_stage=gpu_prefill_probe_engine_initialize_invocation_target_exception`:
  isolated GPU engine の `Engine.initialize()` が反射先例外で失敗している。`probe_reflection_target_exception_*` と
  `probe_exception_chain` を見る。
- `failure_cause_message=Previous invocation still processing. Wait for done=true.`:
  LiteRT-LM の同時 generate 呼び出し疑い。compact では `lite_rt_lm_previous_invocation_still_processing=true`,
  `generate_concurrency_violation_suspected=true`, `guard_recommendation=reset_gpu_engine_or_force_cpu` として扱う。

推奨運用:

- probe 有効時は probe だけ実行する。
- timeout 時はアプリまたは GPU engine を再作成する。
- probe timeout 直後に同じ turn で通常 GPU 生成を続けない。
- `hi` / `max_tokens=1` / no sampler / cache=null でも first token 前 timeout なら、GPU backend の generate 内部問題が濃い。
- probe が通るなら、通常チャット prompt/config/conversation state 差分を疑う。

最新解釈:

- 最小条件 `prompt=hi`, `max_tokens=1`, samplerなし, cache=null の prefill probe で、
  isolated GPU engine は `Engine.initialize` 中に `InvocationTargetException` で失敗した。
- 通常 GPU route は held engine 経由で `Engine.initialize` / conversation 作成 / `generate_started` まで進む。
- したがって prompt / max tokens / sampler / cache dir 単独原因説はさらに弱まり、GPU Engine 初期化条件、
  held engine lifecycle、LiteRT GPU environment、GPU resource state の差分が主対象になる。
- 次の焦点は `InvocationTargetException` の target exception / cause / root cause / chain である。

各モードの目的:

- `gpu_no_sampling_acceleration`: `ConversationConfig` に `SamplerConfig` を渡さず、TopK/OpenCL/WebGPU sampler 周辺を避ける。
- `gpu_disable_topk_gpu_sampler_candidate`: 現APIで明示的に TopK GPU sampler だけを切る direct option はないため、診断上は no sampler config として扱う。
- `gpu_cache_dir_null`: cacheDir を強制 `null` にして Gallery 通常 model path 寄せを明示する。
- `gpu_cache_dir_app_files`: app cache dir を渡し、cacheDir 差分の影響を見る。
- `gpu_max_tokens_32`: `maxNumTokens=32` で compiled model / delegate 初期化負荷の影響を見る。

これらは GPU 明示選択時だけ有効。CPU held-official-flow、Automatic CPU priority、NPU S1、fallback 本挙動は変更しない。
