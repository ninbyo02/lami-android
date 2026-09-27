package io.github.ninbyo02.lami.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode

/** Renders prepared statistics without owning developer diagnostic state or actions.
 * Emits into the existing sheet column to preserve spacing and layout order.
 */
@Composable
internal fun ColumnScope.InferenceStatsContent(
    stats: InferenceStats,
    displayMode: InferenceStatsDisplayMode,
    sections: List<InferenceStatsSectionUi>,
    detailSections: List<InferenceStatsSectionUi>,
    sectionSpacing: Dp,
) {
    sections.forEach { section ->
        InferenceStatsSection(title = section.title) {
            section.items.forEach { item ->
                InferenceStatRow(label = item.label, value = item.value, emphasizeValue = item.emphasizeValue)
            }
        }
    }

    if (displayMode != InferenceStatsDisplayMode.SIMPLE) {
        InferenceTimingBreakdownSection(stats)
        InferenceContextUsageSection(stats)
    }

    if (displayMode != InferenceStatsDisplayMode.SIMPLE && shouldShowInferenceTimingNote(stats)) {
        Text(
            text = inferenceTimingNoteText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (displayMode != InferenceStatsDisplayMode.SIMPLE) {
        Column(
            modifier = Modifier.testTag("inferenceStatsDetailContent"),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            detailSections.forEach { section ->
                InferenceStatsSection(title = section.title) {
                    section.items.forEach { item ->
                        InferenceStatRow(
                            label = item.label,
                            value = item.value,
                            emphasizeValue = item.emphasizeValue,
                        )
                    }
                }
            }
        }
    }
}
