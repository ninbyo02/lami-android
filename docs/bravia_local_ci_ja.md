# PR #2766 BRAVIA tool のローカル CI

対象は [PR #2766](https://github.com/ninbyo02/lami-android/pull/2766)、
`feature/bravia-tv-tool-20261104`、commit `0ca566c055215ba063a2b591d6fe099b8c28cdea`。
2026-11-04 UTC の GET では draft / open、base は `feature/lami-voice-engine-clean`。
PR #2767 の control 実装とは別のコードで、TV テストの package も異なる。

本変更はローカルrunnerとTV2branchのpush除外を含む。
手動CIとmain等へのPR検証は維持し、過去artifact/cache削除、端末・TV・ADB操作は行わない。

## 原本の保全

- 原本：`/home/sato/project/lami-android-bravia-20261104`。
- 開始時点の未コミット・未追跡変更は0。Git 管理対象3,506件の SHA-256 を記録した。
- 無視対象5,864ファイル、うち native `.so` 135件は原本に保持。既存ビルド、JNI、local.properties をコピー・変更しない。
- 独立 clone：`/home/sato/Documents/Codex/2026-11-05/task-2/lami-bravia-tool-local-ci`。
- clone は `--no-hardlinks --single-branch --no-tags`。共有 object の alternates と push 用 remote は持たない。
- ローカル branch：`agent/bravia-tool-local-ci-20261105`。
- 原本の記録：`artifacts/local-ci-preflight/original-snapshot.json`、`tracked-sha256.json`。

最近のタスク一覧で別の TV CI 移行担当は見つからず、既存 TV 担当会話は idle だった。
適用親・対象 checkout に AGENTS.md / `.agents/skills` は見つからなかった。
既読のローカル Codex memory は0件。PR #2767 の成功済み検証を再実行せず、その runner の方式を再利用した。

## 実行手順

通常ホストで、既存 JDK / Android SDK / Gradle cache を使う。
Python 3.11以上と JDK 21 が必要。

```bash
cd /home/sato/Documents/Codex/2026-11-05/task-2/lami-bravia-tool-local-ci
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/home/sato/Android/Sdk
export GRADLE_USER_HOME=/home/sato/.gradle

# ファイルを作らず、予定コマンドを確認
python3 scripts/run_bravia_local_ci.py --profile all --plan

# TV の19件だけを実行（コンパイルにはAndroid SDKを使う）
python3 scripts/run_bravia_local_ci.py --profile tv

# 全 debug / release の検証
python3 scripts/run_bravia_local_ci.py --profile debug
python3 scripts/run_bravia_local_ci.py --profile release
```

| profile | 検証範囲 |
| --- | --- |
| guards | native binary 追跡防止、NPU artifact wiring、release suite の静的検証、A/B の合成データ検証 |
| tv | guards と `:app:testStandardDebugUnitTest --rerun --tests 'io.github.ninbyo02.lami.tv.*'` |
| debug | guards、全 standardDebug unit test、lint、非NPU smoke APK |
| release | 全 standardRelease unit test、lint、非NPU APK/AAB、packaging と16 KiB zipalign |
| all | tv、debug、release を順に実行。最初の失敗で停止 |

`tv` と `debug` のテスト直後に新しい JUnit XML を検査し、
`BraviaControlTest` 15件と `BraviaIntentParserTest` 4件、合計19件・失敗/エラー/skip 0を必須とする。
件数、クラス、テスト名、更新時刻を確認するため、古い結果や空実行は成功扱いにしない。
`--rerun` は Test task だけを再実行し、依存する全ビルドタスクを再実行するものではない。
TV テストは `testStandardDebug` にあるため、release profile の全件数には含まれない。

## 既存ローカル運用の再利用

`/home/sato/lami-tts-daemon/controller.py` と PR #2767 の検証に合わせ、
Gradle offline、worker 2、Kotlin in-process、heap 3072 MiB / metaspace 768 MiB を使用する。
wrapper 設定の Gradle 8.13 と一致する、既存 cache 内の `bin/gradle` を直接実行し、wrapper download を避ける。
Android SDK 36 / build-tools 36.0.0、debug系では API 34 NDK clang++ も事前確認する。
不足時は SDK 導入、ライセンス同意、Docker fallback、vendor runtime コピーを行わず停止する。

Robolectric は Gradle offline と別の Maven resolver を持つため、
`scripts/bravia-local-ci.init.gradle` で offline と既存 SDK 34 JAR の参照を強制する。
既定入力は `~/.m2/repository/org/robolectric/android-all-instrumented/14-robolectric-10818077-i6/`。
run 内にはシンボリックリンクを作り、JAR の SHA-256 を記録する。
`--robolectric-cache`、`--gradle-home`、`--output-root` で各保存場所を指定できる。

debug lint / assemble は `lami.allowMissingQairt244Jni=true`、
release は `lami.standardNpuRuntimeEnabled=false` を明示する。
これらは非NPU検証であり、NPU昇格や実機動作の証拠には使えない。

## 禁止する操作と CI との差分

TV のクライアントテストは MockWebServer の localhost にだけ接続する。
実際の TV のIP、SSDP検索、PIN登録、スマホ、ADB、音声再生には接続しない。
PC の登録ヘルパー `scripts/bravia_tv_setup.py` も実行しない。

GitHub CI にある次の2本は、端末・ADB試験禁止に従って実行しない。

- `test_npu_conversation_policy_device_validation.sh`
- `test_npu_conversation_policy_device_summary.sh`（fake adb を使う模擬試験）

除外は各 `summary.json` に記録する。tokenizer CI は今回の TV 差分の対象外。
`all` が成功しても「GitHub workflow の全項目と完全同等」や「実機検証済み」とは扱わない。

## 保存先

`artifacts/bravia-local-ci/<UTC時刻>-<PID>/` に、実行コマンド、終了コード、入力ハッシュ、
作業状態、ログ、TV XML の検証結果、JUnit / lint レポート、出力の SHA-256 を保存する。
同じ checkout の重複起動は `.lock` で停止し、他の checkout のジョブを止めない。
過去の結果の自動削除や、GitHub への自動アップロードは行わない。

APK / AAB は `app/build/outputs/` に置き、run 内への二重保存を避ける。
今回更新されていない出力は、今回生成のものとして `outputs.json` に記録しない。
Room schema 13 / 14 の存在と、schema が変更されていないことを開始・終了時に確認する。

## 検証記録

runner の Python 単体テスト18件が成功。
証跡は `artifacts/local-ci-preflight/runner-tests.json` と `runner-tests.log`。

通常ホストの debug profile は全1,683件（148 suites、失敗・エラー・skip 0）成功。
この中に TV 19件（BraviaControlTest 15件、BraviaIntentParserTest 4件）を含み、XML の更新時刻・実行件数の検査も成功した。
debug lint と非NPU APK 作成も成功。
証跡：`artifacts/bravia-local-ci/20261104T233632Z-72445/summary.json`、同ディレクトリの `reports/` と `outputs.json`。
APK：`app/build/outputs/apk/standard/debug/app-standard-debug.apk`、207,479,889 bytes、
SHA-256 `32dd11cbddf3e0b22e36064e9e015f126dacc068ced006e656e3ed0353d91079`。

通常ホストの release profile は全1,147件（120 suites、失敗・エラー・skip 0）成功。
release lint、APK / AAB 作成、両パッケージの vendor runtime 混入防止検査、APK の16 KiB zipalign も成功。
証跡：`artifacts/bravia-local-ci/20261104T234203Z-81340/summary.json`、同ディレクトリの検査ログ・`reports/`・`outputs.json`。
unsigned APK：`app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk`、230,226,288 bytes、
SHA-256 `730f6edfc918977dcc110d6fd870d93cb9dae56da89d7d6f228ff355ca49fd08`。
AAB：`app/build/outputs/bundle/standardRelease/app-standard-release.aab`、91,738,757 bytes、
SHA-256 `2094d2f373b390d0ed5acda5095089b96e753fcf5be1338cdfeef63ac2ffc991`。

PR #2767 の既存検証（TV14 / debug1,678 / release1,161）は再実行せず、そのまま保持した。
スマホ・TV・ADB・音声・実機 NPU の検証は未実施。

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
