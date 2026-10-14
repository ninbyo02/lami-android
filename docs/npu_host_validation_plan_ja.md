# LAMI 実機なしNPU事前検証
2026-10-15。実装は専用worktree。アプリ本体・常駐ジョブは変更しない。

## 実装済み
scripts/voice/npu_host_preflight.py は過去JSONと現在の成果物を読み取り、存在・サイズ・記録済みSHA256を検査する。過去のstatusと今回の実行結果を分離する。SHA256がない成果物は未検証と表示する。不正JSON・欠落・不一致は終了コード1。
これはモデル推論、SoCコンパイル、NPU実行、音声品質の検証ではない。

実行例:
python3 scripts/voice/npu_host_preflight.py docs/reviews/2026-10-03-neural-tts-npu-compile.json docs/reviews/2026-10-03-neural-tts-npu-accuracy.json --output /tmp/npu-host-preflight.json

## 実ファイル確認
既存SM8750向けtfliteは記録済みSHA256と一致。contextはサイズ一致、期待SHA256がなく同一性未確認。
既存の合成入力17ステップの記録では位置4/8/9でCPU/NPU argmaxの差がある。独立キャッシュによる生成比較でも、音声品質評価でもない。

## 次段階
1. 実際のTTSコード列とPCMデコーダを確定し、既存check_neural_tts_prefix_decode.pyでホスト数値比較を再実行する。
2. 実機トレース形式を定義する。モデル/ランタイムのハッシュ、入力ID、要求ID、相対時間、失敗段階、出力参照を必須にする。再生結果を実測と混同しない。
3. Android側推論境界に差し替え点があるか確認し、記録再生を接続する。自作状態機械だけの成功をアプリ検証としない。
4. 遅延、途中失敗、停止後の遅着出力、再ロードを注入し実際のキャンセル/復旧処理を検証する。
5. APK内ELF依存とライブラリ組み合わせを検査する。整合性確認とドライバ互換性保証を区別する。
6. 接続時にSM8750で連続推論・LLM同時実行・速度・発熱を最終確認する。

## 合格条件
ホスト、対象SoCコンパイル、実機、連続音声を別々に記録する。未実施はnot_run、基準未設定は未判定。ホスト検証だけでNPU合格に昇格させない。
