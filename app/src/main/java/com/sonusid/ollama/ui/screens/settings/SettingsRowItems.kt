package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val SettingsRowHorizontalPadding = 12.dp
private val SettingsRowVerticalPadding = 10.dp
private val SettingsRowIconSize = 24.dp
private val SettingsRowIconBoxWidth = 36.dp

@Composable
fun SettingsNavRowItem(
    headline: String,
    supporting: String?,
    leadingIcon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    SettingsBaseRow(
        headline = headline,
        supporting = supporting,
        leadingIcon = leadingIcon,
        modifier = modifier,
        enabled = enabled,
        testTag = testTag,
        onRowClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(SettingsRowIconSize),
            )
        },
    )
}

@Composable
fun SettingsToggleRowItem(
    headline: String,
    supporting: String?,
    leadingIcon: ImageVector?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingsBaseRow(
        headline = headline,
        supporting = supporting,
        leadingIcon = leadingIcon,
        modifier = modifier,
        enabled = enabled,
        onRowClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

@Composable
fun SettingsActionRowItem(
    headline: String,
    supporting: String?,
    leadingIcon: ImageVector?,
    action: @Composable () -> Unit,
    onRowClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingsBaseRow(
        headline = headline,
        supporting = supporting,
        leadingIcon = leadingIcon,
        modifier = modifier,
        enabled = enabled,
        onRowClick = onRowClick,
        trailing = action,
    )
}

@Composable
private fun SettingsBaseRow(
    headline: String,
    supporting: String?,
    leadingIcon: ImageVector?,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
    onRowClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onRowClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onRowClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .then(rowModifier)
            .padding(horizontal = SettingsRowHorizontalPadding, vertical = SettingsRowVerticalPadding),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier.width(SettingsRowIconBoxWidth),
                contentAlignment = Alignment.TopStart,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(SettingsRowIconSize),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = headline, style = MaterialTheme.typography.titleMedium)
            if (!supporting.isNullOrBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        trailing()
    }
}
