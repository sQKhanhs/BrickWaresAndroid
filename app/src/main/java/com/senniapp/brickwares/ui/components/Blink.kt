package com.senniapp.brickwares.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * Attention-pulse: fades opacity 100% → 40% → 100% (~650ms each way, reversed) while [enabled].
 * Used on a tab's FAB when that tab is empty, to prompt the first action. The transition is always
 * created (stable composable call count); the alpha is applied only when [enabled].
 */
@Composable
fun Modifier.blinkAttention(enabled: Boolean): Modifier {
    val transition = rememberInfiniteTransition(label = "blinkAttention")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(650, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse",
    )
    return if (enabled) this.alpha(pulse) else this
}
