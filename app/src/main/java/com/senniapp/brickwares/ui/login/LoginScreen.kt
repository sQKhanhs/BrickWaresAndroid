package com.senniapp.brickwares.ui.login

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
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senniapp.brickwares.ui.components.rememberIsOnline
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/**
 * App-open login page (Google sign-in + "stay signed in"). Shown by [com.senniapp.brickwares.MainActivity]
 * when there's no active session; on success the AuthGate routes to the main app. Sign-in needs
 * network, so the button is disabled with a note while offline.
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isOnline = rememberIsOnline()
    val colors = BwTheme.colors

    Box(modifier = modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = buildAnnotatedString {
                    append("Brick")
                    withStyle(SpanStyle(color = colors.brandYellow)) { append("Wares") }
                },
                style = BwType.wordmark.copy(fontSize = 34.sp),
                color = colors.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Manage your LEGO collection",
                style = BwType.body,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { viewModel.onSignIn(context) },
                enabled = isOnline && !state.signingIn,
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandYellow,
                    contentColor = colors.onYellow,
                    disabledContainerColor = colors.track,
                    disabledContentColor = colors.textMuted,
                ),
            ) {
                if (state.signingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.onYellow,
                    )
                } else {
                    Text("Sign in with Google", style = BwType.pill)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { viewModel.onToggleStaySignedIn() },
            ) {
                Checkbox(
                    checked = state.staySignedIn,
                    onCheckedChange = { viewModel.onToggleStaySignedIn() },
                    colors = CheckboxDefaults.colors(checkedColor = colors.brandYellow, checkmarkColor = colors.onYellow),
                )
                Text("Stay signed in", style = BwType.body, color = colors.textSecondary)
            }

            if (!isOnline) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "No internet connection — connect to sign in.",
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.textFaint,
                    textAlign = TextAlign.Center,
                )
            }
            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    style = BwType.body.copy(fontSize = 13.sp),
                    color = colors.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
