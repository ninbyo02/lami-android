# 通常チャット画面：長文・TTS・停止の実機検証

2026-09-12。NX733J、分離パッケージ `io.github.ninbyo02.lami.gpucontrolled`。
対象は前報のOpenCLアクセラレータ置換候補APK（SHA-256 `7c74b1485518ebcc3b94a80234dbd2fa2d11636f7c7faae79bda7ad65bdba70e`）。通常利用パッケージのAPKや会話履歴は変更していない。
実施前の見積もりは20〜30分。ADB IMEで日本語を通常画面の入力欄に入力し、送信・停止・回答再生ボタンを操作した。診断Receiverによる推論ではない。

## 結果

|項目|観測|判定|
|---|---|---|
|GPU長文|600文字を要求、623文字を保存、50,112ms、COMPLETED|生成は成功。逐次表示は未達|
|GPU逐次表示|Connecting中は本文なし。held-official-blocking、partialCount=1、callback streaming=false/not_selected|改善が必要|
|GPU応答品質|日本語の中に「महसूस」「Nothing」が混入|品質課題あり|
|GPU自動TTS|614コードポイントの要求、playback_started、約122秒後playback_done|発話処理の完了を確認|
|GPU再生ボタン|614コードポイントの再要求と開始、Android AudioTrackがSPEECH/started/mutedState:none|再生動作を確認|
|GPU TTS停止|停止後Ready/Send Button、AudioTrack消失|UIと音声サービス状態で停止を確認|
|GPU生成停止|停止後2秒の採取時点でReady/CANCELLED、12秒後も維持|UI・保存状態で成功|
|GPU停止後の再送信|2+3に「5」、10,787ms、COMPLETED|成功|
|NPU長文|500文字を要求、599文字を保存、11,378ms、COMPLETED。NPU実行診断はsuccess/fallback_used=false|生成成功|
|NPU TTS|同一567コードポイントにQUEUE_ADD→QUEUE_FLUSHを約111ms間隔で発行。後者のplayback_startedを確認|二重要求あり。二重に音声を聞いた証拠ではない|
|NPU長文後の短文|3+4でStatus Code 8: Reached maximum number of tokens、max_output_tokens=32。その後ユーザー停止でCANCELLED|連続利用は不合格|
|さらに次のNPU入力|Status Code 3: Input token ids are too long、519 >= 512。後続の別経路で本文が出たが停止しCANCELLED|履歴・コンテキスト上限の問題|
|保存状態|6組12行、未完了PENDING/GENERATINGなし、余分な重複行なし。SQLite quick_check=ok|今回の保存整合性は良好|

## 解釈と制約

- アクセラレータ置換で短文診断だけでなく通常GPU長文生成も成功した。しかし通常経路の逐次表示、品質、NPU連続利用は未解決で、日常用APKへの昇格根拠としては不足。
- GPU streaming関連の2つの端末プロパティは検証後の読み取りで空。実行診断はcallback streaming=false。ソースの標準Debug既定値と実経路が合っていない理由は追加調査が必要。単なるプロパティOFFとは断定できない。
- NPU TTSは逐次側のQUEUE_ADDとphase_owner側のQUEUE_FLUSHが重なる。後者が前者を置き換える可能性があるため、発話の所有者を一つにする必要がある。
- 長文後の失敗は推論例外の実測。出力上限32と入力上限512、履歴再利用の整合を優先して調べる。これだけでモデル全般の上限やネイティブの根本原因を断定しない。
- NPU停止後の再送信シナリオは失敗時に処理が続き、後続の送信予定タップが停止操作になる場合があった。最後の4+5はDBに送信記録がなく、再送信成功として数えない。ネイティブNPU生成中の正確な停止遅延もこの試験では確定していない。
- 音声は発話コールバック・AudioTrack状態で確認。遠隔で音を聴いていないため、音質・聞き取り・冒頭の重複は未評価。
- 停止後のCPU/GPU/NPU使用率や電力は未測定。画面とDBの停止を、ハードウェア負荷ゼロと同一視しない。
- mainアプリの変更、マージ、製品APKの更新は実施していない。検証用アプリを終了し、IMEと画面消灯設定を元に戻し、ChatGPTが前面であることを確認済み。

証拠は同名JSON。生ログ・UI XML・Audio dumpsys・操作スクリプトは開発PCの `/tmp/lami-ui-validation` にある。
