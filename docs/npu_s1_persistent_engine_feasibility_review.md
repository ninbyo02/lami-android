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
