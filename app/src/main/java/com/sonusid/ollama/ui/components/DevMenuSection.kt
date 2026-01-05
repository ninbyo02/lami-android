package com.sonusid.ollama.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonusid.ollama.BuildConfig
import com.sonusid.ollama.ui.screens.settings.INFO_X_OFFSET_MAX
import com.sonusid.ollama.ui.screens.settings.INFO_X_OFFSET_MIN
import com.sonusid.ollama.ui.screens.settings.ReadyPreviewUiState
import kotlin.math.abs

internal data class DevMenuUiState(
    val devUnlocked: Boolean,
    val devMenuEnabled: Boolean,
    val devExpanded: Boolean,
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

internal data class DevMenuCallbacks(
    val onDevExpandedChange: (Boolean) -> Unit,
    val onCopy: () -> Unit,
    val onCharYOffsetChange: (Int) -> Unit,
    val onInfoXOffsetChange: (Int) -> Unit,
    val onInfoYOffsetChange: (Int) -> Unit,
    val onHeaderOffsetLimitChange: (Int) -> Unit,
    val onHeaderLeftXOffsetChange: (Int) -> Unit,
    val onHeaderLeftYOffsetChange: (Int) -> Unit,
    val onHeaderRightXOffsetChange: (Int) -> Unit,
    val onHeaderRightYOffsetChange: (Int) -> Unit,
    val onOuterBottomChange: (Int) -> Unit,
    val onInnerBottomChange: (Int) -> Unit,
    val onInnerVPadChange: (Int) -> Unit,
    val onDetailsMaxHeightChange: (Int) -> Unit,
    val onCardMaxHeightChange: (Int) -> Unit,
    val onCardMinHeightChange: (Int) -> Unit,
    val onDetailsMaxLinesChange: (Int) -> Unit,
    val onHeaderSpacerChange: (Int) -> Unit,
    val onBodySpacerChange: (Int) -> Unit,
)

@Composable
internal fun DebugDevMenuSection(
    devUnlocked: Boolean,
    layoutState: ReadyPreviewLayoutState,
    previewUiState: ReadyPreviewUiState,
    onCopyDevJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.DEBUG) return
    DevMenuSection(
        devUnlocked = devUnlocked,
        layoutState = layoutState,
        previewUiState = previewUiState,
        onCopyDevJson = onCopyDevJson,
        modifier = modifier,
    )
}

@Composable
internal fun DevMenuSection(
    devUnlocked: Boolean,
    layoutState: ReadyPreviewLayoutState,
    previewUiState: ReadyPreviewUiState,
    onCopyDevJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!devUnlocked) return
    var devMenuEnabled by rememberSaveable { mutableStateOf(false) }
    var devExpanded by rememberSaveable { mutableStateOf(false) }

    val devMenuUiState = DevMenuUiState(
        devUnlocked = devUnlocked,
        devMenuEnabled = devMenuEnabled,
        devExpanded = devExpanded,
        charYOffsetDp = layoutState.charYOffsetDp,
        effectiveMinHeightDp = previewUiState.effectiveMinHeightDp,
        effectiveCardMaxH = previewUiState.effectiveCardMaxH,
        infoXOffsetDp = layoutState.infoXOffsetDp,
        infoYOffsetDp = layoutState.infoYOffsetDp,
        headerLeftXOffsetDp = layoutState.headerLeftXOffsetDp,
        headerLeftYOffsetDp = layoutState.headerLeftYOffsetDp,
        headerRightXOffsetDp = layoutState.headerRightXOffsetDp,
        headerRightYOffsetDp = layoutState.headerRightYOffsetDp,
        baseMaxHeightDp = previewUiState.baseMaxHeightDp,
        effectiveDetailsMaxH = previewUiState.effectiveDetailsMaxH,
        outerBottomDp = layoutState.outerBottomDp,
        innerBottomDp = layoutState.innerBottomDp,
        innerVPadDp = layoutState.innerVPadDp,
        detailsMaxHeightDp = layoutState.detailsMaxHeightDp,
        cardMaxHeightDp = layoutState.cardMaxHeightDp,
        cardMinHeightDp = layoutState.cardMinHeightDp,
        detailsMaxLines = layoutState.detailsMaxLines,
        headerOffsetLimitDp = layoutState.headerOffsetLimitDp,
        headerSpacerDp = layoutState.headerSpacerDp,
        bodySpacerDp = layoutState.bodySpacerDp,
    )
    val devMenuCallbacks = DevMenuCallbacks(
        onDevExpandedChange = { expanded -> devExpanded = expanded },
        onCopy = onCopyDevJson,
        onCharYOffsetChange = { delta ->
            layoutState.updateDevSettings { charYOffsetDp = (charYOffsetDp + delta).coerceIn(-200, 200) }
        },
        onInfoXOffsetChange = { delta ->
            layoutState.updateDevSettings { infoXOffsetDp = (infoXOffsetDp + delta).coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX) }
        },
        onInfoYOffsetChange = { delta ->
            layoutState.updateDevSettings { infoYOffsetDp = (infoYOffsetDp + delta).coerceIn(-200, 200) }
        },
        onHeaderOffsetLimitChange = { delta ->
            layoutState.updateDevSettings {
                headerOffsetLimitDp = (headerOffsetLimitDp + delta).coerceIn(0, 400)
                headerLeftXOffsetDp = headerLeftXOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                headerLeftYOffsetDp = headerLeftYOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                headerRightXOffsetDp = headerRightXOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                headerRightYOffsetDp = headerRightYOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
            }
        },
        onHeaderLeftXOffsetChange = { delta ->
            layoutState.updateDevSettings { headerLeftXOffsetDp = (headerLeftXOffsetDp + delta).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) }
        },
        onHeaderLeftYOffsetChange = { delta ->
            layoutState.updateDevSettings { headerLeftYOffsetDp = (headerLeftYOffsetDp + delta).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) }
        },
        onHeaderRightXOffsetChange = { delta ->
            layoutState.updateDevSettings { headerRightXOffsetDp = (headerRightXOffsetDp + delta).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) }
        },
        onHeaderRightYOffsetChange = { delta ->
            layoutState.updateDevSettings { headerRightYOffsetDp = (headerRightYOffsetDp + delta).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) }
        },
        onOuterBottomChange = { delta ->
            layoutState.updateDevSettings { outerBottomDp = (outerBottomDp + delta).coerceIn(0, 80) }
        },
        onInnerBottomChange = { delta ->
            layoutState.updateDevSettings { innerBottomDp = (innerBottomDp + delta).coerceIn(0, 80) }
        },
        onInnerVPadChange = { delta ->
            layoutState.updateDevSettings { innerVPadDp = (innerVPadDp + delta).coerceIn(0, 24) }
        },
        onDetailsMaxHeightChange = { delta ->
            layoutState.updateDevSettings { detailsMaxHeightDp = (detailsMaxHeightDp + delta).coerceIn(0, 1200) }
        },
        onCardMaxHeightChange = { delta ->
            layoutState.updateDevSettings { cardMaxHeightDp = (cardMaxHeightDp + delta).coerceIn(0, 1200) }
        },
        onCardMinHeightChange = { delta ->
            layoutState.updateDevSettings { cardMinHeightDp = (cardMinHeightDp + delta).coerceIn(0, 320) }
        },
        onDetailsMaxLinesChange = { delta ->
            layoutState.updateDevSettings { detailsMaxLines = (detailsMaxLines + delta).coerceIn(1, 6) }
        },
        onHeaderSpacerChange = { delta ->
            layoutState.updateDevSettings { headerSpacerDp = (headerSpacerDp + delta).coerceIn(0, 24) }
        },
        onBodySpacerChange = { delta ->
            layoutState.updateDevSettings { bodySpacerDp = (bodySpacerDp + delta).coerceIn(0, 24) }
        },
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "開発メニュー",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "開発メニューを表示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = devMenuEnabled,
                onCheckedChange = { enabled ->
                    devMenuEnabled = enabled
                    devExpanded = enabled
                }
            )
        }
        DevMenuBlock(uiState = devMenuUiState, callbacks = devMenuCallbacks)
    }
}

@Composable
private fun DevMenuBlock(
    uiState: DevMenuUiState,
    callbacks: DevMenuCallbacks,
) {
    if (uiState.devUnlocked && uiState.devMenuEnabled) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val effectiveMaxLabel = uiState.effectiveCardMaxH?.toString() ?: "∞"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { callbacks.onDevExpandedChange(!uiState.devExpanded) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val devArrow = if (uiState.devExpanded) "▴" else "▾"
                            Text(
                                text = "DEV $devArrow",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "MinH:${uiState.effectiveMinHeightDp} / MaxH:${effectiveMaxLabel}  InfoX:${uiState.infoXOffsetDp}  InfoY:${uiState.infoYOffsetDp}  HdrL:(${uiState.headerLeftXOffsetDp},${uiState.headerLeftYOffsetDp})  HdrR:(${uiState.headerRightXOffsetDp},${uiState.headerRightYOffsetDp})",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = callbacks.onCopy,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("JSONコピー")
                        }
                    }

                    AnimatedVisibility(visible = uiState.devExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Offsets",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "CharY:${uiState.charYOffsetDp}dp",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onCharYOffsetChange(-1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onCharYOffsetChange(1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "InfoX:${uiState.infoXOffsetDp}dp / 情報ブロックX",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onInfoXOffsetChange(-1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onInfoXOffsetChange(1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "InfoY:${uiState.infoYOffsetDp}dp / 情報ブロックY",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onInfoYOffsetChange(-1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onInfoYOffsetChange(1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "HeaderLimit:${uiState.headerOffsetLimitDp}dp / 見出し移動限界",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onHeaderOffsetLimitChange(10) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onHeaderOffsetLimitChange(-10) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "HeaderLeftX:${uiState.headerLeftXOffsetDp}dp / 見出し左X",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onHeaderLeftXOffsetChange(-1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onHeaderLeftXOffsetChange(1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "HeaderLeftY:${uiState.headerLeftYOffsetDp}dp / 見出し左Y",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onHeaderLeftYOffsetChange(-1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onHeaderLeftYOffsetChange(1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "HeaderRightX:${uiState.headerRightXOffsetDp}dp / 見出し右X",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onHeaderRightXOffsetChange(-1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onHeaderRightXOffsetChange(1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "HeaderRightY:${uiState.headerRightYOffsetDp}dp / 見出し右Y",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onHeaderRightYOffsetChange(-1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onHeaderRightYOffsetChange(1) }) {
                                        Text("▼")
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Padding",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "OuterBottom:${abs(uiState.outerBottomDp)}dp / カード下余白",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onOuterBottomChange(1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onOuterBottomChange(-1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "InnerBottom:${abs(uiState.innerBottomDp)}dp / 情報ブロック下余白",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onInnerBottomChange(1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onInnerBottomChange(-1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "InnerVPad:${abs(uiState.innerVPadDp)}dp",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onInnerVPadChange(1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onInnerVPadChange(-1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "DetailsMaxH:${uiState.effectiveDetailsMaxH}dp / DEV:${uiState.detailsMaxHeightDp}dp",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onDetailsMaxHeightChange(10) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onDetailsMaxHeightChange(-10) }) {
                                        Text("▼")
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val cardMaxLabel = uiState.effectiveCardMaxH?.let { "${it}dp" } ?: "制限なし"
                                    Text(
                                        text = "CardMax:${cardMaxLabel} / DEV:${uiState.cardMaxHeightDp}dp / Base:${uiState.baseMaxHeightDp}dp",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onCardMaxHeightChange(10) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onCardMaxHeightChange(-10) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "MinHeight:${uiState.effectiveMinHeightDp}dp / カード最小高",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onCardMinHeightChange(1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onCardMinHeightChange(-1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "DetailsMaxH:${uiState.effectiveDetailsMaxH}dp / DEV:${uiState.detailsMaxHeightDp}dp",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onDetailsMaxHeightChange(10) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onDetailsMaxHeightChange(-10) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "DetailsLines:${uiState.detailsMaxLines} / 詳細行数",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onDetailsMaxLinesChange(1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onDetailsMaxLinesChange(-1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "HeaderSp:${uiState.headerSpacerDp}dp / 見出し余白",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onHeaderSpacerChange(1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onHeaderSpacerChange(-1) }) {
                                        Text("▼")
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "BodySp:${uiState.bodySpacerDp}dp / 本文余白",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(onClick = { callbacks.onBodySpacerChange(1) }) {
                                        Text("▲")
                                    }
                                    IconButton(onClick = { callbacks.onBodySpacerChange(-1) }) {
                                        Text("▼")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
