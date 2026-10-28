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

1. 音響モデルと波形生成を分割し、必要な前後文脈を含むチャンクで全文波形との一致を確認する。
2. 日本語の数字・日付・英字・固有名詞を増やし、音声を聴いて品質を評価する。
3. 軽量な単一話者モデルへ、正本`lami-cute-v3-03`と人が承認した音声データを使って声を学習する。
   Chronicleの音声や、未承認の生成データを教師データへ混ぜない。
4. `LamiVoiceEngine`の独立した実装へ接続し、生成・再生の並行処理と即時停止を検証する。
5. NX733Jで、コールド／ウォームの可聴再生開始、RTF、アンダーラン、メモリ、LLM同時動作、発熱と消費電力を測る。

SDK・モデル・辞書の配布条件は、将来APKへ含める対象を決めた時点で確認します。
今回の変更は開発スクリプトと検証記録だけです。

## Sources

- [MeloTTS](https://github.com/myshell-ai/MeloTTS)
- [MeloTTS Japanese weights](https://huggingface.co/myshell-ai/MeloTTS-Japanese)
- [pyopenjtalk](https://github.com/r9y9/pyopenjtalk)
- [Qualcomm ASR/LLM/TTS example](https://github.com/qualcomm/qidk/tree/master/GenAI-Solutions/ASR-LLM-TTS)

MeloTTSコードはMIT、pyopenjtalkはMIT、Open JTalkはModified BSDです。
ここでは上流コードやSDKのコピーをGitへ追加せず、参照先と版を記録します。
