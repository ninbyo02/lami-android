# LiteRT-LM GPU / Edge Gallery comparison

## 目的

Genericモデル `gemma-4-E2B-it.litertlm` を LAMI の GPU backend で実行したとき、生成開始ではなく `engine_create` が 60秒以内に完了しない原因を、Google AI Edge Gallery の Android 実装と比較して切り分ける。

## LAMI の実機症状

- CPU: `selected_backend=CPU`, `route_family=local_cpu`, Genericモデルで成功。
- GPU: `selected_backend=GPU`, `route_family=local_gpu`, `failure_stage=gpu_watchdog_timeout`。
- GPU timeout 時は `gpu_timeout_stage=engine_create`、`gpu_engine_create_started=true`、`gpu_engine_create_finished=false`、`gpu_generate_started=false`、`gpu_first_token_received=false`。
- 60秒待っても `Engine` 作成が終わらないため、token生成が遅い問題ではなく、GPU backend の初期化またはモデルロード段階の問題として扱う。

## Edge Gallery 側の確認結果

参照:

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt`
- `Android/src/app/src/main/AndroidManifest.xml`
- `Android/src/app/build.gradle.kts`
- `Android/src/gradle/libs.versions.toml`

Gallery の LLM chat helper は、モデル設定の `ACCELERATOR` から backend を選び、GPU の場合は LiteRT-LM の `Backend.GPU()` を `EngineConfig.backend` に渡す。NPU/TPU の場合は `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` を使う。会話作成は NPU 以外で `SamplerConfig` を設定する。

Gallery の `EngineConfig` の主な指定:

- `modelPath = model.getPath(context)`
- `backend = preferredBackend`
- `visionBackend = Backend.GPU()` 相当。ただし画像対応時のみ。
- `audioBackend = Backend.CPU()` 相当。ただし音声対応時のみ。
- `maxNumTokens = maxTokens`
- `cacheDir = context.getExternalFilesDir(null)?.absolutePath` は `/data/local/tmp` のモデルだけ。それ以外は `null`。

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

今回の安全変更後:

- `Automatic(DEFAULT)`: `Backend.CPU()` を使う CPU 優先。
- `CPU`: `Backend.CPU()`。
- `GPU`: `Backend.GPU()`。DEV診断目的の Experimental / 非推奨。
- `visionBackend`: `Backend.GPU()`。
- `audioBackend`: `Backend.CPU()`。
- `cacheDir`: `context.cacheDir.absolutePath`。

Gallery はモデルが `/data/local/tmp` のときだけ外部 files dir を cacheDir に指定し、それ以外は `null`。LAMI は常に app cache dir を渡す。この差分は記録対象だが、今回の症状は `Engine` 作成完了前の 60秒 timeout であり、cacheDir だけを原因とは断定しない。

### fallback / UI制限

Gallery 側の helper では `Engine` / `Conversation` 作成例外を `onDone(error)` に返すが、GPU非対応端末の明示 allowlist / denylist や自動 CPU fallback は確認できなかった。

LAMI では今回、GPU を Settings 上で `Experimental / 非推奨` と明示し、GPU engine_create timeout 時に `guard_recommendation=switch_to_cpu_or_npu` を出す。GPU backend は診断目的として残す。

## 原因候補ランキング

1. 端末の GPU/OpenCL 実装と LiteRT-LM GPU backend の相性問題。
   - `libOpenCL.so` は見えている前提でも、SM8750 / Android 16 / vendor GPU driver の組み合わせで `Engine` 作成が長時間戻らない可能性がある。
2. Generic `gemma-4-E2B-it.litertlm` の GPU 初期化コストまたは GPU delegate 初期化が 60秒を超える。
   - `generate_started=false` なので生成速度問題ではない。
3. LAMI の `EngineConfig` 差分。
   - Gallery と違い LAMI は `cacheDir=context.cacheDir` を常時指定し、`visionBackend=Backend.GPU()` も常時指定している。この差分は追加調査対象。
4. dependency 差分。
   - Gallery は TFLite GPU dependency も持つが、LiteRT-LM `Backend.GPU()` の `Engine` 作成 hang を直接解消する根拠はまだない。
5. Manifest 差分。
   - OpenCL/vndksupport は LAMI に既にあるため、優先度は低い。

## 現時点の運用判断

- Genericモデルの安定確認は CPU を基準にする。
- Automatic は当面 CPU 優先として扱う。
- GPU は DEV診断目的の Experimental / 非推奨として残す。
- GPU timeout 時は `gpu_watchdog_timeout_ms`, `gpu_watchdog_mode`, `gpu_timeout_stage`, `gpu_engine_create_duration_ms`, `gpu_engine_create_timeout_suspected`, `guard_recommendation=switch_to_cpu_or_npu` を確認する。
- NPU S1 native / JNI / QAIRT overlay は今回の調査対象外であり、変更しない。

## 次の調査候補

- `visionBackend=Backend.GPU()` を text-only Generic 推論で `null` にできるか、公式 API の制約を確認する。
- `cacheDir=null` と `cacheDir=context.cacheDir` の差分を DEV フラグで比較する。
- GPU 初回 engine create を 60秒超で放置した場合に native callback が遅れて戻るか、現在の stale callback 診断で観察する。
