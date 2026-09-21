package com.senniapp.brickwares.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-level trigger for the on-demand sign-in overlay. Any gated surface (Home/Collection/Wishlist
 * prompts, Search/Detail add actions) calls [request]; [com.senniapp.brickwares.ui.navigation.BrickWaresApp]
 * observes [showLogin] to show the login overlay and dismisses it once signed in.
 *
 * **Hold:** the forgot-password flow signs the user IN when the emailed code is verified, but still
 * needs the overlay for one more step ("choose a new password"). While [holdOpen] is set, [dismiss] is
 * ignored — so the shell's "signed in → close the modal" reaction can't pull the screen away mid-reset.
 * The login ViewModel releases the hold when the reset finishes, fails, or the user closes the modal.
 */
object SignInController {
    private val _showLogin = MutableStateFlow(false)
    val showLogin: StateFlow<Boolean> = _showLogin.asStateFlow()

    @Volatile
    private var held = false

    fun request() { _showLogin.value = true }

    fun dismiss() { if (!held) _showLogin.value = false }

    /** See the class doc. Release (`false`) BEFORE the dismiss that should actually close the overlay. */
    fun holdOpen(hold: Boolean) { held = hold }
}
