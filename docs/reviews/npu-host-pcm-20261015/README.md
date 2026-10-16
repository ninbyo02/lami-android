# 保存PCMを使った発話キュー検証
2026-10-15。

保存済み「こんにちは。」のCPU版/NPU CP版WAVについて、生成レポートのSHA256・自然EOS・サンプル数・音声形式を確認し、PCM16 fixtureへ変換した。
各mono24kHz・36480samples・1.52秒。CPU/NPUの音声が同じという意味ではなく、それぞれの保存ファイルが記録と一致することを確認した。

PCMファイルは各72960bytes。manifest.jsonに元WAV・生成レポート・PCMのSHA256を記録。pcm.tsvの参照は同じフォルダ内の相対パス。
LamiSpeechPipelinePcmReplayTestはfixtureを読み込み、ハッシュ・サイズを検査してから、実際のアプリLamiSpeechPipelineへByteArrayとして渡す。
- キューに渡したPCMが順序どおり同じハッシュで届く。
- 仮想再生中の停止で先読みクリップを破棄し、後で再利用できる。
音声の長さに相当する時間を仮想時計で進める。実際のスピーカー出力は行っていない。

既存・時間再現・PCMを含むJVM13テストが成功。
Pythonのfixture検証は正常・サンプル数不一致・EOS未到達・WAV改変を確認し成功。
通常のGradle実行ではPCM fixture未指定のテストをスキップする。

再実行:
python3 scripts/voice/run_speech_pipeline_host_tests.py --replay-fixture docs/reviews/npu-host-replay-20261015/codec.tsv --pcm-fixture docs/reviews/npu-host-pcm-20261015/pcm.tsv --output /tmp/lami-pcm-replay-results.json

## 残る範囲
今回のPCMは過去の生成結果。NPU推論・JNI/SIGABRT・AudioTrackの再現ではない。
コード生成時間fixtureとこのPCM fixtureは別の記録であり、両者を同じ要求の一連の実測として結合しない。
Android診断用の収集・再生接続は未実装。まず完全なトレース契約を追加し、同一要求の入力・PCM・時間・ランタイムハッシュを揃える必要がある。
