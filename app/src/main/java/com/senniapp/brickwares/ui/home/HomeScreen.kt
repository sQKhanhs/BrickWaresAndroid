package com.senniapp.brickwares.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.gif.onAnimationEnd
import coil3.gif.repeatCount
import coil3.request.ImageRequest
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.components.SignInPromptCard
import com.senniapp.brickwares.ui.components.StatCardRow
import com.senniapp.brickwares.ui.components.StatEntry
import com.senniapp.brickwares.ui.components.animatedNumber
import com.senniapp.brickwares.ui.components.growthDirection
import com.senniapp.brickwares.ui.components.growthLabel
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.ThemeSummary
import com.senniapp.brickwares.ui.theme.BrickWaresTheme
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatCount
import com.senniapp.brickwares.util.formatIn

/** Stateful entry point — binds the [HomeViewModel] to the stateless [HomeContent]. */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Animate the intro GIF until it has fully played once this session. "Played" is marked
    // only when the animation completes (onGifFinished), so leaving mid-play replays it next
    // visit; once finished, the static poster is shown instead.
    val showGif = remember { !viewModel.hasHeroGifPlayed }
    HomeContent(
        state = state,
        showHeroGif = showGif,
        onGifFinished = viewModel::onHeroGifPlayed,
        onShareClick = viewModel::onShareClick,
        onCloseShare = viewModel::onCloseShare,
        modifier = modifier,
    )
}

/** Stateless Home UI — renders purely from [HomeUiState] so it's preview- and test-friendly. */
@Composable
private fun HomeContent(
    state: HomeUiState,
    showHeroGif: Boolean,
    onGifFinished: () -> Unit,
    onShareClick: () -> Unit,
    onCloseShare: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        if (!state.isReady) {
            // Cold-start: hold the whole page until the collection summary AND the auth session have
            // resolved, so Home appears all at once — instead of the sign-in prompt flashing in alone
            // (summary null + auth not yet resolved) and the hero/stats/themes popping in 1-2s later.
            CircularProgressIndicator(color = colors.brandYellow, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
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
                // Logged out shows zeros; Room may still hold the previous session's data (for
                // offline re-login) but it's hidden until signed in.
                val shown = if (state.isLoggedIn) summary else summary.copy(
                    setCount = 0, minifigCount = 0, pieceCount = 0,
                    collectionValue = 0L, paid = 0L, growthPercent = 0.0,
                )
                HeroCard(
                    summary = shown,
                    // The summary's money is already in state.currency (computed there); format with it,
                    // not BwTheme.currency, so a fresh switch never pairs an old amount with a new symbol.
                    currency = state.currency,
                    showGif = showHeroGif,
                    showNoValue = shown.setCount <= HeroAssets.NO_VALUE_MAX_SETS,
                    onGifFinished = onGifFinished,
                )
                Spacer(Modifier.height(14.dp))
                StatCardRow(
                    entries = listOf(
                        StatEntry(R.drawable.ic_bw_set, shown.setCount.toLong(), stringResource(R.string.stat_sets)),
                        StatEntry(R.drawable.ic_bw_minifig, shown.minifigCount.toLong(), stringResource(R.string.stat_minifigs)),
                        StatEntry(R.drawable.ic_bw_pieces, shown.pieceCount.toLong(), stringResource(R.string.stat_pieces)),
                    ),
                    keyPrefix = "home",
                )
            }
            Spacer(Modifier.height(14.dp))
            if (state.isLoggedIn) {
                if (state.themes.isNotEmpty()) ThemesCard(themes = state.themes, currency = state.currency)
            } else {
                // Logged out: prompt to sign in instead of the "Collection by Theme" card.
                SignInPromptCard(
                    message = stringResource(R.string.home_signin_prompt),
                    onSignIn = { SignInController.request() },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        if (state.shareOpen) {
            ShareCollectionSheet(state = state, onDismiss = onCloseShare)
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
                    contentDescription = stringResource(R.string.home_share_cd),
                    tint = colors.text,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Hero background gif sources. */
private object HeroAssets {
    /** Collections above this many sets show the larger celebratory drop. */
    const val SET_THRESHOLD = 100

    /**
     * At or below this many sets the hero shows the static "no value" brick ([NO_VALUE]) instead of
     * the celebratory drop — the collection is too small to have meaningful value yet. Once the user
     * owns more than this, the drop plays.
     */
    const val NO_VALUE_MAX_SETS = 3

    /** Static single-brick art shown while the collection is tiny (see [NO_VALUE_MAX_SETS]). */
    const val NO_VALUE = "file:///android_asset/no_value.png"

    /** Bundled default intro (compressed, ~5.5 MB) — works offline on first launch. */
    const val SMALL_GIF = "file:///android_asset/lego_drop_small.gif"

    /**
     * Static last frame of [SMALL_GIF], shown as the resting hero background so returning to
     * the tab shows where the gif stopped (not a dark card). Supplied by the user.
     */
    const val POSTER = "file:///android_asset/lego_drop_poster.png"

    /**
     * Larger drop for 100+ sets. Kept OUT of the APK (it's ~16 MB) — host it in Supabase
     * Storage and put the public URL here. Add the `coil-network-okhttp` dependency when set.
     * While blank, the small bundled gif is used for everyone.
     */
    const val BIG_GIF_URL = "" // TODO(supabase): Supabase Storage URL for LegoDrop.gif
}

@Composable
private fun HeroCard(
    summary: CollectionSummary,
    currency: AppCurrency,
    showGif: Boolean,
    showNoValue: Boolean,
    onGifFinished: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            // Light card for the "no value" state (design #f4f4f2), dark card for the celebratory drop.
            .background(if (showNoValue) Color(0xFFF4F4F2) else Color(0xFF1A1A1A)),
    ) {
        if (showNoValue) {
            // Small static single-brick art, low and centred (design ~55% width, near the bottom) —
            // deliberately not full-bleed so the brick reads as small.
            AsyncImage(
                model = HeroAssets.NO_VALUE,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.5f)
                    .padding(bottom = 22.dp),
            )
        } else {
            // Layer 0: resting last-frame poster (the hero's steady background) — shown only when the
            // gif is NOT about to play. Drawing it under a still-loading gif made the hero flash the
            // gif's LAST frame (the poster loads instantly; the ~5.5 MB gif doesn't) before the gif
            // popped in and animated from frame 0. While the gif plays it fully covers the card, so
            // during its load the dark card base shows instead — no last-frame flash on first open.
            if (!showGif) {
                AsyncImage(
                    model = HeroAssets.POSTER,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // Layer 1: animated Lego-drop GIF, played over the dark card once. onAnimationEnd marks it
            // played only on completion, so leaving mid-play replays it next visit. 100+ sets get the
            // larger remote drop; others the small bundled gif.
            if (showGif) {
                val heroGif = if (summary.setCount > HeroAssets.SET_THRESHOLD &&
                    HeroAssets.BIG_GIF_URL.isNotBlank()
                ) {
                    HeroAssets.BIG_GIF_URL
                } else {
                    HeroAssets.SMALL_GIF
                }
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(heroGif)
                        .repeatCount(0)
                        .onAnimationEnd { onGifFinished() }
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        // Layer 2: top-weighted dark gradient — keeps the label/value legible over any image.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xD11A1A1A),
                        0.42f to Color(0x261A1A1A),
                        1f to Color(0x1A1A1A1A),
                    ),
                ),
        )
        // Layer 3: content.
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_collection_value),
                style = BwType.heroLabel,
                color = BwTheme.colors.brandYellow,
            )
            Text(
                text = formatIn(animatedNumber(summary.collectionValue, "home_value"), currency),
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
                    text = stringResource(R.string.home_paid_pill, formatIn(summary.paid, currency)),
                    style = BwType.pill.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
                // Growth pill (▲/▼ glyph + coloured text, per spec) — one decimal place, so sub-1%
                // growth (e.g. +0.5%) isn't rounded away to "0%".
                val label = growthLabel(summary.growthPercent)
                val growthColor = when (growthDirection(summary.growthPercent)) {
                    1 -> Color(0xFF4ADE80)
                    -1 -> Color(0xFFF87171)
                    else -> Color.White
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
private fun ThemesCard(themes: List<ThemeSummary>, currency: AppCurrency) {
    val colors = BwTheme.colors
    val maxValue = themes.maxOfOrNull { it.totalValue } ?: 1L
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.borderSoft),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.home_collection_by_theme), style = BwType.cardTitle, color = colors.text)
            themes.forEach { theme ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        theme.theme,
                        style = BwType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            pluralStringResource(R.plurals.home_theme_set_count, theme.setCount, theme.setCount) + " · ",
                            style = BwType.body.copy(fontSize = 12.sp),
                            color = colors.textMuted,
                        )
                        Text(
                            formatIn(theme.totalValue, currency),
                            style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = colors.text,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.track),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (theme.totalValue.toFloat() / maxValue).coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.brandYellow),
                    )
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

private val previewThemes = listOf(
    ThemeSummary("Icons", 3, 34_200_000),
    ThemeSummary("City", 2, 12_400_000),
    ThemeSummary("Star Wars", 1, 22_500_000),
    ThemeSummary("Architecture", 1, 3_900_000),
)

@Preview(name = "Home – logged in", showBackground = true, heightDp = 720)
@Composable
private fun HomeLoggedInPreview() {
    BrickWaresTheme {
        HomeContent(
            state = HomeUiState(isLoading = false, authReady = true, isLoggedIn = true, summary = previewSummary, themes = previewThemes),
            showHeroGif = false,
            onGifFinished = {},
            onShareClick = {},
        )
    }
}

@Preview(name = "Home – logged out", showBackground = true, heightDp = 720)
@Composable
private fun HomeLoggedOutPreview() {
    BrickWaresTheme {
        HomeContent(
            state = HomeUiState(isLoading = false, authReady = true, isLoggedIn = false, summary = previewSummary),
            showHeroGif = false,
            onGifFinished = {},
            onShareClick = {},
        )
    }
}

@Preview(name = "Home – dark", showBackground = true, heightDp = 720)
@Composable
private fun HomeDarkPreview() {
    BrickWaresTheme(darkTheme = true) {
        HomeContent(
            state = HomeUiState(isLoading = false, authReady = true, isLoggedIn = true, summary = previewSummary),
            showHeroGif = false,
            onGifFinished = {},
            onShareClick = {},
        )
    }
}
