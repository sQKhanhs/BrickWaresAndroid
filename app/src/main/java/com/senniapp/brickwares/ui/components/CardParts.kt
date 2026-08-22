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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
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
 * A set/minifig thumbnail: the catalog image when available, else the item-type icon on a
 * placeholder. [modifier] (e.g. `clickable`) is applied after the clip so ripples stay rounded.
 */
@Composable
fun SetThumb(
    imageUrl: String?,
    itemType: ItemType,
    size: Dp,
    iconSize: Dp,
    corner: Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    val typeIcon: @Composable () -> Unit = {
        Icon(
            painter = painterResource(
                if (itemType == ItemType.MINIFIG) R.drawable.ic_bw_minifig else R.drawable.ic_bw_set,
            ),
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier.size(iconSize),
        )
    }
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
        if (imageUrl == null) {
            typeIcon()
        } else {
            // SubcomposeAsyncImage so a failed/missing image (e.g. set not on the CDN) falls back
            // to the type icon instead of a blank box.
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { typeIcon() } },
            )
        }
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
