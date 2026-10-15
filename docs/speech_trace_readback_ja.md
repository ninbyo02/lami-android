# 収集トレースのPC読み戻し
2026-10-15。

validate_speech_trace.pyを追加。schema1、要求ID、入力/モデル/ランタイムのハッシュ形式、段階順序、非負の単調時刻、成功/失敗の整合性を確認する。
成功ではPCM-readyイベント、固定output.f32参照、Float32LE/24kHz、4MiB以下、サンプル数、SHA256、NaN/Infがないことを確認。
失敗は失敗段階を記録しPCMを持たない。不完全・不正記録は合格にしない。

実行:
python3 scripts/voice/validate_speech_trace.py /path/to/capture/trace.json --output /tmp/trace-readback.json

一括スイートも更新。Kotlin収集部品の成功テストが新規一時ディレクトリへ実際に出力したJSON/PCMを、Python検証器で読み戻す。
この検証は固定の小さなPCMとダミーのモデル/ランタイムハッシュを用いた合成ホストテストであり、実モデル・実機のトレースではない。
元の要求ID・2サンプルPCM・経過70msを読み戻し、PCM整合性を確認した。

JVM22件、Python8件、Kotlin→Python読み戻し成功。
成功、失敗段階、PCM改変、時刻逆転の検査を確認。
一括結果: docs/reviews/npu-host-trace-readback-20261015.json。

制約:
ハッシュ形式の検査と記録内PCMの整合性確認であり、実ロードしたモデル・ドライバとの対応は別途照合が必要。
スピーカーへ音声を出さず、NPU・JNI・SIGABRTを再現しない。
全文の入力は保存していないため、モデルに同じ要求を再入力する際には元の入力を別に用意する。
次はこの収集形式の時間とPCMを同一要求として発話キューへ接続する。
