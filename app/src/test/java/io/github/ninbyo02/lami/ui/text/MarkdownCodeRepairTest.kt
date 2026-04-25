package io.github.ninbyo02.lami.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownCodeRepairTest {

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
    fun dashHeadingWithTrailingCode_isSeparated() {
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

    @Test
    fun looseJapaneseLines_becomeCommentNotCode() {
        val input = """
            ```python
            画面
            サイズ
            、
            pygame.init()
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 画面サイズ、
                pygame.init()
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun pythonCodeLines_areNotCommentedOut() {
        val input = """
            ```python
            import pygame
            from sys import exit
            if True:
            pygame.init()
            screen.blit(sprite, (0, 0))
            keys = pygame.key.get_pressed()
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                import pygame
                from sys import exit
                if True:
                pygame.init()
                screen.blit(sprite, (0, 0))
                keys = pygame.key.get_pressed()
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun plusEquals_isNotBroken() {
        val input = """
            ```python
            score + = 10
            paddle_x - = paddle_speed
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                score += 10
                paddle_x -= paddle_speed
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun falseScoreAndFalseWinGame_areSplit() {
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
    fun pygameInitHashComment_isSplitAndMerged() {
        val input = """
            ```python
            pygame.init()#
            画面
            サイズ
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                pygame.init()
                # 画面サイズ
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fullBreakoutTraceRepresentativeBlock_isRepaired() {
        val input = """
            ```python
            pygame.init()#
            画面
            サイズ
            SCREEN_WIDTH =80SCREEN_HEIGHT =60screen =
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
            block['status'] = Falsescore +=10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                pygame.init()
                # 画面サイズ
                SCREEN_WIDTH = 80
                SCREEN_HEIGHT = 60
                screen =
                # 衝突した方向を判定し、ボールの速度を反転させる
                block['status'] = False
                score += 10
                ```
            """.trimIndent(),
            repaired,
        )
    }
}
