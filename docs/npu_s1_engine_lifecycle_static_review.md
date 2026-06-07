# NPU S1 Engine Lifecycle Static Review

## 調査目的

NPU Standard Route S1 repeated run が毎回 7 回目前後で
`adapter_failure:LiteRtLmJniException` になる原因を、実機なしのコード静的解析で絞る。

このレビューでは推論挙動を変更しない。対象は Engine / Adapter / Decode の
lifecycle と、`reuse` / `recreate` / `recreate_3s` の実際の効果である。

## 現在の実機観測まとめ

- galleryprobe:
  - process: `io.github.ninbyo02.lami.galleryprobe`
  - `Engine.initialize()` -> `LiteRtLmJni_nativeCreateEngine`
  - `liblitertlm_jni.so` 内で `SIGABRT`
  - tombstone あり
- standard chat / NPU S1 repeated run:
  - run1 から run6 は成功
  - run7 で `adapter_failure:LiteRtLmJniException`
  - `run_decode_reached=false`
  - `fallback_used=false`
  - `timeout=false`
  - `fresh_crash=false`
  - `safety_guard_triggered=false`
  - process は生存
  - dropbox なし、新規 tombstone なし
  - `adapter_call_count=7`
  - `decode_success_count=6`
  - `failure_pattern_hint=adapter_failure_after_6_successful_decodes`

静的解析上は、galleryprobe の SIGABRT と S1 repeated run の run7 failure は
同じ LiteRT-LM / QNN / HTP 領域に入るが、同じ失敗形態ではない。galleryprobe
は Java API の `Engine.initialize()` でプロセス abort、S1 は custom JNI wrapper
から JNI 例外が Kotlin 側に返っている。

## Engine lifecycle 呼び出し箇所一覧

### S1 repeated run entry

- `ChatScreen.startNpuS1RepeatedRun()`
  - [ChatScreen.kt](/home/sato/project/lami-android/app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt:1124)
  - run loop は `for (runIndex in 1..requestedRunCount)`。
  - 各 run で `NpuStandardRouteS1Bridge(...).run(...)` を 1 回呼ぶ。
  - run 後に必ず既存の 5 秒 recovery snapshot を取る。
  - `recreate` / `recreate_3s` の場合は、run 結果を record へ保存する前に
    `LocalInferenceEngineHolder.requestRecreateForDev(...)` を呼ぶ。

### S1 provider / adapter path

- `NpuStandardRouteS1ProviderSelector.realProvider()`
  - reflection で `RealNpuStandardRouteS1Provider` を生成する。
- `RealNpuStandardRouteS1Provider.invoke(...)`
  - [RealNpuStandardRouteS1Provider.kt](/home/sato/project/lami-android/app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1Provider.kt:20)
  - request を `DevOnlyNpuOneTurnConversationEntry(appContext).run(request)` に渡す。
  - `runCatching` で throwable を捕捉し、失敗時は
    `RealNpuStandardRouteS1ResultMapper.failure(...)` に変換する。
- `Qairt244DevOnlyNpuRouteAdapter.runRoute(...)`
  - [Qairt244DevOnlyNpuRouteAdapter.kt](/home/sato/project/lami-android/app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244DevOnlyNpuRouteAdapter.kt:288)
  - model resolution と prompt validation の後、`Qairt244ShortMultitokenSmoke.runEditablePrompt(...)`
    を呼ぶ。
  - throwable は `adapter_failure:${throwable.javaClass.simpleName}` として
    `DevOnlyNpuRouteResult` に変換される。
- `Qairt244ShortMultitokenSmoke.runEditablePrompt(...)`
  - [Qairt244ShortMultitokenSmoke.kt](/home/sato/project/lami-android/app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt:47)
  - `System.loadLibrary("litertlm_jni")` は companion init で 1 回。
  - native entrypoint は `nativeRunEditablePrompt(...)`。

### S1 native custom JNI path

現行の source tree には custom JNI 実装本体は入っておらず、patch と staged
`liblitertlm_jni.so` に含まれる。代表 patch:

- [qairt244_litertlm_utf8_128token_128input.patch](/home/sato/project/lami-android/patches/qairt244_litertlm_utf8_128token_128input.patch:1241)

native `nativeRunEditablePrompt(...)` の流れ:

1. prompt / model path / native library dir / cache dir を受け取る。
2. `ModelAssets::Create(model_path)`.
3. `litert::lm::GetBackendFromString("NPU")`.
4. `EngineSettings::CreateDefault(*model_assets, *backend_enum)`.
5. `settings->GetMutableMainExecutorSettings().SetCacheDir(cache_dir)`.
6. `settings->GetMutableMainExecutorSettings().SetLitertDispatchLibDir(native_library_dir)`.
7. `EngineFactory::CreateDefault(*settings)`.
8. `engine_ptr->CreateSession(session_config)`.
9. `session_ptr->RunPrefill(inputs)`.
10. `decode_config.SetMaxOutputTokens(max_output_tokens)`.
11. `session_ptr->RunDecode(decode_config)`.
12. success の場合のみ `session_ptr.reset()` と `engine_ptr.reset()` を明示実行。

重要な静的所見:

- S1 repeated run は各 run で Java `Engine.initialize()` を直接呼ばない。
- S1 repeated run は各 run で custom JNI entrypoint に入り、native 側で Engine と
  Session を作る設計に見える。
- success path では `session_ptr.reset()` / `engine_ptr.reset()` がある。
- failure path では `return` 前に明示 reset がない箇所がある。ただし C++ の
  local `std::unique_ptr` がスコープ終了で破棄されるはずなので、即リークとは
  断定できない。
- ただし `ModelAssets`, `EngineSettings`, QNN/HTP/FastRPC runtime の内部リソースが
  reset 後に同期的に完全解放される保証は、このコードからは確認できない。

### galleryprobe path

- `NpuExperimentProbeActivity.onCreate(...)`
  - [NpuExperimentProbeActivity.kt](/home/sato/project/lami-android/app/src/npuExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuExperimentProbeActivity.kt:8)
- `NpuExperimentProbeLogger.runBackendNpuAttachProbe(...)`
  - [NpuExperimentProbeActivity.kt](/home/sato/project/lami-android/app/src/npuExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuExperimentProbeActivity.kt:66)
- `AcceleratorProbe.captureSnapshot(...)`
- `AcceleratorProbe.probeEngineInitializeDryRunSafely(...)`
  - [AcceleratorProbe.kt](/home/sato/project/lami-android/app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AcceleratorProbe.kt:1298)
- `AcceleratorProbe.invokeEngineInitializeOperation(...)`
  - [AcceleratorProbe.kt](/home/sato/project/lami-android/app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AcceleratorProbe.kt:1863)

galleryprobe は reflection で Java `com.google.ai.edge.litertlm.Engine` を作り、
`Engine.initialize()` または同等 static/factory operation を呼ぶ。ここが
`nativeCreateEngine` へ入り、tombstone で SIGABRT している。

### 通常 local inference / holder path

- `LocalInferenceEngineHolder.acquire(...)`
  - [LocalInferenceEngineHolder.kt](/home/sato/project/lami-android/app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceEngineHolder.kt:145)
  - same key の場合は held Engine を reuse する。
  - `ENABLE_HELD_ENGINE_RELOAD_BY_REUSE_LIMIT=false` なので reuse count による
    自動 recycle は無効。
- `LocalInferenceEngineHolder.requestRecreateForDev(...)`
  - [LocalInferenceEngineHolder.kt](/home/sato/project/lami-android/app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceEngineHolder.kt:328)
  - held Engine に対して close/recreate decision を適用する。
- `LocalInferenceEngineHolder.applyLifecycleDecisionLocked(...)`
  - [LocalInferenceEngineHolder.kt](/home/sato/project/lami-android/app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceEngineHolder.kt:670)
  - `target.closeEngine(appendTrace)` を呼び、`held = null` にする。
- `LocalStreamingRunner` の official direct path
  - [LocalStreamingRunner.kt](/home/sato/project/lami-android/app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt:3980)
  - `Engine(engineConfig)` -> `engine.initialize()` -> `engine.createConversation()`.
  - blocking path も同様。

静的所見:

- `LocalInferenceEngineHolder` は通常チャットの local inference 用 holder であり、
  S1 debug adapter の native `Qairt244ShortMultitokenSmoke` とは同じ Engine instance
  を共有していない。
- したがって repeated run の `recreate` / `recreate_3s` が閉じるのは
  holder が持つ通常 local Engine であり、S1 custom JNI 側の native Engine/session
  には直接作用しない可能性が高い。

## reuse / recreate / recreate_3s のシーケンス

### reuse

```text
ChatScreen.startNpuS1RepeatedRun
  -> for runIndex
  -> memory_before snapshot
  -> NpuStandardRouteS1Bridge.run
     -> NpuStandardRouteS1ProviderSelector.defaultProviderForMode
     -> RealNpuStandardRouteS1Provider.invoke
        -> DevOnlyNpuOneTurnConversationEntry.run
           -> Qairt244DevOnlyNpuRouteAdapter.runDevOnlyConversationOnce
              -> Qairt244DevOnlyNpuRouteAdapter.runRoute
                 -> Qairt244ShortMultitokenSmoke.runEditablePrompt
                    -> nativeRunEditablePrompt
                       -> ModelAssets::Create
                       -> GetBackendFromString("NPU")
                       -> EngineSettings::CreateDefault
                       -> EngineFactory::CreateDefault
                       -> Engine::CreateSession
                       -> Session::RunPrefill
                       -> Session::RunDecode
                       -> unique_ptr reset on success
                       -> QNN/HTP/FastRPC backend work
  -> memory_after snapshot
  -> delay(5s) existing recovery wait
  -> memory_recovery_5s snapshot
  -> record result
  -> stop on adapter_failure / decode false / other safety reason
```

同じ Java/Kotlin Engine object を reuse している形ではない。毎 run native entrypoint
へ入り、native 側で Engine/session を作る設計に見える。ただし QNN/HTP/FastRPC
runtime は process-global / driver-global state を持つ可能性がある。

### recreate

```text
reuse と同じ S1 provider/adapter/native path
  -> result returns
  -> memory_after snapshot
  -> delay(5s)
  -> memory_recovery_5s snapshot
  -> LocalInferenceEngineHolder.requestRecreateForDev(
       reason=npu_s1_repeated_run_recreate_run_<n>
     )
     -> holder's current held Engine close if present
     -> held = null
     -> held conversations cleared
  -> no extra delay
  -> record result
```

`requestRecreateForDev()` は S1 native wrapper の Engine/session を指していない。
S1 run 内で作られた native Engine/session は already returned 後で、native側
unique_ptr cleanup に依存している。

### recreate_3s

```text
recreate と同じ
  -> LocalInferenceEngineHolder.requestRecreateForDev(...)
  -> delay(3s)
  -> record result
  -> next run
```

3 秒 delay は holder recreate 後の待機であり、S1 custom JNI native runtime の
明示 close completion を待つものではない。

## run7 failure 仮説トップ5

### 1. QNN/HTP/FastRPC runtime の process 内連続 attach / teardown 制限

根拠:

- S1 は各 run で native wrapper が Engine/session を新規作成する設計に見える。
- run1-6 success、run7 `adapter_failure`、`decode_success_count=6` は「6 回の
  QNN/HTP decode 成功後、7 回目の Engine/session/decode handoff 前後で失敗」
  という形に合う。
- tombstone がないため、driver/native abort ではなく LiteRT-LM が status を
  JNI 例外として返している可能性が高い。

反証:

- success path には `session_ptr.reset()` / `engine_ptr.reset()` がある。
- 5 秒 recovery 待機があるため、単純な即時連打だけではない。

次に確認すべき診断:

- native result/diag の `model_assets_elapsed_ms`, `engine_settings_elapsed_ms`,
  `engine_create_elapsed_ms`, `session_create_elapsed_ms`, `prefill_elapsed_ms`,
  `decode_elapsed_ms`, `cleanup_elapsed_ms`
- failure 時の native diag 最終 marker
- QNN/HTP/FastRPC エラー文字列
- process 単位の QNN open handle / FastRPC buffer failure 文字列

安全な修正案:

- DEV診断に native diag tail と result file tail を run ごとにコピーする。
- run ごとの native stage を Kotlin record に保存する。
- native wrapper に cleanup start/end と failure-path cleanup evidence を追加する。

危険な修正案:

- QNN/FastRPC session を強制 unload する。
- native runtime `.so` を run ごとにロードし直す。
- process kill / Activity restart を通常生成経路に入れる。

### 2. native failure path の cleanup / resource accounting 不整合

根拠:

- native patch 上、success path は明示 `session_ptr.reset()` / `engine_ptr.reset()` を
  記録する。
- error path は `ThrowLiteRtLmJniException` して return する分岐が複数ある。
  C++ RAII で破棄されるはずだが、diag 上は failure-path cleanup completion が
  success path ほど明確ではない。
- 7 回目 failure が process 生存かつ tombstone なしなら、native status failure
  が JNI exception に変換された可能性が高い。

反証:

- local `std::unique_ptr` はスコープ終了で破棄されるため、C++ 上の明白なリークとは
  断定できない。
- run1-6 success path では cleanup が動いているはず。

次に確認すべき診断:

- failure path で `session_ptr` / `engine_ptr` が作成済みだったか。
- failure path で destructor/reset が何 ms かかったか。
- failure stage が engine create / session create / prefill / decode のどこか。

安全な修正案:

- native wrapper に `cleanup_started`, `cleanup_finished`,
  `cleanup_scope=success|failure` を result/diag に出す。
- Kotlin DEVコピーに `native_last_stage`, `native_cleanup_elapsed_ms`,
  `native_failure_detail` を追加する。

危険な修正案:

- failure 時に未知の native pointer を二重解放する。
- close/dispose API がない object に reflection で無理に close を探す。

### 3. S1 `recreate` が実際の S1 native Engine を解放していない

根拠:

- `ChatScreen` の recreate は `LocalInferenceEngineHolder.requestRecreateForDev()`
  を呼ぶ。
- `LocalInferenceEngineHolder` は通常 local inference の held Engine を管理する。
- S1 native wrapper は `Qairt244ShortMultitokenSmoke.nativeRunEditablePrompt` で
  Engine/session を作るため、holder と同じ instance を共有していない。
- よって `recreate` / `recreate_3s` で failure pattern が変わらない場合、
  それは expected に近い。

反証:

- holder 側に別の local Engine が存在している場合、その解放で process memory や
  runtime pressure は間接的に下がる可能性がある。

次に確認すべき診断:

- S1 run 前後の `holderInstanceHash`, `heldEngineHash`, `recreateRequestCount`,
  `hasHeldEngineBeforeRecreate`, `hasHeldEngineAfterRecreate`
- S1 adapter/native run id と holder generation の対応なしを明示する key

安全な修正案:

- DEVコピーに holder snapshot を repeated run details へ追加する。
- `recreate` の説明を「通常 local holder の recreate。S1 native Engine の直接
  recreate ではない」と明記する。

危険な修正案:

- S1 custom JNI の内部 Engine pointer を外から holder に統合しようとする。
- native session 強制解放 hook を推測で追加する。

### 4. LiteRT-LM / QNN の連続 EngineFactory::CreateDefault 制限

根拠:

- S1 custom JNI は Java `Engine.initialize()` ではなく native
  `EngineFactory::CreateDefault(*settings)` を使う。
- galleryprobe の `nativeCreateEngine` SIGABRT と同じ低層 runtime に触る。
- run7 が `run_decode_reached=false` なので、decode 前の engine/session/prefill
  段階で落ちている可能性がある。

反証:

- run1-6 は同じ path で成功しているため、単発の config mismatch ではない。
- process 生存、dropbox なしなので hard abort ではない。

次に確認すべき診断:

- `engine_create_elapsed_ms` が run ごとに伸びるか。
- run7 result file reason が `engine-create-failed`, `session-create-failed`,
  `prefill-failed`, `decode-failed` のどれか。
- native diag の `before EngineFactory::CreateDefault` / `before CreateSession` /
  `before RunPrefill` / `before RunDecode` の到達点。

安全な修正案:

- native stage を Kotlin に取り込み、DEVコピーで run ごとに見えるようにする。
- `recreate_3s` より長い DEV専用 wait を別実験として追加する場合も、通常経路とは
  分離する。

危険な修正案:

- 通常生成経路に sleep を入れる。
- max tokens / prompt / sanitizer を原因切り分け前に変える。

### 5. memory pressure / native heap delayed release

根拠:

- QNN/HTP/FastRPC buffer は Java heap ではなく native / driver 側に残る可能性がある。
- run7 という再現性は累積 resource pressure と整合する。

反証:

- 最新観測では `fresh_crash=false`, `timeout=false`, process 生存。
- 5 秒 recovery snapshot は既にある。
- prompt と max_output_tokens は固定で、run1-6 と run7 の入力差はない。

次に確認すべき診断:

- `native_heap_pss_mb`, `native_heap_alloc_mb`, `system_available_memory_mb` の
  run ごとの増減。
- QNN/FastRPC 固有の error line。
- native cleanup duration と memory recovery の相関。

安全な修正案:

- run details に native diag stage と memory delta を並べる。
- `dumpsys meminfo` と DEVコピーの wall time を突き合わせる。

危険な修正案:

- GC / finalizer / kill を通常経路に入れる。
- native library unload を試す。

## 低い可能性の候補

- prompt / sanitizer / stop sequence:
  - run1-6 と run7 で prompt は同じ。
  - run7 は `run_decode_reached=false` なので sanitizer 前。
- TTS / DB / streaming:
  - S1 repeated run record 上 `tts=false`, `db=false`, `stream=false` の設計。
- Android Activity lifecycle:
  - process 生存かつ repeated run 中の同一 Activity job なので、主因としては低い。

## 危険な修正と安全な修正

安全:

- DEVコピーに native result/diag tail を run ごとに追加する。
- native wrapper に stage/counter/cleanup evidence を追加する。
- holder snapshot を S1 repeated run details に追加し、holder recreate が S1 native
  Engine に効いていないことを確認できるようにする。
- run mode 説明を DEVコピーに追加する。

危険:

- 通常生成経路へ sleep / retry / process restart を入れる。
- QNN/HTP/FastRPC を推測で強制解放する。
- pointer の ownership が不明なまま native close を二重に呼ぶ。
- prompt / sanitizer / fallback を原因切り分け前に変える。

## 次に追加すべき DEV診断 key

優先度高:

- `native_result_file_path`
- `native_diag_file_path`
- `native_result_tail`
- `native_diag_tail`
- `native_last_stage`
- `native_failure_detail`
- `native_engine_create_elapsed_ms`
- `native_session_create_elapsed_ms`
- `native_prefill_elapsed_ms`
- `native_decode_elapsed_ms`
- `native_cleanup_elapsed_ms`
- `native_cleanup_scope`
- `native_cleanup_completed`

holder / provider identity:

- `provider_identity_hash`
- `adapter_identity_hash`
- `holder_identity_hash`
- `holder_held_engine_hash_before_run`
- `holder_held_engine_hash_after_recreate`
- `holder_recreate_request_count`
- `holder_recreate_completed_count`
- `holder_recreate_reason`
- `s1_recreate_affects_native_engine=false`

runtime/model:

- `model_path_hash`
- `model_basename`
- `native_library_dir_hash`
- `dispatch_runtime_build_id`
- `qnn_system_build_id`
- `qnn_htp_build_id`
- `qnn_htp_v79_stub_build_id`
- `backend_selected_before_each_run`

run/session:

- `native_run_id`
- `native_engine_generation_inferred`
- `native_session_generation_inferred`
- `engine_warm_cold_state`
- `run_mode_effective_action`
- `decode_handoff_started`
- `decode_handoff_returned`

## AI Edge Gallery との差分調査観点

- model reload timing:
  - Gallery が Engine を Activity/session 単位で保持するのか、1 request ごとに作るのか。
  - Lami S1 custom JNI は repeated run ごとに native Engine/session を作るように見える。
- engine/session lifecycle:
  - Gallery の `Engine.close()` / `Conversation.close()` / Activity destroy での解放順。
  - Lami S1 native wrapper は success path で unique_ptr reset、failure path の
    cleanup evidence が不足。
- Activity destroy 時の解放:
  - galleryprobe は isolated Activity/process の probe。
  - standard S1 repeated run は standard app process 内で連続実行。
- NPU backend attach timing:
  - galleryprobe は Java API `Backend.NPU` + `Engine.initialize()`.
  - S1 は native C++ `GetBackendFromString("NPU")` + `EngineFactory::CreateDefault`.
- repeated inference の扱い:
  - Gallery が同一 Engine/session を再利用するなら、Lami の per-run create/destroy と
    QNN/HTP resource pressure が異なる。
- failure recovery:
  - Gallery が failure 時に Engine/session を close する明示 path を持つか。
  - Lami S1 は failure が JNI exception として戻り、Kotlin は failure result に変換する。

## 次回実機確認手順

1. `DEV診断 -> NPU S1 20回連続テスト -> reuse` を実行する。
2. 停止後、DEV診断コピーを保存する。
3. `first_failure_wall_time_ms` と dropbox/tombstone timestamp を比較し、新規 native crash
   がないことを再確認する。
4. `first_failure_counter_snapshot` を確認する。
   - 期待例: `engine_request=7,adapter_call=7,decode_attempt=6,adapter_failure=1,decode_success=6`
5. 次に native result/diag file を取得する。
   - `qairt244_short_multitoken_smoke_result.txt`
   - `qairt244_native_diag.txt`
6. run7 failure の native last stage を見る。
   - `before EngineFactory::CreateDefault`
   - `before CreateSession`
   - `before RunPrefill`
   - `before RunDecode`
   - `decode-failed:*`
7. run1-6 と run7 の elapsed を比較する。
   - `engine_create_elapsed_ms`
   - `session_create_elapsed_ms`
   - `prefill_elapsed_ms`
   - `decode_elapsed_ms`
   - `cleanup_elapsed_ms`

## 結論

最有力仮説は「standard S1 repeated run が 1 run ごとに custom JNI 経由で
LiteRT-LM/QNN/HTP Engine/session を作成・decode・破棄しており、QNN/HTP/FastRPC
側の process 内 resource / session lifecycle が 6 回成功後の 7 回目 handoff で
JNI 例外として返っている」である。

`recreate` / `recreate_3s` は静的解析上、通常 local inference の
`LocalInferenceEngineHolder` を close するだけで、S1 custom JNI の native Engine
には直接効いていない可能性が高い。したがって次の安全な一手は修正ではなく、
native result/diag tail と cleanup evidence を DEV診断コピーへ取り込み、run7 が
Engine create / session create / prefill / decode のどこで失敗しているかを確定すること。
