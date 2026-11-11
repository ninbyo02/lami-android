# TTSの選択と同文試聴

設定の「読み上げ方式」で、次の3方式を選び、同じ文章を試聴できます。初期設定はAndroid標準TTSです。

| 方式 | 使用する音声・モデル |
| --- | --- |
| Android標準TTS | 端末で設定したAndroidの読み上げエンジン |
| LAMI 可愛いTTS（Qwen3-TTS） | 外部配置した`lami_cute`のQwenモデル |
| Supertonic 3 TTS | 外部配置した学習済みLAMI Light5モデル、sid0・5 steps |

3方式とも、届いたPCMを順次`AudioTrack.MODE_STREAM`へ渡す経路を使用します。
設定画面でQwenを明示選択すると、通常の`standardDebug`でもPCMストリーミングが有効になります。別の試験パッケージや隠し設定は不要です。
選択は保存され、チャットの読み上げにも使われます。選択先のモデルがない場合は利用できない理由を表示し、別のエンジンへ自動変更しません。
試聴する文章を入力して方式を切り替えると、同じ文章で比較できます。

## 各方式のストリーミング

| 方式 | 生成途中のPCMを再生へ渡す仕組み |
| --- | --- |
| Android標準TTS | `synthesizeToFile()`の`onBeginSynthesis`で形式を受け取り、`onAudioAvailable`のPCMを再生workerへ送信 |
| LAMI 可愛いTTS（Qwen3-TTS） | codec生成中の累積prefixをcausal decoderでPCM化し、既に送った部分を検査して新しいPCMだけを再生 |
| Supertonic 3 TTS | 全文latentの推論後、nativeのcausal vocoderを8フレームずつ実行し、次の窓を計算する前にPCMを再生へ送信 |

Androidの一時WAVは合成APIの出力先です。再生元は到着したPCM callbackで、ファイル完成を待って読み直す処理はありません。
PCMの形式・sample rate・channel数はAndroidエンジンから受け取ります。実際に生成完了前から再生できるかは、選択したAndroidエンジンのcallback到着時点にも依存します。

Qwenは、検査済みの`speech-decoder-dynamic-et14.pte`に限定してprefixを送出します。
モデルのSHA256とdecoderの2〜256フレーム・1920 samples/frame・24kHzという契約を検査します。
累積prefix間で、既に再生へ渡したPCMとの差が最大絶対差`1e-5`・RMS`1e-6`の両方を満たすことを確認し、新しく増えた部分だけを送ります。
これは対象decoderの浮動小数点計算に生じる微小差の上限です。上限を超えた場合は処理を失敗させて診断記録を残します。
他のdecoderや汎用の診断経路にこの許容値を適用しません。先頭prefixを保持し、待機中の古いprefixは新しい累積prefixへまとめます。

Supertonicは、sherpa-onnx 1.13.6に追加したnative実装を使用します。全文の発話長推定・text encoder・denoisingを維持し、
causal vocoderへ8フレームと過去24フレームの文脈を渡して、新しい部分のPCMを順次生成します。
44.1kHzから24kHzへの変換はfilterの位相と境界状態を保持します。先頭PCMの再生開始を待ってから、native側が次の窓へ進みます。
短い発話では窓が1つになる場合があります。過去の文脈を再計算するため、先頭再生の早さと全文の計算量・RTFは別々に評価します。

## ビルド

Supertonicの文内PCM生成には、変更したnative JNIを含むAARが必要です。Android NDK 28.2.13676358、CMake、Ninjaを用意し、APKの前に生成します。

```bash
python3 scripts/build_supertonic_streaming_runtime.py \
  --ndk "$HOME/Android/Sdk/ndk/28.2.13676358" \
  --jobs 12

env -u ANDROID_SDK_ROOT ANDROID_HOME="$HOME/Android/Sdk" \
  python3 scripts/build_tts_integrated.py \
    --native-dir /path/to/verified/combined-runtime/arm64-v8a
```

出力は`app/libs/sherpa-onnx-1.13.6-lami-stream.aar`です。Gradleはこの生成物がある場合に優先し、元のORT・Java classesは維持します。
対応ABIは既定で`arm64-v8a`と`x86_64`です。元AARだけの場合はSupertonicのストリーミング能力確認で明示エラーになります。
再作成・既存nativeビルドからのパッケージ化・ハッシュ記録は[Supertonicのnativeビルド手順](tts-supertonic-pcm-streaming.md)を参照してください。

実機の統合版には、[検証済みGPU/NPUランタイム](standard_gpu_opencl_runtime.md)の14ライブラリも必要です。
上のスクリプトは入力全体を検査してから配置し、通常の`standardDebug`とAndroidTest APKを作り、最終APKも照合します。
配置済みなら`--native-dir`を省略でき、`--verify-only`では検査だけを行います。
Java APIもこのランタイムと同じLiteRT-LM 0.11.0を使用します。

`-Plami.allowMissingQairt244Jni=true`は実機NPUを含まない隔離スモーク用です。実機の統合版には使わないでください。
過去の`artifacts/tts-streaming-20261110/`、`qwen-eos-continuity-20261110/`、`tts-priority-fixes-20261110/`のAPKはこのスモーク構成です。
TTSの検証記録として保持しますが、通常チャットのNPU推論に対応する配布物ではありません。

2026-11-10の実機修復では、14ライブラリの入力と完成APKを照合した統合版をNX733Jへ上書きしました。
選択済みNPUモデルによる「こんにちは」と短い算数の2回で、実NPU・native streaming・fallbackなしの生成を確認しています。
正常EOSで最後の安全な表示更新を送る修正も含みます。既存の会話8件・チャット1件・設定は更新前後で一致しました。
関連単体テスト442件中437件成功・既存5件skip、Python検証45件が成功しています。
同じ修正版APKで、エミュレーター上のAndroid・Qwen・Supertonicの3方式の実再生も再確認しました。
Androidエンジンでは合成callbackが再生workerの準備より早く完了する場合があるため、先行再生の有無は観測値として記録します。
APK・実機生成結果・保全確認は[統合版の修復記録](../artifacts/integrated-inference-fix-20261110/validation.json)に保存しています。

## モデルの配置

QwenとSupertonicのモデルはAPKに含まれません。現在の比較用実装はdebugビルドを対象とし、配置にはADBとアプリの`run-as`を使用します。
モデルのダウンロードや音声の送信は行いません。Android標準TTSの利用条件は端末で選択したエンジンに従います。

スクリプトはローカルのハッシュを検査してから、明示した端末・パッケージへ配置します。
まず`--dry-run`で確認してください。この指定ではADBを呼び出さず、インストールも端末の変更も行いません。

```bash
python3 scripts/provision_tts_comparison_models.py \
  --serial SERIAL \
  --qwen-model-dir /home/sato/project/lami-android-voice-dataset/releases/prepared-hai-android-v1 \
  --supertonic-model-dir /home/sato/project/lami-supertonic-voice/clone/deployment/lami-supertonic3-v1/model \
  --dry-run
```

確認後、`--dry-run`を外して配置します。`--apk`は任意で、指定すると先に`adb install -r`を行います。
APKと`--package`が一致することをビルドの`output-metadata.json`で確認してください。
既定パッケージは`io.github.ninbyo02.lami.assistantprobe`です。別のapplication IDでビルドした場合は`--package`を指定します。

```bash
python3 scripts/provision_tts_comparison_models.py \
  --serial SERIAL \
  --package io.github.ninbyo02.lami.assistantprobe \
  --apk app/build/outputs/apk/standard/debug/app-standard-debug.apk \
  --qwen-model-dir /path/to/prepared-qwen-bundle \
  --supertonic-model-dir /path/to/lami-supertonic3-v1/model
```

配置処理は対象アプリを停止してから実行します。モデルディレクトリの指定は片方だけでも使用できます。
ファイルはtarの標準入力でアプリ所有の領域へ直接送り、端末でも全ファイルのSHA256を照合してからディレクトリを切り替えます。
`/sdcard`や端末内の一時tarへの大容量コピーは作りません。既存モデルを保持したまま検査するため、更新時には既存分に加えて新しいモデル分の空き容量が必要です。
配置後にアプリを開き、設定画面の利用可否表示を確認してください。

| 配置先（アプリprivate領域） | 内容 |
| --- | --- |
| `files/local_models/lami_tts/prepared_hai` | `voice-text-bundle.json`と、その`sha256`に列挙された全ファイル。`text_frontend/`以下も保持 |
| `files/supertonic3-light5` | `manifest.json`と、4つのONNX・`tts.json`・`unicode_indexer.bin`・`voice.bin` |

Qwenはmanifest version 2・capacity 256の束を受け付けます。speakerが宣言されている場合は`lami_cute`を必須とします。
ストリーミング対象のdecoder SHA256は`05288ee2c763f80067139019ca66915bad150de705acc940200735f653c1b7c5`です。
未参照の診断ファイルなどはコピーしません。APKと外部モデルの両方を用意してください。
Supertonicは次の7ファイルを必須とし、個別ハッシュ・サイズとcanonical bundle hashを照合します。

```text
duration_predictor.int8.onnx
text_encoder.int8.onnx
vector_estimator.int8.onnx
vocoder.int8.onnx
tts.json
unicode_indexer.bin
voice.bin
```

この例のLight5はTTL1850学習済みLAMIの`voice.bin`をsid0に持ち、既成F1とは異なります。
canonical SHA256は`c88ef7f782d4f4ab9b409e3d1422748099f53fc0266e34025b3b3ddc2e1236db`、モデル7ファイルは145,295,768 bytesです。
HQ10は同じファイル名でもモデル内容と推論回数が異なります。Light5と異なるsteps・sidやHQ10プロファイルを宣言したmanifestは、配置前に拒否します。
追加学習候補の音質評価とは分けて扱い、通常Qwen音声と同等の可愛さ・品質を保証するものではありません。

## 計測と停止

設定画面の比較結果には、次の値を表示します。

| 表示 | 意味 |
| --- | --- |
| 再生開始通知まで | 試聴要求から再生側の開始通知まで。`AudioTrack.play()`等を基準にする |
| 再生終了まで（生成・再生を含む） | 要求から生成・待機・再生が終わるまで |
| 音声チャンク | その要求で再生経路が受け取ったPCMチャンク数 |
| 最初の音声チャンクまで | 要求から最初のPCMチャンク通知まで |
| 生成完了前の再生開始を確認 | 2チャンク以上を受け取り、再生開始時刻が入力全体のPCM生成完了時刻より前だった場合に表示 |

内部の比較結果にはPCM生成完了までの時間とunderrun数も保持します。
チャンク数と生成・再生の時刻を組み合わせて確認し、単に分割したPCMが複数あることだけで先行再生と判断しません。
試聴値にはモデルの初期化や待機も含まれます。文章、モデル、推論回数、ロード・常駐状態、端末状態を揃えて比較してください。
スピーカーから耳へ届く音響開始時刻は測定していません。再生終了時間は生成時間やRTFそのものではありません。

停止時は再生を停止し、古い合成結果を破棄します。Supertonic 3のsherpa-onnx 1.13.6には推論途中のnative cancelがないため、
現在の文の推論完了を待ってから解放・マイク移行します。停止処理中の表示が消えるまで待ってください。

JNI callbackの具体クラスと、元AARのORT 1.27.1を維持します。別バージョンのORTを重複追加しないでください。

## 出力保護と入力の検査

Qwen・Supertonicのストリーミングと、Kokoroを含む既存のPCM再生にも出力保護を適用します。
マナー・バイブモードでは、検証できるUSB出力がない読み上げを拒否します。
`assistantprobe`のJabra Evolve2 40 SE出力は、無音を送って実際のルートを確認してから音声を送ります。
USB切断・USB出力先の変更・検証済みUSB以外でのマナーモードへの変更時は、PCM受信待ちや末尾の再生中でも消音して終了します。
無音の確認用フレームは、音声サンプル数と末尾の再生時間の計算から除外します。

Android標準TTSは、整形後の文章を`getMaxSpeechInputLength()`以下のUTF-16長に分割し、各PCMの再生終了後に次を合成します。
句読点・空白を優先し、サロゲートペアを分断しません。初期化前の要求も分割状態を保持し、停止・置き換えでは残りを破棄します。

Qwenの利用可否と配置スクリプトは、選択されたmain・CP、行列、tokenizer、検証済みcausal decoderとRoPE表を必須構成として検査します。
manifestに任意のファイルが列挙されているだけでは利用可能になりません。既存の検証済みFP32・INT8構成も対応するSHA256で受理します。
利用可否の表示はmanifestとファイル情報を検査し、実ファイル全体のハッシュ照合は配置時と音声生成時に行います。

この3点の修正後、関連単体テスト428件中423件成功・既存5件skip、Python検証35件、実モデルを置いたエミュレーターの4テストが成功しました。
3方式の実再生、Supertonicの全文PCMとの一致、正常EOSと意図的な供給不足の区別を確認しています。
USB切断はRobolectricで検証し、物理USBの抜去・音響試聴は未実施です。
修正版APK・各ログ・ハッシュは[優先修正の検証記録](../artifacts/tts-priority-fixes-20261110/validation.json)に保存しています。

## 配置スクリプトの検証

```bash
python3 -m unittest scripts/tests/test_provision_tts_comparison_models.py -v
```

入れ子のファイル、ハッシュ不一致、空ファイル、パス逸脱、symlink、転送中変更、標準入力tar、配置前の端末ハッシュ照合、失敗時の旧モデル保持、dry-runでADB未使用を検証します。

## 統合版の検証と記録

ASR・チャット統合版を含む`da6b51450`を基に、`feat/tts-settings-comparison-20261110`で実装しています。通常の`standardDebug`アプリ内の設定画面から利用できます。

AndroidではPCM callback到着後の再生・不正形式・停止・失敗時の後始末を検査します。
Qwenでは対象decoderのpin、累積prefixの数値差、先頭prefixと末尾PCMの保持を検査します。
Supertonicでは同じnative文字チャンク内の複数PCM窓、最初のPCM受信とnative生成完了の順序、全文生成とのPCM一致を検査します。
共通の設定・比較経路では選択の保存、実際のエンジン生成、停止完了待ち、チャンク数と時刻による先行再生の判定を検査します。

検証用AVDは`LAMI_TTS_COMPARISON_20261110`です。モデル配置と端末ハッシュ照合、設定画面の操作・試聴記録は維持しています。
nativeビルド情報は[Supertonic生成記録](tts-supertonic-pcm-streaming-native.json)、今回のストリーミング検証ログ・設定画面の画像・APKのSHA256は`artifacts/tts-streaming-20261110/`に保存しています。`artifacts/tts-comparison-20261110/`はストリーミング対応前の設定切替検証です。

TTS関連の単体テストは310件中305件成功・既存5件skip、Python検証は29件成功しました。実モデルを配置したエミュレーターでは、設定UI・保存・3方式の実再生・Supertonic全文PCMとの一致を含む7件が成功しました。さらに初回モデルロードを停止バリア内へ移した後の最終APKで、Supertonic実再生・PCM一致の2件を再確認しました。

句読点・空白のない同じ一文「こんにちはラミィがお手伝いします」を使い、Androidは138チャンク、Qwenは5チャンク、Supertonicは7チャンクを受け取り、全方式で生成完了前の再生開始を確認しました。Qwenはこのエミュレーターでunderrunが5回あり、連続したリアルタイム再生が実現したことを示す結果ではありません。音響を録音した測定ではなく、物理端末の検証も未実施です。

最終APKと測定条件・各時間・制約は`artifacts/tts-streaming-20261110/validation.json`を参照してください。エミュレータの値から実機の速度や音質を推定しません。

その後のQwen音切れ改善では、先頭の蓄積目標を640〜2400ms分の音声で調整し、codec生成が再生速度を下回る場合は大きい目標を選びます。短い音声はEOSで残りをすべて再生します。初回decoderロードはモデル検証後に生成と並行して行います。AndroidとSupertonicの先頭再生方針は維持しています。

同じAVD・同じ一文で改善前後を比較し、バッファ不足検知は4回から1回、最初のPCMは17266msから14714ms、再生開始は17302msから20831msでした。再生開始を3529ms遅らせる代わりに途中の不足を減らし、生成完了前の再生開始を維持しました。生成されたcodec・PCMのSHA256も一致しています。これは1回ずつのエミュレーター計測で、音響試聴や実機の測定ではありません。

追加後のTTS単体テストは336件中331件成功・既存5件skipです。変更後APKと検証記録は`artifacts/qwen-smooth-streaming-20261110/`、仕組みと制約は[QwenのPCM再生](tts-qwen-pcm-streaming.md)に保存しています。

さらに正常EOSの終了処理と残り復号量の予測を追加しました。最終PCMを書き終えた時点で正常終了を通知し、末尾のフレームを消費する時間を待ってから解放します。生成が遅い場合はcodec EOSで総量を確定し、残りの復号時間に見合う音声が蓄積してから再生します。先頭待機中も停止できます。

実AudioTrackで、十分なPCMを先に渡しても旧終了処理では末尾でraw不足検知が0→1となること、新終了処理では0を保つことを確認しました。意図的に700ms供給を遅らせる試験では途中の不足を1回検知し、集計にも残します。定数減算や検知回数の補正は行っていません。

Qwen実モデルの初回・同文の連続・長めの文章の3再生すべてでraw不足検知0回、生成完了前の再生開始を確認しました。同文の72960サンプルのPCM SHA256は前のAPKと一致しています。TTS単体テストは363件中358件成功・既存5件skipです。最新のAPKハッシュ・トレース・再現条件は`artifacts/qwen-eos-continuity-20261110/`に保存しています。エミュレーターによる確認で、実機の音響試聴は未実施です。

## 長文の読み上げと推論待機（2026-11-11）

コードブロックを受信しても応答の読み上げ要求を維持し、コードを除いた説明文を同じカーソルで読み進めます。
LLMの出力待ちで発話キューが空になった後も、後続の文章が届くと読み上げを再開します。
Supertonicへ渡す文章は、句読点・空白を優先しながら最大80コードポイントに分割します。無句読点の長文にも上限を適用し、サロゲートペアを保持します。
PCMの30秒上限は維持します。共通キューでは送信前に処理件数とbusy状態を確定し、即時完了による件数の負数化や状態通知の逆転を防ぎます。
Supertonicでは先頭区間の最初のPCMを受け取ってから次区間を準備し、native推論の開始順を保証します。
後続区間が単一nativeスレッドを先に占有し、未開始の後続再生と先頭PCMが互いに待ち続ける状態を防ぎます。先頭区間の末尾再生中に次区間を生成する先読みは維持します。
vocoderが生成した有限の振幅超過は、FIR変換後の出力を`[-1,1]`へクリップし、`clippedSamples`へ集計します。同じsample rateの経路にも同じ処理を適用します。
NaN・Infinityと30秒の累積入力上限は引き続き検査します。

Qwenは実際のtokenizerで各候補の入力トークン数を測り、選択中のmain cacheに音声生成用の余裕を残します。
最大32コードポイントの区間を要求内で順番に処理し、各区間の自然EOS検査を維持します。停止・置き換えでは残りの区間も破棄します。

読み上げの停止判定とNPUの画面更新判定を分離し、NPUの逐次表示は単調増加する推論要求epochで管理します。
音声だけを停止した場合も、進行中の推論結果は画面へ届きます。
LLMから文章が来ない間やPCMの生成が再生に追いつかない間は待機が発生します。途切れのない連続再生には文章とPCMの継続的な供給が必要です。

今回の測定条件・検証結果の記録先は[長文読み上げの検証記録](../artifacts/tts-long-text-20261111/validation.json)です。
関連単体テストは462件中457件成功・既存5件skipです。実モデルを配置した無音エミュレーターで、Qwenの101文字・5区間の生成/再生完了、Supertonicの111文字・2区間から3秒待機とコードブロックを挟んだ34文字・2区間の再開を確認しました。
Supertonicの先頭区間は399,357サンプルのうち23サンプルをクリップし、途切れたままになるエラーを解消しています。raw underrunは各2区間で2回ずつ記録しており、完全な無音待機解消や音響的な連続性を示す測定ではありません。
修正版をNX733Jへ上書きし、実機のnative NPU推論2応答と、設定・チャットDB・共有設定ファイルの更新前後の一致を確認しました。実機での長文の音響試聴は未実施です。

## 発話開始・読み方・音高の追補（2026-11-11）

通常のチャットはLLMの応答開始時に、選択した音声エンジンへ所有権付きのprewarmを要求します。Supertonicのモデル読み込みは非同期に進め、停止・置き換え・音声入力・shutdownは未完了の読み込みも待ちます。prewarm自体は再生中と表示せず、失敗した場合は実際の発話で再試行します。

Supertonicの通常再生は40コードポイント未満で従来の8フレーム窓、40以上で16フレーム窓を使います。単一の事前読み込み済みセッションで順序を循環させた実機比較では、110文字の生成は中央値4,589→4,042ms、40文字は3,150→2,755msでした。短文は窓を増やすと最初のPCMが遅くなったため8を維持します。音声波形とF0を比較しており、窓の変更を声の高さの改善とは扱いません。

コード語はチャットと試聴で`print`→「プリント」、`return`→「リターン」、`Pygame`→「パイゲーム」のように読みます。camelCase、snake_case、略語、inline codeにも読みを適用します。表示・保存する原文と累積未読カーソルは変更しません。URLを識別子と誤認しないよう保護し、`print()`を含む日本語の説明行を読み飛ばしていた条件も限定しました。

実際の見出し`### 基本的なコードの骨組み（概念）`では、括弧部分を一続きに扱うと末尾位置が高くなる事例を再現しました。見出し末尾の全角括弧だけを読み上げ上の読点・句点へ変え、「基本的なコードの骨組み、概念。」と読みます。通常本文の括弧、関数のASCII括弧、画面のMarkdownは保持します。ホストの同一語比較では隣接語からの上昇が約8.9→0.65半音、Android x86の対応末尾位置では約11.95→0.50半音でした。ただしAndroid側のASRは当該語を正確に照合できていないため、発音の完全一致や全文章での音高固定を保証する結果ではありません。

Qwenは要求を横断する一つのキューで、再生中の区間と次の1区間だけを処理します。利用者の「早く話し始める」希望に合わせ、新しい発話群の先頭だけは640msのPCMが届くとprefix再生を開始し、遅い生成を理由にEOSまで待ちません。長文の残りと同じ発話群の後続要求は、自然EOS後に一度だけ復号して先読みします。idle・FLUSH後は再び先頭を優先します。途中の供給不足は発生し得るため、raw underrunをそのまま記録し、EOS検査も維持します。診断の既定方針と、無音の全区間準備計測は別設定です。

端末を利用中に同じアプリの`am instrument`を実行すると、試験の開始・終了でアプリが強制停止します。今回の再生時終了はこの記録と一致し、試験を止めて通常起動へ戻した後、利用者から再生できることを確認しました。以後の試験はホストと無音エミュレーターで行い、端末でのinstrumentationは停止しています。

Kokoroは別プロジェクト`lami-kokoro-voice/scratch`に全層を初期化する学習経路を作り、generator約8,122万・discriminator約93万パラメータを2,000ステップ学習しました。既存Kokoro本体・style checkpointは読み込まず、外部由来の近似整列ラベルは教師情報として明示します。全ステップの有限勾配と全パラメータの更新を確認しましたが、自由生成の検証4件はASR全文一致0/4で、読み上げ内容は未成立です。Androidへは導入していません。評価データ・近似整列・音響制御とdecoderの課題、checkpoint・再現手順は学習プロジェクトの診断記録へ保存しています。

最新版APK、個別の計測条件、試験ログ、学習結果への参照は[発話開始と読み方の検証記録](../artifacts/tts-realtime-20261111/validation.json)を参照してください。
