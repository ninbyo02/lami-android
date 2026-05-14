# LAMI Android

[English README](README.md)

LAMI（ラミィ）は、Edge AI、LiteRT、ローカル推論、表情豊かなスプライトキャラクター、将来的な共有可能AIパーソナリティを重視する Android-first なローカルAIアシスタントプラットフォームです。

## 概要

LAMI は、Android端末上でローカルAIアシスタント体験を作るためのAndroidアプリプロジェクトです。AndroidネイティブのチャットUI、ローカル推論実験、キャラクターUI、音声インタラクション、開発者向け診断、将来的な共有可能AIパーソナリティ形式を主な対象にしています。

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

## Why LAMI?

LAMIは次の方向性を重視しています。

- **Local-first:** 可能な範囲で、端末上またはユーザーが管理できる推論経路を優先します。
- **Android-first:** Androidを主な実行環境として扱い、単なるデスクトップ向けサービスの薄いクライアントにはしません。
- **Character-oriented UI:** 表情豊かなスプライトキャラクターとアシスタントの個性をプロダクトの方向性として維持します。
- **Experimental Edge AI direction:** LiteRT、MediaPipe系ローカルLLM API、tokenizerメトリクス、モバイルアクセラレータ経路を検証します。
- **Privacy-conscious direction:** ローカル推論と診断を重視し、不要なデータ移動を減らす設計を目指します。
- **Future AI personality sharing:** スプライトやパーソナリティの概念を、将来的に端末間で共有できる形へ発展させます。

## Edge AI / Local Inference

### Current

- Ollamaバックエンド対応
- Androidローカル推論実験
- LiteRT / MediaPipe探索
- ストリーミング応答UI
- 推論診断と開発者向け統計

### Future / Experimental

- Qualcomm QNN delegate研究
- NPUアクセラレーション実験
- ローカルASR連携
- 共有可能なAIパーソナリティ

これらは研究・統合作業中の領域です。このREADMEだけを根拠に、QNN/NPUアクセラレーション、オフラインだけで完結する動作、安定済みのローカル推論挙動を前提にしないでください。

## Architecture Overview

```text
Android UI (Jetpack Compose, sprite character UI, TTS)
  |
  v
Backend abstraction and runtime selection
  |
  +-- Ollama backend (available)
  |
  +-- LiteRT / MediaPipe local inference path (experimental)
  |
  +-- Future local inference backends (planned / experimental)
        |
        v
      Future QNN / NPU acceleration direction (planned / experimental)
```

現在の構成では、Android UIとバックエンド側の作業を分けています。これにより、Ollama連携、ローカル推論実験、診断、将来的なアクセラレータ経路を、アプリ全体をOllama専用クライアントにせず発展させやすくしています。

## アーキテクチャ / バックエンド

### Ollamaバックエンド

Ollamaバックエンドは、Ollama互換のモデルサービスを利用しているユーザー向けの連携経路です。LAMIではこれを製品全体の中心ではなく、複数あるバックエンド選択肢の1つとして扱います。

### Local LiteRT / MediaPipeバックエンド

ローカル推論対応はExperimentalです。リポジトリには、LiteRT-LM / MediaPipe系のプローブ、エンジンライフサイクル、ストリーミング試行、ローカル推論統計のための実装が含まれています。モデル互換性、実行時挙動、性能は継続的な開発対象です。

### Qualcomm QNN / NPU経路

QNN / NPU対応はPlannedであり、現状は診断レベルのExperimentalな取り組みです。現在の作業はreadinessチェック、ネイティブライブラリ検出、フォールバック挙動、ドキュメント整備が中心です。NPUアクセラレーションが利用可能またはデフォルトで有効であるとは扱わないでください。

### ASR、TTS、パーソナリティ

TTSは利用可能です。ASR連携、より豊かなスプライト編集、QR共有、共有可能なローカルAIパーソナリティ形式は今後の方向性です。

## Supported / Tested Devices

| Device | Status | Notes |
|---|---|---|
| Nubia Z70S Ultra | Experimental | Snapdragon Edge AI experiments |
| Android Emulator | Supported | Development and testing |

Androidローカル推論、LiteRT / MediaPipe挙動、アクセラレータ診断に関するdevice reportを歓迎します。

## スクリーンショット

現在のLAMI Android UIに合わせたスクリーンショットへ更新予定です。

予定しているスクリーンショット:

- チャット画面
- ローカル推論統計
- TTS / 音声インタラクション
- スプライトキャラクターエディタ
- 開発者向け診断

<!-- TODO: 現在のLAMI Android UIのスクリーンショットが用意できたら追加する。 -->

## Project Direction

- Android-native AI experience
- Local-first AI workflows
- Character + AI integration
- Edge AI and local inference experiments
- Shareable personality direction
- Developer diagnostics for mobile AI runtimes

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

このリポジトリには、ローカルでのupdate、build、install、test、publishを補助する single-developer helper script として `update.sh` も含まれています。利用は任意で、実行前に内容を確認してください。

```bash
./update.sh
./update.sh publish -m "docs: update README"
```

## ドキュメント

- `docs/ui/LAMI_STANDARD_LAYOUT.md`: LAMI UIの密度、余白、inset設計ガイド。
- `docs/qualcomm-qnn-npu-setup.md`: 現在のQNN / NPUセットアップメモと制限事項。

## コミュニティ

Issues、Discussions、feature requests、device reportsを歓迎します。

特に次のような報告が役立ちます。

- Android端末とOSバージョン
- Ollamaバックエンドの挙動
- LiteRT / MediaPipeローカル推論の挙動
- TTS / ASRへの期待
- QNN / NPU診断結果
- スプライトキャラクターやパーソナリティ共有のアイデア

## Attribution

LAMIは独自のAndroidプロジェクトですが、リポジトリ履歴およびnoticeには以前のOllama Android client由来の作業への言及があります。現在のattributionの詳細は `NOTICE` を参照してください。このREADMEは、アプリをOllama専用クライアントとしてではなく、現在のLAMIの方向性として説明するためのものです。

## ライセンス

このプロジェクトには、Apache License, Version 2.0 を含む `LICENSE` ファイルがあります。

MITライセンスのupstream materialを含む第三者attributionについては `NOTICE` を参照してください。

<!--
Suggested GitHub Topics:
edge-ai, local-llm, litert, mediapipe, android-ai, on-device-ai,
ai-assistant, sprite-animation, offline-ai
-->
