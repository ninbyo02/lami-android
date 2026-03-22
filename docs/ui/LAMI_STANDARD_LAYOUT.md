# Lami Standard Layout Guideline

## Purpose

このガイドラインは、Lami における画面密度・余白・Insets 設計の標準をそろえるための文書です。
特に Settings 系画面では、Jetpack Compose の標準余白をそのまま採用するのではなく、Lami 実機で調整した「やや詰め気味・高密度」のバランスを標準として扱います。

本ガイドの目的は、すべての画面を画一化することではなく、標準から外れる判断をレビュー時に説明しやすくすることです。

## Non-goals

- 全画面のレイアウトを強制的に統一すること
- 特殊画面や演出重視画面の自由度を奪うこと
- 既存実装を機械的に一律置換すること

## Core philosophy

- Compose 標準よりも、Lami の実機調整結果を優先する
- 情報密度と視認性を両立する
- 余白は「多ければ安全」ではなく、「責務が明確で過不足がない」ことを重視する
- Insets は見た目調整のためではなく、安全領域の責務分離として扱う

## Settings系標準

Settings 系画面では、原則として以下を標準パターンとします。

### Scaffold

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
    }
) { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
    ) {
        // content
    }
}
```

### Standard rule

- `contentWindowInsets = WindowInsets(0, 0, 0, 0)` を基準とする
- `TopAppBar` も `windowInsets = WindowInsets(0, 0, 0, 0)` を基準とする
- content 側で `padding(innerPadding)` と `consumeWindowInsets(innerPadding)` をセットで扱う
- Insets の責務は 1 か所に寄せる
- Settings 系では Compose 標準より少し高密度な見え方を許容し、Lami の既存バランスを優先する

## Insetsルール

### 原則

- Insets の二重吸収を禁止する
- 同じ safe area を複数箇所で処理しない
- 「なんとなく余白が足りないので Insets を足す」という調整をしない

### 禁止事項

- `statusBarsPadding()` を Settings 系標準の逃げ道として使うこと
- `WindowInsets.statusBars` を追加で直接参照して帳尻合わせすること
- `TopAppBar` と content の両方で上端 inset を処理すること
- Scaffold の `innerPadding` を適用した後、別レイヤーで同じ Insets を再度足すこと

### 判断基準

以下のいずれかに当てはまる場合は、二重吸収を疑います。

- TopAppBar の下に不自然な空きが出る
- 端末差で上余白だけ急に増える
- 調整のために `statusBarsPadding()` を追加したくなる
- `padding(top = ...)` で上端の見た目だけ合わせたくなる

## Spacing方針

### 基本方針

- Compose 標準よりやや詰める
- ただし、可読性・タップ性・情報のまとまりを壊さない範囲にとどめる
- 余白はデザイン意図を持って置く

### 禁止・非推奨

- `Spacer(modifier = Modifier.height(0.dp))` など、意味のない 0dp Spacer を残さない
- 「見た目の帳尻合わせ」のためだけの top padding を追加しない
- 上端余白のずれを `padding(top = x.dp)` で局所修正しない

### 推奨

- 余白はセクション境界・情報グルーピング・操作領域のために使う
- 不要な 1 段分の空きより、一覧性を優先する
- 設定画面では「やや詰め気味」を標準とし、説明可能な理由があるときのみ広げる

## 例外

以下は本ガイドの標準から外れてよい代表例です。

### ChatScreen

Chat 系画面は会話体験・ヘッダー表現・入力欄との関係が強く、Settings 系と同じ密度に縛らない。

### immersive UI

没入感を優先する画面では、safe area や余白を演出上あえて別管理してよい。

### editor系

編集 UI はハンドル・キャンバス・ツールバー・プレビューなど複数責務が混在するため、操作性を優先して独自レイアウトを採用してよい。

## 標準から外れるときの説明テンプレート

標準から外れる場合は、レビューコメントや PR 説明で次の 3 点を明記することを推奨します。

1. なぜ標準の Insets / spacing では不足するのか
2. どの責務をどこで処理するのか
3. その変更でどの画面価値（可読性、操作性、演出、編集効率など）が上がるのか

## Review checklist

レビュー時は最低限、以下を確認します。

- Insets の責務が 1 か所に集約されているか
- 無駄な余白が増えていないか
- `0.dp` のためだけの Spacer や調整コードが残っていないか
- `statusBarsPadding()` や `WindowInsets.statusBars` が場当たり的に追加されていないか
- `padding(innerPadding)` と `consumeWindowInsets(innerPadding)` の責務が崩れていないか
- 標準から外れる場合、その理由を説明できるか

## Notes

このガイドは「絶対ルール集」ではなく、Lami の実装とレビュー判断をそろえるための基準です。
迷った場合は Compose の一般論よりも、Lami 実機での見え方・既存画面との整合・責務の明快さを優先してください。
