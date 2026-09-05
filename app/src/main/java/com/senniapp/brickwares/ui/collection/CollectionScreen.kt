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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.Banner
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.ui.components.ChipItem
import com.senniapp.brickwares.ui.components.EmptyStateArt
import com.senniapp.brickwares.ui.components.SignInPromptCard
import com.senniapp.brickwares.ui.components.blinkAttention
import com.senniapp.brickwares.ui.components.rememberIsLoggedIn
import com.senniapp.brickwares.ui.components.rememberIsOnline
import com.senniapp.brickwares.data.repository.salesSummaryOf
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.ui.components.GrowthPill
import com.senniapp.brickwares.ui.components.LoadingScreen
import com.senniapp.brickwares.ui.components.MetaLine
import com.senniapp.brickwares.ui.components.ItemDetailsDialog
import com.senniapp.brickwares.ui.components.SellCopyDialog
import com.senniapp.brickwares.ui.components.SetThumb
import com.senniapp.brickwares.ui.components.PaginationBar
import com.senniapp.brickwares.ui.components.PriceLine
import com.senniapp.brickwares.ui.components.ValuePriceLine
import com.senniapp.brickwares.ui.components.animatedNumber
import com.senniapp.brickwares.ui.components.StatCardRow
import com.senniapp.brickwares.ui.components.StatEntry
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.components.SwipeToDelete
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.CatalogImages
import com.senniapp.brickwares.util.formatCount
import com.senniapp.brickwares.util.formatIn
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.util.formatMoneyFrom
import com.senniapp.brickwares.util.oneDecimal
import com.senniapp.brickwares.ui.components.releaseLabel
import kotlin.math.roundToInt

@Composable
fun CollectionScreen(
    onOpenSetDetail: (String) -> Unit,
    onOpenMinifigDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectionContent(
        state = state,
        isOnline = rememberIsOnline(),
        isLoggedIn = rememberIsLoggedIn(),
        onOpenSetDetail = onOpenSetDetail,
        onOpenMinifigDetail = onOpenMinifigDetail,
        onFilterSelected = viewModel::onFilterSelected,
        onPageChange = viewModel::onPageChange,
        onSalesPageChange = viewModel::onSalesPageChange,
        onToggleMode = viewModel::onToggleMode,
        onAddClick = viewModel::onAddClick,
        onItemDetail = viewModel::onItemDetail,
        onDismissAddSheet = viewModel::onDismissAddSheet,
        onSearchCatalog = viewModel::searchCatalog,
        onAddItem = viewModel::submitAddSheet,
        onAddSale = viewModel::submitAddSheetSale,
        onEditSaleSubmit = viewModel::submitEditSale,
        onDismissDetail = viewModel::onDismissDetail,
        onDeleteCopy = viewModel::onDeleteCopy,
        onDetailAddCollection = viewModel::onDetailAddCollection,
        onDetailAddSale = viewModel::onDetailAddSale,
        onEditCopy = viewModel::onEditCopy,
        onSellCopyRequest = viewModel::onSellCopyRequest,
        onDismissSell = viewModel::onDismissSell,
        onConfirmSell = viewModel::onConfirmSell,
        onSaleDetail = viewModel::onSaleDetail,
        onEditSale = viewModel::onEditSale,
        onDeleteSale = viewModel::onDeleteSale,
        onRequestDeleteItem = viewModel::onRequestDeleteItem,
        onConfirmDeleteItem = viewModel::onConfirmDeleteItem,
        onCancelDeleteItem = viewModel::onCancelDeleteItem,
        onRequestDeleteSale = viewModel::onRequestDeleteSale,
        onConfirmDeleteSale = viewModel::onConfirmDeleteSale,
        onCancelDeleteSale = viewModel::onCancelDeleteSale,
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
    onOpenMinifigDetail: (String) -> Unit = {},
    onFilterSelected: (CollectionFilter) -> Unit,
    onPageChange: (Int) -> Unit,
    onSalesPageChange: (Int) -> Unit,
    onToggleMode: () -> Unit,
    onAddClick: () -> Unit,
    onItemDetail: (CollectionItem) -> Unit,
    onDismissAddSheet: () -> Unit,
    onSearchCatalog: (String) -> List<CatalogSet>,
    onAddItem: (CollectionItem) -> Unit,
    onAddSale: (CollectionItem, Long) -> Unit,
    onEditSaleSubmit: (CollectionItem, Long) -> Unit,
    onDismissDetail: () -> Unit,
    onDeleteCopy: (String, String) -> Unit,
    onDetailAddCollection: () -> Unit,
    onDetailAddSale: () -> Unit,
    onEditCopy: (CollectionItem, Copy) -> Unit,
    onSellCopyRequest: (String, Copy) -> Unit,
    onDismissSell: () -> Unit,
    onConfirmSell: (Int, Long, AppCurrency, String) -> Unit,
    onSaleDetail: (SoldItem) -> Unit,
    onEditSale: (SoldItem) -> Unit,
    onDeleteSale: (String) -> Unit,
    onRequestDeleteItem: (CollectionItem) -> Unit,
    onConfirmDeleteItem: () -> Unit,
    onCancelDeleteItem: () -> Unit,
    onRequestDeleteSale: (String) -> Unit,
    onConfirmDeleteSale: () -> Unit,
    onCancelDeleteSale: () -> Unit,
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
                    title = stringResource(if (sales) R.string.sales_title else R.string.collection_title),
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
                                StatEntry(R.drawable.ic_bw_set, 0L, stringResource(R.string.stat_sets)),
                                StatEntry(R.drawable.ic_bw_minifig, 0L, stringResource(R.string.stat_minifigs)),
                                StatEntry(R.drawable.ic_bw_pieces, 0L, stringResource(R.string.stat_pieces)),
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
                        message = stringResource(if (sales) R.string.sales_signin_prompt else R.string.collection_signin_prompt),
                        onSignIn = { SignInController.request() },
                    )
                }
            } else if (!sales) {
                state.summary?.let { summary ->
                    item {
                        StatCardRow(
                            entries = listOf(
                                StatEntry(R.drawable.ic_bw_set, summary.setCount.toLong(), stringResource(R.string.stat_sets)),
                                StatEntry(R.drawable.ic_bw_minifig, summary.minifigCount.toLong(), stringResource(R.string.stat_minifigs)),
                                StatEntry(R.drawable.ic_bw_pieces, summary.pieceCount.toLong(), stringResource(R.string.stat_pieces)),
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
                    item { EmptyStateArt(stringResource(R.string.collection_empty)) }
                } else {
                    items(state.pageItems, key = { it.setNumber }) { item ->
                        SwipeToDelete(onSwiped = { onRequestDeleteItem(item) }, autoDismiss = false) {
                            ItemCard(
                                item = item,
                                onDetail = { onItemDetail(item) },
                                onOpenDetail = {
                                    if (item.itemType == ItemType.MINIFIG) onOpenMinifigDetail(item.setNumber)
                                    else onOpenSetDetail(item.setNumber)
                                },
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
                state.salesSummary?.let { _ ->
                    item {
                        // Recompute in the display currency so a single-currency total is exact and a
                        // currency switch updates the tiles (the guard just gates "sales loaded").
                        val s = salesSummaryOf(state.soldItems, BwTheme.currency)
                        SalesStatsRow(s)
                        Spacer(Modifier.height(12.dp))
                        ProfitBar(s)
                        Spacer(Modifier.height(14.dp))
                    }
                }
                if (state.soldItems.isEmpty()) {
                    item { EmptyStateArt(stringResource(R.string.sales_empty)) }
                } else {
                    items(state.salesPageItems, key = { it.id }) { sold ->
                        SwipeToDelete(onSwiped = { onRequestDeleteSale(sold.id) }, autoDismiss = false) {
                            SoldCard(
                                sold,
                                onDetail = { onSaleDetail(sold) },
                                onOpenDetail = {
                                    if (sold.itemType == ItemType.MINIFIG) onOpenMinifigDetail(sold.setNumber)
                                    else onOpenSetDetail(sold.setNumber)
                                },
                            )
                        }
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
                contentDescription = stringResource(R.string.collection_toggle_sales_cd),
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
                    contentDescription = stringResource(R.string.collection_add_fab_cd),
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
                allowSalesMode = true,
                onAddSale = onAddSale,
                // Opened in Sales mode by the Sales-mode FAB or the modal's Sales-tab add button.
                initialSalesMode = state.addSheetSalesMode,
                initialSalePrice = state.editingSalePrice,
                onEditSale = onEditSaleSubmit,
            )
        }

        // Merged See Details — collection copies and/or sales for the same set, with a toggle when both.
        if (state.detailSetNumber != null && (state.detailItem != null || state.detailSales.isNotEmpty())) {
            ItemDetailsDialog(
                item = state.detailItem,
                sales = state.detailSales,
                onDismiss = onDismissDetail,
                onDeleteCopy = onDeleteCopy,
                onEditCopy = { copy -> state.detailItem?.let { onEditCopy(it, copy) } },
                onSellCopy = { copy -> state.detailItem?.let { onSellCopyRequest(it.setNumber, copy) } },
                onEditSale = onEditSale,
                onDeleteSale = onDeleteSale,
                onAddCollection = onDetailAddCollection,
                onAddSale = onDetailAddSale,
                initialTab = state.detailInitialTab,
            )
        }

        state.sellTarget?.let { (item, copy) ->
            SellCopyDialog(
                item = item,
                copy = copy,
                onDismiss = onDismissSell,
                onConfirm = onConfirmSell,
            )
        }

        state.pendingDeleteItem?.let { item ->
            ConfirmDeleteDialog(
                message = stringResource(R.string.collection_delete_confirm, item.name),
                onConfirm = onConfirmDeleteItem,
                onCancel = onCancelDeleteItem,
            )
        }

        state.pendingDeleteSale?.let { sold ->
            ConfirmDeleteDialog(
                message = stringResource(R.string.sales_delete_confirm, sold.name),
                onConfirm = onConfirmDeleteSale,
                onCancel = onCancelDeleteSale,
            )
        }

        BwToast(message = state.toastMessage?.resolve(), onDismiss = onToastShown)
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
                Text(stringResource(R.string.collection_delete_title), style = BwType.cardTitle, color = colors.text)
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
                        Text(stringResource(R.string.action_cancel), style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
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
                        Text(stringResource(R.string.action_delete), style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selected: CollectionFilter, onSelect: (CollectionFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipItem(R.drawable.ic_bw_all, stringResource(R.string.filter_all), selected == CollectionFilter.ALL, { onSelect(CollectionFilter.ALL) }, Modifier.weight(1f))
        ChipItem(R.drawable.ic_bw_set, stringResource(R.string.filter_set), selected == CollectionFilter.SET, { onSelect(CollectionFilter.SET) }, Modifier.weight(1f))
        ChipItem(R.drawable.ic_bw_minifig, stringResource(R.string.filter_minifig), selected == CollectionFilter.MINIFIG, { onSelect(CollectionFilter.MINIFIG) }, Modifier.weight(1f))
    }
}

@Composable
private fun ItemCard(item: CollectionItem, onDetail: () -> Unit, onOpenDetail: () -> Unit) {
    val colors = BwTheme.colors
    val isFig = item.itemType == ItemType.MINIFIG
    // Sets: box shot first, falling back to the render. Minifigs: their stored Rebrickable image.
    val thumbUrl = if (isFig) item.imageUrl else CatalogImages.boxUrl(item.setNumber)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        SetThumb(
            imageUrl = thumbUrl,
            fallbackUrl = if (isFig) null else CatalogImages.thumbUrl(item.setNumber),
            itemType = item.itemType,
            size = 72.dp,
            iconSize = 30.dp,
            // Tap the image → full-screen gallery; the title still opens the detail.
            galleryImages = if (isFig) listOfNotNull(item.imageUrl) else CatalogImages.galleryUrls(item.setNumber),
        )

        Spacer(Modifier.width(12.dp))

        // Title + meta (roomier vertical rhythm). Minifigs mirror the Search minifig card
        // (fig number, name, "in N sets"); sets show the theme/release/pieces meta + status badge.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (isFig) 4.dp else 5.dp),
        ) {
            if (isFig) {
                Text(item.setNumber, style = BwType.micro, color = colors.textMuted)
                Text(
                    item.name,
                    style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = colors.linkAccent,
                    modifier = Modifier.clickable(onClick = onOpenDetail),
                )
                if (item.minifigSetCount > 0) {
                    Text(stringResource(R.string.search_minifig_sets, item.minifigSetCount), style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                }
            } else {
                Text(
                    text = "${item.setNumber} ${item.name}",
                    style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = colors.linkAccent,
                    modifier = Modifier.clickable(onClick = onOpenDetail),
                )
                MetaLine(stringResource(R.string.meta_theme), item.theme)
                MetaLine(stringResource(R.string.meta_release), releaseLabel(item.releaseMonth, item.releaseYear))
                MetaLine(stringResource(R.string.meta_pieces_minifigs), "${item.pieces} / ${item.minifigs}")
                StatusBadge(item.status)
            }
        }

        Spacer(Modifier.width(10.dp))

        // Price column.
        Column(
            modifier = Modifier.width(130.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isFig) {
                // Minifig: Paid → community value → Growth (no retail; value always shown).
                PriceLine(stringResource(R.string.price_paid), formatIn(item.totalPaidIn(BwTheme.currency), BwTheme.currency))
                ValuePriceLine(item.currentValueInfo)
                item.growthPercent?.let { GrowthPill(it) }
            } else {
                // Set: the current value shows only for retired / promo / magazine sets — Retail, a
                // divider, then Paid → Value → Growth. Otherwise just Retail → Paid → Growth (no value).
                // Growth always shows (repo picks the reference: value when shown, else retail vs paid).
                val showValue = item.status == Availability.RETIRED || item.status == Availability.PROMO || item.status == Availability.MAGAZINE
                PriceLine(stringResource(R.string.price_retail), formatMoney(item.retailPrice, BwTheme.currency))
                if (showValue) HorizontalDivider(color = colors.borderSoft)
                PriceLine(stringResource(R.string.price_paid), formatIn(item.totalPaidIn(BwTheme.currency), BwTheme.currency))
                if (showValue) ValuePriceLine(item.currentValueInfo)
                item.growthPercent?.let { GrowthPill(it) }
            }
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.track)
                    .clickable(onClick = onDetail)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_bw_check), contentDescription = null, tint = colors.text, modifier = Modifier.size(13.dp))
                Text(stringResource(R.string.action_see_detail), style = BwType.micro.copy(fontSize = 11.sp), color = colors.text)
            }
        }
    }
}

// ---- Sales mode ----

private fun signedMoney(v: Long, from: AppCurrency, to: AppCurrency): String =
    (if (v > 0) "+" else "") + formatMoneyFrom(v, from, to)

private fun signedPct(p: Double): String {
    // One decimal place (matches the growth pills), so sub-1% profit isn't rounded to "0%".
    val rounded = (p * 10.0).roundToInt() / 10.0
    return (if (rounded > 0) "+" else "") + oneDecimal(rounded) + "%"
}

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
            Text(stringResource(R.string.sales_total_sold), style = BwType.micro, color = gold)
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
                Text(stringResource(R.string.sales_sale_value), style = BwType.micro.copy(fontSize = 12.sp), color = gold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                // summary money is already in the display currency (see the recompute at the call site).
                formatIn(animatedNumber(summary.totalSaleValue, "sales_value"), BwTheme.currency),
                style = BwType.statNumber.copy(fontSize = 22.sp),
                color = colors.text,
            )
        }
    }
}

@Composable
private fun ProfitBar(summary: SalesSummary) {
    val colors = BwTheme.colors
    val profit = summary.totalProfit
    // Zero profit reads neutral: gray, no +, no trend arrow.
    val color = when {
        profit > 0L -> colors.success
        profit < 0L -> colors.error
        else -> colors.textMuted
    }
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
            if (profit != 0L) {
                Icon(
                    painter = painterResource(
                        if (profit > 0L) R.drawable.ic_bw_trending_up else R.drawable.ic_bw_trending_down,
                    ),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                // summary.totalProfit is already in the display currency (recomputed at the call site).
                stringResource(R.string.sales_profit_prefix, signedMoney(animatedNumber(summary.totalProfit, "sales_profit"), BwTheme.currency, BwTheme.currency)),
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
private fun SoldCard(sold: SoldItem, onDetail: () -> Unit, onOpenDetail: () -> Unit) {
    val colors = BwTheme.colors
    val isFig = sold.itemType == ItemType.MINIFIG
    val thumbUrl = if (isFig) sold.imageUrl else CatalogImages.boxUrl(sold.setNumber)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        SetThumb(
            imageUrl = thumbUrl,
            fallbackUrl = if (isFig) null else CatalogImages.thumbUrl(sold.setNumber),
            itemType = sold.itemType,
            size = 72.dp,
            iconSize = 30.dp,
            // Tap the image → full-screen gallery; the title still opens the detail.
            galleryImages = if (isFig) listOfNotNull(sold.imageUrl) else CatalogImages.galleryUrls(sold.setNumber),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (isFig) 4.dp else 5.dp)) {
            if (isFig) {
                Text(sold.setNumber, style = BwType.micro, color = colors.textMuted)
                Text(sold.name, style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.linkAccent, modifier = Modifier.clickable(onClick = onOpenDetail))
            } else {
                Text("${sold.setNumber} ${sold.name}", style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.linkAccent, modifier = Modifier.clickable(onClick = onOpenDetail))
                MetaLine(stringResource(R.string.meta_theme), sold.theme)
                MetaLine(stringResource(R.string.meta_release), releaseLabel(sold.releaseMonth, sold.releaseYear))
                StatusBadge(sold.status)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.width(130.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Set: Retail (+ current Value for retired/promo/magazine) then a divider, then the sale
            // figures. Minifigs skip the retail/value/divider block.
            if (!isFig) {
                val showValue = sold.status == Availability.RETIRED || sold.status == Availability.PROMO || sold.status == Availability.MAGAZINE
                PriceLine(stringResource(R.string.price_retail), formatMoney(sold.retailPrice, BwTheme.currency))
                if (showValue) ValuePriceLine(sold.currentValueInfo)
                HorizontalDivider(color = colors.borderSoft)
            }
            PriceLine(stringResource(R.string.price_paid), formatMoneyFrom(sold.pricePaid, sold.currency, BwTheme.currency))
            PriceLine(stringResource(R.string.price_sale), formatMoneyFrom(sold.saleValue, sold.currency, BwTheme.currency))
            val profitColor = if (sold.profit >= 0) colors.success else colors.error
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sales_profit_label), style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
                Spacer(Modifier.width(6.dp))
                Text(signedMoney(sold.profit, sold.currency, BwTheme.currency), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = profitColor)
            }
            GrowthPill(sold.profitPercent)
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.track)
                    .clickable(onClick = onDetail)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_bw_check), contentDescription = null, tint = colors.text, modifier = Modifier.size(13.dp))
                Text(stringResource(R.string.action_see_detail), style = BwType.micro.copy(fontSize = 11.sp), color = colors.text)
            }
        }
    }
}
