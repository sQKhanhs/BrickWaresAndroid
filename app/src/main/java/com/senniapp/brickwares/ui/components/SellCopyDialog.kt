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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatMoney
import java.time.LocalDate

/**
 * Sells an owned [copy] out of a [CollectionItem]: pick how many units to sell (capped to the copy's
 * quantity), the sale price, and the sold date. Confirming moves that quantity from the collection
 * into Sales (the copy shrinks, or disappears when nothing is left). Opened from the See Details
 * dialog's per-copy Sell button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellCopyDialog(
    item: CollectionItem,
    copy: Copy,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int, salePrice: Long, soldOn: String) -> Unit,
) {
    val colors = BwTheme.colors
    val maxQty = copy.qty.coerceAtLeast(1)
    var qty by remember { mutableStateOf(maxQty.toString()) }
    var salePrice by remember { mutableStateOf(item.retailPrice.takeIf { it > 0L }?.toString() ?: "") }
    var soldOn by remember { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = colors.card) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sell_title), style = BwType.cardTitle, color = colors.text)
                        Text("${item.setNumber} ${item.name}", style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                    }
                    Text(
                        "✕",
                        color = colors.textMuted,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(6.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))

                // Cost basis reference.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.price_paid), style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                    Text(formatMoney(copy.pricePaid, AppCurrency.VND), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
                }
                Spacer(Modifier.height(12.dp))

                FieldLabel(stringResource(R.string.sell_qty, maxQty))
                OutlinedTextField(
                    value = qty,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        qty = when {
                            digits.isBlank() -> ""
                            (digits.toIntOrNull() ?: 0) > maxQty -> maxQty.toString()
                            else -> digits
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                Spacer(Modifier.height(10.dp))
                FieldLabel(stringResource(R.string.sheet_field_sale_price))
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { input -> salePrice = input.filter { it.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("₫", color = colors.textMuted) },
                    placeholder = { Text("0") },
                )

                Spacer(Modifier.height(10.dp))
                FieldLabel(stringResource(R.string.sheet_field_date_sold))
                Box {
                    OutlinedTextField(
                        value = soldOn,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = true,
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showDatePicker = true },
                    )
                }
                if (showDatePicker) {
                    val initMillis = runCatching {
                        LocalDate.parse(soldOn).toEpochDay() * 86_400_000L
                    }.getOrDefault(System.currentTimeMillis())
                    val dpState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                dpState.selectedDateMillis?.let { millis ->
                                    soldOn = LocalDate.ofEpochDay(millis / 86_400_000L).toString()
                                }
                                showDatePicker = false
                            }) { Text(stringResource(R.string.action_ok)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
                        },
                    ) {
                        DatePicker(state = dpState)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.action_cancel), style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
                    }
                    val enabled = (qty.toIntOrNull() ?: 0) in 1..maxQty && salePrice.isNotBlank()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (enabled) colors.brandYellow else colors.track)
                            .clickable(enabled = enabled) {
                                onConfirm(qty.toInt(), salePrice.toLongOrNull() ?: 0L, soldOn)
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.sell_confirm),
                            style = BwType.body.copy(fontWeight = FontWeight.SemiBold),
                            color = if (enabled) colors.onYellow else colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}
