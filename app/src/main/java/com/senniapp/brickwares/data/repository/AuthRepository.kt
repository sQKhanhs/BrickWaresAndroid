package com.senniapp.brickwares.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.senniapp.brickwares.data.local.AccountPrefs
import com.senniapp.brickwares.BuildConfig
import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.local.ThemeFavoritesPrefs
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
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.UUID
import timber.log.Timber

/**
 * Minimum length for a NEW password (sign-up, set-password). Must match the server: supabase/config.toml
 * `minimum_password_length` locally and the prod dashboard (Authentication → Sign In / Providers → Email).
 * Deliberately NOT applied to sign-in — an account created under an older, shorter rule must still be
 * able to log in. Length over composition rules (NIST 800-63B): no forced symbols/digits.
 */
const val MIN_PASSWORD_LENGTH = 8

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

    /** GoTrue rejected (or required and didn't get) the Turnstile `captcha_token` — expired, reused, or
     *  the app has no site key while the project enforces captcha. Ask the user to try again. */
    data object CaptchaFailed : SignInResult

    /** The server refused a new password as too weak/short (its rule is stricter than the app assumed). */
    data object WeakPassword : SignInResult

    /**
     * Password change refused because the session is older than 24 h and the project has
     * `secure_password_change` on (a stolen long-lived token must not be able to set a password).
     * The user has to sign in again first; a nonce-by-email reauth flow is a possible follow-up.
     */
    data object ReauthenticationNeeded : SignInResult

    /** Sign-up failed: an account with this email already exists. */
    data object EmailAlreadyRegistered : SignInResult

    /** Set-password: the account already had this password (nothing to add — it just wasn't known locally). */
    data object PasswordAlreadySet : SignInResult

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
    private const val TAG = "Auth"

    /**
     * An Auth error the UI has no specific message for. Log the server's error CODE + HTTP status so a
     * "Something went wrong" is diagnosable from Logcat/Crashlytics (2026-09-19: a sign-up failed with
     * nothing in the log). The description is debug-only — GoTrue echoes the email address in some.
     */
    private fun rejected(call: String, e: AuthRestException): SignInResult.Error {
        Timber.tag(TAG).w("%s rejected: code=%s status=%d", call, e.error, e.statusCode)
        if (BuildConfig.DEBUG) Timber.tag(TAG).d("%s detail: %s", call, e.errorDescription)
        return SignInResult.Error(e.errorDescription)
    }

    /** Non-Auth failure (network, timeout, parsing) on an auth call — logged with its stack. */
    private fun failed(call: String, e: Exception, fallback: String): SignInResult.Error {
        Timber.tag(TAG).w(e, "%s failed", call)
        return SignInResult.Error(e.message ?: fallback)
    }

    private val client get() = SupabaseClientProvider.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bumped when we learn (from the server, or a set-password result) that an account has a password. */
    private val passwordRevision = MutableStateFlow(0)

    /** User ids already asked `has_password()` this process (one round trip per account). */
    private val passwordChecked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * The live auth state, held as a hot [StateFlow] shared eagerly from app start. Being hot means it
     * always has a current [value], so UI collectors (e.g. `rememberIsLoggedIn`) start from the real
     * state instead of flashing [Loading]/logged-out for a frame on every recomposition. Combined with
     * [passwordRevision] so learning "this account has a password" re-derives [AuthUser.isGoogleOnly].
     */
    val authState: StateFlow<AuthState> = combine(client.auth.sessionStatus, passwordRevision) { status, _ ->
        when (status) {
            is SessionStatus.Authenticated ->
                status.session.user?.let { user ->
                    val mapped = user.toAuthUser()
                    // A Google-only account may still have a password (set from another device, or
                    // before a reinstall) — ask the server once; when it does, the flag flips and the
                    // state re-emits with isGoogleOnly = false ("Set password" hides).
                    if (mapped.isGoogleOnly) checkPasswordOnServer(mapped.id)
                    AuthState.SignedIn(mapped)
                } ?: AuthState.SignedOut
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            is SessionStatus.RefreshFailure -> AuthState.SignedOut
            is SessionStatus.Initializing -> AuthState.Loading
        }
    }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    private fun checkPasswordOnServer(userId: String) {
        if (!passwordChecked.add(userId)) return
        scope.launch {
            runCatching { client.postgrest.rpc("has_password").decodeAs<Boolean>() }
                .onSuccess { has -> if (has) markPasswordSet(userId) }
                .onFailure { e ->
                    passwordChecked.remove(userId) // let a later emission retry (e.g. offline now)
                    Timber.tag(TAG).w(e, "has_password check failed")
                }
        }
    }

    /** Records that [userId] has a password and re-emits [authState] so the UI reflects it. */
    private fun markPasswordSet(userId: String) {
        AccountPrefs.markPasswordSet(userId)
        passwordRevision.value = passwordRevision.value + 1
    }

    /**
     * Launches the native Google account picker and establishes a Supabase session from the returned
     * ID token. Requires an Activity [context] (Credential Manager anchors its UI to the activity).
     *
     * The ID token is **nonce-bound** (OIDC replay protection): Google gets the SHA-256 of a fresh
     * random nonce and embeds it in the token's `nonce` claim; Supabase gets the raw nonce, re-hashes
     * it and compares — so a token captured anywhere else (logs, another surface sharing this client
     * id, e.g. the future iOS app) can't be replayed to mint a session. Prod enforces this ("Skip nonce
     * checks" OFF on the dashboard). The local stack keeps `skip_nonce_check = true` for dev
     * convenience (see `supabase/config.toml`), so a nonce bug here only shows against prod — verify
     * once with a prodDebug sign-in after touching this.
     */
    suspend fun signInWithGoogle(context: Context): SignInResult {
        val rawNonce = UUID.randomUUID().toString()
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(sha256Hex(rawNonce))
            .build()
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
                nonce = rawNonce // raw; Supabase hashes it and compares with the token's claim
            }
            SignInResult.Success
        } catch (e: Exception) {
            SignInResult.Error(e.message ?: "Couldn't complete sign-in")
        }
    }

    /**
     * Email + password sign-in (for users without a Google account). [captchaToken] is the Turnstile
     * token from `CaptchaGate` (null when the gate is disabled) — GoTrue requires one on the password
     * grant once captcha is enabled on the project (handoff §0ar).
     */
    suspend fun signInWithEmail(email: String, password: String, captchaToken: String? = null): SignInResult = try {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
            this.captchaToken = captchaToken
        }
        SignInResult.Success
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            // An unconfirmed account → route the UI back to the code step, not a dead-end error.
            AuthErrorCode.EmailNotConfirmed -> SignInResult.EmailNotConfirmed
            AuthErrorCode.InvalidCredentials -> SignInResult.InvalidCredentials
            AuthErrorCode.CaptchaFailed -> SignInResult.CaptchaFailed
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> SignInResult.TooManyRequests
            else -> rejected("signInWithEmail", e)
        }
    } catch (e: Exception) {
        failed("signInWithEmail", e, "Couldn't sign in")
    }

    /**
     * Email + password sign-up. [languageTag] ("en"/"vi") is the app's current language, stamped into
     * user metadata so the confirmation email renders in that language (the template branches on
     * `{{ .Data.lang }}`; it persists for resends too). If the project auto-confirms (local dev), a
     * session is created and this returns [SignInResult.Success]; if email confirmation is required
     * (prod default), no session yet → [SignInResult.EmailConfirmationRequired].
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        languageTag: String,
        captchaToken: String? = null,
    ): SignInResult = try {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            this.captchaToken = captchaToken
            data = buildJsonObject { put("lang", languageTag) }
        }
        if (client.auth.currentUserOrNull() != null) SignInResult.Success
        else SignInResult.EmailConfirmationRequired
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> SignInResult.EmailAlreadyRegistered
            AuthErrorCode.CaptchaFailed -> SignInResult.CaptchaFailed
            AuthErrorCode.WeakPassword -> SignInResult.WeakPassword
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> SignInResult.TooManyRequests
            else -> rejected("signUpWithEmail", e)
        }
    } catch (e: Exception) {
        failed("signUpWithEmail", e, "Couldn't create account")
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

    /** Re-sends the sign-up confirmation code to [email] (e.g. the first one expired or was missed).
     *  `/resend` is captcha-protected too, so it takes the same [captchaToken] as sign-up. */
    suspend fun resendSignUpCode(email: String, captchaToken: String? = null): SignInResult = try {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email.trim(), captchaToken = captchaToken)
        SignInResult.Success
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            AuthErrorCode.CaptchaFailed -> SignInResult.CaptchaFailed
            AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit -> SignInResult.TooManyRequests
            else -> rejected("resendSignUpCode", e)
        }
    } catch (e: Exception) {
        failed("resendSignUpCode", e, "Couldn't resend the code")
    }

    /**
     * Forgot password, step 1: email a 6-digit recovery code to [email] (template
     * `supabase/templates/recovery.html`). `/recover` is captcha-protected, hence [captchaToken]. GoTrue
     * answers 200 whether or not the address has an account (no user enumeration), so [SignInResult.Success]
     * means "request accepted", not "account exists" — the UI words it that way.
     */
    suspend fun sendPasswordResetCode(email: String, captchaToken: String? = null): SignInResult = try {
        client.auth.resetPasswordForEmail(email.trim(), captchaToken = captchaToken)
        SignInResult.Success
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            AuthErrorCode.CaptchaFailed -> SignInResult.CaptchaFailed
            AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit -> SignInResult.TooManyRequests
            else -> rejected("sendPasswordResetCode", e)
        }
    } catch (e: Exception) {
        failed("sendPasswordResetCode", e, "Couldn't send the reset code")
    }

    /**
     * Forgot password, step 2: verify the recovery code. Success **signs the user in** with a fresh
     * session — which is exactly why the following [setPassword] passes `secure_password_change` (it
     * only refuses sessions older than 24 h). The caller must keep the login overlay open across that
     * sign-in (see `SignInController.holdOpen`).
     */
    suspend fun verifyPasswordResetCode(email: String, code: String): SignInResult = try {
        client.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email.trim(), code.trim())
        SignInResult.Success
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            AuthErrorCode.OverRequestRateLimit -> SignInResult.TooManyRequests
            // otp_expired / invalid → the UI's "code isn't right or has expired".
            else -> SignInResult.Error(e.errorDescription)
        }
    } catch (e: Exception) {
        failed("verifyPasswordResetCode", e, "Couldn't verify the code")
    }

    /**
     * Sets (or changes) the current user's password. For a Google-only account this adds an
     * email/password credential, so they can afterwards sign in with email + password too. Requires a
     * live session (the user is already authenticated, so no takeover risk — unlike signing up again).
     */
    suspend fun setPassword(newPassword: String): SignInResult = try {
        client.auth.updateUser { password = newPassword }
        // Remember it for this account so the "Set password" button goes away (see AccountPrefs).
        client.auth.currentUserOrNull()?.id?.let { markPasswordSet(it) }
        SignInResult.Success
    } catch (e: AuthRestException) {
        when (e.errorCode) {
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> SignInResult.TooManyRequests
            // "New password should be different from the old password" — i.e. the account ALREADY
            // has one (set elsewhere / before a reinstall). Not a failure for our purposes: record it.
            AuthErrorCode.SamePassword -> {
                client.auth.currentUserOrNull()?.id?.let { markPasswordSet(it) }
                SignInResult.PasswordAlreadySet
            }
            AuthErrorCode.WeakPassword -> SignInResult.WeakPassword
            AuthErrorCode.ReauthenticationNeeded -> SignInResult.ReauthenticationNeeded
            else -> {
                Timber.tag(TAG).w(e, "setPassword rejected: %s", e.errorCode)
                SignInResult.Error(e.errorDescription)
            }
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "setPassword failed")
        SignInResult.Error(e.message ?: "Couldn't set the password")
    }

    /** Clears the on-device session (LOCAL scope → no network; the server token just expires). */
    suspend fun signOut() {
        runCatching { client.auth.signOut(SignOutScope.LOCAL) }
    }

    /**
     * Permanently deletes the current account and all of its data (right to erasure — Play requires
     * in-app deletion for account apps). Server-side the SECURITY DEFINER `delete_current_user` RPC
     * removes this auth user; every user-data table FKs auth.users ON DELETE CASCADE, so the
     * collection / wishlist / sales / value contributions go with it (the client key can't touch
     * auth.users directly — hence the RPC). On success it wipes the on-device mirror + sync cursors so
     * nothing lingers for the next account, then drops the local session ([authState] → SignedOut,
     * which routes the UI out). On failure the user stays signed in with their data intact.
     */
    suspend fun deleteAccount(): SignInResult = try {
        client.postgrest.rpc("delete_current_user")
        runCatching {
            AppGraph.database.collectionDao().clearAll()
            AppGraph.database.wishlistDao().clearAll()
            AppGraph.database.salesDao().clearAll()
            AppGraph.syncState.clearPullCursors()
            AppGraph.syncState.setLastAccountId("")
            ThemeFavoritesPrefs.clear() // favorited themes are the deleted account's too
        }
        client.auth.signOut(SignOutScope.LOCAL)
        SignInResult.Success
    } catch (e: Exception) {
        SignInResult.Error(e.message ?: "Couldn't delete the account")
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
        // Google identity, no email identity, and no password set from this app (AccountPrefs) —
        // the last check is what hides "Set password" after it has been used.
        isGoogleOnly = "google" in providers && "email" !in providers && !AccountPrefs.hasPassword(id),
    )
}

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.jsonPrimitive?.contentOrNull

/** Lowercase hex SHA-256 — the encoding Supabase compares the ID token's `nonce` claim against. */
private fun sha256Hex(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
