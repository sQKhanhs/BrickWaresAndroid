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
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.OtpVerifyResult
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Signed-in user, as the app cares about it (derived from the Supabase session). */
data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    /** True when the account has a Google identity but no email/password one yet — so we can offer
     *  to set a password (adding email+password sign-in to a Google-only account). */
    val isGoogleOnly: Boolean = false,
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

    /** Sign-in was rejected because the email/password account still needs email confirmation. */
    data object EmailNotConfirmed : SignInResult

    /** Sign-in failed: wrong email or password. */
    data object InvalidCredentials : SignInResult

    /** Sign-up failed: an account with this email already exists. */
    data object EmailAlreadyRegistered : SignInResult

    /** The auth request was rate-limited (too many attempts / emails). */
    data object TooManyRequests : SignInResult

    /** Any other failure. The [message] is for logging, NOT display — the UI shows a generic string. */
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
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            // An unconfirmed account → route the UI back to the code step, not a dead-end error.
            AuthErrorCode.EmailNotConfirmed -> SignInResult.EmailNotConfirmed
            AuthErrorCode.InvalidCredentials -> SignInResult.InvalidCredentials
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> SignInResult.TooManyRequests
            else -> SignInResult.Error(e.errorDescription)
        }
    } catch (e: Exception) {
        SignInResult.Error(e.message ?: "Couldn't sign in")
    }

    /**
     * Email + password sign-up. [languageTag] ("en"/"vi") is the app's current language, stamped into
     * user metadata so the confirmation email renders in that language (the template branches on
     * `{{ .Data.lang }}`; it persists for resends too). If the project auto-confirms (local dev), a
     * session is created and this returns [SignInResult.Success]; if email confirmation is required
     * (prod default), no session yet → [SignInResult.EmailConfirmationRequired].
     */
    suspend fun signUpWithEmail(email: String, password: String, languageTag: String): SignInResult = try {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject { put("lang", languageTag) }
        }
        if (client.auth.currentUserOrNull() != null) SignInResult.Success
        else SignInResult.EmailConfirmationRequired
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> SignInResult.EmailAlreadyRegistered
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> SignInResult.TooManyRequests
            else -> SignInResult.Error(e.errorDescription)
        }
    } catch (e: Exception) {
        SignInResult.Error(e.message ?: "Couldn't create account")
    }

    /**
     * Confirms a new email/password sign-up with the 6-digit code from the confirmation email
     * (OTP flow — mobile has no working web page for the emailed confirmation link). On success the
     * session is imported automatically, so the app's [authState] flips to SignedIn and routes in.
     */
    suspend fun verifySignUpOtp(email: String, code: String): SignInResult = try {
        when (client.auth.verifyEmailOtp(OtpType.Email.SIGNUP, email.trim(), code.trim())) {
            is OtpVerifyResult.Authenticated -> SignInResult.Success
            OtpVerifyResult.VerifiedNoSession -> SignInResult.Success
        }
    } catch (e: Exception) {
        SignInResult.Error(e.message ?: "Couldn't verify the code")
    }

    /** Re-sends the sign-up confirmation code to [email] (e.g. the first one expired or was missed). */
    suspend fun resendSignUpCode(email: String): SignInResult = try {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
        SignInResult.Success
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit -> SignInResult.TooManyRequests
            else -> SignInResult.Error(e.errorDescription)
        }
    } catch (e: Exception) {
        SignInResult.Error(e.message ?: "Couldn't resend the code")
    }

    /**
     * Sets (or changes) the current user's password. For a Google-only account this adds an
     * email/password credential, so they can afterwards sign in with email + password too. Requires a
     * live session (the user is already authenticated, so no takeover risk — unlike signing up again).
     */
    suspend fun setPassword(newPassword: String): SignInResult = try {
        client.auth.updateUser { password = newPassword }
        SignInResult.Success
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> SignInResult.TooManyRequests
            else -> SignInResult.Error(e.errorDescription)
        }
    } catch (e: Exception) {
        SignInResult.Error(e.message ?: "Couldn't set the password")
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
    val providers = identities.orEmpty().map { it.provider }
    return AuthUser(
        id = id,
        email = email,
        displayName = name,
        avatarUrl = meta.string("avatar_url") ?: meta.string("picture"),
        isGoogleOnly = "google" in providers && "email" !in providers,
    )
}

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.jsonPrimitive?.contentOrNull
