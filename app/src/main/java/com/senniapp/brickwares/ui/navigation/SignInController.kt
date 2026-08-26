package com.senniapp.brickwares.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-level trigger for the on-demand sign-in overlay. Any gated surface (Home/Collection/Wishlist
 * prompts, Search/Detail add actions) calls [request]; [com.senniapp.brickwares.ui.navigation.BrickWaresApp]
 * observes [showLogin] to show the login overlay and dismisses it once signed in.
 */
object SignInController {
    private val _showLogin = MutableStateFlow(false)
    val showLogin: StateFlow<Boolean> = _showLogin.asStateFlow()

    fun request() { _showLogin.value = true }
    fun dismiss() { _showLogin.value = false }
}
