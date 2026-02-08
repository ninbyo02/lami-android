package com.sonusid.ollama.ui.common

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun ProjectSnackbar(
    message: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = SnackbarDefaults.shape,
    ) {
        Text(
            text = message,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
