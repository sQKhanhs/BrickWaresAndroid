package com.senniapp.brickwares.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.CurrentValue
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.data.model.ValueFreshness
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.BackCircleButton
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.ui.components.ErrorScreen
import com.senniapp.brickwares.ui.components.HeroImageGallery
import com.senniapp.brickwares.ui.components.rememberIsLoggedIn
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.ui.components.SearchModal
import com.senniapp.brickwares.ui.components.ItemDetailsDialog
import com.senniapp.brickwares.ui.components.ItemDetailsTab
import com.senniapp.brickwares.ui.components.SellCopyDialog
import com.senniapp.brickwares.ui.components.SetResultCard
import com.senniapp.brickwares.ui.components.SetThumb
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.components.ValueInfoBubble
import com.senniapp.brickwares.ui.components.ValuePriceLine
import com.senniapp.brickwares.ui.components.currentValueNote
import com.senniapp.brickwares.data.repository.ValueRepositoryProvider
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.CatalogImages
import com.senniapp.brickwares.util.formatIn
import kotlinx.coroutines.launch
import com.senniapp.brickwares.ui.components.releaseLabel
import com.senniapp.brickwares.ui.components.retailLabel

/** The filled-heart accent from the design handoff (matches the "Wishlisted" glyph). */
private val WishlistHeart = Color(0xFFC9506F)

@Composable
fun SetDetailScreen(
    setNumber: String,
    onBack: () -> Unit,
    onOpenSetDetail: (String) -> Unit,
    onOpenMinifig: (String) -> Unit,
    /** Open the Search tab filtered to this set's theme (subtheme = null) or subtheme. */
    onOpenTheme: (theme: String, subtheme: String?) -> Unit,
    modifier: Modifier = Modifier,
    /** When true (the detail is viewed from the Search tab), the search FABs are shown. */
    showSearchFab: Boolean = false,
    /** Switch to minifig search: navigate back to the Search tab's minifig browse home. */
    onSwitchToMinifigSearch: () -> Unit = {},
    viewModel: SetDetailViewModel = viewModel(),
) {
    val scrollState = rememberScrollState()
    // Load the set and reset scroll to the top whenever the target changes (e.g. tapping a
    // recommendation), so the new detail doesn't open at the previous page's scroll offset.
    LaunchedEffect(setNumber) {
        viewModel.load(setNumber)
        scrollState.scrollTo(0)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetDetailContent(
        state = state,
        scrollState = scrollState,
        onBack = onBack,
        onOpenSetDetail = onOpenSetDetail,
        onOpenMinifig = onOpenMinifig,
        onOpenTheme = onOpenTheme,
        onAddWishlist = viewModel::onAddToWishlist,
        onAddCollectionClick = viewModel::onAddToCollectionClick,
        onRecommendAddCollection = viewModel::onAddRecommendToCollection,
        onRecommendAddWishlist = viewModel::onAddRecommendToWishlist,
        onRecommendRemoveWishlist = viewModel::onRemoveRecommendFromWishlist,
        onRecommendSeeDetail = viewModel::onRecommendSeeCopies,
        onDismissAdd = viewModel::onDismissAdd,
        onSearchCatalog = viewModel::searchCatalog,
        onAddCollectionSubmit = viewModel::onAddToCollectionSubmit,
        onAddSaleSubmit = viewModel::onAddToSalesSubmit,
        onSeeCopies = viewModel::onSeeCopies,
        onDismissCopies = viewModel::onDismissCopies,
        onDeleteCopy = viewModel::onDeleteCopy,
        onDeleteSale = viewModel::onDeleteSale,
        onEditCopy = viewModel::onEditCopy,
        onAddCopyForSet = viewModel::onAddCopyForSet,
        onAddSaleForSet = viewModel::onAddSaleForSet,
        onSellCopy = viewModel::onSellCopyRequest,
        onDismissSell = viewModel::onDismissSell,
        onConfirmSell = viewModel::onConfirmSell,
        onToastShown = viewModel::onToastShown,
        onRetry = viewModel::retry,
        showSearchFab = showSearchFab,
        onSwitchToMinifigSearch = onSwitchToMinifigSearch,
        modifier = modifier,
    )
}

@Composable
private fun SetDetailContent(
    state: SetDetailUiState,
    scrollState: ScrollState,
    onBack: () -> Unit,
    onOpenSetDetail: (String) -> Unit,
    onOpenMinifig: (String) -> Unit,
    onOpenTheme: (theme: String, subtheme: String?) -> Unit,
    onAddWishlist: () -> Unit,
    onAddCollectionClick: () -> Unit,
    onRecommendAddCollection: (CatalogSet) -> Unit,
    onRecommendAddWishlist: (CatalogSet) -> Unit,
    onRecommendRemoveWishlist: (CatalogSet) -> Unit,
    onRecommendSeeDetail: (CatalogSet) -> Unit,
    onDismissAdd: () -> Unit,
    onSearchCatalog: suspend (String) -> List<CatalogSet>,
    onAddCollectionSubmit: (CollectionItem) -> Unit,
    onAddSaleSubmit: (CollectionItem, Long) -> Unit,
    onSeeCopies: () -> Unit,
    onDismissCopies: () -> Unit,
    onDeleteCopy: (String, String) -> Unit,
    onDeleteSale: (String) -> Unit,
    onEditCopy: (Copy) -> Unit,
    onAddCopyForSet: () -> Unit,
    onAddSaleForSet: () -> Unit,
    onSellCopy: (Copy) -> Unit,
    onDismissSell: () -> Unit,
    onConfirmSell: (Int, Long, AppCurrency, String) -> Unit,
    onToastShown: () -> Unit,
    onRetry: () -> Unit,
    showSearchFab: Boolean = false,
    onSwitchToMinifigSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    var showSearchModal by remember { mutableStateOf(false) }
    val isLoggedIn = rememberIsLoggedIn()
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        // Catalog unavailable (no connection / error) → the error fallback replaces the page.
        if (state.offline) {
            ErrorScreen(message = stringResource(R.string.error_connection), onRetry = onRetry)
            return@Box
        }
        // Resolving the set (fetchSet in flight after opening / navigating to another detail) → a spinner,
        // never the previous set's content. Hardware/gesture Back still works via the app back stack.
        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brandYellow)
            }
            return@Box
        }
        val set = state.set
        // The hero gallery images. The render's small server-resized thumbnail leads (the full render is
        // 1-5 MB — too heavy to fetch on every open); then the box shot (Storage, or the BrickLink box as a
        // stand-in when none was captured). HeroImageGallery drops any that 404/403 and, on tap, swaps the
        // render thumbnail for the full-resolution render (renderFromThumb) so the fullscreen zoom is crisp.
        val galleryImages = listOfNotNull(
            set?.let { it.thumbnailUrl ?: CatalogImages.thumbUrl(it.setNumber, it.numberVariant, size = 640) },
            set?.boxImageUrl,
            if (set?.boxImageUrl == null) set?.let { CatalogImages.boxUrl(it.setNumber, it.numberVariant) } else null,
        ).distinct()
        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky back header — pinned above the scroll so a long minifig list can still be exited
            // from anywhere on the page (not just the top).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackCircleButton(onBack = onBack)
                if (set != null) {
                    Text(
                        "${set.setNumber} ${set.name}",
                        style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = colors.text,
                        maxLines = 2,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            if (set == null) {
                // Offline is handled full-screen above; here the catalog loaded but this set isn't in it.
                if (state.loaded) {
                    Text(stringResource(R.string.detail_set_not_found), style = BwType.body, color = colors.textMuted)
                }
                return@Column
            }

            // Hero card: centered image + title + actions (mirrors the iOS detail hero). The Rebrickable
            // render (thumb) by default, the re-hosted box shot only as a fallback — both reliable CDNs.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Swipeable image with a thumbnail strip below (render + box shot); tap opens the
                // full-screen gallery at the current image (full-res render for crisp zoom).
                HeroImageGallery(
                    candidates = galleryImages,
                    itemType = set.itemType,
                    fullResOf = { CatalogImages.renderFromThumb(it) ?: it },
                )
                Text(
                    set.name,
                    style = BwType.cardTitle.copy(fontSize = 19.sp),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
                if (state.isOwned || state.isSold) {
                    // Owned and/or sold → a single gray "See Detail" opening the merged copies/sales
                    // modal (matches the gray See Detail pill on the item cards).
                    ActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        iconRes = R.drawable.ic_bw_check,
                        label = stringResource(R.string.action_see_detail),
                        filled = true,
                        fillColor = colors.track,
                        contentColor = colors.text,
                        onClick = onSeeCopies,
                    )
                } else {
                    // Add + Wishlist, side by side (iOS-style).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            iconRes = R.drawable.ic_bw_pieces,
                            label = stringResource(R.string.action_add),
                            filled = true,
                            // Adding needs an account; logged out → prompt sign-in instead.
                            onClick = { if (isLoggedIn) onAddCollectionClick() else SignInController.request() },
                        )
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            iconRes = R.drawable.ic_bw_heart,
                            label = stringResource(if (state.isWishlisted) R.string.action_wishlisted else R.string.action_wishlist),
                            filled = false,
                            iconTint = if (state.isWishlisted) WishlistHeart else colors.textMuted,
                            onClick = when {
                                state.isWishlisted -> null
                                isLoggedIn -> onAddWishlist
                                else -> ({ SignInController.request() })
                            },
                        )
                    }
                }
            }

            // Set details card.
            SectionCard(title = stringResource(R.string.detail_set_details)) {
                DetailRow(stringResource(R.string.detail_set_number), set.setNumber)
                DetailRow(stringResource(R.string.detail_name), set.name)
                DetailLinkRow(stringResource(R.string.meta_theme), set.theme) { onOpenTheme(set.theme, null) }
                DetailLinkRow(stringResource(R.string.meta_subtheme), set.subtheme) { onOpenTheme(set.theme, set.subtheme) }
                DetailRow(stringResource(R.string.detail_released), releaseLabel(set.releaseMonth, set.releaseYear))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.detail_availability), style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                    StatusBadge(set.status)
                }
                if (set.status == Availability.RETIRED && set.retiredYear > 0) {
                    DetailRow(stringResource(R.string.detail_retired), releaseLabel(set.retiredMonth, set.retiredYear))
                }
                DetailRow(stringResource(R.string.stat_pieces), set.pieces.toString())
                if (set.minifigs > 0) DetailRow(stringResource(R.string.stat_minifigs), set.minifigs.toString())
            }

            // Pricing card.
            SectionCard(title = stringResource(R.string.detail_pricing)) {
                DetailRow(stringResource(R.string.price_retail), retailLabel(set.retailPrice, BwTheme.currency), strong = true)
                // Brickset availability/sourcing note (only ~11% of sets have one), italic under retail.
                // Show the Vietnamese translation (translate-at-ingest, sets.notes_vi) when the app
                // language is VI and a translation exists; otherwise the original English note.
                val noteLang = LocalConfiguration.current.locales[0].language
                val note = if (noteLang == "vi") (set.notesVi ?: set.notes) else set.notes
                note?.let { n ->
                    Text(
                        n,
                        style = BwType.body.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic),
                        color = colors.textMuted,
                    )
                }
                // Community current value (Decision 17) — median of users' paid prices, with the
                // contribution count / staleness explained in the "!" info bubble.
                CurrentValueRow(value = state.currentValue, loading = state.valueLoading)
                if (state.isOwned) {
                    HorizontalDivider(color = colors.borderSoft)
                    Text(stringResource(R.string.detail_my_collection), style = BwType.micro, color = colors.textMuted)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.detail_total_paid), style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                        Text(
                            "${formatIn(state.ownedItem?.totalPaidIn(BwTheme.currency) ?: 0L, BwTheme.currency)}  ×${state.ownedCount}",
                            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = colors.text,
                        )
                    }
                }
            }

            // Minifigs grid — the figs this set contains (from the set_minifigs inventory).
            if (state.minifigs.isNotEmpty()) {
                MinifigGridSection(minifigs = state.minifigs, onOpenMinifig = onOpenMinifig)
            }

            // Recommended sets — full collection-style cards with Add / Wishlist actions.
            if (state.related.isNotEmpty()) {
                Text(stringResource(R.string.detail_more_in, set.theme), style = BwType.cardTitle.copy(fontSize = 15.sp), color = colors.text)
                state.related.forEach { rel ->
                    SetResultCard(
                        set = rel,
                        wishlisted = rel.variantKey in state.wishlistedNumbers,
                        owned = rel.variantKey in state.ownedNumbers,
                        onOpenDetail = { onOpenSetDetail(rel.id) },
                        onAddCollection = { onRecommendAddCollection(rel) },
                        onAddWishlist = { onRecommendAddWishlist(rel) },
                        onSeeDetail = { onRecommendSeeDetail(rel) },
                        onRemoveWishlist = { onRecommendRemoveWishlist(rel) },
                    )
                }
            }
            }
        }

        state.addTarget?.let { target ->
            AddToCollectionSheet(
                initialSet = target,
                initialCopy = state.editingCopy,
                onDismiss = onDismissAdd,
                onSearch = onSearchCatalog,
                onAdd = onAddCollectionSubmit,
                allowSalesMode = true,
                onAddSale = onAddSaleSubmit,
                initialSalesMode = state.addSalesMode,
            )
        }

        // Owned set (hero OR a recommended owned set) → the copies "See Details" dialog. When the
        // Sell dialog is open it renders on top instead (cancelling Sell returns here).
        val copiesItem = state.copiesItem
        val sellCopy = state.sellCopy
        if (copiesItem != null || state.copiesSales.isNotEmpty()) {
            if (sellCopy != null && copiesItem != null) {
                SellCopyDialog(
                    item = copiesItem,
                    copy = sellCopy,
                    onDismiss = onDismissSell,
                    onConfirm = onConfirmSell,
                )
            } else {
                ItemDetailsDialog(
                    item = copiesItem,
                    sales = state.copiesSales,
                    onDismiss = onDismissCopies,
                    onDeleteCopy = onDeleteCopy,
                    onEditCopy = onEditCopy,
                    onSellCopy = onSellCopy,
                    onDeleteSale = onDeleteSale,
                    onAddCollection = onAddCopyForSet,
                    onAddSale = onAddSaleForSet,
                    salesEditable = false, // sale edit lives on the Collection > Sales tab
                    initialTab = if (copiesItem != null) ItemDetailsTab.COLLECTION else ItemDetailsTab.SALES,
                )
            }
        }

        // Search FABs — only when this detail is viewed from the Search tab, so the user can start a
        // new search (or switch to minifig browse) without backing out first.
        if (showSearchFab) {
            // Switch-to-minifig-search FAB (bottom-start) — mirrors the Search tab's mode toggle;
            // since the detail has no in-place browse, it routes back to the Search tab's minifig home.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 24.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(BorderStroke(1.5.dp, colors.borderStrong), CircleShape)
                    .clickable(onClick = onSwitchToMinifigSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bw_minifig),
                    contentDescription = stringResource(R.string.search_toggle_minifigs_cd),
                    tint = colors.text,
                    modifier = Modifier.size(26.dp),
                )
            }
            // Quick-search FAB (bottom-end) — opens the global search modal.
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
            )
        }

        BwToast(message = state.toastMessage?.resolve(), onDismiss = onToastShown)
    }
}

@Composable
private fun ActionButton(
    iconRes: Int,
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    /** Override the filled background (defaults to brand yellow) — e.g. the gray "See Detail". */
    fillColor: Color? = null,
    /** Override the on-fill icon/text color (defaults to onYellow). */
    contentColor: Color? = null,
    onClick: (() -> Unit)?,
) {
    val colors = BwTheme.colors
    val onFill = contentColor ?: colors.onYellow
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(999.dp))
    val styled = if (filled) {
        base.background(fillColor ?: colors.brandYellow)
    } else {
        base.border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
    }
    val clickable = if (onClick != null) styled.clickable(onClick = onClick) else styled
    Row(
        modifier = clickable.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint ?: if (filled) onFill else colors.text,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(label, style = BwType.pill.copy(fontSize = 12.sp), color = if (filled) onFill else colors.text)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val colors = BwTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = BwType.cardTitle.copy(fontSize = 14.sp), color = colors.text)
        content()
    }
}

/**
 * The "Minifigs" section: the figs this set contains, in a 2-column grid. Each card shows the fig
 * number, name, image, then either an "Exclusive" badge (fig appears in only this set) or an "In N sets"
 * count (how many catalog sets it appears in), and the community value line (with the "!" bubble;
 * `----` when there's no value) — matching the item-list cards.
 * Built manually (not a lazy grid) because the detail page is one scrolling Column.
 */
@Composable
private fun MinifigGridSection(minifigs: List<Minifig>, onOpenMinifig: (String) -> Unit) {
    val colors = BwTheme.colors
    // Overlay each fig's value from the shared warmed cache; re-read when it refreshes (revision).
    val valueRepo = ValueRepositoryProvider.instance
    val valueRev by valueRepo.revision.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.search_results_minifigs, minifigs.size),
            style = BwType.cardTitle.copy(fontSize = 15.sp),
            color = colors.text,
        )
        minifigs.chunked(2).forEach { pair ->
            // IntrinsicSize.Min + fillMaxHeight makes both cards in a row equal height, so their
            // value lines align even when one fig's name wraps to two lines and the other's doesn't.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { fig ->
                    val value = remember(fig.figNum, valueRev) { valueRepo.valueForFig(fig.figNum) }
                    MinifigGridCard(
                        fig = fig,
                        value = value,
                        onClick = { onOpenMinifig(fig.figNum) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // Keep a lone card at half width (don't stretch it across the row).
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MinifigGridCard(fig: Minifig, value: CurrentValue?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Fig number chip.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.track)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(fig.figNum, style = BwType.micro, color = colors.textSecondary, maxLines = 1)
        }
        Text(
            fig.name,
            style = BwType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SetThumb(imageUrl = fig.imageUrl, fallbackUrl = null, itemType = ItemType.MINIFIG, size = 96.dp, iconSize = 36.dp, corner = 10.dp)
        // Below the image (left-aligned, off the image so it stays legible): an "Exclusive" badge when the
        // fig appears in only this set, otherwise how many catalog sets it appears in ("In N sets").
        Row(modifier = Modifier.fillMaxWidth()) {
            if (fig.setCount <= 1) {
                StatusBadge(Availability.EXCLUSIVE)
            } else {
                // A neutral pill (same slot + shape as the Exclusive badge) so "In N sets" reads as a
                // tag that stands apart from the muted value line just below it.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.track)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        pluralStringResource(R.plurals.search_minifig_sets, fig.setCount, fig.setCount),
                        style = BwType.micro,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        // Push the value line to the bottom so it aligns across equal-height cards in a row.
        Spacer(Modifier.weight(1f))
        // Community value line (matches the item-list cards): "Value  [!]  ----" until a value exists.
        ValuePriceLine(value, alignEnd = false)
    }
}

/**
 * The community "current value" row in the Pricing card (Arch Decision 17). Shows the median paid
 * price (or `----` when there's none), a tappable ⓘ that reveals the contribution-count / staleness
 * note, and — when no value exists yet — a "Contribute" link that opens Add-to-Collection.
 */
@Composable
private fun CurrentValueRow(value: CurrentValue, loading: Boolean) {
    val colors = BwTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.current_value), style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
            Spacer(Modifier.width(6.dp))
            if (!loading) ValueInfoBubble(note = currentValueNote(value))
        }
        Spacer(Modifier.width(10.dp))
        val amountText = when {
            loading -> "…"
            else -> value.displayMinor(BwTheme.currency)?.let { formatIn(it, BwTheme.currency) } ?: "----"
        }
        Text(
            amountText,
            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = if (value.freshness == ValueFreshness.STALE) colors.textMuted else colors.text,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, strong: Boolean = false) {
    val colors = BwTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = BwType.body.copy(fontSize = 12.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold),
            color = colors.text,
        )
    }
}

@Composable
private fun DetailLinkRow(label: String, value: String, onClick: () -> Unit) {
    val colors = BwTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = colors.linkAccent2,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

// ImageGalleryDialog now lives in ui/components/ImageGalleryDialog.kt (shared with the item cards).
