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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.collection.CollectionScreen
import com.senniapp.brickwares.ui.home.HomeScreen
import com.senniapp.brickwares.ui.wishlist.WishlistScreen
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/** The five persistent bottom-nav destinations (icons are the design's line-icon drawables). */
enum class BwTab(val label: String, @param:DrawableRes val icon: Int) {
    Home("Home", R.drawable.ic_bw_home),
    Collection("Collection", R.drawable.ic_bw_set),
    Wishlist("Wishlist", R.drawable.ic_bw_heart),
    Search("Search", R.drawable.ic_bw_search),
    Settings("Settings", R.drawable.ic_bw_settings),
}

/** Root app shell: persistent bottom nav + the selected tab's content. */
@Composable
fun BrickWaresApp() {
    var selectedTab by rememberSaveable { mutableStateOf(BwTab.Home) }
    val colors = BwTheme.colors

    Scaffold(
        containerColor = colors.bg,
        bottomBar = { BwBottomBar(selected = selectedTab, onSelect = { selectedTab = it }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                BwTab.Home -> HomeScreen()
                BwTab.Collection -> CollectionScreen()
                BwTab.Wishlist -> WishlistScreen()
                else -> PlaceholderScreen(title = selectedTab.label)
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

@Composable
private fun PlaceholderScreen(title: String) {
    val colors = BwTheme.colors
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title — coming soon", style = BwType.cardTitle, color = colors.textMuted)
    }
}
