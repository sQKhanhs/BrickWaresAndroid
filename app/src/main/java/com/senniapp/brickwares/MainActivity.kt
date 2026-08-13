package com.senniapp.brickwares

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.senniapp.brickwares.ui.navigation.BrickWaresApp
import com.senniapp.brickwares.ui.theme.BrickWaresTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrickWaresTheme {
                BrickWaresApp()
            }
        }
    }
}
