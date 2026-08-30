package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatRetail

/** The filled-heart accent from the design handoff (matches the "Wishlisted" glyph). */
private val WishlistHeart = Color(0xFFC9506F)

/**
 * Shared catalog-set card (Search results + Set Detail recommendations): thumbnail, title, meta,
 * status badge, retail, and the state-aware action column — **Add to Collection** + **Wishlist**
 * when neither, the wishlist button flipping to **Wishlisted**, or a single **See Detail** once the
 * set is owned. Add/wishlist require an account (logged out → the sign-in prompt).
 */
@Composable
fun SetResultCard(
    set: CatalogSet,
    wishlisted: Boolean,
    owned: Boolean,
    onOpenDetail: () -> Unit,
    onAddCollection: () -> Unit,
    onAddWishlist: () -> Unit,
    /** Owned "See Detail" action; defaults to [onOpenDetail] (navigate). Pass to open a copies dialog. */
    onSeeDetail: (() -> Unit)? = null,
    /** When set, the "Wishlisted" button is tappable and calls this (removes from wishlist). */
    onRemoveWishlist: (() -> Unit)? = null,
) {
    val colors = BwTheme.colors
    val isLoggedIn = rememberIsLoggedIn()
    val add = { if (isLoggedIn) onAddCollection() else SignInController.request() }
    val wish = { if (isLoggedIn) onAddWishlist() else SignInController.request() }
    // Wishlisted → remove (if the caller supports it); not wishlisted → add. Null = not tappable.
    val wishClick: (() -> Unit)? = if (wishlisted) onRemoveWishlist else wish
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        SetThumb(
            imageUrl = set.boxImageUrl,
            fallbackUrl = set.thumbnailUrl ?: set.imageUrl,
            itemType = set.itemType,
            size = 72.dp,
            iconSize = 30.dp,
            modifier = Modifier.clickable(onClick = onOpenDetail),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${set.setNumber} ${set.name}", style = BwType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.linkAccent, modifier = Modifier.clickable(onClick = onOpenDetail))
            MetaLine(stringResource(R.string.meta_theme), set.theme)
            MetaLine(stringResource(R.string.meta_release), releaseLabel(set.releaseMonth, set.releaseYear))
            MetaLine(stringResource(R.string.meta_pieces_minifigs), "${set.pieces} / ${set.minifigs}")
            StatusBadge(set.status)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.width(120.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PriceLine(stringResource(R.string.price_retail), formatRetail(set.retailPrice, AppCurrency.VND))
            if (owned) {
                // Already in the collection → a single "See Detail" (opens the set detail), no add/wishlist.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
                        .clickable(onClick = onSeeDetail ?: onOpenDetail)
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
                // Wishlist / Wishlisted (tappable when there's an action for the current state).
                val wishlistModifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
                    .then(if (wishClick != null) Modifier.clickable(onClick = wishClick) else Modifier)
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
