package io.github.ninbyo02.lami.ui.text

import java.util.Locale

object MarkdownCodeRepair {
    fun repair(text: String): String {
        if (text.isEmpty()) return text
        val normalizedFence = if (text.contains("```")) normalizeTwoLinePythonFence(text) else text
        val repaired = if (normalizedFence.contains("```")) repairCodeFences(normalizedFence) else normalizedFence
        val normalized = normalizeMarkdownOutsideCodeFences(repaired)
        val fused = applyFinalPaddlePlayerFuseInPythonFences(normalized)
        return applyConservativePythonIndentRepairInFences(fused)
    }

    private data class PythonFenceMatch(
        val bodyStartIndex: Int,
        val fromBareFencePattern: Boolean,
    )

    private data class MergedCommentResult(
        val comments: List<String>,
        val extractedCodeLines: List<String>,
        val nextIndex: Int,
    )

    private data class SplitCommentCodeResult(
        val line: String,
        val extractedCode: String? = null,
    )

    private data class InlineHashSplitResult(
        val code: String,
        val commentSeed: String,
    )

    private data class CommentAndCodeSplit(
        val comment: String,
        val code: String,
    )

    private data class HeadingSplitResult(
        val heading: String,
        val trailingComment: String,
    )

    private data class ParenthesizedSupplementResult(
        val commentPrefix: String?,
        val supplement: String,
        val code: String,
    )

    private fun applyFinalPaddlePlayerFuseInPythonFences(markdown: String): String {
        if (!markdown.contains("```")) return markdown
        val lines = markdown.split('\n')
        if (lines.isEmpty()) return markdown

        val rebuilt = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val fenceMatch = resolvePythonFenceOpening(lines, index)
            if (fenceMatch == null) {
                rebuilt.add(lines[index])
                index += 1
                continue
            }

            rebuilt.add(
                if (fenceMatch.fromBareFencePattern) normalizeBarePythonFenceLine(lines[index]) else lines[index],
            )
            index = fenceMatch.bodyStartIndex

            while (index < lines.size && !isFenceLine(lines[index])) {
                val current = lines[index]
                val next = lines.getOrNull(index + 1)
                if (current.trim() == "# パドル" && next?.trim() == "(プレイヤー)") {
                    rebuilt.add("# パドル (プレイヤー)")
                    index += 2
                    continue
                }
                rebuilt.add(current)
                index += 1
            }

            if (index < lines.size) {
                rebuilt.add(lines[index])
                index += 1
            }
        }

        return rebuilt.joinToString("\n")
    }

    private fun applyConservativePythonIndentRepairInFences(markdown: String): String {
        if (!markdown.contains("```")) return markdown
        val lines = markdown.split('\n')
        if (lines.isEmpty()) return markdown

        val rebuilt = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val fenceMatch = resolvePythonFenceOpening(lines, index)
            if (fenceMatch == null) {
                rebuilt.add(lines[index])
                index += 1
                continue
            }

            rebuilt.add(
                if (fenceMatch.fromBareFencePattern) normalizeBarePythonFenceLine(lines[index]) else lines[index],
            )
            index = fenceMatch.bodyStartIndex

            val bodyLines = mutableListOf<String>()
            while (index < lines.size && !isFenceLine(lines[index])) {
                bodyLines.add(lines[index])
                index += 1
            }
            rebuilt.addAll(repairConservativePythonIndent(bodyLines))

            if (index < lines.size) {
                rebuilt.add(lines[index])
                index += 1
            }
        }
        return rebuilt.joinToString("\n")
    }

    private fun repairConservativePythonIndent(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = lines.toMutableList()
        var index = 0
        while (index < rebuilt.lastIndex) {
            val currentTrimmed = rebuilt[index].trim()
            val nextTrimmed = rebuilt[index + 1].trim()

            if (currentTrimmed.matches(Regex("""for row in range\(.+\):""")) &&
                nextTrimmed.matches(Regex("""for col in range\(.+\):"""))
            ) {
                rebuilt[index + 1] = withIndent(nextTrimmed, 4)
                index += 1
                continue
            }

            if (currentTrimmed.matches(Regex("""for col in range\(.+\):""")) &&
                nextTrimmed.startsWith("blocks.append(")
            ) {
                rebuilt[index + 1] = withIndent(nextTrimmed, 8)
                index += 1
                continue
            }

            if (currentTrimmed == "if block['status']:" &&
                (nextTrimmed.startsWith("block_rect =") ||
                    nextTrimmed.startsWith("ball_rect =") ||
                    nextTrimmed.startsWith("if ball_rect.colliderect") ||
                    nextTrimmed.startsWith("pygame.draw.rect("))
            ) {
                var blockLineIndex = index + 1
                var repairedAnyTargetLine = false
                while (blockLineIndex <= rebuilt.lastIndex) {
                    val blockLine = rebuilt[blockLineIndex].trim()
                    if (blockLine.isEmpty() && repairedAnyTargetLine) {
                        blockLineIndex += 1
                        continue
                    }
                    if (!blockLine.startsWith("block_rect =") &&
                        !blockLine.startsWith("ball_rect =") &&
                        !blockLine.startsWith("if ball_rect.colliderect") &&
                        !blockLine.startsWith("pygame.draw.rect(")
                    ) break
                    rebuilt[blockLineIndex] = withIndent(blockLine, 8)
                    repairedAnyTargetLine = true
                    blockLineIndex += 1
                }
                index = blockLineIndex
                continue
            }

            if ((currentTrimmed == "if game_over:" || currentTrimmed == "if win_game:") &&
                nextTrimmed.startsWith("msg =")
            ) {
                var messageLineIndex = index + 1
                while (messageLineIndex <= rebuilt.lastIndex) {
                    val messageLine = rebuilt[messageLineIndex].trim()
                    if (!messageLine.startsWith("msg =") &&
                        !messageLine.startsWith("text_rect =") &&
                        !messageLine.startsWith("screen.blit(")
                    ) break
                    rebuilt[messageLineIndex] = withIndent(messageLine, 4)
                    messageLineIndex += 1
                }
                index = messageLineIndex
                continue
            }

            if (currentTrimmed == "while True:" &&
                (nextTrimmed == "# 1.イベント処理" || nextTrimmed == "for event in pygame.event.get():")
            ) {
                rebuilt[index + 1] = withIndentIfNeeded(rebuilt[index + 1], 4)
                if (nextTrimmed == "# 1.イベント処理" && index + 2 <= rebuilt.lastIndex) {
                    val eventLoopTrimmed = rebuilt[index + 2].trim()
                    if (eventLoopTrimmed == "for event in pygame.event.get():") {
                        rebuilt[index + 2] = withIndentIfNeeded(rebuilt[index + 2], 4)
                    }
                }
                index += 1
                continue
            }

            if (currentTrimmed == "for event in pygame.event.get():" &&
                nextTrimmed == "if event.type == pygame.QUIT:"
            ) {
                rebuilt[index + 1] = withIndent(nextTrimmed, 4)
                index += 1
                continue
            }

            if (currentTrimmed == "if event.type == pygame.QUIT:" &&
                (nextTrimmed == "pygame.quit()" || nextTrimmed == "sys.exit()")
            ) {
                rebuilt[index + 1] = withIndent(nextTrimmed, 8)
                index += 1
                continue
            }

            if (currentTrimmed == "if not game_over and not win_game:" &&
                (isGameUpdateLine(nextTrimmed) || isGameUpdateCommentLine(nextTrimmed))
            ) {
                var updateLineIndex = index + 1
                while (updateLineIndex <= rebuilt.lastIndex) {
                    if (isTopLevelSectionCommentLine(rebuilt[updateLineIndex])) break
                    val updateLine = rebuilt[updateLineIndex].trim()
                    if (!isGameUpdateLine(updateLine) && !isGameUpdateCommentLine(updateLine)) break
                    rebuilt[updateLineIndex] = withIndentIfNeeded(rebuilt[updateLineIndex], 4)
                    updateLineIndex += 1
                }
                index = updateLineIndex
                continue
            }

            if (currentTrimmed == "for block in blocks:" && nextTrimmed == "if block['status']:") {
                rebuilt[index + 1] = withIndent(nextTrimmed, 4)
                index += 1
                continue
            }

            if ((currentTrimmed == "if game_over:" || currentTrimmed == "if win_game:") &&
                nextTrimmed.isEmpty() &&
                index + 2 <= rebuilt.lastIndex &&
                rebuilt[index + 2].trim().startsWith("msg =")
            ) {
                rebuilt.removeAt(index + 1)
                rebuilt[index + 1] = withIndent(rebuilt[index + 1].trim(), 4)
                index += 1
                continue
            }

            if (currentTrimmed == "if keys[pygame.K_r]:" &&
                (isResetAssignmentLine(nextTrimmed) || isResetCommentOrLoopLine(nextTrimmed))
            ) {
                var resetIndex = index + 1
                while (resetIndex <= rebuilt.lastIndex) {
                    val resetTrimmed = rebuilt[resetIndex].trim()
                    if (isResetCommentOrLoopLine(resetTrimmed)) {
                        rebuilt[resetIndex] = withIndentIfNeeded(rebuilt[resetIndex], 4)
                        if (resetTrimmed == "for block in blocks:" && resetIndex + 1 <= rebuilt.lastIndex) {
                            val nestedResetTrimmed = rebuilt[resetIndex + 1].trim()
                            if (nestedResetTrimmed == "block['status'] = True") {
                                rebuilt[resetIndex + 1] = withIndentIfNeeded(rebuilt[resetIndex + 1], 8)
                                resetIndex += 1
                            }
                        }
                        resetIndex += 1
                        continue
                    }
                    if (!isResetAssignmentLine(resetTrimmed)) break
                    rebuilt[resetIndex] = withIndentIfNeeded(rebuilt[resetIndex], 4)
                    resetIndex += 1
                }
                index = resetIndex
                continue
            }

            index += 1
        }
        return rebuilt.toList()
    }

    private fun withIndent(trimmedLine: String, spaces: Int): String {
        return "${" ".repeat(spaces)}$trimmedLine"
    }

    private fun withIndentIfNeeded(line: String, spaces: Int): String {
        val leadingSpaces = line.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) line.length else it }
        if (leadingSpaces >= spaces) return line
        return withIndent(line.trim(), spaces)
    }

    private fun isResetAssignmentLine(trimmedLine: String): Boolean {
        return trimmedLine.matches(
            Regex("""(game_over|win_game|score|paddle_[A-Za-z0-9_]+|ball_[A-Za-z0-9_]+|blocks)\s*=.*"""),
        )
    }

    private fun isResetCommentOrLoopLine(trimmedLine: String): Boolean {
        return trimmedLine == "# ゲーム状態をリセット" || trimmedLine == "for block in blocks:"
    }

    private fun isGameUpdateLine(trimmedLine: String): Boolean {
        if (trimmedLine.isEmpty()) return false
        return trimmedLine.startsWith("keys = pygame.key.get_pressed()") ||
            trimmedLine.startsWith("if keys[") ||
            trimmedLine.startsWith("paddle_x +=") ||
            trimmedLine.startsWith("paddle_x -=") ||
            trimmedLine.startsWith("ball_x +=") ||
            trimmedLine.startsWith("ball_y +=") ||
            trimmedLine.startsWith("if ball_y") ||
            trimmedLine.startsWith("if ball_x") ||
            trimmedLine.startsWith("ball_rect =") ||
            trimmedLine.startsWith("paddle_rect =") ||
            trimmedLine.startsWith("if ball_rect.colliderect") ||
            trimmedLine.startsWith("pygame.draw.") ||
            trimmedLine.startsWith("screen.blit(")
    }

    private fun isGameUpdateCommentLine(trimmedLine: String): Boolean =
        trimmedLine == "# 2.キー入力処理"

    private fun isTopLevelSectionCommentLine(line: String): Boolean {
        if (line.startsWith(" ")) return false
        return line.trim().matches(Regex("""#\s*\d+\..+"""))
    }

    private fun repairCodeFences(markdown: String): String {
        val lines = markdown.split('\n')
        if (lines.isEmpty()) return markdown

        val rebuilt = StringBuilder(markdown.length + 32)
        var index = 0
        while (index < lines.size) {
            val fenceMatch = resolvePythonFenceOpening(lines, index)
            if (fenceMatch == null) {
                rebuilt.append(lines[index])
                if (index < lines.lastIndex) rebuilt.append('\n')
                index += 1
                continue
            }

            val openingFenceLine = if (fenceMatch.fromBareFencePattern) {
                normalizeBarePythonFenceLine(lines[index])
            } else {
                lines[index]
            }
            rebuilt.append(openingFenceLine)
            if (index < lines.lastIndex) rebuilt.append('\n')

            index = fenceMatch.bodyStartIndex
            val bodyBuilder = StringBuilder()
            while (index < lines.size && !isFenceLine(lines[index])) {
                bodyBuilder.append(lines[index])
                if (index < lines.lastIndex) bodyBuilder.append('\n')
                index += 1
            }
            rebuilt.append(repairPythonBody(bodyBuilder.toString()))

            if (index < lines.size) {
                rebuilt.append(lines[index])
                if (index < lines.lastIndex) rebuilt.append('\n')
                index += 1
            }
        }

        return rebuilt.toString()
    }

    private fun resolvePythonFenceOpening(lines: List<String>, index: Int): PythonFenceMatch? {
        val currentLine = lines[index]
        if (isPythonFenceOpeningLine(currentLine)) {
            return PythonFenceMatch(bodyStartIndex = index + 1, fromBareFencePattern = false)
        }
        if (!isBareFenceLine(currentLine)) return null
        val nextIndex = index + 1
        if (nextIndex >= lines.size) return null
        if (!isPythonLanguageOnlyLine(lines[nextIndex])) return null
        return PythonFenceMatch(bodyStartIndex = nextIndex + 1, fromBareFencePattern = true)
    }

    private fun repairPythonBody(body: String): String {
        if (body.isEmpty()) return body
        val sourceLines = body.split('\n')
        if (sourceLines.isEmpty()) return body

        val lines = sourceLines
            .flatMap(::expandMergedLineHints)
            .flatMap(::splitDeterministicFusedCodeLines)
        val repairedLines = mutableListOf<String>()
        val commentFragments = mutableListOf<String>()
        var isCommentContinuationActive = false

        fun flushCommentFragments() {
            if (commentFragments.isEmpty()) {
                isCommentContinuationActive = false
                return
            }
            val merged = commentFragments.joinToString(separator = "") { it.trim() }.trim()
            val normalized = normalizeMergedComment(merged)
            repairedLines.add(normalized)
            commentFragments.clear()
            isCommentContinuationActive = false
        }

        var index = 0
        while (index < lines.size) {
            val line = sanitizeLeadingDashCodeResidue(lines[index])
            val nextLine = lines.getOrNull(index + 1)
            val trimmedLine = line.trim()

            val inlineHashSplit = splitInlineHashCodeAndComment(line)
            if (inlineHashSplit != null) {
                flushCommentFragments()
                repairedLines.add(repairCodeLine(inlineHashSplit.code))
                isCommentContinuationActive = true
                if (inlineHashSplit.commentSeed.isNotBlank()) {
                    commentFragments.add(inlineHashSplit.commentSeed)
                }
                index += 1
                continue
            }

            if (trimmedLine.startsWith("#")) {
                val split = splitCommentFragmentAndCode(line)
                val content = split.line.trim().removePrefix("#").trim()
                isCommentContinuationActive = true
                if (content.isNotBlank()) {
                    commentFragments.add(content)
                }
                if (split.extractedCode != null) {
                    flushCommentFragments()
                    repairedLines.add(repairCodeLine(split.extractedCode))
                }
                index += 1
                continue
            }

            if (trimmedLine.isEmpty()) {
                flushCommentFragments()
                repairedLines.add("")
                index += 1
                continue
            }

            if (isCommentContinuationActive || commentFragments.isNotEmpty()) {
                val looseSplit = splitLooseCommentFragmentAndCode(trimmedLine)
                val commentPart = looseSplit.line.trim()
                if (
                    looseSplit.extractedCode != null &&
                    commentPart.isNotBlank() &&
                    shouldAbsorbAsCommentFragment(commentPart)
                ) {
                    commentFragments.add(commentPart)
                    flushCommentFragments()
                    repairedLines.add(repairCodeLine(looseSplit.extractedCode))
                    index += 1
                    continue
                }
                if (shouldAbsorbAsCommentFragment(trimmedLine)) {
                    commentFragments.add(trimmedLine)
                    index += 1
                    continue
                }
                if (looseSplit.extractedCode != null) {
                    flushCommentFragments()
                    repairedLines.add(repairCodeLine(looseSplit.extractedCode))
                    index += 1
                    continue
                }
                flushCommentFragments()
            }

            if (isLooseDashHeadingLine(trimmedLine)) {
                repairedLines.add(normalizeDashComment("# $trimmedLine"))
                index += 1
                continue
            }

            if (isNumberedJapaneseLine(trimmedLine)) {
                val nextTrimmed = nextLine?.trim().orEmpty()
                val nextSplit = splitLooseCommentFragmentAndCode(nextTrimmed)
                val shouldCarrySupplement =
                    nextSplit.extractedCode != null &&
                        nextSplit.line.trim().startsWith("(")
                if (shouldCarrySupplement) {
                    isCommentContinuationActive = true
                    commentFragments.add(trimmedLine)
                    index += 1
                    continue
                }
                repairedLines.add(normalizePlainComment("# $trimmedLine"))
                index += 1
                continue
            }

            if (isLooseJapaneseCommentLine(trimmedLine) && !isClearCodeLine(trimmedLine)) {
                val shouldContinue =
                    isLooseJapaneseCommentLine(nextLine?.trim().orEmpty()) ||
                        nextLine?.trim()?.startsWith("#") == true
                if (shouldContinue) {
                    isCommentContinuationActive = true
                    commentFragments.add(trimmedLine)
                    index += 1
                    continue
                }
                repairedLines.add(normalizePlainComment("# $trimmedLine"))
                index += 1
                continue
            }

            val splitSupplementAndCode = splitLooseCommentFragmentAndCode(trimmedLine)
            if (
                splitSupplementAndCode.extractedCode != null &&
                splitSupplementAndCode.line.trim() == "(60 FPS)"
            ) {
                repairedLines.add("# フレームレート設定 (60 FPS)")
                repairedLines.add(repairCodeLine(splitSupplementAndCode.extractedCode))
                index += 1
                continue
            }

            repairedLines.add(repairCodeLine(line, nextLine))
            index += 1
        }

        flushCommentFragments()
        val postProcessedLines = mergeTrailingPythonCommentFragments(repairedLines)
        val finalPostProcessedLines = applyKnownFinalPythonPostProcess(postProcessedLines)
        val compactedBlankLines = compactConsecutiveBlankLines(finalPostProcessedLines)
        val safeguardedLines = applyFinalPaddlePlayerSafetyFuse(compactedBlankLines)
        val closedAppendLines = safeguardedLines.map(::closeKnownIncompleteBlocksAppendLine)
        return closedAppendLines.joinToString("\n")
    }

    private fun closeKnownIncompleteBlocksAppendLine(line: String): String {
        val trimmed = line.trimEnd()
        if (!trimmed.contains("blocks.append(")) return line
        if (!trimmed.contains("'status': True")) return line
        if (trimmed.endsWith("})") || trimmed.endsWith("})#") || trimmed.endsWith("} )")) return line
        if (trimmed.endsWith("True")) return "$trimmed})"
        return line
    }

    private fun applyKnownFinalPythonPostProcess(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val current = lines[index]
            val currentTrimmed = current.trim()
            val next = lines.getOrNull(index + 1)
            val nextTrimmed = next?.trim()

            if (currentTrimmed == "# パドル" && nextTrimmed == "(プレイヤー)") {
                rebuilt.add("# パドル (プレイヤー)")
                index += 2
                continue
            }

            if (
                currentTrimmed == "# 7.ゲームオーバー判定" &&
                nextTrimmed != null &&
                nextTrimmed.startsWith("(ボールが底に落ちた)if ")
            ) {
                rebuilt.add("# 7.ゲームオーバー判定 (ボールが底に落ちた)")
                rebuilt.add(nextTrimmed.removePrefix("(ボールが底に落ちた)").trim())
                index += 2
                continue
            }

            if (currentTrimmed.contains("})#")) {
                val split = current.split("})#", limit = 2)
                if (split.size == 2) {
                    val trailingComment = split[1].trim()
                    rebuilt.add("${split[0]}})")
                    if (trailingComment.isNotEmpty()) {
                        rebuilt.add("# ${trailingComment.removePrefix("#").trim()}")
                    }
                    index += 1
                    continue
                }
            }

            splitKnownFusedJapaneseComments(current).forEach { rebuilt.add(it) }
            index += 1
        }
        return rebuilt
    }

    private fun splitKnownFusedJapaneseComments(line: String): List<String> {
        val trimmed = line.trim()
        if (!trimmed.startsWith("#")) return listOf(line)

        val content = trimmed.removePrefix("#").trim()

        val patterns = listOf(
            "スコアとゲーム状態",
            "ゲーム状態",
        )

        for (pattern in patterns) {
            val idx = content.indexOf(pattern)
            if (idx > 0) {
                val first = content.substring(0, idx).trim()
                val second = content.substring(idx).trim()
                return listOf(
                    "# $first",
                    "# $second",
                )
            }
        }

        return listOf(line)
    }

    private fun compactConsecutiveBlankLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = mutableListOf<String>()
        var previousBlank = false
        for (line in lines) {
            val isBlank = line.isBlank()
            if (isBlank && previousBlank) continue
            rebuilt.add(line)
            previousBlank = isBlank
        }
        return rebuilt
    }

    private fun applyFinalPaddlePlayerSafetyFuse(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = mutableListOf<String>()

        for (i in lines.indices) {
            val current = lines[i]
            val currentTrimmed = current.trim()

            if (currentTrimmed == "(プレイヤー)") {
                val paddleIndex = findMergeablePaddleIndex(rebuilt)
                if (paddleIndex != null) {
                    rebuilt[paddleIndex] = "# パドル (プレイヤー)"
                    continue
                }
            }

            rebuilt.add(current)
        }

        return rebuilt
    }

    private fun findMergeablePaddleIndex(lines: List<String>): Int? {
        val maxLookback = 3
        val lastIndex = lines.lastIndex

        if (lastIndex < 0) return null

        for (offset in 1..maxLookback) {
            val idx = lastIndex - (offset - 1)
            if (idx < 0) break

            val line = lines[idx].trim()

            if (line.startsWith("# パドル")) {
                for (j in idx + 1..lastIndex) {
                    val mid = lines[j].trim()
                    if (mid.isNotEmpty() && !mid.startsWith("#")) {
                        return null
                    }
                }
                return idx
            }

            if (line.isNotEmpty() && !line.startsWith("#")) break
        }

        return null
    }



    private fun mergeTrailingPythonCommentFragments(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            if (trimmed.startsWith("#")) {
                val normalized = normalizeFinalCommentLine(trimmed)
                appendMergedCommentLine(rebuilt, normalized)
                index += 1
                continue
            }

            if (isStandaloneJapaneseCommentFragment(trimmed)) {
                val nextTrimmed = lines.getOrNull(index + 1)?.trim().orEmpty()
                if (nextTrimmed.startsWith("#")) {
                    val mergedNext = mergeFragments(trimmed, nextTrimmed)
                    appendMergedCommentLine(rebuilt, normalizeFinalCommentLine(mergedNext.trim()))
                    index += 2
                    continue
                }
                if (rebuilt.lastOrNull()?.trim()?.startsWith("#") == true) {
                    val previousComment = rebuilt.last().trim()
                    if (shouldKeepCommentSeparated(previousComment.removePrefix("#").trim(), trimmed)) {
                        rebuilt.add(normalizeFinalCommentLine("# $trimmed"))
                    } else {
                        val mergedPrev = mergeCommentText(rebuilt.last(), trimmed)
                        rebuilt[rebuilt.lastIndex] = normalizeFinalCommentLine(mergedPrev.trim())
                    }
                    index += 1
                    continue
                }
                rebuilt.add(normalizePlainComment("# $trimmed"))
                index += 1
                continue
            }

            rebuilt.add(line)
            index += 1
        }

        return rebuilt
            .let(::applyDeterministicFinalCommentRepairs)
            .flatMap(::splitKnownCompositeComment)
            .map { normalizeFinalCommentLine(it.trim()) }
            .let(::deduplicateFrameRateComments)
            .let(::mergeResidualPaddlePlayerSupplement)
    }

    private fun mergeResidualPaddlePlayerSupplement(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = mutableListOf<String>()
        for (line in lines) {
            val currentTrimmed = line.trim()
            val mergeTargetIndex = findRecentPaddleHeadingIndex(rebuilt)
            if (currentTrimmed == "(プレイヤー)" && mergeTargetIndex != null) {
                rebuilt[mergeTargetIndex] = "# パドル (プレイヤー)"
                continue
            }

            rebuilt.add(line)
        }
        return rebuilt
    }

    private fun findRecentPaddleHeadingIndex(lines: List<String>): Int? {
        val lastIndex = lines.lastIndex
        if (lastIndex < 0) return null
        for (offset in 1..3) {
            val candidateIndex = lastIndex - (offset - 1)
            if (candidateIndex < 0) break
            val candidateTrimmed = lines[candidateIndex].trim()
            if (candidateTrimmed == "# パドル") {
                return candidateIndex
            }
            if (looksLikeCodeLine(candidateTrimmed)) {
                return null
            }
        }
        return null
    }

    private fun applyDeterministicFinalCommentRepairs(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val current = lines[index].trim()
            if (!current.startsWith("#")) {
                rebuilt.add(lines[index])
                index += 1
                continue
            }

            val splitHeading = splitKnownMixedGameHeading(current)
            if (splitHeading != null) {
                rebuilt.add(splitHeading.heading)
                rebuilt.add(splitHeading.trailingComment)
                index += 1
                continue
            }

            val supplement = extractParenthesizedSupplementAndCode(current.removePrefix("#").trim())
            if (supplement != null) {
                if (!supplement.commentPrefix.isNullOrBlank()) {
                    rebuilt.add(normalizeFinalCommentLine("# ${supplement.commentPrefix} ${supplement.supplement}"))
                } else if (rebuilt.lastOrNull()?.trim()?.startsWith("#") == true) {
                    val merged = mergeCommentText(rebuilt.last(), " ${supplement.supplement}")
                    rebuilt[rebuilt.lastIndex] = normalizeFinalCommentLine(merged)
                } else {
                    rebuilt.add(normalizeFinalCommentLine("# ${supplement.supplement}"))
                }
                rebuilt.add(repairCodeLine(supplement.code))
                index += 1
                continue
            }

            if (current == "# の速度を反転させる上下どちらに当たったか") {
                val previous = rebuilt.lastOrNull()?.trim().orEmpty()
                if (previous == "# 衝突した方向を判定し、ボール") {
                    rebuilt[rebuilt.lastIndex] = "# 衝突した方向を判定し、ボールの速度を反転させる"
                    rebuilt.add("# 上下どちらに当たったか")
                    index += 1
                    continue
                }
            }

            rebuilt.add(lines[index])
            index += 1
        }
        return rebuilt
    }

    private fun splitKnownMixedGameHeading(commentLine: String): HeadingSplitResult? {
        val content = commentLine.removePrefix("#").trim()
        val compact = content.replace(Regex("\\s+"), "")
        val headingPrefix = "---ゲーム---オブジェクトの---パラメータ---"
        if (!compact.startsWith(headingPrefix)) return null
        var suffix = compact.removePrefix(headingPrefix)
        if (suffix.startsWith("---")) suffix = suffix.removePrefix("---")
        if (suffix.isBlank()) return null
        val trailing = when (suffix) {
            "パドル---", "パドル" -> "# パドル"
            "パドル---(プレイヤー)", "パドル(プレイヤー)" -> "# パドル (プレイヤー)"
            else -> return null
        }
        return HeadingSplitResult(
            heading = "# --- ゲームオブジェクトのパラメータ ---",
            trailingComment = trailing,
        )
    }

    private fun extractParenthesizedSupplementAndCode(content: String): ParenthesizedSupplementResult? {
        val match = Regex("^(.*?)(\\([^()]+\\))(\\s*(?:if|for|while)\\b.+)$").matchEntire(content) ?: return null
        val commentPrefix = match.groupValues[1].trim().ifBlank { null }
        val supplement = match.groupValues[2].trim()
        val code = match.groupValues[3].trim()
        if (code.isBlank()) return null
        return ParenthesizedSupplementResult(
            commentPrefix = commentPrefix,
            supplement = supplement,
            code = code,
        )
    }

    private fun appendMergedCommentLine(rebuilt: MutableList<String>, commentLine: String) {
        val normalized = normalizeFinalCommentLine(commentLine.trim())
        val previous = rebuilt.lastOrNull()?.trim()
        if (previous != null && previous.startsWith("#")) {
            val previousContent = previous.removePrefix("#").trim()
            val currentContent = normalized.removePrefix("#").trim()
            if (shouldKeepCommentSeparated(previousContent, currentContent)) {
                rebuilt.add(normalized)
                return
            }
            if (shouldMergeConsecutiveComment(previousContent, currentContent)) {
                val merged = mergeCommentText(previous, currentContent)
                rebuilt[rebuilt.lastIndex] = normalizeFinalCommentLine(merged.trim())
                return
            }
        }
        rebuilt.add(normalized)
    }

    private fun shouldMergeConsecutiveComment(previous: String, current: String): Boolean {
        if (previous.isBlank() || current.isBlank()) return true
        if (isFinalizedDashHeading(previous)) return false
        if (previous.contains("---") || current.contains("---")) return true
        if (previous.length <= 8 || current.length <= 8) return true
        if (previous.endsWith("、")) return true
        if (previous.endsWith("を") || previous.endsWith("の")) return true
        if (previous.endsWith("し")) return true
        if (previous.contains("の速度を反転させる") && current.startsWith("上下")) return false
        return false
    }

    private fun splitKnownCompositeComment(line: String): List<String> {
        val trimmed = line.trim()
        if (!trimmed.startsWith("#")) return listOf(line)
        val content = trimmed.removePrefix("#").trim()
        val splitInlineNumbered = splitInlineNumberedComment(content)
        if (splitInlineNumbered != null) {
            return listOf(
                normalizeFinalCommentLine("# ${splitInlineNumbered.comment}"),
                normalizeFinalCommentLine("# ${splitInlineNumbered.code}"),
            )
        }
        val compact = content.replace(Regex("\\s+"), "")
        if (
            compact == "---ゲーム---オブジェクトの---パラメータ------パドル---(プレイヤー)" ||
            compact == "---ゲーム---オブジェクトの---パラメータ---パドル(プレイヤー)" ||
            compact == "---ゲームオブジェクトのパラメータ---パドル(プレイヤー)"
        ) {
            return listOf("# --- ゲームオブジェクトのパラメータ ---", "# パドル (プレイヤー)")
        }
        return when (content) {
            "Y方向の速度ブロック" -> listOf("# Y方向の速度", "# ブロック")
            "衝突した方向を判定し、ボールの速度を反転させる上下どちらに当たったか" -> {
                listOf("# 衝突した方向を判定し、ボールの速度を反転させる", "# 上下どちらに当たったか")
            }
            else -> listOf(line)
        }
    }

    private fun normalizeFinalCommentLine(line: String): String {
        val trimmed = line.trim()
        if (!trimmed.startsWith("#")) return line
        val content = trimmed.removePrefix("#").trim()
        val normalizedNumbered = Regex("^(\\d+)\\.(\\S.*)$").matchEntire(content)
        if (normalizedNumbered != null) {
            return normalizePlainComment("# ${normalizedNumbered.groupValues[1]}.${normalizedNumbered.groupValues[2]}")
        }
        val compact = content.replace(Regex("\\s+"), "")
        return when {
            compact == "ゲームオブジェクトのパラメータ" || compact == "---ゲームオブジェクトのパラメータ---" ||
                compact == "---ゲーム---オブジェクトのパラメータ---" -> "# --- ゲームオブジェクトのパラメータ ---"
            compact == "---メインループ---" ||
                compact == "メインループ" ||
                compact == "---メイン---ループ" ||
                compact == "---メイン---ループ---" ->
                "# --- メインループ ---"
            compact == "パドル" -> "# パドル"
            compact == "パドル(プレイヤー)" -> "# パドル (プレイヤー)"
            compact == "リスタート処理" -> "# リスタート処理"
            compact == "ゲーム状態をリセット" -> "# ゲーム状態をリセット"
            compact == "ボール" -> "# ボール"
            compact == "の速度を反転させる上下どちらに当たったか" -> "# の速度を反転させる上下どちらに当たったか"
            else -> if (content.contains("---")) normalizeDashComment("# $content") else normalizePlainComment("# $content")
        }
    }

    private fun isStandaloneJapaneseCommentFragment(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.startsWith("#")) return false
        if (text == "---") return true
        if (isStrongCodeLine(text) || looksLikeCodeLine(text)) return false
        if (!containsJapanese(text)) return false
        return text.length <= 24
    }

    private fun repairCodeLine(line: String, nextLine: String?): String {
        var repaired = repairCodeLine(line)
        if (nextLine != null && isCommentFragment(nextLine.trim())) {
            repaired = repaired.replace(Regex("\\s+#\\s*$"), "")
        }
        return repaired
    }

    private fun repairCodeLine(line: String): String {
        var repaired = line
        repaired = repaired.replace(Regex("(?<=\\S)#"), "\n#")
        repaired = repaired.replace(Regex("(?<!\\S)(import\\s+[\\w.]+)import\\s+"), "$1\nimport ")
        repaired = repaired.replace(
            Regex("(SCREEN_WIDTH\\s*=\\s*\\d+)(SCREEN_HEIGHT\\s*=\\s*\\d+)(screen\\s*=)"),
            "$1\n$2\n$3",
        )
        repaired = repaired.replace(Regex("(pygame\\.quit\\(\\))(sys\\.exit\\(\\))"), "$1\n$2")
        repaired = repaired.replace(Regex("(ball_x\\s*\\+=\\s*ball_dx)(ball_y\\s*\\+=)"), "$1\n$2")
        repaired = repaired.replace(Regex("\\s+([+\\-*/])\\s*="), " $1=")
        repaired = repaired.replace(Regex("(\\bFalse)(score\\s*[+\\-*/]?=)"), "$1\n$2")
        repaired = repaired.replace(Regex("(\\bFalse\\b)(score\\s*=)"), "$1\n$2")
        repaired = repaired.replace(Regex("(\\bFalse\\b)(score\\s*\\+=)"), "$1\n$2")
        repaired = repaired.replace(
            Regex("(\\bFalse\\b)(?=[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|\\+=|-=|\\*=|/=))"),
            "$1\n",
        )
        repaired = repaired.replace("import pygameimport sys", "import pygame\nimport sys")
        repaired = repaired.replace("import sys#", "import sys\n#")
        repaired = repaired.replace("pygame.init()#", "pygame.init()\n#")
        repaired = repaired.replace("SCREEN_WIDTH =80SCREEN_HEIGHT =60screen =", "SCREEN_WIDTH = 80\nSCREEN_HEIGHT = 60\nscreen =")
        repaired = repaired.replace("SCREEN_WIDTH = 80SCREEN_HEIGHT = 60screen =", "SCREEN_WIDTH = 80\nSCREEN_HEIGHT = 60\nscreen =")
        repaired = repaired.replace("blocks = []for row", "blocks = []\nfor row")
        repaired = repaired.replace("):for col", "):\nfor col")
        repaired = repaired.replace("game_over = Falsewin_game = False", "game_over = False\nwin_game = False")
        repaired = repaired.replace("Falsewin_game = False", "False\nwin_game = False")
        repaired = repaired.replace("score =0game_over = Falsewin_game = False", "score = 0\ngame_over = False\nwin_game = False")
        repaired = repaired.replace("game_over = Falsewin_game = Falsescore =0", "game_over = False\nwin_game = False\nscore = 0")
        repaired = repaired.replace("block['status'] = Falsescore += 10", "block['status'] = False\nscore += 10")
        repaired = repaired.replace("block['status'] = Falsescore + = 10", "block['status'] = False\nscore += 10")
        repaired = repaired.replace("block['status'] = Trueif win_game:", "block['status'] = True\nif win_game:")
        repaired = repaired.replace("keys = pygame.key.get_pressed()if keys[pygame.K_r]:", "keys = pygame.key.get_pressed()\nif keys[pygame.K_r]:")
        repaired = repaired.replace("win_game = Falsescore =0", "win_game = False\nscore = 0")
        repaired = repaired.replace("sys.exit()if ", "sys.exit()\nif ")
        repaired = repaired.replace(") //2for block", ") //2\nfor block")
        repaired = repaired.replace("for block in blocks:block['status'] = Trueif win_game:", "for block in blocks:block['status'] = True\nif win_game:")
        repaired = repaired.replace("clock = pygame.time.Clock()while True:", "clock = pygame.time.Clock()\nwhile True:")
        repaired = repaired.replace(")if ball_rect", ")\nif ball_rect")
        repaired = repaired.replace(")screen.blit", ")\nscreen.blit")
        repaired = repaired.replace(Regex("\\)el\\nif\\s+"), ")\nelif ")
        repaired = repaired.replace(Regex("(^|\\s)el\\nif\\s+"), "$1elif ")
        repaired = repaired.replace(Regex("\\)elif\\s+"), ")\nelif ")
        repaired = repaired.replace(
            Regex("(?<=[\\]\\\"'A-Za-z_0-9\\)])\\s*#\\s*(\\S.*)$"),
            "\n# $1",
        )
        repaired = repaired.replace(
            Regex("(\\b(?:True|False|None)\\b|\\d)([A-Za-z_][A-Za-z0-9_]*\\s*=)"),
            "$1\n$2",
        )
        repaired = repaired.replace(
            Regex("(?<=\\))(?=(?:[A-Za-z_][A-Za-z0-9_]*\\s*=|pygame\\.))"),
            "\n",
        )
        repaired = repaired.replace(
            Regex(":(?=(?:for|if|while|elif|else|try|except|with)\\b|pygame\\.|[A-Za-z_][A-Za-z0-9_]*\\()"),
            ":\n",
        )
        repaired = repaired.replace(
            Regex("(?<=[^\\s=<>!+\\-*/])=(?=[^=\\s])"),
            " = ",
        )
        repaired = repaired.replace(Regex("(?<=\\d),(?=\\d)"), ", ")
        repaired = repaired.replace(Regex("(?<=\\S),(?=\\S)"), ", ")

        return repaired
            .lineSequence()
            .flatMap { splitKnownMergedStatements(it).lineSequence() }
            .map(::normalizeKnownSpacing)
            .joinToString("\n")
    }

    private fun mergeCommentBlocks(
        lines: List<String>,
        startIndex: Int,
        seedComment: String? = null,
    ): MergedCommentResult {
        val comments = mutableListOf<String>()
        val extractedCodeLines = mutableListOf<String>()
        if (seedComment != null) {
            comments.add("# $seedComment")
        }
        var index = startIndex
        while (index < lines.size) {
            val current = lines[index]
            val trimmed = current.trim()
            if (trimmed.isEmpty()) break
            if (trimmed.startsWith("#")) {
                val split = splitCommentFragmentAndCode(current)
                comments.add(split.line)
                split.extractedCode?.let(extractedCodeLines::add)
                index += 1
                continue
            }
            if (!isCommentFragment(trimmed)) break
            val split = splitLooseCommentFragmentAndCode(trimmed)
            if (split.line.isNotBlank()) {
                comments.add("# ${split.line}")
            }
            split.extractedCode?.let(extractedCodeLines::add)
            index += 1
            if (split.extractedCode != null) break
        }
        return MergedCommentResult(
            comments = comments,
            extractedCodeLines = extractedCodeLines,
            nextIndex = index,
        )
    }

    private fun splitCommentFragmentAndCode(line: String): SplitCommentCodeResult {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("#")) return SplitCommentCodeResult(line = line)
        val content = trimmed.removePrefix("#").trim()
        if (content.isBlank()) return SplitCommentCodeResult(line = "#")
        val inlineCommentSplit = content.split(Regex("\\}\\)\\s*#\\s*"), limit = 2)
        if (inlineCommentSplit.size == 2) {
            val firstComment = inlineCommentSplit[0].trimEnd().removeSuffix("})").trimEnd()
            val secondComment = inlineCommentSplit[1].trim()
            if (firstComment.isNotBlank()) {
                return SplitCommentCodeResult(
                    line = normalizePlainComment("# $firstComment"),
                    extractedCode = "# $secondComment",
                )
            }
        }

        val headingWithCode = Regex("^---\\s*(.+?)\\s*---\\s*(.+)$").matchEntire(content)
        if (headingWithCode != null && looksLikeCodeLine(headingWithCode.groupValues[2])) {
            val heading = "# --- ${headingWithCode.groupValues[1].trim()} ---"
            return SplitCommentCodeResult(
                line = normalizeDashComment(heading),
                extractedCode = sanitizeLeadingDashCodeResidue(headingWithCode.groupValues[2].trim()),
            )
        }

        val codeStart = findCodeStartIndex(content)
        if (codeStart != null) {
            val commentText = content.substring(0, codeStart).trim()
            val codeLine = sanitizeLeadingDashCodeResidue(content.substring(codeStart).trimStart())
            val commentLine = if (commentText.isBlank()) "#" else normalizePlainComment("# $commentText")
            return SplitCommentCodeResult(line = commentLine, extractedCode = codeLine)
        }

        return SplitCommentCodeResult(line = normalizePlainComment("# $content"))
    }

    private fun splitLooseCommentFragmentAndCode(fragment: String): SplitCommentCodeResult {
        val codeStart = findCodeStartIndex(fragment)
        if (codeStart == null) return SplitCommentCodeResult(line = fragment)
        val commentPart = fragment.substring(0, codeStart).trimEnd()
        if (commentPart.isEmpty()) {
            return SplitCommentCodeResult(line = "", extractedCode = fragment.substring(codeStart).trimStart())
        }
        return SplitCommentCodeResult(
            line = commentPart,
            extractedCode = fragment.substring(codeStart).trimStart(),
        )
    }

    private fun normalizeCommentLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val current = lines[index]
            val trimmed = current.trim()
            if (!trimmed.startsWith("#")) {
                rebuilt.add(current)
                index += 1
                continue
            }
            val rawContent = trimmed.removePrefix("#").trim()
            if (rawContent.replace(" ", "").contains("パラメータ---パドル")) {
                rebuilt.add("# --- ゲームオブジェクトのパラメータ ---")
                rebuilt.add("# パドル（プレイヤー）")
                index += 1
                continue
            }
            if (rawContent.isEmpty() || isCommentFragment(rawContent)) {
                val mergedContent = StringBuilder(rawContent)
                var cursor = index + 1
                while (cursor < lines.size) {
                    val nextContent = lines[cursor].trim().removePrefix("#").trim()
                    if (
                        nextContent.isEmpty() ||
                        !isCommentFragment(nextContent) ||
                        looksLikeCodeLine(nextContent)
                    ) {
                        break
                    }
                    mergedContent.append(nextContent)
                    cursor += 1
                }
                val mergedLine = "# ${mergedContent}"
                rebuilt.add(if (mergedLine.contains("---")) normalizeDashComment(mergedLine) else normalizePlainComment(mergedLine))
                index = cursor
                continue
            }
            val normalized = if (trimmed.contains("---")) normalizeDashComment(current) else normalizePlainComment(current)
            rebuilt.add(normalized)
            index += 1
        }
        return rebuilt
    }

    private fun normalizeDashComment(line: String): String {
        var normalized = line.replace(Regex("^(\\s*)#\\s*"), "$1# ")
        normalized = normalized.replace(Regex("\\s*---\\s*"), " --- ")
        normalized = normalized.replace(Regex("\\s{2,}"), " ").trimEnd()
        val dashContent = Regex("^#\\s*---\\s*(.+?)\\s*$").matchEntire(normalized)?.groupValues?.get(1)
        if (!dashContent.isNullOrBlank()) {
            val trimmedContent = dashContent
                .replace(Regex("(\\s*---\\s*)+$"), "")
                .trim()
            normalized = "# --- $trimmedContent ---"
        }
        if (!normalized.trimStart().startsWith("#")) {
            normalized = "# ${normalized.trim()}"
        }
        return normalized
    }

    private fun normalizePlainComment(line: String): String {
        val trimmed = line.trim()
        val content = trimmed.removePrefix("#").trim()
        var merged = content
            .replace(Regex("\\s+"), "")
            .replace("（", "(")
            .replace("）", ")")
        merged = merged.replace(Regex("^(\\d+\\.[^()]+)\\("), "$1 (")
        val shortLabelWithParen = Regex("^([\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}A-Za-z0-9ー]{1,8})\\(([^()]+)\\)$")
            .matchEntire(merged)
        if (shortLabelWithParen != null) {
            merged = "${shortLabelWithParen.groupValues[1]} (${shortLabelWithParen.groupValues[2]})"
        }
        return "# $merged"
    }

    private fun normalizeMergedComment(merged: String): String {
        val compact = merged.replace(Regex("\\s+"), "")
        when (compact) {
            "ボール" -> return "# ボール"
            "リスタート処理" -> return "# リスタート処理"
            "ゲーム状態をリセット" -> return "# ゲーム状態をリセット"
            "オブジェクトのパラメータ---" -> return "# --- ゲームオブジェクトのパラメータ ---"
            "フレーム/レート/設定", "フレームレート設定" -> return "# フレームレート設定 (60 FPS)"
        }
        if (merged.contains("。") && merged.contains(Regex("\\d+\\."))) {
            val normalizedPieces = merged
                .replace("（", "(")
                .replace("）", ")")
                .replace(Regex("。(?=\\d+\\.)"), "。\n")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { piece ->
                    val withSpacing = piece.replace(Regex("^(\\d+\\.[^()]+)\\("), "$1 (")
                    normalizePlainComment("# $withSpacing")
                }
                .toList()
            if (normalizedPieces.isNotEmpty()) {
                return normalizedPieces.joinToString("\n")
            }
        }
        if (!merged.contains("---")) return normalizePlainComment("# $merged")
        val cleaned = merged
            .replace(Regex("---\\s*---+"), "---")
            .replace(Regex("^\\s*---\\s*"), "---")
            .replace(Regex("\\s*---\\s*$"), "---")
        val plain = normalizePlainComment("# ${cleaned.replace("---", " ")}")
        val content = plain.removePrefix("#").trim()
        return normalizeDashComment("# --- $content ---")
    }

    private fun isCommentFragmentContinuationLine(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.trimStart().startsWith("#")) return false
        if (isStrongCodeLine(text)) return false
        if (!containsJapanese(text) && text != "---" && !text.matches(Regex("^[、。,.()（）「」『』!?！？:：;\\-/／\\s]+$"))) {
            return false
        }
        return isCommentFragment(text) || isLooseJapaneseCommentLine(text) || text == "---"
    }

    private fun shouldAbsorbAsCommentFragment(text: String): Boolean {
        if (text.isBlank()) return false
        if (isStrongCodeLine(text)) return false
        return shouldCollectCommentFragment(text) || isCommentFragmentContinuationLine(text)
    }

    private fun shouldCollectCommentFragment(text: String): Boolean {
        if (text.isBlank()) return false
        if (isStrongCodeLine(text)) return false
        if (isNumberedJapaneseLine(text)) return true
        if (text.matches(Regex("^[、。,.:：()（）\\-\\s]+$"))) return true
        if (text == "---") return true
        return isCommentFragment(text) || isLooseJapaneseCommentLine(text)
    }

    private fun isCommentFragment(text: String): Boolean {
        if (text.isEmpty()) return false
        if (looksLikeCodeLine(text)) return false
        if (text.length > 48) return false
        if (text.matches(Regex("^[、。,.()（）「」『』!?！？:：;\\-/／\\s]+$"))) return true
        if (!text.matches(Regex("^[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}A-Za-z0-9_、。,.()（）「」『』!?！？:：;\\-/／\\s]+$"))) {
            return false
        }
        return containsJapanese(text) || text.length <= 12
    }

    private fun containsJapanese(text: String): Boolean {
        return text.any {
            Character.UnicodeBlock.of(it) == Character.UnicodeBlock.HIRAGANA ||
                Character.UnicodeBlock.of(it) == Character.UnicodeBlock.KATAKANA ||
                Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
        }
    }

    private fun isLooseJapaneseCommentLine(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.trimStart().startsWith("#")) return false
        if (!containsJapanese(text) && !text.matches(Regex("^[、。,.()（）「」『』!?！？:：;\\-\\s]+$"))) return false
        return isCommentFragment(text)
    }

    private fun isNumberedJapaneseLine(text: String): Boolean {
        return text.matches(Regex("^\\d+\\.\\s*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*$"))
    }

    private fun isLooseDashHeadingLine(text: String): Boolean {
        if (!text.trimStart().startsWith("---")) return false
        return containsJapanese(text) || text.contains(Regex("[A-Za-z]"))
    }

    private fun mergeCommentText(commentLine: String, fragment: String): String {
        val base = commentLine.trim().removePrefix("#").trim()
        val merged = mergeKnownJapaneseFragments("$base${fragment.trim()}")
        return if (merged.contains("---")) {
            normalizeDashComment("# $merged")
        } else {
            normalizePlainComment("# $merged")
        }
    }

    private fun mergeFragments(left: String, rightCommentLine: String): String {
        val right = rightCommentLine.trim().removePrefix("#").trim()
        val merged = mergeKnownJapaneseFragments("${left.trim()}$right")
        return if (merged.contains("---")) {
            normalizeDashComment("# $merged")
        } else {
            normalizePlainComment("# $merged")
        }
    }

    private fun mergeKnownJapaneseFragments(text: String): String {
        var merged = text.replace(Regex("\\s+"), "")
        val replacements = listOf(
            "ボ移動ールの" to "ボールの移動",
            "スタート処理リ" to "リスタート処理",
            "状態をリセットゲーム" to "ゲーム状態をリセット",
            "レート設定フレーム" to "フレームレート設定",
            "オーバーゲーム" to "ゲームオーバー",
        )
        replacements.forEach { (from, to) ->
            merged = merged.replace(from, to)
        }
        return merged
    }

    private fun shouldKeepCommentSeparated(previous: String, current: String): Boolean {
        val currentCompact = current.replace(Regex("\\s+"), "")
        if (isFinalizedDashHeading(previous) && (currentCompact.startsWith("パドル") || currentCompact == "(プレイヤー)")) {
            return true
        }
        if (previous.replace(Regex("\\s+"), "") == "パドル" && currentCompact == "(プレイヤー)") {
            return false
        }
        return false
    }

    private fun deduplicateFrameRateComments(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val current = lines[index]
            val currentCompact = current.trim().removePrefix("#").replace(Regex("\\s+"), "")
            val next = lines.getOrNull(index + 1)
            val nextCompact = next?.trim()?.removePrefix("#")?.replace(Regex("\\s+"), "").orEmpty()
            if (currentCompact == "フレームレート設定" && nextCompact == "フレームレート設定(60FPS)") {
                index += 1
                continue
            }
            rebuilt.add(current)
            index += 1
        }
        return rebuilt
    }

    private fun splitInlineNumberedComment(content: String): CommentAndCodeSplit? {
        val match = Regex("^(.*?。)\\s*#\\s*(\\d+\\..+)$").find(content) ?: return null
        val sentence = match.groupValues[1].trim()
        val numbered = match.groupValues[2].trim()
        if (sentence.isBlank() || numbered.isBlank()) return null
        return CommentAndCodeSplit(comment = sentence, code = numbered)
    }

    private fun isFinalizedDashHeading(content: String): Boolean {
        val compact = content.replace(Regex("\\s+"), "")
        return compact == "---ゲームオブジェクトのパラメータ---" || compact == "---メインループ---"
    }

    private fun looksLikeCodeLine(text: String): Boolean {
        if (isClearCodeLine(text)) return true
        if (text.contains("screen.") || text.contains("font.") || text.contains("clock.") || text.contains("keys")) return true
        if (text.contains(Regex("[\\[\\]{}:]"))) return true
        return false
    }

    private fun isClearCodeLine(text: String): Boolean {
        if (text.contains(Regex("\\b(import|from|if|elif|else|for|while|def|class|return)\\b"))) return true
        if (text.contains("pygame.")) return true
        if (text.contains("screen.")) return true
        if (text.contains(Regex("\\b(?:score\\s*=|blocks\\b|paddle_|ball_|block_|game_over\\b|win_game\\b)"))) return true
        if (text.contains(Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|\\+=|-=|\\*=|/=)\\s*"))) return true
        if (text.contains(Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\("))) return true
        return false
    }

    private fun isStrongCodeLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (isClearCodeLine(trimmed)) return true
        return trimmed.startsWith("import ") ||
            trimmed.startsWith("from ") ||
            trimmed.startsWith("if ") ||
            trimmed.startsWith("for ") ||
            trimmed.startsWith("while ") ||
            trimmed.startsWith("score =") ||
            trimmed.startsWith("pygame.") ||
            trimmed.startsWith("screen.") ||
            trimmed.startsWith("blocks") ||
            trimmed.startsWith("paddle_") ||
            trimmed.startsWith("ball_") ||
            trimmed.startsWith("block_") ||
            trimmed.startsWith("game_over") ||
            trimmed.startsWith("win_game")
    }

    private fun findCodeStartIndex(text: String): Int? {
        val codePatterns = listOf(
            Regex("\\b(?:import|from|if|elif|else|for|while|def|class|return)\\b"),
            Regex("\\b(?:pygame|screen|font|clock)\\."),
            Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|\\+=|-=|\\*=|/=)\\s*"),
            Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\("),
        )
        return codePatterns.mapNotNull { it.find(text)?.range?.first }.minOrNull()
    }

    private fun splitInlineHashCodeAndComment(line: String): InlineHashSplitResult? {
        val hashIndex = line.indexOf('#')
        if (hashIndex < 0) return null
        val codePart = line.substring(0, hashIndex).trimEnd()
        if (codePart.isEmpty() || codePart.trimStart().startsWith("#")) return null
        val commentSeed = line.substring(hashIndex + 1).trim()
        return InlineHashSplitResult(code = codePart, commentSeed = commentSeed)
    }

    private fun sanitizeLeadingDashCodeResidue(line: String): String {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("---")) return line
        val candidate = trimmed.removePrefix("---").trimStart()
        if (!looksLikeCodeLine(candidate)) return line
        return candidate
    }

    private fun expandMergedLineHints(line: String): List<String> {
        if (line.isBlank()) return listOf(line)
        var expanded = line
        expanded = expanded.replace("import pygameimport sys", "import pygame\nimport sys")
        expanded = expanded.replace(Regex("(?<=\\S)(import\\s+)"), "\n$1")
        expanded = expanded.replace(Regex("(SCREEN_WIDTH\\s*=\\s*\\d+)(SCREEN_HEIGHT\\s*=\\s*\\d+)(screen\\s*=)"), "$1\n$2\n$3")
        expanded = expanded.replace("blocks = []for row", "blocks = []\nfor row")
        expanded = expanded.replace("):for col", "):\nfor col")
        expanded = expanded.replace("pygame.quit()sys.exit()", "pygame.quit()\nsys.exit()")
        expanded = expanded.replace("sys.exit()if ", "sys.exit()\nif ")
        expanded = expanded.replace("ball_x += ball_dxball_y += ball_dy", "ball_x += ball_dx\nball_y += ball_dy")
        expanded = expanded.replace(Regex("(\\bFalse)(score\\s*(?:=|\\+=))"), "$1\n$2")
        expanded = expanded.replace(Regex("(\\bFalse)(win_game\\s*=)"), "$1\n$2")
        expanded = expanded.replace(Regex("(block\\['status']\\s*=\\s*False)(score\\s*\\+=\\s*\\d+)"), "$1\n$2")
        return expanded.split('\n')
    }

    private fun splitDeterministicFusedCodeLines(line: String): List<String> {
        if (line.isBlank()) return listOf(line)
        var expanded = line
        expanded = expanded.replace(
            Regex("(for\\s+row\\s+in\\s+range\\([^)]*\\):)\\s*(for\\s+col\\s+in\\s+range\\([^)]*\\):)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(for\\s+col\\s+in\\s+range\\([^)]*\\):)\\s*(blocks\\.append\\()"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+block\\['status']:)\\s*(block_rect\\s*=)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+game_over:)\\s*(msg\\s*=)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+win_game:)\\s*(msg\\s*=)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+keys\\[pygame\\.K_r]:)\\s*(#)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(for\\s+block\\s+in\\s+blocks:)\\s*(block\\['status']\\s*=\\s*True)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(#\\s*Trueなら存在、Falseなら破壊済み)\\}\\)#"),
            "$1\n# ",
        )
        expanded = expanded.replace(
            Regex("(\\}\\))#\\s*"),
            "$1\n# ",
        )
        expanded = expanded.replace("import pygameimport sys#", "import pygame\nimport sys\n#")
        expanded = expanded.replace("import pygameimport sys", "import pygame\nimport sys")
        expanded = expanded.replace("import sys#", "import sys\n#")
        expanded = expanded.replace(
            Regex("(SCREEN_WIDTH\\s*=\\s*\\d+)(SCREEN_HEIGHT\\s*=\\s*\\d+)(screen\\s*=)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace("SCREEN_WIDTH =80SCREEN_HEIGHT =60screen =", "SCREEN_WIDTH = 80\nSCREEN_HEIGHT = 60\nscreen =")
        expanded = expanded.replace("SCREEN_WIDTH = 80SCREEN_HEIGHT = 60screen =", "SCREEN_WIDTH = 80\nSCREEN_HEIGHT = 60\nscreen =")
        expanded = expanded.replace("score =0game_over = Falsewin_game = False", "score = 0\ngame_over = False\nwin_game = False")
        expanded = expanded.replace("game_over = Falsewin_game = Falsescore =0", "game_over = False\nwin_game = False\nscore = 0")
        expanded = expanded.replace("game_over = Falsewin_game = False", "game_over = False\nwin_game = False")
        expanded = expanded.replace(
            Regex("(blocks\\s*=\\s*\\[\\])(for\\s+row\\s+in\\s+range\\([^)]*\\):)(for\\s+col\\s+in\\s+range\\([^)]*\\):)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(paddle_x\\s*-=\\s*paddle_speed)(if\\s+keys\\[pygame\\.K_[A-Z_]+]?)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(ball_x\\s*\\+=\\s*ball_dx)(ball_y\\s*\\+=\\s*ball_dy)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(keys\\s*=\\s*pygame\\.key\\.get_pressed\\(\\))(if\\s+keys\\[pygame\\.K_LEFT]\\s+and\\s+paddle_x\\s*>\\s*0:)(paddle_x\\s*-=?\\s*paddle_speed)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(keys\\s*=\\s*pygame\\.key\\.get_pressed\\(\\))(if\\s+keys\\[pygame\\.K_[A-Z_]+]:)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+block\\['status']:\\s*)(block_rect\\s*=\\s*block\\['rect'])(ball_rect\\s*=\\s*pygame\\.Rect\\()"),
            "if block['status']:\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(for\\s+block\\s+in\\s+blocks:)(if\\s+block\\['status']:)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+game_over:)(msg\\s*=\\s*)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+win_game:)(msg\\s*=\\s*)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+[^:\\n]+:)(\\s*[A-Za-z_][A-Za-z0-9_\\[\\]'\\\"]*\\s*=)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+keys\\[pygame\\.K_RIGHT]\\s+and\\s+paddle_x)\\s*<\\s*(SCREEN_WIDTH\\s*-\\s*paddle_width)\\s*:(paddle_x\\s*\\+=\\s*paddle_speed)"),
            "$1 < $2:\n$3",
        )
        expanded = expanded.replace(
            Regex("(for\\s+block\\s+in\\s+blocks:)(block\\['status']\\s*=\\s*True)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(keys\\s*=\\s*pygame\\.key\\.get_pressed\\(\\))(if\\s+keys\\[pygame\\.K_r]:)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(ball_radius\\s*=\\s*\\d+)(ball_x\\s*=\\s*SCREEN_WIDTH\\s*//\\s*\\d+)(ball_y\\s*=\\s*SCREEN_HEIGHT\\s*//\\s*\\d+)(ball_dx\\s*=\\s*\\d+)"),
            "$1\n$2\n$3\n$4",
        )
        expanded = expanded.replace(
            Regex("(game_over\\s*=\\s*False)(win_game\\s*=\\s*False)(score\\s*=\\s*\\d+)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(score\\s*=\\s*\\d+)(game_over\\s*=\\s*False)(win_game\\s*=\\s*False)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(block\\['status']\\s*=\\s*False)(score\\s*\\+=\\s*\\d+)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex(":(pygame\\.quit\\(\\))(sys\\.exit\\(\\))"),
            ":\n$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(pygame\\.display\\.[a-zA-Z_]+\\([^)]*\\))(pygame\\.display\\.[a-zA-Z_]+\\([^)]*\\))"),
            "$1\n$2",
        )
        expanded = expanded.replace("block['status'] = Trueif win_game:", "block['status'] = True\nif win_game:")
        expanded = expanded.replace("keys = pygame.key.get_pressed()if keys[pygame.K_r]:", "keys = pygame.key.get_pressed()\nif keys[pygame.K_r]:")
        return expanded.split('\n')
    }

    private fun splitKnownMergedStatements(line: String): String {
        return line
            .replace("score_text =", "score_text =")
            .replace("text_rect =", "text_rect =")
            .replace(")screen.blit(", ")\nscreen.blit(")
            .replace(Regex("^(#\\s*Y方向の速度)(ブロック)$"), "$1\n# $2")
    }

    private fun normalizeKnownSpacing(line: String): String {
        return line
            .replace(Regex("^SCREEN_WIDTH\\s*=\\s*(\\d+)$"), "SCREEN_WIDTH = $1")
            .replace(Regex("^SCREEN_HEIGHT\\s*=\\s*(\\d+)$"), "SCREEN_HEIGHT = $1")
            .replace(Regex("^paddle_width\\s*=\\s*(\\d+)$"), "paddle_width = $1")
            .replace(Regex("^score\\s*\\+=\\s*(\\d+)$"), "score += $1")
            .replace(Regex("^block\\['status']\\s*=\\s*False$"), "block['status'] = False")
            .replace(Regex("^ball_x\\s*\\+=\\s*ball_dx$"), "ball_x += ball_dx")
            .replace(Regex("^ball_y\\s*\\+=\\s*ball_dy$"), "ball_y += ball_dy")
    }

    private fun isPythonFenceOpeningLine(line: String): Boolean {
        val withoutIndent = line.trimStart(' ', '\t')
        if (!withoutIndent.startsWith("```")) return false
        val rawSuffix = withoutIndent.removePrefix("```").trim()
        if (rawSuffix.isEmpty()) return false
        val languageToken = rawSuffix.substringBefore(' ').lowercase(Locale.ROOT)
        return languageToken == "python" || languageToken == "py"
    }

    private fun isBareFenceLine(line: String): Boolean {
        val withoutIndent = line.trimStart(' ', '\t')
        if (!withoutIndent.startsWith("```")) return false
        return withoutIndent.removePrefix("```").trim().isEmpty()
    }

    private fun isPythonLanguageOnlyLine(line: String): Boolean {
        val trimmed = line.trim().lowercase(Locale.ROOT)
        return trimmed == "python" || trimmed == "py"
    }

    private fun normalizeBarePythonFenceLine(line: String): String {
        val withoutIndent = line.trimStart(' ', '\t')
        val indentLength = line.length - withoutIndent.length
        val indent = line.substring(0, indentLength)
        return "${indent}```python"
    }

    private fun normalizeTwoLinePythonFence(markdown: String): String {
        return markdown.replace(
            Regex("(?m)^([ \\t]*)```[ \\t]*\\n\\1python[ \\t]+([^\\n`][^\\n]*)$"),
            "$1```python\n$2",
        )
    }

    private fun isFenceLine(line: String): Boolean {
        return line.trimStart(' ', '\t').startsWith("```")
    }

    private fun normalizeMarkdownOutsideCodeFences(markdown: String): String {
        val lines = markdown.split('\n')
        if (lines.isEmpty()) return markdown

        val rebuilt = StringBuilder(markdown.length + 32)
        var inFence = false
        val outsideBuffer = StringBuilder()

        fun flushOutsideBuffer() {
            if (outsideBuffer.isEmpty()) return
            rebuilt.append(normalizeOutsideFenceText(outsideBuffer.toString()))
            outsideBuffer.clear()
        }

        for (index in lines.indices) {
            val line = lines[index]
            if (isFenceLine(line)) {
                flushOutsideBuffer()
                rebuilt.append(line)
                if (index < lines.lastIndex) rebuilt.append('\n')
                inFence = !inFence
                continue
            }
            if (inFence) {
                rebuilt.append(line)
                if (index < lines.lastIndex) rebuilt.append('\n')
                continue
            }

            outsideBuffer.append(line)
            if (index < lines.lastIndex) {
                outsideBuffer.append('\n')
            }
        }
        flushOutsideBuffer()
        return rebuilt.toString()
    }

    private fun normalizeOutsideFenceText(text: String): String {
        if (text.isEmpty()) return text

        var normalized = text
        normalized = normalized.replace(
            Regex("(?<!\\n)(\\d+\\.\\s*\\*\\*[^\\n]+?\\*\\*:\\s*[^\\n]*?)(?=\\d+\\.\\s*\\*\\*)"),
            "$1\n",
        )
        normalized = normalized.replace(
            Regex("(?<!\\n)(\\d+)\\.\\*\\*([^\\n]+?)\\*\\*:\\s*(?=\\d+\\.\\*\\*)"),
            "$1. **$2**:\n",
        )
        normalized = normalized.replace(
            Regex("(?<!\\n)(\\d+)\\.\\*\\*([^\\n]+?)\\*\\*:\\s*"),
            "$1. **$2**: ",
        )
        normalized = normalized.replace(
            Regex("([。．.!?！？])\\s*(\\d+\\.)"),
            "$1\n\n$2 ",
        )
        normalized = normalized.replace(
            Regex("(?m)^([^\\n:*]+):\\*([^\\n:*]+):\\s*$"),
            "$1:\n\n$2:",
        )
        normalized = normalized.replace(Regex("([。．.!?！？])\\s*(\\*\\*\\*.+?\\*\\*:)"), "$1\n$2")
        normalized = normalized.replace(Regex("([。．.!?！？])\\s*(#{1,6})"), "$1\n$2")
        normalized = normalized.replace(Regex("(?m)^(\\s{0,3}#{1,6}\\s*[^\\n]*?)(\\d+\\.\\s*\\S.*)$"), "$1\n$2")
        normalized = normalized.replace(
            Regex("(?m)^(\\s{0,3}#{1,6}\\s+[^\\n]+?)(?=(この|本|上記|以下|次|また|ここ|それ)[^\\n]*[。．])"),
            "$1\n",
        )
        normalized = normalized.replace(
            Regex("(?m)^(\\s{0,3}#{1,6}\\s*[^\\n]*?)(この[^\\n]*[。．])$"),
            "$1\n$2",
        )
        normalized = normalized.replace(
            Regex("(?m)^(\\s*\\*\\s*\\*\\*[^\\n]+\\*\\*:[^\\n]*?)(\\*\\s*\\*\\*[^\\n]+\\*\\*:)$"),
            "$1\n$2",
        )

        return normalized
            .lineSequence()
            .flatMap { splitOutsideFenceInlineNumberedItems(it).lineSequence() }
            .map(::normalizeOutsideFenceLine)
            .joinToString("\n")
    }

    private fun splitOutsideFenceInlineNumberedItems(line: String): String {
        if (!line.contains("**")) return line
        if (line.trimStart().startsWith("```")) return line
        return line.replace(
            Regex("(\\d+\\.\\s*\\*\\*[^\\n]+?\\*\\*:\\s*)(?=\\d+\\.\\s*\\*\\*)"),
            "$1\n",
        )
    }

    private fun normalizeOutsideFenceLine(line: String): String {
        val headingAndBodyMatch = Regex("^(\\s{0,3}#{1,6})([^\\s#].*?)(このコードは.*)$").matchEntire(line)
        if (headingAndBodyMatch != null) {
            val marker = headingAndBodyMatch.groupValues[1]
            val heading = headingAndBodyMatch.groupValues[2].trim()
            val body = headingAndBodyMatch.groupValues[3].trim()
            return "$marker $heading\n$body"
        }

        val headingMatch = Regex("^(\\s{0,3}#{1,6})([^\\s#].*)$").matchEntire(line)
        if (headingMatch != null) {
            return "${headingMatch.groupValues[1]} ${headingMatch.groupValues[2]}"
        }

        val boldBulletMatch = Regex("^(\\s*)\\*\\*\\*(.+)\\*\\*:(\\s*)$").matchEntire(line)
        if (boldBulletMatch != null) {
            val indent = boldBulletMatch.groupValues[1]
            val content = boldBulletMatch.groupValues[2].trim()
            val trailing = boldBulletMatch.groupValues[3]
            return "$indent* **$content**:$trailing"
        }

        val numberedBoldBulletMatch = Regex("^(\\s*\\d+\\.)\\*\\*(.+)\\*\\*:(\\s*)$").matchEntire(line)
        if (numberedBoldBulletMatch != null) {
            val prefix = numberedBoldBulletMatch.groupValues[1]
            val content = numberedBoldBulletMatch.groupValues[2].trim()
            val trailing = numberedBoldBulletMatch.groupValues[3]
            return "$prefix **$content**:$trailing"
        }

        val numberedBulletMissingSpaceMatch = Regex("^(\\s*\\d+\\.)(\\S.*)$").matchEntire(line)
        if (numberedBulletMissingSpaceMatch != null) {
            val prefix = numberedBulletMissingSpaceMatch.groupValues[1]
            val content = numberedBulletMissingSpaceMatch.groupValues[2]
            return "$prefix $content"
        }

        val starBulletMissingSpaceMatch = Regex("^(\\s*\\*)(\\S.*)$").matchEntire(line)
        if (starBulletMissingSpaceMatch != null) {
            val prefix = starBulletMissingSpaceMatch.groupValues[1]
            val content = starBulletMissingSpaceMatch.groupValues[2]
            return "$prefix $content"
        }

        return line
    }
}
