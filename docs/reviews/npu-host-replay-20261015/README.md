# 実機ログの時間情報によるホスト再検証
2026-10-15。第3段階の部分実装。

記録済みAndroid実機診断からcodec段階の4件の所要時間を抽出し、実際のLamiSpeechPipeline.ktをJVMでコンパイルして仮想時計上で処理する。4件はprefix_cold/warmの各2要求。全文発話開始の記録がないため、発話全体の時間を推定しない。
fixtureはmanifest.jsonに元ファイルSHA256と制約を保存、codec.tsvにID・時間・フレーム数を保存する。

新規3シナリオ:
- 記録された遅延で4要求を順に処理。
- 記録された遅延の途中に停止し、古い要求を破棄して次要求を処理。
- 記録された遅延の終わりに失敗を注入し、次要求を処理。

既存8件を含めJVM11テスト成功。抽出器の正常・欠落・重複・不正時間の4テスト成功。
再生対象は時間と要求IDのみ。PCMは仮のクリップIDであり音声再生ではない。NPU/JNI/SIGABRT、Android AudioTrack、実際のハードウェア性能は未検証。
通常のGradleテストではfixture未指定の再生テストをスキップする。専用ホストランナーは明示されたfixtureを使用する。

実行:
python3 scripts/voice/prepare_speech_replay_fixture.py docs/reviews/2026-10-03-neural-tts-prefix-decode-device.json --output-dir /tmp/lami-replay
python3 scripts/voice/run_speech_pipeline_host_tests.py --replay-fixture /tmp/lami-replay/codec.tsv --output /tmp/lami-replay/results.json

残作業:
- 入力・出力PCM・モデル/ランタイムハッシュ・要求開始/終了・失敗段階を備えた完全なトレース収集契約。
- その契約に基づくPCM参照のハッシュ確認と再生。
- Android側の診断専用接続。現在のアプリ音声経路は変更していない。
