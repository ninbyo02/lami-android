# 通常チャットでの実機なしCPU推論開発

2026-10-16。debug版の通常LOCALチャットに明示的なPC CPU reference経路を追加。

## 有効化
前提はemulator_cpu_reference_ja.mdのビルド・エミュレータ起動・インストールと、PCのOllamaに保存済みモデルがあること。

```sh
/home/sato/Android/Sdk/platform-tools/adb -s emulator-5580 shell am start -n io.github.ninbyo02.lami.assistantprobe/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity --ez enable_main_chat true --es model sam860/LFM2:350m
```

開いた通常チャットのLOCALモードで入力・送信する。専用CPU画面の「通常チャットでCPU開発」ボタンからも有効化可能。保存モデル名はPC側の名前。端末側のNPU/汎用モデルスロット設定は書き換えない。

無効化:
```sh
/home/sato/Android/Sdk/platform-tools/adb -s emulator-5580 shell am start -n io.github.ninbyo02.lami.assistantprobe/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity --ez disable_main_chat true
```

切替時はエミュレータ内のActivityタスクを再起動する。Roomの履歴は保持。実機へこれらのコマンドを実行しない。

## 接続と制約
- debug版のranchu/goldfishかつx86/x86_64で、設定を明示的に有効化した時だけ接続する。
- release側の実装は常に無効で、ネットワーク推論を持たない。
- NPUネイティブ経路には入らず、通常LOCAL処理の共通表示・保存経路へ結果を返す。表示モデル名はPC CPU referenceを含む。
- 直近最大12件の会話履歴を渡す。画像履歴は未対応として拒否する。
- 接続先は固定の10.0.2.2:11434。num_gpu=0、keep_alive=0、出力上限128トークン。
- OkHttpのストリームから実際のデルタを返す。キャンセルでCallを閉じ、途中の応答を成功にはしない。
- PC接続失敗時の自動NPU切替・保存応答への置換は行わない。
- 収集レポートはfiles/emulator_main_chat_reference.json。これは推論の結果であり、Roomへの最終保存は通常チャット側の処理で行う。
- CPU用モデルの意味的応答を使う開発経路。NPU専用バイナリのエミュレーション、数値一致、NPU性能やSIGABRT原因の検証ではない。

## 検証結果
Android x86_64エミュレータの通常チャット画面で確認。
- 17＋25→42（860ms、8チャンク）。続けて「その結果を2倍に」→84（719ms、12チャンク）。2件の過去メッセージを渡している。
- ユーザー/応答計4件をCOMPLETEDとしてRoomへ保存。プロセスを止めて再起動後、チャット一覧から開き直して内容を確認。
- 通常チャットの停止ボタンで240ms時点の要求を中断。続くGemma4 E4Bの要求で再生成成功。
- 存在しないモデルのHTTP404は失敗として表示。無効化・再有効化後の別要求も成功。
- debug APKビルドとrelease Kotlinコンパイル成功。releaseのクラスを逆アセンブルし、無効固定かつ通信実装なしを確認。release manifestにも開発Activityがない。
- 試験後のOllama常駐モデル数0。スマホへのインストール・推論は未実施。

結果JSONと保存メッセージの抜粋は `docs/reviews/emulator-main-chat-20261016/`。計測時間はPC CPU経路の観測で、実機NPU性能の予測ではない。

## 通常画面の一括検証
最新版debug APKをエミュレータへインストールしてから実行:

```sh
python3 scripts/voice/check_emulator_main_chat.py --serial emulator-5580 --output /tmp/emulator-main-chat-check.json
```

実行対象は起動済みエミュレータとインストール済みdebugパッケージ。`--adb`、`--package`、`--model` で環境を指定する。既定モデルはPCの `sam860/LFM2:350m`。UI入力自動化では固定の英語2入力を使う。

このコマンドはエミュレータでCPU開発を有効化し、新しい会話を1件作成する。アプリデータや既存履歴は消さない。通常画面で2ターン入力・生成し、表示と応答が一致することを確認する。最後に対象のdebugアプリだけ停止し、DBとWALを一時コピーして、4件すべてがCOMPLETEDで内容・実行元が一致することを調べてからアプリを起動し直す。起動後の履歴選択操作はこのランナーの対象外（前節の手動確認では実施済み）。CPU開発設定は有効のまま残る。

要求ごとにUUIDと入力SHA-256を記録し、モデル名とともに照合して前回の結果を除外する。JSONは一時ファイルからrenameして公開し、読み取り途中のJSONを避ける。結果欠落・推論失敗・保存不一致は終了コード1と結果JSON、すべて合格なら0。生成文の数学的正しさやNPUとの数値一致を判定するテストではない。

スマホのserialを渡すと、端末への操作を実行する前に拒否して失敗JSONを残す。

一括ランナーの実行結果: repeatable-suite.json（合格）。要求IDの異なる2件を照合し、4件の保存と再起動が成功。スマホ指定を拒否する負例はphysical-target-refused.json（期待どおりvalidation=failed、操作なし）。結果はいずれも同じレビュー用ディレクトリに保存。

## E2B .litertlm を直接使うCPU経路
汎用Gemma 4 E2BをLiteRT-LM CPUで実行する経路を追加。モデルSHA256とランタイム版もAndroidへ返す。手順と互換性の制約はlitert_host_e2b_reference_ja.mdを参照。
