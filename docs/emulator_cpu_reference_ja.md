# 実機なしの任意入力推論: Android → PC CPU reference

2026-10-15。NPU命令やQualcommドライバのエミュレーションではなく、実際にモデルを実行する開発用バックエンドの第一段階。

## 動作
専用debug Activity `io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity`。
入力・モデル名・生成・停止を備える。エミュレータから `http://10.0.2.2:11434/api/chat` の既存Ollamaへ接続し、`num_gpu=0`、`keep_alive=0`、最大64出力トークンでCPU実推論する。ストリーム終端と非空応答がそろった場合だけ成功。通信エラーは失敗として記録し、記録再生への自動置換はしない。

モデル名は変更可能。既定の `sam860/LFM2:350m` は軽い接続検証用であり、スマホNPU用Gemmaモデルと同一ではない。PCに保存されている `gemma4:e4b` も指定可能だが、これもSM8750用E2Bと同一モデル・同一量子化ではない。NPU数値一致は未検証。

## ビルドと起動
このworktreeのルートで実行する。Java21と `/home/sato/Android/Sdk` を使用。

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH ANDROID_HOME=/home/sato/Android/Sdk ANDROID_SDK_ROOT=/home/sato/Android/Sdk ./gradlew :app:assembleStandardDebug -Plami.allowMissingQairt244Jni=true --offline --no-daemon --max-workers=2
/home/sato/Android/Sdk/emulator/emulator -avd LAMI_API_35 -port 5580 -read-only -no-window -no-audio -no-snapshot -gpu swiftshader_indirect
```

エミュレータ起動コマンドは別ターミナルで実行。既存AVDのデータへ保存しない一時起動。OllamaとモデルはPCに導入済みであること。実行前に同じポートのエミュレータがないことを確認する。

```sh
/home/sato/Android/Sdk/platform-tools/adb -s emulator-5580 install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk
/home/sato/Android/Sdk/platform-tools/adb -s emulator-5580 shell am start -n io.github.ninbyo02.lami.assistantprobe/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity
python3 scripts/voice/check_emulator_cpu_reference.py --serial emulator-5580 --output /tmp/emulator-cpu-result.json
```

`--model` と複数の `--prompt` で入力を指定可能。ランナーは対象serialがemulatorであることを確認する。スマホにはインストールしない。実行ごとのUUIDと入力SHA256で古い結果の誤認を防ぐ。端末内結果は `files/emulator_cpu_reference.json`。PCへ結果JSONを保存する。

## 開発の到達範囲
- 実機なしでAndroid画面→任意入力の実推論→ストリーム表示を動かす。
- 成功・通信失敗・停止を開発する。
- 通常チャット画面のサーバー経路は既存。今回の専用画面はその設定やスマホのLOCAL/NPU設定を変更しない。
- ローカルNPUスロットへの代替バックエンド注入、同一モデルのCPU/NPU差分比較、TTS/ASRとの統合はこの画面では未実装。
- NPU専用コンパイル済みモデルの実行、ドライバ、性能・発熱・メモリ競合、実際のSIGABRT原因の検証には実機が必要。

## 参照
- https://developer.android.com/studio/run/emulator-networking-address
- https://github.com/ollama/ollama/blob/main/docs/api.md

このブランチのstandardDebugは既存設定によりapplicationIdが `io.github.ninbyo02.lami.assistantprobe`。本体とは別パッケージである。ランナーの既定値もこれに合わせている。別設定なら `--package` を指定する。

## 実機を使わない実測結果
Android x86_64エミュレータ `emulator-5580` にビルド・インストールして確認。
- 軽量モデルで別入力2件が成功。17＋25→42（962ms、8チャンク）、色2つ→red and blue（657ms、7チャンク）。
- Gemma4 E4Bで日本語入力→「こんにちは」（4235ms）。Ollama `/api/ps` の観測4回で `size_vram=0`。
- 存在しないモデルはHTTP404で明示的に失敗。合成応答を返さない。
- UIの停止ボタンで中断を確認。中断後の別要求も成功（810ms）。
- 実機serialを渡すとランナーが実行前に拒否することを確認。
- 試験後のOllama常駐モデル一覧は空。スマホへのインストール・推論は行っていない。

結果は `docs/reviews/emulator-cpu-reference-20261015/`。今回の数値はPC CPUとエミュレータの経路の観測であり、SM8750 NPUの性能予測ではない。
