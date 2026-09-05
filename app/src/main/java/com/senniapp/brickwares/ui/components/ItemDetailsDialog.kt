package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.CurrencyConverter
import com.senniapp.brickwares.util.formatIn
import com.senniapp.brickwares.util.formatMoney
import com.senniapp.brickwares.util.formatMoneyFrom

/** Which sub-view of [ItemDetailsDialog] is showing. */
enum class ItemDetailsTab { COLLECTION, SALES }

private val ACTION_COL_WIDTH = 112.dp

/**
 * Unified "See Details" for an item that may be in the user's collection, their sales, or both. When
 * both exist it shows a Collection ⇄ Sales toggle; otherwise it renders just the side that has data.
 * Replaces the separate collection-copies and single-sale dialogs so a set/fig present in both places
 * is reachable from one modal.
 *
 * @param item owned copies (null / no copies = not in the collection).
 * @param sales this set/fig's sale records (empty = nothing sold).
 * @param allowSell hide the per-copy Sell (coin) action — off for minifigs (minifig sell isn't wired).
 */
@Composable
fun ItemDetailsDialog(
    item: CollectionItem?,
    sales: List<SoldItem>,
    onDismiss: () -> Unit,
    onDeleteCopy: (String, String) -> Unit = { _, _ -> },
    onEditCopy: (Copy) -> Unit = {},
    onSellCopy: (Copy) -> Unit = {},
    allowSell: Boolean = true,
    onEditSale: (SoldItem) -> Unit = {},
    onDeleteSale: (String) -> Unit = {},
    /** Show the per-sale edit pencil. Off on the detail pages (sale edit lives on the Collection tab). */
    salesEditable: Boolean = true,
    /** Open the Add sheet in Collection mode — the add-another-copy button and the empty-tab CTA. */
    onAddCollection: () -> Unit = {},
    /** Open the Add sheet in Sales mode — the add-another-sale button and the empty-tab CTA. */
    onAddSale: () -> Unit = {},
    initialTab: ItemDetailsTab = ItemDetailsTab.COLLECTION,
) {
    val colors = BwTheme.colors
    val hasCollection = item != null && item.copies.isNotEmpty()
    val hasSales = sales.isNotEmpty()
    if (!hasCollection && !hasSales) return

    val name = item?.name ?: sales.firstOrNull()?.name.orEmpty()
    val setNumber = item?.setNumber ?: sales.firstOrNull()?.setNumber.orEmpty()

    var tab by remember(setNumber, initialTab, hasCollection, hasSales) {
        mutableStateOf(
            when {
                initialTab == ItemDetailsTab.COLLECTION && hasCollection -> ItemDetailsTab.COLLECTION
                initialTab == ItemDetailsTab.SALES && hasSales -> ItemDetailsTab.SALES
                hasCollection -> ItemDetailsTab.COLLECTION
                else -> ItemDetailsTab.SALES
            },
        )
    }

    // Cap the dialog height so a long copies list scrolls inside it (pinned header + footer) rather
    // than growing past the screen and pushing the Add button out of reach.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.card,
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = maxHeight),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = BwType.cardTitle, color = colors.text)
                        Text(setNumber, style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                    }
                    Text(
                        "✕",
                        color = colors.textMuted,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(6.dp),
                    )
                }

                // Collection ⇄ Sales toggle — always shown (like the Add sheet) so the user can jump to
                // the other section and add to it even when the item is only in one place.
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.track)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    ToggleChip(stringResource(R.string.sheet_mode_collection), tab == ItemDetailsTab.COLLECTION, Modifier.weight(1f)) { tab = ItemDetailsTab.COLLECTION }
                    ToggleChip(stringResource(R.string.sheet_mode_sales), tab == ItemDetailsTab.SALES, Modifier.weight(1f)) { tab = ItemDetailsTab.SALES }
                }

                Spacer(Modifier.height(14.dp))
                when {
                    tab == ItemDetailsTab.COLLECTION && item != null && item.copies.isNotEmpty() ->
                        CollectionBody(item, onDeleteCopy, onEditCopy, onAddCollection, onSellCopy, allowSell)
                    tab == ItemDetailsTab.COLLECTION -> EmptyDetailBody(onAdd = onAddCollection)
                    hasSales -> SalesBody(sales, onEditSale, onDeleteSale, salesEditable, onAddSale)
                    else -> EmptyDetailBody(onAdd = onAddSale)
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = BwTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.brandYellow else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = BwType.pill.copy(fontSize = 13.sp),
            color = if (selected) colors.onYellow else colors.textSecondary,
        )
    }
}

@Composable
private fun ColumnScope.CollectionBody(
    item: CollectionItem,
    onDeleteCopy: (String, String) -> Unit,
    onEditCopy: (Copy) -> Unit,
    onAddItem: () -> Unit,
    onSellCopy: (Copy) -> Unit,
    allowSell: Boolean,
) {
    val colors = BwTheme.colors
    val expanded = remember { mutableStateListOf<String>() }
    // Column header
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(stringResource(R.string.sd_cond), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.sd_date), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1.3f))
        Text(stringResource(R.string.sd_qty), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(0.5f))
        Text(stringResource(R.string.price_paid), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1.5f))
        Spacer(Modifier.width(ACTION_COL_WIDTH))
    }
    HorizontalDivider(color = colors.borderSoft)

    // Copies scroll in a weighted area so the Avg row + Add button below stay pinned and reachable
    // no matter how many copies there are.
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
    item.copies.forEachIndexed { index, copy ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(if (copy.condition == Condition.NEW) R.string.sheet_condition_new else R.string.sheet_condition_used), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(copy.dateAdded, style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1.3f))
            Text(copy.qty.toString(), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(0.5f))
            Text(formatMoneyFrom(copy.pricePaid, copy.currency, BwTheme.currency), style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.text, modifier = Modifier.weight(1.5f))
            Row(modifier = Modifier.width(ACTION_COL_WIDTH), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_bw_note),
                    contentDescription = stringResource(R.string.sd_toggle_note_cd),
                    tint = if (copy.note != null) colors.linkAccent else colors.borderStrong,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable {
                        if (copy.id in expanded) expanded.remove(copy.id) else expanded.add(copy.id)
                    },
                )
                Icon(
                    painter = painterResource(R.drawable.ic_bw_edit),
                    contentDescription = stringResource(R.string.sd_edit_copy_cd),
                    tint = colors.textMuted2,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onEditCopy(copy) },
                )
                if (allowSell) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(colors.brandYellow).clickable { onSellCopy(copy) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$", style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.onYellow)
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_bw_delete),
                    contentDescription = stringResource(R.string.sd_delete_copy_cd),
                    tint = colors.error,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onDeleteCopy(item.setNumber, copy.id) },
                )
            }
        }
        if (copy.id in expanded && copy.note != null) {
            Text(copy.note, style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (index < item.copies.lastIndex) HorizontalDivider(color = colors.borderSoft)
    }
    }

    HorizontalDivider(color = colors.borderStrong)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.sd_avg), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(1f))
        Spacer(Modifier.weight(1.3f))
        Text(item.totalQty.toString(), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(0.5f))
        Text(formatIn(item.avgPaidIn(BwTheme.currency), BwTheme.currency), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(1.5f))
        Spacer(Modifier.width(ACTION_COL_WIDTH))
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onAddItem,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.brandYellow, contentColor = colors.onYellow),
    ) {
        Text(stringResource(R.string.sheet_add_item), style = BwType.pill.copy(fontSize = 14.sp), modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun ColumnScope.SalesBody(sales: List<SoldItem>, onEditSale: (SoldItem) -> Unit, onDeleteSale: (String) -> Unit, editable: Boolean, onAdd: () -> Unit) {
    val colors = BwTheme.colors
    val expanded = remember { mutableStateListOf<String>() }
    // Column header
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(stringResource(R.string.sd_cond), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.sd_date), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1.3f))
        Text(stringResource(R.string.sd_qty), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(0.5f))
        Text(stringResource(R.string.price_paid), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1.3f))
        Text(stringResource(R.string.price_sale), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1.3f))
        Spacer(Modifier.width(66.dp))
    }
    HorizontalDivider(color = colors.borderSoft)

    // Sales scroll in a weighted area so the profit row + Add button below stay pinned and reachable.
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
    sales.forEachIndexed { index, sold ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(if (sold.condition == Condition.NEW) R.string.sheet_condition_new else R.string.sheet_condition_used), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(sold.soldOn ?: "—", style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1.3f))
            Text(sold.quantity.toString(), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(0.5f))
            Text(formatMoneyFrom(sold.pricePaid, sold.currency, BwTheme.currency), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1.3f))
            Text(formatMoneyFrom(sold.saleValue, sold.currency, BwTheme.currency), style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.text, modifier = Modifier.weight(1.3f))
            Row(modifier = Modifier.width(66.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_bw_note),
                    contentDescription = stringResource(R.string.sd_toggle_note_cd),
                    tint = if (sold.note != null) colors.linkAccent else colors.borderStrong,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable {
                        if (sold.id in expanded) expanded.remove(sold.id) else expanded.add(sold.id)
                    },
                )
                if (editable) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bw_edit),
                        contentDescription = stringResource(R.string.sd_edit_copy_cd),
                        tint = colors.textMuted2,
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onEditSale(sold) },
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_bw_delete),
                    contentDescription = stringResource(R.string.sd_delete_copy_cd),
                    tint = colors.error,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onDeleteSale(sold.id) },
                )
            }
        }
        if (sold.id in expanded && sold.note != null) {
            Text(sold.note, style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (index < sales.lastIndex) HorizontalDivider(color = colors.borderSoft)
    }
    }

    HorizontalDivider(color = colors.borderStrong)
    // Footer mirrors the data columns: total quantity sold under Qty, total profit under Sale. Profit is
    // summed IN the display currency (each sale converted from its own) so a single-currency total is
    // exact — matching the per-row figures — with no ₫→cents→₫ drift.
    val display = BwTheme.currency
    val totalQty = sales.sumOf { it.quantity }
    val totalProfit = sales.sumOf { CurrencyConverter.convert(it.profit, it.currency, display) }
    val profitColor = if (totalProfit >= 0) colors.success else colors.error
    Row(modifier = Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.sales_profit_label), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(1f))
        Spacer(Modifier.weight(1.3f)) // Date column
        Text(totalQty.toString(), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(0.5f))
        Spacer(Modifier.weight(1.3f)) // Paid column
        Text(
            (if (totalProfit > 0) "+" else "") + formatIn(totalProfit, display),
            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = profitColor,
            modifier = Modifier.weight(1.3f), // Sale column
        )
        Spacer(Modifier.width(66.dp)) // action column
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.brandYellow, contentColor = colors.onYellow),
    ) {
        Text(stringResource(R.string.sheet_add_item), style = BwType.pill.copy(fontSize = 14.sp), modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** Empty-tab state in [ItemDetailsDialog]: "nothing here yet" + a button that opens the Add sheet. */
@Composable
private fun EmptyDetailBody(onAdd: () -> Unit) {
    val colors = BwTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.item_details_empty), style = BwType.body.copy(fontSize = 13.sp), color = colors.textMuted)
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandYellow, contentColor = colors.onYellow),
        ) {
            Text(stringResource(R.string.sheet_add_item), style = BwType.pill.copy(fontSize = 14.sp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}
