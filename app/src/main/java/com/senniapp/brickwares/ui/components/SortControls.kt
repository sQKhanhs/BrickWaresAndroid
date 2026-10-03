package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** Height shared by the list-header controls ([SegmentedSwitch] and [SortDropdown]) so they line up. */
val ListControlHeight = 40.dp

/**
 * The sticky header row of the Collection / Sales / Wishlist lists, as on iOS: the All / Set / Minifig
 * [SegmentedSwitch] filling the row and the sort pill beside it.
 */
@Composable
fun <T> FilterSortRow(
    filterOptions: List<Pair<T, String>>,
    selectedFilter: T,
    onFilterSelect: (T) -> Unit,
    sort: ItemSort,
    sortOptions: List<ItemSort>,
    onSortSelect: (ItemSort) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SegmentedSwitch(
            options = filterOptions,
            selected = selectedFilter,
            onSelect = onFilterSelect,
            modifier = Modifier.weight(1f).height(ListControlHeight),
        )
        Spacer(Modifier.width(8.dp))
        SortDropdown(selected = sort, options = sortOptions, onSelect = onSortSelect)
    }
}

/**
 * The sort pill — "⇅ current ⌄" — opening the options in a dropdown menu. Capped in width so the filter
 * switch beside it keeps room; a long option ("Price: high to low") ellipsizes in the pill, in full in
 * the menu.
 */
@Composable
fun SortDropdown(selected: ItemSort, options: List<ItemSort>, onSelect: (ItemSort) -> Unit) {
    val colors = BwTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val pill = RoundedCornerShape(999.dp)
    Box {
        Row(
            modifier = Modifier
                .height(ListControlHeight)
                .widthIn(max = 150.dp)
                .clip(pill)
                .border(BorderStroke(1.dp, colors.borderStrong), pill)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bw_sort),
                contentDescription = stringResource(R.string.search_sort_label),
                tint = colors.text,
                modifier = Modifier.size(15.dp),
            )
            Text(
                selected.label(),
                style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                painter = painterResource(R.drawable.ic_bw_chevron_down),
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(14.dp),
            )
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
