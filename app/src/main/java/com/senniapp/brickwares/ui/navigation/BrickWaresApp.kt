package com.senniapp.brickwares.ui.navigation

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.components.rememberIsLoggedIn
import com.senniapp.brickwares.ui.login.LoginScreen
import com.senniapp.brickwares.ui.collection.CollectionScreen
import com.senniapp.brickwares.ui.detail.MinifigDetailScreen
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

/** Localized bottom-nav label. */
@Composable
private fun BwTab.text(): String = stringResource(
    when (this) {
        BwTab.Home -> R.string.nav_home
        BwTab.Collection -> R.string.nav_collection
        BwTab.Wishlist -> R.string.nav_wishlist
        BwTab.Search -> R.string.nav_search
        BwTab.Settings -> R.string.nav_settings
    },
)

/** Root app shell: persistent bottom nav + the selected tab's content (or a Set Detail overlay). */
@Composable
fun BrickWaresApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(BwTab.Home) }
    // Detail navigation back-stack shown over the current tab (nav bar stays). Each entry is a set
    // ("s:<setNumber>") or a minifig ("f:<figNum>"); the last entry is the visible detail, so opening
    // a set/fig pushes and Back pops — travelling set → fig → set … returns step by step, not straight
    // to the tab. Empty = the tab's own content is shown. Saveable across config change / process death.
    val detailStack = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }
    val openSet: (String) -> Unit = { detailStack.add("s:$it") }
    val openFig: (String) -> Unit = { detailStack.add("f:$it") }
    val popDetail: () -> Unit = { if (detailStack.isNotEmpty()) detailStack.removeAt(detailStack.lastIndex) }
    val current = detailStack.lastOrNull()
    val colors = BwTheme.colors
    // Hardware / gesture Back pops the detail stack while a detail is open (mirrors the ← button).
    BackHandler(enabled = current != null, onBack = popDetail)
    // Held here (Activity-scoped) so re-entering the Search tab from another tab can reset it to
    // its default browse view — a lingering search shouldn't persist across tab switches.
    val searchViewModel: SearchViewModel = viewModel()
    // On-demand sign-in overlay (no login wall): gated surfaces call SignInController.request().
    val showLogin by SignInController.showLogin.collectAsStateWithLifecycle()
    val isLoggedIn = rememberIsLoggedIn()
    // When the user finishes signing in (the modal was open), dismiss it and land on Collection.
    // A cold-start auto-login (modal never opened) leaves them on the current tab.
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && showLogin) {
            SignInController.dismiss()
            detailStack.clear()
            selectedTab = BwTab.Collection
        }
    }

    Scaffold(
        containerColor = colors.bg,
        bottomBar = {
            BwBottomBar(
                selected = selectedTab,
                onSelect = { tab ->
                    // Tapping the Search nav always returns to the search home (browse view) — even
                    // from results, a theme-detail list, or a Set Detail overlay — so the user can
                    // start a fresh search from anywhere. (The detail's back arrow still restores the
                    // previous results, since that path doesn't reset.)
                    if (tab == BwTab.Search) searchViewModel.resetToDefault()
                    selectedTab = tab
                    detailStack.clear()
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (current != null && current.startsWith("f:")) {
                MinifigDetailScreen(
                    figNum = current.substring(2),
                    onBack = popDetail,
                    // Tapping an "appears in" set pushes that set's detail onto the stack.
                    onOpenSetDetail = openSet,
                    // Quick-search can land on another minifig — pushes it.
                    onOpenMinifig = openFig,
                    // Show the search FABs on the detail only when it's opened from the Search tab.
                    showSearchFab = selectedTab == BwTab.Search,
                    // The mode-switch FAB exits the detail stack and lands on the Search set home.
                    onSwitchToSetSearch = {
                        detailStack.clear()
                        selectedTab = BwTab.Search
                        searchViewModel.showSets()
                    },
                )
            } else if (current != null) { // "s:" — a set
                SetDetailScreen(
                    setNumber = current.substring(2),
                    onBack = popDetail,
                    onOpenSetDetail = openSet,
                    // Tapping a minifig in the set's grid pushes the minifig detail.
                    onOpenMinifig = openFig,
                    // Tapping the theme/subtheme link opens the Search tab filtered to that theme.
                    onOpenTheme = { theme, subtheme ->
                        detailStack.clear()
                        selectedTab = BwTab.Search
                        searchViewModel.openSetTheme(theme, subtheme)
                    },
                    // Show the search FABs on the detail only when it's opened from the Search tab.
                    showSearchFab = selectedTab == BwTab.Search,
                    // Switching to minifig search exits the detail stack and lands on the minifig home.
                    onSwitchToMinifigSearch = {
                        detailStack.clear()
                        selectedTab = BwTab.Search
                        searchViewModel.showMinifigs()
                    },
                )
            } else {
                when (selectedTab) {
                    BwTab.Home -> HomeScreen()
                    BwTab.Collection -> CollectionScreen(
                        onOpenSetDetail = openSet,
                        onOpenMinifigDetail = openFig,
                    )
                    BwTab.Wishlist -> WishlistScreen(
                        onNavigateToSearch = { selectedTab = BwTab.Search },
                        onOpenSetDetail = openSet,
                        onOpenMinifigDetail = openFig,
                    )
                    BwTab.Search -> SearchScreen(
                        onOpenSetDetail = openSet,
                        onOpenMinifig = openFig,
                        viewModel = searchViewModel,
                    )
                    BwTab.Settings -> SettingsScreen(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
                }
            }
        }
    }

    // On-demand sign-in modal (its own window; shown over the tabs when a gated action is tapped).
    if (showLogin) {
        LoginScreen(onDismiss = { SignInController.dismiss() })
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
        val label = tab.text()
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = BwType.navLabel, color = tint)
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
