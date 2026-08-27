package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import kotlinx.coroutines.delay

/**
 * A transient toast pill shown near the bottom of a screen (mirrors the design's toast). Renders
 * only while [message] is non-null and auto-clears after a short delay via [onDismiss]. Call it
 * inside a root Box (it aligns itself to bottom-center).
 */
@Composable
fun BoxScope.BwToast(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    val colors = BwTheme.colors
    LaunchedEffect(message) {
        delay(2200)
        onDismiss()
    }
    // Inverse-surface pill so it always contrasts with the page: dark pill + light text in the light
    // theme, light pill + dark text in dark (a near-black pill was invisible on the dark background).
    Text(
        text = message,
        style = BwType.body.copy(fontSize = 12.sp),
        color = colors.bg,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 100.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.text)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
