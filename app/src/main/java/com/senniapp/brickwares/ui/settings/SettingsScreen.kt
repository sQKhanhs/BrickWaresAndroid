package com.senniapp.brickwares.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.ui.theme.ThemeMode
import com.senniapp.brickwares.util.AppCurrency

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onSignIn = viewModel::onSignIn,
        onSignOut = viewModel::onSignOut,
        onRequestDelete = viewModel::onRequestDeleteAccount,
        onCancelDelete = viewModel::onCancelDeleteAccount,
        onConfirmDelete = viewModel::onConfirmDeleteAccount,
        onLanguageChange = viewModel::onLanguageChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onToggleRetirement = viewModel::onToggleRetirementAlerts,
        onToggleAnalytics = viewModel::onToggleAnalytics,
        onToggleChangelog = viewModel::onToggleChangelog,
        onComingSoon = viewModel::onComingSoon,
        onToastShown = viewModel::onToastShown,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onCurrencyChange: (AppCurrency) -> Unit,
    onToggleRetirement: () -> Unit,
    onToggleAnalytics: () -> Unit,
    onToggleChangelog: () -> Unit,
    onComingSoon: (String) -> Unit,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Settings", style = BwType.wordmark, color = colors.text, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))

            // ---- Account ----
            Section("Account") {
                if (state.isLoggedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.brandYellow),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(state.userName.take(1), style = BwType.cardTitle, color = colors.onYellow)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(state.userName, style = BwType.body.copy(fontWeight = FontWeight.Bold), color = colors.text, maxLines = 1)
                            Text(state.userEmail, style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted2, maxLines = 1)
                        }
                        Pill(text = "Sign Out", filled = true, onClick = onSignOut)
                    }
                    RowDivider()
                    NavRow("Delete account", onClick = onRequestDelete, danger = true)
                    if (state.showDeleteConfirm) {
                        Column(modifier = Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "This permanently removes your account and all collection data. This can't be undone.",
                                style = BwType.body.copy(fontSize = 11.sp),
                                color = colors.textMuted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinePill("Cancel", onClick = onCancelDelete, modifier = Modifier.weight(1f))
                                DangerPill("Delete", onClick = onConfirmDelete, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_bw_lock), contentDescription = null, tint = colors.textFaint, modifier = Modifier.size(28.dp))
                        Text(
                            "Sign in to sync your collection across devices and back it up.",
                            style = BwType.body.copy(fontSize = 12.sp),
                            color = colors.textMuted,
                        )
                        Pill(text = "Sign in with Google", filled = true, onClick = onSignIn)
                    }
                }
            }

            // ---- Display ----
            Section("Display") {
                DropdownRow(
                    label = "Language",
                    selectedLabel = state.language.label,
                    options = AppLanguage.entries.map { it to it.label },
                    onSelect = onLanguageChange,
                )
                RowDivider()
                DropdownRow(
                    label = "Currency",
                    selectedLabel = currencyLabel(state.currency),
                    options = listOf(AppCurrency.VND to currencyLabel(AppCurrency.VND), AppCurrency.USD to currencyLabel(AppCurrency.USD)),
                    onSelect = onCurrencyChange,
                )
                RowDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Theme", style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            SegmentButton(label = mode.label, selected = mode == themeMode, onClick = { onThemeModeChange(mode) })
                        }
                    }
                }
            }

            // ---- Data ----
            Section("Data") {
                NavRow("Export collection (CSV)", onClick = { onComingSoon("Export") })
                RowDivider()
                NavRow("Import collection (CSV)", onClick = { onComingSoon("Import") })
            }

            // ---- Notifications ----
            Section("Notifications") {
                ToggleRow("Retirement alerts", checked = state.retirementAlerts, onToggle = onToggleRetirement)
            }

            // ---- Privacy ----
            Section("Privacy") {
                NavRow("Privacy Policy", onClick = { onComingSoon("Privacy Policy") })
                RowDivider()
                NavRow("Terms of Service", onClick = { onComingSoon("Terms of Service") })
                RowDivider()
                ToggleRow(
                    "Usage analytics",
                    checked = state.analyticsConsent,
                    onToggle = onToggleAnalytics,
                    description = "Share anonymous usage data to help improve the app.",
                )
            }

            // ---- About ----
            Section("About") {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Data attribution", style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.text)
                    Text(
                        "Catalog data from Brickset and Rebrickable. LEGO® is a trademark of the LEGO Group, which does not sponsor or endorse this app.",
                        style = BwType.body.copy(fontSize = 11.sp),
                        color = colors.textMuted2,
                    )
                }
                RowDivider()
                NavRow("Version · v0.1.0 (preview)", onClick = onToggleChangelog)
                if (state.showChangelog) {
                    Text(
                        "Preview build on mock data: Home, Collection, Wishlist, Search, Set Detail and Settings. Backend sync coming soon.",
                        style = BwType.body.copy(fontSize = 11.sp),
                        color = colors.textMuted2,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                RowDivider()
                NavRow("Send feedback", onClick = { onComingSoon("Feedback") })
                RowDivider()
                NavRow("Rate BrickWares", onClick = { onComingSoon("Rate") })
            }
        }

        BwToast(message = state.toastMessage, onDismiss = onToastShown)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = BwTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.uppercase(),
            style = BwType.micro.copy(fontWeight = FontWeight.ExtraBold),
            color = colors.textMuted,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(horizontal = 14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = BwTheme.colors.border)
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val colors = BwTheme.colors
    val color = if (danger) colors.error else colors.text
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = color)
        Text("›", style = BwType.cardTitle.copy(fontSize = 18.sp), color = if (danger) colors.error else colors.textFaint)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit, description: String? = null) {
    val colors = BwTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onYellow,
                    checkedTrackColor = colors.brandYellow,
                    uncheckedTrackColor = colors.track,
                    uncheckedBorderColor = colors.borderStrong,
                ),
            )
        }
        if (description != null) {
            Text(description, style = BwType.body.copy(fontSize = 11.sp), color = colors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun <T> DropdownRow(label: String, selectedLabel: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    val colors = BwTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(selectedLabel, style = BwType.body.copy(fontSize = 12.sp), color = colors.textSecondary)
                Text("▾", style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel, style = BwType.body.copy(fontSize = 13.sp), color = colors.text) },
                        onClick = { onSelect(value); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BwTheme.colors
    val bg = if (selected) colors.brandYellow else colors.card
    val fg = if (selected) colors.onYellow else colors.text
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(999.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = BwType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = fg)
    }
}

@Composable
private fun Pill(text: String, filled: Boolean, onClick: () -> Unit) {
    val colors = BwTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) colors.text else colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, style = BwType.pill.copy(fontSize = 12.sp), color = colors.brandYellow)
    }
}

@Composable
private fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

@Composable
private fun DangerPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.error)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BwType.body.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = Color.White)
    }
}

private fun currencyLabel(currency: AppCurrency): String = when (currency) {
    AppCurrency.VND -> "₫ VND"
    AppCurrency.USD -> "$ USD"
}
