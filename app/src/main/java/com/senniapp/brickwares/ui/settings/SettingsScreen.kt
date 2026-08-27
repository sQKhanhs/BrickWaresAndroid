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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.ui.components.rememberIsOnline
import com.senniapp.brickwares.ui.navigation.SignInController
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
    val isOnline = rememberIsOnline()
    SettingsContent(
        state = state,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        isOnline = isOnline,
        onSignOut = viewModel::onSignOut,
        onOpenAvatarPicker = viewModel::onOpenAvatarPicker,
        onCloseAvatarPicker = viewModel::onCloseAvatarPicker,
        onSelectAvatar = viewModel::onSelectAvatar,
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
    isOnline: Boolean = true,
    onSignOut: () -> Unit,
    onOpenAvatarPicker: () -> Unit,
    onCloseAvatarPicker: () -> Unit,
    onSelectAvatar: (AvatarGender) -> Unit,
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
            Text(stringResource(R.string.settings_title), style = BwType.wordmark, color = colors.text, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))

            // ---- Account ----
            Section(stringResource(R.string.settings_section_account)) {
                if (state.isLoggedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = state.avatar.asset,
                            contentDescription = stringResource(R.string.settings_change_avatar_cd),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.surface)
                                .clickable(onClick = onOpenAvatarPicker),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(state.userName, style = BwType.body.copy(fontWeight = FontWeight.Bold), color = colors.text, maxLines = 1)
                            Text(state.userEmail, style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted2, maxLines = 1)
                        }
                        // Sign-out needs network — hidden while offline (an "Offline" chip instead).
                        if (isOnline) {
                            Pill(text = stringResource(R.string.settings_sign_out), filled = true, onClick = onSignOut)
                        } else {
                            Text(stringResource(R.string.settings_offline), style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                        }
                    }
                    RowDivider()
                    NavRow(stringResource(R.string.settings_delete_account), onClick = onRequestDelete, danger = true)
                    if (state.showDeleteConfirm) {
                        Column(modifier = Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.settings_delete_confirm_body),
                                style = BwType.body.copy(fontSize = 11.sp),
                                color = colors.textMuted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinePill(stringResource(R.string.action_cancel), onClick = onCancelDelete, modifier = Modifier.weight(1f))
                                DangerPill(stringResource(R.string.action_delete), onClick = onConfirmDelete, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Opens the sign-in modal (Google + email/password), which handles offline.
                        Button(
                            onClick = { SignInController.request() },
                            modifier = Modifier.fillMaxWidth(0.5f).height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brandYellow,
                                contentColor = colors.onYellow,
                            ),
                        ) {
                            Text(stringResource(R.string.settings_sign_in), style = BwType.pill)
                        }
                    }
                }
            }

            // ---- Display ----
            Section(stringResource(R.string.settings_section_display)) {
                DropdownRow(
                    label = stringResource(R.string.settings_language),
                    selectedLabel = state.language.label,
                    options = AppLanguage.entries.map { it to it.label },
                    onSelect = onLanguageChange,
                )
                RowDivider()
                DropdownRow(
                    label = stringResource(R.string.settings_currency),
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
                    Text(stringResource(R.string.settings_theme), style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val label = stringResource(if (mode == ThemeMode.DARK) R.string.theme_dark else R.string.theme_light)
                            SegmentButton(label = label, selected = mode == themeMode, onClick = { onThemeModeChange(mode) })
                        }
                    }
                }
            }

            // ---- Data ----
            Section(stringResource(R.string.settings_section_data)) {
                NavRow(stringResource(R.string.settings_export_csv), onClick = { onComingSoon("Export") })
                RowDivider()
                NavRow(stringResource(R.string.settings_import_csv), onClick = { onComingSoon("Import") })
            }

            // ---- Notifications ----
            Section(stringResource(R.string.settings_section_notifications)) {
                ToggleRow(stringResource(R.string.settings_retirement_alerts), checked = state.retirementAlerts, onToggle = onToggleRetirement)
            }

            // ---- Privacy ----
            Section(stringResource(R.string.settings_section_privacy)) {
                NavRow(stringResource(R.string.settings_privacy_policy), onClick = { onComingSoon("Privacy Policy") })
                RowDivider()
                NavRow(stringResource(R.string.settings_terms), onClick = { onComingSoon("Terms of Service") })
                RowDivider()
                ToggleRow(
                    stringResource(R.string.settings_usage_analytics),
                    checked = state.analyticsConsent,
                    onToggle = onToggleAnalytics,
                    description = stringResource(R.string.settings_usage_analytics_desc),
                )
            }

            // ---- About ----
            Section(stringResource(R.string.settings_section_about)) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_data_attribution), style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.text)
                    Text(
                        stringResource(R.string.settings_attribution_body),
                        style = BwType.body.copy(fontSize = 11.sp),
                        color = colors.textMuted2,
                    )
                }
                RowDivider()
                NavRow(stringResource(R.string.settings_version), onClick = onToggleChangelog)
                if (state.showChangelog) {
                    Text(
                        stringResource(R.string.settings_changelog_body),
                        style = BwType.body.copy(fontSize = 11.sp),
                        color = colors.textMuted2,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                RowDivider()
                NavRow(stringResource(R.string.settings_send_feedback), onClick = { onComingSoon("Feedback") })
                RowDivider()
                NavRow(stringResource(R.string.settings_rate), onClick = { onComingSoon("Rate") })
            }
        }

        if (state.showAvatarPicker) {
            AvatarPickerDialog(
                selected = state.avatar,
                onSelect = onSelectAvatar,
                onDismiss = onCloseAvatarPicker,
            )
        }

        BwToast(message = state.toastMessage?.resolve(), onDismiss = onToastShown)
    }
}

@Composable
private fun AvatarPickerDialog(
    selected: AvatarGender,
    onSelect: (AvatarGender) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BwTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.settings_choose_avatar), style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AvatarGender.entries.forEach { avatar ->
                        val isSelected = avatar == selected
                        AsyncImage(
                            model = avatar.asset,
                            contentDescription = avatar.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(colors.surface)
                                .border(
                                    BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) colors.brandYellow else colors.borderSoft),
                                    CircleShape,
                                )
                                .clickable { onSelect(avatar) },
                        )
                    }
                }
            }
        }
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
            // Theme-stable dark fill (not colors.text, which flips to near-white in dark and left the
            // yellow label unreadable) so the pill stays dark with yellow text in both themes.
            .background(if (filled) colors.onYellow else colors.card)
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
