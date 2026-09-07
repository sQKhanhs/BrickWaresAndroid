package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
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
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.util.oneDecimal
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared building blocks for the item cards used across the Collection and Wishlist tabs.
 * Extracted so both tabs render identical set/minifig cards (title, meta lines, status, prices)
 * and the same header banner and filter chips.
 */


/**
 * A set/minifig thumbnail: [imageUrl] first, then [fallbackUrl] if it fails to load (e.g. the box
 * photo isn't on BrickLink → fall back to the render), else a "No image" placeholder. The image
 * itself fades in (Coil crossfade); the card is not gated on it. [modifier] (e.g. `clickable`) is
 * applied after the clip so ripples stay rounded. [onState]/[onResolvedUrl] report the TERMINAL load
 * result (a mid-chain failure that still has a fallback is not forwarded) — the detail hero uses
 * [onResolvedUrl] to know which image actually loaded when building its gallery.
 */
@Composable
fun SetThumb(
    imageUrl: String?,
    itemType: ItemType,
    size: Dp,
    iconSize: Dp,
    corner: Dp = 10.dp,
    modifier: Modifier = Modifier,
    fallbackUrl: String? = null,
    /**
     * Extra fallbacks tried in order after [fallbackUrl] — e.g. a Rebrickable image when the BrickLink
     * box AND the Brickset render are both missing for a set (image coverage differs per host).
     */
    extraFallbacks: List<String> = emptyList(),
    /**
     * When non-empty, tapping the thumbnail opens the full-screen [ImageGalleryDialog] over these
     * (full-resolution) URLs instead of the caller wiring a navigation click on [modifier]. 404s are
     * dropped by the gallery, so pass box + render freely.
     */
    galleryImages: List<String> = emptyList(),
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
    /** Reports the URL that actually loaded (the box or its fallback), or null when all failed. */
    onResolvedUrl: ((String?) -> Unit)? = null,
) {
    val colors = BwTheme.colors
    var showGallery by remember { mutableStateOf(false) }
    // The ordered chain of URLs to try (box → fallback → extra fallbacks), de-duplicated.
    val urls = remember(imageUrl, fallbackUrl, extraFallbacks) {
        (listOfNotNull(imageUrl, fallbackUrl) + extraFallbacks).distinct()
    }
    var index by remember(imageUrl, fallbackUrl, extraFallbacks) { mutableStateOf(0) }
    var failed by remember(imageUrl, fallbackUrl, extraFallbacks) { mutableStateOf(urls.isEmpty()) }
    val current = urls.getOrNull(index)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            // White fill so product photos (which have white backgrounds) blend seamlessly;
            // a soft gray outline provides the border.
            .background(Color.White)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(corner))
            // Image tap → full-screen gallery, but only while a thumbnail is actually showing. A
            // no-image / failed-to-load thumbnail renders the placeholder, and tapping it must do
            // nothing — its URLs (if any) 404, so the gallery would just be a black screen. Else the
            // caller's own [modifier] (which may carry a navigation click) applies.
            .then(if (galleryImages.isNotEmpty() && !failed) Modifier.clickable { showGallery = true } else Modifier)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (current == null || failed) {
            NoImagePlaceholder(itemType, iconSize = iconSize)
        }
        if (current != null && !failed) {
            AsyncImage(
                model = current,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> { onState?.invoke(state); onResolvedUrl?.invoke(current) }
                        is AsyncImagePainter.State.Error ->
                            if (index < urls.lastIndex) index++
                            else { failed = true; onState?.invoke(state); onResolvedUrl?.invoke(null) }
                        else -> {}
                    }
                },
            )
        }
    }
    if (showGallery && galleryImages.isNotEmpty()) {
        ImageGalleryDialog(candidates = galleryImages, onDismiss = { showGallery = false })
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
            text = stringResource(R.string.no_image),
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
 * Localized release label for a set's month + year. English uses the short month name
 * ("Jan 2026", "Oct 2026"); Vietnamese uses the "T{n}" form ("T1 2026") — both driven by the
 * `release_months` string-array, so the in-app language switcher applies automatically. When the
 * month is unknown (0, as many catalog sets store year only), just the year is shown ("2026").
 */
@Composable
fun releaseLabel(month: Int, year: Int): String {
    if (month !in 1..12) return year.toString()
    val label = stringArrayResource(R.array.release_months)[month - 1]
    return stringResource(R.string.release_format, label, year)
}

/**
 * Localized retail-price label: the formatted amount in the display [currency], or "No data" when the
 * catalog has no retail figure. Retail is stored in ₫ and converts to any display currency, so there
 * is no per-currency "unavailable" case — a null means the data is genuinely missing.
 */
@Composable
fun retailLabel(amount: Long?, currency: AppCurrency): String =
    if (amount != null) formatMoney(amount, currency) else stringResource(R.string.price_no_retail)

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

/**
 * The circular back button shown at the top of detail / theme-list screens. The chevron is drawn (not
 * a text glyph) so it's crisp, sized to the circle, and exactly centred.
 */
@Composable
fun BackCircleButton(onBack: () -> Unit) {
    val colors = BwTheme.colors
    val ink = colors.text
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.borderStrong), CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            // A "<" chevron, centred in the canvas (apex at 36%, arms at 64%, vertically 20%..80%).
            val path = Path().apply {
                moveTo(w * 0.64f, h * 0.20f)
                lineTo(w * 0.36f, h * 0.50f)
                lineTo(w * 0.64f, h * 0.80f)
            }
            drawPath(path, color = ink, style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun StatusBadge(status: Availability) {
    val colors = BwTheme.colors
    val (textRes, color) = when (status) {
        Availability.RETIRED -> R.string.status_retired to colors.error
        Availability.EXCLUSIVE -> R.string.status_exclusive to colors.linkAccent2
        Availability.GWP -> R.string.status_gwp to colors.gwp
        Availability.PROMO -> R.string.status_promotional to colors.promo
        Availability.MAGAZINE -> R.string.status_magazine to colors.magazine
        Availability.PENDING -> R.string.status_pending to colors.pending
        Availability.AVAILABLE -> R.string.status_available to colors.success
    }
    val text = stringResource(textRes)
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
            // Take the remaining width, right-aligned on one line, so a long ₫ amount never wraps its
            // trailing "₫" to a second line.
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/**
 * Growth direction (-1 / 0 / +1), judged on the **one-decimal-rounded** percent so a value that
 * rounds to 0.0% reads as flat (no arrow/colour). Shared so a label and its colour always agree.
 */
fun growthDirection(percent: Double): Int {
    val tenths = (percent * 10.0).roundToInt()
    return when {
        tenths > 0 -> 1
        tenths < 0 -> -1
        else -> 0
    }
}

/**
 * Localized growth label with **one decimal place**, e.g. "▲ +0.5% Growth" / "▼ -3.2% Growth", or
 * the flat "0% Growth" when it rounds to zero. (An integer round hid sub-1% growth as "0%".)
 */
@Composable
fun growthLabel(percent: Double): String {
    val rounded = (percent * 10.0).roundToInt() / 10.0
    return when {
        rounded > 0 -> stringResource(R.string.growth_up, oneDecimal(abs(rounded)))
        rounded < 0 -> stringResource(R.string.growth_down, oneDecimal(rounded))
        else -> stringResource(R.string.growth_flat)
    }
}

/** Green/red growth pill (▲ +34.0% Growth). */
@Composable
fun GrowthPill(percent: Double) {
    val colors = BwTheme.colors
    val text = growthLabel(percent)
    val color = when (growthDirection(percent)) {
        1 -> colors.success
        -1 -> colors.error
        else -> colors.textMuted
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
