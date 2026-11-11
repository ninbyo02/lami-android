package io.github.ninbyo02.lami.tts

import java.util.Locale

/** Pronunciation for speech only. The displayed code and cumulative source stay untouched. */
internal object JapaneseCodeSpeechText {
    private val words = mapOf(
        "print" to "プリント", "println" to "プリントライン", "return" to "リターン",
        "class" to "クラス", "object" to "オブジェクト", "interface" to "インターフェース",
        "public" to "パブリック", "private" to "プライベート", "protected" to "プロテクテッド",
        "static" to "スタティック", "final" to "ファイナル", "const" to "コンスト",
        "let" to "レット", "var" to "バー", "val" to "バル", "fun" to "ファン",
        "function" to "ファンクション", "def" to "デフ", "if" to "イフ", "else" to "エルス",
        "for" to "フォー", "while" to "ホワイル", "when" to "ウェン", "switch" to "スイッチ",
        "case" to "ケース", "break" to "ブレイク", "continue" to "コンティニュー",
        "try" to "トライ", "catch" to "キャッチ", "finally" to "ファイナリー", "throw" to "スロー",
        "true" to "トゥルー", "false" to "フォルス", "null" to "ヌル", "none" to "ナン",
        "new" to "ニュー", "this" to "ディス", "self" to "セルフ", "import" to "インポート",
        "from" to "フロム", "package" to "パッケージ", "async" to "エーシンク",
        "await" to "アウェイト", "suspend" to "サスペンド", "override" to "オーバーライド",
        "enum" to "イーナム", "string" to "ストリング", "int" to "イント",
        "float" to "フロート", "double" to "ダブル", "boolean" to "ブーリアン",
        "bool" to "ブール", "long" to "ロング", "void" to "ボイド", "list" to "リスト",
        "map" to "マップ", "set" to "セット", "get" to "ゲット", "system" to "システム",
        "out" to "アウト", "hello" to "ハロー", "world" to "ワールド", "foo" to "フー",
        "bar" to "バー", "user" to "ユーザー", "name" to "ネーム", "value" to "バリュー",
        "data" to "データ", "result" to "リザルト", "count" to "カウント",
        "counter" to "カウンター", "create" to "クリエイト", "update" to "アップデート",
        "delete" to "デリート", "load" to "ロード", "save" to "セーブ", "read" to "リード",
        "write" to "ライト", "file" to "ファイル", "path" to "パス", "test" to "テスト",
        "run" to "ラン", "task" to "タスク", "main" to "メイン", "args" to "アーグス",
        "input" to "インプット", "output" to "アウトプット", "response" to "レスポンス",
        "request" to "リクエスト", "error" to "エラー", "exception" to "エクセプション",
        "color" to "カラー", "text" to "テキスト", "view" to "ビュー", "button" to "ボタン",
        "context" to "コンテキスト", "index" to "インデックス", "size" to "サイズ",
        "length" to "レングス", "number" to "ナンバー", "byte" to "バイト", "array" to "アレイ",
        "event" to "イベント", "callback" to "コールバック", "listener" to "リスナー",
        "state" to "ステート", "flow" to "フロー", "scope" to "スコープ", "launch" to "ローンチ",
        "open" to "オープン", "close" to "クローズ", "start" to "スタート", "stop" to "ストップ",
        "version" to "バージョン", "status" to "ステータス", "util" to "ユーティル",
        "c" to "シー", "cpp" to "シープラスプラス", "csharp" to "シーシャープ",
        "python" to "パイソン", "kotlin" to "コトリン", "java" to "ジャバ",
        "pygame" to "パイゲーム", "sys" to "シス", "display" to "ディスプレイ",
        "time" to "タイム", "clock" to "クロック", "init" to "イニット", "quit" to "クイット",
        "screen" to "スクリーン", "draw" to "ドロー", "rect" to "レクト", "fill" to "フィル",
        "flip" to "フリップ", "caption" to "キャプション", "key" to "キー",
        "javascript" to "ジャバスクリプト", "typescript" to "タイプスクリプト",
        "android" to "アンドロイド", "json" to "ジェイソン", "yaml" to "ヤムル",
        "git" to "ギット", "gradle" to "グレードル", "gradlew" to "グレードルダブリュー",
        "bash" to "バッシュ", "shell" to "シェル", "node" to "ノード",
        "npm" to "エヌピーエム", "pip" to "ピップ", "torch" to "トーチ",
    )
    private val letters = listOf("エー", "ビー", "シー", "ディー", "イー", "エフ", "ジー",
        "エイチ", "アイ", "ジェー", "ケー", "エル", "エム", "エヌ", "オー", "ピー",
        "キュー", "アール", "エス", "ティー", "ユー", "ブイ", "ダブリュー", "エックス", "ワイ", "ゼット")
    private val token = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*")
    private val pieces = Regex("[A-Z]+(?=[A-Z][a-z]|[0-9]|$)|[A-Z]?[a-z]+|[A-Z]+|[0-9]+")
    private val codeSpans = Regex("`{1,3}[\\s\\S]*?`{1,3}")
    private val callStart = Regex("[A-Za-z_][A-Za-z0-9_.]*\\(")
    private val urls = Regex("(?:[a-z][a-z0-9+.-]*://|mailto:|tel:|www\\.)[^\\s`、。！？「」『』]+", RegexOption.IGNORE_CASE)
    val celsiusUnit = Regex("°[ \\t]*[CcＣｃ](?![A-Za-z0-9_])")

    fun normalize(text: String): String {
        val code = codeSpans.findAll(text).map { it.range }.toList()
        val preservedSpans = (urls.findAll(text) + celsiusUnit.findAll(text)).map { it.range }.toList()
        val candidates = token.findAll(text).filter { match -> preservedSpans.none { match.range.first in it } }.toList()
        val ordinaryEnglishProse = code.isEmpty() && !callStart.containsMatchIn(text) && text.none { it in "{}[]=;_" } &&
            text.none { Character.UnicodeScript.of(it.code) in japaneseScripts } &&
            candidates.size > 1 && candidates.none { isStructured(it.value) }
        return token.replace(text) { match ->
            if (preservedSpans.any { match.range.first in it }) return@replace match.value
            val value = match.value
            val structured = isStructured(value)
            if (ordinaryEnglishProse && !structured) return@replace value
            val known = words[value.lowercase(Locale.ROOT)]
            if (known != null) return@replace known
            if (!structured && code.none { match.range.first in it }) return@replace value
            value.split('.').joinToString(" ドット ") { part ->
                part.split('_').joinToString(" アンダースコア ") { component ->
                    pieces.findAll(component).joinToString(" ") { piece ->
                        words[piece.value.lowercase(Locale.ROOT)] ?: piece.value.map { ch ->
                            if (ch.isDigit()) ch.toString() else letters[ch.uppercaseChar() - 'A']
                        }.joinToString("")
                    }
                }
            }
        }
    }

    private fun isStructured(value: String): Boolean = value.any { it == '_' || it == '.' } ||
        pieces.findAll(value).take(2).count() > 1 ||
        value.length > 1 && value.all { it in 'A'..'Z' || it.isDigit() }

    private val japaneseScripts = setOf(Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HAN)
}
