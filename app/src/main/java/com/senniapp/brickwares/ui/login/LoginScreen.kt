package com.senniapp.brickwares.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.components.rememberIsOnline
import com.senniapp.brickwares.ui.components.resolve
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/**
 * On-demand sign-in **modal** (a mini login page, not a full screen): "Continue with Google" plus an
 * email/password sign-in & sign-up form (for users without a Google account). Both paths need
 * network, so the actions are disabled with a note while offline. On a successful session the app
 * shell dismisses this overlay and unlocks the write features.
 */
@Composable
fun LoginScreen(
    onDismiss: () -> Unit = {},
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isOnline = rememberIsOnline()
    val colors = BwTheme.colors
    val busy = state.signingIn
    val enabled = isOnline && !busy
    val signUp = state.mode == LoginMode.SIGN_UP

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.card,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("Brick")
                            withStyle(SpanStyle(color = colors.brandYellow)) { append("Wares") }
                        },
                        style = BwType.wordmark.copy(fontSize = 26.sp),
                        color = colors.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(if (signUp) R.string.login_create_account else R.string.login_welcome_back),
                        style = BwType.body.copy(fontSize = 13.sp),
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(22.dp))

                    OutlinedButton(
                        onClick = { viewModel.onGoogleSignIn(context) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(stringResource(R.string.login_google), style = BwType.pill, color = colors.text)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.borderSoft)
                        Text("  ${stringResource(R.string.login_or)}  ", style = BwType.body.copy(fontSize = 12.sp), color = colors.textMuted)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.borderSoft)
                    }
                    Spacer(Modifier.height(16.dp))

                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.brandYellow,
                        unfocusedBorderColor = colors.borderStrong,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        cursorColor = colors.brandYellow,
                    )
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        placeholder = { Text(stringResource(R.string.login_email)) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        placeholder = { Text(stringResource(R.string.login_password)) },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (signUp) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            placeholder = { Text(stringResource(R.string.login_confirm_password)) },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = fieldColors,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { viewModel.onEmailSubmit() },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.brandYellow,
                            contentColor = colors.onYellow,
                            disabledContainerColor = colors.track,
                            disabledContentColor = colors.textMuted,
                        ),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.onYellow)
                        } else {
                            Text(stringResource(if (signUp) R.string.login_signup_action else R.string.action_sign_in), style = BwType.pill)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row {
                        Text(
                            stringResource(if (signUp) R.string.login_have_account else R.string.login_no_account),
                            style = BwType.body.copy(fontSize = 13.sp),
                            color = colors.textMuted,
                        )
                        Text(
                            stringResource(if (signUp) R.string.settings_sign_in else R.string.login_signup_link),
                            style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            color = colors.brandYellow,
                            modifier = Modifier.clickable { viewModel.onSwitchMode() },
                        )
                    }

                    state.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it.resolve(), style = BwType.body.copy(fontSize = 13.sp), color = colors.error, textAlign = TextAlign.Center)
                    }
                    state.info?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it.resolve(), style = BwType.body.copy(fontSize = 13.sp), color = colors.textSecondary, textAlign = TextAlign.Center)
                    }
                    if (!isOnline) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.login_offline),
                            style = BwType.body.copy(fontSize = 13.sp),
                            color = colors.textFaint,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // Close (drawn after the Column so it stays on top and stays tappable).
                Text(
                    "✕",
                    style = BwType.cardTitle.copy(fontSize = 20.sp),
                    color = colors.textMuted,
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clickable(onClick = onDismiss),
                )
            }
        }
    }
}
