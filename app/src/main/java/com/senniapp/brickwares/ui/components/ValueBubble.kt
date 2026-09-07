package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CurrentValue
import com.senniapp.brickwares.data.model.ValueFreshness
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.formatIn
import kotlin.math.roundToInt

/**
 * The card "Value" line (Arch Decision 17): the label, the amount (or `----`), and — only when
 * [showBubble] — the "!" freshness bubble. Item cards pass `showBubble = false` and render the bubble
 * beside the status badge (see [StatusBadgeWithValueInfo]) so it doesn't narrow this row and clip a
 * long ₫ amount's trailing symbol; detail pages keep it here (they have room). The bubble opens toward
 * the left ([alignEnd]) when it lives in the narrow right-hand price column.
 */
@Composable
fun ValuePriceLine(value: CurrentValue?, alignEnd: Boolean = true, showBubble: Boolean = true) {
    val colors = BwTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.price_value), style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
        if (showBubble) {
            Spacer(Modifier.width(4.dp))
            ValueInfoBubble(note = currentValueNote(value ?: CurrentValue.NONE), alignEnd = alignEnd)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            value?.displayMinor(BwTheme.currency)?.let { formatIn(it, BwTheme.currency) } ?: "----",
            // Remaining width, right-aligned, one line. With the bubble moved off this row (item cards),
            // the value has the same room as the Retail line, so a long ₫ amount no longer clips.
            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = colors.text,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/**
 * A [StatusBadge] with the value-freshness "!" bubble beside it. Item cards render the bubble here
 * instead of on the narrow Value price line (where a long ₫ amount clipped its trailing symbol). The
 * bubble opens rightward (there's room in the left-hand meta column). Only used where a value is
 * actually shown; detail pages keep the bubble on the value line and don't use this.
 */
@Composable
fun StatusBadgeWithValueInfo(status: Availability, value: CurrentValue?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusBadge(status)
        ValueInfoBubble(note = currentValueNote(value ?: CurrentValue.NONE), alignEnd = false)
    }
}

/**
 * The minifig "in N sets" meta line with the value-freshness "!" bubble beside it. Minifig cards have
 * no status badge, so they park the bubble here instead of on the narrow value line. Callers render
 * this only when [setCount] > 0; a minifig in no sets keeps the bubble on the value line instead.
 */
@Composable
fun MinifigSetsWithValueInfo(setCount: Int, value: CurrentValue?) {
    val colors = BwTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.search_minifig_sets, setCount, setCount),
            style = BwType.body.copy(fontSize = 12.sp),
            color = colors.textMuted,
        )
        ValueInfoBubble(note = currentValueNote(value ?: CurrentValue.NONE), alignEnd = false)
    }
}

/**
 * The "!" info button + comic-style speech-bubble popup (one continuous outline around body AND
 * pointer, caret pointing up at the button; tap outside to dismiss). Theme-aware: dark mode uses a
 * white fill + bold outline, light mode a soft gray fill + thin outline. [alignEnd] opens the bubble
 * leftward with the caret near its right edge (for the narrow card price column); the default opens
 * it rightward (for the detail page, where there's room).
 */
@Composable
fun ValueInfoBubble(note: String, alignEnd: Boolean = false) {
    val colors = BwTheme.colors
    val fill = if (colors.isDark) Color.White else Color(0xFFEAEAEA)
    val outline = if (colors.isDark) Color(0xFF1A1A1A) else Color(0xFF6E6E6E)
    val outlineWidth = if (colors.isDark) 2.dp else 1.dp
    val ink = Color(0xFF1F1F1F)
    val density = LocalDensity.current
    var open by remember { mutableStateOf(false) }

    val caretW = with(density) { 14.dp.toPx() }
    val caretH = with(density) { 9.dp.toPx() }
    val caretInset = with(density) { 20.dp.toPx() } // caret centre, measured from the near edge
    val r = with(density) { 10.dp.toPx() }
    val bubbleShape = remember(caretW, caretH, caretInset, r, alignEnd) {
        GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            val t = caretH
            val cc = if (alignEnd) w - caretInset else caretInset
            moveTo(r, t)
            lineTo(cc - caretW / 2f, t)
            lineTo(cc, 0f)
            lineTo(cc + caretW / 2f, t)
            lineTo(w - r, t)
            quadraticTo(w, t, w, t + r)
            lineTo(w, h - r)
            quadraticTo(w, h, w - r, h)
            lineTo(r, h)
            quadraticTo(0f, h, 0f, h - r)
            lineTo(0f, t + r)
            quadraticTo(0f, t, r, t)
            close()
        }
    }
    // Places the popup so the caret lands under the "!" button centre (from the button's own bounds,
    // so it works wherever the button sits on the card).
    val positionProvider = remember(alignEnd, caretInset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val buttonCenterX = anchorBounds.left + anchorBounds.width / 2
                val caretX = if (alignEnd) popupContentSize.width - caretInset.roundToInt() else caretInset.roundToInt()
                return IntOffset(buttonCenterX - caretX, anchorBounds.bottom)
            }
        }
    }
    Box {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(colors.textMuted.copy(alpha = 0.18f))
                .clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Text("!", style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = colors.textMuted)
        }
        if (open) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Box(
                    modifier = Modifier
                        // 180dp (was 240): at 240 a long note filled line 1 and left the short last
                        // line a big empty gap on the right; narrower wraps into balanced lines that
                        // hug the bubble, matching how a short note already looks.
                        .widthIn(max = 180.dp)
                        .background(fill, bubbleShape)
                        .border(outlineWidth, outline, bubbleShape)
                        .padding(start = 12.dp, end = 12.dp, top = 17.dp, bottom = 10.dp),
                ) {
                    Text(note, style = BwType.body.copy(fontSize = 11.sp), color = ink)
                }
            }
        }
    }
}

/** The bubble note text for each [CurrentValue] state (FRESH count / STALE age / NONE prompt). */
@Composable
fun currentValueNote(value: CurrentValue): String = when (value.freshness) {
    ValueFreshness.FRESH -> stringResource(R.string.value_note_fresh, value.contributionCount)
    ValueFreshness.STALE -> stringResource(R.string.value_note_stale, valueAgeText(value.newestAgeDays ?: 0))
    ValueFreshness.NONE -> stringResource(R.string.value_note_none)
}

/** A coarse localized age ("3 months", "2 years") for the STALE note. */
@Composable
private fun valueAgeText(days: Int): String = when {
    days < 60 -> stringResource(R.string.value_age_days, days.coerceAtLeast(1))
    days < 730 -> stringResource(R.string.value_age_months, (days / 30).coerceAtLeast(1))
    else -> stringResource(R.string.value_age_years, (days / 365).coerceAtLeast(1))
}
