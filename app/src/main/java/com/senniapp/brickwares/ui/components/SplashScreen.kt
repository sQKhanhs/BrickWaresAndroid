package com.senniapp.brickwares.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import kotlinx.coroutines.launch

/**
 * The branded landing shown on cold start (the [com.senniapp.brickwares.ui.navigation.AuthGate.State.Loading]
 * slot, held for a minimum beat by MainActivity). The minimal Android-12 system splash paints the same
 * brand ground + brick first, so this fades in on top of it seamlessly, then elaborates it with the
 * wordmark, tagline and a subtle "settle into place" entrance. Cross-faded into the app once ready.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    // The brick scales in with a gentle overshoot ("clicks into place"); the wordmark + tagline fade up.
    val logoScale = remember { Animatable(0.72f) }
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
        contentAlpha.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to colors.bg, 1f to colors.surface)),
        contentAlignment = Alignment.Center,
    ) {
        // Soft brand glow behind the logo (nudged up to sit over the brick, which leads the column).
        Box(
            Modifier
                .size(340.dp)
                .offset(y = (-36).dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.brandYellow.copy(alpha = if (colors.isDark) 0.16f else 0.24f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // App-icon "tile" — white rounded square with the brick, echoing the launcher icon the user
            // just tapped (continuity from home screen → splash).
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer { scaleX = logoScale.value; scaleY = logoScale.value }
                    .shadow(14.dp, RoundedCornerShape(28.dp), clip = false)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                // Use the rasterized launcher art: the adaptive-icon foreground vector
                // (ic_launcher_foreground) uses aapt gradient attrs that painterResource can't inflate.
                Image(
                    painter = painterResource(R.drawable.brickwares_launcher),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = buildAnnotatedString {
                    append("Brick")
                    withStyle(SpanStyle(color = colors.brandYellow)) { append("Wares") }
                },
                style = BwType.wordmark.copy(fontSize = 34.sp),
                color = colors.text,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha.value },
            )
        }
        CircularProgressIndicator(
            color = colors.brandYellow,
            strokeWidth = 2.5.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .size(26.dp)
                .graphicsLayer { alpha = contentAlpha.value },
        )
    }
}
