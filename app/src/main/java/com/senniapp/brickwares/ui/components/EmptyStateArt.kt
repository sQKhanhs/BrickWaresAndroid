package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/** Bundled minifig-with-box art (design "EmptyNoBG-keyed"), shown when a tab has no items. */
private const val EMPTY_ART = "file:///android_asset/empty_state.png"

/**
 * The shared empty-state for Collection / Wishlist / Sales: a bold line of copy above the
 * design's minifig-holding-a-box illustration. The paired FAB blinks (see [blinkAttention]) to
 * point the user at the action that fills the tab.
 */
@Composable
fun EmptyStateArt(text: String, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = BwType.cardTitle,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        AsyncImage(
            model = EMPTY_ART,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // Same displayed width as the logged-out sign-in illustration (SignInPromptCard); the
            // art is tight-cropped to the figure so it renders at a matching size.
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(350f / 431f),
        )
    }
}
