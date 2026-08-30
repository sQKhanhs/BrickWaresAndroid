package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/** Bundled error illustration (broken-brick minifig), shown on the error/offline fallback screen. */
private const val ERROR_ART = "file:///android_asset/error_state.png"

/**
 * Full-screen fallback for an unexpected error or a lost internet connection: the error illustration
 * with a message beneath it, and an optional **Retry** button. Shown where the app can't proceed
 * without the network (the catalog-backed Search + Set Detail surfaces).
 */
@Composable
fun ErrorScreen(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val colors = BwTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = BwType.cardTitle,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        AsyncImage(
            model = ERROR_ART,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(190.dp).aspectRatio(443f / 640f),
        )
        if (onRetry != null) {
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandYellow,
                    contentColor = colors.onYellow,
                ),
            ) {
                Text(stringResource(R.string.action_retry), style = BwType.pill, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            }
        }
    }
}
