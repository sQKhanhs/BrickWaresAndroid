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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/**
 * Confirmation before a destructive list action — shared by Collection (delete a set / a sale) and
 * Wishlist (remove an item): a title, a message naming the item, then Cancel and a red confirm button.
 * Dismissing (back / outside tap) counts as Cancel.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = BwTheme.colors
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(18.dp), color = colors.card) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
                            .clickable(onClick = onCancel)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.action_cancel), style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.error)
                            .clickable(onClick = onConfirm)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(confirmLabel, style = BwType.body.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    }
                }
            }
        }
    }
}
