package com.senniapp.brickwares.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.Banner
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.ChipItem
import com.senniapp.brickwares.ui.components.EmptyStateArt
import com.senniapp.brickwares.ui.components.SignInPromptCard
import com.senniapp.brickwares.ui.components.blinkAttention
import com.senniapp.brickwares.ui.components.rememberIsLoggedIn
import com.senniapp.brickwares.ui.components.rememberIsOnline
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.ui.components.GrowthPill
import com.senniapp.brickwares.ui.components.LoadingScreen
import com.senniapp.brickwares.ui.components.MetaLine
import com.senniapp.brickwares.ui.components.PaginationBar
import com.senniapp.brickwares.ui.components.PriceLine
import com.senniapp.brickwares.ui.components.animatedNumber
import com.senniapp.brickwares.ui.components.StatCardRow
import com.senniapp.brickwares.ui.components.StatEntry
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.components.SwipeToDelete
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatCount
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.util.formatRelease
import kotlin.math.roundToInt

@Composable
fun CollectionScreen(
    onOpenSetDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectionContent(
        state = state,
        isOnline = rememberIsOnline(),
        isLoggedIn = rememberIsLoggedIn(),
        onOpenSetDetail = onOpenSetDetail,
        onFilterSelected = viewModel::onFilterSelected,
        onPageChange = viewModel::onPageChange,
        onSalesPageChange = viewModel::onSalesPageChange,
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
        onRequestDeleteItem = viewModel::onRequestDeleteItem,
        onConfirmDeleteItem = viewModel::onConfirmDeleteItem,
        onCancelDeleteItem = viewModel::onCancelDeleteItem,
        onToastShown = viewModel::onToastShown,
        modifier = modifier,
    )
}

@Composable
private fun CollectionContent(
    state: CollectionUiState,
    isOnline: Boolean = true,
    isLoggedIn: Boolean = true,
    onOpenSetDetail: (String) -> Unit,
    onFilterSelected: (CollectionFilter) -> Unit,
    onPageChange: (Int) -> Unit,
    onSalesPageChange: (Int) -> Unit,
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
    onRequestDeleteItem: (CollectionItem) -> Unit,
    onConfirmDeleteItem: () -> Unit,
    onCancelDeleteItem: () -> Unit,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    val listState = rememberLazyListState()
    // Jump to the top when the active page changes (the pager sits at the bottom of the list).
    LaunchedEffect(state.currentPage, state.salesCurrentPage, state.mode) { listState.scrollToItem(0) }
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        if (state.isLoading) {
            LoadingScreen()
            return@Box
        }
        val sales = state.mode == CollectionMode.SALES
        // Blink the Add FAB only when the *collection* is empty — in Sales mode the Add FAB still
        // adds to the collection (there's no add-sale flow yet), so blinking it there would prompt a
        // dead-end action. Sales still shows the empty-state art, just no blink.
        val blinkAdd = !sales && state.visibleItems.isEmpty()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        ) {
            item {
                Banner(
                    imageAsset = if (sales) "file:///android_asset/sales_banner.png" else "file:///android_asset/collection_banner.png",
                    title = if (sales) "My Sales" else "My Collection",
                )
                Spacer(Modifier.height(16.dp))
            }
            if (!isLoggedIn) {
                // Logged out: still show the summary (all zeros), then a sign-in prompt in place of
                // the list — only signed-in users can add/edit/delete.
                if (!sales) {
                    item {
                        StatCardRow(
                            entries = listOf(
                                StatEntry(R.drawable.ic_bw_set, 0L, "Sets"),
                                StatEntry(R.drawable.ic_bw_minifig, 0L, "Minifigs"),
                                StatEntry(R.drawable.ic_bw_pieces, 0L, "Pieces"),
                            ),
                            keyPrefix = "collection",
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                } else {
                    item {
                        val zero = SalesSummary(0, 0L, 0L, 0.0, 0.0)
                        SalesStatsRow(zero)
                        Spacer(Modifier.height(12.dp))
                        ProfitBar(zero)
                        Spacer(Modifier.height(14.dp))
                    }
                }
                item {
                    SignInPromptCard(
                        message = if (sales) "Sign in to track your sales"
                        else "Sign in to view and manage your collection",
                        onSignIn = { SignInController.request() },
                    )
                }
            } else if (!sales) {
                state.summary?.let { summary ->
                    item {
                        StatCardRow(
                            entries = listOf(
                                StatEntry(R.drawable.ic_bw_set, summary.setCount.toLong(), "Sets"),
                                StatEntry(R.drawable.ic_bw_minifig, summary.minifigCount.toLong(), "Minifigs"),
                                StatEntry(R.drawable.ic_bw_pieces, summary.pieceCount.toLong(), "Pieces"),
                            ),
                            keyPrefix = "collection",
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
                item {
                    FilterChips(selected = state.filter, onSelect = onFilterSelected)
                    Spacer(Modifier.height(14.dp))
                }
                if (state.visibleItems.isEmpty()) {
                    item { EmptyStateArt("Nothing here yet, add something") }
                } else {
                    items(state.pageItems, key = { it.setNumber }) { item ->
                        SwipeToDelete(onSwiped = { onRequestDeleteItem(item) }, autoDismiss = false) {
                            ItemCard(
                                item = item,
                                onDetail = { onItemDetail(item) },
                                onOpenDetail = { onOpenSetDetail(item.setNumber) },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    item {
                        PaginationBar(
                            currentPage = state.currentPage,
                            totalPages = state.pageCount,
                            onPageSelected = onPageChange,
                        )
                    }
                }
            } else {
                // Always show the sales stats + profit block (zeros when empty), then either the
                // sold-item list or the empty-state art below it.
                state.salesSummary?.let { s ->
                    item {
                        SalesStatsRow(s)
                        Spacer(Modifier.height(12.dp))
                        ProfitBar(s)
                        Spacer(Modifier.height(14.dp))
                    }
                }
                if (state.soldItems.isEmpty()) {
                    item { EmptyStateArt("No sales yet") }
                } else {
                    items(state.salesPageItems, key = { it.setNumber }) { sold ->
                        SoldCard(sold)
                        Spacer(Modifier.height(12.dp))
                    }
                    item {
                        PaginationBar(
                            currentPage = state.salesCurrentPage,
                            totalPages = state.salesPageCount,
                            onPageSelected = onSalesPageChange,
                        )
                    }
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

        // Add FAB (bottom-end) — blinks while the active tab is empty, to prompt the first add.
        // Only for signed-in users, and hidden offline (adding needs the catalog/network).
        if (isOnline && isLoggedIn) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .blinkAttention(enabled = blinkAdd)
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

        state.pendingDeleteItem?.let { item ->
            ConfirmDeleteDialog(
                message = "Delete \"${item.name}\" and all its copies from your collection?",
                onConfirm = onConfirmDeleteItem,
                onCancel = onCancelDeleteItem,
            )
        }

        BwToast(message = state.toastMessage, onDismiss = onToastShown)
    }
}

@Composable
private fun ConfirmDeleteDialog(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = BwTheme.colors
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(18.dp), color = colors.card) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Delete set", style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
                            .clickable(onClick = onCancel)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.error)
                            .clickable(onClick = onConfirm)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Delete", style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    }
                }
            }
        }
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
private fun ItemCard(item: CollectionItem, onDetail: () -> Unit, onOpenDetail: () -> Unit) {
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
                .background(colors.placeholderA)
                .clickable(onClick = onOpenDetail),
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
                modifier = Modifier.clickable(onClick = onOpenDetail),
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

// ---- Sales mode ----

private fun signedMoney(v: Long): String =
    (if (v > 0) "+" else "") + formatMoney(v, AppCurrency.VND)

private fun signedPct(p: Double): String = "${if (p >= 0) "+" else ""}${p.roundToInt()}%"

@Composable
private fun SalesStatsRow(summary: SalesSummary) {
    val colors = BwTheme.colors
    val cream = colors.brandYellow.copy(alpha = 0.16f)
    val gold = colors.linkAccent2
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Total Sold — circular badge (the taller element)
        Column(
            modifier = Modifier.size(112.dp).clip(CircleShape).background(cream),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(R.drawable.ic_bw_set), null, tint = gold, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text("TOTAL SOLD", style = BwType.micro, color = gold)
            Spacer(Modifier.height(2.dp))
            Text(animatedNumber(summary.totalSold.toLong(), "sales_total_sold").toString(), style = BwType.statNumber, color = colors.text)
        }
        // Sale Value — card, shorter than the circle and vertically centered against it
        Column(
            modifier = Modifier
                .weight(1f)
                .height(88.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cream)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("$", style = BwType.cardTitle.copy(fontSize = 18.sp), color = gold)
                Text("SALE VALUE", style = BwType.micro.copy(fontSize = 12.sp), color = gold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatMoney(animatedNumber(summary.totalSaleValue, "sales_value"), AppCurrency.VND),
                style = BwType.statNumber.copy(fontSize = 22.sp),
                color = colors.text,
            )
        }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                painter = painterResource(
                    if (positive) R.drawable.ic_bw_trending_up else R.drawable.ic_bw_trending_down,
                ),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Profit ${signedMoney(animatedNumber(summary.totalProfit, "sales_profit"))}",
                style = BwType.body.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
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
