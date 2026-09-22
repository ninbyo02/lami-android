package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.runtime.saveable.listSaver
import org.json.JSONObject

internal const val INFO_X_OFFSET_MIN = -500
internal const val INFO_X_OFFSET_MAX = 500

internal data class DevPreviewSettings(
    val cardMaxHeightDp: Int,
    val innerBottomDp: Int,
    val outerBottomDp: Int,
    val innerVPadDp: Int,
    val charXOffsetDp: Int,
    val charYOffsetDp: Int,
    val infoXOffsetDp: Int,
    val infoYOffsetDp: Int,
    val headerOffsetLimitDp: Int,
    val headerLeftXOffsetDp: Int,
    val headerLeftYOffsetDp: Int,
    val headerRightXOffsetDp: Int,
    val headerRightYOffsetDp: Int,
    val cardMinHeightDp: Int,
    val detailsMaxHeightDp: Int,
    val detailsMaxLines: Int,
    val headerSpacerDp: Int,
    val bodySpacerDp: Int,
)

internal data class ReadyPreviewUiState(
    val charXOffsetDp: Int,
    val charYOffsetDp: Int,
    val effectiveMinHeightDp: Int,
    val effectiveCardMaxH: Int?,
    val infoXOffsetDp: Int,
    val infoYOffsetDp: Int,
    val headerLeftXOffsetDp: Int,
    val headerLeftYOffsetDp: Int,
    val headerRightXOffsetDp: Int,
    val headerRightYOffsetDp: Int,
    val baseMaxHeightDp: Int,
    val effectiveDetailsMaxH: Int,
    val outerBottomDp: Int,
    val innerBottomDp: Int,
    val innerVPadDp: Int,
    val detailsMaxHeightDp: Int,
    val cardMaxHeightDp: Int,
    val cardMinHeightDp: Int,
    val detailsMaxLines: Int,
    val headerOffsetLimitDp: Int,
    val headerSpacerDp: Int,
    val bodySpacerDp: Int,
)

private object DevDefaults {
    const val cardMaxHeightDp = 130
    const val innerBottomDp = 0
    const val outerBottomDp = 0
    const val innerVPadDp = 8
    const val charXOffsetDp = 0
    const val charYOffsetDp = 0
    const val infoXOffsetDp = -109
    const val infoYOffsetDp = -4
    const val headerOffsetLimitDp = 150
    const val headerLeftXOffsetDp = 114
    const val headerLeftYOffsetDp = 1
    const val headerRightXOffsetDp = 0
    const val headerRightYOffsetDp = 0
    const val cardMinHeightDp = 156
    const val detailsMaxHeightDp = 40
    const val detailsMaxLines = 2
    const val headerSpacerDp = 0
    const val bodySpacerDp = 0

    fun toDevPreviewSettings(): DevPreviewSettings =
        DevPreviewSettings(
            cardMaxHeightDp = cardMaxHeightDp,
            innerBottomDp = innerBottomDp,
            outerBottomDp = outerBottomDp,
            innerVPadDp = innerVPadDp,
            charXOffsetDp = charXOffsetDp,
            charYOffsetDp = charYOffsetDp,
            infoXOffsetDp = infoXOffsetDp,
            infoYOffsetDp = infoYOffsetDp,
            headerOffsetLimitDp = headerOffsetLimitDp,
            headerLeftXOffsetDp = headerLeftXOffsetDp,
            headerLeftYOffsetDp = headerLeftYOffsetDp,
            headerRightXOffsetDp = headerRightXOffsetDp,
            headerRightYOffsetDp = headerRightYOffsetDp,
            cardMinHeightDp = cardMinHeightDp,
            detailsMaxHeightDp = detailsMaxHeightDp,
            detailsMaxLines = detailsMaxLines,
            headerSpacerDp = headerSpacerDp,
            bodySpacerDp = bodySpacerDp,
        )
}

internal fun defaultDevPreviewSettings(): DevPreviewSettings =
    DevDefaults.toDevPreviewSettings()

internal data class DevSettingsDefaults(
    val cardMaxHeightDp: Int?,
    val innerBottomDp: Int?,
    val outerBottomDp: Int?,
    val innerVPadDp: Int?,
    val charXOffsetDp: Int?,
    val charYOffsetDp: Int?,
    val infoXOffsetDp: Int?,
    val infoYOffsetDp: Int?,
    val headerOffsetLimitDp: Int?,
    val headerLeftXOffsetDp: Int?,
    val headerLeftYOffsetDp: Int?,
    val headerRightXOffsetDp: Int?,
    val headerRightYOffsetDp: Int?,
    val cardMinHeightDp: Int?,
    val detailsMaxHeightDp: Int?,
    val detailsMaxLines: Int?,
    val headerSpacerDp: Int?,
    val bodySpacerDp: Int?,
) {
    companion object {
        fun fromJson(json: String?): DevSettingsDefaults? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val dev = JSONObject(json).optJSONObject("dev") ?: return null
                DevSettingsDefaults(
                    cardMaxHeightDp = dev.optIntOrNull("cardMaxHeightDp"),
                    innerBottomDp = dev.optIntOrNull("innerBottomDp"),
                    outerBottomDp = dev.optIntOrNull("outerBottomDp"),
                    innerVPadDp = dev.optIntOrNull("innerVPadDp"),
                    charXOffsetDp = dev.optIntOrNull("charXOffsetDp"),
                    charYOffsetDp = dev.optIntOrNull("charYOffsetDp"),
                    infoXOffsetDp = dev.optIntOrNull("infoXOffsetDp"),
                    infoYOffsetDp = dev.optIntOrNull("infoYOffsetDp"),
                    headerOffsetLimitDp = dev.optIntOrNull("headerOffsetLimitDp"),
                    headerLeftXOffsetDp = dev.optIntOrNull("headerLeftXOffsetDp"),
                    headerLeftYOffsetDp = dev.optIntOrNull("headerLeftYOffsetDp"),
                    headerRightXOffsetDp = dev.optIntOrNull("headerRightXOffsetDp"),
                    headerRightYOffsetDp = dev.optIntOrNull("headerRightYOffsetDp"),
                    cardMinHeightDp = dev.optIntOrNull("cardMinHeightDp") ?: dev.optIntOrNull("minHeightDp"),
                    detailsMaxHeightDp = dev.optIntOrNull("detailsMaxHeightDp") ?: dev.optIntOrNull("detailsMaxH"),
                    detailsMaxLines = dev.optIntOrNull("detailsMaxLines") ?: dev.optIntOrNull("detailsLines"),
                    headerSpacerDp = dev.optIntOrNull("headerSpacerDp"),
                    bodySpacerDp = dev.optIntOrNull("bodySpacerDp"),
                )
            }.getOrNull()
        }
    }
}

internal fun DevSettingsDefaults.toDevPreviewSettings(): DevPreviewSettings =
    DevPreviewSettings(
        cardMaxHeightDp = cardMaxHeightDp ?: DevDefaults.cardMaxHeightDp,
        innerBottomDp = innerBottomDp ?: DevDefaults.innerBottomDp,
        outerBottomDp = outerBottomDp ?: DevDefaults.outerBottomDp,
        innerVPadDp = innerVPadDp ?: DevDefaults.innerVPadDp,
        charXOffsetDp = charXOffsetDp ?: DevDefaults.charXOffsetDp,
        charYOffsetDp = charYOffsetDp ?: DevDefaults.charYOffsetDp,
        infoXOffsetDp = (infoXOffsetDp ?: DevDefaults.infoXOffsetDp).coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX),
        infoYOffsetDp = infoYOffsetDp ?: DevDefaults.infoYOffsetDp,
        headerOffsetLimitDp = headerOffsetLimitDp ?: DevDefaults.headerOffsetLimitDp,
        headerLeftXOffsetDp = headerLeftXOffsetDp ?: DevDefaults.headerLeftXOffsetDp,
        headerLeftYOffsetDp = headerLeftYOffsetDp ?: DevDefaults.headerLeftYOffsetDp,
        headerRightXOffsetDp = headerRightXOffsetDp ?: DevDefaults.headerRightXOffsetDp,
        headerRightYOffsetDp = headerRightYOffsetDp ?: DevDefaults.headerRightYOffsetDp,
        cardMinHeightDp = cardMinHeightDp ?: DevDefaults.cardMinHeightDp,
        detailsMaxHeightDp = detailsMaxHeightDp ?: DevDefaults.detailsMaxHeightDp,
        detailsMaxLines = detailsMaxLines ?: DevDefaults.detailsMaxLines,
        headerSpacerDp = headerSpacerDp ?: DevDefaults.headerSpacerDp,
        bodySpacerDp = bodySpacerDp ?: DevDefaults.bodySpacerDp,
    )

internal fun devPreviewSettingsSaver() = listSaver<DevPreviewSettings, Int>(
    save = { settings ->
        listOf(
            settings.cardMaxHeightDp,
            settings.innerBottomDp,
            settings.outerBottomDp,
            settings.innerVPadDp,
            settings.charXOffsetDp,
            settings.charYOffsetDp,
            settings.infoYOffsetDp,
            settings.headerOffsetLimitDp,
            settings.headerLeftXOffsetDp,
            settings.headerLeftYOffsetDp,
            settings.headerRightXOffsetDp,
            settings.headerRightYOffsetDp,
            settings.cardMinHeightDp,
            settings.detailsMaxHeightDp,
            settings.detailsMaxLines,
            settings.headerSpacerDp,
            settings.bodySpacerDp,
            settings.infoXOffsetDp,
        )
    },
    restore = { values ->
        if (values.size < 16) {
            DevDefaults.toDevPreviewSettings()
        } else {
            val charXOffsetIndex = if (values.size >= 18) 4 else -1
            val charYOffsetIndex = if (values.size >= 18) 5 else 4
            val infoYOffsetIndex = if (values.size >= 18) 6 else 5
            val headerOffsetLimitIndex = if (values.size >= 18) 7 else 6
            val headerLeftXIndex = if (values.size >= 18) 8 else 7
            val headerLeftYIndex = if (values.size >= 18) 9 else 8
            val headerRightXIndex = if (values.size >= 18) 10 else 9
            val headerRightYIndex = if (values.size >= 18) 11 else 10
            val cardMinHeightIndex = if (values.size >= 18) 12 else 11
            val detailsMaxHeightIndex = if (values.size >= 18) 13 else 12
            val detailsMaxLinesIndex = if (values.size >= 18) 14 else 13
            val headerSpacerIndex = if (values.size >= 18) 15 else 14
            val bodySpacerIndex = if (values.size >= 18) 16 else 15
            val infoXOffsetIndex = if (values.size >= 18) 17 else 16
            DevPreviewSettings(
                cardMaxHeightDp = values[0],
                innerBottomDp = values[1],
                outerBottomDp = values[2],
                innerVPadDp = values[3],
                charXOffsetDp = values.getOrNull(charXOffsetIndex) ?: DevDefaults.charXOffsetDp,
                charYOffsetDp = values[charYOffsetIndex],
                infoYOffsetDp = values[infoYOffsetIndex],
                infoXOffsetDp = values.getOrNull(infoXOffsetIndex)?.coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX) ?: DevDefaults.infoXOffsetDp,
                headerOffsetLimitDp = values[headerOffsetLimitIndex],
                headerLeftXOffsetDp = values[headerLeftXIndex],
                headerLeftYOffsetDp = values[headerLeftYIndex],
                headerRightXOffsetDp = values[headerRightXIndex],
                headerRightYOffsetDp = values[headerRightYIndex],
                cardMinHeightDp = values[cardMinHeightIndex],
                detailsMaxHeightDp = values[detailsMaxHeightIndex],
                detailsMaxLines = values[detailsMaxLinesIndex],
                headerSpacerDp = values[headerSpacerIndex],
                bodySpacerDp = values[bodySpacerIndex],
            )
        }
    }
)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null
