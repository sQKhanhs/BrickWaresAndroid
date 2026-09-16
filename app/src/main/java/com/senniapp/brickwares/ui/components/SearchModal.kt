package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.util.CatalogImages
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** How many quick-search results the modal shows before the user should narrow the query. */
private const val SEARCH_MODAL_MAX = 20

/** Debounce before a live catalog query fires, so fast typing doesn't hit the DB per key. */
private const val SEARCH_DEBOUNCE_MS = 180L

/**
 * Quick-search overlay: type to filter the catalog live, tap a result to act on it. Sets open their
 * detail. When [onSearchMinifigs] is supplied (the Search tab), the search is **global** — matching
 * minifigs are listed too and tapping one calls [onSelectMinifig]. Opened by the Search tab's FAB and
 * (set-only) by the Set Detail FAB, so a new search can start from anywhere without navigating back.
 */
@Composable
fun SearchModal(
    onSearch: suspend (String) -> List<CatalogSet>,
    onOpenSetDetail: (String) -> Unit,
    onDismiss: () -> Unit,
    onSearchMinifigs: (suspend (String) -> List<Minifig>)? = null,
    onSelectMinifig: ((Minifig) -> Unit)? = null,
) {
    val colors = BwTheme.colors
    var query by remember { mutableStateOf("") }
    // Live results are DB queries now (Decision 16 — the catalog isn't held in memory), run off the
    // composition on a debounce keyed to the query so a new keystroke cancels the in-flight search.
    var results by remember { mutableStateOf<List<CatalogSet>>(emptyList()) }
    var figResults by remember { mutableStateOf<List<Minifig>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            figResults = emptyList()
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            try {
                results = onSearch(query).take(SEARCH_MODAL_MAX)
                figResults = onSearchMinifigs?.invoke(query)?.take(SEARCH_MODAL_MAX) ?: emptyList()
            } catch (e: CancellationException) {
                throw e // a newer keystroke cancelled this search — let it unwind, don't blank the results
            } catch (e: Exception) {
                results = emptyList()
                figResults = emptyList()
            }
        }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.card,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_bw_search), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        Text("✕", color = colors.textMuted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(8.dp))
                    },
                )
                Spacer(Modifier.height(10.dp))
                if (query.isNotBlank() && results.isEmpty() && figResults.isEmpty()) {
                    Text(
                        stringResource(R.string.search_no_matches, query),
                        style = BwType.body.copy(fontSize = 13.sp),
                        color = colors.textMuted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(results, key = { it.id }) { set ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSetDetail(set.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Re-hosted box shot when captured, else the Rebrickable render — both reliable (never BrickLink).
                            SetThumb(imageUrl = set.thumbnailUrl ?: CatalogImages.thumbUrl(set.setNumber, set.numberVariant), fallbackUrl = set.boxImageUrl ?: set.imageUrl, itemType = set.itemType, size = 44.dp, iconSize = 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${set.setNumber} ${set.name}", style = BwType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.text, maxLines = 1)
                                Text(set.theme, style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted, maxLines = 1)
                            }
                        }
                        HorizontalDivider(color = colors.borderSoft)
                    }
                    items(figResults, key = { it.figNum }) { fig ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectMinifig?.invoke(fig) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SetThumb(imageUrl = fig.imageUrl, fallbackUrl = null, itemType = ItemType.MINIFIG, size = 44.dp, iconSize = 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(fig.name, style = BwType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.text, maxLines = 1)
                                Text("${fig.figNum} · ${stringResource(R.string.filter_minifig)}", style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted, maxLines = 1)
                            }
                        }
                        HorizontalDivider(color = colors.borderSoft)
                    }
                }
            }
        }
    }
}
