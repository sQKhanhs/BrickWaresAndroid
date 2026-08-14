package com.senniapp.brickwares.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/** One stat shown in a [StatCardRow] (icon + big number + label). */
data class StatEntry(
    @param:DrawableRes val icon: Int,
    val value: String,
    val label: String,
)

/**
 * The shared yellow-framed stat card (Sets / Minifigs / Pieces …) used on both the
 * Home and Collection tabs. Renders each [StatEntry] as an equal-width column.
 */
@Composable
fun StatCardRow(entries: List<StatEntry>, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    // Yellow "frame": 2dp yellow padding (radius 16) around the card (radius 14).
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.brandYellow)
            .padding(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.card)
                .padding(vertical = 18.dp, horizontal = 8.dp),
        ) {
            for (entry in entries) {
                StatItem(entry = entry, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatItem(entry: StatEntry, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(entry.icon),
            contentDescription = null,
            tint = colors.textMuted2,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(entry.value, style = BwType.statNumber, color = colors.text)
        Spacer(Modifier.height(2.dp))
        Text(entry.label, style = BwType.statLabel, color = colors.textMuted)
    }
}
