package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStreamingRunnerChunkAppendTest {

    @Test
    fun `Hello と World の境界では最小 join を入れる`() {
        val builder = StringBuilder("Hello")

        val join = appendStreamingChunk(builder, "World")

        assertEquals(" ", join)
        assertEquals("Hello World", builder.toString())
    }

    @Test
    fun `hello dot py と 日本語助詞は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("hello.py", "を"))
    }

    @Test
    fun `Python と 日本語接続は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("Python", "で"))
    }

    @Test
    fun `print 呼び出しトークンは join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("print(", "\"x\")"))
    }

    @Test
    fun `fenced code の開始は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("```python", "\nimport os"))
    }

    @Test
    fun `foo と comma は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("foo", ","))
    }

    @Test
    fun `comma と World は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween(",", "World"))
    }

    @Test
    fun `先頭空白を含む chunk には join を追加しない`() {
        val builder = StringBuilder("Hello")

        val join = appendStreamingChunk(builder, " World")

        assertEquals("", join)
        assertEquals("Hello World", builder.toString())
    }

    @Test
    fun `空白のみ chunk も streaming chunk として保持対象にする`() {
        assertTrue(shouldPreserveWhitespaceChunk(" "))
        assertTrue(isViableStreamingChunk(" \t"))
        val builder = StringBuilder("Hello")

        val join = appendStreamingChunk(builder, " ")

        assertEquals("", join)
        assertEquals("Hello ", builder.toString())
    }

    @Test
    fun `prose lane は日本語の chunk を壊さず連結する`() {
        val builder = StringBuilder("はい、")
        val context = StreamingAppendContext()

        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = " 以下",
            context = context,
        )

        assertEquals("", join)
        assertEquals("はい、 以下", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `python と import の連結で code lane に入り不要 join を入れない`() {
        val builder = StringBuilder("以下に")
        val context = StreamingAppendContext()
        appendStreamingChunk(
            builder = builder,
            extractedRaw = "python",
            context = context,
        )

        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = "import turtle",
            context = context,
        )

        assertEquals("", join)
        assertEquals("以下に\npython\nimport turtle", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `code lane の print トークン連結では join しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(
            builder = builder,
            extractedRaw = "print(",
            context = context,
        )
        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = "\"x\")",
            context = context,
        )

        assertEquals("", join)
        assertEquals("print(\"x\")", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `prose から code へ遷移しても lane ごとの連結規則を維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(
            builder = builder,
            extractedRaw = "以下に",
            context = context,
        )
        appendStreamingChunk(
            builder = builder,
            extractedRaw = "python",
            context = context,
        )
        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = "print(\"x\")",
            context = context,
        )

        assertEquals("", join)
        assertEquals("以下に\npython\nprint(\"x\")", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `python 単独タグの後に import が来たら改行で再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "import os", context)

        assertEquals("python\nimport os", builder.toString())
    }

    @Test
    fun `python tag は必ず改行でコードと分離される`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"Hello\")", context)

        assertEquals("python\nprint(\"Hello\")", builder.toString())
    }

    @Test
    fun `prose の後に python が来た場合も改行される`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "以下に", context)
        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"x\")", context)

        assertEquals("以下に\npython\nprint(\"x\")", builder.toString())
    }

    @Test
    fun `pythonprint には絶対にならない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(", context)
        appendStreamingChunk(builder, "\"x\")", context)

        assertEquals("python\nprint(\"x\")", builder.toString())
    }

    @Test
    fun `python タグと複数行コードを再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "def main():", context)
        appendStreamingChunk(builder, "    print(\"x\")", context)

        assertEquals("python\ndef main():\n    print(\"x\")", builder.toString())
    }

    @Test
    fun `prose lane は従来どおり自然文を連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "こんにちは、", context)
        appendStreamingChunk(builder, "承知しました。", context)

        assertEquals("こんにちは、承知しました。", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `x equal と空白付き値は 1 行のまま連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "x =", context)
        appendStreamingChunk(builder, " 1", context)

        assertEquals("x = 1", builder.toString())
    }

    @Test
    fun `if の次の print は必要に応じて改行する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "if x > 0:", context)
        appendStreamingChunk(builder, "print(x)", context)

        assertEquals("if x > 0:\nprint(x)", builder.toString())
    }

    @Test
    fun `python の次に改行付き import が来ても line reassembler で維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "import os\n", context)
        appendStreamingChunk(builder, "print(os.getcwd())", context)

        assertEquals("python\nimport os\nprint(os.getcwd())", builder.toString())
    }

    @Test
    fun `prose と code の後に prose が来たら prose lane に戻る`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "以下に", context)
        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"Hello, World!\")", context)
        appendStreamingChunk(builder, "このコードを実行すると", context)

        assertEquals("以下に\npython\nprint(\"Hello, World!\") このコードを実行すると", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `python のコード後に日本語 prose chunk が来たら code lane 固定を解除する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"Hello, World!\")", context)
        appendStreamingChunk(builder, "このコードは非常にシンプルです", context)

        assertEquals("python\nprint(\"Hello, World!\")\nこのコードは非常にシンプルです", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `hello dot py と prose は不自然な改行を入れない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "hello.py", context)
        appendStreamingChunk(builder, "というファイル", context)

        assertEquals("hello.pyというファイル", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `python hello dot py は command 風 chunk でも文字単位分解しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python hello.py", context)

        assertEquals("python hello.py", builder.toString())
    }

    @Test
    fun `引用風 inline chunk は prose lane を維持する`() {
        val builder = StringBuilder("説明: ")
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "\"Hello, World!\"", context)
        appendStreamingChunk(builder, "です。", context)

        assertEquals("説明: \"Hello, World!\"です。", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `language tag の直後に fenced code 風 chunk が来ても python と連結しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "```print(\"x\")", context)

        assertEquals("python\n```print(\"x\")", builder.toString())
    }

    @Test
    fun `prose 中の Python と Hello comma は code lane に入らない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("はい、以下に", "Python", "で", "「", "Hello", ",", " World", "！」")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("はい、以下にPythonで「Hello, World！」", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `fenced code chunk は code lane で連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print(", "\"Hello, World!\"", ")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World!\")\n```", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `fenced python で import 行はキーワード境界で論理行を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "import pygame", "import random", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nimport pygame\nimport random\n```", builder.toString())
    }

    @Test
    fun `fenced python で class と def を別論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "class Block:", "def __init__(self):", "self.x = 1", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nclass Block:\ndef __init__(self):\nself.x = 1\n```", builder.toString())
    }

    @Test
    fun `fenced python で class の次に空白付き __init__ 開始を別論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "class Block:", " __init__(self, x, y)", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nclass Block:\n __init__(self, x, y)\n```", builder.toString())
    }

    @Test
    fun `fenced python で空白付き draw 開始を別論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "class Block:", " draw(self, screen)", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nclass Block:\n draw(self, screen)\n```", builder.toString())
    }

    @Test
    fun `fenced python で空白付き if と for と return を別論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "value = 1", " if value > 0:", " for x in items:", " return x", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nvalue = 1\n if value > 0:\n for x in items:\n return x\n```", builder.toString())
    }

    @Test
    fun `fenced python で未閉じ quote 継続中は空白付き chunk でも分離しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print(\"Hello,", " World\")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World\")\n```", builder.toString())
    }

    @Test
    fun `fenced python でも print の文字列断片は 1 行維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print", "(\"", "Hello,", " World", "!\")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World!\")\n```", builder.toString())
    }

    @Test
    fun `fenced python でハッシュ記号と日本語コメント断片を 1 行維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "#", " ブ", "ロック", "の色", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# ブロックの色\n```", builder.toString())
    }

    @Test
    fun `fenced python で inline comment 断片を 1 行維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf(
            "```python",
            "blocked_colors = COLORS[:6] #",
            " ブ",
            "ロック",
            "の色",
            "リスト",
            "を",
            "初期",
            "化",
            "```",
        )

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nblocked_colors = COLORS[:6] # ブロックの色リストを初期化\n```", builder.toString())
    }

    @Test
    fun `fenced python でコメント行の後に class が来たら新しい論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "# コメント", "class Block:", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# コメント\nclass Block:\n```", builder.toString())
    }

    @Test
    fun `prose lane の C sharp と日本語は従来どおり連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("C#", "の話")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("C#の話", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `fenced bash は python 専用ルールで誤改行しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "echo", " hello", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\necho hello\n```", builder.toString())
    }

    @Test
    fun `fenced bash のコメント行は既存挙動のまま次行を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "# hello", "echo world", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\n# hello\necho world\n```", builder.toString())
    }

    @Test
    fun `fenced bash の空白付き if 風 chunk でも python 専用分離はしない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "echo", " if true", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\necho if true\n```", builder.toString())
    }

    @Test
    fun `prose lane の Python 説明文は従来どおり連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("Python", "の基本", "構造")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("Pythonの基本構造", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `prose lane の先頭空白付き if は従来どおり prose 連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("Python の基本", " 構造", " は", " 大事")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("Python の基本 構造 は 大事", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `language tag の後に prose が来たら prose lane を維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "このコードは", context)

        assertEquals("pythonこのコードは", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `language tag と strong code で code lane に入る`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"x\")", context)

        assertEquals("python\nprint(\"x\")", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `code lane 中に prose like chunk が来たら prose lane に戻る`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "def main():", context)
        appendStreamingChunk(builder, "print(\"x\")", context)
        appendStreamingChunk(builder, "このコードは", context)

        assertEquals("def main():\nprint(\"x\") このコードは", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `Hello comma World は prose lane で改行しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "Hello", context)
        appendStreamingChunk(builder, ",", context)
        appendStreamingChunk(builder, " World", context)

        assertEquals("Hello, World", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `code lane で print 文字列断片を 1 行に再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)
        val chunks = listOf("print", "(\"", "Hello", ",", " World", "!\")")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }
        commitPendingCodeLine(builder, context)

        assertEquals("print(\"Hello, World!\")", builder.toString())
    }

    @Test
    fun `code lane で未閉じ double quote は commit しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "print(\"Hello,", context)
        appendStreamingChunk(builder, " World!\")", context)
        commitPendingCodeLine(builder, context)

        assertEquals("print(\"Hello, World!\")", builder.toString())
    }

    @Test
    fun `code lane で未閉じ single quote は commit しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "msg = 'abc", context)
        appendStreamingChunk(builder, " def'", context)
        commitPendingCodeLine(builder, context)

        assertEquals("msg = 'abc def'", builder.toString())
    }

    @Test
    fun `code lane で開き括弧継続中は commit しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "print(", context)
        appendStreamingChunk(builder, "\"x\")", context)
        commitPendingCodeLine(builder, context)

        assertEquals("print(\"x\")", builder.toString())
    }

    @Test
    fun `code lane で language tag 後の print 文字列断片を 1 行に再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print", context)
        appendStreamingChunk(builder, "(\"", context)
        appendStreamingChunk(builder, "Hello,", context)
        appendStreamingChunk(builder, " World", context)
        appendStreamingChunk(builder, "!\")", context)
        commitPendingCodeLine(builder, context)

        assertEquals("python\nprint(\"Hello, World!\")", builder.toString())
    }

    @Test
    fun `opening fence の直後は必ず改行される`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print", "(\"x\")")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }
        commitPendingCodeLine(builder, context)

        assertEquals("```python\nprint(\"x\")", builder.toString())
        assertFalse(builder.toString().contains("```pythonprint"))
    }

    @Test
    fun `closing fence の前に未閉じ quote 行を flush しても 1 行を維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print", "(\"", "Hello,", " World", "!\")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World!\")\n```", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `closing fence の後は prose lane に戻り prose を混在させない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print", "(\"Hello, World!\")", "```", "このコードを実行すると")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World!\")\n```\nこのコードを実行すると", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
        assertFalse(builder.toString().contains("World!\")このコード"))
    }
}
