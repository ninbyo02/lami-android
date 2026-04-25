package io.github.ninbyo02.lami.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownCodeRepairTest {

    @Test
    fun splitCommentLines_mergesJapaneseCommentFragments() {
        val input = """
            ```python
            #
            # 画面
            # サイズ
            SCREEN_WIDTH =80
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 画面サイズ
                SCREEN_WIDTH = 80
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun splitCommentLines_doesNotMergeCode() {
        val input = """
            ```python
            #
             パ
            ドル
             (プレイヤー)paddle_width =10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル(プレイヤー)
                paddle_width = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun longJapaneseComment_isOneLine() {
        val input = """
            ```python
            #
            # 衝突した位置に応じてdxを調整することで
            、
            より
            リアル
            な
            跳
            ね
            返
            りを
            実現
            できます
            が
            、
            ここでは
            単純
            に
            反
            転
            させ
            ます
            。
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突した位置に応じてdxを調整することで、よりリアルな跳ね返りを実現できますが、ここでは単純に反転させます。
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun codeMerges_areSeparated() {
        val input = """
            ```python
            import pygameimport sys#
            pygame.init()#
            SCREEN_WIDTH =80SCREEN_HEIGHT =60screen =
            ball_x += ball_dxball_y += ball_dy
            block['status'] = Falsescore +=10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                import pygame
                import sys
                #
                pygame.init()
                #
                SCREEN_WIDTH = 80
                SCREEN_HEIGHT = 60
                screen =
                ball_x += ball_dx
                ball_y += ball_dy
                block['status'] = False
                score += 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun inlineHash_separatesCodeAndMergesComment() {
        val input = """
            ```python
            pygame.init()#
            画面
            サイズ
            BLUE = (0, 0, 25)#
             色
            の
            定義
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                pygame.init()
                # 画面サイズ
                BLUE = (0, 0, 25)
                # 色の定義
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun brokenJapaneseCommentFragments_areMergedUntilNextCode() {
        val input = """
            ```python
            #
             パ
            ドル
             (プレイヤー)
            paddle_width = 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル(プレイヤー)
                paddle_width = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun commentDoesNotAbsorbNextAssignment() {
        val input = """
            ```python
            #
            衝突
            した
            方向
            を
            判定
            し
            、
            ボール
            の
            速度
            を
            反
            転
            させる
            score = 0
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突した方向を判定し、ボールの速度を反転させる
                score = 0
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fixesBrokenPlusEquals() {
        val input = """
            ```python
            score + = 10
            paddle_x + = paddle_speed
            paddle_x - = paddle_speed
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                score += 10
                paddle_x += paddle_speed
                paddle_x -= paddle_speed
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fixesFalseScoreMerge() {
        val input = """
            ```python
            block['status'] = Falsescore += 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                block['status'] = False
                score += 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fixesWinGameFalseScoreMerge() {
        val input = """
            ```python
            win_game = Falsescore =0
            game_over = Falsewin_game = False
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                win_game = False
                score = 0
                game_over = False
                win_game = False
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun hashCommentFragments_areMergedIntoOneLine() {
        val input = """
            ```python
            #
            衝突
            した
            方向
            を
            判定
            し
            、
            ボール
            の
            速度
            を
            反
            転
            させる
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突した方向を判定し、ボールの速度を反転させる
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun dashCommentWithTrailingCode_isSeparated() {
        val input = """
            ```python
            #
             --- 初期
            設定
             ---pygame.init()
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- 初期設定 ---
                pygame.init()
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun falseScorePlusEquals_isRepaired() {
        val input = """
            ```python
            block['status'] = Falsescore + = 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                block['status'] = False
                score += 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun falseWinGameScoreMerge_isRepaired() {
        val input = """
            ```python
            game_over = Falsewin_game = False
            win_game = Falsescore =0
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                game_over = False
                win_game = False
                win_game = False
                score = 0
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun inlineIfColonStatement_isSplit() {
        val input = """
            ```python
            if event.type == pygame.QUIT:pygame.quit()
            for row in range(block_rows):for col in range(block_cols):
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                if event.type == pygame.QUIT:
                pygame.quit()
                for row in range(block_rows):
                for col in range(block_cols):
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun falseAndScoreBindings_areAlwaysSeparated() {
        val input = """
            ```python
            Falsescore =0
            Falsescore +=10
            Falsewin_game = False
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                False
                score = 0
                False
                score += 10
                False
                win_game = False
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun japaneseLooseLine_isMergedIntoPreviousComment() {
        val input = """
            ```python
            # 衝突した方向を判定し、
            ボール
            の
            速度を反転させる
            score += 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突した方向を判定し、ボールの速度を反転させる
                score += 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun dashCommentAndTrailingCode_areSplitAcrossLines() {
        val input = """
            ```python
            # --- 初期設定 ---pygame.init()
            # --- メインループ ---clock = pygame.time.Clock()
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- 初期設定 ---
                pygame.init()
                # --- メインループ ---
                clock = pygame.time.Clock()
                ```
            """.trimIndent(),
            repaired,
        )
    }
}
