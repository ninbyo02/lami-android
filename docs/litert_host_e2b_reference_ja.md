# Gemma 4 E2B .litertlm の実機なしCPU参照実行

2026-10-16。PCで既存の汎用Gemma 4 E2B `.litertlm` を直接実行し、エミュレータの通常LOCALチャットへ接続した。
以前の軽量LFM2・Gemma4 E4Bを使う経路に加え、E2B系統のCPU参照実行が使える。

## 構成
モデル: `/home/sato/Downloads/gemma-4-E2B-it.litertlm`、2,588,147,712バイト。
SHA256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`。
隔離Python環境: `/home/sato/lami-tts-daemon/host-litert-reference-20261016/venv`。
`litert-lm-api==0.18.0`。CPU4スレッド、文脈2048、出力上限64トークン、top_k=1/seed=0、thinking無効。

`serve_litert_host_cpu.py` は127.0.0.1:11435だけで待ち受ける、開発用の最小Ollama chat互換アダプタ。完全なOllama APIではない。`/health` と `/api/chat` のみを実装する。
固定のモデルalias・CPU要求・テキスト履歴のみ受け付ける。各要求は専用子プロセスで実モデルをロードし、終了時に解放する。最大120秒で子を終了。同時推論は1件で、競合時は503を返す。
クライアント切断は検出して所有する子プロセスを終了する。サーバー停止時も所有する子だけを終了する。

## 実行
既存変換環境は変更していない。新しく環境を作る場合はvenvを別途作り、`pip install litert-lm-api==0.18.0` を実行する。

PCでリポジトリのルートから:
```sh
python3 scripts/voice/serve_litert_host_cpu.py --model /home/sato/Downloads/gemma-4-E2B-it.litertlm --runtime-python /home/sato/lami-tts-daemon/host-litert-reference-20261016/venv/bin/python
```

サーバーを実行したまま、別ターミナルで最新版debug APKをインストール済みのエミュレータに対して:
```sh
python3 scripts/voice/check_emulator_main_chat.py --serial emulator-5580 --host-port 11435 --model gemma4-e2b-litert-cpu --output /tmp/e2b-reference-check.json
```

通常画面だけを開く場合:
```sh
/home/sato/Android/Sdk/platform-tools/adb -s emulator-5580 shell am start -n io.github.ninbyo02.lami.assistantprobe/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity --ez enable_main_chat true --es model gemma4-e2b-litert-cpu --ei host_port 11435
```

`--host-port` を省略すると従来のPC Ollama（11434）を使う。エミュレータからの接続先ホストは固定の10.0.2.2で、ポートだけを明示変更する。スマホへの配布・推論は行っていない。

CPUモデル単体の確認は `check_litert_host_cpu.py --model ... --runtime-python ... --output ...`。ネイティブ異常終了・時間超過も親プロセスが失敗JSONへ記録する。

## 検証と限界
- E2B汎用モデルのCPU単体推論で17＋25→42。
- 通常チャットで42→その2倍→84。表示とCOMPLETEDの履歴4件を照合し、再起動成功。
- AndroidへモデルSHA256・サイズ・ランタイム版を引き渡し、上記モデルと一致することを確認。
- 誤ったモデル名は404、GPU要求は400。生成中のクライアント切断後、所有する子プロセスが0件になることを確認。
- `gemma4-e2b-qat-srq-a16w8-cpu.litertlm` は標準0.18.0ランタイムで `Input positions not found`。CPUというファイル名だけで実行互換とは判断できない。既存ファイルは変更していない。
- 汎用モデルも最初の文脈512設定ではDYNAMIC_UPDATE_SLICEの割り当てに失敗した。文脈2048にして成功した。
- この経路は汎用E2BのCPU推論。SM8750専用コンパイル済みバイナリやQAT版と同一結果を保証しない。トークン上限64での終了を自然EOSとは断定しない。
- NPU命令、ドライバ、メモリ競合、実機SIGABRTのエミュレーションではない。

実測JSONは `docs/reviews/litert-host-e2b-20261016/`。CPU初回・再ロード込みの時間であり、実機NPU性能の予測には使わない。

APIの一次資料: https://developers.google.com/edge/litert-lm/python
