package com.senniapp.brickwares.ui.wishlist

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.Banner
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.ChipItem
import com.senniapp.brickwares.ui.components.EmptyStateArt
import com.senniapp.brickwares.ui.components.blinkAttention
import com.senniapp.brickwares.ui.components.GrowthPill
import com.senniapp.brickwares.ui.components.LoadingScreen
import com.senniapp.brickwares.ui.components.MetaLine
import com.senniapp.brickwares.ui.components.PriceLine
import com.senniapp.brickwares.ui.components.StatCardRow
import com.senniapp.brickwares.ui.components.StatEntry
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.components.PaginationBar
import com.senniapp.brickwares.ui.components.SwipeToDelete
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatCount
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.util.formatRelease

@Composable
fun WishlistScreen(
    onNavigateToSearch: () -> Unit,
    onOpenSetDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WishlistViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WishlistContent(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onPageChange = viewModel::onPageChange,
        onNavigateToSearch = onNavigateToSearch,
        onOpenSetDetail = onOpenSetDetail,
        onSearchCatalog = viewModel::searchCatalog,
        onMoveClick = viewModel::onMoveClick,
        onDismissMove = viewModel::onDismissMove,
        onMoveSubmit = viewModel::onMoveSubmit,
        onRemove = viewModel::onRemove,
        onToastShown = viewModel::onToastShown,
        modifier = modifier,
    )
}

@Composable
private fun WishlistContent(
    state: WishlistUiState,
    onFilterSelected: (WishlistFilter) -> Unit,
    onPageChange: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onOpenSetDetail: (String) -> Unit,
    onSearchCatalog: (String) -> List<CatalogSet>,
    onMoveClick: (WishlistItem) -> Unit,
    onDismissMove: () -> Unit,
    onMoveSubmit: (CollectionItem) -> Unit,
    onRemove: (String) -> Unit,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    val listState = rememberLazyListState()
    // Jump to the top when the page changes (the pager sits at the bottom of the list).
    LaunchedEffect(state.currentPage) { listState.scrollToItem(0) }
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        if (state.isLoading) {
            LoadingScreen()
            return@Box
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        ) {
            item {
                Banner(
                    imageAsset = "file:///android_asset/wishlist_banner.png",
                    title = "My Wishlist",
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                StatCardRow(
                    entries = listOf(
                        StatEntry(R.drawable.ic_bw_set, state.setCount.toLong(), "Sets"),
                        StatEntry(R.drawable.ic_bw_minifig, state.minifigCount.toLong(), "Minifigs"),
                        StatEntry(R.drawable.ic_bw_pieces, state.pieceCount.toLong(), "Pieces"),
                    ),
                    keyPrefix = "wishlist",
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                FilterChips(selected = state.filter, onSelect = onFilterSelected)
                Spacer(Modifier.height(14.dp))
            }
            if (!state.isLoading && state.visibleItems.isEmpty()) {
                item { EmptyStateArt("Nothing here yet, look something up") }
            }
            items(state.pageItems, key = { it.setNumber }) { item ->
                SwipeToDelete(onSwiped = { onRemove(item.setNumber) }, autoDismiss = true) {
                    WishlistCard(
                        item = item,
                        onMove = { onMoveClick(item) },
                        onRemove = { onRemove(item.setNumber) },
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

        // Search FAB (bottom-end) — sends the user to the Search tab to find sets to wishlist;
        // blinks while the wishlist is empty to prompt that first search.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .blinkAttention(enabled = !state.isLoading && state.visibleItems.isEmpty())
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.brandYellow)
                .clickable(onClick = onNavigateToSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bw_search),
                contentDescription = "Search for sets",
                tint = colors.onYellow,
                modifier = Modifier.size(26.dp),
            )
        }

        state.moveTarget?.let { target ->
            AddToCollectionSheet(
                initialSet = target,
                initialCopy = null,
                onDismiss = onDismissMove,
                onSearch = onSearchCatalog,
                onAdd = onMoveSubmit,
            )
        }

        BwToast(message = state.toastMessage, onDismiss = onToastShown)
    }
}

@Composable
private fun FilterChips(selected: WishlistFilter, onSelect: (WishlistFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipItem(R.drawable.ic_bw_all, "All", selected == WishlistFilter.ALL, { onSelect(WishlistFilter.ALL) }, Modifier.weight(1f))
        ChipItem(R.drawable.ic_bw_set, "Set", selected == WishlistFilter.SET, { onSelect(WishlistFilter.SET) }, Modifier.weight(1f))
        ChipItem(R.drawable.ic_bw_minifig, "Minifig", selected == WishlistFilter.MINIFIG, { onSelect(WishlistFilter.MINIFIG) }, Modifier.weight(1f))
    }
}

/** The filled-heart accent from the design handoff (matches the "Wishlisted" glyph). */
private val WishlistHeart = Color(0xFFC9506F)

@Composable
private fun WishlistCard(item: WishlistItem, onMove: () -> Unit, onRemove: () -> Unit, onOpenDetail: () -> Unit) {
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

        // Price column + wishlist actions (swipe the card to remove).
        Column(
            modifier = Modifier.width(120.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PriceLine("Retail", formatMoney(item.retailPrice, AppCurrency.VND))
            if (item.currentValue != null) {
                PriceLine("Value", formatMoney(item.currentValue, AppCurrency.VND))
                item.growthPercent?.let { GrowthPill(it) }
            }
            // Move to collection — full-width yellow button (matches design).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.brandYellow)
                    .clickable(onClick = onMove)
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(painter = painterResource(R.drawable.ic_bw_pieces), contentDescription = null, tint = colors.onYellow, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add", style = BwType.micro.copy(fontSize = 11.sp), color = colors.onYellow)
            }
            // "Wishlisted" button — tap (or swipe the card) to remove from the wishlist.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
                    .clickable(onClick = onRemove)
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(painter = painterResource(R.drawable.ic_bw_heart), contentDescription = "Remove from wishlist", tint = WishlistHeart, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Wishlisted", style = BwType.micro.copy(fontSize = 11.sp), color = colors.text)
            }
        }
    }
}

