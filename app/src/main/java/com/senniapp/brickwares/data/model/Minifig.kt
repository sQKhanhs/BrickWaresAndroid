package com.senniapp.brickwares.data.model

/**
 * Catalog (reference) data for a minifig, from the `minifigs` table (sourced from Rebrickable).
 * [setCount] and [themes] are derived from the `set_minifigs` join (which sets it appears in) —
 * they drive the "in N sets" badge and the theme browse. Identity is the Rebrickable [figNum]
 * ("fig-017395"); the BrickLink `sh####` id the VN community uses is not available (Arch Decision 14).
 */
data class Minifig(
    val figNum: String,
    val name: String,
    val imageUrl: String? = null,
    val numParts: Int = 0,
    /** How many (catalog) sets this fig appears in. */
    val setCount: Int = 0,
    /** (theme, subtheme) pairs from the sets this fig appears in — drives the theme + subtheme browse. */
    val themeSubthemes: List<Pair<String, String>> = emptyList(),
) {
    /** Distinct themes this fig belongs to. */
    val themes: List<String> get() = themeSubthemes.map { it.first }.distinct()
}
