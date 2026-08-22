package com.senniapp.brickwares.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlin.math.roundToLong

/** Animation keys that have already played this app session, so a number counts up only once. */
private val playedAnimationKeys = mutableSetOf<String>()

/**
 * Returns a value that counts up from 0 to [target] (or down for negatives) the FIRST time this
 * [animationKey] is shown in the app session; afterwards (e.g. re-entering the tab) it snaps
 * straight to the value with no replay.
 *
 * [animationKey] must be unique per on-screen number (e.g. "home_value", "collection-Sets").
 * Progress is interpolated 0→1 and scaled, so the final value is exact even for large ₫ amounts.
 */
@Composable
fun animatedNumber(target: Long, animationKey: String, durationMs: Int = 900): Long {
    val alreadyPlayed = remember(animationKey) { animationKey in playedAnimationKeys }
    val progress = remember(animationKey) { Animatable(if (alreadyPlayed) 1f else 0f) }
    LaunchedEffect(animationKey) {
        if (!alreadyPlayed) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing))
            playedAnimationKeys += animationKey
        }
    }
    return (target.toDouble() * progress.value).roundToLong()
}
