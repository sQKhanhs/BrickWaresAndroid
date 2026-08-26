package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/** Live signed-in state, for gating write features (add/edit/delete need an account). */
@Composable
fun rememberIsLoggedIn(): Boolean {
    val state by AuthRepository.authState.collectAsStateWithLifecycle(initialValue = AuthState.Loading)
    return state is AuthState.SignedIn
}

/**
 * The logged-out placeholder shown where owned-item content would be (Home theme card, the
 * Collection/Wishlist/Sales lists): a lock + message + a "Sign In" button that opens the sign-in
 * overlay. Only signed-in users can add/edit/delete; logged-out users can still browse + search.
 */
@Composable
fun SignInPromptCard(
    message: String,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BwTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bw_lock),
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = BwType.body.copy(fontSize = 13.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onSignIn,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.brandYellow,
                contentColor = colors.onYellow,
            ),
        ) {
            Text("Sign In", style = BwType.pill)
        }
    }
}
