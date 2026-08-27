package com.senniapp.brickwares.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.senniapp.brickwares.BuildConfig
import com.senniapp.brickwares.data.remote.SupabaseClientProvider
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Signed-in user, as the app cares about it (derived from the Supabase session). */
data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
)

/** Coarse auth state the UI observes. [Loading] covers the brief session-restore on launch. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/** Result of a sign-in attempt, so the UI can show the right message. */
sealed interface SignInResult {
    data object Success : SignInResult
    data object Cancelled : SignInResult

    /** No usable Google credential on the device (no account, or Play Services unavailable). */
    data object NoCredential : SignInResult

    /** Email sign-up succeeded but the address must be confirmed before the first sign-in. */
    data object EmailConfirmationRequired : SignInResult
    data class Error(val message: String) : SignInResult
}

/**
 * Owns Google sign-in and exposes the current session as a [Flow]. Uses Android's Credential Manager
 * to obtain a Google ID token, then hands it to Supabase Auth ([IDToken] with the [Google] provider).
 *
 * Single instance (no DI yet) sharing the app-wide [SupabaseClientProvider.client]; the Auth plugin
 * persists + restores the session across launches. This is the seam future user-data repositories key
 * off of — and where the account-switch guard (last_account_id, Arch Decision 10) will live.
 */
object AuthRepository {

    private val client get() = SupabaseClientProvider.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The live auth state, held as a hot [StateFlow] shared eagerly from app start. Being hot means it
     * always has a current [value], so UI collectors (e.g. `rememberIsLoggedIn`) start from the real
     * state instead of flashing [Loading]/logged-out for a frame on every recomposition.
     */
    val authState: StateFlow<AuthState> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated ->
                status.session.user?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.SignedOut
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            is SessionStatus.RefreshFailure -> AuthState.SignedOut
            is SessionStatus.Initializing -> AuthState.Loading
        }
    }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    /**
     * Launches the native Google account picker and establishes a Supabase session from the returned
     * ID token. Requires an Activity [context] (Credential Manager anchors its UI to the activity).
     * Nonce is intentionally omitted — the Supabase Google provider runs with skip-nonce-check
     * (required for the local dev stack; set on the prod dashboard too).
     */
    suspend fun signInWithGoogle(context: Context): SignInResult {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val idToken = try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                return SignInResult.Error("Unexpected credential type from Google")
            }
        } catch (e: GetCredentialCancellationException) {
            return SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            return SignInResult.NoCredential
        } catch (e: GetCredentialException) {
            return SignInResult.Error(e.message ?: "Google sign-in failed")
        }

        return try {
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }
            SignInResult.Success
        } catch (e: Exception) {
            SignInResult.Error(e.message ?: "Couldn't complete sign-in")
        }
    }

    /** Email + password sign-in (for users without a Google account). */
    suspend fun signInWithEmail(email: String, password: String): SignInResult = try {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        SignInResult.Success
    } catch (e: Exception) {
        SignInResult.Error(e.message ?: "Couldn't sign in")
    }

    /**
     * Email + password sign-up. If the project auto-confirms (local dev), a session is created and
     * this returns [SignInResult.Success]; if email confirmation is required (prod default), no
     * session yet → [SignInResult.EmailConfirmationRequired].
     */
    suspend fun signUpWithEmail(email: String, password: String): SignInResult = try {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        if (client.auth.currentUserOrNull() != null) SignInResult.Success
        else SignInResult.EmailConfirmationRequired
    } catch (e: Exception) {
        SignInResult.Error(e.message ?: "Couldn't create account")
    }

    /** Clears the on-device session (LOCAL scope → no network; the server token just expires). */
    suspend fun signOut() {
        runCatching { client.auth.signOut(SignOutScope.LOCAL) }
    }
}

/** Maps a Supabase user to [AuthUser], pulling Google's name/avatar out of the user metadata. */
private fun io.github.jan.supabase.auth.user.UserInfo.toAuthUser(): AuthUser {
    val meta = userMetadata
    val email = this.email.orEmpty()
    val name = meta.string("full_name")
        ?: meta.string("name")
        ?: email.substringBefore("@").ifBlank { "BrickWares user" }
    return AuthUser(
        id = id,
        email = email,
        displayName = name,
        avatarUrl = meta.string("avatar_url") ?: meta.string("picture"),
    )
}

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.jsonPrimitive?.contentOrNull
