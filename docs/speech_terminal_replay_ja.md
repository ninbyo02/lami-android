# 失敗・キャンセル記録の再投入
2026-10-15。

prepare_terminal_speech_replay.pyで検証済みfailure記録の要求ID、終了までの時間、失敗段階、元JSONのSHA256を抽出する。PCMは捏造しない。成功記録と重複要求IDを拒否し、新規ディレクトリへ出力する。

Kotlin収集器のホストテストでload失敗（25ms）・cancelled（0ms）のJSONを実際に保存し、Pythonで検証してから本番LamiSpeechPipelineへ投入する。
- load失敗の時間で準備結果nullを注入し、その要求を再生せず次要求を処理。
- cancelledの時間で外部からstopを注入し、現在要求とキューを破棄。明示的な再要求で処理再開。
キャンセルは通常の失敗後の自動継続と区別する。

実行例:
python3 scripts/voice/prepare_terminal_speech_replay.py /path/failure/trace.json /path/cancelled/trace.json --output-dir /tmp/new-terminal
python3 scripts/voice/run_speech_pipeline_host_tests.py --terminal-fixture /tmp/new-terminal/terminal.tsv --output /tmp/terminal-results.json

一括実行は従来のrun_npu_host_suite.py。既存と新規を合わせJVM26種類、Python8件成功。収集→読込→時間/PCMまたは終端イベント再投入を確認した。
ホスト記録のモデル/ランタイムハッシュとPCMはテスト用であり、スマホ実機の新規記録ではない。NPU/JNIの実際の例外やSIGABRTを再現した結果ではない。failure記録のsynthesis/playback段階は抽出可能だが、今回の終端キューテストはload/cancelledのみを対象にする。

## 次に必要な証拠
基本的なホスト基盤は揃った。以後は合成テスト数を増やすだけでNPU検証の完成度が上がるとは扱わない。
次の段階には同一要求で採取した実機のモデル/ランタイム由来、入力照合情報、時間、PCMまたは失敗段階が必要。現APIへの接続と実機採取を進め、実記録に基づく再投入でホスト基盤を評価する。
それでも発熱・電力・ドライバ異常は実機で確認する。実機不在中はホストモデル変換・数値比較・回帰検証に利用できる。
