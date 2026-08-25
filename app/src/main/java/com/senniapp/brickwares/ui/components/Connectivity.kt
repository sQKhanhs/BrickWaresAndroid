package com.senniapp.brickwares.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senniapp.brickwares.data.local.AppGraph

/** Live online/offline state, for gating network-only UI (sign-in / sign-out are hidden offline). */
@Composable
fun rememberIsOnline(): Boolean {
    val online by AppGraph.connectivity.isOnline.collectAsStateWithLifecycle()
    return online
}
