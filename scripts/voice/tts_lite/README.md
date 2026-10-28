# Japanese low-latency TTS host prototype

LAMI用の軽量TTSを、既存のMeloTTS日本語モデルとOpen JTalkを使って比較する独立した試作です。
この段階では上流のJP話者を使います。ラミィの声の学習・Android組み込み・実機性能の受入は別工程です。

## Scope and evidence

- CPUで任意の日本語文を生成し、入力文・読み・PCM・各段階の時間を記録する。
- MeloTTSの日本語前処理をそのまま使う比較と、Open JTalkの読みを先に通す比較を用意する。
- 実際の日本語発話から得た潜在表現で、波形生成部分を固定形状ONNXへ書き出す。
- 同じ波形生成部分をQAIRTでSM8750向けにオフラインコンパイルする。
- モデル・音声・SDKバイナリは`artifacts/tts-lite/`に置き、Gitには入れない。

モデルの推論計算と日本語の音素化は、版を固定したMeloTTSの実装を再利用します。
日本語モデルは`disable_bert=true`なので、他言語の前処理やBERTの重みを読み込む汎用APIを避け、
必要なモデルと日本語フロントエンドだけを読み込みます。日本語トークナイザーの版も固定します。
Open JTalkは読みの補助で、音声合成バックエンドや別の声は使用しません。

## CPU reproduction

Python 3.12、Git、uv、C/C++ビルド環境を使用します。
`pyopenjtalk==0.4.1`のビルドと、初回の辞書取得にはネットワークが必要です。

```bash
uv venv artifacts/tts-lite/venv --python python3.12
uv pip install --python artifacts/tts-lite/venv/bin/python --index-url https://download.pytorch.org/whl/cpu torch==2.5.1+cpu
uv pip install --python artifacts/tts-lite/venv/bin/python -r scripts/voice/tts_lite/requirements-cpu.txt
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/prepare_assets.py --output artifacts/tts-lite
artifacts/tts-lite/venv/bin/python -m unittest discover -s scripts/voice/tts_lite -p 'test_*.py'
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/benchmark.py \
  --source artifacts/tts-lite/MeloTTS --model-dir artifacts/tts-lite/MeloTTS-Japanese \
  --frontend openjtalk --limit 16 --repeats 2 --output artifacts/tts-lite/cpu-openjtalk-new
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/export_decoder.py \
  --source artifacts/tts-lite/MeloTTS --model-dir artifacts/tts-lite/MeloTTS-Japanese \
  --frames 32 --output artifacts/tts-lite/decoder-new
```

`--frontend upstream`で、上流の前処理を変更しない比較を実行できます。
出力フォルダは既存結果を上書きしないため、新しい名前を指定してください。
`assets.lock.json`がソース・モデル・トークナイザーの版とモデルのSHA256を記録し、
`requirements-cpu.lock.txt`が今回使った依存パッケージ全体を記録します。

## Offline SM8750 compilation

今回検証するSDKはQAIRT **2.46.0.260424**です。モデルコンパイル用の既存ローカルイメージ
`qairt-dev:ubuntu22`を土台に、C++ランタイムとClangを追加します。
SDKは利用者が別途用意し、読み取り専用でコンテナへマウントします。
CPU用Python 3.12環境と、SDK用Python 3.10環境を分けています。

```bash
docker build -t lami-tts-lite-qairt:20261028 -f scripts/voice/tts_lite/Dockerfile.qairt scripts/voice/tts_lite
docker run --rm --user "$(id -u):$(id -g)" \
  -e UV_CACHE_DIR=/work/qairt-uv-cache \
  -v "$PWD/artifacts/tts-lite:/work" -v "$(command -v uv):/usr/local/bin/uv:ro" \
  --entrypoint /usr/local/bin/uv lami-tts-lite-qairt:20261028 \
  venv /work/qairt-venv --python /usr/bin/python3
docker run --rm --user "$(id -u):$(id -g)" \
  -e UV_CACHE_DIR=/work/qairt-uv-cache \
  -v "$PWD/artifacts/tts-lite:/work" -v "$PWD/scripts/voice/tts_lite:/inputs:ro" \
  -v "$(command -v uv):/usr/local/bin/uv:ro" \
  --entrypoint /usr/local/bin/uv lami-tts-lite-qairt:20261028 \
  pip install --python /work/qairt-venv/bin/python -r /inputs/requirements-qairt.txt
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/probe_qnn.py \
  --sdk-root /path/to/qairt/2.46.0.260424 --artifacts artifacts/tts-lite \
  --decoder artifacts/tts-lite/decoder-new/decoder.onnx --output artifacts/tts-lite/qnn-sm8750-new
```

変換・モデルライブラリ作成・HTPコンテキスト生成を順番に実行し、各段階の結果とハッシュを保存します。
ネットワークを使うのは環境と公開資産の準備時です。コンパイルは`--network none`で実行します。
SM8750、FP16、VTCM 8MBを指定します。生成結果の存在とサイズを確認してから成功と記録します。

## Interpreting the measurements

- `pcm_ready_ms`は前処理と**全文の波形生成完了**までの時間です。発話開始時間ではありません。
- `model_init_ms`は別に記録します。最初の推論もウォーム集計から外します。
- CPUカーネルのキャッシュ効果を混ぜないよう、初回の文と同じ文の繰り返しを分けて集計します。
- RTFは生成時間÷音声の長さです。PCのRTFからAndroidやNPUの速度は推定しません。
- ONNXの出力一致はFP32の書き出し確認です。HTPのFP16出力品質を検証したことにはなりません。
- 固定長の潜在表現を切り出したデコーダは、連続チャンク境界の品質を保証しません。
- Open JTalkで確認した読みの修正も、任意の固有名詞や抑揚の品質を保証しません。

## Next implementation gates

音響モデルの分離と固定サイズのチャンク化は、16文でCPU/ONNX出力を確認済みです。
約186ms分の音声を取り出すために、前後16フレームの文脈を含む48フレームのデコーダを使います。
各段階のマスクで真の先頭・末尾の境界を再現するため、先頭／途中／末尾で別モデルを常駐させる必要がありません。
この実装は[MeloTTSの既存演算を利用した派生部分](THIRD_PARTY_NOTICES.md)を含みます。

```bash
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/validate_chunking.py \
  --source artifacts/tts-lite/MeloTTS --model-dir artifacts/tts-lite/MeloTTS-Japanese \
  --output artifacts/tts-lite/chunk-parity-new
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/benchmark_chunks.py \
  --source artifacts/tts-lite/MeloTTS --model-dir artifacts/tts-lite/MeloTTS-Japanese \
  --output artifacts/tts-lite/chunk-benchmark-new
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/export_chunk_decoder.py \
  --source artifacts/tts-lite/MeloTTS --model-dir artifacts/tts-lite/MeloTTS-Japanese \
  --output artifacts/tts-lite/chunk-onnx-new
```

`probe_qnn.py`へ`chunk-onnx-new/decoder.onnx`を渡すと、7入力の固定サイズモデルを事前コンパイルできます。
書き出した`input_list.txt`はONNX用です。QNNコンパイラは潜在表現の軸をNCTからNTCへ変えるため、
実機へ渡す入力は別に準備します。`prepare_qnn_inputs.py`は変換後のメタデータから軸とネイティブ精度を読み、
先頭・途中・末尾の入力を変換し、ファイルからの読み戻しも確認します。

```bash
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/prepare_qnn_inputs.py \
  --onnx-dir artifacts/tts-lite/chunk-onnx-new --compiled-dir artifacts/tts-lite/qnn-sm8750-new \
  --output artifacts/tts-lite/qnn-native-inputs-new
```

ネイティブFP16のリストはQNN NetRunで`--use_native_input_files`を付けて使います。
入力のFP16丸めだけをONNXで確認しており、HTP内部の演算やFP16の重みを検証した結果ではありません。

`first_pcm_chunk_ready_ms`はモデル常駐後、全文の入力が渡されてから最初のPCMチャンクができるまでの時間です。
Pythonの起動、モデル読み込み、音声デバイスのバッファは含みません。`projected_gaps`は時刻からの推定で、実機のアンダーランではありません。
文脈の再計算をするため、全文の一括合成より総計算量は増えます。

## LAMI voice training preparation

以前確定した`lami-cute-v1`のmanifestハッシュを固定し、全500音声の元ファイルのハッシュを照合します。
元データは変更せず、44.1kHzのコピーと、推論時と同じOpen JTalkの読みを使う学習用メタデータを作ります。
同じ正規化テキスト・同じ音声が学習用と検証用に分かれないように分割します。
この準備と学習用forward/backwardの確認は、学習済みの声を作ったことを意味しません。

```bash
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/prepare_voice_data.py \
  --source artifacts/tts-lite/MeloTTS --model-dir artifacts/tts-lite/MeloTTS-Japanese \
  --dataset /path/to/lami-android-voice-dataset/frozen/lami-cute-v1 \
  --output artifacts/tts-lite/lami-voice-new
uv pip install --python artifacts/tts-lite/venv/bin/python -r scripts/voice/tts_lite/requirements-training-smoke.txt
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/training_smoke.py \
  --source artifacts/tts-lite/MeloTTS --model-dir artifacts/tts-lite/MeloTTS-Japanese \
  --prepared-data artifacts/tts-lite/lami-voice-new --output artifacts/tts-lite/training-smoke-new.json
```

日本語モデルの既存埋め込みを保持するため、256行の話者テーブルを残し、話者0だけを`lami_cute`へ割り当てます。
`disable_bert=true`でも上流ローダーがBERTファイルを開くため、期待する形のゼロテンソルを小さな共有ストレージとして保存します。
`config.prepared.json`は学習用の準備設定で、GPUやGAN学習ループの検証済み設定ではありません。

## Remaining implementation gates

1. 日本語の数字・日付・英字・固有名詞を増やし、音声を聴いて品質を評価する。
2. 軽量な単一話者モデルへ、正本`lami-cute-v3-03`と人が承認した音声データを使って声を学習する。
   Chronicleの音声や、未承認の生成データを教師データへ混ぜない。
3. `LamiVoiceEngine`の独立した実装へ接続し、生成・再生の並行処理と即時停止を検証する。
4. NX733Jで、コールド／ウォームの可聴再生開始、RTF、アンダーラン、メモリ、LLM同時動作、発熱と消費電力を測る。

SDK・モデル・辞書の配布条件は、将来APKへ含める対象を決めた時点で確認します。
今回の変更は開発スクリプトと検証記録だけです。

## Sources

- [MeloTTS](https://github.com/myshell-ai/MeloTTS)
- [MeloTTS Japanese weights](https://huggingface.co/myshell-ai/MeloTTS-Japanese)
- [pyopenjtalk](https://github.com/r9y9/pyopenjtalk)
- [Qualcomm ASR/LLM/TTS example](https://github.com/qualcomm/qidk/tree/master/GenAI-Solutions/ASR-LLM-TTS)

MeloTTSコードはMIT、pyopenjtalkはMIT、Open JTalkはModified BSDです。
派生コードのライセンス通知を同梱し、モデル重みとSDKは外部の版を固定して参照します。
