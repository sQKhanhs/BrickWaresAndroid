package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/** Items shown per page in the numbered-pagination lists (theme results, wishlist, collection). */
const val PAGE_SIZE = 10

/**
 * A numbered pager: ‹ [1] [2] … [n] › — tappable page numbers so users can jump straight to a
 * remembered page. Renders nothing when there's a single page. Page numbers window around the
 * current page with ellipses when there are many.
 */
@Composable
fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalPages <= 1) return
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageCell(label = "‹", enabled = currentPage > 1) { onPageSelected(currentPage - 1) }
        pageWindow(currentPage, totalPages).forEach { token ->
            if (token == null) {
                Text("…", style = BwType.body, color = BwTheme.colors.textMuted)
            } else {
                PageCell(label = token.toString(), selected = token == currentPage) { onPageSelected(token) }
            }
        }
        PageCell(label = "›", enabled = currentPage < totalPages) { onPageSelected(currentPage + 1) }
    }
}

@Composable
private fun PageCell(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = BwTheme.colors
    val bg = if (selected) colors.brandYellow else colors.card
    val fg = when {
        selected -> colors.onYellow
        !enabled -> colors.textFaint
        else -> colors.text
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(BorderStroke(1.dp, colors.borderSoft), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = BwType.body, color = fg, textAlign = TextAlign.Center)
    }
}

/** Page tokens to render: page numbers, with null marking an ellipsis gap. */
private fun pageWindow(current: Int, total: Int): List<Int?> {
    if (total <= 7) return (1..total).toList()
    val pages = linkedSetOf(1)
    for (p in (current - 1)..(current + 1)) if (p in 2 until total) pages.add(p)
    pages.add(total)
    val result = mutableListOf<Int?>()
    var prev = 0
    for (p in pages.sorted()) {
        if (prev != 0 && p - prev > 1) result.add(null)
        result.add(p)
        prev = p
    }
    return result
}
