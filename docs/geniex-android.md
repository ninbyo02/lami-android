# GenieX対応のAndroid統合版

GenieXは、LAMIのローカルテキスト会話でLFM3-1.7Bを生成するバックエンドです。「GenieX ローカル推論」でCPU・GPU・NPU・Hybridを選べます。初期値はオフです。読み上げにはLAMIの設定で選んだTTSエンジンを使います。

## ビルド

Android SDK 36、JDK 21、Python 3.11以降を用意します。先にSupertonicのストリーミング用AARを作成し、既存の統合GPU/NPUランタイムのarm64用ファイルを用意します。 [Supertonic AARの作成手順](tts-supertonic-pcm-streaming.md#nativeライブラリの再作成) · [統合GPU/NPUランタイム](standard_gpu_opencl_runtime.md)

```bash
python3 scripts/build_tts_integrated.py --geniex \
  --native-dir /path/to/verified/combined-runtime/arm64-v8a
```

出力は次の2本です。標準のDebugビルドでは、両方のAPKを同じローカルDebug証明書で署名します。コンパニオンとの接続には同じ署名が必要です。

| APK | パス |
| --- | --- |
| LAMI統合版 | `app/build/outputs/apk/standard/debug/app-standard-debug.apk` |
| GenieXコンパニオン | `geniex-engine/build/outputs/apk/debug/geniex-engine-debug.apk` |

ビルドスクリプトは、LAMI APKの既存GPU/NPUライブラリ14本とTTSランタイムを検査します。コンパニオンAPKではGenieX Android SDK 0.10.0のarm64ライブラリ52本をSDKのAARと照合します。GenieXはコンパニオンの`:geniex`プロセスで実行されるため、LAMI本体と別々のQAIRTライブラリを読み込めます。

## インストールとモデル取り込み

`SERIAL`を`adb devices`に表示された対象端末のシリアルに置き換えて、両方をインストールします。

```bash
adb -s SERIAL install -r geniex-engine/build/outputs/apk/debug/geniex-engine-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk
```

1. 公式の`LFM3-1.7B-QAD-Q4_0.gguf`を端末から選べる場所に置きます。ファイルサイズは1,049,615,680バイトです。 [Liquid AIのモデル配布元](https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF)
2. LAMIの設定で「GenieX ローカル推論」から「GenieXモデルの取り込み」を開き、コンパニオンでGGUFを選びます。取り込み時にSHA-256を照合します。
3. LAMIの設定に戻り、希望する実行先を選びます。ローカルのテキストチャットを送信すると、応答が順次表示されます。
