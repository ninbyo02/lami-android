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
