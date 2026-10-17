# エミュレータのAndroid TTS

通常チャットは既存のAndroidTtsControllerを使い、回答をAndroid標準TextToSpeech APIへ渡す。
Android 11以降のTTSエンジン検出に必要な `android.intent.action.TTS_SERVICE` のqueries宣言を追加した。
エミュレータdebug版では、準備済みニューラル音声がある場合もAndroid TTSを明示選択できる。
この選択はdebugソース内のx86エミュレータ判定で限定され、実機・releaseの音声選択には作用しない。

## 常用環境
Google Play対応AVD `LAMI_GoogleTTS_API36_1`（API36.1、emulator-5582）を使用する。
Google TTSと日本語音声パック約22MBを導入し、Voice Iを選択済み。
ユーザーがPCスピーカーでの可聴性・声質を確認した。音量は12/15。
LAMIの話速0.92・ピッチ1.18は実機と同じ既定値。
実機とエミュレータのGoogle TTSのバージョンは異なるため、音声の完全一致は保証しない。
Google APK・音声モデルはリポジトリに含めない。

OSイメージの設定・音声パック導入は初回に必要。通常AOSPイメージでのeSpeakはロボット的な声になり、
Google TTS APK単体の追加では日本語パックのダウンロードが進まなかったため、常用にはGoogle Play対応イメージを使う。

## 有効化
```sh
adb -s emulator-5582 shell am start -n io.github.ninbyo02.lami.assistantprobe/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity --ez enable_main_chat true --es model gemma4-e2b-litert-cpu --ei host_port 11436 --ez android_tts true
```

開発画面の「Android TTSで通常チャット」ボタンでも選択できる。
`--ez android_tts false` は明示選択の解除。読み上げそのものの無効化は通常設定から行う。
通常画面を開き直すが、会話履歴は消去しない。

## PC画面と音声
音声なし・画面なしで起動したエミュレータも、scrcpyでAndroidの画面と音声をPCへ転送できる。
PCの別TTSで回答を作り直す方式ではない。

```sh
SDL_AUDIODRIVER=pulseaudio SDL_AUDIO_DRIVER=pulseaudio scrcpy -s emulator-5582 --audio-codec=raw
```

PCのログインセッションから起動する。遠隔シェルではDISPLAY等のセッション環境が必要。
エミュレータの音量5/15では非常に小さかった。音量ボタン操作で12/15に設定している。
この環境では `cmd media_session volume --set` が反映されなかったため、必ず `--get` または画面で実際の音量を確認する。
PC全体の音量はこの起動ツールから変更しない。

## 再確認
```sh
python3 scripts/voice/check_emulator_android_tts.py --serial emulator-5582 --output /tmp/lami-tts.json
```

新規会話で日本語の自己紹介を生成し、要求ID・入力SHA256を照合して、Android TTSの日本語初期化と各文節の再生完了を確認する。
Google TTSは言語利用可=0を返す場合があるため、Androidの有効値0/1/2を受け付ける。
結果JSONがまだない初回起動も、新規の結果を待つ。
TTS完了・デジタル音声信号の存在は、物理スピーカーの可聴性・声質とは別の判定である。

起動・APK更新は `emulator_development_workflow_ja.md`、統合時の結果は `docs/reviews/emulator-tts-integration-20261017/`。
