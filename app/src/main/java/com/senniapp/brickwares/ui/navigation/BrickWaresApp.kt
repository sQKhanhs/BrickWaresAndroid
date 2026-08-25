package com.senniapp.brickwares.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.ui.collection.CollectionScreen
import com.senniapp.brickwares.ui.detail.SetDetailScreen
import com.senniapp.brickwares.ui.home.HomeScreen
import com.senniapp.brickwares.ui.search.SearchScreen
import com.senniapp.brickwares.ui.search.SearchViewModel
import com.senniapp.brickwares.ui.settings.SettingsScreen
import com.senniapp.brickwares.ui.wishlist.WishlistScreen
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.ThemeMode
import com.senniapp.brickwares.ui.theme.BwType

/** The five persistent bottom-nav destinations (icons are the design's line-icon drawables). */
enum class BwTab(val label: String, @param:DrawableRes val icon: Int) {
    Home("Home", R.drawable.ic_bw_home),
    Collection("Collection", R.drawable.ic_bw_set),
    Wishlist("Wishlist", R.drawable.ic_bw_heart),
    Search("Search", R.drawable.ic_bw_search),
    Settings("Settings", R.drawable.ic_bw_settings),
}

/** Root app shell: persistent bottom nav + the selected tab's content (or a Set Detail overlay). */
@Composable
fun BrickWaresApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(BwTab.Home) }
    // When non-null, the Set Detail page is shown over the current tab (nav bar stays visible).
    var detailSetNumber by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = BwTheme.colors
    // Held here (Activity-scoped) so re-entering the Search tab from another tab can reset it to
    // its default browse view — a lingering search shouldn't persist across tab switches.
    val searchViewModel: SearchViewModel = viewModel()
    // Account-switch guard (Decision 10): non-null when a different account signed in over existing
    // local data. Observing it also starts the SyncCoordinator (auth-driven sync).
    val pendingSwitch by CollectionRepositoryProvider.syncCoordinator.pendingSwitch
        .collectAsStateWithLifecycle()

    Scaffold(
        containerColor = colors.bg,
        bottomBar = {
            BwBottomBar(
                selected = selectedTab,
                onSelect = { tab ->
                    // Reset Search only when arriving from a different tab (not when returning from
                    // a Set Detail overlay, which keeps the current results in place).
                    if (tab == BwTab.Search && selectedTab != BwTab.Search) searchViewModel.resetToDefault()
                    selectedTab = tab
                    detailSetNumber = null
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val detail = detailSetNumber
            if (detail != null) {
                SetDetailScreen(
                    setNumber = detail,
                    onBack = { detailSetNumber = null },
                    onOpenSetDetail = { detailSetNumber = it },
                    onNavigateToSearch = { detailSetNumber = null; selectedTab = BwTab.Search },
                )
            } else {
                when (selectedTab) {
                    BwTab.Home -> HomeScreen()
                    BwTab.Collection -> CollectionScreen(onOpenSetDetail = { detailSetNumber = it })
                    BwTab.Wishlist -> WishlistScreen(
                        onNavigateToSearch = { selectedTab = BwTab.Search },
                        onOpenSetDetail = { detailSetNumber = it },
                    )
                    BwTab.Search -> SearchScreen(
                        onOpenSetDetail = { detailSetNumber = it },
                        viewModel = searchViewModel,
                    )
                    BwTab.Settings -> SettingsScreen(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
                }
            }
        }
    }

    pendingSwitch?.let { p ->
        AccountSwitchDialog(
            accountName = p.accountName,
            onKeepMerge = { CollectionRepositoryProvider.syncCoordinator.keepAndMerge() },
            onDiscardLoad = { CollectionRepositoryProvider.syncCoordinator.discardAndLoad() },
        )
    }
}

/**
 * Account-switch prompt (Decision 10): a different account signed in on a device already holding
 * another session's data. The user MUST choose — the dialog isn't dismissable — so two accounts'
 * data are never silently mixed.
 */
@Composable
private fun AccountSwitchDialog(
    accountName: String,
    onKeepMerge: () -> Unit,
    onDiscardLoad: () -> Unit,
) {
    val colors = BwTheme.colors
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Different account", style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(10.dp))
                Text(
                    "This device has collection data from another session. Keep and merge it into " +
                        "$accountName, or discard it and load $accountName's collection?",
                    style = BwType.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onKeepMerge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandYellow,
                        contentColor = colors.onYellow,
                    ),
                ) {
                    Text("Keep & merge", style = BwType.pill)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDiscardLoad,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("Discard local & load", style = BwType.pill, color = colors.text)
                }
            }
        }
    }
}

@Composable
private fun BwBottomBar(selected: BwTab, onSelect: (BwTab) -> Unit) {
    val colors = BwTheme.colors
    Surface(color = colors.card) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = colors.borderSoft)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BwTab.entries.forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: BwTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    val tint = if (selected) colors.text else colors.textMuted
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(tab.label, style = BwType.navLabel, color = tint)
        Spacer(Modifier.height(4.dp))
        // Active-tab underline indicator (yellow).
        Box(
            modifier = Modifier
                .height(2.5.dp)
                .width(18.dp)
                .clip(CircleShape)
                .background(if (selected) colors.brandYellow else Color.Transparent),
        )
    }
}
