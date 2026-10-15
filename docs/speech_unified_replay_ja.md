# 同一要求の時間とPCMを発話キューへ再投入
2026-10-15。

## 実装
prepare_unified_speech_replay.pyはvalidate_speech_trace.pyで検証した成功要求のみを受け付ける。
同じtrace.jsonから要求ID、要求開始からPCM準備までの時間、PCMハッシュ、サンプル数を抽出し、output.f32を無変換でコピーする。入力・モデル・ランタイムのハッシュもmanifestへ記録。
出力先は新規ディレクトリのみ。検証後のコピー時にもPCMハッシュを再検査する。
失敗要求・不完全要求はこの成功PCM再生形式には変換しない。

実行:
python3 scripts/voice/prepare_unified_speech_replay.py /path/to/capture/trace.json --output-dir /tmp/new-unified-replay
python3 scripts/voice/run_speech_pipeline_host_tests.py --unified-fixture /tmp/new-unified-replay/unified.tsv --output /tmp/unified-results.json

## 一括スイートでの確認
Kotlin収集部品が一時保存→PythonでJSONとPCM整合性を検査→同一要求の再生データへ変換→本番LamiSpeechPipelineをJVM上で実行。
合成ホスト記録の50msを仮想時計に設定し、PCMハッシュが一致する内容が50msで消費側へ届くことを確認。
生成待ち途中で停止すればPCMは届かず、その後の再要求で同じPCMを処理できる。
既存22件と新規2件、計24種類のJVMテスト成功。Python8件も成功。
スイートは収集と再投入のためJVMを2回実行し、重複するテストがある。実行件数の単純合計を独立テスト数として扱わない。

## 制約
今回は合成ホスト記録（2サンプル、ダミーモデル/ランタイムハッシュ）。実際のスマホで新たに採取した記録ではない。
消費側はPCMの内容を確認するテスト用関数で、スピーカー出力・AudioTrack・NPU・JNIを再現しない。
準備遅延を使うキュー検証であり、実機性能の予測ではない。新規テストは正のPCM準備時間を持つ成功記録を対象とする。
