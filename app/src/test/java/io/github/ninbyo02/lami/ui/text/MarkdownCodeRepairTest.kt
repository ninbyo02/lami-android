package io.github.ninbyo02.lami.ui.text

import io.github.ninbyo02.lami.ui.screens.home.buildFinalizedStreamingResponseForPersist
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
    fun fusedJapaneseComment_isSplit() {
        val input = """
            ```python
            # Trueなら存在、Falseなら破壊済みスコアとゲーム状態
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # Trueなら存在、Falseなら破壊済み
                # スコアとゲーム状態
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
    fun `paddle supplement line is merged after heading`() {
        val input = """
            ```python
            # --- ゲームオブジェクトのパラメータ ---
            # パドル
            (プレイヤー)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                # パドル (プレイヤー)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `game over supplement before if is merged into previous numbered comment`() {
        val input = """
            ```python
            # 7.ゲームオーバー判定
            (ボールが底に落ちた)if ball_y + ball_radius > SCREEN_HEIGHT:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 7.ゲームオーバー判定 (ボールが底に落ちた)
                if ball_y + ball_radius > SCREEN_HEIGHT:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `heading does not absorb paddle player comment`() {
        val input = """
            ```python
            # --- ゲームオブジェクトのパラメータ ---
            # パドル (プレイヤー)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                # パドル (プレイヤー)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `code line after supplement is preserved`() {
        val input = """
            ```python
            # 7.ゲームオーバー判定(ボールが底に落ちた)if ball_y + ball_radius > SCREEN_HEIGHT:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 7.ゲームオーバー判定 (ボールが底に落ちた)
                if ball_y + ball_radius > SCREEN_HEIGHT:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun knownPostProcessPatterns_doNotApplyOutsidePythonFence() {
        val input = """
            # パドル
            (プレイヤー)
            # 7.ゲームオーバー判定
            (ボールが底に落ちた)if ball_y + ball_radius > SCREEN_HEIGHT:
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(input, repaired)
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
    fun twoLinePythonFence_isNormalized() {
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
    fun realBreakoutRawCore_isRepaired() {
        val input = """
            ```python
            import pygameimport sys#
            --- 初期
            設定
            ---pygame.init()#
            画面
            サイズ
            SCREEN_WIDTH =80SCREEN_HEIGHT =60screen =
            #
            ボ
            ール
            ball_radius =10ball_x = SCREEN_WIDTH //2ball_y = SCREEN_HEIGHT //2ball_dx =5#
            X方向
            の
            速度
            ball_dy = -5 #
            Y方向
            の
            速度
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
                import pygame
                import sys
                # --- 初期設定 ---
                pygame.init()
                # 画面サイズ
                SCREEN_WIDTH = 80
                SCREEN_HEIGHT = 60
                screen =
                # ボール
                ball_radius = 10
                ball_x = SCREEN_WIDTH //2
                ball_y = SCREEN_HEIGHT //2
                ball_dx = 5
                # X方向の速度
                ball_dy = -5
                # Y方向の速度
                # 衝突した方向を判定し、ボールの速度を反転させる
                block['status'] = False
                score += 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideMarkdownListNormalization_doesNotTouchFenceBody() {
        val input = """
            1.初期設定: 説明。2.オブジェクトの定義: 説明。
            
            ```python
            print("***イベント処理**:")
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                1. 初期設定: 説明。
                2. オブジェクトの定義: 説明。
                
                ```python
                print("***イベント処理**:")
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
    fun fusedImportLines_areSplitDeterministically() {
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
    fun fusedScreenAssignments_areSplitDeterministically() {
        val input = """
            ```python
            SCREEN_WIDTH =80SCREEN_HEIGHT =60screen =
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                SCREEN_WIDTH = 80
                SCREEN_HEIGHT = 60
                screen =
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedLoopHeaderLines_areSplitDeterministically() {
        val input = """
            ```python
            blocks = []for row in range(block_rows):for col in range(block_cols):blocks.append(block)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                blocks = []
                for row in range(block_rows):
                for col in range(block_cols):
                blocks.append(block)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedIfInlineActionLine_isSplitDeterministically() {
        val input = """
            ```python
            keys = pygame.key.get_pressed()if keys[pygame.K_LEFT] and paddle_x >0:paddle_x -= paddle_speed
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                keys = pygame.key.get_pressed()
                if keys[pygame.K_LEFT] and paddle_x > 0:
                paddle_x -= paddle_speed
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedIfAndAssignments_areSplitDeterministically() {
        val input = """
            ```python
            if block['status']:block_rect = block['rect']ball_rect = pygame.Rect(0, 0, 1, 1)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                if block['status']:
                block_rect = block['rect']
                ball_rect = pygame.Rect(0, 0, 1, 1)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun dashHeadingAndPaddleComment_areSeparatedWithoutMixing() {
        val input = """
            ```python
            # --- ゲーム --- オブジェクトの --- パラメータ --- --- パドル ---
            (プレイヤー)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                # パドル (プレイヤー)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun gameHeadingDoesNotAbsorbPaddleComment() {
        val input = """
            ```python
            # --- ゲーム --- オブジェクトの --- パラメータ ---
            # パドル
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                # パドル
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun barePaddleSupplementIsMergedIntoPaddleComment() {
        val input = """
            ```python
            # パドル
            (プレイヤー)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル (プレイヤー)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun numberedHeadingSupplementAndIf_areSplitDeterministically() {
        val input = """
            ```python
            7.ゲームオーバー判定
            (ボールが底に落ちた)if ball_y + ball_radius > SCREEN_HEIGHT:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 7.ゲームオーバー判定 (ボールが底に落ちた)
                if ball_y + ball_radius > SCREEN_HEIGHT:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun gameOverSupplementAndIfAreSplit() {
        val input = """
            ```python
            7.ゲームオーバー判定
            (ボールが底に落ちた)if ball_y + ball_radius > SCREEN_HEIGHT:
            game_over =True
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
    fun frameRateSupplementAndClockTick_areSplitDeterministically() {
        val input = """
            ```python
            (60 FPS)clock.tick(60)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # フレームレート設定 (60 FPS)
                clock.tick(60)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun frameRateCommentDeduplicated() {
        val input = """
            ```python
            # フレームレート設定
            (60 FPS)clock.tick(60)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # フレームレート設定 (60 FPS)
                clock.tick(60)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun inlineHashNumberedCommentAfterLongSentenceIsSplit() {
        val input = """
            ```python
            # パドルに当たった時の角度調整(オプション)衝突した位置に応じてdxを調整することで、よりリアルな跳ね返りを実現できますが、ここでは単純に反転させます。#6.衝突判定：ブロックとの衝突
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドルに当たった時の角度調整(オプション)衝突した位置に応じてdxを調整することで、よりリアルな跳ね返りを実現できますが、ここでは単純に反転させます。
                # 6.衝突判定：ブロックとの衝突
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcessDoesNotAbsorbCodeAfterDashHeading() {
        val input = """
            ```python
            # --- ゲーム --- オブジェクトの --- パラメータ ---
            if running:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                if running:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedIfConditionAndInlineAssignment_areSplitDeterministically() {
        val input = """
            ```python
            if keys[pygame.K_RIGHT] and paddle_x< SCREEN_WIDTH - paddle_width:paddle_x += paddle_speed
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                if keys[pygame.K_RIGHT] and paddle_x < SCREEN_WIDTH - paddle_width:
                paddle_x += paddle_speed
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedForAndAppend_areSplitDeterministically() {
        val input = """
            ```python
            for col in range(block_cols):blocks.append(block)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                for col in range(block_cols):
                blocks.append(block)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedFalseAssignments_areSplitDeterministically() {
        val input = """
            ```python
            game_over = Falsewin_game = Falsescore =0
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                game_over = False
                win_game = False
                score = 0
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedFalseAndScoreIncrement_areSplitDeterministically() {
        val input = """
            ```python
            block['status'] = Falsescore +=10
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
    fun codeLinesAreNotAbsorbedAfterCommentFragments() {
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

    @Test
    fun duplicatedDashHeading_isNormalized() {
        val input = """
            ```python
            # --- 初期設定 --- ---
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- 初期設定 ---
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun gameObjectParameterFragments_mergeToSingleDashHeading() {
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
    fun splitKatakanaBallComment_merges() {
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
    fun particleSpeedReverseComment_merges() {
        val input = """
            ```python
            # の
            # 速度
            # を
            # 反
            # 転
            # させる
            if ball_dy > 0:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # の速度を反転させる
                if ball_dy > 0:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun gameOverClearDisplayComment_withoutLeadingHash_merges() {
        val input = """
            ```python
            ゲーム
            オーバー
            /クリア
            # 画面
            # の
            # 表示
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ゲームオーバー/クリア画面の表示
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun restartCommentFragments_merge() {
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
    fun fragmentedGameSectionComment_fromRealBreakoutSample_isMerged() {
        val input = """
            ```python
            # --- ゲーム ---
            # オブジェクト
            # の
            パラメータ
            ---
            paddle_width = 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                paddle_width = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fragmentedBallComment_fromRealBreakoutSample_isMerged() {
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
    fun fragmentedMoveComment_fromRealBreakoutSample_isMerged() {
        val input = """
            ```python
            # 3.ボ
            ールの
            # 移動
            ball_x += ball_dx
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 3.ボールの移動
                ball_x += ball_dx
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fragmentedCollisionDirectionComment_fromRealBreakoutSample_isMerged() {
        val input = """
            ```python
            # 衝突した方向を判定し、
            ボール
            # の
            # 速度
            # を
            # 反
            # 転
            # させる
            if ball_dy >0:
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
    fun looseGameOverClearDisplayComment_fromRealBreakoutSample_isMerged() {
        val input = """
            ```python
            ゲーム
            オーバー
            /クリア
            # 画面
            # の
            # 表示
            if game_over:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ゲームオーバー/クリア画面の表示
                if game_over:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_headingAndListAreSeparated() {
        val input = "###コードの解説1.**初期設定 (`pygame.init()`)**:"

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ### コードの解説
                1. **初期設定 (`pygame.init()`)**:
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_tripleAsteriskBulletIsNormalized() {
        val input = """
            ***イベント処理**:
            ***移動処理**:
            ***衝突判定**:
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                * **イベント処理**:
                * **移動処理**:
                * **衝突判定**:
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_headingAndNumberedBodyAreSeparated() {
        val input = "###実行方法1.上記のコードを `pong.py`"

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ### 実行方法
                1. 上記のコードを `pong.py`
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_headingAndSentenceAreSeparated() {
        val input = "###改善点と次のステップこのコードは非常に基本的なものです。"

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ### 改善点と次のステップ
                このコードは非常に基本的なものです。
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_normalizationDoesNotChangeCodeFenceBody() {
        val input = """
            ###実行方法1.上記のコードを `pong.py`
            ```python
            print("###実行方法")
            print("***イベント処理**:")
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ### 実行方法
                1. 上記のコードを `pong.py`
                ```python
                print("###実行方法")
                print("***イベント処理**:")
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fragmentedGameObjectParameterComment_isMerged() {
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
    fun fragmentedNumberedMoveComment_isMerged() {
        val input = """
            ```python
            # 3.ボ
            ールの
            # 移動
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 3.ボールの移動
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun looseGameOverClearDisplayComment_isMerged() {
        val input = """
            ```python
            ゲーム
            オーバー
            /クリア
            # 画面
            # の
            # 表示
            if game_over:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ゲームオーバー/クリア画面の表示
                if game_over:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_numberedBoldItems_areSplit() {
        val input = "1.**初期設定 (`pygame.init()`)**: Pygameの機能を初期化します。2.**オブジェクトの定義**: ボールを作成します。"

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                1. **初期設定 (`pygame.init()`)**: Pygameの機能を初期化します。
                2. **オブジェクトの定義**: ボールを作成します。
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_starBoldBullets_areNormalized() {
        val input = """
            ***イベント処理**: ウィンドウを閉じたり...
            ***移動処理**: パドルを...
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                * **イベント処理**: ウィンドウを閉じたり...
                * **移動処理**: パドルを...
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_headingAndFollowingSentence_areSplit() {
        val input = "###改善点と次のステップこのコードは非常に基本的なものです。"

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ### 改善点と次のステップ
                このコードは非常に基本的なものです。
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_normalizationDoesNotChangePythonFenceBody() {
        val input = """
            1.**初期設定 (`pygame.init()`)**: Pygameの機能を初期化します。2.**オブジェクトの定義**: ボールを作成します。
            ```python
            print("***イベント処理**:")
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                1. **初期設定 (`pygame.init()`)**: Pygameの機能を初期化します。
                2. **オブジェクトの定義**: ボールを作成します。
                ```python
                print("***イベント処理**:")
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_fusedControlAndAssignmentLines_areSplit() {
        val input = """
            ```python
            if game_over:msg = "retry"
            for block in blocks:if block['status']:block_rect = block['rect']
            if running:pygame.quit()sys.exit()
            paddle_x -= paddle_speedif keys[pygame.K_RIGHT]:
            ball_x += ball_dxball_y += ball_dy
            keys = pygame.key.get_pressed()if keys[pygame.K_r]:
            block['status'] = Falsescore +=10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                if game_over:
                msg = "retry"
                for block in blocks:
                if block['status']:
                block_rect = block['rect']
                if running:
                pygame.quit()
                sys.exit()
                paddle_x -= paddle_speed
                if keys[pygame.K_RIGHT]:
                ball_x += ball_dx
                ball_y += ball_dy
                keys = pygame.key.get_pressed()
                if keys[pygame.K_r]:
                block['status'] = False
                score += 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_commentFragments_areMergedToSingleLines() {
        val input = """
            ```python
            # --- ゲーム ---
            # オブジェクト
            # の
            パラメータ
            ---
            # ボ
            ール
            # 描
            画
            # ゲーム
            # 状態
            # を
            # リ
            # セット
            # 衝突した方向を判定し、
            ボール
            # の
            # 速度
            # を
            # 反
            # 転
            # させる
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                # ボール
                # 描画
                # ゲーム状態をリセット
                # 衝突した方向を判定し、ボールの速度を反転させる
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_outsideFenceMarkdown_isNormalizedWithoutTouchingPythonStrings() {
        val input = """
            1. **初期設定 (`pygame.init()`)**: 説明です。2. **オブジェクト定義**: 続きです。
            * **衝突判定**:***壁**:
            ### 改善点と次のステップこのコードは非常に基本的なものです。
            ```python
            print("***イベント処理**:")
            print("###実行方法")
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                1. **初期設定 (`pygame.init()`)**: 説明です。
                2. **オブジェクト定義**: 続きです。
                * **衝突判定**:
                * **壁**:
                ### 改善点と次のステップ
                このコードは非常に基本的なものです。
                ```python
                print("***イベント処理**:")
                print("###実行方法")
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutGameObjectComment_isMerged() {
        val input = """
            ```python
            # --- ゲーム ---
            # オブジェクトの
            パラメータ
            ---
            paddle_width = 10
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                paddle_width = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutBallComment_isMerged() {
        val input = """
            ```python
            # ボ
            ール
            ball_radius = 10
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
    fun regression_breakoutGameOverCommentAndCode_areSplit() {
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
    fun regression_breakoutGameOverClearDisplayComment_isMerged() {
        val input = """
            ```python
            ゲーム
            オーバー
            /クリア
            # 画面
            # の
            # 表示
            if game_over:msg = font.render("GAME OVER! Press R to Restart", True, WHITE)
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                # ゲームオーバー/クリア画面の表示
                if game_over:
                msg = font.render("GAME OVER! Press R to Restart", True, WHITE)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutRestartKeyLine_isSplit() {
        val input = """
            ```python
            keys = pygame.key.get_pressed()if keys[pygame.K_r]:
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                keys = pygame.key.get_pressed()
                if keys[pygame.K_r]:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutForBlockStatusLine_isSplit() {
        val input = """
            ```python
            for block in blocks:block['status'] = True
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                for block in blocks:
                block['status'] = True
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutScoreGameStateLine_isSplit() {
        val input = """
            ```python
            score =0game_over = Falsewin_game = False
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                score = 0
                game_over = False
                win_game = False
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutBallParameterLine_isSplit() {
        val input = """
            ```python
            ball_radius =10ball_x = SCREEN_WIDTH //2ball_y = SCREEN_HEIGHT //2ball_dx =5
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                ball_radius = 10
                ball_x = SCREEN_WIDTH //2
                ball_y = SCREEN_HEIGHT //2
                ball_dx = 5
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutPaddleLongComment_isSplitIntoComments() {
        val input = """
            ```python
            # パドルに当たった時の角度調整(オプション)衝突した位置に応じてdxを調整することで、よりリアルな跳ね返りを実現できますが、ここでは単純に反転させます。6.衝突判定：ブロックとの衝突
            for block in blocks:
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                # パドルに当たった時の角度調整 (オプション)
                # 衝突した位置に応じてdxを調整することで、よりリアルな跳ね返りを実現できますが、ここでは単純に反転させます。
                # 6.衝突判定：ブロックとの衝突
                for block in blocks:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_executionStepsAreSplit() {
        val input = """
            実行方法

            1. 上記のコードを pong.py のような名前で保存します。2.ターミナルで python pong.py を実行します。
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                実行方法

                1. 上記のコードを pong.py のような名前で保存します。

                2. ターミナルで python pong.py を実行します。
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun outsideFence_nestedBoldBulletIsSplit() {
        val input = """
            衝突判定:*壁:
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                衝突判定:

                壁:
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutStatusAndWinGameLine_isSplit() {
        val input = """
            ```python
            block['status'] = Trueif win_game:
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                block['status'] = True
                if win_game:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutFrameRateComment_isMergedBeforeClockTick() {
        val input = """
            ```python
            # フレーム
            / レート
            / 設定
            clock.tick(60)
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                # フレームレート設定 (60 FPS)
                clock.tick(60)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutRestartCommentFragments_areMerged() {
        val input = """
            ```python
            # リ
            スタート
            # 処理
            if keys[pygame.K_r]:
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                # リスタート処理
                if keys[pygame.K_r]:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun regression_breakoutGameStateResetCommentFragments_areMerged() {
        val input = """
            ```python
            ゲーム
            # 状態をリセット
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
    fun regression_breakoutObjectParameterHeadingFragments_areMerged() {
        val input = """
            ```python
            # オブジェクトの
            パラメータ
            ---
            paddle_width = 100
            ```
        """.trimIndent()
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                paddle_width = 100
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fragmentedBallComment_isMergedToOneLine() {
        val input = "```python\n# ボ\nール\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals("```python\n# ボール\n```", repaired)
    }

    @Test
    fun fragmentedRestartComment_isMergedToOneLine() {
        val input = "```python\n# リ\nスタート\n# 処理\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals("```python\n# リスタート処理\n```", repaired)
    }

    @Test
    fun objectParameterHeadingFragments_areMergedToDashHeading() {
        val input = "```python\n# オブジェクトの\nパラメータ\n---\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals("```python\n# --- ゲームオブジェクトのパラメータ ---\n```", repaired)
    }

    @Test
    fun fusedIfWinGameMessage_isSplit() {
        val input = "```python\nif win_game:msg = font.render(\"CLEAR\", True, WHITE)\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            "```python\nif win_game:\nmsg = font.render(\"CLEAR\", True, WHITE)\n```",
            repaired,
        )
    }

    @Test
    fun fusedForBlockStatusReset_isSplit() {
        val input = "```python\nfor block in blocks:block['status'] = True\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals("```python\nfor block in blocks:\nblock['status'] = True\n```", repaired)
    }

    @Test
    fun fusedKeysThenIfR_isSplit() {
        val input = "```python\nkeys = pygame.key.get_pressed()if keys[pygame.K_r]:\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals("```python\nkeys = pygame.key.get_pressed()\nif keys[pygame.K_r]:\n```", repaired)
    }

    @Test
    fun fusedIfGameOverAssignment_isSplit() {
        val input = "```python\nif ball_y + ball_radius > SCREEN_HEIGHT:game_over = True\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals(
            "```python\nif ball_y + ball_radius > SCREEN_HEIGHT:\ngame_over = True\n```",
            repaired,
        )
    }

    @Test
    fun fusedAllBlocksWinAssignment_isSplit() {
        val input = "```python\nif all(not b['status'] for b in blocks):win_game = True\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals("```python\nif all(not b['status'] for b in blocks):\nwin_game = True\n```", repaired)
    }

    @Test
    fun yVelocityAndBlockComment_areSplit() {
        val input = "```python\n# Y方向の速度ブロック\nblock_rows = 5\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals("```python\n# Y方向の速度\n# ブロック\nblock_rows = 5\n```", repaired)
    }

    @Test
    fun inlineDestroyedCommentAndScoreComment_areSeparated() {
        val input = "```python\n# Trueなら存在、Falseなら破壊済み})#スコアと\n```"
        val repaired = MarkdownCodeRepair.repair(input)
        assertEquals("```python\n# Trueなら存在、Falseなら破壊済み\n# スコアと\n```", repaired)
    }



    @Test
    fun finalPostProcess_ballMovementOrder() {
        val input = """
            ```python
            # ボ
            ールの
            # 移動
            ball_radius =10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ボールの移動
                ball_radius = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_restartOrder() {
        val input = """
            ```python
            # リ
            スタート
            # 処理
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
    fun finalPostProcess_gameStateResetOrder() {
        val input = """
            ```python
            ゲーム
            # 状態をリセット
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
    fun finalPostProcess_frameRateOrder() {
        val input = """
            ```python
            # フレーム
            レート
            # 設定
            clock.tick(60)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # フレームレート設定 (60 FPS)
                clock.tick(60)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_mergesMainLoopHeading() {
        val input = """
            ```python
            # --- メイン ---
            ループ
            clock = pygame.time.Clock()
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- メインループ ---
                clock = pygame.time.Clock()
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_mergesGameObjectParameterHeading() {
        val input = """
            ```python
            # --- ゲーム ---
            # オブジェクトの
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
    fun finalPostProcess_splitsVelocityAndBlockComment() {
        val input = """
            ```python
            # Y方向の速度ブロック
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
    fun finalPostProcess_mergesCollisionDirectionComment() {
        val input = """
            ```python
            # 衝突した方向を判定し、
            ボール
            # の速度を反転させる上下どちらに当たったか
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突した方向を判定し、ボールの速度を反転させる
                # 上下どちらに当たったか
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_preservesCommentFragmentOrder() {
        val input = """
            ```python
            # ボールの
            移動
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ボールの移動
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_doesNotMergePaddleIntoGameHeading() {
        val input = """
            ```python
            # --- ゲーム ---
            # オブジェクトの
            パラメータ
            ---
            # パドル
            (プレイヤー)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                # パドル (プレイヤー)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_doesNotAbsorbCodeLines() {
        val input = """
            ```python
            ループ
            clock = pygame.time.Clock()
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # ループ
                clock = pygame.time.Clock()
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_splitsMixedGameHeadingAndPaddleComment() {
        val input = """
            ```python
            # --- ゲーム --- オブジェクトの --- パラメータ --- --- パドル ---
            (プレイヤー)
            paddle_width = 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                # パドル (プレイヤー)
                paddle_width = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_mergesResidualPaddlePlayerSupplementAfterHeading() {
        val input = """
            ```python
            # --- ゲームオブジェクトのパラメータ ---
            # パドル
            (プレイヤー)
            paddle_width = 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                # パドル (プレイヤー)
                paddle_width = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_movesGameOverSupplementBeforeIfCode() {
        val input = """
            ```python
            # 7.ゲームオーバー判定
            (ボールが底に落ちた)if ball_y + ball_radius > SCREEN_HEIGHT:
            game_over = True
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
    fun finalPostProcess_movesWinSupplementBeforeIfCode() {
        val input = """
            ```python
            # 8.ゲームクリア判定
            (全てのブロックが破壊された)if all(not block['status'] for block in blocks):
            win_game = True
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 8.ゲームクリア判定 (全てのブロックが破壊された)
                if all(not block['status'] for block in blocks):
                win_game = True
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_splitsInlineNumberedCommentAfterSentence() {
        val input = """
            ```python
            # パドルに当たった時の角度調整(オプション)衝突した位置に応じてdxを調整することで、よりリアルな跳ね返りを実現できますが、ここでは単純に反転させます。#6.衝突判定：ブロックとの衝突
            for block in blocks:
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドルに当たった時の角度調整(オプション)衝突した位置に応じてdxを調整することで、よりリアルな跳ね返りを実現できますが、ここでは単純に反転させます。
                # 6.衝突判定：ブロックとの衝突
                for block in blocks:
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_absorbsFrameRateSupplementBeforeClockCode() {
        val input = """
            ```python
            # フレームレート設定
            (60 FPS)clock.tick(60)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # フレームレート設定 (60 FPS)
                clock.tick(60)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_preservesCollisionCommentOrderWithLooseBallFragment() {
        val input = """
            ```python
            # 衝突した方向を判定し、
            ボール
            # の速度を反転させる
            # 上下どちらに当たったか
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # 衝突した方向を判定し、ボールの速度を反転させる
                # 上下どちらに当たったか
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `paddle player supplement is merged`() {
        val input = """
            ```python
            # パドル
            (プレイヤー)
            paddle_x = 10
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル (プレイヤー)
                paddle_x = 10
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `game over supplement with following if is split`() {
        val input = """
            ```python
            # 7.ゲームオーバー判定
            (ボールが底に落ちた)if ball_y + ball_radius > SCREEN_HEIGHT:
            game_over = True
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
    fun `main loop dash heading is normalized`() {
        val input = """
            ```python
            # --- メイン --- ループ ---
            clock = pygame.time.Clock()
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- メインループ ---
                clock = pygame.time.Clock()
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `dash heading does not absorb paddle comment`() {
        val input = """
            ```python
            # --- ゲームオブジェクトのパラメータ ---
            # パドル
            (プレイヤー)
            paddle_width = 100
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # --- ゲームオブジェクトのパラメータ ---
                # パドル (プレイヤー)
                paddle_width = 100
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `residual paddle supplement is merged when paddle heading exists within three lines`() {
        val input = """
            ```python
            # パドル
            # 実出力の残存形
            (プレイヤー)
            paddle_width = 100
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル (プレイヤー)
                # 実出力の残存形
                paddle_width = 100
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `residual paddle supplement is not merged when code line exists before supplement`() {
        val input = """
            ```python
            # パドル
            paddle_x = 100
            (プレイヤー)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル
                paddle_x = 100
                (プレイヤー)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `outside python fence keeps paddle supplement split`() {
        val input = """
            # パドル
            (プレイヤー)
            ```python
            score = 0
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                # パドル
                (プレイヤー)
                ```python
                score = 0
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `final safety fuse merges paddle supplement only for consecutive lines in python fence`() {
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
    fun `final safety fuse does not apply outside python fence`() {
        val input = """
            # パドル
            (プレイヤー)
            ```python
            score = 0
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                # パドル
                (プレイヤー)
                ```python
                score = 0
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun `final safety fuse does not merge when a code line exists between paddle lines`() {
        val input = """
            ```python
            # パドル
            paddle_x = 20
            (プレイヤー)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル
                paddle_x = 20
                (プレイヤー)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun finalPostProcess_mergesPaddlePlayerOnlyWhenSafe() {
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
    fun finalPostProcess_doesNotMergeWhenCodeBetween() {
        val input = """
            ```python
            # パドル
            paddle_x = 20
            (プレイヤー)
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # パドル
                paddle_x = 20
                (プレイヤー)
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun buildFinalizedStreamingResponseForPersist_mergesPaddlePlayerInsidePythonFence() {
        val input = """
            ```python
            # パドル
            (プレイヤー)
            paddle_width = 10
            ```
        """.trimIndent()

        val repaired = buildFinalizedStreamingResponseForPersist(input)

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
    fun fusedForColAndBlocksAppend_isSplitInPythonFence() {
        val input = """
            ```python
            for col in range(block_cols):blocks.append({'status': True})#スコアとゲーム状態
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                for col in range(block_cols):
                blocks.append({'status': True})
                # スコアとゲーム状態
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedForRowAndForCol_isSplitInPythonFence() {
        val input = """
            ```python
            for row in range(block_rows):for col in range(block_cols):
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                for row in range(block_rows):
                for col in range(block_cols):
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedTrueCommentAndAppendComment_areSeparated() {
        val input = """
            ```python
            # Trueなら存在、Falseなら破壊済み})#スコアとゲーム状態
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                # Trueなら存在、Falseなら破壊済み
                # スコアとゲーム状態
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedIfAndInlineMsgAssignments_areSplit() {
        val input = """
            ```python
            if game_over:msg = "Game Over"
            if win_game:msg = "You Win!"
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                if game_over:
                msg = "Game Over"
                if win_game:
                msg = "You Win!"
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedIfRestartAndComment_isSplit() {
        val input = """
            ```python
            if keys[pygame.K_r]:# リスタート処理
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                if keys[pygame.K_r]:
                # リスタート処理
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedForBlockAndAssignment_isSplit() {
        val input = """
            ```python
            for block in blocks:block['status'] = True
            ```
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(
            """
                ```python
                for block in blocks:
                block['status'] = True
                ```
            """.trimIndent(),
            repaired,
        )
    }

    @Test
    fun fusedRepairPatterns_doNotRunOutsidePythonFence() {
        val input = """
            for col in range(block_cols):blocks.append({'status': True})#スコアとゲーム状態
            if game_over:msg = "Game Over"
            for block in blocks:block['status'] = True
        """.trimIndent()

        val repaired = MarkdownCodeRepair.repair(input)

        assertEquals(input, repaired)
    }

    @Test
    fun buildFinalizedStreamingResponseForPersist_appliesSameFusedRepairs() {
        val input = """
            ```python
            if game_over:msg = "Game Over"


            msg_rect = msg.get_rect()
            ```
        """.trimIndent()

        val repaired = buildFinalizedStreamingResponseForPersist(input)

        assertEquals(
            """
                ```python
                if game_over:
                msg = "Game Over"

                msg_rect = msg.get_rect()
                ```
            """.trimIndent(),
            repaired,
        )
    }

}
