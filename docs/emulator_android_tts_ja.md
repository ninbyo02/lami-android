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
SDL_AUDIODRIVER=pulseaudio SDL_AUDIO_DRIVER=pulseaudio scrcpy -s emulator-5580 --audio-codec=raw --window-title="LAMI Android"
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

## PCで聞こえない場合の追加確認（2026-10-17）

ユーザーから実際には聞こえないとの報告があり、TTS完了や非ゼロPCMだけでは可聴性を保証できないことを再確認した。
PC側のscrcpyはミュートなし・既知のアナログスピーカー出力先だったが、Androidのメディア音量は5/15だった。
scrcpyの音声バックエンドをPulseAudioに固定し、エミュレータの音量ボタンでメディア音量を10/15に変更した。PC全体の音量は変更していない。
この環境では `cmd media_session volume --set` は音量に反映されず、キー操作後の `--get` で10/15を確認した。
同じ日本語応答のAndroid音声ピークは約−35dBFSから−17dBFSに増加。最終テストではPCスピーカー出力のモニター信号も測定した。
物理スピーカーからの可聴性はユーザー確認待ち。音声合成・転送・デジタル出力信号の成功と区別する。

## Google TTSへ切り替え（2026-10-17）

eSpeakのロボット的な声を実機の声へ近づけるため、Google Play対応の別AVD `LAMI_GoogleTTS_API36_1` を作成した。
現在PCで表示しているLAMIは **emulator-5582 / API36.1**。元のemulator-5580は保全した。
Google TTS、日本語音声パック約22MB、Voice Iを使用。音量12/15。
実機とエミュレータには話速・ピッチの保存上書きがなく、LAMIソースの既定値0.92/1.18が一致している。
Google TTSのバージョンは実機210683481・エミュレータ210582126で異なり、声の完全一致は未確認。

通常AOSP環境へのGoogle TTS APK単体インストールでは、日本語パックがダウンロード中から進まず、Google Play不足のログが出た。
Google Play対応イメージでは音声パック導入が完了し、選択音声のnotInstalledフラグも消えた。
通常画面で「こんにちは、私はラミです。」を生成し、Google TTSの全2文節が完了した。
結果は `docs/reviews/emulator-google-tts-20261017/`。Google APK・音声パックはリポジトリへ含めない。

Google TTSはLANG_AVAILABLE=0を返すため、検証はAndroidの有効値0/1/2を受け付けるよう修正した。
新規インストール直後は前回推論結果JSONがないこともあるので、ランナーは最初の新しい結果を待つ。
最初の結果欠落エラーと修正後の成功を両方保存した。

再確認コマンド:
```sh
python3 scripts/voice/check_emulator_android_tts.py --serial emulator-5582 --output /tmp/lami-google-tts.json
```
