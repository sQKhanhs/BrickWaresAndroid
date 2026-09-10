package com.senniapp.brickwares.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.components.AddToCollectionSheet
import com.senniapp.brickwares.ui.components.BackCircleButton
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.SetResultCard
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.ui.search.SearchViewModel
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/**
 * The full "New LEGO Sets" page reached from the Home card's "View more". Same result cards as the
 * Search theme-detail list (via [SetResultCard]) but with no filter/sort — the new sets are simply
 * divided into theme sections (A→Z). Reuses the shared [SearchViewModel] for the add / wishlist /
 * owned-state plumbing and the Add-to-Collection sheet, so this page adds no duplicate logic.
 */
@Composable
fun NewSetsScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenSetDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = BwTheme.colors
    val groups = state.newSetsByTheme
    val total = groups.sumOf { it.second.size }
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed back header — a long grouped list can still be exited from anywhere.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackCircleButton(onBack = onBack)
                Text(stringResource(R.string.new_sets_title), style = BwType.wordmark.copy(fontSize = 20.sp), color = colors.text)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.new_sets_count, total),
                        style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = colors.textMuted,
                    )
                }
                groups.forEach { (theme, sets) ->
                    item(key = "hdr-$theme") { NewSetsThemeHeader(theme = theme) }
                    items(sets, key = { it.id }) { set ->
                        SetResultCard(
                            set = set,
                            wishlisted = set.setNumber in state.wishlistedNumbers,
                            owned = set.setNumber in state.ownedNumbers || set.setNumber in state.soldNumbers,
                            onOpenDetail = { onOpenSetDetail(set.id) },
                            onAddCollection = { viewModel.onAddToCollectionClick(set) },
                            onAddWishlist = { viewModel.onAddToWishlist(set) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        // Shared Add-to-Collection sheet (same one the Search tab uses).
        state.addTarget?.let { target ->
            AddToCollectionSheet(
                initialSet = target,
                initialCopy = null,
                onDismiss = viewModel::onDismissAdd,
                onSearch = viewModel::searchCatalog,
                onAdd = viewModel::onAddToCollectionSubmit,
                allowSalesMode = true,
                onAddSale = viewModel::onAddToSalesSubmit,
            )
        }

        BwToast(message = state.toastMessage?.resolve(), onDismiss = viewModel::onToastShown)
    }
}

/** A theme section header inside the New Sets list: the theme name over a divider (like the web layout). */
@Composable
private fun NewSetsThemeHeader(theme: String) {
    val colors = BwTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(20.dp))
        Text(theme, style = BwType.cardTitle.copy(fontSize = 18.sp), color = colors.text)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.borderSoft)
        Spacer(Modifier.height(12.dp))
    }
}
