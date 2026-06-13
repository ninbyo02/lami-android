# LiteRT-LM GPU / Edge Gallery comparison

## 目的

Genericモデル `gemma-4-E2B-it.litertlm` を LAMI の GPU backend で実行したとき、生成開始ではなく `engine_create` が 60秒以内に完了しない原因を、Google AI Edge Gallery の Android 実装と比較して切り分ける。

## LAMI の実機症状

- CPU: `selected_backend=CPU`, `route_family=local_cpu`, Genericモデルで成功。
- Automatic: 当面 CPU 優先で成功。
- GPU: `selected_backend=GPU`, `route_family=local_gpu`, `failure_stage=gpu_watchdog_timeout`。
- GPU timeout 時は `gpu_watchdog_timeout_ms=60000`、`gpu_watchdog_mode=extended_dev_60s`、`gpu_timeout_stage=engine_create`、`gpu_engine_create_started=true`、`gpu_engine_create_finished=false`、`gpu_engine_create_duration_ms=60008`、`gpu_generate_started=false`、`gpu_first_token_received=false`。
- 60秒待っても `Engine` 作成が終わらないため、token生成が遅い問題ではなく、GPU backend の初期化またはモデルロード段階の問題として扱う。
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

LAMI では GPU を Settings 上で `Experimental / 非推奨` と明示し、GPU engine_create timeout 時に `guard_recommendation=switch_to_cpu_or_npu` を出す。今回さらに、GPU 明示選択時だけ `edge_gallery_like` EngineConfig / ConversationConfig profile を適用する。

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
   - engine_create 後の差分なので timeout の直接原因ではなさそうだが、成功後の挙動差分を減らすため `edge_gallery_like` では Gallery default に寄せた。
5. 端末の GPU/OpenCL 実装と LiteRT-LM GPU backend の相性問題。
   - `libOpenCL.so` は見えている前提でも、SM8750 / Android 16 / vendor GPU driver の組み合わせで `Engine` 作成が長時間戻らない可能性がある。
6. Generic `gemma-4-E2B-it.litertlm` の GPU 初期化コストまたは GPU delegate 初期化が 60秒を超える。
   - `generate_started=false` なので生成速度問題ではない。
7. Manifest 差分。
   - OpenCL/vndksupport は LAMI に既にあるため、優先度は低い。

## 現時点の運用判断

- Genericモデルの安定確認は CPU を基準にする。
- Automatic は当面 CPU 優先として扱う。
- GPU は DEV診断目的の Experimental / 非推奨として残す。
- GPU 明示選択時は `edge_gallery_like` compatibility mode を試す。
- GPU timeout 時は `gpu_watchdog_timeout_ms`, `gpu_watchdog_mode`, `gpu_timeout_stage`, `gpu_engine_create_duration_ms`, `gpu_engine_create_timeout_suspected`, `gpu_compatibility_mode`, `gpu_engine_config_profile`, `gpu_cache_dir_mode`, `gpu_edge_gallery_diff_applied`, `guard_recommendation=switch_to_cpu_or_npu` を確認する。
- NPU S1 native / JNI / QAIRT overlay は今回の調査対象外であり、変更しない。

## 次の調査候補

- `edge_gallery_like` 適用後の実機 GPU で、`Engine` 作成が完了するか確認する。
- まだ timeout する場合は、Gallery と同じ external files dir 配下へ model file を配置して model path / mmap 差分を潰す。
- まだ timeout する場合は、Gallery の TFLite GPU dependency 差分を standardDebug だけで追加検証する。
- GPU 初回 engine create を 60秒超で放置した場合に native callback が遅れて戻るか、現在の stale callback 診断で観察する。

## APK native library diagnostics

`gemma-4-E2B-it.litertlm` が CPU では成功し、GPU だけ `Engine.initialize` 前後の `engine_create` で 60秒 timeout する場合は、モデル/Tokenizer/生成処理より先に GPU backend 初期化または native library 整合性を疑う。まず standardDebug APK に最終的に入った `lib/arm64-v8a` の実体を確認する。

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
