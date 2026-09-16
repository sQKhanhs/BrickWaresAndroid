package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/**
 * The "Enjoying BrickWares?" rating prompt, raised by [com.senniapp.brickwares.util.RatePrompt] once
 * the user is clearly engaged. Three answers: **Rate on Google Play** (opens the listing, never asks
 * again), **Not now** (snoozed — also what Back / tapping outside means), and a quiet **Don't ask
 * again** link. Same card style as the Settings dialogs, with the brand star as the visual anchor.
 */
@Composable
fun RateAppDialog(onRate: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    val colors = BwTheme.colors
    Dialog(onDismissRequest = onLater, properties = DialogProperties(dismissOnClickOutside = true)) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.brandYellow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bw_star),
                        contentDescription = null,
                        tint = colors.onYellow,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.rate_prompt_title), style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.rate_prompt_body),
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(20.dp))
                // Stacked full-width buttons: the primary label is too long to share a row without wrapping.
                RatePill(stringResource(R.string.action_rate_now), onClick = onRate, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                RateOutlinePill(stringResource(R.string.action_not_now), onClick = onLater, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.rate_prompt_never),
                    style = BwType.body.copy(fontSize = 12.sp),
                    color = colors.textFaint,
                    modifier = Modifier.clickable(onClick = onNever).padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun RatePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.onYellow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BwType.pill.copy(fontSize = 12.sp), color = colors.brandYellow)
    }
}

@Composable
private fun RateOutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BwType.body.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = colors.text)
    }
}
