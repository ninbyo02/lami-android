# 実機なし検証 第2段階 — 2026-10-15

## 今回実行した検証
保存済み「こんにちは。」のCPU/NPU CP生成コード各19フレームを、同じホストCPU ExecuTorch PCMデコーダで再デコードした。2/4/8/16フレームを全文デコードの先頭と比較し、両経路全8比較で最大絶対差・RMSが0、全サンプル一致。
CPU/NPUのコード列同士・音声同士が同一という意味ではない。

使用した入力・デコーダのSHA256はprovenance.jsonに保存。デコーダは既存記録の05288ee2c763f80067139019ca66915bad150de705acc940200735f653c1b7c5と一致。
全文デコードは各1回、CPUコード1409.54ms、NPUコード1346.63ms。ウォームアップやPC負荷を統制しておらず性能の改善・悪化判定に使わない。

## 実際のアプリ処理のホストテスト
scripts/voice/run_speech_pipeline_host_tests.py は、Android依存のない本番LamiSpeechPipeline.ktを直接Kotlinコンパイラでコンパイルし、JUnit上で実行する。Android SDK、APKビルド、実機は不要。既存GradleキャッシュにKotlin2.3.0、reflect1.6.10、coroutines1.9.0、JUnit4.13.2、Hamcrest1.3、annotations23.0.0が必要。

既存7テストと新規1テスト、計8テスト成功。
- 停止後に遅れて届く生成結果の破棄、後処理完了を待つ再開。
- 再生中の先行生成、停止、連続置換。
- 生成が遅延し失敗をnullで返した後も、次要求と再利用を処理。

再実行:
python3 scripts/voice/run_speech_pipeline_host_tests.py --output /tmp/lami-pipeline-host.json

## 次の接続点
LamiNeuralChatVoiceEngine内のprepareUtteranceでsynthesizeTextを呼び、LamiSpeechPipelineのprepare/play関数へ接続している。ここが記録再生を注入する候補。
現状のトレースには要求IDと相対時間があるが、再生用の入力・PCM参照・モデル/ランタイムハッシュを揃えた契約は未実装。

## 限界
今回NPUを再実行していない。JNI/SIGABRT、Android AudioTrack、発熱、電池、メモリ不足、LLMとのNPU競合は未検証。
部分デコードの一致は当該短文・当該コード列の結果。音切れのないストリーミングや全文章での一致は保証しない。
