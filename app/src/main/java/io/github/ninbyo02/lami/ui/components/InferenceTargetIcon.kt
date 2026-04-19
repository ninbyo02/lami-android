package io.github.ninbyo02.lami.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun InferenceTargetIcon(
    target: InferenceTarget,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = if (target == InferenceTarget.LOCAL) Icons.Outlined.Memory else Icons.Outlined.Cloud,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
