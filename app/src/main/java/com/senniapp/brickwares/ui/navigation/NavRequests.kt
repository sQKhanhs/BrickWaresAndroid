package com.senniapp.brickwares.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot tab-navigation requests from outside the Compose tree — e.g. a tapped notification whose
 * intent asks to land on the Wishlist. [MainActivity] posts a request from the launch / new intent;
 * [BrickWaresApp] observes [tab], switches to it, and calls [consume]. Held in a StateFlow so a request
 * made while the splash still covers the app is honoured once the app composes.
 */
object NavRequests {
    private val _tab = MutableStateFlow<BwTab?>(null)
    val tab: StateFlow<BwTab?> = _tab.asStateFlow()

    fun request(tab: BwTab) {
        _tab.value = tab
    }

    fun consume() {
        _tab.value = null
    }
}
