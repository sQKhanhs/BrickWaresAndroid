package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatMoney

/**
 * Sold-item "See Details": mirrors the collection's [SeeDetailsDialog] but for a single sale — the
 * condition/date/qty/paid/sale row with a note toggle, edit and delete (no sell), and a Profit
 * footer. Edit reopens the shared Add sheet in Sale-edit mode; delete removes the sale.
 */
@Composable
fun SaleDetailsDialog(
    sold: SoldItem,
    onDismiss: () -> Unit,
    onEdit: (SoldItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = BwTheme.colors
    var noteExpanded by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.card,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sold.name, style = BwType.cardTitle, color = colors.text)
                        Text(sold.setNumber, style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                    }
                    Text(
                        "✕",
                        color = colors.textMuted,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(6.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))

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

                // Sale row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(if (sold.condition == Condition.NEW) R.string.sheet_condition_new else R.string.sheet_condition_used), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Text(sold.soldOn ?: "—", style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1.3f))
                    Text(sold.quantity.toString(), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(0.5f))
                    Text(formatMoney(sold.pricePaid, AppCurrency.VND), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1.3f))
                    Text(formatMoney(sold.saleValue, AppCurrency.VND), style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.text, modifier = Modifier.weight(1.3f))
                    Row(modifier = Modifier.width(66.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bw_note),
                            contentDescription = stringResource(R.string.sd_toggle_note_cd),
                            tint = if (sold.note != null) colors.linkAccent else colors.borderStrong,
                            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { noteExpanded = !noteExpanded },
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_bw_edit),
                            contentDescription = stringResource(R.string.sd_edit_copy_cd),
                            tint = colors.textMuted2,
                            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onEdit(sold) },
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_bw_delete),
                            contentDescription = stringResource(R.string.sd_delete_copy_cd),
                            tint = colors.error,
                            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onDelete(sold.id) },
                        )
                    }
                }
                if (noteExpanded && sold.note != null) {
                    Text(
                        sold.note,
                        style = BwType.body.copy(fontSize = 11.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                HorizontalDivider(color = colors.borderStrong)
                // Profit footer
                val profitColor = if (sold.profit >= 0) colors.success else colors.error
                Row(modifier = Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sales_profit_label), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(1f))
                    Text(
                        (if (sold.profit > 0) "+" else "") + formatMoney(sold.profit, AppCurrency.VND),
                        style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = profitColor,
                    )
                }
            }
        }
    }
}
