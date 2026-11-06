# Web検索の同梱Agent Skill

LAMIの初版Web検索は、DuckDuckGoの検索結果を最大5件、タイトル・概要・出典URLとして返す。設定の「Web検索（DuckDuckGo）」は初期状態でオフ。有効にした後、「Webで○○を検索して」または「/web ○○」と明示的に依頼すると実行する。

検索語の抽出・結果の整形・会話への保存は端末内で行う。アプリのデータから外部へ送るのは今回の検索語だけで、会話履歴・添付・記憶は検索へ渡さない。クラウドLLMへの自動送信や、別サービスへの自動切り替えは行わない。オフ時は検索通信を開始しない。

## 共通形式とAndroidへの接続

正本は[`skills/web-search/SKILL.md`](../skills/web-search/SKILL.md)と[`references/android.md`](../skills/web-search/references/android.md)。標準のYAML frontmatterに`name`・`description`・`metadata`を持ち、その後のMarkdown本文に実行手順を記載する。

Gradleの`stageBundledAgentSkills`が、このスキルの`SKILL.md`と`references/`だけを`app/build/generated/bundled-agent-skills/assets/skills/web-search/`へコピーする。AGPの生成アセットとして全ビルドに接続し、他のスキルやスクリプトを一括でアプリへ取り込まない。

`BundledAgentSkills`は固定の同梱`web-search`だけを読み込む。`AgentSkillParser`がメタデータを解析する。Androidでは検索が有効かを実行時に確認し、本文とAndroid参照を読み込めた場合だけ、アプリ内のネイティブ`web_search`アダプターへ接続する。同梱文書を読み込めない場合は外部通信の前に失敗として返す。

今回は標準パッケージの読み込みと、明示コマンドを処理するネイティブ実行を提供する。汎用LLMへのスキルカタログ公開・本文のプロンプト注入・自律的なツール呼び出しは今後の統合範囲。ローダーの`activeCatalog` APIは無効なスキルを除外できるが、現チャットではLLMカタログとして呼び出していない。

任意ZIPのインポート、インターネットからのスキル取得、任意Python・シェル実行は提供しない。同じ`SKILL.md`を他のAgent Skills対応クライアントで使う場合も、その環境にDuckDuckGoを検索する`web_search`アダプターとON/OFFの制御が必要になる。Python/JARをAndroid上で動かす前提にはしていない。

## 読み込みの制約

- SnakeYAML 2.7の`SafeConstructor`を使い、任意のJavaクラスを生成しない。重複キー・コレクションへのエイリアスを拒否し、ネスト・入力サイズに上限を設ける。
- `name`・`description`と、標準の任意項目`metadata`・`license`・`compatibility`・`allowed-tools`を解析する。`metadata`のキー・値は文字列。`allowed-tools`を読み込んでも実行権限を追加しない。
- 同梱ディレクトリ名と`name`の一致を確認する。参照は同じスキルの`references/`内のMarkdownに限定し、パストラバーサルや他スキルへのアクセスを認めない。
- 無効なスキルは有効カタログへ含めず、本文も読み込まない。スキルの読み込みは通信やコード実行を伴わない。

## 検索結果の扱いと初版の範囲

検索結果は非信頼の外部データとして表示し、命令・権限・ツール呼び出しとして解釈しない。結果内の文言から別の通信や操作を開始しない。検索結果の概要だけでは公開日時や内容の真偽を独立確認できないため、不明な点を補わない。

初版は検索結果の取得・表示・出典・保存まで。リンク先本文の取得、動画の公開日時確認、テレビ連携は含めない。検索処理はTTS・音声経路から独立し、この変更では読み上げ処理を変更しない。

対応する依頼は`Webで○○を検索して`、`DuckDuckGoで○○を検索して`、`/web ○○`。検索語は1〜200文字で、添付・引用・疑問文・否定表現を含む依頼は検索せず、入力と添付を保持して説明する。検索中は送信ボタンで停止でき、設定をオフにした場合やチャット画面を離れた場合も中止する。

## LOCALとSERVERの履歴

検索依頼と結果はローカル専用タグを付けて保存し、SERVERへ送る会話履歴から除く。後続のLOCAL回答が検索結果を要約しても自動でサーバーへ流れないよう、同じ会話の最初のWebタグ以降にあるassistantメッセージもすべて除く。

この初版の保守的な扱いにより、検索依頼を保存した後はSERVER自身が生成した過去のassistant回答も、次回のSERVER履歴には含まれない。OFF・検索失敗の返信を保存した場合にも同じ制限を適用する。通常の利用者の発言と現在の送信文は維持する。LOCALでは検索結果を含む全履歴を維持し、Web検索依頼を保存していない会話では従来の履歴を使う。

初版の外部検索先はDuckDuckGoに限定する。出典リンクを利用者がブラウザで開くことは別の操作で、検索スキルがリンク先本文を自動取得することはない。

参照した仕様・ライブラリ：

- [Agent Skills仕様](https://agentskills.io/specification)
- [クライアントへの統合ガイド](https://agentskills.io/client-implementation/adding-skills-support)
- [SnakeYAML 2.7 SafeConstructor](https://javadoc.io/static/org.yaml/snakeyaml/2.7/org/yaml/snakeyaml/constructor/SafeConstructor.html)
