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
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import java.time.LocalDate

/**
 * The Add-to-Collection bottom sheet. Shared by the Collection tab (add/edit a copy) and the
 * Wishlist tab (move a wishlisted set into the collection). When [initialSet] is provided the
 * set is preselected; when [initialCopy] is provided the sheet is in edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCollectionSheet(
    initialSet: CatalogSet?,
    initialCopy: Copy?,
    onDismiss: () -> Unit,
    onSearch: (String) -> List<CatalogSet>,
    onAdd: (CollectionItem) -> Unit,
) {
    val colors = BwTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEdit = initialCopy != null

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initialSet) }
    var paid by remember { mutableStateOf(initialCopy?.pricePaid?.toString() ?: initialSet?.retailPrice?.toString() ?: "") }
    var qty by remember { mutableStateOf(initialCopy?.qty?.toString() ?: "1") }
    var condition by remember { mutableStateOf(initialCopy?.condition ?: Condition.NEW) }
    var note by remember { mutableStateOf(initialCopy?.note ?: "") }
    var dateAdded by remember { mutableStateOf(initialCopy?.dateAdded ?: LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val suggestions = if (selected == null) onSearch(query) else emptyList()

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
            Text(stringResource(if (isEdit) R.string.sheet_edit_item else R.string.action_add_to_collection), style = BwType.cardTitle.copy(fontSize = 18.sp), color = colors.text)

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
                                if (paid.isBlank()) paid = set.retailPrice?.toString() ?: ""
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

            FieldLabel(stringResource(R.string.sheet_field_paid))
            OutlinedTextField(
                value = paid,
                onValueChange = { input -> paid = input.filter { it.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("₫", color = colors.textMuted) },
                placeholder = { Text("0") },
            )

            FieldLabel(stringResource(R.string.sheet_field_qty))
            OutlinedTextField(
                value = qty,
                onValueChange = { input -> qty = input.filter { it.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            FieldLabel(stringResource(R.string.sheet_field_condition))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConditionChip(stringResource(R.string.sheet_condition_new), condition == Condition.NEW, { condition = Condition.NEW }, Modifier.weight(1f))
                ConditionChip(stringResource(R.string.sheet_condition_used), condition == Condition.USED, { condition = Condition.USED }, Modifier.weight(1f))
            }

            FieldLabel(stringResource(R.string.sheet_field_date_added))
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
            val canAdd = currentSelection != null && paid.isNotBlank()
            Button(
                onClick = {
                    val set = selected ?: return@Button
                    onAdd(
                        CollectionItem(
                            setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                            theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                            pieces = set.pieces, minifigs = set.minifigs,
                            retailPrice = set.retailPrice ?: 0L,
                            currentValue = null, growthPercent = null, status = set.status,
                            copies = listOf(
                                Copy(
                                    id = initialCopy?.id ?: "${set.setNumber}-${System.currentTimeMillis()}",
                                    condition = condition,
                                    qty = qty.toIntOrNull() ?: 1,
                                    // Blank/invalid paid → fall back to the set's retail price.
                                    pricePaid = paid.toLongOrNull() ?: (set.retailPrice ?: 0L),
                                    dateAdded = dateAdded,
                                    note = note.ifBlank { null },
                                ),
                            ),
                        ),
                    )
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
                Text(stringResource(if (isEdit) R.string.sheet_save else R.string.sheet_add_item), style = BwType.pill.copy(fontSize = 14.sp), modifier = Modifier.padding(vertical = 4.dp))
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
