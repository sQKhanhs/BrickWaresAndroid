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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
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
        onDismissAddSheet = viewModel::onDismissAddSheet,
        onSearchCatalog = viewModel::searchCatalog,
        onAddItem = viewModel::submitAddSheet,
        onDismissDetail = viewModel::onDismissDetail,
        onDeleteCopy = viewModel::onDeleteCopy,
        onAddCopyForSet = viewModel::onAddCopyForSet,
        onEditCopy = viewModel::onEditCopy,
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
    onDismissAddSheet: () -> Unit,
    onSearchCatalog: (String) -> List<CatalogSet>,
    onAddItem: (CollectionItem) -> Unit,
    onDismissDetail: () -> Unit,
    onDeleteCopy: (String, String) -> Unit,
    onAddCopyForSet: (CollectionItem) -> Unit,
    onEditCopy: (CollectionItem, Copy) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        ) {
            val sales = state.mode == CollectionMode.SALES
            item {
                Banner(
                    imageAsset = if (sales) "file:///android_asset/sales_banner.png" else "file:///android_asset/collection_banner.png",
                    title = if (sales) "My Sales" else "My Collection",
                )
                Spacer(Modifier.height(16.dp))
            }
            if (!sales) {
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
                item {
                    FilterChips(selected = state.filter, onSelect = onFilterSelected)
                    Spacer(Modifier.height(14.dp))
                }
                items(state.visibleItems, key = { it.setNumber }) { item ->
                    ItemCard(item = item, onDetail = { onItemDetail(item) })
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                state.salesSummary?.let { s ->
                    item {
                        SalesStatsGrid(s)
                        Spacer(Modifier.height(12.dp))
                        ProfitBar(s)
                        Spacer(Modifier.height(14.dp))
                    }
                }
                items(state.soldItems, key = { it.setNumber }) { sold ->
                    SoldCard(sold)
                    Spacer(Modifier.height(12.dp))
                }
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

        if (state.showAddSheet) {
            AddToCollectionSheet(
                initialSet = state.addSheetPreselect,
                initialCopy = state.editingCopy,
                onDismiss = onDismissAddSheet,
                onSearch = onSearchCatalog,
                onAdd = onAddItem,
            )
        }

        state.detailItem?.let { detail ->
            SeeDetailsDialog(
                item = detail,
                onDismiss = onDismissDetail,
                onDeleteCopy = onDeleteCopy,
                onEditCopy = { copy -> onEditCopy(detail, copy) },
                onAddItem = { onAddCopyForSet(detail) },
            )
        }
    }
}

@Composable
private fun Banner(imageAsset: String, title: String) {
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
            PriceLine("Paid", formatMoney(item.totalPaid, AppCurrency.VND))
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

// ---- Sales mode ----

private fun signedMoney(v: Long): String =
    (if (v > 0) "+" else "") + formatMoney(v, AppCurrency.VND)

private fun signedPct(p: Double): String = "${if (p >= 0) "+" else ""}${p.roundToInt()}%"

@Composable
private fun SalesStatsGrid(summary: SalesSummary) {
    val colors = BwTheme.colors
    val profitColor = if (summary.totalProfit >= 0) colors.success else colors.error
    // Yellow frame, matching the collection stat card.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.brandYellow)
            .padding(2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.card)
                .padding(vertical = 18.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row {
                SalesStatCell("Total Sold", summary.totalSold.toString(), colors.text, Modifier.weight(1f))
                SalesStatCell("Total Profit", signedMoney(summary.totalProfit), profitColor, Modifier.weight(1f))
            }
            Row {
                SalesStatCell("Avg Profit %", signedPct(summary.avgProfitPercent), if (summary.avgProfitPercent >= 0) colors.success else colors.error, Modifier.weight(1f))
                SalesStatCell("Profit %", signedPct(summary.profitPercent), profitColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SalesStatCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = BwType.statNumber.copy(fontSize = 18.sp), color = valueColor)
        Spacer(Modifier.height(3.dp))
        Text(label, style = BwType.statLabel, color = colors.textMuted)
    }
}

@Composable
private fun ProfitBar(summary: SalesSummary) {
    val colors = BwTheme.colors
    val positive = summary.totalProfit >= 0
    val color = if (positive) colors.success else colors.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${if (positive) "▲" else "▼"} Profit ${signedMoney(summary.totalProfit)}",
            style = BwType.body.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(signedPct(summary.profitPercent), style = BwType.micro, color = color)
        }
    }
}

@Composable
private fun SoldCard(sold: SoldItem) {
    val colors = BwTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(colors.placeholderA),
            contentAlignment = Alignment.Center,
        ) {
            if (sold.imageUrl != null) {
                AsyncImage(model = sold.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            } else {
                Icon(painterResource(R.drawable.ic_bw_set), null, tint = colors.textFaint, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${sold.setNumber} ${sold.name}", style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.linkAccent)
            MetaLine("Theme", sold.theme)
            MetaLine("Release", formatRelease(sold.releaseMonth, sold.releaseYear))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.width(130.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            PriceLine("Retail", formatMoney(sold.retailPrice, AppCurrency.VND))
            PriceLine("Sale", formatMoney(sold.saleValue, AppCurrency.VND))
            val profitColor = if (sold.profit >= 0) colors.success else colors.error
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Profit", style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
                Spacer(Modifier.width(6.dp))
                Text(signedMoney(sold.profit), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = profitColor)
            }
            GrowthPill(sold.profitPercent)
        }
    }
}

// ---- See Details ----

@Composable
private fun SeeDetailsDialog(
    item: CollectionItem,
    onDismiss: () -> Unit,
    onDeleteCopy: (String, String) -> Unit,
    onEditCopy: (Copy) -> Unit,
    onAddItem: () -> Unit,
) {
    val colors = BwTheme.colors
    val expanded = remember { mutableStateListOf<String>() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.card,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = BwType.cardTitle, color = colors.text)
                        Text(item.setNumber, style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                    }
                    Text(
                        "✕",
                        color = colors.textMuted,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(6.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))

                // Column header
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text("Cond.", style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1f))
                    Text("Date", style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1.3f))
                    Text("Qty", style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(0.5f))
                    Text("Paid", style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1.5f))
                    Spacer(Modifier.width(84.dp))
                }
                HorizontalDivider(color = colors.borderSoft)

                item.copies.forEachIndexed { index, copy ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (copy.condition == Condition.NEW) "New" else "Used", style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1f))
                        Text(copy.dateAdded, style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1.3f))
                        Text(copy.qty.toString(), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(0.5f))
                        Text(formatMoney(copy.pricePaid, AppCurrency.VND), style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.text, modifier = Modifier.weight(1.5f))
                        Row(modifier = Modifier.width(84.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bw_note),
                                contentDescription = "Toggle note",
                                tint = if (copy.note != null) colors.linkAccent else colors.borderStrong,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (copy.id in expanded) expanded.remove(copy.id) else expanded.add(copy.id)
                                    },
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_bw_edit),
                                contentDescription = "Edit copy",
                                tint = colors.textMuted2,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable { onEditCopy(copy) },
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_bw_delete),
                                contentDescription = "Delete copy",
                                tint = colors.error,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable { onDeleteCopy(item.setNumber, copy.id) },
                            )
                        }
                    }
                    if (copy.id in expanded && copy.note != null) {
                        Text(
                            copy.note,
                            style = BwType.body.copy(fontSize = 11.sp),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (index < item.copies.lastIndex) HorizontalDivider(color = colors.borderSoft)
                }

                HorizontalDivider(color = colors.borderStrong)
                // Avg row
                Row(modifier = Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Avg", style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(1f))
                    Spacer(Modifier.weight(1.3f))
                    Text(item.totalQty.toString(), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(0.5f))
                    Text(formatMoney(item.avgPaid, AppCurrency.VND), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(1.5f))
                    Spacer(Modifier.width(84.dp))
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAddItem,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brandYellow, contentColor = colors.onYellow),
                ) {
                    Text("Add Item", style = BwType.pill.copy(fontSize = 14.sp), modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToCollectionSheet(
    initialSet: CatalogSet?,
    initialCopy: Copy?,
    onDismiss: () -> Unit,
    onSearch: (String) -> List<CatalogSet>,
    onAdd: (CollectionItem) -> Unit,
) {
    val colors = BwTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEdit = initialCopy != null

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initialSet) }
    var paid by remember { mutableStateOf(initialCopy?.pricePaid?.toString() ?: initialSet?.retailPrice?.toString() ?: "") }
    var qty by remember { mutableStateOf(initialCopy?.qty?.toString() ?: "1") }
    var condition by remember { mutableStateOf(initialCopy?.condition ?: Condition.NEW) }
    var note by remember { mutableStateOf(initialCopy?.note ?: "") }
    var dateAdded by remember { mutableStateOf(initialCopy?.dateAdded ?: LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val suggestions = if (selected == null) onSearch(query) else emptyList()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (isEdit) "Edit Item" else "Add to Collection", style = BwType.cardTitle.copy(fontSize = 18.sp), color = colors.text)

            val currentSelection = selected
            if (currentSelection == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Enter set number, e.g. 75192") },
                )
                suggestions.take(6).forEach { set ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selected = set
                                query = ""
                                if (paid.isBlank()) paid = set.retailPrice.toString()
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                    ) {
                        Column {
                            Text("${set.setNumber} ${set.name}", style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
                            Text("${set.theme} · ${set.pieces} pcs", style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${currentSelection.setNumber} ${currentSelection.name}", style = BwType.body.copy(fontWeight = FontWeight.Bold), color = colors.text)
                        Text(currentSelection.theme, style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
                    }
                    Text(
                        "✕",
                        color = colors.textMuted,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { selected = null }
                            .padding(8.dp),
                    )
                }
            }

            FieldLabel("Paid")
            OutlinedTextField(
                value = paid,
                onValueChange = { input -> paid = input.filter { it.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("₫", color = colors.textMuted) },
                placeholder = { Text("0") },
            )

            FieldLabel("Qty")
            OutlinedTextField(
                value = qty,
                onValueChange = { input -> qty = input.filter { it.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            FieldLabel("Condition")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConditionChip("New", condition == Condition.NEW, { condition = Condition.NEW }, Modifier.weight(1f))
                ConditionChip("Used", condition == Condition.USED, { condition = Condition.USED }, Modifier.weight(1f))
            }

            FieldLabel("Date Added")
            Box {
                OutlinedTextField(
                    value = dateAdded,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = true,
                )
                // Transparent overlay so tapping the read-only field opens the date picker.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showDatePicker = true },
                )
            }
            if (showDatePicker) {
                val initMillis = runCatching {
                    LocalDate.parse(dateAdded).toEpochDay() * 86_400_000L
                }.getOrDefault(System.currentTimeMillis())
                val dpState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            dpState.selectedDateMillis?.let { millis ->
                                dateAdded = LocalDate.ofEpochDay(millis / 86_400_000L).toString()
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    },
                ) {
                    DatePicker(state = dpState)
                }
            }

            FieldLabel("Note")
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().height(90.dp),
                placeholder = { Text("Optional") },
            )

            Spacer(Modifier.height(4.dp))
            val canAdd = currentSelection != null && paid.isNotBlank()
            Button(
                onClick = {
                    val set = selected ?: return@Button
                    onAdd(
                        CollectionItem(
                            setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                            theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                            pieces = set.pieces, minifigs = set.minifigs,
                            retailPrice = set.retailPrice,
                            currentValue = null, growthPercent = null, status = set.status,
                            copies = listOf(
                                Copy(
                                    id = initialCopy?.id ?: "${set.setNumber}-${System.currentTimeMillis()}",
                                    condition = condition,
                                    qty = qty.toIntOrNull() ?: 1,
                                    pricePaid = paid.toLongOrNull() ?: 0L,
                                    dateAdded = dateAdded,
                                    note = note.ifBlank { null },
                                ),
                            ),
                        ),
                    )
                },
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandYellow,
                    contentColor = colors.onYellow,
                    disabledContainerColor = colors.track,
                    disabledContentColor = colors.textMuted,
                ),
            ) {
                Text(if (isEdit) "Save" else "Add Item", style = BwType.pill.copy(fontSize = 14.sp), modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        color = BwTheme.colors.textMuted,
    )
}

@Composable
private fun ConditionChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    val bg = if (selected) colors.brandYellow else colors.card
    val fg = if (selected) colors.onYellow else colors.text
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = fg)
    }
}
