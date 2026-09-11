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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.senniapp.brickwares.BuildConfig
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.local.LocalePrefs
import com.senniapp.brickwares.ui.components.BwToast
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.ui.components.rememberIsOnline
import com.senniapp.brickwares.ui.navigation.SignInController
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import com.senniapp.brickwares.ui.theme.ThemeMode
import com.senniapp.brickwares.util.AppCurrency
import java.time.LocalDate

// Public legal pages, hosted on the web (brickwares.app) rather than baked into the app, so the text
// can be updated without an app release. Paths match the hosted files (privacy-policy.html /
// terms-of-service.html). Google Play also requires the privacy URL in the store listing, and the
// answers on the Play Data Safety form must match what the policy states.
private const val PRIVACY_POLICY_URL = "https://brickwares.app/privacy-policy"
private const val TERMS_OF_SERVICE_URL = "https://brickwares.app/terms-of-service"

/**
 * Opens [url] in a Chrome Custom Tab — an in-app browser overlay, so the user returns to Settings with
 * one tap (the tab's close/back button) instead of task-switching to a separate browser app. Falls
 * back to the default browser if no Custom Tabs provider is available.
 */
private fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching {
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline = rememberIsOnline()
    val context = LocalContext.current
    // Derive the active language from the live config, not retained VM state (the VM survives the
    // recreate, so its init-time snapshot would go stale after a switch).
    val currentLanguage = AppLanguage.fromTag(LocalConfiguration.current.locales[0].language)
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
        onOpenSetPassword = viewModel::onOpenSetPassword,
        onCloseSetPassword = viewModel::onCloseSetPassword,
        onSetPassword = viewModel::onSetPassword,
        // Persist the chosen language and recreate the activity so resources re-resolve to it.
        currentLanguage = currentLanguage,
        onLanguageChange = { lang ->
            if (lang != currentLanguage) {
                LocalePrefs.languageTag = lang.tag
                (context as? Activity)?.recreate()
            }
        },
        onCurrencyChange = viewModel::onCurrencyChange,
        onSetRetirement = viewModel::onSetRetirementAlerts,
        onNotificationsBlocked = viewModel::onNotificationsBlocked,
        onDismissNotificationsBlocked = viewModel::onDismissNotificationsBlocked,
        onOpenNotificationSettings = viewModel::onOpenNotificationSettings,
        onResumedFromNotificationSettings = viewModel::onResumedFromNotificationSettings,
        onToggleAnalytics = viewModel::onToggleAnalytics,
        onToggleChangelog = viewModel::onToggleChangelog,
        onSendTestNonFatal = viewModel::onSendTestNonFatal,
        onForceTestCrash = viewModel::onForceTestCrash,
        onExport = viewModel::onExportCollection,
        onImport = viewModel::onImportCollection,
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
    onOpenSetPassword: () -> Unit,
    onCloseSetPassword: () -> Unit,
    onSetPassword: (String) -> Unit,
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onCurrencyChange: (AppCurrency) -> Unit,
    onSetRetirement: (Boolean) -> Unit,
    onNotificationsBlocked: () -> Unit,
    onDismissNotificationsBlocked: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onResumedFromNotificationSettings: (Boolean) -> Unit,
    onToggleAnalytics: () -> Unit,
    onToggleChangelog: () -> Unit,
    onSendTestNonFatal: () -> Unit,
    onForceTestCrash: () -> Unit,
    onExport: (Uri, ContentResolver) -> Unit,
    onImport: (Uri, ContentResolver) -> Unit,
    onComingSoon: (String) -> Unit,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    val context = LocalContext.current
    val resolver = context.contentResolver
    // Storage Access Framework: the user chooses where to save / which file to load (no storage perms).
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { onExport(it, resolver) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it, resolver) }
    }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showNoInternet by remember { mutableStateOf(false) }
    // Completes an enable the user started via the blocked-notifications dialog's "Open settings":
    // when the app returns to the foreground with notifications now allowed, alerts switch on for
    // them (no second tap); if they came back without enabling anything, the pending intent is
    // dropped. Observes the PROCESS lifecycle — "came back from system settings" is an app-level
    // resume. (An observer added while already resumed gets a synthetic ON_RESUME; harmless, since
    // nothing is pending until "Open settings" is tapped.)
    val awaitingSettings by rememberUpdatedState(state.awaitingNotificationSettings)
    DisposableEffect(Unit) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingSettings) {
                onResumedFromNotificationSettings(notificationsAllowed(context))
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val exportFileName = "brickwares-backup-${LocalDate.now()}.csv"
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
                    // Google-only account → offer to add a password so email + password sign-in works too.
                    if (state.isGoogleOnly && isOnline) {
                        RowDivider()
                        NavRow(stringResource(R.string.settings_set_password), onClick = onOpenSetPassword)
                    }
                    RowDivider()
                    NavRow(stringResource(R.string.settings_delete_account), onClick = onRequestDelete, danger = true)
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
                    selectedLabel = currentLanguage.label,
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
                // VND is a converted display (USD is the catalog's canonical currency), so tell the user
                // the retail figures aren't native prices.
                if (state.currency == AppCurrency.VND) {
                    Text(
                        stringResource(R.string.settings_currency_vnd_note),
                        style = BwType.body.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic),
                        color = colors.textMuted,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
                    )
                }
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
            // Export/Import act on the signed-in account's collection, so they're disabled (greyed,
            // untappable) while signed out.
            Section(stringResource(R.string.settings_section_data)) {
                NavRow(stringResource(R.string.settings_export_csv), onClick = { exportLauncher.launch(exportFileName) }, enabled = state.isLoggedIn)
                RowDivider()
                // Import overwrites the collection → confirm first, then open the file picker.
                NavRow(stringResource(R.string.settings_import_csv), onClick = { showImportConfirm = true }, enabled = state.isLoggedIn)
            }

            // ---- Notifications ----
            // Retirement alerts watch the account's wishlist, so the whole section is hidden while
            // signed out (a setting the user can't act on is just noise). Android 13+ only shows
            // notifications once POST_NOTIFICATIONS is granted, so switching the alerts ON asks for it
            // (the toggle persists either way; posting is guarded if it's denied).
            if (state.isLoggedIn) {
                // The switch is controlled by the persisted pref, so it only turns ON once the Android
                // 13+ POST_NOTIFICATIONS prompt is GRANTED. A denial — or a request the system won't
                // show (permanently denied: the callback returns false with no dialog) — leaves it off.
                val notifPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    when {
                        granted -> onSetRetirement(true)
                        // Denied with NO dialog shown (permanently denied): rationale stays false. A fresh
                        // "No" flips rationale to true — that user just answered, so don't nag them.
                        (context as? Activity)?.let {
                            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                        } == true -> onNotificationsBlocked()
                    }
                }
                Section(stringResource(R.string.settings_section_notifications)) {
                    ToggleRow(
                        stringResource(R.string.settings_retirement_alerts),
                        checked = state.retirementAlerts,
                        onToggle = {
                            when {
                                state.retirementAlerts -> onSetRetirement(false)
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                NotificationManagerCompat.from(context).areNotificationsEnabled() -> onSetRetirement(true)
                                // Notifications are off for the app in system settings and there's no
                                // prompt to show → stay off, and explain + offer "Open settings".
                                else -> onNotificationsBlocked()
                            }
                        },
                        description = stringResource(R.string.settings_retirement_alerts_desc),
                    )
                }
            }

            // ---- Privacy ----
            Section(stringResource(R.string.settings_section_privacy)) {
                NavRow(stringResource(R.string.settings_privacy_policy), onClick = { openUrl(context, PRIVACY_POLICY_URL) })
                RowDivider()
                NavRow(stringResource(R.string.settings_terms), onClick = { openUrl(context, TERMS_OF_SERVICE_URL) })
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

            // ---- Developer (debug builds only) ----
            // Crashlytics setup checks: a test non-fatal (Timber → Crashlytics, no crash) and the Firebase
            // docs' "force a test crash" (uncaught exception; the report uploads on the NEXT launch).
            // Hidden in release. Crashlytics is always on, so these need no toggle.
            if (BuildConfig.DEBUG) {
                Section(stringResource(R.string.settings_section_developer)) {
                    Text(
                        stringResource(R.string.settings_developer_desc),
                        style = BwType.body.copy(fontSize = 11.sp),
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    NavRow(stringResource(R.string.settings_test_non_fatal), onClick = onSendTestNonFatal)
                    RowDivider()
                    NavRow(stringResource(R.string.settings_test_crash), onClick = onForceTestCrash, danger = true)
                }
            }
        }

        if (state.showAvatarPicker) {
            AvatarPickerDialog(
                selected = state.avatar,
                onSelect = onSelectAvatar,
                onDismiss = onCloseAvatarPicker,
            )
        }

        if (state.showSetPassword) {
            SetPasswordDialog(onConfirm = onSetPassword, onDismiss = onCloseSetPassword)
        }

        if (state.showDeleteConfirm) {
            DeleteAccountDialog(onConfirm = onConfirmDelete, onDismiss = onCancelDelete)
        }

        if (showImportConfirm) {
            ImportCollectionDialog(
                // Import overwrites then syncs, so it needs a connection — block it while offline.
                onConfirm = {
                    showImportConfirm = false
                    if (isOnline) importLauncher.launch(arrayOf("*/*")) else showNoInternet = true
                },
                onDismiss = { showImportConfirm = false },
            )
        }

        if (showNoInternet) {
            NoInternetDialog(onDismiss = { showNoInternet = false })
        }

        // Retirement alerts can't be enabled (notifications blocked, or the permission prompt won't
        // show anymore): explain, and deep-link to the app's notification settings.
        if (state.showNotificationsBlocked) {
            NotificationsBlockedDialog(
                onOpenSettings = {
                    onOpenNotificationSettings() // dismiss + remember to finish the enable on return
                    openAppNotificationSettings(context)
                },
                onDismiss = onDismissNotificationsBlocked,
            )
        }

        // Import runs an overwrite + immediate sync; lock the UI behind a non-dismissable loading modal
        // until it completes (can't back out or navigate away mid-import).
        if (state.importing) {
            ImportLoadingDialog()
        }

        BwToast(message = state.toastMessage?.resolve(), onDismiss = onToastShown)
    }
}

@Composable
private fun SetPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = BwTheme.colors
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.brandYellow,
        unfocusedBorderColor = colors.borderStrong,
        focusedTextColor = colors.text,
        unfocusedTextColor = colors.text,
        cursorColor = colors.brandYellow,
    )
    val eye: @Composable () -> Unit = {
        IconButton(onClick = { visible = !visible }) {
            Icon(
                painter = painterResource(if (visible) R.drawable.ic_bw_eye_off else R.drawable.ic_bw_eye),
                contentDescription = stringResource(R.string.login_toggle_password),
                tint = colors.textMuted,
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.set_password_title), style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.set_password_body), style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    placeholder = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = eye,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColors,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    placeholder = { Text(stringResource(R.string.login_confirm_password)) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = eye,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColors,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(it, 6), style = BwType.body.copy(fontSize = 12.sp), color = colors.error)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinePill(stringResource(R.string.action_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                    Pill(
                        text = stringResource(R.string.action_save),
                        filled = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when {
                                password.length < 6 -> error = R.string.login_err_password_short
                                password != confirm -> error = R.string.login_err_password_mismatch
                                else -> onConfirm(password)
                            }
                        },
                    )
                }
            }
        }
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

/**
 * Confirmation modal for the destructive "Delete account" action. Replaces the old inline
 * expand-in-place row so the irreversible choice is a deliberate, focused decision (dim scrim +
 * centered card) rather than something that quietly unfolds under the tapped row. State + handlers
 * live in the VM (showDeleteConfirm / onConfirmDelete / onCancelDelete); this only renders them.
 */
@Composable
private fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = BwTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.settings_delete_confirm_title),
                    style = BwType.cardTitle,
                    color = colors.error,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_delete_confirm_body),
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinePill(stringResource(R.string.action_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                    DangerPill(stringResource(R.string.action_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Warns that importing replaces the current collection before the file picker opens. Confirming opens
 * the picker (in the caller); the actual overwrite runs on the picked file.
 */
@Composable
private fun ImportCollectionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = BwTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.settings_import_confirm_title), style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_import_confirm_body),
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinePill(stringResource(R.string.action_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                    Pill(text = stringResource(R.string.action_import), filled = true, onClick = onConfirm, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Shown when the user confirms an import while offline — import needs a connection to sync. */
@Composable
private fun NoInternetDialog(onDismiss: () -> Unit) {
    val colors = BwTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.settings_no_internet_title), style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_no_internet_body),
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Pill(text = stringResource(R.string.action_ok), filled = true, onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * Retirement alerts can't be enabled — notifications are off for the app, or the permission prompt
 * won't show anymore (permanently denied). Explains why and deep-links to the fix, instead of leaving
 * the toggle tap a silent no-op.
 */
@Composable
private fun NotificationsBlockedDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val colors = BwTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.settings_notifications_blocked_title), style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_notifications_disabled),
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinePill(stringResource(R.string.action_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                    Pill(text = stringResource(R.string.settings_open_notification_settings), filled = true, onClick = onOpenSettings, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Whether the app may post notifications right now (Android 13+ runtime grant, and not disabled in device settings). */
private fun notificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** Deep-links to the app's notification settings (Android 8+; the app-details page before that). */
private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Non-dismissable loading modal shown while a CSV import (overwrite + sync) runs. */
@Composable
private fun ImportLoadingDialog() {
    val colors = BwTheme.colors
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.card) {
            Row(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = colors.brandYellow)
                Text(
                    stringResource(R.string.settings_importing),
                    style = BwType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                )
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
private fun NavRow(label: String, onClick: () -> Unit, danger: Boolean = false, enabled: Boolean = true) {
    val colors = BwTheme.colors
    // Disabled → greyed out and untappable (e.g. Export/Import while signed out).
    val labelColor = when {
        !enabled -> colors.textFaint
        danger -> colors.error
        else -> colors.text
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = labelColor)
        Text("›", style = BwType.cardTitle.copy(fontSize = 18.sp), color = if (enabled && danger) colors.error else colors.textFaint)
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
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = colors.card,
                tonalElevation = 0.dp, // kill Material's tinted-surface (lavender) overlay
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, colors.borderSoft),
            ) {
                options.forEach { (value, optionLabel) ->
                    val isSelected = optionLabel == selectedLabel
                    DropdownMenuItem(
                        text = {
                            Text(
                                optionLabel,
                                style = BwType.body.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (isSelected) colors.brandYellow else colors.text,
                            )
                        },
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
private fun Pill(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BwTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            // Theme-stable dark fill (not colors.text, which flips to near-white in dark and left the
            // yellow label unreadable) so the pill stays dark with yellow text in both themes.
            .background(if (filled) colors.onYellow else colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
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
