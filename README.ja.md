# LAMI Android

[English README](README.md)

LAMI（ラミィ）は、Edge AI、LiteRT、オフライン推論、共有可能なAIパーソナリティを重視する Android-first なローカルAIアシスタントです。

## 概要

LAMI は、Android端末上でローカルAIアシスタント体験を作るためのAndroidアプリプロジェクトです。AndroidネイティブのチャットUI、ローカル推論実験、音声インタラクション、開発者向け診断、将来的な共有可能AIパーソナリティ形式を主な対象にしています。

Ollama連携もサポート対象ですが、LAMIはOllama専用クライアントではありません。Ollamaはバックエンドの1つであり、LiteRT / MediaPipe系ローカルLLM APIや将来的なアクセラレータ実験と並ぶ選択肢として扱います。

パッケージ名:

```text
io.github.ninbyo02.lami
```

## 機能

### 実装済み / 利用可能

- Jetpack ComposeベースのAndroidチャットUI
- Ollamaバックエンド連携
- ストリーミング応答UI
- Android Text-to-Speech対応
- ローカル推論連携用のフックとプローブ
- 推論統計と開発者向け診断
- スプライトアニメーションの状態管理とデバッグ設定

### Experimental

- LiteRT / MediaPipe系ローカルLLMフロー
- ローカル推論エンジンの保持とライフサイクル最適化
- tokenizerベースのローカル推論統計
- 文単位のストリーミングTTS
- スプライトアニメーション / デバッグ設定ツール
- Qualcomm QNN / NPU readiness診断

### Planned

- Qualcomm QNN / NPUアクセラレーション経路
- LAMI ASR連携
- ユーザー向けスプライトキャラクターエディタ
- QRによるスプライト / パーソナリティ共有
- 共有可能なローカルAIパーソナリティ形式
- コントリビューションガイドと公開ドキュメントの整理

## スクリーンショット

現在のLAMI Android UIに合わせたスクリーンショットへ更新予定です。

予定しているスクリーンショット:

- チャット画面
- ローカル推論統計
- TTS / 音声インタラクション
- スプライトキャラクターエディタ
- 開発者向け診断

<!-- TODO: 現在のLAMI Android UIのスクリーンショットが用意できたら追加する。 -->

## Why LAMI?

LAMIは次の方向性を重視しています。

- **Local-first:** 可能な範囲で、端末上またはユーザーが管理できる推論経路を優先します。
- **Android-first:** Androidを主な実行環境として扱い、単なるデスクトップ向けサービスの薄いクライアントにはしません。
- **Offline-capable direction:** 常時ネットワーク接続を前提にせず、ローカルで動かせるワークフローを育てていきます。
- **Edge AI experimentation:** LiteRT、MediaPipe系ローカルLLM API、tokenizerメトリクス、モバイルアクセラレータ経路を検証します。
- **Privacy-conscious design:** ローカル推論と診断を重視し、不要なデータ移動を減らす設計を目指します。
- **AI personality sharing direction:** スプライトやパーソナリティの概念を、将来的に端末間で共有できる形へ発展させます。

## アーキテクチャ / バックエンド

### Ollamaバックエンド

Ollamaバックエンドは、Ollama互換のモデルサービスを利用しているユーザー向けの連携経路です。LAMIではこれを製品全体の中心ではなく、複数あるバックエンド選択肢の1つとして扱います。

### Local LiteRT / MediaPipeバックエンド

ローカル推論対応はExperimentalです。リポジトリには、LiteRT-LM / MediaPipe系のプローブ、エンジンライフサイクル、ストリーミング試行、ローカル推論統計のための実装が含まれています。モデル互換性、実行時挙動、性能は継続的な開発対象です。

### Qualcomm QNN / NPU経路

QNN / NPU対応はPlannedであり、現状は診断レベルのExperimentalな取り組みです。現在の作業はreadinessチェック、ネイティブライブラリ検出、フォールバック挙動、ドキュメント整備が中心です。NPUアクセラレーションが利用可能またはデフォルトで有効であるとは扱わないでください。

### ASR、TTS、パーソナリティ

TTSは利用可能です。ASR連携、より豊かなスプライト編集、QR共有、共有可能なローカルAIパーソナリティ形式は今後の方向性です。

## ロードマップ

- [ ] 古いスクリーンショットを現在のLAMI Android UIへ置き換える
- [ ] LiteRTローカル推論を安定化する
- [ ] ローカル推論統計を改善する
- [ ] QNN delegate / NPUアクセラレーション実験を追加する
- [ ] LAMI ASR連携を追加する
- [ ] ユーザー向けスプライトエディタを追加する
- [ ] QRによるスプライト / パーソナリティ共有形式を追加する
- [ ] 共有可能なローカルAIパーソナリティ形式を定義する
- [ ] コントリビューションガイドを用意する
- [ ] リリース手順と公開ドキュメントを整理する

## 開発

Android Studioでプロジェクトを開くか、リポジトリルートからGradle wrapperを使ってください。

現在のAndroid設定:

- Application ID: `io.github.ninbyo02.lami`
- Minimum SDK: 34
- Target SDK: 35
- Compile SDK: 35
- Java compatibility: 11

debug APKをビルド:

```bash
./gradlew :app:assembleDebug
```

ユニットテストを実行:

```bash
./gradlew test
```

このリポジトリには、ローカルでのupdate、build、install、testを補助する single-developer helper script として `update.sh` も含まれています。利用は任意で、実行前に内容を確認してください。

```bash
./update.sh
```

## ドキュメント

- `docs/ui/LAMI_STANDARD_LAYOUT.md`: LAMI UIの密度、余白、inset設計ガイド。
- `docs/qualcomm-qnn-npu-setup.md`: 現在のQNN / NPUセットアップメモと制限事項。

## Attribution

LAMIは独自のAndroidプロジェクトですが、リポジトリ履歴およびnoticeには以前のOllama Android client由来の作業への言及があります。現在のattributionの詳細は `NOTICE` を参照してください。このREADMEは、アプリをOllama専用クライアントとしてではなく、現在のLAMIの方向性として説明するためのものです。

## ライセンス

このプロジェクトには、Apache License, Version 2.0 を含む `LICENSE` ファイルがあります。

MITライセンスのupstream materialを含む第三者attributionについては `NOTICE` を参照してください。
