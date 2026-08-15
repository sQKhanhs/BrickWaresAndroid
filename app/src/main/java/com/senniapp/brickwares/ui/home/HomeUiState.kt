package com.senniapp.brickwares.ui.home

import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.ThemeSummary
import com.senniapp.brickwares.util.AppCurrency

/**
 * Immutable UI state for the Home screen. The View renders purely from this; all
 * mutations happen in [HomeViewModel].
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = true,
    val currency: AppCurrency = AppCurrency.VND,
    val summary: CollectionSummary? = null,
    val themes: List<ThemeSummary> = emptyList(),
    val showSignInDialog: Boolean = false,
) {
    /** The header share action only appears when signed in with a non-empty collection. */
    val canShare: Boolean
        get() = isLoggedIn && (summary?.setCount ?: 0) > 0
}
