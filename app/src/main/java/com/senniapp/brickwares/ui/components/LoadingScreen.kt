package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.senniapp.brickwares.ui.theme.BwTheme

/**
 * Full-size centered spinner shown while a tab's data loads, so the tab appears fully-formed
 * (stat card + cards together) instead of the stat card popping in after the list.
 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BwTheme.colors.brandYellow)
    }
}
