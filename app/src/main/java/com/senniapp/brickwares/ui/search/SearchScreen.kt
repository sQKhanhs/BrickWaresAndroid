package com.senniapp.brickwares.ui.search

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.Banner
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.MetaLine
import com.senniapp.brickwares.ui.components.rememberIsLoggedIn
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.ui.components.PaginationBar
import com.senniapp.brickwares.ui.components.PriceLine
import com.senniapp.brickwares.ui.components.SetThumb
import com.senniapp.brickwares.ui.components.rememberCardImageReveal
import com.senniapp.brickwares.ui.components.revealWhenReady
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.util.formatRetail
import com.senniapp.brickwares.util.formatRelease

/** The filled-heart accent from the design handoff (matches the "Wishlisted" glyph). */
private val WishlistHeart = Color(0xFFC9506F)

/** Inactive (not-favorited) theme star color from the design. */
private val StarInactive = Color(0xFFC9C9C0)

@Composable
fun SearchScreen(
    onOpenSetDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        onOpenSetDetail = onOpenSetDetail,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::onSubmit,
        onClearSearch = viewModel::onClearSearch,
        onThemeClick = viewModel::onThemeClick,
        onSubthemeClick = viewModel::onSubthemeClick,
        onThemeSortChange = viewModel::onThemeSortChange,
        onToggleFavorite = viewModel::onToggleFavorite,
        onThemeDetailBack = viewModel::onThemeDetailBack,
        onThemeDetailSubChange = viewModel::onThemeDetailSubChange,
        onThemeDetailSortChange = viewModel::onThemeDetailSortChange,
        onThemeDetailPageChange = viewModel::onThemeDetailPageChange,
        onAddToWishlist = viewModel::onAddToWishlist,
        onAddToCollectionClick = viewModel::onAddToCollectionClick,
        onDismissAdd = viewModel::onDismissAdd,
        onSearchCatalog = viewModel::searchCatalog,
        onAddToCollectionSubmit = viewModel::onAddToCollectionSubmit,
        onToastShown = viewModel::onToastShown,
        modifier = modifier,
    )
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    onOpenSetDetail: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearSearch: () -> Unit,
    onThemeClick: (String) -> Unit,
    onSubthemeClick: (String, String) -> Unit,
    onThemeSortChange: (ThemeSort) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onThemeDetailBack: () -> Unit,
    onThemeDetailSubChange: (String) -> Unit,
    onThemeDetailSortChange: (ThemeDetailSort) -> Unit,
    onThemeDetailPageChange: (Int) -> Unit,
    onAddToWishlist: (CatalogSet) -> Unit,
    onAddToCollectionClick: (CatalogSet) -> Unit,
    onDismissAdd: () -> Unit,
    onSearchCatalog: (String) -> List<CatalogSet>,
    onAddToCollectionSubmit: (CollectionItem) -> Unit,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        if (state.showThemeDetail) {
            ThemeDetailView(
                theme = state.themeDetail.orEmpty(),
                results = state.themeDetailPageItems,
                sub = state.themeDetailSub,
                subOptions = state.themeDetailSubOptions,
                sort = state.themeDetailSort,
                wishlistedNumbers = state.wishlistedNumbers,
                totalCount = state.themeDetailResults.size,
                currentPage = state.themeDetailCurrentPage,
                pageCount = state.themeDetailPageCount,
                onPageChange = onThemeDetailPageChange,
                onBack = onThemeDetailBack,
                onSubChange = onThemeDetailSubChange,
                onSortChange = onThemeDetailSortChange,
                onOpenSetDetail = onOpenSetDetail,
                onAddCollection = onAddToCollectionClick,
                onAddWishlist = onAddToWishlist,
            )
            state.addTarget?.let { target ->
                AddToCollectionSheet(
                    initialSet = target,
                    initialCopy = null,
                    onDismiss = onDismissAdd,
                    onSearch = onSearchCatalog,
                    onAdd = onAddToCollectionSubmit,
                )
            }
            BwToast(message = state.toastMessage, onDismiss = onToastShown)
            return@Box
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        ) {
            item {
                Banner(imageAsset = "file:///android_asset/search_banner.png", title = "Search Sets")
                Spacer(Modifier.height(16.dp))
            }
            item {
                SearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onSubmit = onSubmit,
                    onClear = onClearSearch,
                )
                Spacer(Modifier.height(14.dp))
            }

            when {
                state.showBrowse -> {
                    item {
                        ThemeSortSelector(selected = state.themeSort, onSelect = onThemeSortChange)
                        Spacer(Modifier.height(12.dp))
                    }
                    items(state.sortedThemes, key = { it.theme }) { group ->
                        ThemeCard(
                            group = group,
                            isFavorite = group.theme in state.favoriteThemes,
                            onClick = { onThemeClick(group.theme) },
                            onToggleFavorite = { onToggleFavorite(group.theme) },
                            onSubthemeClick = { sub -> onSubthemeClick(group.theme, sub) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                state.showSuggestions -> {
                    if (state.suggestions.isEmpty()) {
                        item { SectionLabel("No matches for \"${state.query}\"") }
                    } else {
                        item { SuggestionList(state.suggestions) { onOpenSetDetail(it.id) } }
                    }
                }

                state.tooMany -> {
                    item { TooManyResults(query = state.submittedQuery.orEmpty()) }
                }

                state.results.isEmpty() -> {
                    item { NoResults(query = state.submittedQuery.orEmpty()) }
                }

                else -> {
                    item {
                        SectionLabel("Results for \"${state.submittedQuery.orEmpty()}\" (${state.results.size})")
                        Spacer(Modifier.height(10.dp))
                    }
                    items(state.results, key = { it.id }) { set ->
                        ResultCard(
                            set = set,
                            wishlisted = set.setNumber in state.wishlistedNumbers,
                            onOpenDetail = { onOpenSetDetail(set.id) },
                            onAddCollection = { onAddToCollectionClick(set) },
                            onAddWishlist = { onAddToWishlist(set) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        state.addTarget?.let { target ->
            AddToCollectionSheet(
                initialSet = target,
                initialCopy = null,
                onDismiss = onDismissAdd,
                onSearch = onSearchCatalog,
                onAdd = onAddToCollectionSubmit,
            )
        }

        BwToast(message = state.toastMessage, onDismiss = onToastShown)
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = BwTheme.colors
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search by name or set number") },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_bw_search), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                Text(
                    "✕",
                    color = colors.textMuted,
                    modifier = Modifier.clip(CircleShape).clickable {
                        onClear()
                        focusManager.clearFocus()
                    }.padding(8.dp),
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSubmit()
            focusManager.clearFocus()
        }),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = BwTheme.colors.textMuted)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeCard(
    group: ThemeGroup,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSubthemeClick: (String) -> Unit,
) {
    val colors = BwTheme.colors
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .clickable(onClick = onClick)
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.card)
                    .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (group.logoAsset != null) {
                    AsyncImage(
                        model = group.logoAsset,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                    )
                } else {
                    Text("logo", style = BwType.micro, color = colors.textFaint)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(group.theme, style = BwType.cardTitle, color = colors.text)
                Text("(${group.setCount})", style = BwType.body.copy(fontSize = 13.sp), color = colors.textMuted)
            }
            if (group.subthemes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    group.subthemes.forEach { sub ->
                        Text(
                            "${sub.name} (${sub.count})",
                            style = BwType.body.copy(fontSize = 12.sp),
                            color = colors.linkAccent,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSubthemeClick(sub.name) }.padding(horizontal = 2.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        // Favorite star (top-right overlay).
        Icon(
            painter = painterResource(R.drawable.ic_bw_star),
            contentDescription = if (isFavorite) "Unmark favorite" else "Mark favorite",
            tint = if (isFavorite) colors.brandYellow else StarInactive,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite)
                .padding(6.dp)
                .size(22.dp),
        )
    }
}

@Composable
private fun ThemeDetailView(
    theme: String,
    results: List<CatalogSet>,
    sub: String,
    subOptions: List<SubthemeCount>,
    sort: ThemeDetailSort,
    wishlistedNumbers: Set<String>,
    totalCount: Int,
    currentPage: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onBack: () -> Unit,
    onSubChange: (String) -> Unit,
    onSortChange: (ThemeDetailSort) -> Unit,
    onOpenSetDetail: (String) -> Unit,
    onAddCollection: (CatalogSet) -> Unit,
    onAddWishlist: (CatalogSet) -> Unit,
) {
    val colors = BwTheme.colors
    val listState = rememberLazyListState()
    // Jump to the top when the page changes (the pager sits at the bottom of the list).
    LaunchedEffect(currentPage) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(BorderStroke(1.dp, colors.borderStrong), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", style = BwType.cardTitle.copy(fontSize = 20.sp), color = colors.text)
                }
                Text(theme, style = BwType.wordmark.copy(fontSize = 20.sp), color = colors.text)
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$totalCount ${if (totalCount == 1) "set" else "sets"}",
                    style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = colors.textMuted,
                )
                Spacer(Modifier.weight(1f))
                if (subOptions.size > 1) {
                    val subLabel = if (sub == ALL_SUBTHEMES) "All Subthemes" else sub
                    OptionDropdown(
                        selectedLabel = subLabel,
                        options = listOf(ALL_SUBTHEMES to "All Subthemes") + subOptions.map { it.name to "${it.name} (${it.count})" },
                        onSelect = onSubChange,
                    )
                }
                OptionDropdown(
                    selectedLabel = sort.label,
                    options = ThemeDetailSort.entries.map { it to it.label },
                    onSelect = onSortChange,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        items(results, key = { it.id }) { set ->
            ResultCard(
                set = set,
                wishlisted = set.setNumber in wishlistedNumbers,
                onOpenDetail = { onOpenSetDetail(set.id) },
                onAddCollection = { onAddCollection(set) },
                onAddWishlist = { onAddWishlist(set) },
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            PaginationBar(
                currentPage = currentPage,
                totalPages = pageCount,
                onPageSelected = onPageChange,
            )
        }
    }
}

@Composable
private fun <T> OptionDropdown(
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
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
            Text(selectedLabel, style = BwType.body.copy(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1)
            Text("▾", style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = BwType.body.copy(fontSize = 13.sp), color = colors.text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeSortSelector(selected: ThemeSort, onSelect: (ThemeSort) -> Unit) {
    val colors = BwTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sort", style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.textMuted)
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected.label, style = BwType.body.copy(fontSize = 13.sp), color = colors.textSecondary)
                Text("▾", style = BwType.body.copy(fontSize = 13.sp), color = colors.textMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ThemeSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, style = BwType.body.copy(fontSize = 13.sp), color = colors.text) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionList(suggestions: List<CatalogSet>, onClick: (CatalogSet) -> Unit) {
    val colors = BwTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp)),
    ) {
        suggestions.forEachIndexed { index, set ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(set) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(if (set.itemType == ItemType.MINIFIG) R.drawable.ic_bw_minifig else R.drawable.ic_bw_set),
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${set.setNumber} ${set.name}", style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text, maxLines = 1)
                    Text(set.theme, style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
                }
            }
            if (index < suggestions.lastIndex) HorizontalDivider(color = colors.borderSoft)
        }
    }
}

@Composable
private fun ResultCard(
    set: CatalogSet,
    wishlisted: Boolean,
    onOpenDetail: () -> Unit,
    onAddCollection: () -> Unit,
    onAddWishlist: () -> Unit,
) {
    val colors = BwTheme.colors
    // Adding/wishlisting needs an account; logged out → prompt sign-in instead.
    val isLoggedIn = rememberIsLoggedIn()
    val add = { if (isLoggedIn) onAddCollection() else SignInController.request() }
    val wish = { if (isLoggedIn) onAddWishlist() else SignInController.request() }
    val thumbUrl = set.thumbnailUrl ?: set.imageUrl
    // Reveal the card only once its image has resolved (loaded or failed).
    val reveal = rememberCardImageReveal(thumbUrl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .revealWhenReady(reveal)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        SetThumb(
            imageUrl = thumbUrl,
            itemType = set.itemType,
            size = 72.dp,
            iconSize = 30.dp,
            modifier = Modifier.clickable(onClick = onOpenDetail),
            onState = reveal.onState,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${set.setNumber} ${set.name}", style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.linkAccent, modifier = Modifier.clickable(onClick = onOpenDetail))
            MetaLine("Theme", set.theme)
            MetaLine("Release", formatRelease(set.releaseMonth, set.releaseYear))
            MetaLine("Pieces / Minifigs", "${set.pieces} / ${set.minifigs}")
            StatusBadge(set.status)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.width(120.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PriceLine("Retail", formatRetail(set.retailPrice, AppCurrency.VND))
            // Add to collection.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.brandYellow)
                    .clickable(onClick = add)
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(painter = painterResource(R.drawable.ic_bw_pieces), contentDescription = null, tint = colors.onYellow, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add", style = BwType.micro.copy(fontSize = 11.sp), color = colors.onYellow)
            }
            // Wishlist / Wishlisted.
            val wishlistModifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
                .then(if (wishlisted) Modifier else Modifier.clickable(onClick = wish))
                .padding(vertical = 7.dp)
            Row(
                modifier = wishlistModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bw_heart),
                    contentDescription = null,
                    tint = if (wishlisted) WishlistHeart else colors.textMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (wishlisted) "Wishlisted" else "Wishlist", style = BwType.micro.copy(fontSize = 11.sp), color = colors.text)
            }
        }
    }
}

@Composable
private fun TooManyResults(query: String) {
    val colors = BwTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(colors.text),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", style = BwType.body.copy(fontWeight = FontWeight.Black), color = colors.card)
            }
            Text(
                "There are too many results for \"$query\", please narrow your search.",
                style = BwType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = colors.text,
            )
        }
        Text("Search Tips", style = BwType.body.copy(fontWeight = FontWeight.Bold), color = colors.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tip("Include the set's number, name, or theme.")
            Tip("The quickest way to find a set is to enter its number.")
            Tip("Try more specific terms or fewer keywords.")
            Tip("Check the spelling and try again.")
        }
    }
}

@Composable
private fun Tip(text: String) {
    val colors = BwTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = BwType.body.copy(fontSize = 13.sp), color = colors.textMuted)
        Text(text, style = BwType.body.copy(fontSize = 13.sp), color = colors.textSecondary)
    }
}

@Composable
private fun NoResults(query: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("No sets found for \"$query\"", style = BwType.body.copy(fontSize = 13.sp), color = BwTheme.colors.textMuted)
    }
}
