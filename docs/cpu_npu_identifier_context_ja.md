# 型番の誤答を切り分ける試験

2026-10-17、従来の6ターン試験でNPUがZ31をZ1と答えたため、履歴・転記・周辺記録量を分けて調べました。元の採点基準やモデルは変更していません。

## 実行

CPUのLiteRTブリッジを127.0.0.1:11436で起動し、専用実機APKとモデルを用意します。

```bash
python3 scripts/voice/run_identifier_probe_matrix.py \
  --serial DEVICE_HOST:PORT --output-dir /tmp/identifier-reference
python3 scripts/voice/run_identifier_probe_matrix.py \
  --controlled-lengths --cold-start \
  --serial DEVICE_HOST:PORT --output-dir /tmp/identifier-reference-cold
```

`--cold-start` は各試験前に専用パッケージ `.npuvalidation.assistantprobe` のみをforce-stopし、既存の比較ツールが再起動します。通常のLAMIやモデルスロットを変更しません。各試験は逐次実行し、固有scenarioIdのCPU/NPU結果を保存します。`execution_passed` と回答の全問正解は別判定です。

## 結果

短い日本語転記・英語転記・短い作業記録・短い履歴ではCPU/NPUともZ31を返しました。別の型番R4821の直接転記も両方で正解しました。数字を常に落とす現象ではありません。

同じ指示文と重要記録を使い、周囲の定型記録数を変えた、毎回アプリを再起動する試験：

| 周辺記録数 | CPUの回答 | NPUの回答 | 生の入力のCPUトークン数 |
|---|---|---|---:|
| 0 | Z31 | Z31 | 56 |
| 4 | Z31 | Z31 | 152 |
| 8 | Z31 | A | 248 |
| 16 | Z31 | Aの型番は **Z1** です。 | 440 |

トークン数はCPU側のtokenizeでユーザー文だけを測った値です。システム指示・テンプレートは含みません。NPUの実際の最大コンテキスト長や内部トークン列を測った値ではなく、これだけで上限超過とは判定しません。重要記録の相対位置も変わるため、長さだけの因果効果と断定しません。

プロセスを保持した4条件では、0件のエンジン生成失敗と4件のReceiver配信失敗も発生しました。これらを誤答や合格に数えず保存しています。再起動条件では4条件とも実行に成功しましたが、品質差は残りました。再起動で全問題が解消したとは扱いません。

## トークナイザーの照合

`fingerprint_litert_tokenizer.py` は、ローカルLiteRT-LMの `schema/core/litertlm_header_schema.fbs` に従い、既知のトークナイザーセクション(kind 4/6)のみを読み取り・ハッシュ化します。メタデータ2MiB、各トークナイザー16MiBを上限とし、範囲外参照・欠落・切り詰め等を拒否します。全モデルの重みを読み込む必要はありません。

```bash
python3 scripts/voice/fingerprint_litert_tokenizer.py \
  CPU_MODEL.litertlm NPU_MODEL.litertlm --output /tmp/tokenizers.json
```

今回の2モデルは、トークナイザーセクション4,689,013バイトのSHA-256が一致しました。PC上のNPUモデル全体のSHA-256も、以前に実機へ配置して確認したモデルと一致しました。

これでトークナイザー本体の不一致は説明候補から外せますが、テンプレート・特殊トークンの扱い・重み・量子化・ランタイムの差は残ります。今回の比較はNPUハードウェア単独の精度劣化の証明ではありません。

記録は `docs/reviews/identifier-context-20261017/` にあります。開発環境で短い転記が通っても、長めの記録からの抽出結果が実機で同等になるとは保証できないことを示す回帰試験として保持します。
