# 2026-09-12 Draft PR 7件の詳細判断

基準: `074db1037799811e0e3d70316d43ef9d27ea2f61`。差分・現行コード・履歴・テストを照合。
旧PRのコードをマージする必要性と、解決すべき課題の有無を分けて判定した。

結論: 必要な課題が残る2件（#680、#2446）はDraftで維持。不要な5件は理由付きでクローズ済み。元のブランチと履歴は保存する。

| PR | 判断 | 根拠・対応 |
|---|---|---|
| [#680](https://github.com/ninbyo02/lami-android/pull/680) | 必要・要再構成 | 必要：現行APIに合わせた回帰テスト再整備。4973f92dで隔離8件のうち3件復帰・5件削除。Retrofit初期化は代替テストあり。挿入アニメの確率・周期・クールダウン判定、スプライト座標変換、非空サーバー保存とモデル選択の一部は同等の直接テストを確認できない。古いヘッダー余白変更は除外し、不足契約だけをテスト化する。旧PRは現状のままマージ不可。 |
| [#1510](https://github.com/ninbyo02/lami-android/pull/1510) | 不要・クローズ | 不要：旧36dp IconButtonへの(+2,-2)dp offset。現在は72dpプレビュー右上の28dp円形Box、上端・右端1dp余白、24dp背景と14dpアイコンに再設計済み。旧寸法の微調整を復活させる根拠がなく、境界外へずらす変更は採用しない。視覚的な不具合の再現を主張する判断ではない。 |
| [#1628](https://github.com/ninbyo02/lami-android/pull/1628) | 不要・クローズ | 不要：削除ボタンの右余白0→8dpのみ。現在はServerRowTrailingSlotWidthで専用領域を予約し、保存ボタンと削除ボタンを右余白0dpで揃えている。削除側だけ8dpずらす必要はなく、既存の整列方針を維持する。 |
| [#1679](https://github.com/ninbyo02/lami-android/pull/1679) | 不要・クローズ | 不要：検索クリアを右端へ寄せる16→8dp変更。現在は既にend=0dpで、IconButton自体の領域を確保している。8dpへ戻すと元PRの目的と逆方向になる。 |
| [#1778](https://github.com/ninbyo02/lami-android/pull/1778) | 不要・クローズ | 不要：コードブロックのアクセント追加は現行UIで実現済み。primaryの外枠、1.5dp/alpha0.35の上端線、ラベルとコピーアイコンのprimary混色がある。旧1dp/alpha0.42と単なる透明度変更を戻す必要はない。行番号・生成中表示を含む現行描画を維持する。 |
| [#2267](https://github.com/ninbyo02/lami-android/pull/2267) | 不要・クローズ | 不要：旧差分は採用しない。更新回数÷時間をtoken/sとするのは単位が誤り。LaunchedEffectでの状態観測は描画完了時刻ではなく、イベント集約・完了との順序にも影響される。現行mainの体感速度はoutputTokens÷totalDurationで、旧計算式は置換済み。UI更新の診断を将来追加するなら更新/sとして要求単位の集計器に分離する。なお現行DEV sourceラベルに旧assistantUpdateCount式が残る不整合は別途小修正の対象で、このPRを採用する理由にはならない。 |
| [#2446](https://github.com/ninbyo02/lami-android/pull/2446) | 必要・要再構成 | 必要：インデント修復の安全性修正。現行MarkdownCodeRepairをコンパイルして実行し、正しいdef main()/while/forのコードが書換え後にPython構文エラーになる例を再現。トップレベルforも正常な4/8空白が8/12へ変更される。旧PRの親相対補正という意図は必要だが現行実装は複数行処理へ変化しており、旧差分だけでは十分でない。正常入力不変・ネスト保持・代表的破損入力・fence外不変の回帰テストを伴う再実装が必要。 |

## #680: 削除されたテストを「復旧済み」と取り違えない

`4973f92d075a57e18360ef56a38467b5d35e4729` は3ファイルを復帰し5ファイルを削除した。
`testDisabled` が現在存在しないことだけでは、テスト目的の達成を証明できない。
`app/src` 全体のテストソースを検索し、同名だけでなく検証対象の呼び出しとassert内容も確認した。

| 当時のテスト | 現状 | 残作業 |
|---|---|---|
| SpriteAnalysisTest | src/testに復帰、旧3ケース保持 | この目的での復旧不要 |
| UrlUtilsTest | src/testに復帰。公開HTTP拒否・全角ポート正規化など現仕様へ更新 | 旧期待値を復活させない |
| LamiAnimationStatusMapperTest | src/testに復帰。thinking等追加 | 旧期待値を復活させない |
| RetrofitClientTest | 削除。RetrofitClientBaseUrlInitializationTestが空・有効active・全不正を検証 | 旧localhost自動補完は現仕様に反する。混在URLとactive不在の残り契約を必要に応じて追加 |
| SpriteFrameRepositoryTest | 削除。toFrameMaps/toFrameYOffsetPxMapの直接テストを確認できない | 現行座標APIでオフセット変換の回帰確認 |
| InsertionAnimationSettingsTest | 削除。Androidテストには設定値・復元の検証があるが、挿入判定境界の直接検証は確認できない | 確率0/100/境界、周期、クールダウン、空/無効パターンを現行APIで検証 |
| SettingsTest | 削除。SettingsServerEmptyStateTestは空サーバーの検証 | 非空サーバーのactive限定検証・保存後active更新順序を追加 |
| OllamaViewModelTest | 削除。ConnectionFailureTestに選択保持・provider別保持・遅延応答排除あり | 単一/複数モデル・保存選択が消えた場合の契約を現仕様で補完 |

旧ヘッダーの62dp上限と上余白2dpの変更はテスト復旧と無関係であり採用しない。
現在のヘッダーは独立部品・clip=false等に変わっている。
優先はアニメーション判定の回帰テスト、次に接続設定とモデル選択。既存テストの機械的な全復活は行わない。

## #2446: 実行して確認した不具合

mainの `MarkdownCodeRepair.kt` をキャッシュ済みKotlin 2.0.21コンパイラで単独コンパイルし、
Javaから実際の `MarkdownCodeRepair.INSTANCE.repair` を呼び出した。結果のPythonコードを `ast.parse` で構文検証した。
アプリ全体のテスト成功を示す検証ではない。結果は `2026-09-12-markdown-repair-probes.json` を参照。

- 正常なトップレベルfor: 4/8空白を8/12へ変更。Pythonとしては通るが正常入力不変を満たさない。
- 正常な関数内while/for: `def main():` 直下のwhileが左詰めになり、`expected an indented block` が発生。
- 左詰めされたfor代表例: 構文は通るが旧PR期待の4/8と異なる8/12を生成。
- 左詰めされたwhile代表例: 現在でも4/8/12へ修復できる。この成功例を壊さないこと。

関数内whileの破損は親相対for修正だけで直ると断定できない。
修復パイプライン全体で既に正しいネストを維持する必要がある。
`MarkdownStreamingMode.DEFAULT` は `LAMI_RECOVERY_V1` であり、legacyという名前だけで不要扱いできない。現行の `buildFinalizedStreamingResponseForPersist`（10671–10687行）から、EDGE_GALLERY_COMPAT以外の最終保存時に実際に呼び出される。
今回アプリコードは変更していない。

完了条件:
1. 正常入力（トップレベル・関数内・複数ネスト）は不変。
2. 修復対象の代表的破損コードは期待した親相対インデントになる。
3. fence外および対象外言語を変更しない。
4. 修復後の再修復が安定し、既存のMarkdown回帰テストを維持する。

## #2267: 計測の単位と観測点

現行 `buildLamiPerceivedTokensPerSecondText` は `outputTokens / totalDurationMs`、
通常の生成速度はTTFTを除いた時間を使う。旧PRのUI更新回数への置換は採用しない。
LaunchedEffectの実行はユーザーに文字が描画された証拠ではなく、共有remember状態のリセット・
最終化のタイミングにも依存する。受信チャンク数、UI状態更新数、トークン数は別の量である。
将来UI負荷を計測する場合は更新/sとして独立したDEV診断にする。
現行DEVラベルの旧式文字列は実際の計算式と整合させる小修正が必要。

## CPU追加調査

過去のEXCESSIVE CPU USAGEの原因は未確定。
最初の追加スレッド採取はprocess/main-threadのIDが同じため辞書で上書きされた。
その数値はmain threadのみでありプロセス全体のCPU使用率として採用しない。
また途中で画面を操作した背景区間は対照試験から除外し、プロセスstatのみの読み取りと
20秒ごとの前面Activity確認を用いた5分区間で再測定する。

有効な5分背景測定: 301.287秒、プロセスCPU時間1.81秒、1コア基準0.601%。15回の前面Activity確認は全てChatGPT。推論なし、アプリPIDは13718のまま。今回の条件では過去の約6.9%背景負荷を再現しなかった。

### アニメーション有効・無効・再有効の比較

全て同一プロセス、同じチャット画面、推論なし。画面復帰から20秒待って40秒ずつ採取。
設定画面の「キャラクターアニメーション」が有効であることをUIで確認し、無効化を読み戻し確認。
測定後に有効へ戻したことを読み戻し確認し、同じ画面で再測定した。最後にChatGPTへ戻した。
APKは以前インストール済みの `a1b71691` のまま。今回アプリの再インストールやコード変更はない。

| 条件 | 実測時間 | CPU時間 | CPU使用率（1コア基準） |
|---|---:|---:|---:|
| foreground_original_setting_after_20s_settle | 40.065s | 19.53s | 48.746% |
| foreground_animation_off | 40.085s | 0.33s | 0.823% |
| foreground_animation_on_restored | 40.084s | 19.53s | 48.723% |

有効→無効→有効で負荷が再現し、前面表示中の高負荷はキャラクターアニメーション経路と強く関連する。
約48.7%から0.82%へ低下し、元に戻すと48.7%へ復帰した。ただし特定関数の寄与を示すスタックプロファイルではない。
過去の5分背景CPU超過の原因確定・解消を意味しない。今回の5分背景測定は0.60%だった。
有効な生データは `2026-09-12-cpu-followup-samples.json` に保存した。

次の修正候補は `LamiStatusSprite.kt` の同期フレーム更新とタイムライン計算、および非同期再生ループ。
同期待ちでは毎画面フレームでsyncTimeMsを書き換え、表示フレームが変わらない場合も計算する。
表示フレームの必要な更新頻度に抑え、非表示時の停止・再表示時の再開を明示する方向で検証する。
どちらのループが主因か、実際のアニメ設定とプロファイルを結び付けるまでは断定しない。
空/無効挿入パターンのcontinueや0ms間隔も、#680の回帰テスト再整備と合わせて検証する。

## 実施範囲と読み戻し確認

5件のPRをクローズし、2件はDraftのまま必要作業と完了条件を追記。全7件のhead SHAを変更前後で照合した。
旧PRブランチは削除していない。元の開発ディレクトリはmain基準のclean状態を維持。
この追記は調査報告であり、CPU/Markdown不具合の製品修正を含まない。
