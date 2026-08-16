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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    LaunchedEffect(message) {
        delay(2200)
        onDismiss()
    }
    Text(
        text = message,
        style = BwType.body.copy(fontSize = 12.sp),
        color = Color.White,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 100.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
