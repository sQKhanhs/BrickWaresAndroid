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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.RateAppDialog
import com.senniapp.brickwares.ui.components.rememberIsLoggedIn
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.util.RatePrompt
import com.senniapp.brickwares.util.RetirementAlerts
import com.senniapp.brickwares.ui.login.LoginScreen
import com.senniapp.brickwares.ui.collection.CollectionScreen
import com.senniapp.brickwares.ui.detail.MinifigDetailScreen
import com.senniapp.brickwares.ui.detail.SetDetailScreen
import com.senniapp.brickwares.ui.home.HomeScreen
import com.senniapp.brickwares.ui.home.NewSetsScreen
import com.senniapp.brickwares.ui.search.SearchScreen
import com.senniapp.brickwares.ui.search.ThemeResultsScreen
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
    // The bottom-nav visit history, Home at the root. Switching tabs PUSHES onto this, so Back retraces
    // the tabs the user actually visited (Home → Collection → Search … → Back → Collection → Home → exit)
    // instead of exiting on the first Back. `selectedTab` is just the top. Saveable across config change /
    // process death.
    val tabStack = rememberSaveable(
        saver = listSaver(
            save = { it.map(BwTab::name) },
            restore = { it.map { name -> BwTab.valueOf(name) }.toMutableStateList() },
        ),
    ) { mutableStateListOf(BwTab.Home) }
    val selectedTab = tabStack.last()
    // Detail navigation back-stack shown over the current tab (nav bar stays). Each entry is a set
    // ("s:<setNumber>"), a minifig ("f:<figNum>"), the New Sets page ("n:") or a theme's result list
    // ("t:<theme>␟<subtheme>", opened from a Set Detail's theme link); the last entry is the visible
    // detail, so opening a set/fig pushes and Back pops — travelling set → fig → set … returns step by
    // step, not straight to the tab. Empty = the tab's own content is shown. Cleared on a tab switch.
    // Saveable across config change / process death.
    val detailStack = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }
    val openSet: (String) -> Unit = { detailStack.add("s:$it") }
    val openFig: (String) -> Unit = { detailStack.add("f:$it") }
    val popDetail: () -> Unit = { if (detailStack.isNotEmpty()) detailStack.removeAt(detailStack.lastIndex) }
    // Switch tabs: record the destination in the visit history (a no-op re-tap of the current tab isn't
    // pushed) and drop the current tab's transient detail stack so the destination opens at its root.
    val goToTab: (BwTab) -> Unit = { tab ->
        if (tab != tabStack.last()) tabStack.add(tab)
        detailStack.clear()
    }
    val current = detailStack.lastOrNull()
    val colors = BwTheme.colors
    // Unified Back: pop an open detail first, else step back through the tab history; at the Home root
    // (nothing left to pop) it's disabled and the system exits the app. A tab's own full-screen sub-view
    // (the Search theme browse) registers a deeper BackHandler, so that's handled before this fires.
    BackHandler(enabled = current != null || tabStack.size > 1) {
        if (detailStack.isNotEmpty()) popDetail() else if (tabStack.size > 1) tabStack.removeAt(tabStack.lastIndex)
    }
    // Held here (Activity-scoped) so re-entering the Search tab from another tab can reset it to
    // its default browse view — a lingering search shouldn't persist across tab switches.
    val searchViewModel: SearchViewModel = viewModel()
    // On-demand sign-in overlay (no login wall): gated surfaces call SignInController.request().
    val showLogin by SignInController.showLogin.collectAsStateWithLifecycle()
    val isLoggedIn = rememberIsLoggedIn()
    // An external tab request (a tapped retirement-alert notification → Wishlist), whether it arrived
    // at launch or while running. Consumed so it fires once.
    val requestedTab by NavRequests.tab.collectAsStateWithLifecycle()
    LaunchedEffect(requestedTab) {
        requestedTab?.let { tab ->
            // A deep link (e.g. a retirement-alert tap → Wishlist) resets the history to Home → tab, so
            // Back from the deep-linked tab returns to Home, then exits.
            detailStack.clear()
            tabStack.clear()
            if (tab != BwTab.Home) tabStack.add(BwTab.Home)
            tabStack.add(tab)
            NavRequests.consume()
        }
    }
    // When the user finishes signing in (the modal was open), dismiss it and land on Collection.
    // A cold-start auto-login (modal never opened) leaves them on the current tab.
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && showLogin) {
            SignInController.dismiss()
            detailStack.clear()
            tabStack.clear()
            tabStack.add(BwTab.Home)
            tabStack.add(BwTab.Collection)
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
                    if (tab == BwTab.Search) searchViewModel.onEnterSearchTab()
                    goToTab(tab)
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
                        goToTab(BwTab.Search)
                        searchViewModel.showSets()
                    },
                )
            } else if (current != null && current.startsWith("t:")) { // a theme's results (Set Detail link)
                val (theme, subtheme) = decodeThemeEntry(current)
                // Its OWN view model, NOT the Search tab's — opening a theme from a Set Detail must not
                // clobber the tab's live results (search → result → theme → Back, Back should return to the
                // search results, not the theme page). Keyed, so it survives Back and re-open.
                val themeResultsViewModel: SearchViewModel = viewModel(key = "themeResults")
                ThemeResultsScreen(
                    theme = theme,
                    subtheme = subtheme,
                    viewModel = themeResultsViewModel,
                    onBack = popDetail,
                    // Tapping a result pushes its detail; Back returns to this theme list.
                    onOpenSetDetail = openSet,
                )
            } else if (current != null && current.startsWith("n:")) { // the "New Sets" page
                NewSetsScreen(
                    viewModel = searchViewModel,
                    onBack = popDetail,
                    // Tapping a new set pushes its detail onto the same stack (Back returns here).
                    onOpenSetDetail = openSet,
                )
            } else if (current != null) { // "s:" — a set
                SetDetailScreen(
                    setNumber = current.substring(2),
                    onBack = popDetail,
                    onOpenSetDetail = openSet,
                    // Tapping a minifig in the set's grid pushes the minifig detail.
                    onOpenMinifig = openFig,
                    // Tapping the theme/subtheme link pushes that theme's result list onto the stack
                    // (stays on the current tab), so Back returns to this set — not the Search home.
                    onOpenTheme = { theme, subtheme ->
                        // Just push the entry — ThemeResultsScreen opens the theme on its OWN view model,
                        // so the Search tab's results are left intact.
                        detailStack.add(themeEntry(theme, subtheme))
                    },
                    // Show the search FABs on the detail only when it's opened from the Search tab.
                    showSearchFab = selectedTab == BwTab.Search,
                    // Switching to minifig search exits the detail stack and lands on the minifig home.
                    onSwitchToMinifigSearch = {
                        goToTab(BwTab.Search)
                        searchViewModel.showMinifigs()
                    },
                )
            } else {
                when (selectedTab) {
                    BwTab.Home -> HomeScreen(
                        onOpenSetDetail = openSet,
                        onOpenNewSets = { detailStack.add("n:") },
                    )
                    BwTab.Collection -> CollectionScreen(
                        onOpenSetDetail = openSet,
                        onOpenMinifigDetail = openFig,
                    )
                    BwTab.Wishlist -> WishlistScreen(
                        // Match the Search nav tap: reset to the browse home, don't drop the user back
                        // into the Search tab's last theme/results view.
                        onNavigateToSearch = {
                            searchViewModel.onEnterSearchTab()
                            goToTab(BwTab.Search)
                        },
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
            // Foreground retirement notice: when the app is visible, RetirementAlerts routes the "item
            // retired" message here as a toast instead of posting a system notification.
            val retirementNotice by RetirementAlerts.inAppNotice.collectAsStateWithLifecycle()
            BwToast(message = retirementNotice?.resolve(), onDismiss = RetirementAlerts::clearInAppNotice)
        }
    }

    // On-demand sign-in modal (its own window; shown over the tabs when a gated action is tapped).
    if (showLogin) {
        LoginScreen(onDismiss = { SignInController.dismiss() })
    }

    // "Enjoying BrickWares?" rating prompt — raised by RatePrompt once the collection/sales + wishlist
    // thresholds are met; held back while the sign-in modal is up so two dialogs don't stack.
    val showRatePrompt by RatePrompt.show.collectAsStateWithLifecycle()
    if (showRatePrompt && !showLogin) {
        val context = LocalContext.current
        RateAppDialog(
            onRate = { RatePrompt.onRateNow(context) },
            onLater = RatePrompt::onLater,
            onNever = RatePrompt::onNever,
        )
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

/** Separator inside a "t:" stack entry — a control char that can't appear in a theme/subtheme name. */
private const val THEME_ENTRY_SEP = ''

/** Encodes a theme's result-list stack entry: "t:<theme><sep><subtheme>" (empty subtheme = all). */
private fun themeEntry(theme: String, subtheme: String?): String =
    "t:$theme$THEME_ENTRY_SEP${subtheme.orEmpty()}"

/** Inverse of [themeEntry]: the theme and the subtheme (null when the entry covers the whole theme). */
private fun decodeThemeEntry(entry: String): Pair<String, String?> {
    val body = entry.substring(2)
    val i = body.indexOf(THEME_ENTRY_SEP)
    if (i < 0) return body to null
    return body.substring(0, i) to body.substring(i + 1).ifEmpty { null }
}
