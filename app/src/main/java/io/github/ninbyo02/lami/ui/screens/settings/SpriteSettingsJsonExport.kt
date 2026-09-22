package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.data.SpriteSheetConfig
import org.json.JSONArray
import org.json.JSONObject

private const val EXPORT_JSON_WEIGHT_KEY = "weight"
private const val EXPORT_JSON_PATTERN_INTERVAL_MS_KEY = "intervalMs"

// UI調整: 下部操作ボタンをピル形状に戻し、矢印を真紫で視認性を向上。
internal fun buildSettingsJsonAnimationOnly(
    animationType: AnimationType,
    readyBase: ReadyAnimationSettings,
    talkingBase: ReadyAnimationSettings,
    readyInsertion: InsertionAnimationSettings,
    talkingInsertion: InsertionAnimationSettings,
): String {
    val root = buildAnimationSettingsJsonRoot(
        animationType = animationType,
        readyBase = readyBase,
        talkingBase = talkingBase,
        readyInsertion = readyInsertion,
        talkingInsertion = talkingInsertion,
    )
    return root.toString(2)
}

internal fun buildSettingsJsonFull(
    animationType: AnimationType,
    spriteSheetConfig: SpriteSheetConfig,
    readyBase: ReadyAnimationSettings,
    talkingBase: ReadyAnimationSettings,
    readyInsertion: InsertionAnimationSettings,
    talkingInsertion: InsertionAnimationSettings,
    devSettings: DevPreviewSettings,
): String {
    val root = buildAnimationSettingsJsonRoot(
        animationType = animationType,
        readyBase = readyBase,
        talkingBase = talkingBase,
        readyInsertion = readyInsertion,
        talkingInsertion = talkingInsertion,
    )
    root.put("spriteSheetConfig", spriteSheetConfig.toJsonObject())
    root.put("dev", devSettings.toJsonObject())
    return root.toString(2)
}

private fun buildAnimationSettingsJsonRoot(
    animationType: AnimationType,
    readyBase: ReadyAnimationSettings,
    talkingBase: ReadyAnimationSettings,
    readyInsertion: InsertionAnimationSettings,
    talkingInsertion: InsertionAnimationSettings,
): JSONObject {
    val root = JSONObject()
    // JSON互換のため、保存キーは従来の内部名を維持する
    root.put("animationType", animationType.internalKey)
    root.put(
        "ready",
        JSONObject()
            .put("base", readyBase.toJsonObject())
            .put("insertion", readyInsertion.toJsonObject())
    )
    root.put(
        "talking",
        JSONObject()
            .put("base", talkingBase.toJsonObject())
            .put("insertion", talkingInsertion.toJsonObject())
    )
    return root
}

internal fun ReadyAnimationSettings.toJsonObject(): JSONObject =
    JSONObject()
        .put("frames", frames().toJsonArray())
        .put("intervalMs", intervalMs)

internal fun InsertionAnimationSettings.toJsonObject(): JSONObject =
    JSONObject().apply {
        put("enabled", enabled)
        put("patterns", patterns.toPatternsJsonArray())
        if (intervalMs != null) {
            put("intervalMs", intervalMs)
        }
        put("everyNLoops", everyNLoops)
        put("probabilityPercent", probabilityPercent)
        put("cooldownLoops", cooldownLoops)
        put("exclusive", exclusive)
    }

private fun List<InsertionPattern>.toPatternsJsonArray(): JSONArray =
    JSONArray().apply {
        forEach { pattern ->
            val patternObject = JSONObject()
                .put("frames", pattern.frames().toJsonArray())
                .put(EXPORT_JSON_WEIGHT_KEY, pattern.weight)
            pattern.intervalMs?.let { intervalMs ->
                patternObject.put(EXPORT_JSON_PATTERN_INTERVAL_MS_KEY, intervalMs)
            }
            put(patternObject)
        }
    }

private fun SpriteSheetConfig.toJsonObject(): JSONObject =
    JSONObject()
        .put("rows", rows)
        .put("cols", cols)
        .put("frameWidth", frameWidth)
        .put("frameHeight", frameHeight)
        .put(
            "boxes",
            JSONArray().apply {
                boxes.forEach { box ->
                    put(
                        JSONObject()
                            .put("frameIndex", box.frameIndex)
                            .put("x", box.x)
                            .put("y", box.y)
                            .put("width", box.width)
                            .put("height", box.height)
                    )
                }
            }
        )

internal fun DevPreviewSettings.toJsonObject(): JSONObject =
    JSONObject()
        .put("cardMaxHeightDp", cardMaxHeightDp)
        .put("charXOffsetDp", charXOffsetDp)
        .put("charYOffsetDp", charYOffsetDp)
        .put("infoXOffsetDp", infoXOffsetDp)
        .put("infoYOffsetDp", infoYOffsetDp)
        .put("headerOffsetLimitDp", headerOffsetLimitDp)
        .put("headerLeftXOffsetDp", headerLeftXOffsetDp)
        .put("headerLeftYOffsetDp", headerLeftYOffsetDp)
        .put("headerRightXOffsetDp", headerRightXOffsetDp)
        .put("headerRightYOffsetDp", headerRightYOffsetDp)
        .put("innerVPadDp", innerVPadDp)
        .put("innerBottomDp", innerBottomDp)
        .put("outerBottomDp", outerBottomDp)
        .put("cardMinHeightDp", cardMinHeightDp)
        .put("detailsMaxHeightDp", detailsMaxHeightDp)
        .put("detailsMaxLines", detailsMaxLines)
        .put("headerSpacerDp", headerSpacerDp)
        .put("bodySpacerDp", bodySpacerDp)

private fun List<Int>.toJsonArray(): JSONArray =
    JSONArray().apply { forEach { value -> put(value) } }
