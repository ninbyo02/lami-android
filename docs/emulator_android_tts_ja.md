# エミュレータのAndroid TTS

LAMIの既存 `AndroidTtsController` を使い、回答をAndroid標準TextToSpeech APIで読み上げる。
エミュレータdebug版では、準備済みニューラル音声モデルの有無にかかわらずAndroid TTSを明示選択できる。

## 設定
エミュレータへAndroid TTSエンジンをインストールし、OSの「テキスト読み上げの出力」で選ぶ。
2026-10-17はF-Droid配布のeSpeak 1.52.0（x86_64）を導入し、日本語の初期化・WAV生成を確認した。
音声品質はLAMIの可愛いニューラル音声とは異なる。

```sh
adb -s emulator-5580 shell am start -n io.github.ninbyo02.lami.assistantprobe/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity --ez enable_main_chat true --es model gemma4-e2b-litert-cpu --ei host_port 11436 --ez android_tts true
```

開発画面の「Android TTSで通常チャット」ボタンでも選択できる。
この操作はエミュレータ内の読み上げを有効にし、通常チャットを再起動する。会話履歴は消去しない。
`--ez android_tts false` で明示選択を解除する（読み上げ自体の無効化は通常設定から行う）。
選択用の引数・設定・工場クラスの分岐はdebugソースだけにあり、かつx86エミュレータ判定を通る場合だけ作用する。

## PC画面・スピーカー
現在のエミュレータは `-no-window -no-audio` で起動済みのため、scrcpyを使って画面とAndroid音声をPCへ転送する。
Android TTSの音声が転送される構成であり、PCの別TTSで回答を作り直す方式ではない。

```sh
scrcpy -s emulator-5580 --audio-codec=raw --window-title="LAMI Android"
```

PCのログインセッションから起動する。遠隔シェルではそのセッションのDISPLAY・XAUTHORITY・XDG_RUNTIME_DIR等を指定する。
実機へのインストール・設定変更は不要。マイク入力やASRはこの変更の対象外。

配布元: https://f-droid.org/en/packages/com.reecedunn.espeak/
scrcpy: https://github.com/Genymobile/scrcpy

## 実測結果

`docs/reviews/emulator-android-tts-20261017/` に保存。
- エミュレータ用debug APKをビルド・更新。ManifestのTTSエンジン検出宣言を含む。
- 通常画面で日本語自己紹介を要求し、「こんにちは、私はラミです。」と応答。
- Android TTSの日本語初期化、2文節の受付・開始・完了を同一の新規要求で確認。
- scrcpyのAndroid音声出力をPCMとして記録し、非ゼロの信号を確認。聴感品質・実際のスピーカーからの可聴性は自動判定しない。
- AndroidTtsControllerの停止・遅延通知等の6テスト、Pythonの既存35テストが成功。
- 最初の検証はログローテーションによって失敗。失敗記録を残し、端末時刻以降のTTSイベントを照合するようランナーを修正して再実行した。

再実行:
```sh
python3 scripts/voice/check_emulator_android_tts.py --serial emulator-5580 --output /tmp/lami-android-tts.json
```

このランナーはエミュレータだけに作用し、新しい会話を作成してAndroid TTSを有効化する。
PC音声転送の検証は別途scrcpyを起動して行う。端末・PCの音量と出力先に依存する。
