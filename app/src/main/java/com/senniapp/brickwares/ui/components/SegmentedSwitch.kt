package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/**
 * An iOS-style segmented switch: a soft pill track with the selected option on a raised pill, text only.
 * The All / Set / Minifig filter on Collection, Sales and Wishlist (matching the iOS app), in [FilterSortRow].
 */
@Composable
fun <T> SegmentedSwitch(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    val pill = RoundedCornerShape(999.dp)
    // The selected pill must stand out from the track in both themes: white (+ a hairline shadow) on the
    // light track; a lighter veil on the dark one, whose card colour is nearly the track's.
    val selectedBg = if (colors.isDark) Color.White.copy(alpha = 0.14f) else colors.card
    Row(
        modifier = modifier
            .clip(pill)
            .background(colors.track)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    // Fills a fixed-height switch ([FilterSortRow]); no effect when the height wraps.
                    .fillMaxHeight()
                    .then(if (isSelected && !colors.isDark) Modifier.shadow(1.dp, pill) else Modifier)
                    .clip(pill)
                    .background(if (isSelected) selectedBg else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(value) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = BwType.body.copy(
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (isSelected) colors.text else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
