# 終了前の段階記録
2026-10-15。

## 修正理由
終了時のtrace.jsonだけでは、プロセスが生成途中で終了した場合に最後の段階を把握できなかった。
収集の開始時にrequest.propertiesへ要求IDと入力/モデル/ランタイムのハッシュを保存し、開始・生成開始・PCM準備・再生開始をevents.jsonlへ逐次追記する。
各追記でファイルを閉じる。端末電源断時の永続化を保証するfsyncは行っていない。
正常終了時は従来どおりtrace.jsonとPCMを確定し、最後に終了イベントを追記する。

## 読込
validate_speech_trace.pyへtrace.jsonを指定し、そのファイルがなければ隣のevents.jsonlを検査する。
JSONLの末尾が途中で切れていても、最後まで有効だった段階を表示する。
判定は常にvalidation=failed、incomplete_capture.status=incomplete。未完了を成功・SIGABRTと断定しない。
不完全ファイル・未完了収集は8件上限の対象となる。

## 検証
終了しないホスト収集でも生成開始と25msが保存されていることを確認。
途中で切れたJSONLの読み込みでsynthesis_startedまで取得でき、未完了を維持することを確認。
既存の収集→読込→同一要求再投入も回帰実行。ホストJVM27種類・Python9件。Androidコンパイルは結果JSON参照。

## 制約
実際のSIGABRTを発生・再現した検証ではない。SIGABRTの特定にはtombstone/logcatと時刻の照合が必要。
現時点の時計は要求相対時刻であり、プロセスID・壁時計によるtombstone対応付けは今後の課題。
診断用の同期I/Oが加わるため、その状態の時間を通常動作の性能と混同しない。
