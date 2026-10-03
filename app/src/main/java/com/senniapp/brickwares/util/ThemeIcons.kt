package com.senniapp.brickwares.util

import coil3.network.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which theme icons don't exist. Icons are addressed by a slug of the theme name
 * ([CatalogImages.themeIconUrl]), so a theme with no icon uploaded only reveals itself by a 404. Those
 * URLs are recorded here — by the browse prefetch ([ImagePrefetcher.warm]) and by any theme card that
 * hits one — so the cards drop the logo box entirely instead of showing an empty frame. In memory for the
 * process: an icon uploaded later appears on the next launch.
 */
object ThemeIcons {
    private val _missing = MutableStateFlow<Set<String>>(emptySet())
    val missing: StateFlow<Set<String>> = _missing.asStateFlow()

    /**
     * Records [url] as missing — only for a real 404 from the icon store, never for a network failure:
     * being offline must not hide icons that exist.
     */
    fun onLoadError(url: String, error: Throwable) {
        if ((error as? HttpException)?.response?.code == 404) _missing.update { it + url }
    }
}
