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

## GPU Phase 3: generate callback / first token stall

現在の確認済み状態:

- Generic `gemma-4-E2B-it.litertlm` の CPU route は成功済み。
- 通常 GPU route は `Engine.initialize` と conversation 作成まで成功する。
- 通常 GPU route は `generate_started=true` 後、`first_token_received=false` のまま 60秒 timeout する。
- held engine は timeout cleanup で holder clear されるため、次回 probe の `no_held_engine` は期待される。
- isolated prefill probe は `Engine.initialize` で compiled model 作成エラーになり、通常 held route とは失敗地点が異なる。

Phase 3 の焦点は、generate 開始後 first token 前の停止が native generate hang なのか、callback / UI handling の見落としなのかを分けること。`LOCAL_ROUTE_DIAG` と Local inference failure compact に以下を追加して観測する。

- `gpu_generate_call_entered`
- `gpu_generate_call_returned`
- `gpu_callback_invoked_count`
- `gpu_callback_first_invoked_at_elapsed_ms`
- `gpu_callback_last_invoked_at_elapsed_ms`
- `gpu_callback_thread_name`
- `gpu_callback_done_true_seen`
- `gpu_callback_error_seen`
- `gpu_callback_empty_text_count`
- `gpu_callback_non_empty_text_count`
- `gpu_callback_last_text_length`
- `gpu_callback_last_text_head`
- `gpu_first_non_empty_text_elapsed_ms`
- `gpu_first_token_classification_reason`
- `gpu_callback_exception_class`
- `gpu_callback_exception_message`
- `gpu_callback_exception_chain`
- `gpu_callback_exception_stage`
- `gpu_generate_stall_interpretation`
- `callback_route_diff`

`gpu_generate_stall_interpretation` の読み方:

- `native_generate_no_callback`: `generate` 呼び出し後、callback が一度も来ていない。
- `callback_empty_until_timeout`: callback は来ているが空 text のみ。
- `callback_done_without_text`: `done=true` は来たが text が空。
- `callback_exception_before_first_token`: first token 前に callback 内例外を検出。
- `ui_first_token_detection_missed`: non-empty text は観測したが UI 側 first-token 判定が立っていない。

DEV opt-in の追加 mode:

```sh
adb shell setprop debug.lami.gpu_generate_probe_mode raw_callback_only
```

`raw_callback_only` は GPU 明示選択時だけ有効で、generate callback を記録しつつ streaming UI 更新を抑える。通常 route の TTS/DB/Markdown/NPU/native library には手を入れない。比較用に `ascii_prompt`, `max_tokens_1`, `no_sampler`, `no_streaming_ui` も mode 名として受け付けるが、既定値は `normal` のまま。

次の実機確認:

1. `adb shell setprop debug.lami.gpu_generate_probe_mode raw_callback_only`
2. Settings で GPU を明示選択。
3. prompt=`こんにちは` で通常チャットを1回実行。
4. Copy Compact または LOCAL_ROUTE_DIAG で `gpu_callback_invoked_count` と `gpu_generate_stall_interpretation` を確認。

判断:

- `gpu_callback_invoked_count=0` なら native generate が first callback 前で止まっている可能性が強い。
- callback が来て text が空なら callback payload / done handling を追加確認する。
- non-empty text があるのに first token 判定がない場合は UI first-token detection の問題を疑う。

## GPU Phase 4: compiled model invoke failure

Phase 3 の実機結果で、通常 GPU route は engine / conversation 生成ではなく generate phase で失敗していることが分かった。

確認済み:

- `Engine.initialize` は成功する。
- `Conversation.create` は成功する。
- `generateResponse` / `sendMessageAsync` 呼び出しは戻る。
- `raw_callback_only` では `gpu_generate_call_entered=true`、`gpu_generate_call_returned=true`。
- `gpu_callback_invoked_count=0` のまま、LiteRT-LM から `LiteRtLmJniException` が出る。
- 例外は `Status Code: 13` / `Failed to invoke the compiled model` / `runtime/executor/llm_litert_compiled_model_executor.cc:735`。
- `no_sampler` でも同じ Status Code 13 になるため、TopK sampler 単独原因の優先度は下がる。
- `max_tokens_1` は `Status Code: 3` / `Input token ids are too long` になる。LiteRT-LM では max token が入力 token 長または total budget に効くように見えるため、GPU root cause 判定には使わない。

Phase 4 では、Status Code 13 を 60秒 watchdog timeout まで待たず、即時の generate failure として扱う。compact / LOCAL_ROUTE_DIAG には以下を追加する。

- `gpu_generate_exception_seen`
- `gpu_generate_exception_class`
- `gpu_generate_exception_message_raw`
- `gpu_generate_exception_message_sanitized`
- `gpu_generate_exception_status_code`
- `gpu_generate_exception_error_file`
- `gpu_generate_exception_error_line`
- `gpu_generate_exception_summary`
- `gpu_generate_failed_before_first_token`
- `gpu_watchdog_bypassed_due_to_generate_exception`
- `litert_lm_error_kind`
- `litert_lm_error_status_code`
- `litert_lm_error_primary_file`
- `litert_lm_error_primary_line`
- `litert_lm_error_secondary_file`
- `litert_lm_error_secondary_line`
- `litert_lm_error_recoverability_hint`

現在の Status Code 13 の期待分類:

- `failure_stage=gpu_generate_compiled_model_invoke_failed`
- `gpu_generate_exception_status_code=13`
- `gpu_generate_exception_error_file=runtime/executor/llm_litert_compiled_model_executor.cc`
- `gpu_generate_exception_error_line=735`
- `gpu_generate_exception_summary=failed_to_invoke_compiled_model`
- `litert_lm_error_kind=compiled_model_invoke_failed`
- `litert_lm_error_recoverability_hint=try_gpu_runtime_stack_alignment`
- `gpu_watchdog_bypassed_due_to_generate_exception=true`

DEV probe mode:

```sh
adb shell setprop debug.lami.gpu_generate_probe_mode raw_callback_only
adb shell setprop debug.lami.gpu_generate_probe_mode no_sampler
adb shell setprop debug.lami.gpu_generate_probe_mode ascii_prompt_no_sampler
adb shell setprop debug.lami.gpu_generate_probe_mode max_tokens_16
adb shell setprop debug.lami.gpu_generate_probe_mode max_tokens_32
adb shell setprop debug.lami.gpu_generate_probe_mode cache_dir_app_files_no_sampler
adb shell setprop debug.lami.gpu_generate_probe_mode cache_dir_null_no_sampler
```

`max_tokens_1` は残すが、Status Code 3 の token budget 確認用であり、主実験には使わない。`max_tokens_16` を `hi` / `こんにちは` 向けの最小寄り budget として使う。

CPU/GPU callback 比較は opt-in のみ:

```sh
adb shell setprop debug.lami.compare_cpu_gpu_callback true
```

CPU 側が同じ prompt/model で callback を返し、GPU 側だけ Status Code 13 の場合は `cpu_gpu_generate_diff=cpu_callback_ok_gpu_compiled_model_invoke_failed` として扱う。

現在の root split:

- GPU compiled model invoke / prefill / decode / KV cache / delegate 実行の問題。
- public `Backend.GPU` と Edge Gallery 内部の `GPU_ARTISAN` / executor selection 差分。
- `libLiteRt.so` / `liblitertlm_jni.so` を含む runtime stack 差分。

## GPU Phase 5: Runtime stack alignment

Phase 5 では、GPU 失敗を runtime stack / executor selection / compiled model invoke requirement の差分として整理する。
CPU route は同一 model で成功しており、UI callback、watchdog、prompt language、TopK sampler、sampler 無効化、cache dir、max tokens、model corruption は主因候補から下がっている。

追加 artifact:

- `artifacts/edge_gallery_static/runtime_alignment_summary.md`
- `artifacts/edge_gallery_static/runtime_stack_diff.md`

Edge Gallery 静的抽出で確認した executor / runtime evidence:

- `LlmGpuArtisanExecutor`
- `LlmLiteRtCompiledModelExecutor`
- `LlmLiteRtCompiledModelExecutorDynamic`
- `GPU_ARTISAN`
- `CPU_ARTISAN`
- `GOOGLE_TENSOR_ARTISAN`
- `Artisan model detected. Switching backend from GPU to GPU_ARTISAN.`
- `backend constraint mismatch. Model requires one of [`
- `Preferred engine types: [`
- `LiteRtRegisterGpuAccelerator`
- `LiteRtGpuEnvironmentCreate`
- `Statically linked GPU accelerator registered.`
- `tflite_gpu_kv_cache`
- `tflite_opencl_kv_cache`

LAMI 側の現時点の public API reflection:

- public backend candidates は `CPU,GPU,NPU`。
- `GPU_ARTISAN` / `CPU_ARTISAN` / `GOOGLE_TENSOR_ARTISAN` は public `Backend` 候補として見えていない。
- `RuntimeConfig` / `BackendConstraint` / `PreferredEngineType` / executor selection は追加 reflection key で継続確認する。

Phase 5 で compact / LOCAL_ROUTE_DIAG / DEV 推論統計に追加する key:

- `litert_runtime_executor_candidates`
- `litert_runtime_executor_selection_hint`
- `litert_runtime_backend_constraint_hint`
- `litert_runtime_compiled_model_executor_hint`
- `litert_runtime_gpu_executor_hint`
- `litert_runtime_artisan_evidence`
- `litert_compiled_model_executor_failure_category`

`runtime/executor/llm_litert_compiled_model_executor.cc:735` の現在の分類:

- `litert_lm_error_kind=compiled_model_invoke_failed`
- `litert_compiled_model_executor_failure_category=compiled_model_invoke`
- `litert_lm_error_recoverability_hint=try_gpu_runtime_stack_alignment`

解釈:

- Edge Gallery と LAMI の `libLiteRt.so` / `liblitertlm_jni.so` は SHA / build ID / size が一致していない。
- Edge Gallery には artisan executor と backend constraint / preferred engine type の静的 evidence がある。
- LAMI の public `Backend.GPU` route は、Edge Gallery が実際に使っている可能性のある `GPU_ARTISAN` / internal executor selection と同一とは限らない。
- 現在の LAMI 失敗は、public compiled model executor path での generate invoke failure として扱うのが妥当。
- `libLiteRt.so` / `liblitertlm_jni.so` の単体差し替えは禁止。検証する場合は別 flavor で runtime stack 全体を隔離する。

次の推奨実験:

1. `debug.lami.compare_cpu_gpu_callback=true` を維持し、CPU callback success / GPU Status Code 13 を同一 prompt で再確認する。
2. `litert_runtime_*` key で public API に executor selection / backend constraint surface が見えるか確認する。
3. Edge Gallery 実 model file の同一性を確認する。
4. public API から `GPU_ARTISAN` 相当へ到達できない場合は、通常 route へ雑に適用せず、別 flavor で Edge Gallery 同等 runtime/API stack を隔離検証する。

## GPU Phase 6: model identity and runtime stack isolation

Phase 6 の目的は、Edge Gallery で GPU が動いているように見える条件と、LAMI の public `Backend.GPU` が `Status Code 13` / compiled model invoke failure になる条件を、モデル同一性・runtime stack・executor selection の3点で分離すること。

現時点の goal matrix:

| Target | Model | Backend | Status |
| --- | --- | --- | --- |
| Generic E2B CPU | `gemma-4-E2B-it.litertlm` | CPU | working |
| Generic E2B GPU | `gemma-4-E2B-it.litertlm` | GPU | investigating |
| SM8750 E2B NPU | `gemma-4-E2B-it_qualcomm_sm8750.litertlm` | Qualcomm / NPU | target, gated |

Current GPU finding:

- CPU callback comparison succeeds: `cpu_gpu_generate_diff=cpu_callback_ok_gpu_compiled_model_invoke_failed`。
- GPU route fails as `failure_stage=gpu_generate_compiled_model_invoke_failed`。
- Parsed failure is `Status Code 13` at `runtime/executor/llm_litert_compiled_model_executor.cc:735` with `gpu_generate_exception_summary=failed_to_invoke_compiled_model`。
- `litert_compiled_model_executor_failure_category=compiled_model_invoke`。
- UI callback handling、watchdog、prompt language、sampler / TopK、cache dir、model corruption、CPU route は主因候補から下がっている。

追加 artifact:

- `artifacts/edge_gallery_static/model_identity_report.md`
- `artifacts/edge_gallery_static/full_native_runtime_diff.md`
- `artifacts/edge_gallery_static/gpu_artisan_access_path.md`
- `docs/litert_lm_gallery_stack_gpu_probe_plan.md`

生成:

```bash
scripts/inspect_edge_gallery_model_identity.sh --dry-run
scripts/inspect_edge_gallery_model_identity.sh
```

Edge Gallery model identity:

- APK/split の静的 strings には `Gemma 4`、`E2B`、`E4B`、`.litertlm`、`https://huggingface.co/litert-community` などの手掛かりがある。
- APK/split の entry list には packaged `.litertlm` model binary は見つかっていない。
- ただし APK/split 静的情報だけでは、Edge Gallery が実機で使った model file の exact filename / size / SHA-256 / source URL は確定できない。
- したがって `same_model_claim=not_supported_by_static_apk` と扱う。LAMI の `1781343249464_gemma-4-E2B-it.litertlm` と同一モデルだとは、size/hash/source evidence なしに断定しない。

GPU_ARTISAN access path:

- Edge Gallery static evidence には `GPU_ARTISAN`、`CPU_ARTISAN`、`GOOGLE_TENSOR_ARTISAN`、`LlmGpuArtisanExecutor`、`Artisan model detected. Switching backend from GPU to GPU_ARTISAN.` がある。
- LAMI runtime reflection では public backend candidates は `CPU,GPU,NPU` のみ。
- 現時点では `GPU_ARTISAN` は LAMI public API から到達できる経路として確認できていない。
- 静的 evidence は、Edge Gallery runtime 内部または model metadata/backend constraint による executor selection の可能性を示すが、通常チャットへ `GPU_ARTISAN` を即適用する根拠にはしない。

Runtime stack isolation:

- LiteRT-LM が Android GPU execution を持つことは静的 evidence と Edge Gallery 観察から plausible。
- 一方、LAMI の current public `Backend.GPU` path は compiled model invoke で失敗している。
- 次の安全な道筋は、`libLiteRt.so` / `liblitertlm_jni.so` の単体差し替えではなく、model identity 確認と full runtime stack isolation。
- 将来検証する場合は `galleryStackGpuProbe` のような DEV-only 別 flavor / 別 applicationId / 別 native lib source dir / 別 model directory で行い、`standardDebug` と production route には触れない。

Phase 6 の次アクション:

1. Edge Gallery app data または公式 download source から、実 model file の size / SHA-256 / filename を確認する。
2. LAMI selected model と Edge Gallery model が同一か判定する。
3. 同一モデルであれば、full runtime stack isolation flavor の設計に進む。
4. モデルが違う場合は、GPU成否を runtime差分と判断する前にモデル差分を潰す。
5. public API で `GPU_ARTISAN` へ到達できない場合は、Edge Gallery 同等 runtime/API stack を隔離 flavor でのみ検証する。

## GPU Phase 7: galleryStackGpuProbe implementation

Phase 7 では DEV-only の isolated flavor `galleryStackGpuProbe` を追加する。目的は、Edge Gallery model identity が確認できた後も LAMI standard public `Backend.GPU` path が `cc:735` compiled model invoke failure になるため、standardDebug を触らず runtime stack isolation を試せる APK を作ること。

確認済み model identity:

- Edge Gallery model path: `/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm`
- `modelId=litert-community/gemma-4-E2B-it-litert-lm`
- `commitHash=6e5c4f1e395deb959c494953478fa5cec4b8008f`
- `sizeInBytes=2588147712`
- `accelerators=gpu,cpu`
- `visionAccelerator=gpu`
- `topK=64`
- `topP=0.95`
- `temperature=1.0`
- `maxTokens=4000`
- `maxContextLength=32000`
- `capabilities=llm_thinking,speculative_decoding`

Model-only test result:

- Edge Gallery E2B model works in LAMI CPU route.
- Same model still fails in LAMI GPU route with `failure_stage=gpu_generate_compiled_model_invoke_failed` and `gpu_generate_exception_status_code=13`.
- Therefore model corruption or model identity mismatch is no longer enough to explain the GPU failure.

Added flavor:

- Flavor: `galleryStackGpuProbe`
- Debug task: `./gradlew :app:assembleGalleryStackGpuProbeDebug`
- Install task: `./gradlew :app:installGalleryStackGpuProbeDebug`
- Application id suffix: `.gallerystackgpu`
- Version suffix: `-galleryStackGpuProbe`
- Native source set: `app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/`
- Release variant disabled.

Native staging:

```bash
scripts/stage_gallery_stack_gpu_probe_native_libs.sh
scripts/stage_gallery_stack_gpu_probe_native_libs.sh --stage
```

The default mode is report-only. `--stage` extracts `lib/arm64-v8a/*.so` from `artifacts/external/edge_gallery_apks/` into the probe flavor source set and writes:

```text
artifacts/gallery_stack_gpu_probe/native_lib_manifest.tsv
```

The script refuses to stage into standard/shared source sets. Staged `.so` files under `app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/` are gitignored and intentionally not committed.

DEV opt-in property:

```bash
adb shell setprop debug.lami.gallery_stack_gpu_probe true
```

When false, the flavor runs safely and does not force GPU. When true and backend is GPU, LAMI applies the Edge Gallery allowlist profile that is safe through the current public API:

- `topK=64`
- `topP=0.95`
- `temperature=1.0`
- `maxTokens=4000`

The flavor does not fake unsupported APIs:

- `gallery_stack_probe_thinking_api_available=false`
- `gallery_stack_probe_speculative_decoding_api_available=false`

Diagnostics added to compact diagnostics, `LOCAL_ROUTE_DIAG`, and developer inference stats:

- `gallery_stack_probe_enabled`
- `gallery_stack_probe_application_id`
- `gallery_stack_probe_native_stack_source`
- `gallery_stack_probe_liblitert_sha256`
- `gallery_stack_probe_liblitertlm_jni_sha256`
- `gallery_stack_probe_libs_manifest_present`
- `gallery_stack_probe_edge_gallery_model_expected`
- `gallery_stack_probe_model_path`
- `gallery_stack_probe_model_exists`
- `gallery_stack_probe_model_size_bytes`
- `gallery_stack_probe_model_sha256_if_available`
- `gallery_stack_probe_allowlist_config_applied`
- `gallery_stack_probe_runtime_stack_alignment_level`

Suggested test flow:

```bash
./gradlew :app:installGalleryStackGpuProbeDebug
adb shell setprop debug.lami.gallery_stack_gpu_probe true
adb shell setprop debug.lami.compare_cpu_gpu_callback true
adb shell setprop debug.lami.gpu_generate_probe_mode raw_callback_only
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
```

Manual model selection:

```text
/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm
```

Expected interpretation:

- GPU succeeds: model/runtime stack alignment likely fixed the issue.
- GPU still fails at `cc:735`: public `Backend.GPU` or inaccessible `GPU_ARTISAN`/internal executor remains the blocker.
- GPU fails earlier at load/init: staged native stack is incompatible with current app packaging or dependency graph.

Rollback:

- Uninstall `io.github.ninbyo02.lami.gallerystackgpu`.
- Clear `debug.lami.gallery_stack_gpu_probe`.
- Remove local staged `.so` files from `app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a/`.

## GPU Phase 9: callback streaming promotion

`galleryStackGpuProbe` で `debug.lami.gpu_generate_probe_mode=callback_to_ui` を使うと、Edge Gallery E2B model の GPU callback text を LAMI の assistant UI へ promote できることを確認した。

Confirmed result:

- `selected_backend=GPU`
- `route_family=local_gpu`
- `status=success`
- `stage=generate_streaming_completed`
- `first_token_received=true`
- `gpu_callback_non_empty_text_count=12`
- `gpu_callback_text_promoted_to_ui=true`
- `gpu_ui_append_finished=true`
- `gpu_streaming_completion_reason=flow_completed_non_empty_response`
- TTFT: `370 ms`
- backend speed: `37.4 token/s`

Phase 9 では `callback_to_ui` を診断 mode として残したまま、同じ内部 callback streaming path を使う候補 mode `normal_callback_streaming` を追加した。これは normal GPU route へ昇格する前の DEV opt-in mode で、GPU callback text を通常 assistant message に append し、non-empty response を success として扱う。

Manual check:

```bash
adb shell setprop debug.lami.gallery_stack_gpu_probe true
adb shell setprop debug.lami.compare_cpu_gpu_callback true
adb shell setprop debug.lami.gpu_generate_probe_mode normal_callback_streaming
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
```

Expected success diagnostics:

- `debug_lami_gpu_generate_probe_mode=normal_callback_streaming`
- `gpu_callback_to_ui_enabled=true`
- `gpu_callback_text_promoted_to_ui=true`
- `gpu_ui_append_finished=true`
- `gpu_streaming_completion_reason=flow_completed_non_empty_response`
- `failure_stage=none`

Current limitation:

- GPU remains explicit user/dev selection only.
- `normal_callback_streaming` is still DEV opt-in.
- Standard UI should not make GPU the default until repeated stability checks cover app restart, multiple prompts, longer responses, and failure cleanup.

Next promotion gate:

- Run repeated manual checks in `galleryStackGpuProbe` with the Edge Gallery E2B model.
- Confirm Copy Compact / stats details preserve callback streaming diagnostics on success and failure.
- If stable, promote the shared callback streaming implementation to the normal GPU route while keeping the older failure diagnostics intact.

## GPU Phase 10: guarded normal route candidate

`normal_callback_streaming` は短い応答と長めの応答で複数回成功した。

Confirmed examples:

- Short response: TTFT `259 ms`, backend speed `36.3 token/s`.
- Longer response: `output_tokens=457`, `gpu_callback_non_empty_text_count=461`, `held_engine_reused=true`, TTFT `486 ms`, backend speed `25.5 token/s`.

Phase 10 では、probe mode を `normal` のまま callback streaming 実装を使う DEV-only gate を追加した。

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
```

Expected guarded normal route diagnostics:

- `debug_lami_gpu_generate_probe_mode=normal`
- `gpu_normal_route_use_callback_streaming=true`
- `gpu_callback_streaming_path_selected=true`
- `gpu_callback_streaming_path_reason=dev_gate_normal_route`
- `gpu_callback_streaming_success_count=1`
- `gpu_callback_streaming_non_empty_callback_count>0`
- `gpu_callback_streaming_done_true_seen=true`
- `gpu_callback_streaming_reused_held_engine=true` when the holder is reused
- `gpu_callback_streaming_completion_reason=flow_completed_non_empty_response`
- `gpu_callback_streaming_failure_reason=none`
- `gpu_callback_text_promoted_to_ui=true`
- `gpu_ui_append_finished=true`
- `failure_stage=none`

Manual sequence:

```bash
adb shell setprop debug.lami.gallery_stack_gpu_probe true
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
```

Test prompts:

1. `こんにちは`
2. `カレーの材料をお願いします。`
3. `さっぱり系で`

This is still not a production promotion. GPU remains explicit user/dev selection, and the callback streaming route remains behind the DEV property until repeated stability checks pass on `galleryStackGpuProbe` and the standardDebug model/runtime comparison is resolved.

## StandardDebug Edge Gallery E2B model probe

Phase 11 compares model identity and runtime stack separately. The successful `galleryStackGpuProbe` path used the Edge Gallery E2B model:

- file: `gemma-4-E2B-it.litertlm`
- staged manual name: `gemma-4-E2B-it-edge-gallery.litertlm`
- size: `2588147712`
- sha256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
- commit: `6e5c4f1e395deb959c494953478fa5cec4b8008f`
- modelId: `litert-community/gemma-4-E2B-it-litert-lm`

Use the helper script to verify and stage the model to shared storage:

```bash
scripts/stage_edge_gallery_e2b_model_to_lami_standard.sh --dry-run
scripts/stage_edge_gallery_e2b_model_to_lami_standard.sh
```

Manual fallback:

```bash
adb push \
  artifacts/edge_gallery_model/Gemma_4_E2B_it/gemma-4-E2B-it.litertlm \
  /sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm
```

Install and run standardDebug:

```bash
./gradlew :app:installStandardDebug
adb shell monkey -p io.github.ninbyo02.lami 1
```

Enable the guarded callback streaming route:

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
```

Expected standardDebug probe keys:

- `standard_gpu_probe_expected_edge_gallery_e2b=true`
- `standard_gpu_probe_model_size_bytes=2588147712`
- `standard_gpu_probe_model_sha256_expected=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
- `standard_gpu_probe_model_sha256_actual=device_unavailable`
- `standard_gpu_probe_model_identity_hint=edge_gallery_e2b_expected`
- `standard_gpu_probe_runtime_stack=standardDebug`
- `standard_gpu_probe_callback_streaming_gate=true`
- `standard_gpu_probe_result_candidate=success` or `failure`

Outcome interpretation:

1. `standardDebug + Edge Gallery E2B model + callback streaming` succeeds: model identity was the main blocker.
2. It fails with `runtime/executor/llm_litert_compiled_model_executor.cc:735`: runtime/native stack difference remains the main blocker.
3. It succeeds only in `galleryStackGpuProbe`: keep the isolated runtime stack as the promotion candidate and do not promote the standard route yet.

## GPU Phase 12: standardDebug vs galleryStackGpuProbe native stack split

The current model-separation result is:

- `standardDebug + Edge Gallery E2B model + guarded callback streaming`: `failure_stage=gpu_generate_compiled_model_invoke_failed`, `runtime/executor/llm_litert_compiled_model_executor.cc:735`.
- `galleryStackGpuProbe + the same Edge Gallery E2B model + guarded callback streaming`: GPU success, callback text promoted to UI, roughly `25-38 token/s` in manual checks.

This moves the primary root cause away from model identity. The same model can run on GPU in LAMI when the probe flavor/runtime stack is used, while the standard runtime stack still fails at compiled model invoke. Treat the remaining blocker as a runtime/native stack delta until a new result contradicts it.

Use the APK-to-APK native stack comparison after both variants are assembled:

```bash
./gradlew :app:assembleStandardDebug
./gradlew :app:assembleGalleryStackGpuProbeDebug
scripts/compare_standard_gallery_stack_gpu_probe_native_libs.sh
```

Outputs are written under:

```text
artifacts/gpu_runtime_stack_compare/
```

Important outputs:

- `standard_debug_native_libs.tsv`: final `standardDebug` APK `lib/arm64-v8a` inventory.
- `gallery_stack_gpu_probe_native_libs.tsv`: final `galleryStackGpuProbeDebug` APK `lib/arm64-v8a` inventory.
- `native_lib_diff.tsv`: presence, size, sha256, build id, NEEDED, category, and classification.
- `needed_dependency_edges.tsv`: per-library dynamic dependency edges.
- `gpu_runtime_stack_classification.md`: focused interpretation for GPU runtime candidates.

Latest local comparison summary:

- Compared `29` arm64 native libraries.
- Same-name SHA-256 differences: `9`.
- Presence differences: `3`.
- High-priority runtime candidates: `5`.
- `libLiteRt.so`: present in both, but size/SHA/build-id differ.
- `liblitertlm_jni.so`: present in both, but size/SHA/build-id differ.
- `libLiteRtDispatch_Qualcomm.so`: present in `standardDebug`, absent from `galleryStackGpuProbeDebug`.
- `libLiteRtCompilerPlugin_Qualcomm.so`: present in `standardDebug`, absent from `galleryStackGpuProbeDebug`.
- `libGemmaModelConstraintProvider.so`: present in `standardDebug`, absent from `galleryStackGpuProbeDebug`.
- QNN libs differ in several places, including `libQnnGpu.so`, but they remain lower priority for generic GPU because the working Gallery-style path does not point to QNN GPU as the root requirement.

Classification guidance:

| Area | Current priority | Interpretation |
| --- | --- | --- |
| `libLiteRt.so` | Highest | Core LiteRT runtime. If it differs, review only as part of a matched full stack. |
| `liblitertlm_jni.so` | Highest | Core LiteRT-LM JNI/runtime bridge. Do not single-swap. |
| `libLiteRtDispatch_Qualcomm.so` | High review | Dispatch/runtime extension candidate. Review with the matched core runtime pair. |
| `libLiteRtCompilerPlugin_Qualcomm.so` | High review | Compiler/plugin candidate. Review with the matched core runtime pair. |
| `libGemmaModelConstraintProvider.so` | High review | Model constraint/provider candidate. Review with runtime stack, not alone. |
| QNN libraries | Lower for generic GPU | Keep visible for Qualcomm/NPU coupling, but `libQnnGpu.so` remains weakly supported as a generic GPU root cause because Edge Gallery GPU evidence does not point to QNN GPU. |
| Other Edge Gallery-derived libraries | Review | Treat presence/build-id differences as full-stack alignment candidates only if they are in the probe APK and linked by core runtime libraries. |
| Unrelated support libraries | Low | Mismatch can matter indirectly, but they are not first-order GPU executor candidates. |

Next-phase DEV-only promotion plan:

1. Do not change `standardDebug` production defaults.
2. Continue repeated stability checks in `galleryStackGpuProbe` with `debug.lami.gpu_normal_route_use_callback_streaming=true`.
3. Use `native_lib_diff.tsv` to identify the minimal full-stack candidate set, starting from `libLiteRt.so` + `liblitertlm_jni.so` and any directly related dispatch/compiler/model-constraint members.
4. Do not replace a single `.so`. If promotion is tested, test a full matched runtime stack in a separate DEV flavor/application id first.
5. Promote nothing back to `standardDebug` until the full stack candidate has repeated success, diagnostics remain clean, and provenance/licensing are documented.

## GPU Phase 13: gpuRuntimeAlignmentProbe promotion candidate

Phase 13 adds a DEV-only flavor:

- Flavor: `gpuRuntimeAlignmentProbe`
- Debug task: `./gradlew :app:assembleGpuRuntimeAlignmentProbeDebug`
- Install task: `./gradlew :app:installGpuRuntimeAlignmentProbeDebug`
- Application id suffix: `.gpualignment`
- Native source set: `app/src/gpuRuntimeAlignmentProbeDebug/jniLibs/arm64-v8a/`

The flavor is a promotion candidate harness for the runtime/native stack that is already proven in `galleryStackGpuProbe`. It does not change `standardDebug`, CPU route, NPU S1 route, fallback behavior, or production defaults.

The current conclusion remains:

- The Edge Gallery E2B model itself is not the blocker.
- `galleryStackGpuProbe` has proven GPU callback streaming success with that model.
- `standardDebug` still fails with `runtime/executor/llm_litert_compiled_model_executor.cc:735`.
- Therefore the active blocker is runtime/native stack alignment, not model identity.

Promotion candidate rules:

- Treat `libLiteRt.so` + `liblitertlm_jni.so` as a matched core pair.
- Review `libLiteRtDispatch_Qualcomm.so`, `libLiteRtCompilerPlugin_Qualcomm.so`, and `libGemmaModelConstraintProvider.so` only with the matched core pair.
- Do not single-swap `.so` files.
- Do not stage native libraries into `standardDebug`.
- Do not enable GPU by default.

`gpuRuntimeAlignmentProbe` emits these additional keys:

- `runtime_alignment_probe_flavor`
- `runtime_alignment_stack_source`
- `runtime_alignment_liblitert_sha256`
- `runtime_alignment_liblitertlm_jni_sha256`
- `runtime_alignment_dispatch_qualcomm_present`
- `runtime_alignment_compiler_plugin_qualcomm_present`
- `runtime_alignment_gemma_constraint_provider_present`
- `runtime_alignment_result_candidate`
- `runtime_alignment_success_gate`

It also preserves the GPU callback streaming and failure keys:

- `gpu_callback_streaming_path_selected`
- `gpu_callback_text_promoted_to_ui`
- `gpu_ui_append_finished`
- `gpu_streaming_completion_reason`
- `failure_stage`
- `litert_lm_error_kind`
- `gpu_litert_executor_error_file`
- `gpu_litert_executor_error_line`

Suggested runtime alignment test setup:

```bash
./gradlew :app:installGpuRuntimeAlignmentProbeDebug
adb shell setprop debug.lami.runtime_alignment_probe true
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell monkey -p io.github.ninbyo02.lami.gpualignment 1
```

Use the Edge Gallery E2B model:

```text
/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm
```

Stability checklist before any standardDebug promotion:

| Check | Expected result |
| --- | --- |
| App restart then first GPU request | Succeeds with `runtime_alignment_result_candidate=success`. |
| Held engine reuse | Later turn shows reuse without stale callback or previous invocation errors. |
| Short prompt: `こんにちは` | Assistant text appears; callback text promoted to UI. |
| Medium prompt: `カレーの材料をお願いします` | Success; stats visible. |
| Long prompt: `キーマカレーの材料を詳しく` | Success; no `cc:735`. |
| Continuous 3-5 turns | No holder leak, stale callback, or forced CPU fallback. |
| Failure cleanup | GPU failure remains visible in diagnostics and next run is safe. |
| CPU fallback impact | CPU route remains unchanged and explicit only. |
| NPU S1 impact | NPU S1 route remains gated and unchanged. |

Only after repeated clean `gpuRuntimeAlignmentProbe` runs should a separate standardDebug promotion design be considered.

## GPU Phase 14: runtime alignment holder reuse and cleanup diagnostics

`gpuRuntimeAlignmentProbe` has already shown GPU callback streaming success across short, medium, longer, and multi-turn
manual checks when the Edge Gallery E2B model, runtime alignment stack, callback streaming path, and allowlist-derived
config are used. This is still a DEV-only probe result. It is not a standardDebug or production promotion.

The remaining runtime-alignment questions are holder reuse, success cleanup, and promotion readiness. Phase 14 adds
explicit holder alignment keys to `LOCAL_ROUTE_DIAG`, compact diagnostics, and inference stats detail:

- `gpu_alignment_holder_present_before_acquire`
- `gpu_alignment_holder_acquire_result`
- `gpu_alignment_holder_reused`
- `gpu_alignment_holder_created`
- `gpu_alignment_holder_cleared`
- `gpu_alignment_holder_clear_reason`
- `gpu_alignment_holder_close_started`
- `gpu_alignment_holder_close_finished`
- `gpu_alignment_holder_reuse_block_reason`
- `gpu_alignment_holder_model_path_changed`
- `gpu_alignment_holder_backend_changed`
- `gpu_alignment_holder_app_process_start_marker`
- `gpu_alignment_turn_index_if_available`
- `gpu_alignment_previous_turn_success`
- `gpu_alignment_previous_turn_failure_stage`

`gpu_alignment_holder_reuse_block_reason` is classified as:

- `reuse_ok`
- `first_turn_no_previous_holder`
- `model_path_changed`
- `backend_changed`
- `holder_cleared_after_success`
- `holder_cleared_after_failure`
- `app_process_restarted`
- `explicit_debug_no_held_engine`
- `unsupported_or_unknown`

Code inspection found explicit holder clear paths for timeout/failure and lifecycle transitions such as model/backend
change, app background/idle timeout, low memory, TTS playback, and manual recreate. No normal GPU callback streaming
success-only holder clear path was added or promoted. Success cleanup remains observable through
`gpu_alignment_holder_cleared` and `gpu_alignment_holder_clear_reason`; failure/timeout cleanup remains preserved.

Production promotion remains blocked until all of these gates are checked on device:

1. App force-stop/restart then first GPU request succeeds.
2. Same model succeeds for 3-5 continuous turns.
3. Second and later turns show `gpu_alignment_holder_reused=true` / `reuse_ok`, or a clear non-reuse reason.
4. Long output of 800+ tokens succeeds.
5. Failure after a forced or natural GPU error runs holder cleanup and the next run starts safely.
6. CPU route success is unchanged.
7. NPU S1 remains gated and unchanged.
8. `standardDebug` may still fail because its native runtime stack is intentionally not promoted.

## GPU Phase 12: Standard dev-gated runtime alignment candidate

Phase 1 of Standard integration adds a DEV-only candidate gate for the Standard app:

```bash
adb shell setprop debug.lami.standard_gpu_runtime_alignment_candidate true
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
```

The gate is off by default. When it is unset, Standard app behavior remains unchanged and existing GPU
`cc:735` failure diagnostics remain the baseline. When it is enabled, the candidate path is eligible only when:

- selected backend is `GPU`;
- selected model looks like Edge Gallery E2B;
- model size is `2588147712` bytes when the file size is available;
- `debug.lami.gpu_normal_route_use_callback_streaming=true`;
- no existing active generation or model/backend switch gate reports a block;
- callback streaming diagnostics remain enabled.

New diagnostics:

- `standard_gpu_runtime_alignment_candidate_enabled`
- `standard_gpu_runtime_alignment_candidate_eligible`
- `standard_gpu_runtime_alignment_candidate_block_reason`
- `standard_gpu_runtime_alignment_candidate_model_size_bytes`
- `standard_gpu_runtime_alignment_candidate_model_identity_hint`
- `standard_gpu_runtime_alignment_candidate_runtime_stack`
- `standard_gpu_runtime_alignment_candidate_result`

Manual Standard verification:

```bash
adb shell setprop debug.lami.standard_gpu_runtime_alignment_candidate true
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell monkey -p io.github.ninbyo02.lami 1
```

Use the Edge Gallery E2B model and GPU backend. Test prompts:

1. `こんにちは`
2. `カレーの材料をお願いします。`
3. `さっぱり系でお願いします。`
4. `分量も教えてください。`

Expected success indicators:

- `selected_backend=GPU`
- `route_family=local_gpu`
- `failure_stage=none`
- `standard_gpu_runtime_alignment_candidate_enabled=true`
- `standard_gpu_runtime_alignment_candidate_eligible=true`
- `gpu_callback_streaming_path_selected=true`
- `gpu_callback_text_promoted_to_ui=true`
- `gpu_ui_append_finished=true`

Rollback:

```bash
adb shell setprop debug.lami.standard_gpu_runtime_alignment_candidate false
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming false
adb shell setprop debug.lami.gpu_generate_probe_mode normal
```

This is still not production promotion. Standard GPU remains off by default; native runtime stack promotion is still
prohibited outside an explicit full-stack alignment plan.

### Standard candidate result after Phase 1

Latest Standard app probe result:

- `standard_gpu_runtime_alignment_candidate_enabled=true`
- `standard_gpu_runtime_alignment_candidate_eligible=true`
- `standard_gpu_runtime_alignment_candidate_result=failure`
- `failure_stage=gpu_generate_compiled_model_invoke_failed`
- `gpu_litert_executor_error_file=runtime/executor/llm_litert_compiled_model_executor.cc`
- `gpu_litert_executor_error_line=735`

The same Edge Gallery E2B model succeeds in `gpuRuntimeAlignmentProbeDebug` with callback streaming and held-engine
reuse. This confirms the current blocker is not model identity. Treat the blocker as a runtime/native stack mismatch
between `standardDebug` and `gpuRuntimeAlignmentProbeDebug`.

New loaded runtime stack diagnostics are emitted for GPU routes:

- `runtime_stack_loaded_source_flavor`
- `runtime_stack_loaded_native_library_dir`
- `runtime_stack_loaded_native_stack_source`
- `runtime_stack_loaded_liblitert_present`
- `runtime_stack_loaded_liblitert_sha256`
- `runtime_stack_loaded_liblitertlm_jni_present`
- `runtime_stack_loaded_liblitertlm_jni_sha256`
- `runtime_stack_loaded_dispatch_qualcomm_present`
- `runtime_stack_loaded_dispatch_qualcomm_sha256`
- `runtime_stack_loaded_compiler_plugin_qualcomm_present`
- `runtime_stack_loaded_compiler_plugin_qualcomm_sha256`
- `runtime_stack_loaded_gemma_constraint_provider_present`
- `runtime_stack_loaded_gemma_constraint_provider_sha256`
- `runtime_stack_loaded_full_stack_candidate_unit`
- `runtime_stack_alignment_interpretation`

Expected interpretation for the Standard failure above:

```text
runtime_stack_alignment_interpretation=standard_runtime_stack_mismatch_candidate
standard_gpu_runtime_stack_mismatch_summary=runtime_stack_mismatch_suspected
standard_gpu_runtime_stack_promotion_blocked_reason=standard_runtime_stack_not_aligned
```

Expected interpretation for the successful runtime alignment probe:

```text
runtime_stack_alignment_interpretation=runtime_alignment_probe_stack_success
```

Compare the final packaged Standard and runtime alignment probe native stacks with:

```bash
./gradlew :app:assembleStandardDebug
./gradlew :app:assembleGpuRuntimeAlignmentProbeDebug
scripts/compare_standard_gpu_runtime_alignment_probe_native_libs.sh
```

The script writes under `artifacts/gpu_runtime_stack_compare/`:

- `standard_debug_native_libs.tsv`
- `gpu_runtime_alignment_probe_native_libs.tsv`
- `standard_vs_gpu_runtime_alignment_native_lib_diff.tsv`
- `standard_vs_gpu_runtime_alignment_needed_edges.tsv`
- `standard_vs_gpu_runtime_alignment_stack_classification.md`
- `standard_to_gpu_runtime_alignment_probe.md`

Latest local comparison:

- Compared `29` arm64 native libraries.
- Same-name SHA-256 differences: `9`.
- Presence differences: `3`.
- High-priority runtime candidates: `5`.
- `libLiteRt.so`: `standardDebug` size `5405080`, build id `a03032ad1eeefda446478aea308c2ed0`; `gpuRuntimeAlignmentProbeDebug` size `5046960`, build id `80fa0688ac32301185275c903cec97bd`.
- `liblitertlm_jni.so`: `standardDebug` size `55249224`, build id `89aac06377e25627695d408eb12ae8cd`; `gpuRuntimeAlignmentProbeDebug` size `15370288`, build id `c2c27170ba409dbd0bc01820fa738580`.
- `libLiteRtDispatch_Qualcomm.so`, `libLiteRtCompilerPlugin_Qualcomm.so`, and `libGemmaModelConstraintProvider.so` are present in `standardDebug` and absent from `gpuRuntimeAlignmentProbeDebug`.

The promotion candidate remains a full stack unit:

```text
libLiteRt.so + liblitertlm_jni.so + dispatch/compiler/model-constraint members
```

Do not test or promote any individual `.so` into `standardDebug`.

Standard candidate now also emits:

- `standard_gpu_runtime_stack_mismatch_high_priority_candidates`
- `standard_gpu_runtime_stack_mismatch_summary`
- `standard_gpu_runtime_stack_required_alignment_unit`
- `standard_gpu_runtime_stack_single_so_swap_forbidden=true`
- `standard_gpu_runtime_stack_promotion_blocked_reason`

The Standard integration order remains:

1. Phase 1: DEV-gated candidate, OFF by default.
2. Phase 2: safety soak in `gpuRuntimeAlignmentProbeDebug` and Standard candidate diagnostics.
3. Phase 3: explicit Experimental GPU UI toggle only after clean soak.
4. Phase 4: production consideration only after runtime/native stack provenance, packaging, and regression gates pass.

Rollback remains property-only for Standard:

```bash
adb shell setprop debug.lami.standard_gpu_runtime_alignment_candidate false
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming false
adb shell setprop debug.lami.gpu_generate_probe_mode normal
```

Standard promotion risk checklist:

- License/provenance for staged LiteRT-LM/LiteRT runtime artifacts must be documented before any redistribution.
- Android packaging risks include duplicate native libs, strip warnings, `extractNativeLibs` / legacy packaging behavior,
  and ABI compatibility across `libLiteRt.so`, `liblitertlm_jni.so`, dispatch/compiler plugins, and support libs.
- `standardDebug` must remain unchanged until a separate full-stack promotion design is approved.

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

## 最新の失敗地点整理

Edge Gallery が同一端末上で `Gemma-4-E2B-it` + GPU 応答できているように見える一方、LAMI では通常GPU route と isolated GPU prefill probe で失敗地点が異なる。

通常GPU route:

- `engine_config_build_finished=true`
- `engine_constructor_finished=true`
- `engine_initialize_finished=true`
- `conversation_create_finished=true`
- `generate_started=true`
- `first_token_received=false`
- `gpu_timeout_stage=generate_before_first_token`
- `gpu_watchdog_failure_stage=gpu_watchdog_timeout_generate_before_first_token`

GPU experiment:

- `gpu_no_sampling_acceleration`: NG
- `gpu_cache_dir_app_files`: NG
- `gpu_max_tokens_32`: NG
- いずれも `generate_before_first_token` timeout。
- sampler / cacheDir / max tokens 単独原因説は弱い。

isolated GPU prefill probe:

- `prompt=hi`
- `max_tokens=1`
- `sampler=none`
- `cache_dir=null`
- `probe_isolated_engine_used=true`
- `probe_engine_config_finished=true`
- `probe_engine_initialize_started=true`
- `probe_engine_initialize_finished=false`
- `probe_timeout_stage=engine_initialize`
- `probe_failure_stage=gpu_prefill_probe_engine_initialize_invocation_target_exception`
- `probe_exception_cause_class=com.google.ai.edge.litertlm.LiteRtLmJniException`
- `probe_exception_cause_message=Failed_to_create_engine:INTERNAL:ERROR:[runtime/executor/llm_litert_compiled_model_executor.cc:1546]└ERROR:[external/litert/litert/cc/litert_compiled_model.h:1140]`

この差分から、通常 route の held engine lifecycle では `generate_started` まで進むが、probe の isolated engine は `Engine.initialize` で失敗する。次は prompt / sampler / cacheDir / max tokens ではなく、runtime stack、model 実体、API 呼び出し条件、engine lifecycle の一致度を詰める。

## 三軸比較

### Runtime stack

| 項目 | Edge Gallery | LAMI standardDebug | 状態 |
| --- | --- | --- | --- |
| `libLiteRt.so` | `split_config.arm64_v8a.apk` に存在。GPU accelerator 関連文字列あり。 | APK に存在。GPU accelerator 関連文字列あり。 | 機能欠落ではなさそう。ただし SHA / build id は不一致。 |
| `liblitertlm_jni.so` | `split_config.arm64_v8a.apk` に存在。GPU accelerator 関連文字列あり。 | APK に存在。GPU accelerator 関連文字列あり。 | 機能欠落ではなさそう。ただし SHA / build id は不一致。 |
| `libLiteRtDispatch_Qualcomm.so` | split により存在する場合あり。NPU/Qualcomm dispatch 側の比較対象。 | standardDebug に存在。 | Generic GPU 直接原因かは未確定。 |
| `libLiteRtGpuAccelerator.so` など独立 GPU accelerator `.so` | APK 内に見えていない。 | APK 内に見えていない。 | 両者とも LiteRT / JNI 側へ静的リンクされている可能性が高い。 |
| GPU accelerator strings/symbols | `Statically linked GPU accelerator registered` などを確認。 | 同系統の strings/symbols を確認。 | 一致済み。ただし実装バイナリは一致していない。 |
| `libQnnGpu.so` | Edge Gallery GPU 成功根拠としては見えていない。 | QNN payload として存在する場合がある。 | Generic GPU timeout の主対象から外す。 |
| 単体差し替え | 禁止。 | 禁止。 | `libLiteRt.so` / `liblitertlm_jni.so` はセット依存が強く、片方だけの差し替えはしない。 |

### Model

| 項目 | Edge Gallery | LAMI | 状態 |
| --- | --- | --- | --- |
| 表示モデル名 | 実機観察では `Gemma-4-E2B-it`。 | `gemma-4-E2B-it.litertlm`。 | 表示名は近いが、同一ファイルとは未確認。 |
| ファイル名 | app data / model config 抽出が必要。 | `gemma-4-E2B-it.litertlm`。 | 未確認。 |
| 保存先 | Gallery の model manager 配下、または app external files 配下の可能性。 | LAMI の user selected file path。 | path / mmap / storage 条件差分が残る。 |
| 拡張子 | `.litertlm` と想定するが静的情報だけでは確定しない。 | `.litertlm`。 | 未確認。 |
| サイズ | app data または APK config から確認が必要。 | selected file の診断 key で確認可能。 | 未確認。 |
| soc-specific / SM8750 版 | Gallery が端末別 model / accelerator config を選んでいる可能性あり。 | Generic `gemma-4-E2B-it.litertlm` と `gemma-4-E2B-it_qualcomm_sm8750.litertlm` の比較余地あり。 | 追加検証が必要。 |

Edge Gallery が本当に LAMI と同一 `.litertlm` を GPU で走らせているかは、model file name、size、sha256、保存先、accelerator config を揃えるまで確定しない。`Gemma-4-E2B-it` 表示だけで「同一 model」とは扱わない。

### API 呼び出し条件

| 項目 | Edge Gallery | LAMI `edge_gallery_like` | 状態 |
| --- | --- | --- | --- |
| text backend | `Backend.GPU()` | `Backend.GPU()` | 一致寄せ済み。 |
| `EngineConfig.modelPath` | `model.getPath(context)` | selected model path | path / storage 条件は未一致。 |
| `EngineConfig.cacheDir` | 通常 model path では `null`。`/data/local/tmp` model だけ external files dir。 | `edge_gallery_like` では通常 path で `null`。実験で app files も可。 | 単独原因説は弱いが比較継続。 |
| `maxNumTokens` | 既定 1024。 | 1024。実験で 32。 | 単独原因説は弱い。 |
| sampler | 非NPUでは `SamplerConfig(64, 0.95, 1.0)`。 | Gallery defaults または no sampler 実験。 | no sampler でも NG。 |
| ConversationConfig | 非NPUでは sampler あり。 | sampler あり / なしを診断。 | sampler 単独原因説は弱い。 |
| thinking | default false。 | false として診断。 | 一致寄せ済み。 |
| speculative decoding | default false unless enabled by model/user config。 | false として診断。 | 一致寄せ済み。 |
| vision/audio backend | capability に応じて設定。text-only は null 寄せ。 | `edge_gallery_like_text_only` で null。 | 一致寄せ済み。 |
| held engine reuse | Gallery helper は task/model lifecycle で engine を保持。 | 通常 GPU route は held engine 経由で generate まで進む。 | isolated probe との差が大きい。 |
| isolated engine | Gallery の通常 UI では未確認。 | prefill probe で `Engine.initialize` 例外。 | engine lifecycle / GPU environment 差分の主観測。 |
| initialize timing | helper 内で constructor 後に明示 initialize。 | constructor 後に明示 initialize。 | 呼び出し順は一致寄せ済み。 |

## Edge Gallery static extraction

Edge Gallery split APK 群から、logcat に依存せず静的情報を抽出する。

```bash
scripts/extract_edge_gallery_gpu_static_info.sh --dry-run
scripts/extract_edge_gallery_gpu_static_info.sh
```

既定入力:

- `artifacts/external/edge_gallery_apks/`

既定出力:

- `artifacts/edge_gallery_static/summary.txt`
- `artifacts/edge_gallery_static/apk_inventory.tsv`
- `artifacts/edge_gallery_static/native_lib_inventory.tsv`
- `artifacts/edge_gallery_static/apk_entries/*.entries.txt`
- `artifacts/edge_gallery_static/native_libs/<apk>/`
- `artifacts/edge_gallery_static/strings/all_classes_dex.filtered.txt`
- `artifacts/edge_gallery_static/strings/all_libLiteRt_so.filtered.txt`
- `artifacts/edge_gallery_static/strings/all_liblitertlm_jni_so.filtered.txt`
- `artifacts/edge_gallery_static/strings/all_edge_gallery_gpu_focus.filtered.txt`
- `artifacts/edge_gallery_static/app_data_static_check_instructions.md`

抽出する focus strings:

- model
- accelerator
- gpu
- backend
- delegate
- sampler
- cache
- litert
- gemma
- sm8750
- qualcomm
- opencl / vulkan / webgpu
- qnn / npu / tpu
- conversation / engine / EngineConfig
- max tokens / topK / topP / temperature
- speculative / thinking

app data 静的確認は debuggable build の場合だけ `run-as` で行う。

```bash
adb shell pm list packages | grep -i 'gallery\|edge\|google'
adb shell run-as <edge_gallery_package> find shared_prefs files databases -maxdepth 4 -print
```

`run-as` が不可の場合は、その旨を記録する。logcat は使わない。Edge Gallery APK から native runtime を LAMI へコピーしない。

## Edge Gallery alignment checklist

| 項目 | 状態 | 判定 |
| --- | --- | --- |
| `Backend.GPU()` 使用 | Edge Gallery / LAMI で一致寄せ済み | 一致済み |
| text-only `visionBackend` / `audioBackend` | LAMI `edge_gallery_like_text_only` で null 寄せ済み | 一致済み |
| `maxNumTokens=1024` | LAMI default profile で寄せ済み | 一致済み |
| `SamplerConfig(64, 0.95, 1.0)` | LAMI default profile で寄せ済み | 一致済み |
| thinking / speculative decoding disabled | LAMI 診断上 false | 一致済み |
| GPU accelerator strings/symbols | 両者に存在 | 一致済み |
| `libLiteRt.so` SHA / build id | Edge Gallery と LAMI で不一致 | 不一致 |
| `liblitertlm_jni.so` SHA / build id | Edge Gallery と LAMI で不一致 | 不一致 |
| Edge Gallery 実 model file | file name / size / sha256 / path 未確認 | 未確認 |
| model path / storage / mmap 条件 | Gallery model path と LAMI selected path が未一致 | 未確認 |
| held engine lifecycle | 通常 LAMI route は held engine で generate まで進む。isolated probe は initialize 例外 | 追加検証が必要 |
| `libQnnGpu.so` | Edge Gallery GPU 成功根拠がない | 主対象外 |
| `libLiteRt.so` / `liblitertlm_jni.so` 単体差し替え | ABI / JNI / registration 結合が強い | 禁止 |
| Edge Gallery runtime 移植 | ライセンス / 依存整合 / ABI risk がある | 無断移植禁止 |
| 同一 runtime stack の検証 | 別 flavor / isolated APK でのみ検討 | 追加検証が必要 |

GPU を標準 UI で推奨扱いに戻す条件は、少なくとも同一 model、同一 runtime stack、同一 API 条件の三点が揃った状態で GPU success が確認できること。それまでは GPU は Experimental / 非推奨のまま扱う。

## 次の実験案

実装変更は DEV / diagnostic 限定にする。

1. held engine reuse 前提の prefill probe
   - isolated engine ではなく、通常 route と同じ held engine lifecycle に寄せる。
   - 通常 generate と競合させない。
   - probe 実行中は通常チャット generate を skip し、probe 結果だけを診断へ出す。

2. 通常 held engine に対する軽量 probe
   - `Engine.initialize` 直後、conversation 作成直後、generate 直前のどこで health check が可能か調査する。
   - LiteRT-LM API に安全な no-op / minimal call がない場合は実装しない。

3. model 種類別比較
   - `gemma-4-E2B-it.litertlm`
   - `gemma-4-E2B-it_qualcomm_sm8750.litertlm` が存在する場合
   - E4B 系 model
   - CPU で同一 model が成功することを前提に、GPU だけの failure stage / root cause / timeout stage を比較する。

4. Edge Gallery model 実体確認
   - app data から model file name / size / sha256 / path / accelerator preference を確認する。
   - LAMI の selected model と一致しない場合は、まず model 差分を原因候補として扱う。

5. 別 flavor での同一 runtime stack 隔離検証
   - 将来案として、Edge Gallery と同一 LiteRT-LM runtime stack を別 flavor / 別 APK に隔離して検証する。
   - standardDebug の本経路、NPU S1、CPU held-official-flow、fallback には混ぜない。
   - `libLiteRt.so` / `liblitertlm_jni.so` の単体差し替えではなく、依存セット全体として検証する。

## Backend / executor selection hypothesis

Edge Gallery 静的抽出では、単純な `Backend.GPU()` 以外に artisan executor 系の分岐が存在することを示す文字列が見えている。

代表的な静的証拠:

- `Supported backends are: [CPU, GPU, NPU, GPU_ARTISAN, CPU_ARTISAN, GOOGLE_TENSOR_ARTISAN]`
- `Artisan model detected. Switching backend from GPU to GPU_ARTISAN.`
- `LlmGpuArtisanExecutor`
- `LlmGpuArtisanExecutor::Create`
- `LlmGpuArtisanExecutor::Prefill`
- `LlmLiteRtCompiledModelExecutor`
- `LlmLiteRtCompiledModelExecutorDynamic`
- `backend constraint mismatch. Model requires one of [`
- `LlmLiteRtCompiledModelExecutorDynamic only supports CPU backend.`
- `GPU sampler unavailable. Falling back to CPU sampling.`
- `LiteRtTopKOpenClSampler`
- `LiteRtTopKWebGpuSampler`
- `tflite_gpu_kv_cache`
- `tflite_opencl_kv_cache`

このため、Edge Gallery が GPU で応答できているとしても、その実行経路が LAMI の単純な `EngineConfig.backend=Backend.GPU()` と同じとは限らない。モデル metadata / backend constraint / artisan model 判定により、Gallery または LiteRT-LM runtime 内部で `GPU_ARTISAN` executor へ切り替わっている可能性がある。

LAMI の現在の観測:

- 通常 GPU route は held engine lifecycle で `Engine.initialize` と conversation 作成を完了し、`generate_started=true` まで進むが、first token 前で timeout する。
- isolated GPU prefill probe は `Engine.initialize` 中に `LiteRtLmJniException` で失敗する。
- root cause は `llm_litert_compiled_model_executor.cc:1546` と `litert_compiled_model.h:1140` 付近で、compiled model executor / backend constraint / runtime selection 差分を疑う材料になる。

したがって次の焦点は runtime 差分だけではなく、backend / executor selection 差分である。GPU 成功条件は「同じ GPU を指定」では足りず、少なくとも以下の一致が必要になる。

- same model file / same metadata
- same LiteRT / LiteRT-LM runtime stack
- same backend / executor selection
- same EngineConfig / RuntimeConfig / ConversationConfig
- same held engine lifecycle

`scripts/extract_edge_gallery_gpu_static_info.sh` は `artifacts/edge_gallery_static/backend_artisan_analysis/` を出力し、dex / native strings / optional jadx hits から artisan executor 分岐の手掛かりを集める。

```bash
scripts/extract_edge_gallery_gpu_static_info.sh --dry-run
scripts/extract_edge_gallery_gpu_static_info.sh
```

LAMI 側の DEV 診断には、runtime reflection による public API inventory として以下を追加する。

- `litert_lm_backend_candidates`
- `litert_lm_backend_gpu_artisan_available`
- `litert_lm_backend_cpu_artisan_available`
- `litert_lm_backend_google_tensor_artisan_available`
- `litert_lm_engine_config_artisan_api_available`
- `litert_lm_runtime_config_available`
- `litert_lm_backend_constraint_api_available`
- `litert_lm_preferred_engine_type_api_available`
- `selected_model_backend_constraint_hint`
- `selected_model_artisan_hint`
- `edge_gallery_artisan_static_evidence`

この reflection 診断は class / method / field 名を列挙するだけで、`GPU_ARTISAN` を生成せず、EngineConfig に渡さず、通常チャットの route も変更しない。

モデルファイル側は `scripts/extract_lami_model_static_hints.sh` で pull 済み `.litertlm` を静的確認する。

```bash
scripts/extract_lami_model_static_hints.sh --dry-run
scripts/extract_lami_model_static_hints.sh --input artifacts/lami_model_static/input
```

確認対象:

- `GPU_ARTISAN`
- `backend`
- `constraint`
- `sm8750`
- `qualcomm`
- `artisan`
- `gpu`
- `npu`
- `RuntimeConfig`
- `EngineConfig`
- `tflite_gpu_kv_cache`
- `tflite_opencl_kv_cache`

## Artisan follow-up experiments

実装する場合も DEV / explicit opt-in probe に限定する。

1. `GPU_ARTISAN` API が LAMI runtime から見える場合
   - まず isolated config-only probe で `Backend` object を作れるか確認する。
   - 次に `EngineConfig` dry-build だけを行う。
   - Engine.initialize / Conversation / generate へ進むのは別の明示 opt-in phase に分ける。
   - 通常チャットには適用しない。

2. `GPU_ARTISAN` API が public API から見えない場合
   - LAMI が利用中の LiteRT-LM public API では Edge Gallery と同等の artisan GPU executor へ到達できない可能性を記録する。
   - この場合は runtime stack 差分と model metadata 差分を引き続き比較し、public API で選べる範囲を明確にする。

3. 同一 runtime stack 隔離検証
   - 将来案として、Edge Gallery と同一 runtime stack を別 flavor / 別 applicationId で隔離して検証する。
   - Edge Gallery APK からの無断 runtime 移植は禁止。
   - `libLiteRt.so` / `liblitertlm_jni.so` の単体差し替えは禁止。
   - 検証する場合も依存セット全体、model、backend/executor selection、lifecycle を揃える。

## Goal matrix and next GPU phase

LAMI の最終目標は、Generic E2B の CPU/GPU と SM8750 専用 E2B の NPU を混ぜずに扱うこと。

| 経路 | Model | Backend | 現状 | 方針 |
| --- | --- | --- | --- | --- |
| Generic E2B CPU | `gemma-4-E2B-it.litertlm` | CPU | working | 安定 baseline として維持する。 |
| Generic E2B GPU | `gemma-4-E2B-it.litertlm` | GPU | investigating | Edge Gallery との差分を詰め、LAMI で動かす現実的条件を探す。 |
| SM8750 E2B NPU | `gemma-4-E2B-it_qualcomm_sm8750.litertlm` | Qualcomm SM8750 / NPU | target, gated | NPU S1 safety gate 内で別フェーズとして扱う。今回の GPU 調査では本体挙動を変えない。 |

### Current GPU failure map

通常 GPU route:

- `EngineConfig` build: success
- `Engine` constructor: success
- `Engine.initialize`: success
- Conversation create: success
- `generate_started=true`
- `first_token_received=false`
- `gpu_timeout_stage=generate_before_first_token`
- `gpu_watchdog_failure_stage=gpu_watchdog_timeout_generate_before_first_token`

既に試した GPU experiments:

- `gpu_no_sampling_acceleration`: NG
- `gpu_cache_dir_app_files`: NG
- `gpu_max_tokens_32`: NG
- いずれも `generate_before_first_token` timeout。
- sampler / cacheDir / max tokens 単独原因説は低くなっている。

isolated GPU prefill probe:

- `prompt=hi`
- `max_tokens=1`
- `sampler=none`
- `cache_dir=null`
- isolated engine
- `EngineConfig` build: success
- `Engine.initialize`: failure
- `failure_stage=gpu_prefill_probe_engine_initialize_invocation_target_exception`
- root cause: `com.google.ai.edge.litertlm.LiteRtLmJniException`
- native message: `Failed_to_create_engine:INTERNAL:ERROR:[runtime/executor/llm_litert_compiled_model_executor.cc:1546]└ERROR:[external/litert/litert/cc/litert_compiled_model.h:1140]`

解釈:

- 通常 route は held engine 経由で `generate_started` まで進む。
- isolated engine は `Engine.initialize` で compiled model executor 作成に失敗する。
- prompt / sampler / cache / max tokens より、runtime stack、backend executor selection、lifecycle 差分が濃い。

### Edge Gallery comparison update

Edge Gallery 静的抽出では以下が見えている。

- `GPU_ARTISAN`
- `CPU_ARTISAN`
- `GOOGLE_TENSOR_ARTISAN`
- `Artisan model detected. Switching backend from GPU to GPU_ARTISAN.`
- `LlmGpuArtisanExecutor`
- `backend constraint mismatch. Model requires one of [`
- `Supported backends are: [CPU, GPU, NPU, GPU_ARTISAN, CPU_ARTISAN, GOOGLE_TENSOR_ARTISAN]`
- `GPU sampler unavailable. Falling back to CPU sampling.`

LAMI runtime reflection の現状:

- `litert_lm_backend_candidates=CPU,GPU,NPU`
- `litert_lm_backend_gpu_artisan_available=false`
- `litert_lm_backend_cpu_artisan_available=false`
- `litert_lm_backend_google_tensor_artisan_available=false`
- `litert_lm_engine_config_artisan_api_available=false`
- `litert_lm_runtime_config_available=false`
- `litert_lm_backend_constraint_api_available=false`
- `litert_lm_preferred_engine_type_api_available=false`

この結果から、LAMI が利用している LiteRT-LM public API では `GPU_ARTISAN` 経路へ到達できない可能性が高い。ただし「不可能」とは断定しない。Edge Gallery 内部専用 API、model metadata による runtime 内部切替、または LAMI と異なる runtime stack が関係している可能性が残る。

重要なのは、Edge Gallery が GPU で動く場合でも、その route が LAMI の public `Backend.GPU()` route と同じとは限らない点である。

### Working hypotheses ranked

1. backend / executor selection mismatch
   - Edge Gallery は model constraint / artisan 判定で `GPU_ARTISAN` executor へ切り替えている可能性がある。
   - LAMI public API からは現在 `CPU,GPU,NPU` しか見えていない。
2. runtime stack mismatch
   - Edge Gallery と LAMI の `libLiteRt.so` / `liblitertlm_jni.so` は SHA/build id が一致していない。
   - 単体差し替えは禁止。比較は別 flavor / isolated APK の将来案に限定する。
3. lifecycle / held engine difference
   - LAMI 通常 route は held engine で generate まで進む。
   - isolated probe は initialize で失敗するため、GPU environment / resource state / lifecycle 差分がある。
4. model metadata / backend constraint difference
   - Edge Gallery が実際に使用している model file と LAMI の `gemma-4-E2B-it.litertlm` が同一か未確定。
   - model 内 metadata に backend constraint / artisan hint があるか確認が必要。
5. sampler / cacheDir / max tokens
   - 既存実験では単独原因説は低い。
   - ただし他差分と組み合わさる可能性は残る。

### Safety policy

- CPU held-official-flow は stable default として維持する。
- GPU は experimental / not recommended のまま扱う。GPU失敗時は CPU への切替を推奨する。
- 今回は UX 変更しない。standard UI で GPU を experimental / not recommended と明示する案は次フェーズの提案に留める。
- NPU は SM8750 専用 model の gated route として別フェーズで扱う。
- NPU S1 本体、fallback、production default は変更しない。
- native lib 差し替えは禁止。
- `libLiteRt.so` / `liblitertlm_jni.so` の単体差し替えは禁止。
- Edge Gallery APK から runtime を無断移植しない。
- `GPU_ARTISAN` が見えても通常チャットに即適用しない。

## Selected model static metadata helpers

LAMI の selected model file が PC に pull 済みの場合:

```bash
scripts/extract_lami_model_static_hints.sh --input artifacts/lami_model_static/input
```

selected model path を実機から pull する場合は、明示 opt-in で行う。

```bash
scripts/pull_lami_selected_model_for_static_hints.sh --dry-run
scripts/pull_lami_selected_model_for_static_hints.sh
scripts/pull_lami_selected_model_for_static_hints.sh --pull \
  --device-path /data/user/0/io.github.ninbyo02.lami/files/local_models/1781265409941_gemma-4-E2B-it.litertlm
```

`--pull` なしの通常実行は device へ接続せず、`artifacts/lami_model_static/selected_model_pull_summary.txt` に手順だけを書く。実機が必要な pull は `--pull` 付きでのみ実行する。

静的確認 keyword:

- `GPU_ARTISAN`
- `CPU_ARTISAN`
- `GOOGLE_TENSOR_ARTISAN`
- `backend`
- `constraint`
- `requires one of`
- `sm8750`
- `qualcomm`
- `gpu`
- `npu`
- `artisan`

出力:

- `artifacts/lami_model_static/summary.txt`
- `artifacts/lami_model_static/model_inventory.tsv`
- `artifacts/lami_model_static/model_keyword_presence.tsv`
- `artifacts/lami_model_static/all_model_backend_hints.txt`
- `artifacts/lami_model_static/strings/*.backend_hints.txt`
- `artifacts/lami_model_static/context/*.context.txt`
- `artifacts/lami_model_static/graphs/*.graph_names.txt`
- `artifacts/lami_model_static/metadata/*.possible_metadata_blocks.txt`
- `artifacts/lami_model_static/device_pull_instructions.md`

`.litertlm` が 100MB 未満の場合は、pull 失敗時の 89 bytes / 156 bytes 程度の error text を model と誤認しないため、
`suspicious_model_file=true` / `analysis_status=failed_suspicious_small_litertlm_file` として扱う。
`scripts/pull_lami_selected_model_for_static_hints.sh --pull` 実行時は、`selected_model_pull_summary.txt` に
`device_reported_size_bytes`、`pc_output_size_bytes`、`device_pc_size_match` を出し、実機 reported size と PC output size を照合する。

短い `gpu` / `npu` / `artisan` 断片は binary strings 上のノイズになりやすい。判断時は
`model_keyword_presence.tsv` だけでなく、`context/`、`graphs/`、`metadata/` の周辺文字列を優先して読む。

## GPU initialize failure classification

isolated GPU prefill probe の `InvocationTargetException` は wrapper なので、診断では target / cause / root cause に加えて
LiteRT-LM native error の file/line を専用 key として出す。

追加 key:

- `probe_exception_cause_message_raw`
- `probe_exception_cause_message_sanitized`
- `gpu_litert_executor_error_file`
- `gpu_litert_executor_error_line`
- `gpu_litert_compiled_model_error_file`
- `gpu_litert_compiled_model_error_line`
- `gpu_engine_initialize_internal_error_detected`
- `gpu_compiled_model_creation_failed`
- `gpu_failure_interpretation`

現在の isolated probe 例では以下を期待する。

```text
gpu_litert_executor_error_file=runtime/executor/llm_litert_compiled_model_executor.cc
gpu_litert_executor_error_line=1546
gpu_litert_compiled_model_error_file=external/litert/litert/cc/litert_compiled_model.h
gpu_litert_compiled_model_error_line=1140
gpu_failure_interpretation=compiled_model_creation_failed_before_conversation
```

通常 held GPU route が `Engine.initialize` / `Conversation.create` 後に first token 前で止まる場合は、
`gpu_failure_interpretation=normal_route_generate_hangs_after_successful_initialize` として、isolated initialize failure と分けて扱う。

## Held-engine prefill probe

isolated engine probe は initialize で落ちるため、通常 route との差分を見るために held engine 前提の DEV-only probe を追加した。
production default、CPU held-official-flow、NPU S1、fallback 本体には入れない。

起動 property:

```bash
adb shell setprop debug.lami.gpu_probe_use_held_engine true
adb shell setprop debug.lami.gpu_prefill_probe_prompt hi
adb shell setprop debug.lami.gpu_prefill_probe_max_tokens 1
adb shell setprop debug.lami.gpu_prefill_probe_sampler none
adb shell setprop debug.lami.gpu_prefill_probe_cache_dir null
```

運用:

1. Settings で GPU を選ぶ。
2. 先に通常 GPU route を 1 回試し、held engine が作られるか確認する。
3. 上記 property を有効にして再度入力する。
4. probe 有効時は通常 generate を必ず skip し、probe 結果のみを compact / LOCAL_ROUTE_DIAG / UI 詳細へ出す。
5. timeout または held engine 使用後は engine recreate を要求する。続けて通常 GPU generate を走らせない。

追加 key:

- `probe_use_held_engine_requested`
- `probe_used_held_engine`
- `probe_held_engine_present_before`
- `probe_held_engine_acquire_result`
- `probe_held_engine_generate_started`
- `probe_held_engine_first_token_received`
- `probe_held_engine_failure_stage`
- `probe_held_engine_timeout_stage`
- `probe_held_engine_invalidated_after`

`probe_start_blocked_reason=no_held_engine` の場合は、held engine がないため probe は開始していない。これは失敗ではなく、
通常 route との差分を見るための前提が満たされていない状態として扱う。

## Manifest and packaging notes

LAMI の manifest は Edge Gallery と同じく GPU/OpenCL native library を optional 宣言する方針を取る。
今回の調査では Manifest / Gradle / native packaging は変更しない。

確認観点:

- `uses-native-library` の optional OpenCL / vendor support 宣言
- `extractNativeLibs` / `useLegacyPackaging` の warning
- duplicate native lib warning で、app overlay と AAR のどちらが最終 APK に入るか
- strip unable warning
- `libLiteRt.so` / `liblitertlm_jni.so` の SHA/build id 差分

Edge Gallery で GPU が動く根拠は `libQnnGpu.so` ではなく、`libLiteRt.so` / `liblitertlm_jni.so` 内の LiteRT GPU stack と
executor selection の可能性が高い。`libLiteRt.so` / `liblitertlm_jni.so` はセット依存が強いため、単体差し替えは禁止する。

## Edge Gallery app data model inventory

Edge Gallery が実際に使っている model 名、file 名、size、保存先を確認する。logcat は使わない。

```bash
adb shell pm list packages | grep -i 'gallery\|edge\|google'
adb shell run-as <edge_gallery_package> ls -la
adb shell run-as <edge_gallery_package> find shared_prefs files databases -maxdepth 4 -print
```

確認したいもの:

- model 表示名
- 実 file 名
- file size
- 保存先 path
- accelerator / backend 設定の shared_prefs
- model metadata / manifest らしき file

`run-as` が不可の場合は、以下のように記録する。

```text
run_as_available=false
run_as_failure=<exact shell message>
```

`adb backup` などの危険または不要な手法は使わない。

## GPU next phase priority

1. selected generic model の metadata / backend constraint を静的確認する。
2. Edge Gallery 実 model の同一性を確認する。
3. LAMI public `Backend.GPU()` で動かす余地があるか再評価する。
4. public API では無理そうなら、別 flavor で Edge Gallery 同等 runtime / API stack を隔離検証する設計に進む。
5. NPU は別フェーズで `gemma-4-E2B-it_qualcomm_sm8750.litertlm` に戻る。

## GPU investigation Phase 2: held engine lifecycle

Confirmed:

- normal GPU route reaches `Engine.initialize` finished.
- normal GPU route reaches `Conversation.create` finished.
- normal GPU route reaches `generate_started=true`.
- normal GPU route still times out before first token:
  `gpu_watchdog_timeout_generate_before_first_token`.
- isolated GPU prefill probe fails earlier, during `Engine.initialize`, with compiled model creation failure:
  `llm_litert_compiled_model_executor.cc:1546` and `litert_compiled_model.h:1140`.
- held-engine probe can report `probe_start_blocked_reason=no_held_engine` even after a previous timeout run reported
  `held_engine_exists=true`.

The code path now records the holder lifecycle so that this mismatch is explicit. The GPU watchdog path performs:

1. build timeout diagnostics from the progress tracker
2. insert timeout assistant message
3. `resetConversation(reason=gpu_watchdog_timeout)`
4. `clear(reason=gpu_watchdog_timeout_holder_clear, failureStage=gpu_watchdog_timeout)`

Therefore the strongest current explanation for `held_engine_exists=true` during the timeout but
`probe_held_engine_present_before=false` on the next probe is:

```text
normal timeout run held engine existed at timeout checkpoint
watchdog cleanup then explicitly cleared the holder
next held-engine probe correctly found no held engine
```

New diagnostics:

- `holder_created`
- `holder_acquired`
- `holder_reused`
- `holder_invalidated`
- `holder_closed`
- `holder_timeout_cleanup`
- `holder_failure_cleanup`
- `holder_process_restart`
- `held_engine_lifecycle_history`
- `held_engine_destroy_reason`
- `held_engine_last_owner`
- `held_engine_last_failure_stage`
- `held_engine_snapshot_before_destroy`
- `gpu_route_divergence_point`

`held_engine_snapshot_before_destroy` includes holder hash, engine hash, backend, model path, use count, namespace,
destroy reason, owner, and links to the GPU initialize/conversation/generate state keys in the same diagnostic.

Open questions:

- why isolated GPU engine compiled model creation fails in `Engine.initialize`
- why the held normal route can initialize and create conversation but hangs before first token
- whether Edge Gallery reaches a different executor selection path, such as an internal artisan executor

Next device check:

1. Run normal GPU once until timeout.
2. Copy Local inference failure compact and check:
   `held_engine_destroy_reason=gpu_watchdog_timeout_holder_clear`.
3. Enable held-engine probe:

```bash
adb shell setprop debug.lami.gpu_probe_use_held_engine true
adb shell setprop debug.lami.gpu_prefill_probe_prompt hi
adb shell setprop debug.lami.gpu_prefill_probe_max_tokens 1
adb shell setprop debug.lami.gpu_prefill_probe_sampler none
adb shell setprop debug.lami.gpu_prefill_probe_cache_dir null
```

4. If `probe_start_blocked_reason=no_held_engine`, compare `held_engine_lifecycle_history` and
   `held_engine_snapshot_before_destroy` from the same compact copy before attempting another normal GPU run.
