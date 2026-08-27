package com.senniapp.brickwares.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A deferred, localizable piece of user-facing text produced outside a composable (e.g. a ViewModel
 * toast or error). ViewModels can't call [stringResource], so they emit a [UiText] and the screen
 * resolves it with [resolve]. [Res] carries a string resource + format args; [Raw] wraps a string we
 * can't localize (e.g. a backend error message).
 */
sealed interface UiText {
    data class Res(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
}

/** Resolves a [UiText] to a display string in the current locale. */
@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> stringResource(resId, *args.toTypedArray())
    is UiText.Raw -> text
}
