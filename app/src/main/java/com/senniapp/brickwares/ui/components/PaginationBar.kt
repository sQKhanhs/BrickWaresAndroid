package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.senniapp.brickwares.R
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
    var showJump by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageCell(label = "‹", enabled = currentPage > 1) { onPageSelected(currentPage - 1) }
        pageWindow(currentPage, totalPages).forEach { token ->
            if (token == null) {
                // The ellipsis is tappable → a "go to page" dialog, so far pages are one jump away.
                PageCell(label = "…") { showJump = true }
            } else {
                PageCell(label = token.toString(), selected = token == currentPage) { onPageSelected(token) }
            }
        }
        PageCell(label = "›", enabled = currentPage < totalPages) { onPageSelected(currentPage + 1) }
    }
    if (showJump) {
        JumpToPageDialog(
            totalPages = totalPages,
            onDismiss = { showJump = false },
            onGo = { page -> showJump = false; onPageSelected(page.coerceIn(1, totalPages)) },
        )
    }
}

/** "Go to page 1–N" — a number field so far pages are reachable in one jump, not many taps. */
@Composable
private fun JumpToPageDialog(totalPages: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    val colors = BwTheme.colors
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()
    val valid = page != null && page in 1..totalPages
    val submit = { if (valid) onGo(page!!) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.pagination_go_title), style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter(Char::isDigit).take(totalPages.toString().length) },
                    singleLine = true,
                    placeholder = { Text("1–$totalPages") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.brandYellow,
                        unfocusedBorderColor = colors.borderStrong,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        cursorColor = colors.brandYellow,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JumpButton(stringResource(R.string.action_cancel), filled = false, enabled = true, onClick = onDismiss, modifier = Modifier.weight(1f))
                    JumpButton(stringResource(R.string.action_go), filled = true, enabled = valid, onClick = submit, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun JumpButton(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .then(if (filled) Modifier.background(colors.onYellow) else Modifier.border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = BwType.pill.copy(fontSize = 13.sp),
            color = if (filled) (if (enabled) colors.brandYellow else colors.textFaint) else colors.text,
        )
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
