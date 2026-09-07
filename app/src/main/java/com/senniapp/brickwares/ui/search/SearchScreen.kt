package com.senniapp.brickwares.ui.search

import androidx.annotation.DrawableRes
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.util.CatalogImages
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.Banner
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.ui.components.BackCircleButton
import com.senniapp.brickwares.ui.components.MetaLine
import com.senniapp.brickwares.ui.components.rememberIsLoggedIn
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.ui.components.PaginationBar
import com.senniapp.brickwares.ui.components.PriceLine
import com.senniapp.brickwares.ui.components.MinifigSetsWithValueInfo
import com.senniapp.brickwares.ui.components.StatusBadgeWithValueInfo
import com.senniapp.brickwares.ui.components.ValuePriceLine
import com.senniapp.brickwares.ui.components.SearchModal
import com.senniapp.brickwares.ui.components.ErrorScreen
import com.senniapp.brickwares.ui.components.SetThumb
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.data.repository.ValueRepositoryProvider
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.ui.components.releaseLabel
import com.senniapp.brickwares.ui.components.retailLabel

/** The filled-heart accent from the design handoff (matches the "Wishlisted" glyph). */
private val WishlistHeart = Color(0xFFC9506F)

/** Inactive (not-favorited) theme star color from the design. */
private val StarInactive = Color(0xFFC9C9C0)

@Composable
fun SearchScreen(
    onOpenSetDetail: (String) -> Unit,
    onOpenMinifig: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        onOpenSetDetail = onOpenSetDetail,
        onOpenMinifig = onOpenMinifig,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::onSubmit,
        onClearSearch = viewModel::onClearSearch,
        onThemeClick = viewModel::onThemeClick,
        onSubthemeClick = viewModel::onSubthemeClick,
        onThemeSortChange = viewModel::onThemeSortChange,
        onThemePageChange = viewModel::onThemePageChange,
        onMinifigThemePageChange = viewModel::onMinifigThemePageChange,
        onToggleFavorite = viewModel::onToggleFavorite,
        onToggleMinifigFavorite = viewModel::onToggleMinifigFavorite,
        onThemeDetailBack = viewModel::onThemeDetailBack,
        onThemeDetailSubChange = viewModel::onThemeDetailSubChange,
        onThemeDetailSortChange = viewModel::onThemeDetailSortChange,
        onThemeDetailPageChange = viewModel::onThemeDetailPageChange,
        onAddToWishlist = viewModel::onAddToWishlist,
        onAddToCollectionClick = viewModel::onAddToCollectionClick,
        onDismissAdd = viewModel::onDismissAdd,
        onSearchCatalog = viewModel::searchCatalog,
        onAddToCollectionSubmit = viewModel::onAddToCollectionSubmit,
        onAddToSalesSubmit = viewModel::onAddToSalesSubmit,
        onToastShown = viewModel::onToastShown,
        onRetry = viewModel::retry,
        onToggleMode = viewModel::onToggleMode,
        onMinifigThemeClick = viewModel::onMinifigThemeClick,
        onMinifigSubthemeClick = viewModel::onMinifigSubthemeClick,
        onMinifigThemeBack = viewModel::onMinifigThemeBack,
        onMinifigThemeDetailSubChange = viewModel::onMinifigThemeDetailSubChange,
        onMinifigThemeDetailSortChange = viewModel::onMinifigThemeDetailSortChange,
        onMinifigPageChange = viewModel::onMinifigPageChange,
        onAddMinifig = viewModel::onAddMinifigClick,
        onWishlistMinifig = viewModel::onAddMinifigToWishlist,
        onSearchMinifigsForModal = viewModel::searchMinifigs,
        modifier = modifier,
    )
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    onOpenSetDetail: (String) -> Unit,
    onOpenMinifig: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearSearch: () -> Unit,
    onThemeClick: (String) -> Unit,
    onSubthemeClick: (String, String) -> Unit,
    onThemeSortChange: (ThemeSort) -> Unit,
    onThemePageChange: (Int) -> Unit,
    onMinifigThemePageChange: (Int) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleMinifigFavorite: (String) -> Unit,
    onThemeDetailBack: () -> Unit,
    onThemeDetailSubChange: (String) -> Unit,
    onThemeDetailSortChange: (ThemeDetailSort) -> Unit,
    onThemeDetailPageChange: (Int) -> Unit,
    onAddToWishlist: (CatalogSet) -> Unit,
    onAddToCollectionClick: (CatalogSet) -> Unit,
    onDismissAdd: () -> Unit,
    onSearchCatalog: (String) -> List<CatalogSet>,
    onAddToCollectionSubmit: (CollectionItem) -> Unit,
    onAddToSalesSubmit: (CollectionItem, Long) -> Unit,
    onToastShown: () -> Unit,
    onRetry: () -> Unit,
    onToggleMode: () -> Unit,
    onMinifigThemeClick: (String) -> Unit,
    onMinifigSubthemeClick: (String, String) -> Unit,
    onMinifigThemeBack: () -> Unit,
    onMinifigThemeDetailSubChange: (String) -> Unit,
    onMinifigThemeDetailSortChange: (MinifigSort) -> Unit,
    onMinifigPageChange: (Int) -> Unit,
    onAddMinifig: (Minifig) -> Unit,
    onWishlistMinifig: (Minifig) -> Unit,
    onSearchMinifigsForModal: (String) -> List<Minifig>,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    var showSearchModal by remember { mutableStateOf(false) }
    val browseListState = rememberLazyListState()
    val browseScope = rememberCoroutineScope() // scroll the browse list to top on a theme page change
    // Scroll the browse list to the top when the tab resets to its browse home (mode toggle, a mode
    // switch from a detail page, or the Search-nav tap) — signalled by [homeScrollTick]. Guarded by a
    // saved last-seen tick so back-navigation (which doesn't bump the tick) keeps its prior scroll.
    var lastHomeTick by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(state.homeScrollTick) {
        if (state.homeScrollTick != lastHomeTick) {
            lastHomeTick = state.homeScrollTick
            browseListState.scrollToItem(0)
        }
    }
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        // Catalog unavailable (no connection / error) → the error fallback replaces the whole tab.
        if (state.loadError) {
            ErrorScreen(message = stringResource(R.string.error_connection), onRetry = onRetry)
            return@Box
        }
        if (state.showThemeDetail) {
            ThemeDetailView(
                theme = state.themeDetail.orEmpty(),
                results = state.themeDetailPageItems,
                sub = state.themeDetailSub,
                subOptions = state.themeDetailSubOptions,
                sort = state.themeDetailSort,
                wishlistedNumbers = state.wishlistedNumbers,
                ownedNumbers = state.ownedNumbers,
                soldNumbers = state.soldNumbers,
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
        } else if (state.showMinifigThemeDetail) {
            MinifigThemeDetailView(
                theme = state.minifigThemeDetail.orEmpty(),
                results = state.minifigPageItems,
                sub = state.minifigThemeDetailSub,
                subOptions = state.minifigThemeDetailSubOptions,
                sort = state.minifigThemeDetailSort,
                ownedNumbers = state.ownedNumbers,
                wishlistedNumbers = state.wishlistedNumbers,
                totalCount = state.minifigItems.size,
                currentPage = state.minifigCurrentPage,
                pageCount = state.minifigPageCount,
                onPageChange = onMinifigPageChange,
                onBack = onMinifigThemeBack,
                onSubChange = onMinifigThemeDetailSubChange,
                onSortChange = onMinifigThemeDetailSortChange,
                onOpen = onOpenMinifig,
                onAdd = onAddMinifig,
                onWishlist = onWishlistMinifig,
            )
        } else {
        LazyColumn(
            state = browseListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        ) {
            item {
                Banner(
                    imageAsset = "file:///android_asset/search_banner.png",
                    title = stringResource(if (state.isMinifigMode) R.string.search_title_minifigs else R.string.search_title),
                )
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
                // Live suggestions while typing (both browse modes) — sets first, then minifigs.
                state.showSuggestions -> {
                    if (state.suggestions.isEmpty() && state.minifigSuggestions.isEmpty()) {
                        item { SectionLabel(stringResource(R.string.search_no_matches, state.query)) }
                    } else {
                        item {
                            SuggestionList(
                                sets = state.suggestions,
                                figs = state.minifigSuggestions,
                                onSetClick = { onOpenSetDetail(it.id) },
                                onFigClick = { onOpenMinifig(it.figNum) },
                            )
                        }
                    }
                }

                // Global search results — matching sets AND minifigs, independent of the browse mode.
                state.submittedQuery != null -> {
                    val q = state.submittedQuery.orEmpty()
                    if (!state.tooMany && state.results.isEmpty() && state.minifigItems.isEmpty()) {
                        item { NoResults(query = q) }
                    }
                    if (state.tooMany) {
                        item { TooManyResults(query = q) }
                    } else if (state.results.isNotEmpty()) {
                        item {
                            SectionLabel(stringResource(R.string.search_results_sets, state.results.size))
                            Spacer(Modifier.height(10.dp))
                        }
                        items(state.results, key = { it.id }) { set ->
                            ResultCard(
                                set = set,
                                wishlisted = set.setNumber in state.wishlistedNumbers,
                                owned = set.setNumber in state.ownedNumbers || set.setNumber in state.soldNumbers,
                                onOpenDetail = { onOpenSetDetail(set.id) },
                                onAddCollection = { onAddToCollectionClick(set) },
                                onAddWishlist = { onAddToWishlist(set) },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    if (state.minifigItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            SectionLabel(stringResource(R.string.search_results_minifigs, state.minifigItems.size))
                            Spacer(Modifier.height(10.dp))
                        }
                        items(state.minifigPageItems, key = { it.figNum }) { fig ->
                            MinifigCard(
                                fig = fig,
                                owned = fig.figNum in state.ownedNumbers,
                                wishlisted = fig.figNum in state.wishlistedNumbers,
                                onOpen = { onOpenMinifig(fig.figNum) },
                                onAdd = { onAddMinifig(fig) },
                                onWishlist = { onWishlistMinifig(fig) },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        item {
                            PaginationBar(
                                currentPage = state.minifigCurrentPage,
                                totalPages = state.minifigPageCount,
                                onPageSelected = onMinifigPageChange,
                            )
                        }
                    }
                }

                // Minifig browse home (mode = Minifigs): loading, theme browse, or a theme's figs.
                state.isMinifigMode -> {
                    if (state.minifigsLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.brandYellow)
                            }
                        }
                    } else if (state.minifigThemes.isEmpty()) {
                        item { SectionLabel(stringResource(R.string.search_minifig_empty)) }
                    } else {
                        item {
                            ThemeSortSelector(selected = state.themeSort, onSelect = onThemeSortChange)
                            Spacer(Modifier.height(12.dp))
                        }
                        if (state.minifigThemePageItems.isEmpty()) {
                            item { SectionLabel(stringResource(R.string.search_no_favorite_themes)) }
                        } else {
                            items(state.minifigThemePageItems, key = { it.theme }) { group ->
                                ThemeCard(
                                    group = group,
                                    isFavorite = group.theme in state.favoriteMinifigThemes,
                                    onClick = { onMinifigThemeClick(group.theme) },
                                    onToggleFavorite = { onToggleMinifigFavorite(group.theme) },
                                    onSubthemeClick = { sub -> onMinifigSubthemeClick(group.theme, sub) },
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            item {
                                PaginationBar(
                                    currentPage = state.minifigThemeCurrentPage,
                                    totalPages = state.minifigThemePageCount,
                                    onPageSelected = { page ->
                                        onMinifigThemePageChange(page)
                                        browseScope.launch { browseListState.scrollToItem(0) }
                                    },
                                )
                            }
                        }
                    }
                }

                // Set theme browse home (mode = Sets). Paginated 10/page; favorites pinned on top.
                else -> {
                    item {
                        ThemeSortSelector(selected = state.themeSort, onSelect = onThemeSortChange)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (state.themePageItems.isEmpty()) {
                        item { SectionLabel(stringResource(R.string.search_no_favorite_themes)) }
                    } else {
                        items(state.themePageItems, key = { it.theme }) { group ->
                            ThemeCard(
                                group = group,
                                isFavorite = group.theme in state.favoriteThemes,
                                onClick = { onThemeClick(group.theme) },
                                onToggleFavorite = { onToggleFavorite(group.theme) },
                                onSubthemeClick = { sub -> onSubthemeClick(group.theme, sub) },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        item {
                            PaginationBar(
                                currentPage = state.themeCurrentPage,
                                totalPages = state.themePageCount,
                                onPageSelected = { page ->
                                    onThemePageChange(page)
                                    browseScope.launch { browseListState.scrollToItem(0) }
                                },
                            )
                        }
                    }
                }
            }
        }
        }

        // Add-to-collection sheet (shared by both modes).
        state.addTarget?.let { target ->
            AddToCollectionSheet(
                initialSet = target,
                initialCopy = null,
                onDismiss = onDismissAdd,
                onSearch = onSearchCatalog,
                onAdd = onAddToCollectionSubmit,
                allowSalesMode = true,
                onAddSale = onAddToSalesSubmit,
            )
        }

        // Mode-toggle FAB (bottom-start) — flips the tab between Sets and Minifigs (like the
        // Collection tab's swap FAB). Yellow while browsing minifigs.
        val minifigMode = state.isMinifigMode
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 24.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(if (minifigMode) colors.brandYellow else colors.card)
                .border(BorderStroke(1.5.dp, if (minifigMode) colors.brandYellow else colors.borderStrong), CircleShape)
                .clickable(onClick = onToggleMode),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bw_minifig),
                contentDescription = stringResource(R.string.search_toggle_minifigs_cd),
                tint = if (minifigMode) colors.onYellow else colors.text,
                modifier = Modifier.size(26.dp),
            )
        }

        // Quick-search FAB — opens the global search modal (sets + minifigs). Hidden while the inline
        // search field is the primary interface: the browse home (theme grid) and live-suggestions
        // (typing) views, where a floating search button is redundant. Shown on results + theme-detail
        // views, where the field may be scrolled away or absent.
        val hideSearchFab = state.submittedQuery == null && !state.showThemeDetail && state.minifigThemeDetail == null
        if (!hideSearchFab) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.brandYellow)
                    .clickable { showSearchModal = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bw_search),
                    contentDescription = stringResource(R.string.nav_search),
                    tint = colors.onYellow,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        if (showSearchModal) {
            SearchModal(
                onSearch = onSearchCatalog,
                onOpenSetDetail = { id -> showSearchModal = false; onOpenSetDetail(id) },
                onDismiss = { showSearchModal = false },
                onSearchMinifigs = onSearchMinifigsForModal,
                // Tapping a result opens its detail page (like the set rows), not the Add sheet.
                onSelectMinifig = { fig -> showSearchModal = false; onOpenMinifig(fig.figNum) },
            )
        }

        BwToast(message = state.toastMessage?.resolve(), onDismiss = onToastShown)
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
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
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
            contentDescription = stringResource(if (isFavorite) R.string.search_unmark_favorite_cd else R.string.search_mark_favorite_cd),
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
    ownedNumbers: Set<String>,
    soldNumbers: Set<String>,
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
    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky back header — pinned so a long theme result list can still be exited from anywhere.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackCircleButton(onBack = onBack)
            Text(theme, style = BwType.wordmark.copy(fontSize = 20.sp), color = colors.text)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(R.plurals.home_theme_set_count, totalCount, totalCount),
                    style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = colors.textMuted,
                )
                Spacer(Modifier.weight(1f))
                val allSubthemesLabel = stringResource(R.string.search_all_subthemes)
                if (subOptions.size > 1) {
                    val subLabel = if (sub == ALL_SUBTHEMES) allSubthemesLabel else sub
                    OptionDropdown(
                        selectedLabel = subLabel,
                        options = listOf(ALL_SUBTHEMES to allSubthemesLabel) + subOptions.map { it.name to "${it.name} (${it.count})" },
                        onSelect = onSubChange,
                    )
                }
                OptionDropdown(
                    selectedLabel = sort.text(),
                    options = ThemeDetailSort.entries.map { it to it.text() },
                    onSelect = onSortChange,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        items(results, key = { it.id }) { set ->
            ResultCard(
                set = set,
                wishlisted = set.setNumber in wishlistedNumbers,
                owned = set.setNumber in ownedNumbers || set.setNumber in soldNumbers,
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
}

/**
 * Minifig theme-detail — the minifig-mode counterpart of [ThemeDetailView]: its own full page (no
 * banner/search) with a back header, count, subtheme + sort filters, minifig cards, and pagination.
 * Sort has no newest/price (minifigs carry neither); "value" sorts use the community current value.
 */
@Composable
private fun MinifigThemeDetailView(
    theme: String,
    results: List<Minifig>,
    sub: String,
    subOptions: List<SubthemeCount>,
    sort: MinifigSort,
    ownedNumbers: Set<String>,
    wishlistedNumbers: Set<String>,
    totalCount: Int,
    currentPage: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onBack: () -> Unit,
    onSubChange: (String) -> Unit,
    onSortChange: (MinifigSort) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: (Minifig) -> Unit,
    onWishlist: (Minifig) -> Unit,
) {
    val colors = BwTheme.colors
    val listState = rememberLazyListState()
    LaunchedEffect(currentPage) { listState.scrollToItem(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky back header — pinned so a long theme result list can still be exited from anywhere.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackCircleButton(onBack = onBack)
            Text(theme, style = BwType.wordmark.copy(fontSize = 20.sp), color = colors.text)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_results_minifigs, totalCount),
                    style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = colors.textMuted,
                )
                Spacer(Modifier.weight(1f))
                val allSubthemesLabel = stringResource(R.string.search_all_subthemes)
                if (subOptions.size > 1) {
                    val subLabel = if (sub == ALL_SUBTHEMES) allSubthemesLabel else sub
                    OptionDropdown(
                        selectedLabel = subLabel,
                        options = listOf(ALL_SUBTHEMES to allSubthemesLabel) + subOptions.map { it.name to "${it.name} (${it.count})" },
                        onSelect = onSubChange,
                    )
                }
                OptionDropdown(
                    selectedLabel = sort.text(),
                    options = MinifigSort.entries.map { it to it.text() },
                    onSelect = onSortChange,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (results.isEmpty()) {
            item { SectionLabel(stringResource(R.string.search_minifig_empty)) }
        } else {
            items(results, key = { it.figNum }) { fig ->
                MinifigCard(
                    fig = fig,
                    owned = fig.figNum in ownedNumbers,
                    wishlisted = fig.figNum in wishlistedNumbers,
                    onOpen = { onOpen(fig.figNum) },
                    onAdd = { onAdd(fig) },
                    onWishlist = { onWishlist(fig) },
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
        Text(stringResource(R.string.search_sort_label), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.textMuted)
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
                Text(selected.text(), style = BwType.body.copy(fontSize = 13.sp), color = colors.textSecondary)
                Text("▾", style = BwType.body.copy(fontSize = 13.sp), color = colors.textMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ThemeSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.text(), style = BwType.body.copy(fontSize = 13.sp), color = colors.text) },
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

// ---- Minifig mode ----

@Composable
private fun MinifigCard(fig: Minifig, owned: Boolean, wishlisted: Boolean, onOpen: () -> Unit, onAdd: () -> Unit, onWishlist: () -> Unit) {
    val colors = BwTheme.colors
    val isLoggedIn = rememberIsLoggedIn()
    val add = { if (isLoggedIn) onAdd() else SignInController.request() }
    val wish = { if (isLoggedIn) onWishlist() else SignInController.request() }
    val valueRepo = ValueRepositoryProvider.instance
    val valueRev by valueRepo.revision.collectAsStateWithLifecycle()
    val currentValue = remember(fig.figNum, valueRev) { valueRepo.valueForFig(fig.figNum) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        SetThumb(imageUrl = fig.imageUrl, fallbackUrl = null, itemType = ItemType.MINIFIG, size = 64.dp, iconSize = 28.dp, galleryImages = listOfNotNull(fig.imageUrl))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(fig.figNum, style = BwType.micro, color = colors.textMuted)
            Text(fig.name, style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.linkAccent, modifier = Modifier.clickable(onClick = onOpen))
            if (fig.setCount > 0) {
                MinifigSetsWithValueInfo(fig.setCount, currentValue)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.width(118.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Bubble sits beside "in N sets" (above) when shown; a set-less minifig keeps it on this line.
            ValuePriceLine(currentValue, showBubble = fig.setCount == 0)
            if (owned) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
                        .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp)).padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_bw_check), null, tint = colors.text, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_owned), style = BwType.micro.copy(fontSize = 11.sp), color = colors.text)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(colors.brandYellow)
                        .clickable(onClick = add).padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_bw_pieces), null, tint = colors.onYellow, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_add), style = BwType.micro.copy(fontSize = 11.sp), color = colors.onYellow)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
                        .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
                        .then(if (wishlisted) Modifier else Modifier.clickable(onClick = wish)).padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_bw_heart), null, tint = if (wishlisted) WishlistHeart else colors.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (wishlisted) R.string.action_wishlisted else R.string.action_wishlist), style = BwType.micro.copy(fontSize = 11.sp), color = colors.text)
                }
            }
        }
    }
}

/**
 * Live typing dropdown: matching [sets] first, then matching [figs] (minifigs). One bordered card; a
 * divider between every row including the set→minifig seam. Tapping a set opens its detail; tapping a
 * minifig opens the minifig detail.
 */
@Composable
private fun SuggestionList(
    sets: List<CatalogSet>,
    figs: List<Minifig>,
    onSetClick: (CatalogSet) -> Unit,
    onFigClick: (Minifig) -> Unit,
) {
    val colors = BwTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp)),
    ) {
        sets.forEachIndexed { index, set ->
            SuggestionRow(
                icon = if (set.itemType == ItemType.MINIFIG) R.drawable.ic_bw_minifig else R.drawable.ic_bw_set,
                title = "${set.setNumber} ${set.name}",
                subtitle = set.theme,
                onClick = { onSetClick(set) },
            )
            // Divider after each set, and after the last set when minifigs follow.
            if (index < sets.lastIndex || figs.isNotEmpty()) HorizontalDivider(color = colors.borderSoft)
        }
        figs.forEachIndexed { index, fig ->
            SuggestionRow(
                icon = R.drawable.ic_bw_minifig,
                title = fig.name,
                subtitle = "${fig.figNum} · ${stringResource(R.string.filter_minifig)}",
                onClick = { onFigClick(fig) },
            )
            if (index < figs.lastIndex) HorizontalDivider(color = colors.borderSoft)
        }
    }
}

@Composable
private fun SuggestionRow(@DrawableRes icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = BwTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text, maxLines = 1)
            Text(subtitle, style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted, maxLines = 1)
        }
    }
}

@Composable
private fun ResultCard(
    set: CatalogSet,
    wishlisted: Boolean,
    owned: Boolean,
    onOpenDetail: () -> Unit,
    onAddCollection: () -> Unit,
    onAddWishlist: () -> Unit,
) {
    val colors = BwTheme.colors
    // Adding/wishlisting needs an account; logged out → prompt sign-in instead.
    val isLoggedIn = rememberIsLoggedIn()
    val add = { if (isLoggedIn) onAddCollection() else SignInController.request() }
    val wish = { if (isLoggedIn) onAddWishlist() else SignInController.request() }
    // Community value (Decision 17), read once so the meta column's "!" freshness bubble (beside the
    // status badge) and the price column's amount share it.
    val valueRepo = ValueRepositoryProvider.instance
    val valueRev by valueRepo.revision.collectAsStateWithLifecycle()
    val currentValue = remember(set.setId, valueRev) { valueRepo.valueFor(set.setId) }
    // The re-hosted box shot (our Storage) when captured, else the Rebrickable render — both reliable
    // CDNs, never BrickLink (which rate-limits the burst a scrolling list makes).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        SetThumb(
            imageUrl = set.boxImageUrl ?: CatalogImages.thumbUrl(set.setNumber, set.numberVariant),
            fallbackUrl = set.thumbnailUrl ?: set.imageUrl,
            itemType = set.itemType,
            size = 72.dp,
            iconSize = 30.dp,
            // Tap the image → full-screen gallery (box + render); the title still opens the detail.
            galleryImages = listOfNotNull(set.boxImageUrl, set.imageUrl),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${set.setNumber} ${set.name}", style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.linkAccent, modifier = Modifier.clickable(onClick = onOpenDetail))
            MetaLine(stringResource(R.string.meta_theme), set.theme)
            MetaLine(stringResource(R.string.meta_release), releaseLabel(set.releaseMonth, set.releaseYear))
            MetaLine(stringResource(R.string.meta_pieces_minifigs), "${set.pieces} / ${set.minifigs}")
            StatusBadgeWithValueInfo(set.status, currentValue)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.width(120.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PriceLine(stringResource(R.string.price_retail), retailLabel(set.retailPrice, BwTheme.currency))
            // Value amount (Decision 17); its "!" freshness bubble sits beside the status badge above.
            ValuePriceLine(currentValue, showBubble = false)
            if (owned) {
                // Already in the collection → a single "See Detail" (opens the set detail), no add/wishlist.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.track)
                        .clickable(onClick = onOpenDetail)
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(painter = painterResource(R.drawable.ic_bw_check), contentDescription = null, tint = colors.text, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_see_detail), style = BwType.micro.copy(fontSize = 11.sp), color = colors.text)
                }
            } else {
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
                    Text(stringResource(R.string.action_add), style = BwType.micro.copy(fontSize = 11.sp), color = colors.onYellow)
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
                    Text(stringResource(if (wishlisted) R.string.action_wishlisted else R.string.action_wishlist), style = BwType.micro.copy(fontSize = 11.sp), color = colors.text)
                }
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
                stringResource(R.string.search_too_many, query),
                style = BwType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = colors.text,
            )
        }
        Text(stringResource(R.string.search_tips_title), style = BwType.body.copy(fontWeight = FontWeight.Bold), color = colors.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tip(stringResource(R.string.search_tip_1))
            Tip(stringResource(R.string.search_tip_2))
            Tip(stringResource(R.string.search_tip_3))
            Tip(stringResource(R.string.search_tip_4))
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
        Text(stringResource(R.string.search_no_results, query), style = BwType.body.copy(fontSize = 13.sp), color = BwTheme.colors.textMuted)
    }
}

/** Localized display label for the theme-browse sort order. */
@Composable
private fun ThemeSort.text(): String = stringResource(
    when (this) {
        ThemeSort.ALPHABETICAL -> R.string.sort_alphabetical
        ThemeSort.COUNT -> R.string.sort_amount
        ThemeSort.FAVORITE -> R.string.sort_favorites
    },
)

/** Localized display label for the theme-detail sort order. */
@Composable
private fun ThemeDetailSort.text(): String = stringResource(
    when (this) {
        ThemeDetailSort.NEWEST -> R.string.sort_newest
        ThemeDetailSort.OLDEST -> R.string.sort_oldest
        ThemeDetailSort.PRICE_HIGH -> R.string.sort_price_high
        ThemeDetailSort.PRICE_LOW -> R.string.sort_price_low
        ThemeDetailSort.NAME -> R.string.sort_name
    },
)

/** Localized display label for the minifig theme-detail sort order. */
@Composable
private fun MinifigSort.text(): String = stringResource(
    when (this) {
        MinifigSort.NAME -> R.string.sort_name
        MinifigSort.VALUE_HIGH -> R.string.sort_value_high
        MinifigSort.VALUE_LOW -> R.string.sort_value_low
        MinifigSort.MOST_SETS -> R.string.sort_most_sets
    },
)

