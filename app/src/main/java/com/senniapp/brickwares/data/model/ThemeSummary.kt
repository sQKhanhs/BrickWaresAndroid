package com.senniapp.brickwares.data.model

/** Per-theme rollup for the Home "Collection by Theme" section. */
data class ThemeSummary(
    val theme: String,
    val setCount: Int,
    val totalValue: Long,
)
