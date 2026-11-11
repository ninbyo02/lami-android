# Japanese low-latency TTS host prototype

LAMI用の軽量TTSを、既存のMeloTTS日本語モデルとOpen JTalkを使って比較する独立した試作です。
上流のJP話者を基準に、採用済みラミィ音声を使う短い学習試験と比較できます。
学習候補は未採用です。Android組み込み・実機性能の受入は別工程です。

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
`config.prepared.json`は準備用の設定です。GPUでの短いGAN学習は下記の上限付きラッパーで検証します。

## Bounded GPU voice trials

採用済み480音声で実際に重みを更新し、20音声を検証専用に使う短い試験です。
GPU環境はCPU推論環境から分離します。元のMeloTTS生成器・判定器・MAS整列・GAN損失を再利用し、
学習回数と時間に上限を設けたラッパーだけを追加しています。

```bash
docker build -t lami-tts-lite-training:20261028 \
  -f scripts/voice/tts_lite/Dockerfile.training scripts/voice/tts_lite
python3 scripts/voice/tts_lite/run_finetune.py \
  --output artifacts/tts-lite/voice-trial-new --steps 200 --max-minutes 20
# 発音の変化を抑える比較試験。両方とも同じ元の日本語モデルから始めます。
python3 scripts/voice/tts_lite/run_finetune.py \
  --output artifacts/tts-lite/voice-frozen-trial-new --steps 200 --max-minutes 20 \
  --freeze-text-duration
```

現在のランチャーはLinuxの開発PC（UID/GID 1000）とROCm GPUを対象とします。
ホストのGPUデバイスの数値GIDを渡し、コンテナ内のグループ名との差を避けます。
実行時はネットワークを無効化し、元のモデル・MeloTTSソース・準備済み音声を読み取り専用で渡します。
200ステップまたは20分の上限を、各学習ステップの開始前に確認します。
初期化・検証・保存の時間はこの学習時間制限とは別です。

上流が指定する判定器 `D.pth` / `DUR.pth` のS3 URLは実行時に404でした。
今回は日本語の生成器だけを既存重みから始め、判定器は上流構造の標準初期化を使います。
生成器の学習率は1e-5、判定器は2e-4、batch 4、FP32です。
`--freeze-text-duration` は `enc_p` / `dp` / `sdp` / `emb_g` を固定し、
それらを評価モードで使います。音素長の損失は記録されますが、固定した音素長予測器の更新はありません。

`candidate-step*/` はCPU推論・チャンク比較・ONNX書き出しの `--model-dir` に渡せます。
チェックポイントには試験中の声であること、学習回数、元データのハッシュ、固定層を記録します。
`training-state.pth` はモデルとoptimizerの保存です。データローダー状態を含む厳密な再開機能はまだありません。
通常アプリの既存エンジンへは自動採用しません。

```bash
uv venv --python python3 artifacts/tts-lite/asr-venv
uv pip install --python artifacts/tts-lite/asr-venv/bin/python \
  -r scripts/voice/tts_lite/requirements-voice-asr.txt
artifacts/tts-lite/asr-venv/bin/python scripts/voice/tts_lite/evaluate_voice_asr.py \
  --trial artifacts/tts-lite/voice-trial-new --model /path/to/cached/faster-whisper-small/snapshot \
  --openjtalk-dict /path/to/open_jtalk_dic_utf_8-1.11 --output artifacts/tts-lite/voice-asr-new
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/make_voice_comparison.py \
  --trial artifacts/tts-lite/voice-trial-new \
  --dataset /path/to/lami-android-voice-dataset/frozen/lami-cute-v1 \
  --output artifacts/tts-lite/voice-comparison-new.wav
```

音声認識には期待する文章を渡しません。基準音声・学習前・学習後を同じ設定で認識し、
Open JTalkでカナ化した文字誤り率（CER）を比較します。この指標は声の類似度・抑揚・自然さの合否を表しません。
比較WAVは検証専用の3文を基準音声→学習前→学習後の順に並べ、時刻とハッシュをJSONへ記録します。
主観評価は別に必要です。
`--alternate-trial` と `--limit 1` を付けると、同じ1文について2つの学習候補まで並べて比較できます。
今回の[試験結果と未達項目](../../../docs/reviews/2026-10-28-tts-lite-voice-trials.md)も参照してください。

## Remaining implementation gates

最新の検証は[更新する層・学習率の比較](../../../docs/reviews/2026-10-28-tts-lite-update-groups.md)を参照してください。
[長音の欠落修正と1,000ステップの比較学習](../../../docs/reviews/2026-10-28-tts-lite-long-vowels.md)から続けて原因を調べています。
声の類似度・自然さを含む採用判断は、音声を聴いて確認するまで保留しています。

### Long-vowel profile and checkpoint evaluation

ピン留めした上流の `ー` の変換規則は `":"` ですが、文字列分割でその出力が消えます。
`japanese_phonemes.py` は元の変換器を再利用し、長音を直前の母音へ解決します。
`melo_legacy` は過去の比較用、`melo_long_vowels_v1` はこの修正を有効にするプロファイルです。
モデル設定の `tts_lite_phoneme_profile` を推論時に自動で読みます。省略時は過去のモデルの再現用にlegacyを使います。

```bash
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/prepare_voice_data.py \
  --source artifacts/tts-lite/MeloTTS --model-dir artifacts/tts-lite/MeloTTS-Japanese \
  --dataset /path/to/lami-android-voice-dataset/frozen/lami-cute-v1 \
  --output artifacts/tts-lite/lami-voice-long-vowels-44100 --phoneme-profile melo_long_vowels_v1
python3 scripts/voice/tts_lite/run_finetune.py \
  --prepared-data artifacts/tts-lite/lami-voice-long-vowels-44100 \
  --output artifacts/tts-lite/lami-gan-long-vowels-1000 \
  --steps 1000 --max-minutes 20 --save-every 200 --eval-every 200 \
  --eval-seeds 42,43,44 --freeze-text-duration
```

新しい比較は同じ元の日本語モデルから開始します。誤った音素列で学習した旧候補からの継続ではありません。
音声・正規化した読み・480/20件の分割を保持し、変換済み音声のハッシュと全500件の学習／推論のID一致を
`audit_long_vowels.py` で確認します。モデル設定とチェックポイントには音素プロファイルも記録します。

途中の各段階では検証20文を3つの乱数条件で生成し、保存済みの候補を比較できます。
評価時は文ごとに乱数をリセットし、MASの評価用ノイズを0にします。学習用の乱数とノイズ設定は評価後に戻します。
過去の1条件の検証とは評価手順が異なるため、今回の学習前と学習後を組にして比較します。

```bash
artifacts/tts-lite/asr-venv/bin/python scripts/voice/tts_lite/evaluate_voice_asr.py \
  --trial artifacts/tts-lite/lami-gan-long-vowels-1000 --step 200 \
  --model /path/to/cached/faster-whisper-small/snapshot --openjtalk-dict /path/to/open_jtalk_dic_utf_8-1.11 \
  --output artifacts/tts-lite/long-vowels-asr-200
# --step 400/600/800/1000 の結果も別フォルダへ保存します。
# --reuse-baseline に前のASR結果フォルダを渡すと、モデル・設定・音声ハッシュ・期待する読みが同じ結果を再利用します。
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/select_voice_checkpoint.py \
  --artifacts artifacts/tts-lite --trial artifacts/tts-lite/lami-gan-long-vowels-1000 \
  --output artifacts/tts-lite/long-vowels-selection.json
```

`probe_phoneme_profiles.py` は学習なし・同じCPUモデルで長音修正だけを比較します。
出力の `comparisons.jsonl` をASRの `--comparisons` へ渡せます。
この形式は `variants` 内の `baseline` / `candidate` と任意の `reference` の音声パスを持ち、追加文の比較にも使えます。
比較WAVへ `--step 1000 --frontend-control .../comparisons.jsonl --limit 1` を付けると、
基準音声→修正前→長音修正後→学習後を同じ1文で聞き比べられます。

### Update groups and learning-rate ablations

`diagnose_voice_modules.py` は同じ上流モデルと学習後モデルのflow・デコーダを組み替え、
16文×3乱数×4組の音声を作ります。テキスト・音素長予測器・話者埋め込みの重みが同じことを要求し、
各音素の長さとpriorのサンプルが全組で等しいことを確認します。
組み替えたモデルは学習時の組合せを崩すため、原因を調べる補助であり採用候補ではありません。

```bash
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/diagnose_voice_modules.py \
  --source artifacts/tts-lite/MeloTTS --model-dir artifacts/tts-lite/MeloTTS-Japanese-long-vowels \
  --candidate artifacts/tts-lite/lami-gan-long-vowels-1000/candidate-step001000 \
  --prepared-data artifacts/tts-lite/lami-voice-long-vowels-44100 \
  --output artifacts/tts-lite/voice-module-swaps-new
python3 scripts/voice/tts_lite/run_finetune.py \
  --prepared-data artifacts/tts-lite/lami-voice-long-vowels-44100 \
  --output artifacts/tts-lite/voice-low-lr-new --steps 400 --max-minutes 10 \
  --save-every 400 --eval-every 400 --eval-seeds 42,43,44 \
  --learning-rate 3e-6 --update-modules enc_q,flow,dec
```

`--update-modules enc_q,flow` はデコーダを固定し、`enc_q,dec` はflowを固定します。
既存の各モジュールを単位に更新対象を選び、構造は変えません。指定したもの以外は固定して評価モードにし、
更新対象・学習率・学習可能パラメータ数と10ステップごとのモジュール別勾配を保存します。
`--freeze-text-duration` との同時指定はできません。
音声のposterior encoderである `enc_q` は学習用forwardで使いますが、テキストからの自由合成では呼びません。
そのため、学習用メル誤差の低下だけで自由合成の改善とは判断できません。

`render_voice_candidates.py` は、事前に準備したphone/tone/language IDとモデル一覧JSONを使い、
同じ環境の自由合成で候補を比較します。既定はGPU、`--device cpu` はCPUです。
入力JSONLは `id` / `normalized_text` / `phoneme_profile` と
`phone` / `tone` / `language` のID配列を持ちます。モデル一覧は `baseline` を含む名前→ディレクトリです。
CPUとGPUでは乱数列が異なるため、別の実験の値を直接差し引きません。

`summarize_voice_ablation.py` は条件名→ASR結果ディレクトリのJSONを受け取り、
基準WAVのハッシュ・期待する読み・認識文・ASR設定が一致することを確認して集計します。
全条件の値、seed別、改善／悪化した要求数、任意のカテゴリ別を残します。
同じ16文を3回生成しても独立した48文にはなりません。過去に使った検証集合や追加文も独立した最終テストではありません。

```bash
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/summarize_voice_ablation.py \
  --reports artifacts/tts-lite/voice-review-additional-reports.json \
  --corpus scripts/voice/tts_lite/japanese-probes.jsonl \
  --output artifacts/tts-lite/voice-review-additional-summary.json
artifacts/tts-lite/venv/bin/python scripts/voice/tts_lite/audit_voice_ablation.py \
  --artifacts artifacts/tts-lite --output artifacts/tts-lite/voice-review-integrity.json
```

今回のASRでは、ほぼ等しい全文合成／チャンク合成のPCM16の差が最大1でも、
「2本／日本」の表記などが変わりカナCERが動く例を確認しました。
学習前後は同じ合成方式と設定で比較し、小さな指標差を発音・声質の改善の証明には使いません。

以下の採用条件は継続します。

1. 日本語の数字・日付・英字・固有名詞を増やし、音声を聴いて品質を評価する。
2. 正本`lami-cute-v3-03`と人が承認した音声データによる短い学習結果を比較し、発音と声質を満たす候補へ育てる。
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
