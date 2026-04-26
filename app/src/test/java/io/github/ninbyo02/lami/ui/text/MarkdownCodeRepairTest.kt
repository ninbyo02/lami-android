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

    @Test
    fun yDirectionComment_doesNotMergeWithBlockHeading() {
        val input = """
            ```python
            # 
            Y方向の速度
            ブロック
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # Y方向の速度
                # ブロック
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun optionComment_doesNotMergeWithNextNumberedSection() {
        val input = """
            ```python
            # パドルに当たった時の角度調整(オプション)
            衝突した位置に応じてdxを調整することで、よりリアルな跳ね返りを実現できますが、ここでは単純に反転させます。
            6.衝突判定：ブロックとの衝突
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドルに当たった時の角度調整(オプション)
                # 衝突した位置に応じてdxを調整することで、よりリアルな跳ね返りを実現できますが、ここでは単純に反転させます。
                # 6.衝突判定：ブロックとの衝突
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun looseDashHeadingFragments_areNormalizedAsComment() {
        val input = """
            ```python
            --- ゲームオブジェクトのパラメータ ---
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun numberedJapaneseLine_becomesComment() {
        val input = """
            ```python
            1. イベント処理
            2. キー入力処理
            7. ゲームオーバー判定
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 1.イベント処理
                # 2.キー入力処理
                # 7.ゲームオーバー判定
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun codeLineFlushesPendingCommentFragments() {
        val input = """
            ```python
            #
            画面
            サイズ
            import pygame
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 画面サイズ
                import pygame
                ```
            """.trimIndent(),
            repaired,
        )
    }


    @Test
    fun bareFenceNextLinePython_isNormalizedToPythonFence() {
        val input = """
            ```
            python
            import pygame
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                import pygame
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun hashStartsCommentAndMergesJapaneseFragments() {
        val input = """
            ```python
            pygame.init()#
            画面
            サイズ
            SCREEN_WIDTH =80
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                pygame.init()
                # 画面サイズ
                SCREEN_WIDTH = 80
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun inlineHashAfterCode_splitsCodeAndComment() {
        val input = """
            ```python
            pygame.init()# 画面 サイズ
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
    fun importSysHash_splitsImportAndDropsEmptyComment() {
        val input = """
            ```python
            import pygameimport sys#
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                import pygame
                import sys
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun pythonCommentFragments_areMergedUntilNextCodeLine() {
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
            if ball_dy > 0:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突した方向を判定し、ボールの速度を反転させる
                if ball_dy > 0:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun pythonLooseJapaneseFragments_doNotRemainAsCode() {
        val input = """
            ```python
            ボール
            # の
            # 速度
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ボールの速度
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun commentFragments_mergeUntilNextCodeLine() {
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
            #
            の
            #
            速度
            #
            を
            #
            反
            #
            転
            #
            させる
            if ball_dy > 0:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突した方向を判定し、ボールの速度を反転させる
                if ball_dy > 0:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun headingCommentFragments_areNormalized() {
        val input = """
            ```python
            #
            ---
            初期
            設定
            ---
            pygame.init()
            #
            ---
            メ
            イン
            ループ
            ---
            clock = pygame.time.Clock()
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
    fun numberedJapaneseCommentThenCode_isMerged() {
        val input = """
            ```python
            7. ゲーム
            オーバー
            判定
            (ボールが底に落ちた)if ball_y + ball_radius > SCREEN_HEIGHT:game_over = True
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 7.ゲームオーバー判定 (ボールが底に落ちた)
                if ball_y + ball_radius > SCREEN_HEIGHT:
                game_over = True
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fragmentedRestartComment_isMerged() {
        val input = """
            ```python
            #
            リ
            スタート
            処理
            keys = pygame.key.get_pressed()
            #
            ゲーム
            状態
            を
            リ
            セット
            game_over = False
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # リスタート処理
                keys = pygame.key.get_pressed()
                # ゲーム状態をリセット
                game_over = False
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun markdownBoldBulletCollapse_isNormalizedOutsideCodeFence() {
        val input = """
            ***イベント処理**:
            ***移動処理**:
            ***衝突判定**:
            ```python
            print("***イベント処理**:")
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                * **イベント処理**:
                * **移動処理**:
                * **衝突判定**:
                ```python
                print("***イベント処理**:")
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun markdownHeadingWithoutSpace_isNormalizedOutsideCodeFence() {
        val input = """
            ###実行方法
            ```python
            print("###実行方法")
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ### 実行方法
                ```python
                print("###実行方法")
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun hashStartsCommentBlockAndMergesJapaneseFragments() {
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
            if ball_dy > 0:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突した方向を判定し、ボールの速度を反転させる
                if ball_dy > 0:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun hashFragmentBeforeCodeFlushesAsOneComment() {
        val input = """
            ```python
            # ボ
            ール
            ball_radius =10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ボール
                ball_radius = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun dashHeaderCommentMergesAndSplitsTrailingCode() {
        val input = """
            ```python
            # --- 初期
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
    fun numberedJapaneseCommentMergesBeforeCode() {
        val input = """
            ```python
            # 1. イベント
            処理
            for event in pygame.event.get():
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 1.イベント処理
                for event in pygame.event.get():
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun noBareJapaneseFragmentsRemainInTypicalBrokenPygameTrace() {
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

    @Test
    fun noRepeatedSingleHashLinesRemainAfterRepair() {
        val input = """
            ```python
            #
            #
            #
            衝突
            判定
            if ball_dy > 0:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突判定
                if ball_dy > 0:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun gameObjectParameterFragments_areMergedIntoDashHeading() {
        val input = """
            ```python
            # --- ゲーム ---
            # オブジェクト
            # の
            パラメータ
            ---
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun gameOverJudgementComment_isSeparatedFromTrailingCode() {
        val input = """
            ```python
            # 7.ゲーム
            オーバー
            # 判定
            (ボールが底に落ちた)if ball_y + ball_radius > SCREEN_HEIGHT:game_over = True
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 7.ゲームオーバー判定 (ボールが底に落ちた)
                if ball_y + ball_radius > SCREEN_HEIGHT:
                game_over = True
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun gameStateResetFragments_areMergedBeforeCode() {
        val input = """
            ```python
            ゲーム
            # 状態
            # を
            # リ
            # セット
            game_over = False
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ゲーム状態をリセット
                game_over = False
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun paddlePlayerComment_keepsSpaceBeforeParenthesis() {
        val input = """
            ```python
            # パドル
            (プレイヤー)
            paddle_width = 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル (プレイヤー)
                paddle_width = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fragmentedGameObjectDashComment_isMerged() {
        val input = """
            ```python
            # --- ゲーム ---
            # オブジェクト
            # の
            パラメータ
            ---
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun paddlePlayerComment_isMerged() {
        val input = """
            ```python
            # パドル
            (プレイヤー)
            paddle_width = 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル (プレイヤー)
                paddle_width = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fragmentedBallComment_isMerged() {
        val input = """
            ```python
            # ボ
            ール
            ball_radius =10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ボール
                ball_radius = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun commentFragments_flushBeforeCodeLine() {
        val input = """
            ```python
            #
            リ
            スタート
            処理
            keys = pygame.key.get_pressed()
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # リスタート処理
                keys = pygame.key.get_pressed()
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun codeLinesAreNotAbsorbedAsComment() {
        val input = """
            ```python
            #
            初期
            設定
            import pygame
            from sys import exit
            if True:
            score = 0
            pygame.init()
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 初期設定
                import pygame
                from sys import exit
                if True:
                score = 0
                pygame.init()
                ```
            """.trimIndent(),
            repaired,
        )
    }

}
