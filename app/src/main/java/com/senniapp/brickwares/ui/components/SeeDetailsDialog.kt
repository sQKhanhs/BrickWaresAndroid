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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatMoney

/**
 * Owned-item "See Details": the copies table for a [CollectionItem] with per-copy note toggle, edit
 * and delete, an average row, and an add-another-copy button. Shared by the Collection tab and the
 * Set Detail page (for a set the user already owns).
 */
@Composable
fun SeeDetailsDialog(
    item: CollectionItem,
    onDismiss: () -> Unit,
    onDeleteCopy: (String, String) -> Unit,
    onEditCopy: (Copy) -> Unit,
    onAddItem: () -> Unit,
) {
    val colors = BwTheme.colors
    val expanded = remember { mutableStateListOf<String>() }
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
                        Text(item.name, style = BwType.cardTitle, color = colors.text)
                        Text(item.setNumber, style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
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
                    Text(stringResource(R.string.price_paid), style = BwType.micro, color = colors.textMuted, modifier = Modifier.weight(1.5f))
                    Spacer(Modifier.width(84.dp))
                }
                HorizontalDivider(color = colors.borderSoft)

                item.copies.forEachIndexed { index, copy ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(if (copy.condition == Condition.NEW) R.string.sheet_condition_new else R.string.sheet_condition_used), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1f))
                        Text(copy.dateAdded, style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(1.3f))
                        Text(copy.qty.toString(), style = BwType.body.copy(fontSize = 11.sp), color = colors.textSecondary, modifier = Modifier.weight(0.5f))
                        Text(formatMoney(copy.pricePaid, AppCurrency.VND), style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.text, modifier = Modifier.weight(1.5f))
                        Row(modifier = Modifier.width(84.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bw_note),
                                contentDescription = stringResource(R.string.sd_toggle_note_cd),
                                tint = if (copy.note != null) colors.linkAccent else colors.borderStrong,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (copy.id in expanded) expanded.remove(copy.id) else expanded.add(copy.id)
                                    },
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_bw_edit),
                                contentDescription = stringResource(R.string.sd_edit_copy_cd),
                                tint = colors.textMuted2,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable { onEditCopy(copy) },
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_bw_delete),
                                contentDescription = stringResource(R.string.sd_delete_copy_cd),
                                tint = colors.error,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable { onDeleteCopy(item.setNumber, copy.id) },
                            )
                        }
                    }
                    if (copy.id in expanded && copy.note != null) {
                        Text(
                            copy.note,
                            style = BwType.body.copy(fontSize = 11.sp),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (index < item.copies.lastIndex) HorizontalDivider(color = colors.borderSoft)
                }

                HorizontalDivider(color = colors.borderStrong)
                // Avg row
                Row(modifier = Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sd_avg), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(1f))
                    Spacer(Modifier.weight(1.3f))
                    Text(item.totalQty.toString(), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(0.5f))
                    Text(formatMoney(item.avgPaid, AppCurrency.VND), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(1.5f))
                    Spacer(Modifier.width(84.dp))
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
        }
    }
}
