package com.senniapp.brickwares

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senniapp.brickwares.data.local.LocalePrefs
import com.senniapp.brickwares.ui.components.SplashScreen
import com.senniapp.brickwares.ui.navigation.AuthGate
import com.senniapp.brickwares.ui.navigation.BrickWaresApp
import com.senniapp.brickwares.ui.theme.BrickWaresTheme
import com.senniapp.brickwares.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    // Apply the user's chosen language before the UI attaches, so resources resolve to it. Null =
    // follow the system language. Changing it in Settings writes the tag and recreates the activity.
    override fun attachBaseContext(newBase: Context) {
        val tag = LocalePrefs.read(newBase)
        super.attachBaseContext(if (tag.isNullOrBlank()) newBase else newBase.withLocale(tag))
    }

    private fun Context.withLocale(tag: String): Context {
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        return createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Minimal Android-12 system splash (brand ground + brick) — it paints before the first Compose
        // frame and hands off to the branded Compose SplashScreen (same ground) with no colour flash.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme preference lives here so the Settings toggle can re-theme the whole app.
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.LIGHT) }
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BrickWaresTheme(darkTheme = darkTheme) {
                // No login wall: the splash covers only the initial session restore, then the app always
                // shows (logged-out users browse; write features prompt sign-in on demand). Hold the
                // branded splash for a minimum beat so its entrance animation is seen even when the
                // session restores instantly, then cross-fade into the app.
                val gate by AuthGate.state.collectAsStateWithLifecycle()
                var minElapsed by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(SPLASH_MIN_MS); minElapsed = true }
                val showSplash = gate == AuthGate.State.Loading || !minElapsed
                Crossfade(targetState = showSplash, animationSpec = tween(400), label = "splash") { splash ->
                    if (splash) {
                        SplashScreen()
                    } else {
                        BrickWaresApp(themeMode = themeMode, onThemeModeChange = { themeMode = it })
                    }
                }
            }
        }
    }

    private companion object {
        /** Minimum time the branded splash stays up so its entrance animation isn't cut off. */
        const val SPLASH_MIN_MS = 1400L
    }
}
