# NPU S2_DB Stability Test Plan

## 1. 目的

NPU standard route S2_DB の安定性を、複数プロンプトで比較・記録する。

S3 へ進む前に、Qualcomm/sm8750 版 LiteRT-LM モデルで NPU decode が成功した場合のみ DB 保存され、raw role contamination や echo 系の失敗が DB 保存されないことを確認する。

## 2. 前提条件

- Qualcomm/sm8750 版 LiteRT-LM モデルを使用する。
- NPU standard route は `S2_DB` を選択する。
- `max_output_tokens=128` で実行する。
- S2_DB 判定では以下を確認する。
  - `fallback_used=false`
  - `fresh_crash=false`
  - `timeout=false`
  - `run_decode_reached=true`
  - `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`

## 3. モデル条件

- Generic 版 `.litertlm` は対象外。
- Qualcomm / sm8750 / QNN / NPU 向けの LiteRT-LM モデルを選択する。
- モデル名またはファイル名で NPU 対応モデルとして判定されること。
- Generic CPU stable baseline や GPU experimental route とは結果を混在させない。

## 4. 設定条件

- 推論対象: Local
- NPU standard route mode: `S2_DB`
- max output tokens: `128`
- 自動 CPU/GPU fallback: 使用しない
- S3 Markdown: 無効
- S4 streaming: 無効
- TTS: 無効
- 記録対象は S2_DB の診断表示と DB 保存結果に限定する。

## 5. 合格条件

各プロンプトで以下を記録する。

- NPU decode が到達した場合は `run_decode_reached=true` であること。
- NPU 成功扱いでは `fallback_used=false`, `fresh_crash=false`, `timeout=false` であること。
- 正常な短文応答は `db=true` かつ `conversation_history_saved=true` になること。
- raw role contamination がある応答は `db=false` かつ `conversation_history_saved=false` になること。
- DB 保存された assistant message に `ユーザー:` / `アシスタント:` / `User:` / `Assistant:` が含まれないこと。
- 同じ assistant 応答が二重保存されないこと。

## 6. テストプロンプト一覧

1. こんにちは
2. ああああ
3. 明日の天気は
4. Pythonについて一言で教えて
5. 1+1は？
6. 自己紹介して
7. 日本語で短く返答してください
8. 箇条書きで3つ教えて
9. 今日の予定を確認したい
10. ありがとう

## 7. 記録表

| No | prompt | status | reason | quality_classification | sanitized_output | raw_role_contamination | db | conversation_history_saved | run_decode_reached | fallback_used | timeout | fresh_crash | npu_s1_decode_ms | npu_s1_tokens_per_second | judgement | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | こんにちは |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2 | ああああ |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 3 | 明日の天気は |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 4 | Pythonについて一言で教えて |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 5 | 1+1は？ |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 6 | 自己紹介して |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 7 | 日本語で短く返答してください |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 8 | 箇条書きで3つ教えて |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 9 | 今日の予定を確認したい |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 10 | ありがとう |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |

## 8. S3へ進む判定基準

S3 Markdown へ進む前に、次の条件を満たすこと。

- 10件中、正常プロンプトで `db=true` が安定して出る。
- raw role contamination は `db=false` で確実にブロックされる。
- NPU 成功ケースで `fallback_used=false` を維持する。
- `fresh_crash=false` を維持する。
- `timeout=false` を維持する。
- `run_decode_reached=true` を維持する。
- DB 保存された応答にロール混入がない。
- 会話履歴に user message または assistant message の二重保存がない。

