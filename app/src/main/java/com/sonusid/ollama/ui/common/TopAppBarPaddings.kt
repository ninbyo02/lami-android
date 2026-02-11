package com.sonusid.ollama.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val TopAppBarExtraStartPadding: Dp = 6.dp

fun topAppBarContentPaddingWithExtraStart(layoutDirection: LayoutDirection): PaddingValues {
    val defaultPadding = TopAppBarDefaults.ContentPadding
    return PaddingValues(
        start = defaultPadding.calculateStartPadding(layoutDirection) + TopAppBarExtraStartPadding,
        top = defaultPadding.calculateTopPadding(),
        end = defaultPadding.calculateEndPadding(layoutDirection),
        bottom = defaultPadding.calculateBottomPadding(),
    )
}
