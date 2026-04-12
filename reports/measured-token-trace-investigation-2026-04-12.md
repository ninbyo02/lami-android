# LiteRT-LM measured token trace 実測確認レポート（2026-04-12）

## 実行モード
- モードB（ビルド実行可能か確認して実行）

## 1. branch / worktree 確認
- 実行:
  - `git branch --show-current`
  - `git status --short`
- 結果:
  - branch: `work`
  - 変更なし（clean）

## 2. measured token trace の保存先と出力経路
- 保存先（コード読解）:
  - `context.filesDir/debug/local_reflection_trace.log`
- 生成ログ書式（コード読解）:
  - `UPSTREAM measured-tokens input=... output=... total=... path=...`
- 主な path 候補（コード読解）:
  - `held-official-flow`
  - `held-official-blocking`
  - `official-flow`
  - `official-blocking`
  - `official-direct-flow`
  - `official-direct-blocking`
  - `fallback-official-flow`
  - `fallback-official-blocking`

## 3. 既存 trace ログファイル確認
- リポジトリ配下のファイル探索では `local_reflection_trace.log` は見つからず。
- （注）このログは `filesDir` 配下に出るため、通常は端末内アプリ領域に存在。

## 4. ビルド確認（compileDebugKotlin）
- 実行:
  - `./gradlew :app:compileDebugKotlin`
- 結果:
  - 失敗。表示メッセージは `25.0.1` のみで停止。
- 補足:
  - `java -version` は `25.0.1`。
  - `sdkmanager` は未導入（`command not found` 相当）。

## 5. 実機ログ採取
- 実行候補コマンドを試行:
  - `adb shell run-as io.github.ninbyo02.lami ...`
- 結果:
  - `adb: command not found` のため採取不可。

## 6. local inference 1回実行手順（実機側）
1. 端末で debug ビルドをインストール済みにする（`installDebug` 等）。
2. アプリ起動。
3. local inference を有効化。
4. 「こんにちは」を1回送信し、応答完了まで待つ。
5. 端末側で以下を実行しログ採取:
   - `adb shell run-as io.github.ninbyo02.lami sh -c 'tail -n 400 files/debug/local_reflection_trace.log' | rg -n "UPSTREAM measured-tokens|official-direct|official-blocking|held-official-flow|close-summary|resultLength" -n -C 8`

## 7. 実ログ抽出結果
- 今回の実行環境では `adb` 未導入のため、**実ログ行の取得は未実施**。
- そのため、以下は判定不能:
  - `UPSTREAM measured-tokens ...` 実ログ行
  - 前後文脈
  - `input/output/total` が `null` か数値か
  - `path` 実測値

## 8. 判定
- 実ログ確認: **失敗（環境制約）**
- ログ保存場所: `filesDir/debug/local_reflection_trace.log`（コード上の定義）
- BenchmarkInfo non-null / null 判定: **未判定（実ログ不足）**

## 9. 追加修正要否
- 今回の目的（ログ採取）に対して、**コード修正は不要**。
- 最優先提案（1件）:
  - `adb` が使える環境で同コマンドを再実行し、`UPSTREAM measured-tokens` 実行ログを採取する。
