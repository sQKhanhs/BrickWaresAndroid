package com.senniapp.brickwares.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.ui.components.StatCardRow
import com.senniapp.brickwares.ui.components.StatEntry
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatCount
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.util.formatRelease
import kotlin.math.roundToInt

@Composable
fun CollectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectionContent(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onToggleMode = viewModel::onToggleMode,
        onAddClick = viewModel::onAddClick,
        onItemDetail = viewModel::onItemDetail,
        modifier = modifier,
    )
}

@Composable
private fun CollectionContent(
    state: CollectionUiState,
    onFilterSelected: (CollectionFilter) -> Unit,
    onToggleMode: () -> Unit,
    onAddClick: () -> Unit,
    onItemDetail: (CollectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        ) {
            item {
                Banner(title = if (state.mode == CollectionMode.SALES) "My Sales" else "My Collection")
                Spacer(Modifier.height(16.dp))
            }
            state.summary?.let { summary ->
                item {
                    StatCardRow(
                        entries = listOf(
                            StatEntry(R.drawable.ic_bw_set, summary.setCount.toString(), "Sets"),
                            StatEntry(R.drawable.ic_bw_minifig, formatCount(summary.minifigCount), "Minifigs"),
                            StatEntry(R.drawable.ic_bw_pieces, formatCount(summary.pieceCount), "Pieces"),
                        ),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (state.mode == CollectionMode.COLLECTION) {
                item {
                    FilterChips(selected = state.filter, onSelect = onFilterSelected)
                    Spacer(Modifier.height(14.dp))
                }
                items(state.visibleItems, key = { it.setNumber }) { item ->
                    ItemCard(item = item, onDetail = { onItemDetail(item) })
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                item { SalesPlaceholder() }
            }
        }

        // Swap FAB (bottom-start) — toggles Collection <-> Sales; turns yellow in Sales mode.
        val salesActive = state.mode == CollectionMode.SALES
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 24.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(if (salesActive) colors.brandYellow else colors.card)
                .border(BorderStroke(1.5.dp, if (salesActive) colors.brandYellow else colors.borderStrong), CircleShape)
                .clickable(onClick = onToggleMode),
            contentAlignment = Alignment.Center,
        ) {
            val swapTint = if (salesActive) colors.onYellow else colors.text
            Icon(
                painter = painterResource(R.drawable.ic_bw_sales_swap),
                contentDescription = "Toggle sales mode",
                tint = swapTint,
                modifier = Modifier.size(26.dp),
            )
            Text("$", color = swapTint, fontWeight = FontWeight.Black, fontSize = 9.sp)
        }

        // Add FAB (bottom-end).
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.brandYellow)
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bw_plus),
                contentDescription = "Add to collection",
                tint = colors.onYellow,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun Banner(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2C2C2C)),
    ) {
        AsyncImage(
            model = "file:///android_asset/collection_banner.png",
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

@Composable
private fun FilterChips(selected: CollectionFilter, onSelect: (CollectionFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipItem(R.drawable.ic_bw_all, "All", selected == CollectionFilter.ALL, { onSelect(CollectionFilter.ALL) }, Modifier.weight(1f))
        ChipItem(R.drawable.ic_bw_set, "Set", selected == CollectionFilter.SET, { onSelect(CollectionFilter.SET) }, Modifier.weight(1f))
        ChipItem(R.drawable.ic_bw_minifig, "Minifig", selected == CollectionFilter.MINIFIG, { onSelect(CollectionFilter.MINIFIG) }, Modifier.weight(1f))
    }
}

@Composable
private fun ChipItem(
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

@Composable
private fun ItemCard(item: CollectionItem, onDetail: () -> Unit) {
    val colors = BwTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        // Image (placeholder until real photography is wired).
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.placeholderA),
            contentAlignment = Alignment.Center,
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    painter = painterResource(if (item.itemType == ItemType.MINIFIG) R.drawable.ic_bw_minifig else R.drawable.ic_bw_set),
                    contentDescription = null,
                    tint = colors.textFaint,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Title + meta (roomier vertical rhythm).
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "${item.setNumber} ${item.name}",
                style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.linkAccent,
            )
            MetaLine("Theme", item.theme)
            MetaLine("Release", formatRelease(item.releaseMonth, item.releaseYear))
            MetaLine("Pieces / Minifigs", "${item.pieces} / ${item.minifigs}")
            StatusBadge(item.status)
        }

        Spacer(Modifier.width(10.dp))

        // Price column.
        Column(
            modifier = Modifier.width(130.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PriceLine("Retail", formatMoney(item.retailPrice, AppCurrency.VND))
            PriceLine("Paid", formatMoney(item.pricePaid, AppCurrency.VND))
            if (item.currentValue != null) {
                PriceLine("Value", formatMoney(item.currentValue, AppCurrency.VND))
                item.growthPercent?.let { GrowthPill(it) }
            }
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
                    .clickable(onClick = onDetail)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_bw_check), contentDescription = null, tint = colors.text, modifier = Modifier.size(13.dp))
                Text("See Detail", style = BwType.micro.copy(fontSize = 11.sp), color = colors.text)
            }
        }
    }
}

/**
 * A meta line (label + value). Uses [FlowRow] with a non-wrapping value so that when the
 * value doesn't fit beside the label it drops to the next line *whole* (e.g. "3066 / 5"),
 * instead of breaking in the middle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaLine(label: String, value: String) {
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
private fun StatusBadge(status: Availability) {
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

@Composable
private fun PriceLine(label: String, value: String) {
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

@Composable
private fun GrowthPill(percent: Double) {
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

@Composable
private fun SalesPlaceholder() {
    val colors = BwTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Sales view — coming soon", style = BwType.cardTitle, color = colors.textMuted)
    }
}
