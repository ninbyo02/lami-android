# Supertonicの文内PCMストリーミング

Supertonic 3の選択経路は、同じ文の残りの音声を生成している間に、先頭のPCMを再生へ渡します。
完成した全文PCMを後から分割する実装ではありません。元のsherpa-onnx 1.13.6は全文字チャンクのボコーダー処理後にcallbackするため、native実装にも変更を加えています。

## 生成から再生まで

文章全体の発話長推定・text encoder・denoisingは従来と同じです。その後、ボコーダーへ圧縮latentを8フレームずつ渡し、
1回の推論で得たPCMをcallbackしてから次の窓を計算します。1フレームは44.1kHzで3072サンプルです。
過去24フレームを文脈として保持し、その重複部分のPCMを捨てて新しい部分だけを送ります。将来側の文脈とcross-fadeは使用しません。

このモデルのボコーダーは左側だけを参照する畳み込み、時刻ごとのLayerNorm、固定統計のBatchNormで構成されています。
全文のlatentを保持したまま窓ごとのボコーダー処理を行うため、文の途中で文章を切って抑揚を作り直す必要がありません。
短い発話では窓が1つになる場合もあります。先頭PCMまでの時間には、窓の処理に入る前の全文推論時間も含まれます。

JNIの具体callbackクラスから、上限付きチャネルで別workerへ渡し、状態を保つresamplerで44.1kHzから24kHzへ変換します。
再生側は`AudioTrack.MODE_STREAM`を使用します。最初のemitは再生開始通知を待ってからnative callbackへ応答するため、
次のボコーダー処理へ進む前に先頭PCMの再生を開始できます。

窓の過去部分を再計算するため、ボコーダー全体の計算量は増えます。先頭再生の待ち時間と全文の生成時間・RTFは別々に評価してください。
停止では再生を止め、以後のcallback結果を破棄します。実行中のnative呼出しの完了を待ってから解放し、マイクへ移行します。

## native変更の範囲

- 元ソース：sherpa-onnx `v1.13.6`、commit `1cb484af5e69d3c7803c1eb0b3b5ab8041e0e911`。
- [適用パッチ](../patches/sherpa-onnx-1.13.6-lami-causal-vocoder-stream.patch)。元の全文生成は既定値のまま保持。
- `GenerationConfig.extra["lami_vocoder_chunk_frames"] = "8"`で窓生成を有効化。`0`は元の実装。
- `lami_vocoder_left_context = "24"`を指定。24未満は拒否し、対応するSupertonic 3のlatentレイアウトも検査。
- JNIの`LamiSupertonicStreamingRuntime.version()`が`1`を返す場合だけ、この経路を使用。

元のAARは未知のextraを無視します。能力確認を設け、旧native実装で文内ストリーミングを行ったと誤認しないようにしています。
元AARのみでビルドした場合、このSupertonicストリーミングの試聴は明示エラーになり、完成PCMの分割処理へ自動変更しません。

原典：[元の生成処理](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.6/sherpa-onnx/csrc/offline-tts-supertonic-impl.cc)、
[元のボコーダー呼出し](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.6/sherpa-onnx/csrc/offline-tts-supertonic-model.cc)。

## nativeライブラリの再作成

必要なものはPython 3、Git、CMake、Ninja、Android NDK **28.2.13676358**です。
元の`app/libs/sherpa-onnx-1.13.6.aar`を残したまま実行します。

```bash
python3 scripts/build_supertonic_streaming_runtime.py \
  --ndk "$HOME/Android/Sdk/ndk/28.2.13676358" \
  --source-dir artifacts/supertonic-streaming-runtime/source \
  --jobs 12
```

初回は指定commitのソースと、ハッシュを固定したORT 1.27.1の8つのheaderを取得します。
NNAPIのprovider headerも含みます。ORTの`.so`はダウンロードし直さず、元AARの各ABIのファイルをそのまま使います。
元AAR、ソースcommit、適用パッチ以外のtracked変更、headerハッシュを確認してからビルドします。

既定ABIは`arm64-v8a`と`x86_64`です。Android API34・Ninja・Release・shared ORTを使い、JNIとTTSを有効化し、
binary、test、PortAudio、WebSocket、Python、C API、espeak/piperの実行ファイル・テストを無効化します。
ABIごとに順番にビルドし、各ビルドのworker数に`--jobs`を適用します。

既存のビルド結果をパッケージ化する場合は、同じsource directoryで次を使用します。configure、取得、コンパイルは行いません。

```bash
python3 scripts/build_supertonic_streaming_runtime.py \
  --source-dir /home/sato/project/lami-supertonic-streaming-runtime-20261110 \
  --ndk "$HOME/Android/Sdk/ndk/28.2.13676358" \
  --package-only
```

出力は`app/libs/sherpa-onnx-1.13.6-lami-stream.aar`と、隣の`.build.json`です。どちらもローカル生成物としてGit対象外です。
`.build.json`にはソース・パッチ・script・header・元AAR・ORT・新しいJNIのSHA256、ABI、NDK、実際のCMake設定を保存します。
非strip版のビルド結果を保持し、パッケージには`llvm-strip --strip-unneeded`を適用したコピーを使います。

元AARのうち、指定ABIの`libsherpa-onnx-jni.so`だけを置き換えます。Java classes、ORT、C API/C++ API、その他のABIはpayloadのハッシュが同じであることを照合します。
新JNIは独立したshared sherpa coreに依存しません。元AARを上書きせず、能力確認symbolと16KBのELF LOAD alignmentを検査してから出力します。
選択しなかったABIには元の非ストリーミングJNIが残ります。

Gradleは生成済みAARがあればそれを優先します。新AARを生成・更新した後はAPKを再ビルドしてください。
モデル配置は[TTS比較の手順](tts-comparison.md)を使用します。モデル内容・話者・推論回数はこのnative変更では変更しません。

## 検証

ホストのORT 1.31.1で、Light5の`vocoder.int8.onnx`に同一のランダムlatentを入力し、全文処理と窓処理を比較しました。
latent長37・64・113、計657,408サンプルで、過去24フレーム・窓8フレームの最大絶対差は`1.42e-7`以下でした。
過去16フレームでは最大約`0.02`の差が発生したため採用していません。これは窓処理の数値検査で、音質試聴や実機速度測定とは分けて扱います。

```bash
python3 -m unittest scripts/tests/test_build_supertonic_streaming_runtime.py -v
```

`LamiSupertonicPcmStreamTest`は、先頭PCMの応答待ち、callback配列の所有、停止・再生失敗時の待ち合わせとnative完了待ちを検証します。
モデルを配置した端末では`LamiSupertonicStreamingInstrumentedTest`により、1つのnative文字チャンクから複数PCM窓が届くこと、
先頭PCM受信がnative生成完了より前であること、全文生成とサンプル数が一致し最大差`2e-5`以下であることを検査します。
モデル未配置時はこの端末テストをskipしますが、配置済みで能力確認が通らない場合は失敗します。

ビルドした両ABIのハッシュとalignmentは[生成記録](tts-supertonic-pcm-streaming-native.json)に保存しています。
音響開始時刻を計測したものではありません。実機の待ち時間・RTFは、同じ文章・設定・常駐状態で別途測定します。
