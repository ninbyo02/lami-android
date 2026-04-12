# LiteRT-LM local inference token 実測可否 調査メモ（2026-04-12）

## 目的
- lami-android の local inference 経路（held-official-flow / official-flow / blocking fallback）で、`inputTokens` / `outputTokens` / `totalTokens` の実測取得点があるかを確認する。

## 調査結果（結論）
- 現行実装では、3 指標とも「UI表示に使う実測値としては未接続」。
- ただし LiteRT-LM 公式 Kotlin API（`BenchmarkInfo`）には prefill/decode token カウントが存在するため、**最小差し込みで実測取得自体は可能**。
- よって分類は以下。
  - inputTokens: `CANDIDATE_ONLY`
  - outputTokens: `CANDIDATE_ONLY`
  - totalTokens: `CANDIDATE_ONLY`

## 根拠（コード棚卸し）
1. local official flow は `Conversation.sendMessageAsync(...).collect { message -> ... }` で `message.contents` からテキストのみ抽出している。token usage を取得・保存していない。
2. local blocking flow も `conversation.sendMessage(prompt)` の戻り `Message` からテキストのみ抽出している。usage/token フィールド参照なし。
3. held engine flow も `sendMessageAsync` / `sendMessage` の戻り値からテキスト抽出のみで、token を trace へ詰めていない。
4. 既存の `LocalStatsCandidateProbe` は `getTokenCount` 等の候補を reflection 探索するが、`BenchmarkInfo` 由来の token 値は見に行っていない。
5. `LocalInferenceResolvedStatsResolver` は `trace.outputTokenProbe` と `trace.estimatedTokenProbe` を読む設計で、公式 flow の token 実測値注入ポイントは未配線。
6. 依存 `litertlm-android:0.10.0` の API には `Conversation.getBenchmarkInfo()` があり、`BenchmarkInfo.getLastPrefillTokenCount()` / `getLastDecodeTokenCount()` が公開されている（=実測候補API）。

## 最小差し込み口（実装するなら）
- official / held の各成功パスで、`sendMessageAsync` collect 完了直後（または blocking `sendMessage` 成功直後）に `Conversation.getBenchmarkInfo()` を 1 回読み出し、
  - input = `lastPrefillTokenCount`
  - output = `lastDecodeTokenCount`
  - total = input + output
  を `LocalInferenceMeasuredTokenSnapshot`（既存）へ渡すのが最小。
- 既存の安全表示（MEASURED / DERIVED / ESTIMATED / API_CANDIDATE_ONLY / UNAVAILABLE）を維持したまま配線できる。

## 仕様判断
- 今回の時点では実装未着手のため、プロダクト仕様としては「候補のみ表示（API_CANDIDATE_ONLY）継続」が妥当。
- 次の1手は、上記最小差し込みを **trace追加レベルで限定実装** し、実機で non-null が安定するかを確認すること。
