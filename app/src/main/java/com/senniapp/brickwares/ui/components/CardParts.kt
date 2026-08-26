package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import kotlin.math.roundToInt

/**
 * Shared building blocks for the item cards used across the Collection and Wishlist tabs.
 * Extracted so both tabs render identical set/minifig cards (title, meta lines, status, prices)
 * and the same header banner and filter chips.
 */

/**
 * Tracks whether an item card's lead image has resolved, so the whole card can be revealed only
 * once the photo is ready (no blank-thumbnail flash). A card creates one via [rememberCardImageReveal],
 * feeds [onState] into its image (AsyncImage/SetThumb), applies [Modifier.revealWhenReady], and shows
 * a [NoImagePlaceholder] while [failed] (or when there is no URL). Items with no URL start [ready].
 */
@Stable
class CardImageReveal(hasImage: Boolean) {
    var ready by mutableStateOf(!hasImage)
        private set
    var failed by mutableStateOf(false)
        private set

    val onState: (AsyncImagePainter.State) -> Unit = { state ->
        when (state) {
            is AsyncImagePainter.State.Success -> { failed = false; ready = true }
            is AsyncImagePainter.State.Error -> { failed = true; ready = true }
            else -> {}
        }
    }
}

/** Remembers a [CardImageReveal] for [imageUrl]; re-initializes if the URL changes. */
@Composable
fun rememberCardImageReveal(imageUrl: String?): CardImageReveal =
    remember(imageUrl) { CardImageReveal(hasImage = imageUrl != null) }

/** Fades a card in (via alpha) once its [reveal] image has resolved (loaded or failed). */
@Composable
fun Modifier.revealWhenReady(reveal: CardImageReveal): Modifier {
    val alpha by animateFloatAsState(if (reveal.ready) 1f else 0f, label = "cardReveal")
    return this.graphicsLayer { this.alpha = alpha }
}

/**
 * A set/minifig thumbnail: the catalog image when available, else a "No image" placeholder (also
 * shown if the image fails to load, e.g. a set not on the CDN). [modifier] (e.g. `clickable`) is
 * applied after the clip so ripples stay rounded. Pass [onState] to let a parent card gate its
 * reveal on this image's load.
 */
@Composable
fun SetThumb(
    imageUrl: String?,
    itemType: ItemType,
    size: Dp,
    iconSize: Dp,
    corner: Dp = 10.dp,
    modifier: Modifier = Modifier,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    val colors = BwTheme.colors
    var failed by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            // White fill so product photos (which have white backgrounds) blend seamlessly;
            // a soft gray outline provides the border.
            .background(Color.White)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(corner))
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Placeholder behind the image; the loaded photo covers it on success.
        if (imageUrl == null || failed) {
            NoImagePlaceholder(itemType, iconSize = iconSize)
        }
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> failed = false
                        is AsyncImagePainter.State.Error -> failed = true
                        else -> {}
                    }
                    onState?.invoke(state)
                },
            )
        }
    }
}

/**
 * Fallback shown inside a card thumbnail when an item has no image URL, or its photo failed to load
 * (e.g. a set not on the CDN): the item-type icon over a small "No image available" caption. Sized
 * to fit small (72dp) card thumbnails.
 */
@Composable
fun NoImagePlaceholder(itemType: ItemType, modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    val colors = BwTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(
                if (itemType == ItemType.MINIFIG) R.drawable.ic_bw_minifig else R.drawable.ic_bw_set,
            ),
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "No image",
            style = BwType.body.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** A tab header: a background image (asset path) with a scrim and the tab title. */
@Composable
fun Banner(imageAsset: String, title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2C2C2C)),
    ) {
        AsyncImage(
            model = imageAsset,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Scrim so the title stays legible over any image.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(0f to Color(0x33000000), 1f to Color(0x99000000)),
                ),
        )
        Text(
            text = title,
            style = BwType.wordmark,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 20.dp),
        )
    }
}

/**
 * A meta line (label + value). Uses [FlowRow] with a non-wrapping value so that when the
 * value doesn't fit beside the label it drops to the next line *whole* (e.g. "3066 / 5"),
 * instead of breaking in the middle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetaLine(label: String, value: String) {
    val colors = BwTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
        Text(
            value,
            style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
            softWrap = false,
            maxLines = 1,
        )
    }
}

@Composable
fun StatusBadge(status: Availability) {
    val colors = BwTheme.colors
    val (text, color) = when (status) {
        Availability.RETIRED -> "Retired" to colors.error
        Availability.EXCLUSIVE -> "Exclusive" to colors.linkAccent2
        Availability.AVAILABLE -> "Available" to colors.success
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, style = BwType.micro, color = color)
    }
}

/** A right-aligned "label ....... value" row used in the card price column. */
@Composable
fun PriceLine(label: String, value: String) {
    val colors = BwTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = colors.text,
        )
    }
}

/** Green/red growth pill (▲ +34% Growth). */
@Composable
fun GrowthPill(percent: Double) {
    val colors = BwTheme.colors
    val pct = percent.roundToInt()
    val (text, color) = when {
        pct > 0 -> "▲ +$pct% Growth" to colors.success
        pct < 0 -> "▼ $pct% Growth" to colors.error
        else -> "0% Growth" to colors.textMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = BwType.micro, color = color)
    }
}

/** A single filter chip (icon over label). Selected chips turn yellow. */
@Composable
fun ChipItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    val bg = if (selected) colors.brandYellow else colors.card
    val fg = if (selected) colors.onYellow else colors.text
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = BwType.navLabel, color = fg, fontWeight = FontWeight.Bold)
    }
}
