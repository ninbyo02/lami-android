package io.github.ninbyo02.lami.ui.screens.home

internal data class InferenceStatsSectionUi(
    val title: String,
    val items: List<InferenceStatItemUi>,
)

internal data class InferenceStatItemUi(
    val label: String,
    val value: String,
    val emphasizeValue: Boolean = false,
)
