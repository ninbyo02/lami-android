# LlmInferenceSession.createFromOptions → addQueryChunk → generateResponseAsync の最小PoCを DEV 専用で追加する
## 超安全 Codex ワンタッチフルプロンプト（work対応版）

以下を **そのまま Codex に貼り付けて実行**してください。

---

あなたはこのリポジトリ（`/workspace/lami-android`）専属の実装アシスタントです。  
目的は、**DEV専用**で `LlmInferenceSession.createFromOptions` → `addQueryChunk` → `generateResponseAsync` を通す最小PoCを、安全に既存本線へ影響を出さず追加することです。

## 0. 実行モード宣言
- 先頭で必ず「モードA（人間実行前提）」または「モードB（ビルド実行可能）」を宣言。
- モードBに入る前に次を実行して判定:
  - `test -x ./gradlew`
  - `java -version`
  - `sdkmanager --version`
  - `./gradlew --version`
- どれか満たせない場合はモードAで進める。

## 1. ガードレール（必須）
- 変更は **最小差分**。既存の通常推論フロー（本番相当）を壊さない。
- PoCは **DEVフラグで完全隔離**（デフォルトOFF）。
- 例外時は握りつぶさず、原因カテゴリ（create/addQueryChunk/generate）を判別可能なログを残す。
- 秘密情報・トークン・端末固有情報をログ出力しない。
- `main` / `stable` へ直接pushしない。featureブランチ前提。

## 2. 事前調査（必須）
以下を順に実行し、該当ファイル・行番号・参照元を整理してから着手。

```bash
rg -n "LlmInferenceSession|createFromOptions|addQueryChunk|generateResponseAsync|DEV" app/src/main
rg -n "tryCreateLlmInferenceSessionViaReflectionForDev|tryCallLlmInferenceSessionGenerateResponseAsyncForDev" app/src/main
nl -ba app/src/main/java/com/sonusid/ollama/ui/screens/home/ChatScreen.kt | sed -n '2520,2960p'
```

## 3. 実装方針
- 既存の `ChatScreen.kt` にある反射ユーティリティを再利用。
- 新規追加ではなく、既存のDEV専用経路が未接続/不十分な場合のみ補強する。
- 最低限、以下を満たす:
  1. `createFromOptions` 成功/失敗ログ
  2. `addQueryChunk` 呼び出しログ
  3. `generateResponseAsync` の戻り型判定（Future系）ログ
  4. タイムアウト付き待機（短時間）と結果文字列化
  5. 後始末（close）

## 4. 受け入れ条件（Definition of Done）
- デフォルト設定ではPoC経路は動かない（OFF）。
- DEVフラグON時のみPoC経路を試行。
- 失敗時に「どの段階で失敗したか」がログで判読可能。
- 既存フローの戻り値契約を変更しない。
- Kotlinコンパイルが通る。

## 5. テスト方針（最小）
- Unit優先。難しい場合はPoC判定ロジックを小関数化してテスト。
- 少なくとも「DEVフラグOFF時にPoCが実行されない」ことを検証。
- 反射本体はインターフェース抽象化か関数分離でテスト可能範囲を作る。

## 6. ドキュメント更新
- `docs/` に「DEV専用PoCであること」「有効化条件」「失敗時ログの見方」を追記。
- 将来削除しやすいよう、暫定コードである旨を明記。

## 7. 実行コマンド（原則1つ）
目的がコンパイル健全性確認なので、最後は原則これを提示/実行:

```bash
./gradlew :app:compileDebugKotlin
```

## 8. 失敗時リカバリ
- `Unresolved reference`:
  - import不足 or 関数スコープ不整合を確認。
- `NoSuchMethodException`:
  - メソッドシグネチャ差異。`methods`列挙ログを追加して再判定。
- `InvocationTargetException`:
  - `targetException` を展開し、create/add/generate のどこかに分類。
- `ClassNotFoundException`:
  - 依存ライブラリ/ABI/variant差分を確認。

## 9. 最終出力フォーマット
最終報告は必ず次の順:
1. 実行モード宣言（A/B）
2. 事前調査結果（ファイル・行番号・影響範囲）
3. 修正方針
4. 修正コード（必要箇所）
5. 追加テスト
6. ドキュメント差分
7. 次に実行するコマンド（1つ）
8. （モードBのみ）ビルド実行結果要約

---

### 補足（運用メモ）
- このPoCは **検証用**。常時利用の本実装に昇格させる場合は、反射依存を段階的に排除し、Port/Adapter境界を明確化すること。
- クリーンアーキテクチャの依存方向（内→外）を維持し、UI層に実装詳細を閉じ込めること。
