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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.moneyFieldText
import com.senniapp.brickwares.util.moneyInputToAmount
import com.senniapp.brickwares.util.sanitizeMoneyInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Input caps for the numeric fields: 10 price digits (≈ 10B₫, above any real set) and 4 quantity
 *  digits — so a stray very long number can't be typed. */
private const val MAX_PRICE_DIGITS = 10
private const val MAX_QTY_DIGITS = 4

/** Debounce before a live-suggestion catalog query fires, so fast typing doesn't hit the DB per key. */
private const val SEARCH_DEBOUNCE_MS = 180L

/**
 * The Add-to-Collection bottom sheet. Shared by the Collection tab (add/edit a copy), the Wishlist
 * tab (move a wishlisted set into the collection), Search and Set Detail. When [initialSet] is
 * provided the set is preselected; when [initialCopy] is provided the sheet is in edit mode.
 *
 * When [allowSalesMode] is true (and not editing), a Collection/Sales toggle lets the user record
 * the item as a **sale** instead — an extra Sale price field appears and submit routes to [onAddSale]
 * (a standalone Sales entry; it does not add to the collection). [initialSalesMode] opens the sheet
 * already on the Sales side (used by the Sales-mode Add FAB). When [initialSalePrice] is provided
 * alongside [initialCopy] the sheet edits an existing sale (Sales fields locked on, submit → [onEditSale]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCollectionSheet(
    initialSet: CatalogSet?,
    initialCopy: Copy?,
    onDismiss: () -> Unit,
    onSearch: suspend (String) -> List<CatalogSet>,
    onAdd: (CollectionItem) -> Unit,
    allowSalesMode: Boolean = false,
    onAddSale: (CollectionItem, Long) -> Unit = { _, _ -> },
    initialSalesMode: Boolean = false,
    initialSalePrice: Long? = null,
    onEditSale: (CollectionItem, Long) -> Unit = { _, _ -> },
) {
    val colors = BwTheme.colors
    // The field always uses the current DISPLAY currency — a fresh add records it, and an edit re-bases
    // the entry to it (the prefill converts from the entry's stored currency). So editing a USD entry
    // while viewing ₫ shows ₫, and saving re-records the entry as ₫. Values store in this currency+tag.
    val currency = BwTheme.currency
    // The currency an edited entry was stored in — the prefill converts from it into the field currency.
    val editFrom = initialCopy?.currency ?: AppCurrency.USD
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEdit = initialCopy != null
    val isSaleEdit = isEdit && initialSalePrice != null

    var salesMode by remember { mutableStateOf(isSaleEdit || (initialSalesMode && !isEdit)) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initialSet) }
    // Prefills convert into the field (display) currency: an edited copy/sale from its stored currency,
    // a retail default from USD cents.
    var paid by remember {
        mutableStateOf(
            if (initialCopy != null) moneyFieldText(initialCopy.pricePaid, editFrom, currency)
            else moneyFieldText(initialSet?.retailPrice, AppCurrency.USD, currency),
        )
    }
    var salePrice by remember {
        mutableStateOf(
            if (initialSalePrice != null) moneyFieldText(initialSalePrice, editFrom, currency)
            else moneyFieldText(initialSet?.retailPrice, AppCurrency.USD, currency),
        )
    }
    var qty by remember { mutableStateOf(initialCopy?.qty?.toString() ?: "1") }
    var condition by remember { mutableStateOf(initialCopy?.condition ?: Condition.NEW) }
    var note by remember { mutableStateOf(initialCopy?.note ?: "") }
    var dateAdded by remember { mutableStateOf(initialCopy?.dateAdded ?: LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Live suggestions are a DB query now (Decision 16 — the catalog isn't held in memory), so they run
    // off the composition on a debounce keyed to the query; a new keystroke cancels the in-flight search.
    var suggestions by remember { mutableStateOf<List<CatalogSet>>(emptyList()) }
    LaunchedEffect(query, selected) {
        if (selected != null || query.isBlank()) {
            suggestions = emptyList()
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            suggestions = try {
                onSearch(query)
            } catch (e: CancellationException) {
                throw e // a newer keystroke cancelled this search — let it unwind, don't blank the list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val title = when {
                isSaleEdit -> R.string.sheet_edit_sale
                isEdit -> R.string.sheet_edit_item
                salesMode -> R.string.action_add_to_sales
                else -> R.string.action_add_to_collection
            }
            Text(stringResource(title), style = BwType.cardTitle.copy(fontSize = 18.sp), color = colors.text)

            // Collection / Sales destination toggle (fresh adds only — not when editing a copy).
            if (allowSalesMode && !isEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConditionChip(stringResource(R.string.sheet_mode_collection), !salesMode, { salesMode = false }, Modifier.weight(1f))
                    ConditionChip(stringResource(R.string.sheet_mode_sales), salesMode, { salesMode = true }, Modifier.weight(1f))
                }
            }

            val currentSelection = selected
            if (currentSelection == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.sheet_enter_set_number)) },
                )
                suggestions.take(6).forEach { set ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selected = set
                                query = ""
                                if (paid.isBlank()) paid = moneyFieldText(set.retailPrice, AppCurrency.USD, currency)
                                if (salePrice.isBlank()) salePrice = moneyFieldText(set.retailPrice, AppCurrency.USD, currency)
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                    ) {
                        Column {
                            Text("${set.setNumber} ${set.name}", style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
                            Text(stringResource(R.string.sheet_theme_pcs, set.theme, set.pieces), style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${currentSelection.setNumber} ${currentSelection.name}", style = BwType.body.copy(fontWeight = FontWeight.Bold), color = colors.text)
                        Text(currentSelection.theme, style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted)
                    }
                    Text(
                        "✕",
                        color = colors.textMuted,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { selected = null }
                            .padding(8.dp),
                    )
                }
            }

            // Currency-aware money field decoration: ₫ suffix for VND, "$" prefix for USD (which also
            // allows a decimal point). Shared by the Paid and Sale-price fields.
            val moneyPrefix: (@Composable () -> Unit)? =
                if (currency == AppCurrency.USD) ({ Text(AppCurrency.USD.symbol, color = colors.textMuted) }) else null
            val moneySuffix: (@Composable () -> Unit)? =
                if (currency == AppCurrency.USD) null else ({ Text(AppCurrency.VND.symbol, color = colors.textMuted) })
            val moneyKeyboard = KeyboardOptions(
                keyboardType = if (currency == AppCurrency.USD) KeyboardType.Decimal else KeyboardType.Number,
            )
            // Group ₫ digits into thousands live ("1000000" → "1.000.000"); USD keeps its own decimals.
            val moneyTransformation: VisualTransformation =
                if (currency == AppCurrency.VND) ThousandsSeparatorTransformation() else VisualTransformation.None

            FieldLabel(stringResource(R.string.sheet_field_paid))
            OutlinedTextField(
                value = paid,
                // Filtered + capped for the currency (VND digits only; USD digits + 2 decimals).
                onValueChange = { input -> paid = sanitizeMoneyInput(input, currency, MAX_PRICE_DIGITS) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = moneyKeyboard,
                visualTransformation = moneyTransformation,
                prefix = moneyPrefix,
                suffix = moneySuffix,
                placeholder = { Text("0") },
            )

            if (salesMode) {
                FieldLabel(stringResource(R.string.sheet_field_sale_price))
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { input -> salePrice = sanitizeMoneyInput(input, currency, MAX_PRICE_DIGITS) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = moneyKeyboard,
                    visualTransformation = moneyTransformation,
                    prefix = moneyPrefix,
                    suffix = moneySuffix,
                    placeholder = { Text("0") },
                )
            }

            FieldLabel(stringResource(R.string.sheet_field_qty))
            OutlinedTextField(
                value = qty,
                onValueChange = { input -> qty = input.filter { it.isDigit() }.take(MAX_QTY_DIGITS) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            FieldLabel(stringResource(R.string.sheet_field_condition))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConditionChip(stringResource(R.string.sheet_condition_new), condition == Condition.NEW, { condition = Condition.NEW }, Modifier.weight(1f))
                ConditionChip(stringResource(R.string.sheet_condition_used), condition == Condition.USED, { condition = Condition.USED }, Modifier.weight(1f))
            }

            FieldLabel(stringResource(if (salesMode) R.string.sheet_field_date_sold else R.string.sheet_field_date_added))
            Box {
                OutlinedTextField(
                    value = dateAdded,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = true,
                )
                // Transparent overlay so tapping the read-only field opens the date picker.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showDatePicker = true },
                )
            }
            if (showDatePicker) {
                val initMillis = runCatching {
                    LocalDate.parse(dateAdded).toEpochDay() * 86_400_000L
                }.getOrDefault(System.currentTimeMillis())
                val dpState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            dpState.selectedDateMillis?.let { millis ->
                                dateAdded = LocalDate.ofEpochDay(millis / 86_400_000L).toString()
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

            FieldLabel(stringResource(R.string.sheet_field_note))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().height(90.dp),
                placeholder = { Text(stringResource(R.string.sheet_note_optional)) },
            )

            Spacer(Modifier.height(4.dp))
            val canAdd = currentSelection != null && paid.isNotBlank() && (!salesMode || salePrice.isNotBlank())
            Button(
                onClick = {
                    val set = selected ?: return@Button
                    val item = CollectionItem(
                        setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                        theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                        pieces = set.pieces, minifigs = set.minifigs,
                        setId = set.setId, // the exact selected variant (CMF/SDCC share a number)
                        retailPrice = set.retailPrice ?: 0L,
                        currentValue = null, growthPercent = null, status = set.status,
                        imageUrl = set.imageUrl,
                        copies = listOf(
                            Copy(
                                id = initialCopy?.id ?: "${set.setNumber}-${System.currentTimeMillis()}",
                                condition = condition,
                                qty = qty.toIntOrNull() ?: 1,
                                // Parse the field into the field currency's own unit; store with its tag.
                                pricePaid = moneyInputToAmount(paid, currency),
                                currency = currency,
                                dateAdded = dateAdded,
                                note = note.ifBlank { null },
                            ),
                        ),
                    )
                    val salePriceLong = moneyInputToAmount(salePrice, currency)
                    when {
                        isSaleEdit -> onEditSale(item, salePriceLong)
                        salesMode -> onAddSale(item, salePriceLong)
                        else -> onAdd(item)
                    }
                },
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandYellow,
                    contentColor = colors.onYellow,
                    disabledContainerColor = colors.track,
                    disabledContentColor = colors.textMuted,
                ),
            ) {
                val buttonLabel = when {
                    isEdit -> R.string.sheet_save
                    salesMode -> R.string.sheet_add_sale
                    else -> R.string.sheet_add_item
                }
                Text(stringResource(buttonLabel), style = BwType.pill.copy(fontSize = 14.sp), modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        color = BwTheme.colors.textMuted,
    )
}

@Composable
fun ConditionChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    val bg = if (selected) colors.brandYellow else colors.card
    val fg = if (selected) colors.onYellow else colors.text
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = fg)
    }
}
