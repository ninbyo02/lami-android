# 拡張CPU/NPU機能比較と通常チャットの停止・再送

## 同一入力の日本語・履歴試験

```bash
python3 scripts/voice/check_cpu_npu_reference.py \
  --serial DEVICE_HOST:PORT \
  --package io.github.ninbyo02.lami.npuvalidation.assistantprobe --launch-app \
  --case-set scripts/data/cpu_npu_japanese_history_v1.json \
  --output-dir /tmp/lami-expanded-reference
```

6ターンで、日本語応答、姓の記憶、長めの作業記録からの担当者抽出、型番と曜日の履歴参照、曜日の英訳を確認します。正解は実行前にJSONに固定し、誤答を見た後で判定基準を緩めません。`--case-set` は1〜6ターンの非空prompt/expectedを受け付けます。これはモデルの最大コンテキスト長や大量連続利用の負荷試験ではありません。

`--npu-history-mode rehydrate_each_turn_v1` で履歴再構築方式も比較できます。要求設定はrequest.jsonに保存され、実際のモードはNPU結果のconversationStateProfileでも確認できます。

## 通常チャットのUI・停止・保存

```bash
python3 scripts/voice/check_normal_chat_lifecycle.py \
  --backend host_cpu --serial emulator-5580 --output /tmp/cpu-chat-lifecycle.json
python3 scripts/voice/check_normal_chat_lifecycle.py \
  --backend npu --serial DEVICE_HOST:PORT --output /tmp/npu-chat-lifecycle.json
```

CPU試験はエミュレータの `.assistantprobe`、NPU試験は実機の `.npuvalidation.assistantprobe` のみを操作します。NPU側は `lami.standardNpuRuntimeEnabled=true` のStandard Debugビルドと、対応するNPUモデルが必要です。通常版LAMIにはインストール・設定変更を行いません。専用パッケージは試験前後にforce-stop／再起動されます。

デバッグ専用 `NpuReferenceChatSetupActivity` は、パッケージ名を厳密に確認してからモデルスロット・LOCAL・NPUを設定し、TTSを無効にします。通常チャットで42→84、長い出力要求の停止、続けて3+4=7を試します。Roomの8メッセージの順序、応答のCOMPLETED/CANCELLED、バックエンドの記録を確認します。履歴は削除しません。

停止は送信位置に停止操作を送り、保存結果がCANCELLEDかを確認します。途中の文章が空の場合もあります。記録するUI復帰時間にはADBと画面ダンプの待ち時間が含まれ、NPUカーネルの中断遅延を直接測った値ではありません。再起動確認はActivity起動までで、過去チャットを選び直す操作の自動確認は含みません。TTS併用の安定性は未検証です。

## 2026-10-17の結果

- CPU基準：6/6正解。
- NPUのpersistent方式：別scenarioIdの2回とも5/6正解。型番Z31をZ1と回答しました。rawOutput段階でZ1であり、比較スクリプトやサニタイザーによる数字削除ではありません。
- NPUのrehydrate方式：4/6正解。型番に加え金曜日の英訳も誤りました。改善策として採用しません。
- エミュレータのCPU通常チャットと、専用実機APKのNPU通常チャット：42→84、キャンセル保存、次の7、Room保存、再起動の試験に合格しました。

実行安定性と回答品質を別に評価します。現在の差は、異なるモデル実体・量子化・ランタイム・思考設定等を含む比較であり、NPUハードウェアだけが原因とは断定しません。通常版への全面反映・品質同等認定は保留し、型番の再現性を次の検討事項として残します。記録は `docs/reviews/cpu-npu-expanded-20261017/` にあります。
