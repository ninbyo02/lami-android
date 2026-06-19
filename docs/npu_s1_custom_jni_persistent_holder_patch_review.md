# NPU S1 custom JNI persistent holder patch review

## 調査目的

`custom JNI S1` の repeated run が run6-7 前後で
`adapter_failure:LiteRtLmJniException` になる原因を絞るため、
`patches/qairt244_litertlm_utf8_128token_128input.patch` にある
native one-shot 実装を静的解析した。

目的は、現在毎回実行されている `EngineFactory::CreateDefault` を
DEV 専用 PoC で 1 回だけにし、同じ `Engine` から run ごとに
fresh `Session` / `RunPrefill` / `RunDecode` できるかを検証するための
設計レビューである。今回は native patch 実装には入らない。

## 現在の実機観測

- custom JNI one-shot S1 は NPU decode 自体は成功する。
- `reuse`, `recreate`, `recreate_3s`, `reuse_30s` はすべて run6-7 前後で失敗した。
- `reuse_30s` は合計 180 秒待機しても run7 failure だった。
- failure stage は `native_call`。
- native diag は以下で止まる。
  - `before EngineFactory::CreateDefault`
  - `engine-create-failed:INTERNAL`
  - `runtime/executor/llm_litert_npu_compiled_model_executor.cc:2725`
  - `external/litert/litert/cc/litert_compiled_model.h:1140`
- 新規 tombstone / Dropbox はない。
- official Java/Kotlin persistent Engine は `Engine.initialize()` に成功したが、
  Conversation / Session decode が
  `Decode for logits output not implemented for backend: LiteRT NPU Compiled Model`
  で失敗した。

このため、本命は custom JNI の `RunPrefill` / `RunDecode` 経路を維持したまま
`EngineFactory::CreateDefault` を repeated run loop の外へ出すこと。

## patch 内の関数一覧

主対象: `patches/qairt244_litertlm_utf8_128token_128input.patch`

追加 helper:

- `AppendQairt244Diag(...)`
  - patch line 64 付近。
  - diag file に marker 付きで追記する。
- `GetOptionalJString(...)`
  - patch line 84 付近。
  - JNI string を `std::string` に変換し、`ReleaseStringUTFChars` まで行う。
- `WriteQairt244SingleTokenResult(...)`
  - patch line 109 付近。
- `WriteQairt244TokenTimingResult(...)`
  - patch line 139 付近。
- `WriteQairt244ShortMultitokenResult(...)`
  - patch line 186 付近。
- `WriteQairt244EditablePromptResult(...)`
  - patch line 365 / 432 付近。
- `Qairt244SentinelLog(...)`
  - patch line 450 付近。

既存 LiteRT-LM JNI への診断追加:

- `JNI_METHOD(nativeCreateEngine)`
  - patch line 471 付近から既存 official JNI entrypoint へ sentinel / diag を追加。
  - `ModelAssets::Create`: patch line 556 付近。
  - `EngineSettings::CreateDefault`: patch line 606-626 付近。
  - `EngineFactory::CreateDefault`: patch line 714-718 付近。

追加 one-shot JNI entrypoint:

- `Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244LowerLevelSingleTokenSmoke_nativeRun`
  - patch line 745 付近。
  - prompt は `"Hi"`、`RunDecode SetMaxOutputTokens(1)`。
- `Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRun`
  - patch line 971 付近。
  - prompt は `"Hi"`、`RunDecode SetMaxOutputTokens(3)`。
- `Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt`
  - patch line 1241 付近。
  - Kotlin S1 custom JNI adapter が使う本命 entrypoint。
  - prompt は Java/Kotlin から受け取り、native UTF-8 / length guard を通して
    `RunDecode SetMaxOutputTokens(max_output_tokens)` を実行する。

## 既存 one-shot native lifecycle

S1 repeated run が使う editable prompt entrypoint の流れ:

```text
Kotlin Qairt244ShortMultitokenSmoke.runEditablePrompt
  -> Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt
    -> GetOptionalJString(model_path/native_library_dir/cache_dir/result_path/diag_path)
    -> prompt GetStringUTFChars
    -> ValidateQairt244EditablePrompt
    -> FileExists(model_path)
    -> AppendQairt244Diag("before ModelAssets::Create")
    -> ModelAssets::Create(model_path_str)
    -> GetBackendFromString("NPU")
    -> AppendQairt244Diag("before EngineSettings::CreateDefault")
    -> EngineSettings::CreateDefault(*model_assets, *backend_enum)
    -> settings.GetMutableMainExecutorSettings().SetCacheDir(cache_dir)
    -> settings.GetMutableMainExecutorSettings().SetLitertDispatchLibDir(native_library_dir)
    -> AppendQairt244Diag("before EngineFactory::CreateDefault")
    -> EngineFactory::CreateDefault(*settings)
    -> std::unique_ptr<Engine> engine_ptr = std::move(*engine)
    -> AppendQairt244Diag("before CreateSession")
    -> SessionConfig::CreateDefault()
    -> engine_ptr->CreateSession(session_config)
    -> std::unique_ptr<Engine::Session> session_ptr = std::move(*session)
    -> InputData(InputText(normalized_prompt.c_str()))
    -> AppendQairt244Diag("before RunPrefill")
    -> session_ptr->RunPrefill(inputs)
    -> DecodeConfig::CreateDefault()
    -> decode_config.SetMaxOutputTokens(max_output_tokens)
    -> AppendQairt244Diag("before RunDecode SetMaxOutputTokens(...)")
    -> session_ptr->RunDecode(decode_config)
    -> responses->GetTexts()[0]
    -> session_ptr.reset()
    -> engine_ptr.reset()
    -> AppendQairt244Diag("success ... Engine.close=unique_ptr_cleanup")
    -> WriteQairt244EditablePromptResult(...)
    -> NewStringStandardUTF(output)
```

失敗時は各 stage で:

```text
AppendQairt244Diag(marker + detail)
WriteQairt244EditablePromptResult(result="failure", detail=...)
ThrowLiteRtLmJniException(env, detail)
return nullptr
```

## EngineFactory::CreateDefault 箇所

patch 内では少なくとも以下に出現する。

- official `nativeCreateEngine`: patch line 714-718 付近。
- lower-level single token smoke: patch line 859-862 付近。
- short multitoken smoke: patch line 1115-1118 付近。
- editable prompt smoke: patch line 1499-1502 付近。

S1 repeated run の run6-7 failure と対応するのは editable prompt smoke の
`Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt`
内の呼び出しである。

## RunDecode 箇所

patch 内では以下に出現する。

- lower-level single token smoke:
  - patch line 918-923 付近。
  - `decode_config.SetMaxOutputTokens(1)`
  - `session_ptr->RunDecode(decode_config)`
- short multitoken smoke:
  - patch line 1185-1189 付近。
  - `decode_config.SetMaxOutputTokens(3)`
  - `session_ptr->RunDecode(decode_config)`
- editable prompt smoke:
  - patch line 1574-1578 付近。
  - `decode_config.SetMaxOutputTokens(max_output_tokens)`
  - `session_ptr->RunDecode(decode_config)`

S1 standard route は editable prompt smoke の `max_output_tokens` 可変経路を使う。

## cleanup 箇所

patch 内の one-shot smoke は成功時に以下を行う。

- lower-level single token smoke:
  - patch line 940-941 付近。
  - `session_ptr.reset(); engine_ptr.reset();`
  - success diag に `Engine.close=unique_ptr_cleanup`。
- short multitoken smoke:
  - patch line 1210-1211 付近。
  - `session_ptr.reset(); engine_ptr.reset();`
  - success diag に `Engine.close=unique_ptr_cleanup`。
- editable prompt smoke:
  - patch line 1599-1600 付近。
  - `session_ptr.reset(); engine_ptr.reset();`
  - success diag に `Engine.close=unique_ptr_cleanup`。

注意点:

- 成功 path では明示的に `session_ptr` と `engine_ptr` を reset している。
- 失敗 path は local `unique_ptr` のスコープ終了で destructor に任せる構造。
- `EngineFactory::CreateDefault` 失敗時は `engine_ptr` が存在しないため、cleanup 対象は
  `model_assets` / `settings` などの stack local に限られる。
- native abort / SIGABRT の場合は destructor / result writer / diag writer が実行されない。

## persistent holder 化の可否

静的には可能性が高い。

根拠:

- `EngineFactory::CreateDefault(*settings)` の戻り値は `std::unique_ptr<Engine>` として扱われている。
- `Engine` から `CreateSession(session_config)` しており、prompt ごとの状態は
  `Engine::Session` 側に持たせる構造に見える。
- one-shot 実装も `RunPrefill` / `RunDecode` は `session_ptr` に対して呼ぶ。
- したがって、`Engine` を holder に保持し、各 run で fresh `Session` を作り直す構造は
  API 形状上は自然。

不確定点:

- LiteRT-LM / QNN / HTP backend が同一 `Engine` から複数 `Session` を作ることを
  NPU compiled model で安全に許容するかは patch だけでは断定できない。
- `RunDecode` 後に `Session` destructor が HTP/FastRPC resource を完全に解放するかは不明。
- `Engine` destructor にしか紐づかない backend resource がある場合、persistent holder は
  one-shot より長く下位 resource を保持する。
- SIGABRT は Kotlin / JNI catch に戻らないため、holder invalidation で復旧できない。

結論:

- DEV PoC としては実装価値が高い。
- 製品経路への昇格は、20 run / activity restart / model change / cancel / failure recovery の
  実機検証後に限定すべき。

## prompt ごとにリセットすべきもの

Engine は保持する。

run ごとに作り直すべきもの:

- `SessionConfig`
- `Engine::Session`
- `std::vector<InputData>`
- `InputText`
- `DecodeConfig`
- response buffer / output string
- result file / diag run section

run ごとに保持してはいけないもの:

- 前 run の `Engine::Session`
- 前 run の `InputText(normalized_prompt.c_str())` が参照する文字列 lifetime
- 前 run の `Responses`

model / backend 条件が変わるまで保持してよい候補:

- `Engine`
- 可能なら `ModelAssets` / `EngineSettings` は Engine 作成後不要なので holder に持たない。

## holder key 設計

Kotlin 側 probe の期待と合わせ、native holder key は以下で構成する。

- `model_path`
- `model_file_last_modified`
- `model_file_size`
- `backend`
- `cache_dir`
- `max_token_budget`
- `engine_config_version`

再生成条件:

- holder が未生成。
- holder が invalidated。
- 上記 key のいずれかが現在値と異なる。
- `EngineFactory::CreateDefault` / `CreateSession` / `RunPrefill` / `RunDecode` のいずれかで
  failure が返った。
- explicit `nativePersistentInvalidate(...)` が呼ばれた。

`max_token_budget` は `DecodeConfig.SetMaxOutputTokens(max_output_tokens)` の上限設計に使う。
run ごとの requested tokens が holder key の budget 以下なら Engine 再生成は不要とする案がよい。
requested tokens そのものを key にすると、UI の小変更で holder が頻繁に破棄される。

## mutex / 排他設計

global mutex は必須。

理由:

- `Engine` / `Session` が thread-safe か patch からは分からない。
- Kotlin UI から repeated run / persistent probe / existing one-shot が並行すると、
  QNN/HTP/FastRPC 下位 resource を同時に触る可能性がある。
- holder reset と decode 実行が競合すると use-after-free になり得る。

推奨:

```text
static std::mutex g_qairt244_persistent_mutex;
static Qairt244PersistentHolder g_qairt244_holder;

nativeRunPersistentProbe(...)
  lock mutex
  if holder key mismatch:
    close holder
    create holder
  for run in 1..20:
    if holder invalid: break
    create fresh session
    prefill
    decode
    destroy session
  close holder at probe end
  unlock mutex
```

複数 entrypoint に分ける場合も、`init/run/close/status/invalidate` すべて同じ mutex を取る。

## 危険ポイント

timeout 後に blocking JNI が残る危険:

- Kotlin coroutine timeout / cancel は blocking JNI を中断できない。
- `RunDecode` が戻らない場合、Java/Kotlin 側は job cancel 済みに見えても native thread は残る。
- persistent holder PoC では native 内 loop を同期実行し、Kotlin 側 timeout は
  「戻ってきた後に停止扱い」にしか使えないと明記するべき。
- native 側に cooperative cancel flag を足しても、`RunDecode` 内部で blocking している間は効かない。

holder reset と native 実行中の競合:

- reset / invalidate / close は decode 実行中に走らせてはいけない。
- mutex で排他し、実行中 close は `busy` として拒否するか、probe の終了を待つ。

Android Activity 破棄時の close:

- Activity / Compose dispose だけで blocking native を止めることはできない。
- DEV PoC では activity destroy 時の close request は可能だが、実行中なら busy と診断して
  native 側で session / engine を直接破壊しない。

model 変更時の close:

- holder key mismatch で close -> recreate。
- model file size / lastModified が変わったら必ず別 key とする。
- model path のみ同じでも上書き更新を検知できる。

失敗後に holder を再利用してよいか:

- しない。
- `CreateSession`, `RunPrefill`, `RunDecode` のどれかが失敗したら
  `holder_invalidated=true` とし、以後の run は止める。
- 特に QNN/HTP/FastRPC の internal failure 後は下位 state が不明。

QNN/HTP/FastRPC 下位 resource の解放タイミング:

- one-shot 成功時は `engine_ptr.reset()` で `Engine.close=unique_ptr_cleanup` と記録している。
- persistent holder では Engine を probe 終了まで保持するため、下位 resource の解放は最後になる。
- run ごとの解放対象は session のみ。
- これで run7 failure が消えれば「EngineFactory / compiled model 作成の累積」が本命。
- 消えなければ「Session / RunDecode 側の累積」または下位 backend state の問題。

SIGABRT 時:

- process abort は復旧不能。
- result file / diag file / holder invalidation は書けない可能性が高い。
- crash 前の最後の diag line を重視する。

## 推奨 JNI API

最初は分割 API より probe 用 1 entrypoint を推奨する。

理由:

- Kotlin からの `init` / `run` / `close` 分割呼び出しは Activity lifecycle / cancel / reset 競合が増える。
- まずは「Engine create 1 回 + RunDecode 20 回 + close 1 回」を検証するだけなので、
  native 内で同期的に完結するほうが state が読みやすい。

推奨 entrypoint:

```cpp
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunPersistentProbe(
    JNIEnv* env,
    jclass thiz,
    jstring model_path,
    jstring native_library_dir,
    jstring cache_dir,
    jstring result_path,
    jstring diag_path,
    jstring prompt,
    jstring prompt_input_limit_mode,
    jint max_output_tokens,
    jint run_count,
    jstring holder_key)
```

この entrypoint の要件:

- mutex を取る。
- holder key を比較する。
- key mismatch / invalidated / missing なら既存 holder を close し、新規 Engine を作る。
- `EngineFactory::CreateDefault` はこの entrypoint の実行中に最大 1 回。
- 各 run で fresh `Session` を作る。
- 各 run で `RunPrefill` と `RunDecode` を実行する。
- run 成功後に session を reset する。
- failure 時は `holder_invalidated=true` とし、loop を停止する。
- probe 終了時に Engine close / cleanup する。
- result file は summary + details を key-value 形式で書く。
- diag file は stage history と native tail を追記する。

将来の分割 API 候補:

- `nativePersistentInit(...)`
- `nativePersistentRunDecode(...)`
- `nativePersistentClose(...)`
- `nativePersistentGetStatus(...)`
- `nativePersistentInvalidate(...)`

分割 API は、1 entrypoint PoC で 20 run 成功後に検討する。

## DEV 診断設計

native result file に最低限出す summary:

- `native_holder_entrypoint_available=true`
- `persistent_custom_jni_status`
- `run_count_requested`
- `run_count_completed`
- `success_count`
- `failure_count`
- `engine_create_count`
- `decode_attempt_count`
- `decode_success_count`
- `decode_failure_count`
- `engine_close_reached`
- `engine_close_success`
- `holder_generation`
- `holder_key`
- `holder_reused_count`
- `holder_invalidated`
- `holder_key_mismatch_detected`
- `holder_key_mismatch_reason`
- `first_failure_run_index`
- `first_failure_stage`
- `first_failure_reason`
- `first_failure_exception_class`
- `first_failure_diag_tail`
- `backend_evidence`
- `persistent_custom_jni_hypothesis_result`

run details:

- `run_index`
- `status`
- `reason`
- `session_create_started`
- `session_create_finished`
- `prefill_started`
- `prefill_finished`
- `decode_started`
- `decode_finished`
- `session_cleanup_started`
- `session_cleanup_finished`
- `raw_output`
- `sanitized_output` は Kotlin 側でよいが native raw output は必須。
- `total_ms`
- `session_create_ms`
- `prefill_ms`
- `decode_ms`
- `session_cleanup_ms`
- `failure_stage`
- `failure_reason`
- `native_diag_tail`

Kotlin 側の既存 custom JNI persistent probe は、これらを読んで
`[DEV診断: NPU S1 persistent custom JNI summary/details]` に流し込む。

## failure recovery 設計

stage 別の扱い:

- `model_assets`
  - holder は作らない。
  - `holder_invalidated=true`。
- `engine_settings`
  - holder は作らない。
  - key / cache / backend の問題を疑う。
- `engine_create`
  - holder は作らない。
  - 現在の run7 failure と同じ本命 stage。
- `session_create`
  - Engine は作れたが session 作成に失敗。
  - holder は invalidated として close。
- `prefill`
  - prompt / tokenizer / input path も疑うが、下位 state 不明なので holder invalidated。
- `decode`
  - RunDecode / QNN/HTP 側の失敗。
  - holder invalidated。
- `session_cleanup`
  - session destructor 失敗を直接検出できる API はない可能性が高い。
  - diag 上は cleanup reached / finished を出す。
- `engine_close`
  - close 失敗後は process 内 state が不明なので holder invalidated。

どの failure でも、同一 holder の再利用はしない。

## モデル更新対応

holder key に `model_file_size` と `model_file_last_modified` を含める。

判定:

```text
if current_key != holder.key:
  close current holder if any
  holder_generation += 1
  create new holder
```

`model_path` が同じで file content だけ更新された場合も検知できる。
`engine_config_version` を上げれば native 実装変更時に既存 holder を強制的に捨てられる。

## 実装フェーズ

Phase 1: native 1 entrypoint PoC

- `nativeRunPersistentProbe(...)` を patch に追加。
- Kotlin debug probe から呼ぶ。
- result file parser は既存 one-shot parser を壊さず新規に追加。
- 20 run 成功/失敗を DEV 診断コピーで確認。

Phase 2: failure / cancel / model change 検証

- run6-7 で失敗するか。
- model key mismatch で close -> recreate するか。
- Activity destroy / cancel 時に unsafe close しないか。
- new tombstone / Dropbox が出ないか。

Phase 3: 分割 API 検討

- `init/run/close/status/invalidate` 分割は、必要になった場合のみ。
- 通常チャット接続はまだしない。

Phase 4: 製品経路設計

- persistent holder が 20 run 以上安定し、failure recovery が安全と確認できた後に検討。
- fallback / sanitizer / prompt / TTS / DB / streaming とは別レビューにする。

## 最大リスク

最大リスクは blocking native call と process abort。

- `RunDecode` や QNN/HTP/FastRPC 内部で止まった場合、Kotlin coroutine cancel では止められない。
- `SIGABRT` は Java/Kotlin に戻らない。
- holder が global になることで、実行中 close / reset / model change との競合が増える。
- `Engine` を保持することで下位 resource を長く掴むため、one-shot とは異なる resource pressure が出る可能性がある。

このため、DEV PoC は mutex で完全直列化し、failure 後は必ず invalidated にする。
危険な native session 強制解放や別 thread からの close は入れない。

## 次に実装すべき最小 native PoC

次の実装単位:

1. `patches/qairt244_litertlm_utf8_128token_128input.patch` に
   `nativeRunPersistentProbe(...)` を追加する。
2. static holder:
   - `std::unique_ptr<Engine> engine`
   - `std::string holder_key`
   - `int64_t holder_generation`
   - `bool invalidated`
   - counters
   - mutex
3. Engine create:
   - `ModelAssets::Create`
   - `EngineSettings::CreateDefault`
   - `SetCacheDir`
   - `SetLitertDispatchLibDir`
   - `EngineFactory::CreateDefault`
   - `engine_create_count++`
4. loop:
   - fresh `SessionConfig`
   - `engine->CreateSession`
   - fresh `InputData(InputText(normalized_prompt.c_str()))`
   - `RunPrefill`
   - `RunDecode`
   - `session_ptr.reset()`
5. end:
   - `engine_ptr.reset()`
   - `engine_close_reached=true`
   - `engine_close_success=true` if returned
6. result / diag:
   - summary + per-run details を file に出す。
7. Kotlin debug probe:
   - `native_holder_entrypoint_available=true` を確認し、
     result file を `[DEV診断: NPU S1 persistent custom JNI ...]` に反映する。

## 実装メモ

`patches/qairt244_litertlm_utf8_128token_128input.patch` に
`Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunPersistentProbe`
を追加した。

設計:

- native static holder:
  - `std::unique_ptr<Engine> engine`
  - `holder_key`
  - `generation`
  - `reused_count`
  - `invalidated`
- `std::mutex g_qairt244_persistent_custom_jni_mutex` で entrypoint 全体を排他する。
- holder key mismatch または invalidated の場合は close してから再生成する。
- probe 中の `EngineFactory::CreateDefault` は holder 未生成時の 1 回だけ。
- 各 run は fresh `Session` を作り、`RunPrefill` と `RunDecode` を実行する。
- run ごとに `session_ptr.reset()` する。
- probe 終了時に holder の Engine を close する。
- failure 時は `holder_invalidated=true` として loop を止める。

Kotlin 側:

- `Qairt244ShortMultitokenSmoke.runPersistentProbe(...)` から
  `nativeRunPersistentProbe(...)` を呼ぶ。
- native が `LiteRtLmJniException` を投げても result/diag file を読み、DEV 診断に反映する。
- `NpuS1PersistentCustomJniDevProbe` は result file の summary/details を parse する。
- native entrypoint が未 staging の APK では
  `native_holder_entrypoint_available=false` として安全停止する。

次回実機確認:

- `native_holder_entrypoint_available=true`
- `engine_create_count=1`
- `run_count_completed=20`
- `success_count=20`
- `decode_attempt_count=20`
- `decode_success_count=20`
- `holder_invalidated=false`
- `engine_close_reached=true`
- `engine_close_success=true`
- new tombstone / Dropbox なし

## patch 適用 / rebuild / stage 手順

今回確認した LiteRT-LM checkout:

- checkout: `/home/sato/project/litert-custom-build/LiteRT-LM`
- target file: `kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`
- HEAD: `1d535d5038c6a951b7f9f7adbed69efca1f62566`

context mismatch の原因:

- checkout 側の `litertlm.cc` には、すでに QAIRT244 one-shot S1 の
  `nativeRunEditablePrompt` / 512 token guard / native stage diag が入っていた。
- 旧 `patches/qairt244_litertlm_utf8_128token_128input.patch` は upstream からの
  full patch に近く、既存適用済み hunk と重複していた。
- そのため、line drift ではなく「既存 patch 適用済み checkout に stale full patch を
  もう一度当てようとしていた」ことが主因。

修正内容:

- `patches/qairt244_litertlm_utf8_128token_128input.patch` を、現在の checkout の
  `litertlm.cc` に対する incremental patch として再生成した。
- 追加差分は persistent holder PoC に限定した。
  - `#include <mutex>`
  - `qairt244_persistent_custom_jni_probe_v1` marker
  - native result writer helper
  - static holder / mutex
  - `nativeRunPersistentProbe(...)`
- 既存 one-shot entrypoint は変更しない。

適用確認:

```bash
git -C /home/sato/project/litert-custom-build/LiteRT-LM apply --check \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_128token_128input.patch
```

適用:

```bash
git -C /home/sato/project/litert-custom-build/LiteRT-LM apply \
  /home/sato/project/lami-android/patches/qairt244_litertlm_utf8_128token_128input.patch
```

rebuild:

```bash
scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --label persistent_holder
```

今回の build output:

- `/home/sato/project/lami-android/artifacts/litert_custom_build/20260609_001336_persistent_holder`
- rebuilt library:
  `/home/sato/project/lami-android/artifacts/litert_custom_build/20260609_001336_persistent_holder/built_libs/liblitertlm_jni.so`

entrypoint 確認:

```bash
strings artifacts/litert_custom_build/20260609_001336_persistent_holder/built_libs/liblitertlm_jni.so |
  rg "nativeRunPersistentProbe|qairt244_persistent_custom_jni_probe_v1"

nm -D --defined-only artifacts/litert_custom_build/20260609_001336_persistent_holder/built_libs/liblitertlm_jni.so |
  rg "nativeRunPersistentProbe"
```

stage:

```bash
scripts/stage_litert_custom_build_stack_for_experiment.sh \
  artifacts/litert_custom_build/20260609_001336_persistent_holder
```

stage 先:

- `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/liblitertlm_jni.so`

stage 後の確認:

- `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/liblitertlm_jni.so` に
  `nativeRunPersistentProbe` と `qairt244_persistent_custom_jni_probe_v1` が入っていることを確認した。

standardDebug への取り込み:

- `app/build.gradle.kts` の `stageQairt244StandardDebugNativeLibs` が
  `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a` から
  `app/build/generated/qairt244StandardDebugJniLibs/arm64-v8a` へコピーする。
- `standardDebug` source set は generated JNI libs を packaging 対象にする。
- `./gradlew :app:assembleStandardDebug` 実行時に
  `stageQairt244StandardDebugNativeLibs`, `mergeStandardDebugNativeLibs`,
  `overlayQairt244StandardDebugNativeLibs` が実行されることを確認した。

APK 検証:

- APK: `app/build/outputs/apk/standard/debug/app-standard-debug.apk`
- `unzip -l` で `lib/arm64-v8a/liblitertlm_jni.so` が含まれることを確認した。
- APK から展開した `lib/arm64-v8a/liblitertlm_jni.so` に
  `nativeRunPersistentProbe` と `qairt244_persistent_custom_jni_probe_v1` が入っていることを確認した。
- `nm -D --defined-only` で
  `Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunPersistentProbe`
  が exported symbol であることを確認した。

次回実機確認手順:

1. `./gradlew :app:assembleStandardDebug` で APK を作る。
2. APK 内の `liblitertlm_jni.so` に
   `nativeRunPersistentProbe` または `qairt244_persistent_custom_jni_probe_v1` が
   入っていることを確認する。
3. standardDebug APK を実機へ install する。
4. Lami を起動し、DEV 診断から
   `NPU S1 persistent custom JNI 20回テスト` を実行する。
5. DEV 診断コピーで以下を確認する。
   - `native_holder_entrypoint_available=true`
   - `engine_create_count=1`
   - `run_count_completed=20`
   - `success_count=20`
   - `decode_success_count=20`
   - `holder_invalidated=false`
   - `engine_close_reached=true`
   - `engine_close_success=true`
6. 失敗時は `first_failure_stage`, `first_failure_reason`,
   `first_failure_diag_tail`, `native_diag_tail` を保存し、tombstone / Dropbox が
   新規発生していないか確認する。

## 結論

patch の静的構造上、custom JNI persistent holder 化は DEV PoC として実装可能そうである。
現在の one-shot lifecycle は prompt ごとに `EngineFactory::CreateDefault` まで含めて
完全再生成しており、run6-7 の `engine-create-failed:INTERNAL` と整合する。

最小 PoC は分割 API ではなく `nativeRunPersistentProbe(...)` 1 本で実装し、
Engine create を 1 回、fresh Session + `RunPrefill` + `RunDecode` を 20 回、
最後に Engine cleanup を 1 回にするのが最も安全に検証できる。

成功すれば `EngineFactory::CreateDefault` 累積問題が本命。
失敗が run6-7 のままなら、同一 Engine 内の Session / RunDecode / QNN/HTP/FastRPC
側の累積問題を次に調べる。
