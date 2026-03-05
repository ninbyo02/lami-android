package com.sonusid.ollama.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object LamiTypographyTokens {
    private val readableBodyLineHeight = 22.sp

    @Composable
    fun bodyReadable(color: Color = MaterialTheme.colorScheme.onSurface): TextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            color = color,
            fontWeight = FontWeight.Medium,
            lineHeight = readableBodyLineHeight,
        )

    @Composable
    fun aboutVersion(): TextStyle =
        MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )

    @Composable
    fun aboutBuild(): TextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium,
        )

    @Composable
    fun chatPlaceholder(): TextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium,
        )

    @Composable
    fun fieldPlaceholder(): TextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium,
        )

    @Composable
    fun fieldLabel(): TextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium,
        )
}
