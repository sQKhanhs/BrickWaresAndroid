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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.MetaLine
import com.senniapp.brickwares.ui.components.PriceLine
import com.senniapp.brickwares.ui.components.SetThumb
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.util.formatRetail
import com.senniapp.brickwares.util.formatRelease

/** The filled-heart accent from the design handoff (matches the "Wishlisted" glyph). */
private val WishlistHeart = Color(0xFFC9506F)

@Composable
fun SetDetailScreen(
    setNumber: String,
    onBack: () -> Unit,
    onOpenSetDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
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
        onDismissAdd = viewModel::onDismissAdd,
        onSearchCatalog = viewModel::searchCatalog,
        onAddCollectionSubmit = viewModel::onAddToCollectionSubmit,
        onToastShown = viewModel::onToastShown,
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
    onDismissAdd: () -> Unit,
    onSearchCatalog: (String) -> List<CatalogSet>,
    onAddCollectionSubmit: (CollectionItem) -> Unit,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    // When non-null, show the set image full-screen (tapped from the hero).
    var fullImageUrl by remember { mutableStateOf<String?>(null) }
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        val set = state.set
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Back header.
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
                if (state.loaded) {
                    Text("Set not found.", style = BwType.body, color = colors.textMuted)
                }
                return@Column
            }

            // Hero: image + title + actions.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val heroImage = set.imageUrl ?: set.thumbnailUrl
                SetThumb(
                    imageUrl = heroImage,
                    itemType = set.itemType,
                    size = 96.dp,
                    iconSize = 40.dp,
                    corner = 12.dp,
                    // Tap the image to view it full-screen (only when there's an image to show).
                    modifier = if (heroImage != null) {
                        Modifier.clickable { fullImageUrl = heroImage }
                    } else {
                        Modifier
                    },
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(set.name, style = BwType.cardTitle.copy(fontSize = 17.sp), color = colors.text)
                    // Add to Collection.
                    ActionButton(
                        iconRes = R.drawable.ic_bw_pieces,
                        label = "Add to Collection",
                        filled = true,
                        onClick = onAddCollectionClick,
                    )
                    // Wishlist / Wishlisted.
                    ActionButton(
                        iconRes = R.drawable.ic_bw_heart,
                        label = if (state.isWishlisted) "Wishlisted" else "Wishlist",
                        filled = false,
                        iconTint = if (state.isWishlisted) WishlistHeart else colors.textMuted,
                        onClick = if (state.isWishlisted) null else onAddWishlist,
                    )
                }
            }

            // Set details card.
            SectionCard(title = "Set Details") {
                DetailRow("Set number", set.setNumber)
                DetailRow("Name", set.name)
                DetailLinkRow("Theme", set.theme, onNavigateToSearch)
                DetailLinkRow("Subtheme", set.subtheme, onNavigateToSearch)
                DetailRow("Released", formatRelease(set.releaseMonth, set.releaseYear))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Availability", style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                    StatusBadge(set.status)
                }
                DetailRow("Pieces", set.pieces.toString())
                if (set.minifigs > 0) DetailRow("Minifigs", set.minifigs.toString())
            }

            // Pricing card.
            SectionCard(title = "Pricing") {
                DetailRow("Retail", formatRetail(set.retailPrice, AppCurrency.VND), strong = true)
                if (state.isOwned) {
                    HorizontalDivider(color = colors.borderSoft)
                    Text("My Collection", style = BwType.micro, color = colors.textMuted)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total paid", style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                        Text(
                            "${formatMoney(state.totalPaid, AppCurrency.VND)}  ×${state.ownedCount}",
                            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = colors.text,
                        )
                    }
                }
            }

            // Related.
            if (state.related.isNotEmpty()) {
                Text("More in ${set.theme}", style = BwType.cardTitle.copy(fontSize = 15.sp), color = colors.text)
                state.related.forEach { rel ->
                    RelatedCard(set = rel, onClick = { onOpenSetDetail(rel.setNumber) })
                }
            }
        }

        state.addTarget?.let { target ->
            AddToCollectionSheet(
                initialSet = target,
                initialCopy = null,
                onDismiss = onDismissAdd,
                onSearch = onSearchCatalog,
                onAdd = onAddCollectionSubmit,
            )
        }

        // Full-screen image viewer (tap anywhere / back to dismiss).
        val fullImg = fullImageUrl
        if (fullImg != null) {
            Dialog(
                onDismissRequest = { fullImageUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xF2000000))
                        .clickable { fullImageUrl = null },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = fullImg,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }

        BwToast(message = state.toastMessage, onDismiss = onToastShown)
    }
}

@Composable
private fun ActionButton(
    iconRes: Int,
    label: String,
    filled: Boolean,
    iconTint: Color? = null,
    onClick: (() -> Unit)?,
) {
    val colors = BwTheme.colors
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(999.dp))
    val styled = if (filled) {
        base.background(colors.brandYellow)
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
            tint = iconTint ?: if (filled) colors.onYellow else colors.text,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(label, style = BwType.pill.copy(fontSize = 12.sp), color = if (filled) colors.onYellow else colors.text)
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

@Composable
private fun RelatedCard(set: CatalogSet, onClick: () -> Unit) {
    val colors = BwTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        SetThumb(
            imageUrl = set.thumbnailUrl ?: set.imageUrl,
            itemType = set.itemType,
            size = 60.dp,
            iconSize = 26.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${set.setNumber} ${set.name}", style = BwType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = colors.linkAccent, maxLines = 1)
            MetaLine("Release", formatRelease(set.releaseMonth, set.releaseYear))
            PriceLine("Retail", formatRetail(set.retailPrice, AppCurrency.VND))
        }
    }
}
