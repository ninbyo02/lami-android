# BRAVIA のローカル CI

PR [#2767](https://github.com/ninbyo02/lami-android/pull/2767) の TV 連携を、既存のホスト運用に合わせてローカルで検証する。
本変更はローカルrunnerとTV2branchのpush除外を含む。手動CIとmain等へのPR検証は維持する。過去artifact/cacheの削除は含まない。

## 対象と隔離

2026-11-04 UTC の GitHub 読み取りでは、PR #2767 は draft / open、head は
`feature/bravia-tv-control-20261104`、commit は
`9ef90b7e08f71e830e5685845467e27aa39a4d9e`、base は `feature/lami-voice-engine-clean`。
元の作業ツリーは `/home/sato/project/lami-bravia-20261104`。
依頼時に挙がった `feature/bravia-tv-tool-20261104` は、別の
`/home/sato/project/lami-android-bravia-20261104` にあり、[PR #2766](https://github.com/ninbyo02/lami-android/pull/2766) に対応する。
今回のスクリプトは PR #2767 の control 実装用で、tool 実装には未対応。

両方の元作業ツリーは調査時に未コミット・未追跡変更なし。
TV 担当会話「赤外線送受信調査」は idle、最新報告は登録成功・14件成功・スマホ反映保留。
最近のタスク一覧に、今回以外の TV CI 移行担当は見つからなかった。
TTS の作業は別途進行中なので、端末・TV・ADB には触れない。

今回の変更先は次の独立 Git clone。共有 object や元リポジトリへの書き込みを避け、push 先 remote も設定していない。

```text
/home/sato/Documents/Codex/2026-11-05/task-2/lami-bravia-local-ci
branch: agent/bravia-local-ci-20261105
base:   9ef90b7e08f71e830e5685845467e27aa39a4d9e
```

今後も他担当が利用中の checkout ではなく、レビュー対象 commit から作った専用 clone で実行する。
同じ checkout での重複起動は `artifacts/bravia-local-ci/.lock` により停止する。
他 checkout や TTS controller のジョブを停止する機能は持たない。

## 引き継いだ方式

`/home/sato/lami-tts-daemon/controller.py` の既存検証と同じく、オフライン、JDK 21、
Gradle worker 2、Kotlin in-process、Gradle heap 3072 MiB / metaspace 768 MiB を使う。
既存ホストキャッシュは通常 `/home/sato/.gradle`。キャッシュを変更できる通常ホストで Android の検証を行う。
ソースの CI 正本は `.github/workflows/ci.yml`。本変更でTV2branchのpushだけを対象外にし、PRと手動の検証jobは維持する。
適用親の AGENTS.md と対象 checkout の `.agents/skills` は見つからず、ローカル Codex memory は0件だった。
`lami-ci-bootstrap.sh` は別リポジトリの runner 登録用なので使用しない。

## 実行

Python 3.11 以上、JDK 21、Git、Bash が必要。
最初に `--plan` で予定コマンドを読み取り確認できる。`--plan` は出力ディレクトリも作成しない。

```bash
cd /home/sato/Documents/Codex/2026-11-05/task-2/lami-bravia-local-ci

# JVM 14件と端末を使わない既存チェック4本
python3 scripts/run_bravia_local_ci.py --profile tv --plan
python3 scripts/run_bravia_local_ci.py --profile tv

# 端末を使わない既存チェック4本だけ
python3 scripts/run_bravia_local_ci.py --profile guards

# Android の通常ホスト検証。既存SDK・依存キャッシュのみ使用する
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/home/sato/Android/Sdk
export GRADLE_USER_HOME=/home/sato/.gradle
python3 scripts/run_bravia_local_ci.py --profile debug --plan
python3 scripts/run_bravia_local_ci.py --profile debug
python3 scripts/run_bravia_local_ci.py --profile release

# guards / JVM / debug / release を順に行う
python3 scripts/run_bravia_local_ci.py --profile all
```

`--gradle-home PATH` と `--output-root PATH` で場所を指定できる。
全プロファイルが Room schema 13・14の存在と schema の未変更を検証する。
途中の失敗は後続処理を止め、`summary.json` を失敗として保存する。

| profile | 実行する検証 |
| --- | --- |
| guards | native binary 追跡防止、NPU artifact wiring、release suite 静的検証、A/B の合成データ検証 |
| tv | guards と BraviaCommand / Discovery / Protocol の JVM 14件 |
| debug | guards、standardDebug 全 unit test、lint、非NPU smoke APK |
| release | standardRelease 全 unit test、lint、非NPU APK / AAB、packaging、16 KiB zipalign、SHA-256 / native library 集計 |
| all | 上記を順次実行。最初の失敗で停止 |

Android プロファイルは wrapper 設定の Gradle 8.13 に一致する、既存キャッシュ内の `bin/gradle` を直接使う。
`--offline` は wrapper のダウンロードを止めないため、wrapper の起動を避けている。
Android SDK 36・build-tools 36.0.0 の存在を事前確認し、SDK 自動取得も無効にする。
release は既存の `cmdline-tools/latest/bin/apkanalyzer` が必要。
この環境の既定 `ANDROID_SDK_ROOT=/usr/lib/android-sdk` は build-tools 36.0.0 が不足している。
上のコマンドどおり `/home/sato/Android/Sdk` を明示すれば既存SDKの事前確認が通る。新規インストールは不要。
debug の lint / assemble は workflow と同じ `lami.allowMissingQairt244Jni=true`、
release は `lami.standardNpuRuntimeEnabled=false` を明示する。NPU 昇格の証拠には使えない。
キャッシュやSDKが不足した場合、取得・ライセンス同意・vendor runtime コピーはせず停止する。

Gradle の `--offline` とは別に、Robolectric にも `robolectric.offline=true` を適用する。
`scripts/bravia-local-ci.init.gradle` が、既存 SDK 34 instrumented JAR を置いたディレクトリを各 Test JVM に渡す。
既定入力は `~/.m2/repository/org/robolectric/android-all-instrumented/14-robolectric-10818077-i6/`。
`--robolectric-cache PATH` でこのバージョン別ディレクトリの親を変更できる。
JAR のコピーはせず run 内にシンボリックリンクを作り、入力の SHA-256 を記録する。
設定が後から上書きされた場合もテスト起動前に停止する。
debug ビルドには既存SDK内の NDK API 34 clang++ も必須とし、Docker image の取得を伴い得るフォールバックを防止する。

## JVM プロファイルの範囲

`scripts/run_bravia_jvm_tests.py` は Gradle を起動せず、既存キャッシュから固定 jar を読み取る。
Kotlin 2.3.0 compiler、JUnit 4.13.2、Gson 2.13.2、OkHttp 4.12.0、MockWebServer 4.9.3、
Okio JVM 3.8.0 等を明示し、jar とソースの SHA-256 を記録する。
app の OkHttp 宣言は4.9.3だが、既存の解決済みモデルでは4.12.0に昇格しているため、そのホスト用 profile を固定している。
これは Gradle による依存解決・Android 全体のコンパイルの代替証明にはならない。
必要な jar がなければ、別バージョンへの置換やネットワーク取得をせず停止する。
ソース・宣言・テスト件数が変わった場合は profile をレビューして更新する。

テストは fake transport と XML サンプルが中心。HTTP テスト1件だけ `127.0.0.1` の MockWebServer を使う。
TV 検索・TV のIPへの通信・登録・操作、ADB、スマートフォンへのインストールは実行しない。
ソケットを許可しない sandbox では14件完全成功にならない。制限を回避して再実行せず、結果を blocked として扱う。

## GitHub CI との意図的な差分

今回の端末・ADB試験禁止に従い、workflow にある次の2本は実行しない。
`summary.json` にも除外を記録する。

- `test_npu_conversation_policy_device_validation.sh`：device runner の引数・起動の試験。
- `test_npu_conversation_policy_device_summary.sh`：fake adb を使う模擬試験。

このため `all` の成功でも「GitHub workflow 全項目と同等」や「端末検証済み」とは扱わない。
tokenizer CI は TV 差分に含まれず、今回の runner の対象外。

## 保存と確認

通常の出力先は `artifacts/bravia-local-ci/<UTC時刻>-<PID>/`。
コマンド・終了コード・ログ・実行時間は `summary.json`、作業状態は `working-tree.txt`、
今回更新された Android 出力のサイズ・SHA-256 は `outputs.json` に残す。
テスト・lint のレポートは実行ディレクトリへコピーする。
APK / AAB は `app/build/outputs/` に残し、大型ファイルの二重保存を避ける。
Gradle が UP-TO-DATE とした過去の出力は今回生成のものとして記録しない。
実行ログは残すため、この場合もタスクの結果を追跡できる。

`artifacts/` と `app/build/` は既存の ignore 対象。自動アップロード・自動削除・定期実行はない。
不要な過去の run を確認してローカルで整理する運用とし、このスクリプトは過去の結果を消さない。

## 今回の検証結果

- runner の Python 単体テスト：16件成功（CI runner 11件、JVM runner 5件）。
- guards：既存チェック4本と Room schema guard が成功。
- TV の6 Kotlinファイル：コンパイル成功。継続指示後、通常ホストで JUnit **14件すべて成功**。
- 初回 sandbox では14件中13件成功、1件 `httpTransportReadsSmallBodiesAndDoesNotFollowRedirects` は
  ServerSocket の禁止により `java.net.SocketException: Operation not permitted` となった。
  初回は停止し、その後の継続指示と通常ホスト実行の承認を受けて確認した。
- 初回の未加工証跡は `artifacts/bravia-local-ci-jvm-20261105-first/{result.json,compile.log,junit.log,java-version.log}`。
  初回 `result.json` は改善前の集計コードによる `failed` のまま保存。後に集計部を改善し、同じログの解析を単体テストで確認した。
- 通常ホストの TV 成功証跡：`artifacts/bravia-local-ci/20261104T230734Z-1014874/jvm/result.json`。
  同 run の Android 部分は Robolectric の自動取得を防ぐ設定の追加前に手動停止しており、CI 全体成功の証跡ではない。
- offline init の Gradle Test task への設定確認は、端末を使わない小さな fixture の `verifyLocalCi` で成功。
  証跡：`artifacts/bravia-local-ci-final-verification/init-check.log`。
- 通常ホストの debug profile：全 unit test **1,678件成功**（149 suites、失敗・エラー・skip 0）、lint 成功、非NPU APK 作成成功。
  証跡：`artifacts/bravia-local-ci/20261104T231035Z-3182884/summary.json`、同ディレクトリの `outputs.json` と `reports/`。
  APK は `app/build/outputs/apk/standard/debug/app-standard-debug.apk`、207,425,568 bytes、
  SHA-256 `1694c9a4b73f85840f835c318c6d8364590faf58c22d1c08e3924dfed45d481f`。
- 通常ホストの release profile：全 unit test **1,161件成功**（123 suites、失敗・エラー・skip 0）、lint、APK / AAB 作成、
  APK / AAB 双方の vendor runtime 混入防止検査、APK の16 KiB zipalign がすべて成功。
  証跡：`artifacts/bravia-local-ci/20261104T231642Z-48110/summary.json`、同ディレクトリの検査ログ・`outputs.json`・`reports/`。
  unsigned APK は `app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk`、230,177,064 bytes、
  SHA-256 `0317622eb3adb146d9897381ad752559c25c992a79e5602a30de67645b7e72bd`。
  AAB は `app/build/outputs/bundle/standardRelease/app-standard-release.aab`、91,719,981 bytes、
  SHA-256 `edd5d15b3ce10d8f1b3ae35ad416886591541d2022d01e29bdae100543b605bc`。
- `/home/sato/Android/Sdk` の Android 36 / build-tools 36.0.0 / apkanalyzer と、キャッシュ内 Gradle 8.13 は読み取りによる事前確認成功。
- 上記のローカル準備時点ではTV・スマホ操作、ADB試験、runner登録、GitHub変更は実施していない。今回のworkflow差分は次節のとおり。

## GitHub CIとの分担

`.github/workflows/ci.yml` は次の2つの完全一致branchだけをpush対象から除外する。

- `feature/bravia-tv-tool-20261104`（PR #2766）
- `feature/bravia-tv-control-20261104`（PR #2767）

変更ごとの通常検証は、対象SHAの専用checkoutで上記ローカルrunnerを明示実行する。
新しい常駐serviceやGitHub self-hosted runnerは登録しない。2つのTV checkoutは順番に実行し、
他checkoutやTTS controllerの処理・lock・設定を変更しない。

GitHubの手動 `workflow_dispatch` は従来どおり検証jobを実行する。
TVの完全一致ref、またはこのrepositoryのTV headからのPRではartifact uploadだけを省略する。
forkの同名head、他branch・他workflowにはこの除外を広げない。
`pull_request` のbase filterは既存の `main` / `future` / `stable` のまま。
現在のbase `feature/lami-voice-engine-clean` はその対象外で、2026-11-05 UTCのGET時点で必須checkはない。
将来main等へのPRでは `Standard debug verification` / `Standard release verification` を実行し、
TV headという理由でjobをskipしない。mainの必須名と期待App15368は変更しない。
ローカル結果を同名statusとして投稿して必須checkの代わりにしない。

この変更で既存runはcancelせず、過去のGitHub artifact/cacheも削除しない。
大型artifactの過去分の容量は自動的には解放されない。
永久削除は保持対象・復旧用データ・明示ID一覧の確認後の別作業とし、現在は保全したままにする。
mainへ将来merge/rebaseする場合は、その時点のmainの手動upload既定false・保存3日の方針を維持する。
古いfeature workflow全体でmainの設定を上書きしない。

両PRのdocs・runner・runner testは同じ新規パスで異なる実装を持つ。
将来両アプリ実装を同じbaseへmergeするときは、その3パスのadd/add競合を解決し、
採用する実装に対応したTVテストを必須にする。今回、アプリ実装の統合は行わない。
