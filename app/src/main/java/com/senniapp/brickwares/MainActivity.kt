package com.senniapp.brickwares

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senniapp.brickwares.ui.components.LoadingScreen
import com.senniapp.brickwares.ui.navigation.AuthGate
import com.senniapp.brickwares.ui.navigation.BrickWaresApp
import com.senniapp.brickwares.ui.theme.BrickWaresTheme
import com.senniapp.brickwares.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
                // No login wall: splash only while the persisted session restores, then always show
                // the app (logged-out users browse; write features prompt sign-in on demand).
                val gate by AuthGate.state.collectAsStateWithLifecycle()
                when (gate) {
                    AuthGate.State.Loading -> LoadingScreen()
                    AuthGate.State.Ready ->
                        BrickWaresApp(themeMode = themeMode, onThemeModeChange = { themeMode = it })
                }
            }
        }
    }
}
