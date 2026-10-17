# 実機なしでのAndroid開発チェック

PCの実CPU推論とAndroidエミュレータを使う開発経路。スマホの接続は不要。
NPU版の回答品質の認定とは分けて判定する。

## 前提
- 起動済みx86_64 Androidエミュレータと、CPU参照機能を含むdebug APK（`io.github.ninbyo02.lami.assistantprobe`）。
- PCの汎用E2Bモデルと `litert-lm-api==0.18.0` の隔離環境。
- 実機用APK、NPU専用モデル、QAIRT SDKはこのチェックの実行には不要。

PCでCPUブリッジを起動する。既に11436で起動済みなら再起動不要。

```sh
python3 scripts/voice/serve_litert_host_cpu.py --model /home/sato/Downloads/gemma-4-E2B-it.litertlm --runtime-python /home/sato/lami-tts-daemon/host-litert-reference-20261016/venv/bin/python --port 11436
```

別ターミナルでリポジトリのルートから実行する。

```sh
python3 scripts/voice/run_phone_free_development.py --serial emulator-5580 --output-dir /tmp/lami-phone-free
```

`--adb` でADBのパスを変更可能。ポート11436、モデルalias `gemma4-e2b-litert-cpu`、debugパッケージは検証済みの組み合わせに固定。

## 判定
新しいUUIDのディレクトリに入力、実CPU回答、UI検証、総合結果を保存する。
1. エミュレータのserialと `ro.kernel.qemu=1` を確認。スマホserialはADB操作前に拒否する。
2. 日本語6ターンを実CPUで推論し、期待する回答と照合。実行成功と回答正否は別項目。
3. 通常画面で42→84の会話履歴、生成中の停止、停止後の7への回答を確認。
4. Roomの8メッセージ、COMPLETED/CANCELLED、CPU実行元、対象アプリの再起動を確認。

全項目成功のみ終了コード0。欠落・不正JSON・タイムアウト・誤答は成功扱いしない。
CPU回答が誤っていても、独立したUI検証は続ける。

対象debugアプリの停止・起動と会話追加を行う。データ消去はしない。CPU開発設定は有効のまま残る。
再起動後の履歴画面選択は自動化対象外。保存内容はRoomを照合する。

## 適用範囲
チャット、履歴、ストリーム応答、中断、復帰の開発に使える。
NPUバイナリの実行互換性、数値一致、速度、消費電力、メモリ、音声同時処理は別途検証が必要。
CPUはPC上で動作するため、PCなし・オフライン単体のAndroid開発環境ではない。

NPU比較の現状は `cpu_npu_identifier_context_ja.md` を参照。
トークナイザーデータの一致だけでは、重み・量子化・入力テンプレート・ランタイムの一致を意味しない。

## 2026-10-17の実行結果

`docs/reviews/phone-free-development-20261017/` に保存。日本語6ターン全問正答、通常画面の履歴・中断・復帰・Room保存・再起動が成功。スマホserial指定は操作前に拒否。スマホがADB一覧に存在していても、このチェックでは一切操作しない。
