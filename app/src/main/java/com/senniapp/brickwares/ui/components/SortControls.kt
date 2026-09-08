package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/**
 * Ordering for the owned/wishlisted item lists (Collection / Sales / Wishlist) — mirrors the search
 * result-list sort ([com.senniapp.brickwares.ui.search.ThemeDetailSort]) plus [DATE_ADDED]. Each list
 * maps these to its own fields — price is paid (Collection) / sale value (Sales) / retail (Wishlist),
 * and "Date added" is the acquired (Collection) / sold (Sales) / wishlisted (Wishlist) date.
 */
enum class ItemSort { NAME, PRICE_HIGH, PRICE_LOW, DATE_ADDED, RELEASE_NEWEST, RELEASE_OLDEST }

/** The sort options offered on the Collection / Sales / Wishlist lists (all six, in menu order). */
val ItemSortOptionsFull = listOf(
    ItemSort.NAME, ItemSort.PRICE_HIGH, ItemSort.PRICE_LOW,
    ItemSort.DATE_ADDED, ItemSort.RELEASE_NEWEST, ItemSort.RELEASE_OLDEST,
)

@Composable
fun ItemSort.label(): String = stringResource(
    when (this) {
        ItemSort.NAME -> R.string.sort_alphabetical
        ItemSort.PRICE_HIGH -> R.string.sort_price_high
        ItemSort.PRICE_LOW -> R.string.sort_price_low
        ItemSort.DATE_ADDED -> R.string.sort_date_added
        ItemSort.RELEASE_NEWEST -> R.string.sort_newest
        ItemSort.RELEASE_OLDEST -> R.string.sort_oldest
    },
)

/** A "Sort  ……  [current ▾]" row — the label on the left, the dropdown on the right. */
@Composable
fun SortRow(selected: ItemSort, options: List<ItemSort>, onSelect: (ItemSort) -> Unit) {
    val colors = BwTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.search_sort_label),
            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = colors.textMuted,
        )
        Spacer(Modifier.weight(1f))
        SortDropdown(selected = selected, options = options, onSelect = onSelect)
    }
}

/** A compact bordered "current sort ▾" pill that opens the options in a dropdown menu. */
@Composable
fun SortDropdown(selected: ItemSort, options: List<ItemSort>, onSelect: (ItemSort) -> Unit) {
    val colors = BwTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(selected.label(), style = BwType.body.copy(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1)
            Text("▾", style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label(), style = BwType.body.copy(fontSize = 13.sp), color = colors.text) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}
