package com.senniapp.brickwares.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.ui.theme.BrickWaresTheme
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatCount
import com.senniapp.brickwares.util.formatMoney
import kotlin.math.roundToInt

/** Stateful entry point — binds the [HomeViewModel] to the stateless [HomeContent]. */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onShareClick = viewModel::onShareClick,
        onSignInPrompt = viewModel::onSignInPrompt,
        onDismissSignInDialog = viewModel::onDismissSignInDialog,
        onSignIn = viewModel::onSignIn,
        modifier = modifier,
    )
}

/** Stateless Home UI — renders purely from [HomeUiState] so it's preview- and test-friendly. */
@Composable
private fun HomeContent(
    state: HomeUiState,
    onShareClick: () -> Unit,
    onSignInPrompt: () -> Unit,
    onDismissSignInDialog: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Header(canShare = state.canShare, onShareClick = onShareClick)
            Spacer(Modifier.height(14.dp))
            state.summary?.let { summary ->
                HeroCard(summary = summary, currency = state.currency)
                Spacer(Modifier.height(14.dp))
                StatRow(summary = summary)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (!state.isLoggedIn) {
            SignInFab(
                onClick = onSignInPrompt,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp),
            )
        }

        if (state.showSignInDialog) {
            SignInDialog(onDismiss = onDismissSignInDialog, onSignIn = onSignIn)
        }
    }
}

@Composable
private fun Header(canShare: Boolean, onShareClick: () -> Unit) {
    val colors = BwTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                append("Brick")
                withStyle(SpanStyle(color = colors.brandYellow)) { append("Wares") }
            },
            style = BwType.wordmark,
            color = colors.text,
        )
        if (canShare) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .clickable(onClick = onShareClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bw_share),
                    contentDescription = "Share collection",
                    tint = colors.text,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(summary: CollectionSummary, currency: AppCurrency) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            // Base near-black + top-weighted dark gradient (per spec). When a real collection
            // photo/gif is wired later it sits behind this gradient for text legibility.
            .background(Color(0xFF1A1A1A))
            .background(
                Brush.verticalGradient(
                    0f to Color(0xD11A1A1A),
                    0.42f to Color(0x261A1A1A),
                    1f to Color(0x1A1A1A1A),
                ),
            )
            .padding(28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Collection Value",
                style = BwType.heroLabel,
                color = BwTheme.colors.brandYellow,
            )
            Text(
                text = formatMoney(summary.collectionValue, currency),
                style = BwType.heroValue.copy(
                    shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 10f),
                ),
                color = Color.White,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Paid pill
                Text(
                    text = "Paid ${formatMoney(summary.paid, currency)}",
                    style = BwType.pill.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
                // Growth pill (▲/▼ glyph + coloured text, per spec)
                val pct = summary.growthPercent.roundToInt()
                val (label, growthColor) = when {
                    pct > 0 -> "▲ +$pct% Growth" to Color(0xFF4ADE80)
                    pct < 0 -> "▼ $pct% Growth" to Color(0xFFF87171)
                    else -> "0% Growth" to Color.White
                }
                Text(
                    text = label,
                    style = BwType.pill.copy(fontSize = 11.sp),
                    color = growthColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x80000000))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun StatRow(summary: CollectionSummary) {
    val colors = BwTheme.colors
    // Faithful yellow "frame": 2dp yellow padding (radius 16) around the card (radius 14).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.brandYellow)
            .padding(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.card)
                .padding(vertical = 18.dp, horizontal = 8.dp),
        ) {
            StatItem(R.drawable.ic_bw_set, summary.setCount.toString(), "Sets", Modifier.weight(1f))
            StatItem(R.drawable.ic_bw_minifig, formatCount(summary.minifigCount), "Minifigs", Modifier.weight(1f))
            StatItem(R.drawable.ic_bw_pieces, formatCount(summary.pieceCount), "Pieces", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatItem(
    @DrawableRes iconRes: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colors.textMuted2,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(value, style = BwType.statNumber, color = colors.text)
        Spacer(Modifier.height(2.dp))
        Text(label, style = BwType.statLabel, color = colors.textMuted)
    }
}

@Composable
private fun SignInFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    // Blink attention: opacity pulse 100%→40%→100% over 1.3s (650ms each way, reversed).
    val transition = rememberInfiniteTransition(label = "signInFab")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(650, easing = EaseInOut), RepeatMode.Reverse),
        label = "blink",
    )
    Box(
        modifier = modifier
            .size(56.dp)
            .alpha(blink)
            .clip(CircleShape)
            .background(colors.error)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
    }
}

@Composable
private fun SignInDialog(onDismiss: () -> Unit, onSignIn: () -> Unit) {
    val colors = BwTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bw_lock),
                    contentDescription = null,
                    tint = colors.textMuted2,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Sign in to save and sync your collection across devices",
                    style = BwType.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandYellow,
                        contentColor = colors.onYellow,
                    ),
                ) {
                    Text("Sign In", style = BwType.pill)
                }
            }
        }
    }
}

// ---- Previews ----

private val previewSummary = CollectionSummary(
    setCount = 8,
    minifigCount = 38,
    pieceCount = 28_553,
    collectionValue = 90_608_440,
    paid = 82_939_480,
    growthPercent = 9.0,
)

@Preview(name = "Home – logged in", showBackground = true, heightDp = 720)
@Composable
private fun HomeLoggedInPreview() {
    BrickWaresTheme {
        HomeContent(
            state = HomeUiState(isLoading = false, isLoggedIn = true, summary = previewSummary),
            onShareClick = {}, onSignInPrompt = {}, onDismissSignInDialog = {}, onSignIn = {},
        )
    }
}

@Preview(name = "Home – logged out", showBackground = true, heightDp = 720)
@Composable
private fun HomeLoggedOutPreview() {
    BrickWaresTheme {
        HomeContent(
            state = HomeUiState(isLoading = false, isLoggedIn = false, summary = previewSummary),
            onShareClick = {}, onSignInPrompt = {}, onDismissSignInDialog = {}, onSignIn = {},
        )
    }
}

@Preview(name = "Home – dark", showBackground = true, heightDp = 720)
@Composable
private fun HomeDarkPreview() {
    BrickWaresTheme(darkTheme = true) {
        HomeContent(
            state = HomeUiState(isLoading = false, isLoggedIn = true, summary = previewSummary),
            onShareClick = {}, onSignInPrompt = {}, onDismissSignInDialog = {}, onSignIn = {},
        )
    }
}
