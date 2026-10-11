package io.github.ninbyo02.lami.ui.screens.settings

internal data class AnimationDefaults(
    val base: ReadyAnimationSettings,
    val insertion: InsertionAnimationSettings,
)

internal data class AllAnimations(
    val animations: Map<AnimationType, AnimationDefaults>,
)

internal data class AnimationInputState(
    val frameInput: String,
    val intervalInput: String,
    val framesError: String?,
    val intervalError: String?,
    val insertionPattern1FramesInput: String,
    val insertionPattern1WeightInput: String,
    val insertionPattern1IntervalInput: String,
    val insertionPattern2FramesInput: String,
    val insertionPattern2WeightInput: String,
    val insertionPattern2IntervalInput: String,
    val insertionIntervalInput: String,
    val insertionEveryNInput: String,
    val insertionProbabilityInput: String,
    val insertionCooldownInput: String,
    val insertionEnabled: Boolean,
    val insertionExclusive: Boolean,
    val insertionPattern1FramesError: String?,
    val insertionPattern1WeightError: String?,
    val insertionPattern1IntervalError: String?,
    val insertionPattern2FramesError: String?,
    val insertionPattern2WeightError: String?,
    val insertionPattern2IntervalError: String?,
    val insertionIntervalError: String?,
    val insertionEveryNError: String?,
    val insertionProbabilityError: String?,
    val insertionCooldownError: String?,
    val appliedBase: ReadyAnimationSettings,
    val appliedInsertion: InsertionAnimationSettings,
)

// 暫定: statusAnimationMap に近い値をここで簡易マッピングする。
internal fun buildExtraAnimationDefaults(
    settingsPreferences: SettingsPreferences,
): Map<AnimationType, AnimationDefaults> {
    val (errorLightBaseDefaults, errorLightInsertionDefaults) =
        settingsPreferences.defaultAnimationSettingsForState(SpriteState.ERROR)
    val (errorHeavyBaseDefaults, errorHeavyInsertionDefaults) =
        settingsPreferences.defaultErrorAnimationSettingsForKey("ErrorHeavy")
    val (offlineBaseDefaults, offlineInsertionDefaults) =
        settingsPreferences.defaultAnimationSettingsForState(SpriteState.OFFLINE)
    return mapOf(
        AnimationType.IDLE to AnimationDefaults(
            base = ReadyAnimationSettings(listOf(8, 8, 8, 8), intervalMs = 180),
            insertion = InsertionAnimationSettings(
                enabled = true,
                patterns = listOf(
                    InsertionPattern(listOf(5, 5, 8, 8, 8, 5, 5), weight = 3, intervalMs = 180),
                    InsertionPattern(listOf(5, 5, 7, 7, 7, 5, 5), weight = 1, intervalMs = 180),
                ),
                intervalMs = 180,
                everyNLoops = 6,
                probabilityPercent = 60,
                cooldownLoops = 5,
                exclusive = true,
            ),
        ),
        AnimationType.THINKING to AnimationDefaults(
            base = ReadyAnimationSettings.THINKING_DEFAULT,
            insertion = InsertionAnimationSettings.THINKING_DEFAULT,
        ),
        AnimationType.TALK_SHORT to AnimationDefaults(
            base = ReadyAnimationSettings(listOf(0, 6, 2, 6, 0), intervalMs = 130),
            insertion = InsertionAnimationSettings.TALKING_DEFAULT.copy(
                enabled = false,
                patterns = listOf(InsertionPattern(listOf(0, 6, 2, 6, 0))),
                intervalMs = 130,
            ),
        ),
        AnimationType.TALK_LONG to AnimationDefaults(
            base = ReadyAnimationSettings(listOf(0, 4, 6, 4, 4, 6, 4, 0), intervalMs = 190),
            insertion = InsertionAnimationSettings(
                enabled = true,
                patterns = listOf(InsertionPattern(listOf(1))),
                intervalMs = 190,
                everyNLoops = 2,
                probabilityPercent = 100,
                cooldownLoops = 0,
                exclusive = true,
            ),
        ),
        AnimationType.TALK_CALM to AnimationDefaults(
            base = ReadyAnimationSettings(listOf(7, 4, 7, 8, 7), intervalMs = 280),
            insertion = InsertionAnimationSettings.TALKING_DEFAULT.copy(
                enabled = false,
                patterns = listOf(InsertionPattern(listOf(7, 4, 7, 8, 7))),
                intervalMs = 280,
            ),
        ),
        AnimationType.ERROR_LIGHT to AnimationDefaults(
            base = errorLightBaseDefaults,
            insertion = errorLightInsertionDefaults,
        ),
        AnimationType.ERROR_HEAVY to AnimationDefaults(
            base = errorHeavyBaseDefaults,
            insertion = errorHeavyInsertionDefaults,
        ),
        AnimationType.OFFLINE_LOOP to AnimationDefaults(
            base = offlineBaseDefaults,
            insertion = offlineInsertionDefaults,
        ),
    )
}

internal fun List<Int>.toFrameInputText(): String =
    joinToString(separator = ",") { value -> (value + 1).toString() }

internal data class InsertionPatternInputs(
    val pattern1FramesInput: String,
    val pattern1WeightInput: String,
    val pattern1IntervalInput: String,
    val pattern2FramesInput: String,
    val pattern2WeightInput: String,
    val pattern2IntervalInput: String,
)

internal fun List<InsertionPattern>.toInsertionPatternInputs(): InsertionPatternInputs {
    val pattern1 = getOrNull(0)
    val pattern2 = getOrNull(1)
    return InsertionPatternInputs(
        pattern1FramesInput = pattern1?.frames()?.toFrameInputText().orEmpty(),
        pattern1WeightInput = (pattern1?.weight ?: 1).toString(),
        pattern1IntervalInput = pattern1?.intervalMs?.toString().orEmpty(),
        pattern2FramesInput = pattern2?.frames()?.toFrameInputText().orEmpty(),
        pattern2WeightInput = (pattern2?.weight ?: 0).toString(),
        pattern2IntervalInput = pattern2?.intervalMs?.toString().orEmpty(),
    )
}

internal fun List<InsertionPatternConfig>.toInsertionPatterns(): List<InsertionPattern> =
    map { pattern ->
        InsertionPattern(
            frameSequence = pattern.frames,
            weight = pattern.weight,
            intervalMs = pattern.intervalMs,
        )
    }

internal fun AnimationDefaults.toInputState(): AnimationInputState =
    insertion.patterns.toInsertionPatternInputs().let { inputs ->
        AnimationInputState(
            frameInput = base.frames().toFrameInputText(),
            intervalInput = base.intervalMs.toString(),
            framesError = null,
            intervalError = null,
            insertionPattern1FramesInput = inputs.pattern1FramesInput,
            insertionPattern1WeightInput = inputs.pattern1WeightInput,
            insertionPattern1IntervalInput = inputs.pattern1IntervalInput,
            insertionPattern2FramesInput = inputs.pattern2FramesInput,
            insertionPattern2WeightInput = inputs.pattern2WeightInput,
            insertionPattern2IntervalInput = inputs.pattern2IntervalInput,
            insertionIntervalInput = insertion.intervalMs?.toString().orEmpty(),
            insertionEveryNInput = insertion.everyNLoops.toString(),
            insertionProbabilityInput = insertion.probabilityPercent.toString(),
            insertionCooldownInput = insertion.cooldownLoops.toString(),
            insertionEnabled = insertion.enabled,
            insertionExclusive = insertion.exclusive,
            insertionPattern1FramesError = null,
            insertionPattern1WeightError = null,
            insertionPattern1IntervalError = null,
            insertionPattern2FramesError = null,
            insertionPattern2WeightError = null,
            insertionPattern2IntervalError = null,
            insertionIntervalError = null,
            insertionEveryNError = null,
            insertionProbabilityError = null,
            insertionCooldownError = null,
            appliedBase = base,
            appliedInsertion = insertion,
        )
    }

internal data class AnimationSummary(
    val label: String,
    val frames: List<Int>,
    val intervalMs: Int,
    val everyNLoops: Int? = null,
    val probabilityPercent: Int? = null,
    val cooldownLoops: Int? = null,
    val exclusive: Boolean? = null,
    val enabled: Boolean = false,
)

internal data class InsertionPreviewValues(
    val framesText: String,
    val intervalText: String,
    val everyNText: String,
    val probabilityText: String,
    val cooldownText: String,
    val exclusiveText: String,
    val pattern1WeightText: String,
    val pattern2WeightText: String,
)

internal fun buildInsertionPreviewSummary(
    label: String,
    enabled: Boolean,
    pattern1FramesInput: String,
    pattern1WeightInput: String,
    pattern2FramesInput: String,
    pattern2WeightInput: String,
    intervalInput: String,
    everyNInput: String,
    probabilityInput: String,
    cooldownInput: String,
    exclusive: Boolean,
    defaultIntervalMs: Int,
    frameCount: Int
): Pair<AnimationSummary, InsertionPreviewValues> {
    val framesText = pattern1FramesInput.trim()
    val pattern2FramesText = pattern2FramesInput.trim()
    val primaryFramesText = framesText.ifEmpty { pattern2FramesText }
    val intervalText = intervalInput.trim()
    val everyNText = everyNInput.trim()
    val probabilityText = probabilityInput.trim()
    val cooldownText = cooldownInput.trim()
    val pattern1WeightText = if (framesText.isEmpty()) {
        "0"
    } else {
        pattern1WeightInput.trim().ifEmpty { "1" }
    }
    val pattern2WeightText = if (pattern2FramesText.isEmpty()) {
        "0"
    } else {
        pattern2WeightInput.trim().ifEmpty { "1" }
    }

    val previewValues = InsertionPreviewValues(
        framesText = primaryFramesText.ifEmpty { "-" },
        intervalText = intervalText.ifEmpty { "-" },
        everyNText = everyNText.ifEmpty { "-" },
        probabilityText = probabilityText.ifEmpty { "-" },
        cooldownText = cooldownText.ifEmpty { "-" },
        exclusiveText = if (exclusive) "ON" else "OFF",
        pattern1WeightText = pattern1WeightText,
        pattern2WeightText = pattern2WeightText,
    )

    val primaryPatternText = primaryFramesText
        .replace("｜", "|")
        .replace("，", ",")
        .replace("、", ",")
        .split("|")
        .map { token -> token.trim() }
        .firstOrNull { token -> token.isNotEmpty() }
        .orEmpty()
    val parsedFrames = primaryPatternText
        .split(",")
        .map { token -> token.trim() }
        .filter { token -> token.isNotEmpty() }
        .mapNotNull { token ->
            token.toIntOrNull()
                ?.takeIf { value -> value in 1..frameCount }
                ?.minus(1)
        }
    val frames = parsedFrames.ifEmpty { listOf(0) }
    val intervalMs = intervalText.toIntOrNull()?.takeIf { it > 0 } ?: defaultIntervalMs
    val everyNLoops = everyNText.toIntOrNull()
    val probabilityPercent = probabilityText.toIntOrNull()
    val cooldownLoops = cooldownText.toIntOrNull()

    val summary = AnimationSummary(
        label = label,
        frames = frames,
        intervalMs = intervalMs,
        everyNLoops = everyNLoops,
        probabilityPercent = probabilityPercent,
        cooldownLoops = cooldownLoops,
        exclusive = exclusive,
        enabled = enabled,
    )

    return summary to previewValues
}
