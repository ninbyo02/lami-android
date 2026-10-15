# 子プロセス異常終了後の記録確認
2026-10-15。

LamiTraceExitProbeは、実際のLamiSpeechTraceCaptureを使って要求開始とsynthesis_started（25ms）を保存し、readyファイルを作って待機する。
check_trace_process_exit.pyは自分が起動したJVM子プロセスだけへSIGKILL/SIGABRTを送り、終了コード、trace.json未生成、JSONLの最後の有効段階を確認する。
作業中アプリや常駐ジョブを検索して停止する処理はない。JVMのコアダンプ生成は無効、エラーファイルは一時ディレクトリへ限定する。
プローブが失敗・タイムアウトした場合も、自分が起動した子プロセスだけを後処理する。

一括のrun_npu_host_suite.pyで実行可能。
単体:
python3 scripts/voice/run_speech_pipeline_host_tests.py --process-exit-probe --output /tmp/trace-exit-results.json

判定は結果JSON内process_exit_probeを参照する。既存JVM/Pythonテスト件数にこの2ケースを混ぜず、プロセス終了テストとして別記する。

この検証が示すのはLinuxホスト上で異常終了前にファイルを閉じた段階記録を読み戻せること。
NPUをロードしておらず、Android/LAMIのSIGABRT原因を再現・解消した証拠ではない。
端末電源断・ディスク故障・tombstoneとの時刻照合は対象外。
