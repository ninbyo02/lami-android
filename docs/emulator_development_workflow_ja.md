# LAMIの実機なし開発環境

PCデスクトップ／アプリ一覧の **LAMI Emulator Development** から起動できる。
Google日本語TTSを備えたAndroidエミュレータ、PC CPU推論、画面と音声の転送をまとめて準備する。
起動済みのエミュレータ・サーバー・転送画面を再利用する。

## 開く・状態確認
現在のPCの作業リポジトリは `/home/sato/lami-tts-daemon/emulator-tts-integration-20261017`。
このディレクトリから:

```sh
python3 scripts/voice/start_emulator_development.py start --install-shortcut
python3 scripts/voice/start_emulator_development.py status
```

- AVD: `LAMI_GoogleTTS_API36_1`、serial: `emulator-5582`、Android API36.1 x86_64。
- アプリ: `io.github.ninbyo02.lami.assistantprobe`（エミュレータ用debug版）。
- 推論: `127.0.0.1:11436`、汎用Gemma4 E2BをPC CPUで実行。Androidからは `10.0.2.2:11436`。
- 読み上げ: Google TTS、日本語パック導入済み、Voice I、話速0.92・ピッチ1.18。
- メディア音量: 現在12/15。必要ならエミュレータ側の音量を調整する。
- 画面／音声: scrcpy、PC音声はPulseAudio経路。

起動コマンドは通常チャットを開き直してCPU推論とAndroid TTSを有効にする。保存済み会話は消去しない。
画面を閉じてもエミュレータと共有CPUブリッジは稼働し続ける。もう一度起動すれば画面を開き直せる。
PC再起動後はログインしてから起動する。OSログイン時の自動起動は設定していない。

ログと直近の状態JSONは `~/.local/state/lami-emulator-development/`。
モデル・Python環境・SDK・scrcpyのパスは起動コマンドの引数で変更可能（`--help`）。
初期インストール済みAPK・AVD・音声データがない場合は、先に既存のビルド／Google TTS準備手順を実施する。
このコマンドはパッケージやモデルをインターネットから自動取得しない。

## 会話と読み上げの確認
```sh
python3 scripts/voice/check_emulator_android_tts.py --serial emulator-5582 --output /tmp/lami-tts-check.json
python3 scripts/voice/run_phone_free_development.py --serial emulator-5582 --output-dir /tmp/lami-development-check
```

最初は通常画面の日本語回答→Google TTSの再生完了を確認する。
次は日本語6ターンのCPU回答、通常画面の履歴・生成停止・停止後の復帰・Room保存・再起動を検証する。
両方とも新しい会話を追加し、対象debugアプリだけを再起動する。結果JSONは成功・失敗を明示する。
自動検証で確認するTTS完了と、スピーカーからの実際の可聴性・声質は区別する。

## 開発したAPKを反映する
リポジトリの変更後、エミュレータ向けのdebug APKをビルドする。

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/sato/Android/Sdk ./gradlew :app:assembleStandardDebug -Plami.standardNpuRuntimeEnabled=false -Plami.allowMissingQairt244Jni=true -Plami.standardGpuOpenClEnabled=false --offline --no-daemon --max-workers=2
/home/sato/Android/Sdk/platform-tools/adb -s emulator-5582 install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk
python3 scripts/voice/start_emulator_development.py start
```

`standardNpuRuntimeEnabled=true` のAPKは別パッケージになるため、この環境用には使わない。
`install -r` で会話・設定を保持して更新する。データ消去やAVD再作成は通常の開発手順に含めない。
実機NPUの動作・速度・消費電力・音声同時処理の保証には、別の実機検証が必要。

関連: `emulator_android_tts_ja.md`、`phone_free_development_ja.md`。

## 検証した起動動作
2026-10-17: 状態確認、連続2回の起動（同一プロセス再利用）、閉じた転送画面の再作成、AVD停止後の起動とTTS設定保持を確認した。
PC全体の再起動は実施していない。共有のCPUブリッジを停止せずに再利用しているため、ブリッジの未起動時の自動起動分岐は今回の実測対象外。
起動・状態確認は環境の準備状況を判定する。会話・モデル品質・可聴性を保証する判定ではない。

最終一括検証も合格: CPU日本語6ターン全問正答、通常画面の履歴・停止・復帰・Room保存・再起動が成功。Python 38テスト成功。結果は `docs/reviews/emulator-tts-integration-20261017/`。

`--install-shortcut` は現在のチェックアウトの絶対パスからデスクトップとアプリ一覧のランチャーを生成する。リポジトリを移動した場合は、その場所で再実行する。
