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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.senniapp.brickwares.ui.components.ChipItem
import com.senniapp.brickwares.ui.components.GrowthPill
import com.senniapp.brickwares.ui.components.MetaLine
import com.senniapp.brickwares.ui.components.PriceLine
import com.senniapp.brickwares.ui.components.StatCardRow
import com.senniapp.brickwares.ui.components.StatEntry
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatCount
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.util.formatRelease

@Composable
fun WishlistScreen(
    modifier: Modifier = Modifier,
    viewModel: WishlistViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WishlistContent(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onAddClick = viewModel::onAddClick,
        onDismissAddSheet = viewModel::onDismissAddSheet,
        onSearchCatalog = viewModel::searchCatalog,
        onAddToWishlist = viewModel::onAddToWishlist,
        onMoveClick = viewModel::onMoveClick,
        onDismissMove = viewModel::onDismissMove,
        onMoveSubmit = viewModel::onMoveSubmit,
        onRemove = viewModel::onRemove,
        modifier = modifier,
    )
}

@Composable
private fun WishlistContent(
    state: WishlistUiState,
    onFilterSelected: (WishlistFilter) -> Unit,
    onAddClick: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onSearchCatalog: (String) -> List<CatalogSet>,
    onAddToWishlist: (CatalogSet) -> Unit,
    onMoveClick: (WishlistItem) -> Unit,
    onDismissMove: () -> Unit,
    onMoveSubmit: (CollectionItem) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(
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
                        StatEntry(R.drawable.ic_bw_set, state.setCount.toString(), "Sets"),
                        StatEntry(R.drawable.ic_bw_minifig, state.minifigCount.toString(), "Minifigs"),
                        StatEntry(R.drawable.ic_bw_pieces, formatCount(state.pieceCount), "Pieces"),
                    ),
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                FilterChips(selected = state.filter, onSelect = onFilterSelected)
                Spacer(Modifier.height(14.dp))
            }
            if (!state.isLoading && state.visibleItems.isEmpty()) {
                item { EmptyState() }
            }
            items(state.visibleItems, key = { it.setNumber }) { item ->
                WishlistCard(
                    item = item,
                    onMove = { onMoveClick(item) },
                    onRemove = { onRemove(item.setNumber) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // Add FAB (bottom-end) — opens catalog search to add a set to the wishlist.
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
                contentDescription = "Add to wishlist",
                tint = colors.onYellow,
                modifier = Modifier.size(26.dp),
            )
        }

        if (state.showAddSheet) {
            AddToWishlistSheet(
                onDismiss = onDismissAddSheet,
                onSearch = onSearchCatalog,
                onAdd = onAddToWishlist,
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

@Composable
private fun WishlistCard(item: WishlistItem, onMove: () -> Unit, onRemove: () -> Unit) {
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

        // Price column + wishlist actions.
        Column(
            modifier = Modifier.width(132.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PriceLine("Retail", formatMoney(item.retailPrice, AppCurrency.VND))
            if (item.currentValue != null) {
                PriceLine("Value", formatMoney(item.currentValue, AppCurrency.VND))
                item.growthPercent?.let { GrowthPill(it) }
            }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Remove from wishlist.
                Icon(
                    painter = painterResource(R.drawable.ic_bw_delete),
                    contentDescription = "Remove from wishlist",
                    tint = colors.error,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRemove),
                )
                // Move to collection.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.brandYellow)
                        .clickable(onClick = onMove)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(painter = painterResource(R.drawable.ic_bw_plus), contentDescription = null, tint = colors.onYellow, modifier = Modifier.size(13.dp))
                    Text("Collection", style = BwType.micro.copy(fontSize = 11.sp), color = colors.onYellow)
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = BwTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bw_heart),
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Your wishlist is empty", style = BwType.cardTitle, color = colors.textMuted)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap + to add sets you're eyeing.",
            style = BwType.body.copy(fontSize = 13.sp),
            color = colors.textFaint,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToWishlistSheet(
    onDismiss: () -> Unit,
    onSearch: (String) -> List<CatalogSet>,
    onAdd: (CatalogSet) -> Unit,
) {
    val colors = BwTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val suggestions = onSearch(query)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Add to Wishlist", style = BwType.cardTitle.copy(fontSize = 18.sp), color = colors.text)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Enter set number, e.g. 75313") },
            )
            suggestions.take(6).forEach { set ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAdd(set) }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${set.setNumber} ${set.name}", style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
                        Text("${set.theme} · ${set.pieces} pcs", style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_bw_plus),
                        contentDescription = "Add ${set.setNumber} to wishlist",
                        tint = colors.linkAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
