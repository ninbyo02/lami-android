package com.sonusid.ollama.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sonusid.ollama.ui.screens.settings.DevPreviewSettings
import com.sonusid.ollama.ui.screens.settings.INFO_X_OFFSET_MAX
import com.sonusid.ollama.ui.screens.settings.INFO_X_OFFSET_MIN

internal class ReadyPreviewLayoutState(
    cardMaxHeightDp: MutableState<Int>,
    innerBottomDp: MutableState<Int>,
    outerBottomDp: MutableState<Int>,
    innerVPadDp: MutableState<Int>,
    charXOffsetDp: MutableState<Int>,
    charYOffsetDp: MutableState<Int>,
    infoXOffsetDp: MutableState<Int>,
    infoYOffsetDp: MutableState<Int>,
    headerOffsetLimitDp: MutableState<Int>,
    headerLeftXOffsetDp: MutableState<Int>,
    headerLeftYOffsetDp: MutableState<Int>,
    headerRightXOffsetDp: MutableState<Int>,
    headerRightYOffsetDp: MutableState<Int>,
    cardMinHeightDp: MutableState<Int>,
    detailsMaxHeightDp: MutableState<Int>,
    detailsMaxLines: MutableState<Int>,
    headerSpacerDp: MutableState<Int>,
    bodySpacerDp: MutableState<Int>,
    private val onDevSettingsChange: (DevPreviewSettings) -> Unit,
) {
    var cardMaxHeightDp by cardMaxHeightDp
    var innerBottomDp by innerBottomDp
    var outerBottomDp by outerBottomDp
    var innerVPadDp by innerVPadDp
    var charXOffsetDp by charXOffsetDp
    var charYOffsetDp by charYOffsetDp
    var infoXOffsetDp by infoXOffsetDp
    var infoYOffsetDp by infoYOffsetDp
    var headerOffsetLimitDp by headerOffsetLimitDp
    var headerLeftXOffsetDp by headerLeftXOffsetDp
    var headerLeftYOffsetDp by headerLeftYOffsetDp
    var headerRightXOffsetDp by headerRightXOffsetDp
    var headerRightYOffsetDp by headerRightYOffsetDp
    var cardMinHeightDp by cardMinHeightDp
    var detailsMaxHeightDp by detailsMaxHeightDp
    var detailsMaxLines by detailsMaxLines
    var headerSpacerDp by headerSpacerDp
    var bodySpacerDp by bodySpacerDp

    fun updateFrom(devSettings: DevPreviewSettings) {
        cardMaxHeightDp = devSettings.cardMaxHeightDp
        innerBottomDp = devSettings.innerBottomDp
        outerBottomDp = devSettings.outerBottomDp
        innerVPadDp = devSettings.innerVPadDp
        charXOffsetDp = devSettings.charXOffsetDp
        charYOffsetDp = devSettings.charYOffsetDp
        infoXOffsetDp = devSettings.infoXOffsetDp.coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX)
        infoYOffsetDp = devSettings.infoYOffsetDp
        headerOffsetLimitDp = devSettings.headerOffsetLimitDp
        headerLeftXOffsetDp = devSettings.headerLeftXOffsetDp
        headerLeftYOffsetDp = devSettings.headerLeftYOffsetDp
        headerRightXOffsetDp = devSettings.headerRightXOffsetDp
        headerRightYOffsetDp = devSettings.headerRightYOffsetDp
        cardMinHeightDp = devSettings.cardMinHeightDp
        detailsMaxHeightDp = devSettings.detailsMaxHeightDp
        detailsMaxLines = devSettings.detailsMaxLines
        headerSpacerDp = devSettings.headerSpacerDp
        bodySpacerDp = devSettings.bodySpacerDp
    }

    fun propagateDevSettings() {
        val clampedInfoXOffsetDp = infoXOffsetDp.coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX)
        infoXOffsetDp = clampedInfoXOffsetDp
        onDevSettingsChange(
            DevPreviewSettings(
                cardMaxHeightDp = cardMaxHeightDp,
                innerBottomDp = innerBottomDp,
                outerBottomDp = outerBottomDp,
                innerVPadDp = innerVPadDp,
                charXOffsetDp = charXOffsetDp,
                charYOffsetDp = charYOffsetDp,
                infoXOffsetDp = clampedInfoXOffsetDp,
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
        )
    }

    fun updateDevSettings(block: ReadyPreviewLayoutState.() -> Unit) {
        block()
        propagateDevSettings()
    }
}

@Composable
internal fun rememberReadyPreviewLayoutState(
    devSettings: DevPreviewSettings,
    onDevSettingsChange: (DevPreviewSettings) -> Unit
): ReadyPreviewLayoutState {
    val cardMaxHeightDp = rememberSaveable(devSettings.cardMaxHeightDp) { mutableStateOf(devSettings.cardMaxHeightDp) }
    val innerBottomDp = rememberSaveable(devSettings.innerBottomDp) { mutableStateOf(devSettings.innerBottomDp) }
    val outerBottomDp = rememberSaveable(devSettings.outerBottomDp) { mutableStateOf(devSettings.outerBottomDp) }
    val innerVPadDp = rememberSaveable(devSettings.innerVPadDp) { mutableStateOf(devSettings.innerVPadDp) }
    val charXOffsetDp = rememberSaveable(devSettings.charXOffsetDp) { mutableStateOf(devSettings.charXOffsetDp) }
    val charYOffsetDp = rememberSaveable(devSettings.charYOffsetDp) { mutableStateOf(devSettings.charYOffsetDp) }
    val infoXOffsetDp = rememberSaveable(devSettings.infoXOffsetDp) { mutableStateOf(devSettings.infoXOffsetDp) }
    val infoYOffsetDp = rememberSaveable(devSettings.infoYOffsetDp) { mutableStateOf(devSettings.infoYOffsetDp) }
    val headerOffsetLimitDp = rememberSaveable(devSettings.headerOffsetLimitDp) { mutableStateOf(devSettings.headerOffsetLimitDp) }
    val headerLeftXOffsetDp = rememberSaveable(devSettings.headerLeftXOffsetDp) { mutableStateOf(devSettings.headerLeftXOffsetDp) }
    val headerLeftYOffsetDp = rememberSaveable(devSettings.headerLeftYOffsetDp) { mutableStateOf(devSettings.headerLeftYOffsetDp) }
    val headerRightXOffsetDp = rememberSaveable(devSettings.headerRightXOffsetDp) { mutableStateOf(devSettings.headerRightXOffsetDp) }
    val headerRightYOffsetDp = rememberSaveable(devSettings.headerRightYOffsetDp) { mutableStateOf(devSettings.headerRightYOffsetDp) }
    val cardMinHeightDp = rememberSaveable(devSettings.cardMinHeightDp) { mutableStateOf(devSettings.cardMinHeightDp) }
    val detailsMaxHeightDp = rememberSaveable(devSettings.detailsMaxHeightDp) { mutableStateOf(devSettings.detailsMaxHeightDp) }
    val detailsMaxLines = rememberSaveable(devSettings.detailsMaxLines) { mutableStateOf(devSettings.detailsMaxLines) }
    val headerSpacerDp = rememberSaveable(devSettings.headerSpacerDp) { mutableStateOf(devSettings.headerSpacerDp) }
    val bodySpacerDp = rememberSaveable(devSettings.bodySpacerDp) { mutableStateOf(devSettings.bodySpacerDp) }

    val state = remember(
        cardMaxHeightDp,
        innerBottomDp,
        outerBottomDp,
        innerVPadDp,
        charXOffsetDp,
        charYOffsetDp,
        infoXOffsetDp,
        infoYOffsetDp,
        headerOffsetLimitDp,
        headerLeftXOffsetDp,
        headerLeftYOffsetDp,
        headerRightXOffsetDp,
        headerRightYOffsetDp,
        cardMinHeightDp,
        detailsMaxHeightDp,
        detailsMaxLines,
        headerSpacerDp,
        bodySpacerDp,
        onDevSettingsChange
    ) {
        ReadyPreviewLayoutState(
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
            onDevSettingsChange = onDevSettingsChange
        )
    }

    LaunchedEffect(devSettings) {
        state.updateFrom(devSettings)
    }

    return state
}
