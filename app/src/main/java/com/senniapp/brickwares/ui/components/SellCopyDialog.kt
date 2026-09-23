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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatMoneyFrom
import com.senniapp.brickwares.util.moneyFieldText
import com.senniapp.brickwares.util.moneyInputToAmount
import com.senniapp.brickwares.util.sanitizeMoneyInput
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
    onConfirm: (quantity: Int, salePrice: Long, currency: AppCurrency, soldOn: String) -> Unit,
) {
    val colors = BwTheme.colors
    // Display + input currency; the sale price is parsed back to stored ₫ on confirm.
    val currency = BwTheme.currency
    val maxQty = copy.qty.coerceAtLeast(1)
    // All input is rememberSaveable so a rotation (or process death) doesn't wipe a half-filled dialog.
    var qty by rememberSaveable { mutableStateOf(maxQty.toString()) }
    // The sale row stores the TOTAL for the units sold (sellCopy prorates the cost by quantity), so the
    // sale-price prefill is retail × quantity — not one unit's retail — and it recomputes as the quantity
    // changes until the user types their own amount.
    fun retailFor(units: Int) = moneyFieldText(item.retailPrice.takeIf { it > 0L }?.let { it * units }, AppCurrency.USD, currency)
    var saleEdited by rememberSaveable { mutableStateOf(false) }
    var salePrice by rememberSaveable { mutableStateOf(retailFor(maxQty)) }
    var soldOn by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

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
                    Text(formatMoneyFrom(copy.pricePaid, copy.currency, currency), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
                }
                Spacer(Modifier.height(12.dp))

                FieldLabel(stringResource(R.string.sell_qty, maxQty))
                OutlinedTextField(
                    value = qty,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        val newQty = when {
                            digits.isBlank() -> ""
                            (digits.toIntOrNull() ?: 0) > maxQty -> maxQty.toString()
                            else -> digits
                        }
                        qty = newQty
                        // Keep the retail-based prefill in step with the quantity (until the user edits it).
                        if (!saleEdited) newQty.toIntOrNull()?.takeIf { it > 0 }?.let { salePrice = retailFor(it) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                Spacer(Modifier.height(10.dp))
                FieldLabel(stringResource(R.string.sheet_field_sale_price))
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { input -> saleEdited = true; salePrice = sanitizeMoneyInput(input, currency) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (currency == AppCurrency.USD) KeyboardType.Decimal else KeyboardType.Number,
                    ),
                    // Group ₫ digits into thousands live; USD keeps its own decimal formatting.
                    visualTransformation = if (currency == AppCurrency.VND) ThousandsSeparatorTransformation() else VisualTransformation.None,
                    prefix = if (currency == AppCurrency.USD) ({ Text(AppCurrency.USD.symbol, color = colors.textMuted) }) else null,
                    suffix = if (currency == AppCurrency.USD) null else ({ Text(AppCurrency.VND.symbol, color = colors.textMuted) }),
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
                                onConfirm(qty.toInt(), moneyInputToAmount(salePrice, currency), currency, soldOn)
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
