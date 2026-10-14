# LAMI 実機なし検証の実行
2026-10-15。専用ブランチ experiment/npu-host-validation-20261015。

## 1コマンド
PC上で以下を実行:
python3 /home/sato/lami-tts-daemon/npu-host-validation-20261015/scripts/voice/run_npu_host_suite.py --output /home/sato/lami-tts-daemon/npu-host-suite-latest.json

必要環境: Python3、Java、既存Gradleキャッシュ内のKotlin2.3.0/reflect1.6.10/coroutines1.9.0/JUnit4.13.2/Hamcrest1.3/annotations23.0.0。
Android SDK・スマホ・新規ネットワーク通信は不要。ライブラリをダウンロードせず、欠落は失敗として記録する。

## 実行する内容
- 事前検査器の正常・不正ファイル処理テスト（現在のモデルを再推論するものではない）。
- 記録したcodec段階の時間fixture抽出器の検証。
- 保存WAVの同一性・PCM形式・改変検出の検証。
- 本番LamiSpeechPipelineを直接コンパイルし、停止・置換・遅延・失敗・記録時間・実PCM転送をJVM上で検証。

計Python7テスト、JVM13テスト。すべて成功。
fixture欠落と依存キャッシュ欠落の2ケースは終了コード1かつ結果JSON生成を確認。

## 結果
終了コード0=ホスト検証合格、1=失敗。host_suiteとstepsで判断する。
npu_execution、soc_compile、android_audio、sigabrt_reproductionはnot_run。合格はハードウェアの動作・発熱・音声品質の保証ではない。
時間fixtureとPCMfixtureは異なる過去の記録を使用しており、同一要求の統合実測と扱わない。

## 追加の実測
PCMデコーダのホスト推論は既存check_neural_tts_prefix_decode.pyで別実行する。重いExecuTorch環境を使うため一括の軽量回帰には含めない。
現在のモデルファイル同一性はnpu_host_preflight.pyで対象レポートを明示して検査する。

## 第一段階の到達点と次の課題
実機なしでモデルファイル確認、保存コードのPCM推論、発話キューの回帰を行う入口が完成。
完全なNPUエミュレータは未実装。次の課題はAndroid診断で同一要求の入力、PCM参照、開始/終了、失敗段階、モデル/ランタイムハッシュを収集し、ホスト側で同一トレースを検証すること。
既存アプリの音声経路、端末、常駐開発プロセスには変更を加えていない。現ブランチはローカルコミット済み、未プッシュ・未マージ。
