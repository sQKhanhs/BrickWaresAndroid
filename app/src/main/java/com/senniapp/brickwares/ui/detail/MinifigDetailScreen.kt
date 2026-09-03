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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.BackCircleButton
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.ErrorScreen
import com.senniapp.brickwares.ui.components.SeeDetailsDialog
import com.senniapp.brickwares.ui.components.SetResultCard
import com.senniapp.brickwares.ui.components.SetThumb
import com.senniapp.brickwares.ui.components.StatusBadge
import com.senniapp.brickwares.ui.components.ValuePriceLine
import com.senniapp.brickwares.ui.components.rememberIsLoggedIn
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/** The filled-heart accent (matches the "Wishlisted" glyph elsewhere). */
private val WishlistHeart = Color(0xFFC9506F)

@Composable
fun MinifigDetailScreen(
    figNum: String,
    onBack: () -> Unit,
    onOpenSetDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MinifigDetailViewModel = viewModel(),
) {
    LaunchedEffect(figNum) { viewModel.load(figNum) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = BwTheme.colors
    val isLoggedIn = rememberIsLoggedIn()

    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        if (state.offline) {
            ErrorScreen(message = stringResource(R.string.error_connection), onRetry = viewModel::retry)
            return@Box
        }
        val fig = state.fig
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
                if (fig != null) {
                    Text(
                        "${fig.figNum}  ${fig.name}",
                        style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = colors.text,
                        maxLines = 2,
                    )
                }
            }

            if (fig == null) {
                if (state.loaded) {
                    Text(stringResource(R.string.detail_set_not_found), style = BwType.body, color = colors.textMuted)
                }
                return@Column
            }

            // Hero: image + name + actions.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SetThumb(imageUrl = fig.imageUrl, fallbackUrl = null, itemType = ItemType.MINIFIG, size = 96.dp, iconSize = 40.dp, corner = 12.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fig.name, style = BwType.cardTitle.copy(fontSize = 17.sp), color = colors.text)
                    if (state.isOwned) {
                        MinifigActionButton(
                            iconRes = R.drawable.ic_bw_check,
                            label = stringResource(R.string.action_see_detail),
                            filled = true,
                            fillColor = colors.track,
                            contentColor = colors.text,
                            onClick = viewModel::onSeeCopies,
                        )
                    } else {
                        MinifigActionButton(
                            iconRes = R.drawable.ic_bw_pieces,
                            label = stringResource(R.string.action_add_to_collection),
                            filled = true,
                            onClick = { if (isLoggedIn) viewModel.onAddClick() else SignInController.request() },
                        )
                        MinifigActionButton(
                            iconRes = R.drawable.ic_bw_heart,
                            label = stringResource(if (state.isWishlisted) R.string.action_wishlisted else R.string.action_wishlist),
                            filled = false,
                            iconTint = if (state.isWishlisted) WishlistHeart else colors.textMuted,
                            onClick = when {
                                state.isWishlisted -> viewModel::onRemoveFromWishlist
                                isLoggedIn -> viewModel::onAddToWishlist
                                else -> ({ SignInController.request() })
                            },
                        )
                    }
                }
            }

            // Minifig details.
            SectionCard(title = stringResource(R.string.minifig_details)) {
                DetailRow(stringResource(R.string.minifig_number), fig.figNum)
                DetailRow(stringResource(R.string.detail_name), fig.name)
                // "In sets" count; when the fig is in only one set, mark it Exclusive next to the count.
                if (fig.setCount > 0) InSetsRow(count = fig.setCount)
                if (fig.themes.isNotEmpty()) DetailRow(stringResource(R.string.meta_theme), fig.themes.joinToString(", "))
                // Two-state availability (Retail / Retired), derived from the fig's sets. Shown only
                // once it's known (a set has resolved from the catalog).
                state.retired?.let { retired ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.detail_availability), style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                        MinifigStatusBadge(retired = retired)
                    }
                }
            }

            // Community value (Decision 17) — no retail anchor for minifigs.
            SectionCard(title = stringResource(R.string.detail_pricing)) {
                ValuePriceLine(state.currentValue, alignEnd = false)
            }

            // Appears in these sets — full collection-style cards that open the set's detail.
            if (state.appearsIn.isNotEmpty()) {
                Text(stringResource(R.string.minifig_appears_in), style = BwType.cardTitle.copy(fontSize = 15.sp), color = colors.text)
                state.appearsIn.forEach { set ->
                    SetResultCard(
                        set = set,
                        wishlisted = set.setNumber in state.wishlistedNumbers,
                        owned = set.setNumber in state.ownedNumbers,
                        onOpenDetail = { onOpenSetDetail(set.id) },
                        onAddCollection = { viewModel.onSetAddCollection(set) },
                        onAddWishlist = { viewModel.onSetAddWishlist(set) },
                        onSeeDetail = { onOpenSetDetail(set.id) },
                        onRemoveWishlist = { viewModel.onSetRemoveWishlist(set) },
                    )
                }
            }
        }

        state.addTarget?.let { target ->
            AddToCollectionSheet(
                initialSet = target,
                initialCopy = state.editingCopy,
                onDismiss = viewModel::onDismissAdd,
                onSearch = viewModel::searchCatalog,
                onAdd = viewModel::onAddSubmit,
            )
        }

        val ownedItem = state.ownedItem
        if (state.showCopies && ownedItem != null) {
            SeeDetailsDialog(
                item = ownedItem,
                onDismiss = viewModel::onDismissCopies,
                onDeleteCopy = viewModel::onDeleteCopy,
                onEditCopy = viewModel::onEditCopy,
                onAddItem = viewModel::onAddCopy,
                allowSell = false,
            )
        }

        BwToast(message = state.toastMessage?.resolve(), onDismiss = viewModel::onToastShown)
    }
}

@Composable
private fun MinifigActionButton(
    iconRes: Int,
    label: String,
    filled: Boolean,
    iconTint: Color? = null,
    fillColor: Color? = null,
    contentColor: Color? = null,
    onClick: (() -> Unit)?,
) {
    val colors = BwTheme.colors
    val onFill = contentColor ?: colors.onYellow
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
    val styled = if (filled) base.background(fillColor ?: colors.brandYellow) else base.border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
    val clickable = if (onClick != null) styled.clickable(onClick = onClick) else styled
    Row(modifier = clickable.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = iconTint ?: if (filled) onFill else colors.text, modifier = Modifier.size(15.dp))
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

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = BwTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
        Spacer(Modifier.width(10.dp))
        Text(value, style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
    }
}

/** Two-state availability pill for the minifig detail: Retail (green) or Retired (red). */
@Composable
private fun MinifigStatusBadge(retired: Boolean) {
    val colors = BwTheme.colors
    val (textRes, color) = if (retired) {
        R.string.status_retired to colors.error
    } else {
        R.string.status_retail to colors.success
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(stringResource(textRes), style = BwType.micro, color = color)
    }
}

/** The "In sets" row: the set count on the right, plus an "Exclusive" badge when it's exactly 1. */
@Composable
private fun InSetsRow(count: Int) {
    val colors = BwTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.minifig_in_sets_label), style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(count.toString(), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
            if (count == 1) StatusBadge(Availability.EXCLUSIVE)
        }
    }
}
