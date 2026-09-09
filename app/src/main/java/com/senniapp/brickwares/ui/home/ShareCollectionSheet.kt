package com.senniapp.brickwares.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.formatCount
import com.senniapp.brickwares.util.formatIn
import com.senniapp.brickwares.util.saveCollectionImage
import com.senniapp.brickwares.util.shareCollectionImage
import kotlinx.coroutines.launch

private val ShareAccent = Color(0xFFC99A1E)
private val BrandYellow = Color(0xFFFFD500)
private const val HIDDEN = "••••"

/** The share card's palette for a given preview theme — independent of the app's theme. */
private class ShareCardColors(
    val text: Color,
    val muted: Color,
    val border: Color,
    val surface: Color,
    val track: Color,
    val overlay: Color,
    val bgRes: Int,
)

private fun shareCardColors(dark: Boolean): ShareCardColors = if (dark) {
    ShareCardColors(
        text = Color.White,
        muted = Color.White.copy(alpha = 0.55f),
        border = Color.White.copy(alpha = 0.15f),
        surface = Color.White.copy(alpha = 0.08f),
        track = Color.White.copy(alpha = 0.15f),
        overlay = Color(0xFF18181B).copy(alpha = 0.82f),
        bgRes = R.drawable.share_preview_dark,
    )
} else {
    ShareCardColors(
        text = Color(0xFF1A1A1A),
        muted = Color(0xFF1A1A1A).copy(alpha = 0.55f),
        border = Color(0xFF1A1A1A).copy(alpha = 0.12f),
        surface = Color.White.copy(alpha = 0.65f),
        track = Color(0xFF1A1A1A).copy(alpha = 0.12f),
        overlay = Color(0xFFFAF8F5).copy(alpha = 0.85f),
        bgRes = R.drawable.share_preview_light,
    )
}

/**
 * The "Share Collection" bottom sheet: a preview card (rendered to an image on Share) with a
 * light/dark preview toggle and a hide-value toggle. Currency + language follow the app's current
 * settings. "Top Sets" is three editable slots — tap one to pick a set from the collection; empty
 * slots show a dashed "add" placeholder in the editor and are omitted from the shared image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCollectionSheet(state: HomeUiState, onDismiss: () -> Unit) {
    val colors = BwTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var previewDark by remember { mutableStateOf(colors.isDark) }
    // showValue → the itemized Top-Sets + By-Theme values; showCollectionValue → the big hero value.
    var showValue by remember { mutableStateOf(true) }
    var showCollectionValue by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }
    // While true the card omits empty slots (+ any placeholders) so the shared image is clean.
    var captureMode by remember { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    // The three chosen slots, by set number (null = empty).
    val slots = remember { mutableStateListOf<String?>(null, null, null) }
    val graphicsLayer = rememberGraphicsLayer()
    val chooserTitle = stringResource(R.string.share_chooser_title)

    var saving by remember { mutableStateOf(false) }

    // Resolve slots to live collection entries (drops any set no longer owned).
    val selected: List<FeaturedSet?> = slots.map { sn -> state.collectionSets.firstOrNull { it.setNumber == sn } }

    // Render the card into the graphics layer with the empty-slot placeholders hidden, then snapshot it.
    val captureCard: suspend () -> android.graphics.Bitmap = {
        captureMode = true
        withFrameNanos {}
        withFrameNanos {}
        val bmp = graphicsLayer.toImageBitmap().asAndroidBitmap()
        captureMode = false
        bmp
    }
    val doSave = {
        if (!sharing && !saving) {
            saving = true
            scope.launch {
                val ok = saveCollectionImage(context, captureCard())
                Toast.makeText(context, context.getString(if (ok) R.string.share_saved else R.string.share_save_failed), Toast.LENGTH_SHORT).show()
                saving = false
            }
        }
    }
    // Pre-Android-10 needs WRITE_EXTERNAL_STORAGE to write to the gallery; request it, then save.
    val writePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doSave() else Toast.makeText(context, context.getString(R.string.share_save_failed), Toast.LENGTH_SHORT).show()
    }
    val onSaveClick = {
        val ready = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (ready) doSave() else writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    val onShareClick = {
        if (!sharing && !saving) {
            sharing = true
            scope.launch {
                shareCollectionImage(context, captureCard(), chooserTitle)
                sharing = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.bg, dragHandle = null) {
        val editing = editingSlot
        if (editing != null) {
            // Sets already placed in the OTHER slots — greyed out + unpickable so no slot duplicates.
            val taken = slots.indices.filter { it != editing }.mapNotNull { slots[it] }.toSet()
            SetPickerContent(
                sets = state.collectionSets,
                currency = state.currency,
                disabled = taken,
                onBack = { editingSlot = null },
                onPick = { sn -> slots[editing] = sn; editingSlot = null },
            )
        } else {
            ShareSheetContent(
                state = state,
                previewDark = previewDark,
                showValue = showValue,
                showCollectionValue = showCollectionValue,
                captureMode = captureMode,
                selected = selected,
                busy = sharing || saving,
                graphicsLayer = graphicsLayer,
                onEditSlot = { editingSlot = it },
                onClearSlot = { slots[it] = null },
                onSetPreviewDark = { previewDark = it },
                onToggleValue = { showValue = !showValue },
                onToggleCollectionValue = { showCollectionValue = !showCollectionValue },
                onSave = onSaveClick,
                onShare = onShareClick,
            )
        }
    }
}

@Composable
private fun ShareSheetContent(
    state: HomeUiState,
    previewDark: Boolean,
    showValue: Boolean,
    showCollectionValue: Boolean,
    captureMode: Boolean,
    selected: List<FeaturedSet?>,
    busy: Boolean,
    graphicsLayer: GraphicsLayer,
    onEditSlot: (Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    onSetPreviewDark: (Boolean) -> Unit,
    onToggleValue: () -> Unit,
    onToggleCollectionValue: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = BwTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.share_title),
            style = BwType.cardTitle.copy(fontSize = 16.sp),
            color = colors.text,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        )

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.TopCenter) {
            SharePreviewCard(
                state = state,
                dark = previewDark,
                showValue = showValue,
                showCollectionValue = showCollectionValue,
                captureMode = captureMode,
                selected = selected,
                onEditSlot = onEditSlot,
                onClearSlot = onClearSlot,
                onToggleCollectionValue = onToggleCollectionValue,
                modifier = Modifier
                    .width(300.dp)
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawContent()
                    },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(999.dp))
                    .padding(3.dp),
            ) {
                PreviewThemeButton(stringResource(R.string.theme_light), selected = !previewDark) { onSetPreviewDark(false) }
                PreviewThemeButton(stringResource(R.string.theme_dark), selected = previewDark) { onSetPreviewDark(true) }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(999.dp))
                    .clickable(onClick = onToggleValue)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    painter = painterResource(if (showValue) R.drawable.ic_bw_eye else R.drawable.ic_bw_eye_off),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(if (showValue) R.string.share_hide_value else R.string.share_show_value),
                    style = BwType.body.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Save to the gallery (secondary).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(12.dp))
                        .clickable(enabled = !busy, onClick = onSave)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_bw_download), contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.share_save), style = BwType.pill.copy(fontSize = 14.sp), color = colors.text)
                    }
                }
                // Share via the Android sheet (primary).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brandYellow)
                        .clickable(enabled = !busy, onClick = onShare)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_bw_share), contentDescription = null, tint = colors.onYellow, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.share_action), style = BwType.pill.copy(fontSize = 14.sp), color = colors.onYellow)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewThemeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BwTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(if (selected) Modifier.background(colors.onYellow) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = if (selected) Color.White else colors.textSecondary)
    }
}

@Composable
private fun SharePreviewCard(
    state: HomeUiState,
    dark: Boolean,
    showValue: Boolean,
    showCollectionValue: Boolean,
    captureMode: Boolean,
    selected: List<FeaturedSet?>,
    onEditSlot: (Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    onToggleCollectionValue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sc = shareCardColors(dark)
    val currency = state.currency
    val summary = state.summary
    val topThemes = remember(state.themes) { state.themes.sortedByDescending { it.totalValue }.take(4) }
    val maxThemeValue = topThemes.maxOfOrNull { it.totalValue }?.coerceAtLeast(1L) ?: 1L
    val anyFilled = selected.any { it != null }

    Box(modifier.clip(RoundedCornerShape(18.dp)).border(BorderStroke(1.dp, sc.border), RoundedCornerShape(18.dp))) {
        Image(painterResource(sc.bgRes), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(sc.overlay))
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f, fill = false)) {
                    if (state.memberName.isNotBlank()) {
                        Text(state.memberName, style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = sc.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(stringResource(R.string.collection_title), style = BwType.body.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = sc.muted)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    buildAnnotatedString { append("Brick"); withStyle(SpanStyle(color = BrandYellow)) { append("Wares") } },
                    style = BwType.wordmark.copy(fontSize = 12.sp),
                    color = sc.text,
                )
            }

            HairLine(sc.border)

            // Collection value + its own hide/show toggle (independent of the itemized-values toggle).
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.home_collection_value).uppercase(), style = BwType.micro.copy(fontSize = 10.sp, letterSpacing = 0.4.sp), color = ShareAccent)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (showCollectionValue) formatIn(summary?.collectionValue ?: 0L, currency) else HIDDEN, style = BwType.wordmark.copy(fontSize = 32.sp), color = sc.text)
                    // Eye toggle next to the value (like a password reveal); editor only — never in the image.
                    if (!captureMode) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(sc.surface).clickable(onClick = onToggleCollectionValue),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(if (showCollectionValue) R.drawable.ic_bw_eye else R.drawable.ic_bw_eye_off),
                                contentDescription = null,
                                tint = sc.muted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            // Stat tiles
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile((summary?.setCount ?: 0).toString(), stringResource(R.string.stat_sets), sc, Modifier.weight(1f))
                StatTile(formatCount(summary?.pieceCount ?: 0), stringResource(R.string.stat_pieces), sc, Modifier.weight(1f))
                StatTile((summary?.minifigCount ?: 0).toString(), stringResource(R.string.stat_minifigs), sc, Modifier.weight(1f))
            }

            // Top sets — three editable slots (empty ones omitted from the shared image).
            if (!captureMode || anyFilled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.share_top_sets), sc)
                    selected.forEachIndexed { index, fs ->
                        when {
                            fs != null -> SlotFilled(fs, sc, showValue, currency, captureMode, onClick = { onEditSlot(index) }, onClear = { onClearSlot(index) })
                            !captureMode -> SlotPlaceholder(sc) { onEditSlot(index) }
                        }
                    }
                }
            }

            // By theme
            if (topThemes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SectionLabel(stringResource(R.string.share_by_theme), sc)
                    topThemes.forEach { tm ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(tm.theme, style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = sc.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(8.dp))
                                Row {
                                    Text(pluralStringResource(R.plurals.home_theme_set_count, tm.setCount, tm.setCount) + " · ", style = BwType.body.copy(fontSize = 11.sp), color = sc.muted)
                                    Text(if (showValue) formatIn(tm.totalValue, currency) else HIDDEN, style = BwType.body.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = ShareAccent)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(sc.track)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(fraction = (tm.totalValue.toFloat() / maxThemeValue).coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(BrandYellow),
                                )
                            }
                        }
                    }
                }
            }

            HairLine(sc.border)
            Text(
                stringResource(R.string.share_footer),
                modifier = Modifier.fillMaxWidth(),
                style = BwType.body.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                color = sc.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SlotFilled(fs: FeaturedSet, sc: ShareCardColors, showValue: Boolean, currency: AppCurrency, captureMode: Boolean, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        // The thumb/name area (tap to change) is clickable only in the editor; the shared image is static.
        modifier = Modifier.fillMaxWidth().then(if (captureMode) Modifier else Modifier.clickable(onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SetThumbBox(fs, sc)
        Column(Modifier.weight(1f)) {
            Text(fs.name, style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = sc.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fs.theme, style = BwType.body.copy(fontSize = 10.sp), color = sc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (showValue) formatIn(fs.value, currency) else HIDDEN, style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = ShareAccent)
        // Clear this slot back to empty — editor only (never in the shared image).
        if (!captureMode) {
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(sc.surface).clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", style = BwType.body.copy(fontSize = 12.sp), color = sc.muted)
            }
        }
    }
}

@Composable
private fun SetThumbBox(fs: FeaturedSet, sc: ShareCardColors) {
    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).background(sc.surface), contentAlignment = Alignment.Center) {
        if (fs.imageUrl != null) {
            AsyncImage(model = fs.imageUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            Text(fs.setNumber, style = BwType.micro.copy(fontSize = 8.sp, fontFamily = FontFamily.Monospace), color = ShareAccent)
        }
    }
}

@Composable
private fun SlotPlaceholder(sc: ShareCardColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .dashedRoundedBorder(sc.muted)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_bw_edit), contentDescription = null, tint = sc.muted, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.share_add_set_slot), style = BwType.body.copy(fontSize = 11.sp), color = sc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SetPickerContent(sets: List<FeaturedSet>, currency: AppCurrency, disabled: Set<String>, onBack: () -> Unit, onPick: (String) -> Unit) {
    val colors = BwTheme.colors
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", style = BwType.wordmark.copy(fontSize = 22.sp), color = colors.text)
            }
            Text(stringResource(R.string.share_choose_set), style = BwType.cardTitle.copy(fontSize = 16.sp), color = colors.text)
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            items(sets, key = { it.setNumber }) { s ->
                val isTaken = s.setNumber in disabled
                Row(
                    // Sets already in another slot are greyed + unpickable (no duplicates on the card).
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (isTaken) Modifier else Modifier.clickable { onPick(s.setNumber) })
                        .alpha(if (isTaken) 0.4f else 1f)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface), contentAlignment = Alignment.Center) {
                        if (s.imageUrl != null) {
                            AsyncImage(model = s.imageUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                        } else {
                            Text(s.setNumber, style = BwType.micro, color = colors.textMuted)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.theme, style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatIn(s.value, currency), style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.text)
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, sc: ShareCardColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(sc.surface).padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = BwType.body.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), color = sc.text, maxLines = 1)
        Text(label, style = BwType.body.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = sc.muted, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(text: String, sc: ShareCardColors) {
    Text(text.uppercase(), style = BwType.micro.copy(fontSize = 10.sp, letterSpacing = 0.3.sp), color = sc.muted)
}

@Composable
private fun HairLine(color: Color) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

/** A dashed ("cut line") rounded-rectangle border, for an empty Top-Sets slot. */
private fun Modifier.dashedRoundedBorder(color: Color, width: androidx.compose.ui.unit.Dp = 1.dp, corner: androidx.compose.ui.unit.Dp = 10.dp): Modifier =
    drawBehind {
        drawRoundRect(
            color = color,
            style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            cornerRadius = CornerRadius(corner.toPx()),
        )
    }
