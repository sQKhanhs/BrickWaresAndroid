package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType

/** Bundled "sign in" illustration (waving minifig with a phone), shown in the logged-out prompt. */
private const val SIGN_IN_ART = "file:///android_asset/sign_in.png"

/** Live signed-in state, for gating write features (add/edit/delete need an account). */
@Composable
fun rememberIsLoggedIn(): Boolean {
    val state by AuthRepository.authState.collectAsStateWithLifecycle(initialValue = AuthState.Loading)
    return state is AuthState.SignedIn
}

/**
 * The logged-out placeholder shown where owned-item content would be (Home theme card, the
 * Collection/Wishlist/Sales lists): a centered **Sign In** button, then the message and the sign-in
 * illustration below it. Only signed-in users can add/edit/delete; logged-out users still browse/search.
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
            .padding(top = 20.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(0.5f).height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.brandYellow,
                contentColor = colors.onYellow,
            ),
        ) {
            Text("Sign In", style = BwType.pill)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            message,
            style = BwType.cardTitle,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        AsyncImage(
            model = SIGN_IN_ART,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(160.dp).aspectRatio(450f / 601f),
        )
    }
}
