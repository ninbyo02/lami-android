# CPU基準とNPU実機の機能比較

`check_cpu_npu_reference.py` は同じ2入力とシステム指示をPC CPU版と実機の既存NPU Conversation診断へ渡します。17+25=42、続けてその2倍=84という回答と履歴参照を確認します。通常チャットUI・Room保存の確認には別途 `check_emulator_main_chat.py` を使用します。

```bash
python3 scripts/voice/check_cpu_npu_reference.py \
  --serial DEVICE_HOST:PORT --package io.github.ninbyo02.lami \
  --host-port 11436 --model gemma4-e2b-litert-cpu \
  --output-dir /tmp/lami-cpu-npu-comparison
```

先に `serve_litert_host_cpu.py` を指定ポートで起動してください（既定ポートは11435）。実機には対応する診断Receiver、NPUランタイム、認識可能なSM8750用モデルが必要です。このツールはAPKインストール・モデル転送・モデルスロット設定変更・アプリデータ削除・logcat消去を行いません。診断Receiverが更新する既存結果・ネイティブログ4ファイルは、実行前に出力ディレクトリへ保全します。実機で別の推論試験が動いていない状態で実行してください。

実機なしでCPU基準だけ検証する場合：

```bash
python3 scripts/voice/check_cpu_npu_reference.py --cpu-only \
  --host-port 11436 --output-dir /tmp/lami-cpu-npu-comparison
```

毎回UUID付きディレクトリを新規作成し、入力、CPU結果、NPU結果、比較JSONを保存します。NPU結果のscenarioId一致・入力文と順序・バックエンド・全ターンの成功と非空応答を検証します。過去の成功結果を今回の結果として扱いません。未実行・モデル未検出・タイムアウトはNPU合格になりません。処理成功と回答の正誤は別項目です。CPU単独の成功は`cpu_only`で、NPUは`not_run`です。

これは機能比較であり、NPUの数値一致やハードウェア実行の独立認証ではありません。CPUとNPUではモデル実体・ランタイム・履歴処理・思考設定が異なります。両方top_k=1を要求しますが、CPU seed=0、NPU seed=42です。PCとスマホの所要時間からNPUの加速倍率を算出しません。応答が一致しても同じ重み・同じlogit・同じ量子化演算であることの証明にはなりません。

## モデル名互換性

旧APKは `gemma-4-E2B-it_qualcomm_sm8750.litertlm` だけを認識する場合があります。正式名 `ninbyo02-gemma-4-E2B-it-SM8750-NPU.litertlm` を保存した端末では、ファイルが存在しても `model_file_not_found` になることがあります。mainの `Qairt244ModelPathResolver` は両名とタイムスタンプ付きの名前を認識します。恒久対応はこの実装を含むAPKへの更新です。比較ツール自身はファイル名・モデルを変更しません。

NPUが完了JSONを残さず終了した場合も、ネイティブ診断結果と終了履歴を保存します。これらが過去の試験の記録である可能性もあるため、時刻・PIDと今回のリクエストを照合して判断してください。終了履歴だけからクラッシュの根本原因を断定しません。

新規インストール直後のAPKは一度起動してから比較してください。診断Receiverの受理コード244が得られない場合は、比較ツールが配信失敗として終了します。QNNランタイム不一致の原因と修正確認は `qairt244_qnn_runtime_mismatch_ja.md` を参照してください。
