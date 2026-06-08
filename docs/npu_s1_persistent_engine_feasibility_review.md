# NPU S1 Persistent Engine Feasibility Review

## 1. 調査目的

NPU Standard Route S1 / custom JNI repeated run が run6-7 付近で
`adapter_failure:LiteRtLmJniException` になる原因を踏まえ、S1 custom JNI を
「毎回 `EngineFactory::CreateDefault` する one-shot 設計」から
「プロセス内で Engine を保持する常駐Engine設計」へ移行できるかを静的調査する。

今回は実装しない。目的は、可否、設計案、リスク、最小PoCを整理すること。

## 2. 現在の実機観測まとめ

実機観測:

- `reuse`: run7 failure
- `recreate`: run7 failure
- `recreate_3s`: run6 failure
- `reuse_30s`: run7 failure
- `reuse_30s` では合計 180 秒待機しても失敗
- failure stage: `native_call`
- native diag:
  - `before EngineFactory::CreateDefault`
  - `engine-create-failed:INTERNAL`
  - `runtime/executor/llm_litert_npu_compiled_model_executor.cc:2725`
  - `external/litert/litert/cc/litert_compiled_model.h:1140`
- 成功 run では:
  - `Engine.close=unique_ptr_cleanup`
  - `cleanup_reached=true`
  - `cleanup_finished=true`
- 新規 tombstone なし
- Dropbox なし

静的には、30 秒待機でも run6-7 failure が移動しないため、単純な
QNN / HTP / FastRPC cleanup 遅延よりも、同一プロセス内での
`EngineFactory::CreateDefault` / LiteRT compiled model / QNN runtime 資源の
累積問題が濃厚である。

## 3. 毎回 EngineFactory::CreateDefault の問題点

現在の S1 custom JNI 経路は、各 run ごとに native one-shot を実行する。

Kotlin 経路:

```text
ChatScreen
-> NpuStandardRouteS1Bridge
-> RealNpuStandardRouteS1Provider
-> DevOnlyNpuOneTurnConversationEntry
-> Qairt244DevOnlyNpuRouteAdapter
-> Qairt244ShortMultitokenSmoke.runEditablePrompt
-> nativeRunEditablePrompt
```

主な参照:

- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt`
- `app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1Provider.kt`
- `app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuOneTurnConversationEntry.kt`
- `app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244DevOnlyNpuRouteAdapter.kt`
- `app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt`

native 実装は checked-in C++ source としては存在せず、patch / artifact evidence として
`patches/qairt244_litertlm_utf8_128token_128input.patch` に表れている。
`nativeRunEditablePrompt` は以下を毎回実行する。

```text
ModelAssets::Create
GetBackendFromString("NPU")
EngineSettings::CreateDefault
SetCacheDir
SetLitertDispatchLibDir
EngineFactory::CreateDefault
CreateSession
RunPrefill
RunDecode
session_ptr.reset()
engine_ptr.reset()
```

問題点:

- `EngineFactory::CreateDefault` が run ごとに QNN / HTP / FastRPC / LiteRT compiled model を再初期化する。
- 成功時は `unique_ptr` cleanup に到達しているが、QNN 側の process-global state が同期的に完全解放された証明ではない。
- failure は `EngineFactory::CreateDefault` 内部で `INTERNAL` になっており、decode や sanitizer 以前で止まっている。
- `recreate` / `recreate_3s` は `LocalInferenceEngineHolder` に対する DEV recreate であり、S1 custom JNI の native Engine には接続されていない。
- 30 秒待機でも復旧しないため、短い cleanup delay では説明しにくい。

## 4. 常駐Engine化が可能か

結論: 可能性はある。ただし、最初に試すべきなのは Java/Kotlin official
LiteRT-LM `Engine` を保持する設計であり、C++ `EngineFactory::CreateDefault`
由来の native `Engine` を static holder 化する設計は PoC 段階でも高リスクである。

根拠:

- Lami には既に Java/Kotlin official LiteRT-LM `Engine` を保持する
  `LocalInferenceEngineHolder` がある。
- `LocalInferenceEngineHolder` は `HeldEngineKey(modelPath, backendKey, cacheDirPath)`
  で `Engine` を再利用し、model/backend/reset/fatal/low-memory/background/TTS などで閉じる。
- official Java API 側には `Engine.createConversation(...)` や `Engine.createSession(...)`
  の存在が repo-local static inspection で確認されている。
- Gallery parity 系の debug benchmark は `Engine` を作成し、`Conversation` を作成し、
  `sendMessageAsync(contents, callback, ...)` で生成し、最後に
  `conversation.close()` / `engine.close()` する。

一方で、C++ lower-level `EngineFactory::CreateDefault` の `Engine` が
複数 `Session` / 複数 `RunDecode` を安全に処理できるかは、repo 内では未証明である。
現行 patch は 1 Engine -> 1 Session -> 1 Prefill -> 1 Decode しか試していない。

## 5. 可能な場合の設計案

優先順位は以下。

### 案1: Java/Kotlin official Engine holder に S1 PoC を寄せる

既存 `LocalInferenceEngineHolder` と Gallery parity 参考実装を使い、S1 専用の
DEV PoC として以下を試す。

```text
Engine initialize once
for each prompt:
  create fresh Conversation or Session
  run one prompt
  close Conversation or Session
keep Engine
```

利点:

- 既存 holder / lifecycle / close 設計を流用できる。
- Java API の `Engine.close()` / `Conversation.close()` を使える。
- Gallery に近い lifecycle へ寄せられる。
- custom JNI の `EngineFactory::CreateDefault` 累積を避けられる可能性が高い。

欠点:

- 現在の S1 custom JNI と出力・sanitizer・diagnostic shape が異なる可能性がある。
- NPU backend attach が Gallery probe と同じ `nativeCreateEngine` SIGABRT 問題を踏む可能性がある。
- S1 の raw dialog tail / max tokens / Japanese-only constraints を Java API 経路へ移植する必要がある。

### 案2: native Engine holder を custom JNI 内に置く

native static holder を置く。

```text
static mutex
static holder {
  modelPath
  nativeLibraryDir
  cacheDir
  unique_ptr<Engine>
  generation
  poisoned
}

runPrompt:
  lock
  if no engine or key changed or poisoned:
    create EngineFactory::CreateDefault once
  create fresh Session
  RunPrefill
  RunDecode
  reset Session
  keep Engine
```

利点:

- 既存 custom JNI の prompt/result/diag を維持しやすい。
- `EngineFactory::CreateDefault` 回数を減らせる。
- `maxOutputTokens` は `DecodeConfig` 側なので、Engine key から外せる可能性が高い。

欠点:

- C++ `Engine` の multi-session support が未証明。
- JNI blocking call に対して coroutine timeout は native 実行を止められない。
- failure / timeout 後に holder が安全か判定しにくい。
- Android lifecycle と明示的に接続しないと process lifetime まで QNN resource を保持する。

## 6. 不可能な場合の代替案

常駐Engineが成立しない場合の代替案:

- NPU S1 を同一 process 内 repeated run ではなく isolated process / probe process へ逃がす。
- run6-7 累積問題を避けるため、NPU Standard Route S1 を production では無効化し DEV 限定に戻す。
- 一定回数ごとに app process restart / worker process restart する。ただし UX と安全性が悪く、通常チャットには不適。
- NPU route を Gallery-compatible Java API path へ全面移行し、custom JNI one-shot を廃止する。
- NPU が安定しない端末では GPU fallback / CPU fallback を明示する。ただし今回の S1 repeated run 目的とは別。

## 7. JNI側 holder 設計案

native holder key:

- `modelPath`
- `nativeLibraryDir`
- `cacheDir`
- backend: fixed `NPU`
- model basename / file size / modified time

Engine recreate 条件:

- key changed
- model file missing or modified
- explicit close/reset requested
- previous run threw before session cleanup
- timeout observed by Kotlin side
- native generation marked poisoned
- low memory / app background / lifecycle close

`maxOutputTokens` は `DecodeConfig.SetMaxOutputTokens(...)` で run ごとに変える値なので、
Engine recreate 条件にはしない。ただし native library / LiteRT API が max token を
Engine settings にも持つ場合は、PoC で再確認する。

thread safety:

- native holder access は `std::mutex` で直列化する。
- concurrent prompt は禁止する。
- recursive call / nested call を検出し `busy` として失敗させる。

session policy:

- まずは "persistent Engine + fresh Session per prompt" のみ試す。
- same Session に対する複数 prompt / multiple `RunPrefill` は試さない。
- session cleanup は毎 run 必須。

diagnostics:

- `engine_generation`
- `engine_create_count`
- `engine_reuse_count`
- `session_create_count`
- `session_close_count`
- `holder_poisoned`
- `holder_key_hash`
- `holder_close_reason`
- `native_thread_id`
- `engine_factory_call_count`

## 8. Kotlin側 holder 設計案

Java/Kotlin official API に寄せる場合は、既存 `LocalInferenceEngineHolder` を
S1 用に使うか、S1 専用 holder を薄く作る。

推奨は S1 専用 wrapper:

```text
NpuS1PersistentEngineProvider
  acquire S1 Engine by modelPath/backend/cacheDir
  create fresh Conversation per prompt
  send prompt
  close Conversation
  keep Engine
```

理由:

- 既存 local holder は通常チャット / GPU / CPU も扱うため、S1 custom NPU の失敗で
 通常生成側を巻き込まない方がよい。
- S1 には DEV diagnostics と hard gate が必要。
- NPU failure recovery は通常 local inference より保守的にすべき。

ただし lifecycle hook は既存 holder と同じイベントへ接続する:

- model changed
- backend changed
- explicit reset
- fatal error
- low memory
- app backgrounded / idle timeout
- TTS playback
- Activity / process lifecycle

## 9. failure recovery 設計

原則:

- native failure / timeout 後の Engine 再利用は禁止。
- timeout は特に危険。Kotlin coroutine cancellation では blocking JNI は止まらないため、
  native call が戻るまで holder を安全に close できるとは限らない。
- `LiteRtLmJniException` / `engine-create-failed:INTERNAL` / QNN error は holder poison。
- missing cleanup evidence は holder poison。

推奨 state:

```text
READY
BUSY
POISONED
CLOSING
CLOSED
```

recovery:

- `READY -> BUSY -> READY`: success and session cleanup confirmed
- `BUSY -> POISONED`: throwable, timeout, decode failure, cleanup missing
- `POISONED -> CLOSED`: explicit close only
- `CLOSED -> READY`: next acquire creates a new Engine

timeout policy:

- blocking native call が戻らない可能性があるため、通常 app flow では timeout 後の
  immediate recreate をしない。
- DEV PoC では timeout 後 `reuse_allowed=false` とし、以後の run を止める。
- production に入れるなら worker process isolation も検討する。

## 10. Android lifecycle 対応

JNI holder を採用する場合、既存 `LocalInferenceEngineHolder` の lifecycle とは別に
明示的 native close API が必要。

必要 API:

- `nativeClosePersistentEngine(reason)`
- `nativeResetPersistentEngine(reason)`
- `nativeGetPersistentEngineDiagnostics()`

呼ぶべきタイミング:

- model selection changed
- app backgrounded / idle timeout
- low memory
- TTS playback before start
- explicit DEV reset
- Activity destroyed if S1 running or holder exists
- fatal error / safety stop

注意:

- Activity destroy だけを release boundary として信用しない。
- process-global QNN state の問題であれば Activity restart では不足する可能性がある。
- force-stop / separate process はより強い isolation だが UX への影響が大きい。

## 11. AI Edge Galleryとの差分

Gallery / Gallery parity に近い設計:

- `Engine` を作る。
- `Conversation` を作る。
- prompt を `sendMessageAsync` で流す。
- callback done / error を terminal boundary とする。
- `Conversation.close()` / `Engine.close()` を明示する。
- sampler / conversation config を使う。

Lami S1 custom JNI:

- run ごとに `EngineFactory::CreateDefault`。
- run ごとに `CreateSession`。
- run ごとに `RunPrefill` / `RunDecode`。
- success path は `unique_ptr` reset。
- Kotlin から Engine / Session instance は見えない。
- `recreate` mode は normal local holder を閉じるだけで S1 native state には効かない。

取り込むべき方針:

- Engine creation を prompt ごとに繰り返さない。
- request 単位では Conversation / Session を新規作成し、Engine は保持する。
- terminal callback / cleanup evidence / run-id を再利用可否の条件にする。
- failure 後は conservative に poison する。

## 12. 実装フェーズ分け

Phase 0: docs / static review

- 本レポート。

Phase 1: diagnostic-only PoC

- standardDebug / DEV only。
- UI通常経路には接続しない。
- `NPU S1 persistent engine 20回テスト` 相当を hidden/dev path で作る。
- まずは Java/Kotlin official Engine + fresh Conversation per prompt を試す。

Phase 2: custom JNI holder PoC

- native static holder を debug flavor のみで実装。
- fresh Session per prompt。
- holder diagnostics を result file に出す。
- timeout / throwable / cleanup missing は poison。

Phase 3: repeated run比較

- one-shot custom JNI vs persistent Java Engine vs persistent native Engine を比較。
- 指標:
  - failure run index
  - `EngineFactory::CreateDefault` count
  - `engine-create-failed:INTERNAL`
  - session create count
  - cleanup evidence
  - native PSS / system available memory

Phase 4: guarded integration decision

- run20 以上の安定性。
- Activity background / foreground。
- model switch。
- low memory。
- timeout recovery。
- no fallback behavior regression。

## 13. リスク

最大リスク:

- persistent native Engine が QNN / HTP / FastRPC resource をさらに長く保持し、
  failure recovery を難しくする。

その他:

- C++ Engine multi-session support が未証明。
- JNI blocking call timeout 後に native がまだ走っている可能性。
- holder close と active native call の競合。
- model path changed 時の stale Engine。
- cache dir / native library dir changed 時の stale backend。
- failure 後に stale compiled model state を再利用して次 run が壊れる。
- Java API path と custom JNI path の出力差分。
- Gallery-compatible Conversation API の prompt formatting 差分。
- production に入れると NPU failure が通常チャット UX に波及する。

## 14. 次に実装すべき最小PoC

最小PoCは、custom JNI holder ではなく Java/Kotlin official Engine path を推奨する。

PoC名:

```text
NPU S1 persistent Java Engine DEV probe
```

内容:

- standardDebug / DEV only。
- 通常チャットには接続しない。
- model path は S1 required SM8750 model のみ。
- `Engine` を 1 回 initialize。
- 各 run で fresh `Conversation` を作成。
- prompt を raw dialog tail variant C 相当に整形。
- `sendMessageAsync` or Gallery parity `Contents` callback で生成。
- run ごとに `Conversation.close()`。
- `Engine.close()` はテスト終了時のみ。

見るべき値:

- `engine_initialize_count=1`
- `conversation_create_count=20`
- `conversation_close_count=20`
- `failure_run_index`
- `nativeCreateEngine` tombstone 有無
- `engine-create-failed:INTERNAL` 有無
- output quality / run_decode_reached 相当

成功条件:

- 20 runs 完了。
- run6-7 failure が消える。
- new tombstone / dropbox なし。
- output が S1 quality gate を満たす。

失敗した場合:

- Java API `nativeCreateEngine` 側でも同じ resource limit を踏む可能性。
- 次に native holder PoC を検討する。ただし timeout poison と explicit close API を必須にする。

## 15. 結論

常駐Engine化は検討価値が高い。実機結果から、S1 custom JNI の
「毎回 `EngineFactory::CreateDefault`」は安定性上の本命リスクであり、
30 秒待機でも run7 failure が変わらないため、短い cleanup 遅延ではなく
process 内の EngineFactory / LiteRT compiled model / QNN / HTP / FastRPC の
累積 state 問題と見るのが自然である。

ただし、C++ lower-level `EngineFactory::CreateDefault` 由来の `Engine` を
static holder 化できるかは repo-local evidence では未証明である。
最初に作るべき PoC は、Gallery に近い Java/Kotlin official Engine 常駐設計である。
その PoC で run20 が通るなら、S1 の将来設計は
"persistent Engine + per-request Conversation/Session" へ寄せるべきである。

## 16. DEV PoC 実装メモ

`standardDebug` / DEV 診断専用で `NpuS1PersistentEngineDevProbe` を追加した。
通常チャット送信、既存 S1 custom JNI repeated run、fallback、TTS、DB、streaming には
接続しない。

PoC の目的:

- custom JNI の run ごと `EngineFactory::CreateDefault` を避ける。
- official Java/Kotlin LiteRT-LM `Engine` を 1 回だけ `initialize()` する。
- 同じ `Engine` から run ごとに fresh `Conversation` を作る。
- run ごとに `Conversation.close()` し、20 run 後に `Engine.close()` する。
- 結果は DEV 診断コピーの
  `[DEV診断: NPU S1 persistent engine summary]` /
  `[DEV診断: NPU S1 persistent engine details]` に出す。

custom JNI repeated run との差分:

- custom JNI は各 run で native one-shot を呼び、native 側で
  `EngineFactory::CreateDefault` から `RunDecode` までを毎回実行する。
- persistent Engine PoC は official Java/Kotlin `Engine` を保持し、
  `Engine.initialize()` を loop 外で 1 回だけ呼ぶ。
- prompt は既存 S1 と同じ DEV repeated run default prompt と raw dialog tail variant を使う。
- sanitizer は debug 側の `Qairt244NpuOutputSanitizer` を使う。

Conversation / Session の扱い:

- `Conversation` は run ごとに `engine.createConversation(...)` で作成し、
  run 後に `conversation.close()` する。
- `Session` API は official API 上存在する。
- Conversation mode では `sendMessageAsync` が
  `Decode for logits output not implemented for backend: LiteRT NPU Compiled Model`
  で失敗した。
- そのため PoC の `auto` mode は direct `Session.generateContent(List<InputData>)` を
  優先する。`Conversation` 実装は比較用に残す。
- `Session.generateContentStream(List<InputData>, ResponseCallback)` も surface 上は存在するが、
  最小修正ではまず blocking `generateContent` を試す。

token limit 修正:

- 初回 PoC では `Engine.initialize()` は成功した。
- ただし run1 の `sendMessageAsync` で
  `Input token ids are too long. Exceeding the maximum number of tokens allowed: 78 >= 32`
  により失敗した。
- 原因は、PoC が `EngineConfig.maxNumTokens` に S1 の
  `MAX_OUTPUT_TOKENS=32` を渡していたこと。
- repo 内の official Java/Kotlin LiteRT-LM API surface では、`sendMessageAsync` /
  `ConversationConfig` / `SamplerConfig` に output 専用 token limit は見つからない。
- `maxNumTokens` は output limit ではなく total/input/context token budget として
  扱うべきなので、PoC では `official_total_token_limit=512` に上げる。
- requested output としての S1 期待値は
  `requested_max_output_tokens=32` として診断に分離する。
- output 専用 limit は official API では `official_output_token_limit=not_exposed`
  と扱う。
- この run1 failure は token budget 設定の問題であり、persistent Engine 仮説自体は
  まだ否定されていない。

logits failure 調査:

- `maxNumTokens=512` 後、official Engine initialize と Conversation create/close は成功した。
- ただし `Conversation.sendMessageAsync` の decode は
  `Decode for logits output not implemented for backend: LiteRT NPU Compiled Model`
  で失敗した。
- `SamplerConfig` / `ConversationConfig` / `SessionConfig` に logits output を無効化する
  public option は repo 内 surface では見つからない。
- `Session` surface には `generateContent`, `generateContentStream`, `runPrefill`,
  `runDecode` がある。
- PoC は `persistent_engine_api_mode=auto` とし、
  `selected_api_mode=session` で `Session.generateContent` を試す。
- session / streaming でも同じ logits failure の場合、official persistent Java/Kotlin
  Engine の NPU decode はこの backend/model では困難と見なし、次は custom JNI
  persistent Engine holder PoC を検討する。

成功時の判定:

- `engine_initialize_count=1`
- `run_count_completed=20`
- `success_count=20`
- `conversation_create_count=20`
- `conversation_close_count=20`
- `persistent_engine_hypothesis_result=engine_initialize_once_20_runs_success`
- アプリ生存、new tombstone なし、Dropbox なし

失敗時の読み方:

- `persistent_engine_hypothesis_result=engine_initialize_once_failed`
  - official Java/Kotlin Engine の NPU attach / `nativeCreateEngine` で失敗。
- `persistent_engine_hypothesis_result=conversation_create_failed`
  - Engine は作れたが fresh Conversation 作成で失敗。
- `persistent_engine_hypothesis_result=token_limit_failed`
  - official total/input/context token limit が prompt より小さい。
- `persistent_engine_hypothesis_result=logits_output_not_supported_on_npu_backend`
  - selected API mode が NPU backend で logits output を要求している。
- `persistent_engine_hypothesis_result=decode_failed`
  - Engine/Session or Conversation 作成後の generate/decode でその他失敗。
- `engine_initialize_count=1` かつ run6-7 で `decode_failed`
  - Conversation 経路でも同一 Engine 内の累積問題がある可能性。

次回実機確認手順:

1. Lami を `standardDebug` で起動する。
2. DEV 診断を開く。
3. `NPU S1 persistent Engine 20回テスト` を実行する。
4. 停止後、DEV 診断コピーを保存する。
5. 以下を見る:
   - `engine_initialize_count`
   - `run_count_completed`
   - `success_count`
   - `conversation_create_count`
   - `conversation_close_count`
   - `session_create_count`
   - `session_close_count`
   - `persistent_engine_api_mode`
   - `attempted_api_modes`
   - `selected_api_mode`
   - `session_api_available`
   - `session_api_used`
   - `conversation_api_used`
   - `streaming_api_used`
   - `logits_failure_detected`
   - `logits_failure_message`
   - `requested_max_output_tokens`
   - `official_total_token_limit`
   - `official_output_token_limit`
   - `token_limit_source`
   - `first_failure_stage`
   - `first_failure_reason`
   - `persistent_engine_hypothesis_result`

release 影響:

- official LiteRT-LM typed 実装は `app/src/debug` に置く。
- main 側は `NpuS1PersistentEngineProbeRunner` interface と reflection で debug 実装を探すだけ。
- release / 他 variant で debug 実装が存在しない場合は runner unavailable として止まる。

## Custom JNI persistent holder PoC

目的:

- custom JNI S1 の one-shot 経路が毎回 `EngineFactory::CreateDefault` を呼ぶ設計を避け、
  1 つの native Engine で `RunDecode` を 20 回実行できるかを検証する。
- 既存 S1 repeated run / official persistent Engine PoC とは独立した DEV 専用 PoC とする。

現時点の実装状況:

- アプリ側には `NPU S1 persistent custom JNI 20回テスト` の DEV UI と
  `[DEV診断: NPU S1 persistent custom JNI summary/details]` を追加した。
- holder key は以下で構成する:
  - `model_path`
  - `model_file_last_modified`
  - `model_file_size`
  - `backend`
  - `cache_dir`
  - `max_token_budget`
  - `engine_config_version`
- これらのいずれかが変わった場合は native holder key mismatch として既存 holder を
  close / cleanup して再生成する設計にする。
- ただし、現在 repo にチェックインされているアプリ内 C++ は sentinel / direct probe のみで、
  `EngineFactory::CreateDefault` / `RunDecode` を実装している実体は
  `patches/qairt244_litertlm_utf8_128token_128input.patch` から作られる
  `liblitertlm_jni.so` 側にある。
- そのため今回の Kotlin 側 PoC は native persistent holder エントリポイントの有無を
  DEV 診断へ出し、未実装なら
  `persistent_custom_jni_hypothesis_result=native_holder_entrypoint_not_available`
  として安全停止する。

native holder で必要な最小設計:

- native 側に holder generation と holder key を持つ。
- probe start 時に holder key を比較する。
- key が一致し有効な holder があれば `EngineFactory::CreateDefault` は呼ばず reuse する。
- key が不一致、未生成、invalidated の場合だけ既存 holder を cleanup し、
  `EngineFactory::CreateDefault` を 1 回呼ぶ。
- 各 run は同じ Engine から fresh Session を作り、`RunPrefill` / `RunDecode` を実行する。
- run 成功後は Session を破棄する。Engine は probe 全体の最後まで保持する。
- run failure 時は `holder_invalidated=true` とし、それ以降の run は止める。
- probe end / cancel 時に Engine close / unique_ptr cleanup を 1 回だけ実行する。

DEV 診断で見る key:

- `persistent_custom_jni_status`
- `engine_create_count`
- `engine_close_reached`
- `engine_close_success`
- `holder_key`
- `holder_generation`
- `holder_reused_count`
- `holder_invalidated`
- `native_holder_entrypoint_available`
- `first_failure_stage`
- `first_failure_reason`
- `first_failure_diag_tail`
- `persistent_custom_jni_hypothesis_result`

成功判定:

- native 実装追加後に `engine_create_count=1`
- `run_count_completed=20`
- `success_count=20`
- `holder_reused_count=19` 以上
- `engine_close_reached=true`
- `engine_close_success=true`
- アプリ生存、new tombstone なし、Dropbox なし

失敗判定:

- `native_holder_entrypoint_available=false`
  - app 側ではなく `liblitertlm_jni.so` patch に persistent holder entrypoint を追加する必要がある。
- `engine_create_count=1` でも run6-7 failure
  - `EngineFactory::CreateDefault` 累積ではなく、同一 Engine 内の Session / RunDecode 側の累積問題。
- `holder_key_mismatch_detected=true`
  - model 更新 / cache dir / token budget / config version の変更検知は機能している。

次に作るべき native PoC:

1. `patches/qairt244_litertlm_utf8_128token_128input.patch` に
   `nativeRunEditablePromptPersistentHolder` 相当の DEV 専用 JNI を追加する。
2. holder key を native に渡し、key mismatch 時のみ Engine を再生成する。
3. `engine_create_count`, `holder_generation`, `holder_reused_count`,
   `holder_invalidated`, `Engine.close=unique_ptr_cleanup` を native diag に出す。
4. Kotlin debug probe からその entrypoint を呼ぶ。
5. standardDebug APK に staging した native artifact で実機確認する。
