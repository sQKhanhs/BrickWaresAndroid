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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.senniapp.brickwares.data.model.ValueFreshness
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.BackCircleButton
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.ui.components.ErrorScreen
import com.senniapp.brickwares.ui.components.rememberIsLoggedIn
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.ui.components.SearchModal
import com.senniapp.brickwares.ui.components.SeeDetailsDialog
import com.senniapp.brickwares.ui.components.SellCopyDialog
import com.senniapp.brickwares.ui.components.SetResultCard
import com.senniapp.brickwares.ui.components.SetThumb
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.components.ValueInfoBubble
import com.senniapp.brickwares.ui.components.currentValueNote
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatMoney
import kotlinx.coroutines.launch
import com.senniapp.brickwares.util.formatRetail
import com.senniapp.brickwares.ui.components.releaseLabel

/** The filled-heart accent from the design handoff (matches the "Wishlisted" glyph). */
private val WishlistHeart = Color(0xFFC9506F)

@Composable
fun SetDetailScreen(
    setNumber: String,
    onBack: () -> Unit,
    onOpenSetDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
    /** When true (the detail is viewed from the Search tab), the search FABs are shown. */
    showSearchFab: Boolean = false,
    /** Switch to minifig search: navigate back to the Search tab's minifig browse home. */
    onSwitchToMinifigSearch: () -> Unit = {},
    viewModel: SetDetailViewModel = viewModel(),
) {
    LaunchedEffect(setNumber) { viewModel.load(setNumber) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetDetailContent(
        state = state,
        onBack = onBack,
        onOpenSetDetail = onOpenSetDetail,
        onNavigateToSearch = onNavigateToSearch,
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
        onEditCopy = viewModel::onEditCopy,
        onAddCopyForSet = viewModel::onAddCopyForSet,
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
    onBack: () -> Unit,
    onOpenSetDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onAddWishlist: () -> Unit,
    onAddCollectionClick: () -> Unit,
    onRecommendAddCollection: (CatalogSet) -> Unit,
    onRecommendAddWishlist: (CatalogSet) -> Unit,
    onRecommendRemoveWishlist: (CatalogSet) -> Unit,
    onRecommendSeeDetail: (CatalogSet) -> Unit,
    onDismissAdd: () -> Unit,
    onSearchCatalog: (String) -> List<CatalogSet>,
    onAddCollectionSubmit: (CollectionItem) -> Unit,
    onAddSaleSubmit: (CollectionItem, Long) -> Unit,
    onSeeCopies: () -> Unit,
    onDismissCopies: () -> Unit,
    onDeleteCopy: (String, String) -> Unit,
    onEditCopy: (Copy) -> Unit,
    onAddCopyForSet: () -> Unit,
    onSellCopy: (Copy) -> Unit,
    onDismissSell: () -> Unit,
    onConfirmSell: (Int, Long, String) -> Unit,
    onToastShown: () -> Unit,
    onRetry: () -> Unit,
    showSearchFab: Boolean = false,
    onSwitchToMinifigSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    var showSearchModal by remember { mutableStateOf(false) }
    val isLoggedIn = rememberIsLoggedIn()
    // Tapping the hero opens a full-screen image gallery (box shot + set render, swipeable).
    var showGallery by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        // Catalog unavailable (no connection / error) → the error fallback replaces the page.
        if (state.offline) {
            ErrorScreen(message = stringResource(R.string.error_connection), onRetry = onRetry)
            return@Box
        }
        val set = state.set
        // The URL the hero actually loaded (box, or its render fallback for a boxless set) — the
        // gallery lists only images that exist, so a boxless set shows just the render (no dead page).
        var heroResolved by remember(set?.id) { mutableStateOf<String?>(null) }
        val galleryImages = when (heroResolved) {
            set?.boxImageUrl -> listOfNotNull(set?.boxImageUrl, set?.imageUrl).distinct()
            set?.imageUrl -> listOfNotNull(set?.imageUrl)
            else -> emptyList()
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Back header.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

            if (set == null) {
                // Offline is handled full-screen above; here the catalog loaded but this set isn't in it.
                if (state.loaded) {
                    Text(stringResource(R.string.detail_set_not_found), style = BwType.body, color = colors.textMuted)
                }
                return@Column
            }

            // Hero: image + title + actions. Prefer the box shot, fall back to the render.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SetThumb(
                    imageUrl = set.boxImageUrl,
                    fallbackUrl = set.imageUrl,
                    itemType = set.itemType,
                    size = 96.dp,
                    iconSize = 40.dp,
                    corner = 12.dp,
                    onResolvedUrl = { heroResolved = it },
                    // Tap the image to open the full-screen gallery (only when one has loaded).
                    modifier = if (galleryImages.isNotEmpty()) {
                        Modifier.clickable { showGallery = true }
                    } else {
                        Modifier
                    },
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(set.name, style = BwType.cardTitle.copy(fontSize = 17.sp), color = colors.text)
                    if (state.isOwned) {
                        // Already owned → a single gray "See Detail" opening the copies dialog (matches
                        // the gray See Detail pill on the item cards).
                        ActionButton(
                            iconRes = R.drawable.ic_bw_check,
                            label = stringResource(R.string.action_see_detail),
                            filled = true,
                            fillColor = colors.track,
                            contentColor = colors.text,
                            onClick = onSeeCopies,
                        )
                    } else {
                        // Add to Collection.
                        ActionButton(
                            iconRes = R.drawable.ic_bw_pieces,
                            label = stringResource(R.string.action_add_to_collection),
                            filled = true,
                            // Adding needs an account; logged out → prompt sign-in instead.
                            onClick = { if (isLoggedIn) onAddCollectionClick() else SignInController.request() },
                        )
                        // Wishlist / Wishlisted.
                        ActionButton(
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
                DetailLinkRow(stringResource(R.string.meta_theme), set.theme, onNavigateToSearch)
                DetailLinkRow(stringResource(R.string.meta_subtheme), set.subtheme, onNavigateToSearch)
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
                DetailRow(stringResource(R.string.price_retail), formatRetail(set.retailPrice, AppCurrency.VND), strong = true)
                // Brickset availability/sourcing note (only ~11% of sets have one), italic under retail.
                set.notes?.let { note ->
                    Text(
                        note,
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
                            "${formatMoney(state.totalPaid, AppCurrency.VND)}  ×${state.ownedCount}",
                            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = colors.text,
                        )
                    }
                }
            }

            // Recommended sets — full collection-style cards with Add / Wishlist actions.
            if (state.related.isNotEmpty()) {
                Text(stringResource(R.string.detail_more_in, set.theme), style = BwType.cardTitle.copy(fontSize = 15.sp), color = colors.text)
                state.related.forEach { rel ->
                    SetResultCard(
                        set = rel,
                        wishlisted = rel.setNumber in state.wishlistedNumbers,
                        owned = rel.setNumber in state.ownedNumbers,
                        onOpenDetail = { onOpenSetDetail(rel.id) },
                        onAddCollection = { onRecommendAddCollection(rel) },
                        onAddWishlist = { onRecommendAddWishlist(rel) },
                        onSeeDetail = { onRecommendSeeDetail(rel) },
                        onRemoveWishlist = { onRecommendRemoveWishlist(rel) },
                    )
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
            )
        }

        // Owned set (hero OR a recommended owned set) → the copies "See Details" dialog. When the
        // Sell dialog is open it renders on top instead (cancelling Sell returns here).
        val copiesItem = state.copiesItem
        val sellCopy = state.sellCopy
        if (copiesItem != null) {
            if (sellCopy != null) {
                SellCopyDialog(
                    item = copiesItem,
                    copy = sellCopy,
                    onDismiss = onDismissSell,
                    onConfirm = onConfirmSell,
                )
            } else {
                SeeDetailsDialog(
                    item = copiesItem,
                    onDismiss = onDismissCopies,
                    onDeleteCopy = onDeleteCopy,
                    onEditCopy = onEditCopy,
                    onAddItem = onAddCopyForSet,
                    onSellCopy = onSellCopy,
                )
            }
        }

        // Full-screen image gallery: swipe between the images that actually loaded, tap a thumbnail
        // to jump, tap the backdrop or back to dismiss.
        if (showGallery && galleryImages.isNotEmpty()) {
            ImageGalleryDialog(candidates = galleryImages, onDismiss = { showGallery = false })
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
    iconTint: Color? = null,
    /** Override the filled background (defaults to brand yellow) — e.g. the gray "See Detail". */
    fillColor: Color? = null,
    /** Override the on-fill icon/text color (defaults to onYellow). */
    contentColor: Color? = null,
    onClick: (() -> Unit)?,
) {
    val colors = BwTheme.colors
    val onFill = contentColor ?: colors.onYellow
    val base = Modifier
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
            value.amountVnd != null -> formatMoney(value.amountVnd, AppCurrency.VND)
            else -> "----"
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

/**
 * Full-screen image gallery: a swipeable pager over the [candidates] (box shot + set render) with a
 * thumbnail strip for jumping between them. Any candidate whose image 404s (e.g. a set that has a box
 * but no render, or vice-versa) is dropped, so only real photos are listed. Tap the backdrop (or
 * back) to dismiss.
 */
@Composable
private fun ImageGalleryDialog(candidates: List<String>, onDismiss: () -> Unit) {
    val colors = BwTheme.colors
    // Images that failed to load — removed from the pager and the thumbnail strip.
    val failed = remember(candidates) { mutableStateListOf<String>() }
    val images = candidates.filterNot { it in failed }
    val onImageError: (String) -> Unit = { url -> if (url !in failed) failed.add(url) }
    val pagerState = rememberPagerState(pageCount = { candidates.size - failed.size })
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xF2000000))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val url = images.getOrNull(page)
                Box(
                    modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 96.dp),
                            onState = { if (it is AsyncImagePainter.State.Error) onImageError(url) },
                        )
                    }
                }
            }
            // Close affordance.
            Text(
                "✕",
                style = BwType.cardTitle.copy(fontSize = 22.sp),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).clickable(onClick = onDismiss),
            )
            // Thumbnail strip — only meaningful with more than one image. The thumbnails render all
            // candidates, so a 404 is detected and dropped even if the user never swipes to it.
            if (images.size > 1) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    images.forEachIndexed { i, url ->
                        val selected = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(
                                    BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.brandYellow else Color(0x55FFFFFF)),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { scope.launch { pagerState.animateScrollToPage(i) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                onState = { if (it is AsyncImagePainter.State.Error) onImageError(url) },
                            )
                        }
                    }
                }
            }
        }
    }
}
