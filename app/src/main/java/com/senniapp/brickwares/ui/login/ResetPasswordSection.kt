package com.senniapp.brickwares.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.repository.MIN_PASSWORD_LENGTH
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/**
 * The forgot-password steps, rendered inside the login card's Column in place of the sign-in form
 * (see [ResetStep]): **email** → **6-digit code** (the same [OtpCodeField] as sign-up) → **new password**.
 * Errors / info and the offline note are drawn by [LoginScreen] below this, as for the other steps.
 *
 * The last step has no Back: verifying the code already signed the user in, so the only ways out are
 * saving a password or closing the modal (they stay signed in, with the old password).
 */
@Composable
internal fun ResetPasswordSection(
    state: LoginUiState,
    viewModel: LoginViewModel,
    enabled: Boolean,
    busy: Boolean,
    resendSecondsLeft: Int,
    fieldColors: TextFieldColors,
    yellowButton: ButtonColors,
) {
    val colors = BwTheme.colors

    @Composable
    fun Lead(text: String) = Text(
        text,
        style = BwType.body.copy(fontSize = 13.sp),
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
    )

    @Composable
    fun Primary(label: String, onClick: () -> Unit) = Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(50),
        colors = yellowButton,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.onYellow)
        } else {
            Text(label, style = BwType.pill)
        }
    }

    @Composable
    fun Back() = Text(
        stringResource(R.string.login_back),
        style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        color = colors.textSecondary,
        modifier = Modifier.clickable(enabled = !busy) { viewModel.onBackFromReset() },
    )

    when (state.reset) {
        ResetStep.EMAIL -> {
            Lead(stringResource(R.string.login_reset_body))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                placeholder = { Text(stringResource(R.string.login_email)) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.onSendResetCode() }),
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Primary(stringResource(R.string.login_reset_send)) { viewModel.onSendResetCode() }
            Spacer(Modifier.height(14.dp))
            Back()
        }

        ResetStep.CODE -> {
            // Conditional wording on purpose: the server never says whether the address has an account.
            Lead(stringResource(R.string.login_reset_sent, state.pendingEmail))
            Spacer(Modifier.height(16.dp))
            OtpCodeField(
                code = state.code,
                onCodeChange = viewModel::onCodeChange,
                enabled = !busy,
                onImeDone = { viewModel.onVerifyResetCode() },
            )
            Spacer(Modifier.height(18.dp))
            Primary(stringResource(R.string.login_verify_action)) { viewModel.onVerifyResetCode() }
            Spacer(Modifier.height(14.dp))
            Row {
                Text(
                    stringResource(R.string.login_resend_prompt),
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.textMuted,
                )
                Spacer(Modifier.width(4.dp))
                if (resendSecondsLeft > 0) {
                    Text(
                        stringResource(R.string.login_resend_in, resendSecondsLeft),
                        style = BwType.body.copy(fontSize = 13.sp),
                        color = colors.textFaint,
                    )
                } else {
                    Text(
                        stringResource(R.string.login_resend_code),
                        style = BwType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = colors.brandYellow,
                        modifier = Modifier.clickable(enabled = enabled) { viewModel.onSendResetCode() },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Back()
        }

        ResetStep.NEW_PASSWORD -> {
            var passwordVisible by remember { mutableStateOf(false) }
            var confirmVisible by remember { mutableStateOf(false) }

            @Composable
            fun Eye(visible: Boolean, onToggle: () -> Unit) = IconButton(onClick = onToggle) {
                Icon(
                    painter = painterResource(if (visible) R.drawable.ic_bw_eye_off else R.drawable.ic_bw_eye),
                    contentDescription = stringResource(R.string.login_toggle_password),
                    tint = colors.textMuted,
                )
            }

            Lead(stringResource(R.string.login_reset_new_body))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                placeholder = { Text(stringResource(R.string.login_password_new, MIN_PASSWORD_LENGTH)) },
                singleLine = true,
                enabled = !busy,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { Eye(passwordVisible) { passwordVisible = !passwordVisible } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                placeholder = { Text(stringResource(R.string.login_confirm_password)) },
                singleLine = true,
                enabled = !busy,
                visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { Eye(confirmVisible) { confirmVisible = !confirmVisible } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.onSubmitNewPassword() }),
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Primary(stringResource(R.string.login_reset_save)) { viewModel.onSubmitNewPassword() }
        }

        ResetStep.NONE -> Unit
    }
}
