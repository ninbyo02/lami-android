# 過去の実機時間を使うキュー回帰
2026-10-15。ADB devices確認時点で接続端末なし。新規の実機操作・インストールは行っていない。

10月9日のcp-delta-native実機診断に残る4ログ・8要求を使用。
summaryでall_outputs_match=trueとrestore=restored_and_hash_verifiedを要求し、要求ごとに生成開始・完了・再生開始イベントが各1つあることを検査する。
ログとsummaryのSHA256をmanifest.jsonに保存。生成時間は開始と完了の差、再生開始までの時間は別項目に分離した。

実測値の再抽出:
baselineの生成時間は24360/11072/23448/10282ms、groupedは23019/10247/22516/10330ms。
古いCPU診断結果であり現在の性能やNPU速度を示さない。
timing.tsvの第2列はcodec段階ではなく発話の生成全体の時間。既存の時間注入テストへ渡すため同じ3列形式を使う。

この8件の記録時間を使い、本番LamiSpeechPipelineの要求順序・途中停止・失敗注入と次要求の継続を検証。ホストJVM20テスト成功。
抽出器の正常・開始欠落・イベント重複・時刻逆転のテスト成功。
これはログの時間を仮想時計に設定したキュー検証であり、当時の障害そのものを再現したものではない。

PCMのハッシュ記録はあるが、同一要求の生PCM実体は今回未確認。元フォルダのoriginal-neural_tts_last.wavはバックアップであり、要求のPCMとして結合しなかった。
モデル/ランタイムの実ロード由来も未照合。現行schemaの完全トレースへ不明なハッシュ・PCMを補完していない。

再実行:
python3 scripts/voice/run_speech_pipeline_host_tests.py --replay-fixture docs/reviews/legacy-device-timing-20261015/timing.tsv --output /tmp/legacy-timing-results.json

次に必要なのは端末のADB接続と診断収集の有効化による新規記録。実機不在中も既存の数値比較・ホスト回帰は利用可能。
