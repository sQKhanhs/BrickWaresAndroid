package com.senniapp.brickwares.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.senniapp.brickwares.BuildConfig
import com.senniapp.brickwares.data.local.RatePrefs
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * "Enjoying BrickWares? Rate it" — asks engaged, SIGNED-IN users for a Play Store rating, at most
 * [MAX_PROMPTS] times, each time only after a further step of real investment: prompt *n* needs
 * **≥ 10·n items across My Collection + My Sales AND ≥ 20·n items on the wishlist** — so the first
 * asks at 10 / 20, the second at 20 / 40 (product owner, 2026-09-15). Observes the auth state and
 * the three Room flows for the life of the process (like [RetirementAlerts]) and raises [show]; the
 * app shell renders the dialog.
 *
 * "Rate now" and "Don't ask again" end it for good ([RatePrefs.done] — also set when the user taps
 * the Settings row, since they've been to the store); "Not now" counts the prompt and waits for the
 * next threshold step.
 */
object RatePrompt {
    private const val COLLECTION_AND_SALES_STEP = 10
    private const val WISHLIST_STEP = 20
    private const val MAX_PROMPTS = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _show = MutableStateFlow(false)
    /** True while the rate dialog should be on screen. */
    val show: StateFlow<Boolean> = _show.asStateFlow()

    fun start() {
        val repo = CollectionRepositoryProvider.instance
        scope.launch {
            combine(
                AuthRepository.authState,
                repo.getCollectionItems(),
                repo.getSoldItems(),
                repo.getWishlistItems(),
            ) { auth, owned, sold, wishlist ->
                // Only a SIGNED-IN user. The Room mirror is not wiped on sign-out, so its rows still
                // flow while signed out — the auth check keeps the prompt off then.
                val signedIn = auth is AuthState.SignedIn
                val step = RatePrefs.promptCount + 1 // the prompt we would show next (1-based)
                val engaged = owned.size + sold.size >= COLLECTION_AND_SALES_STEP * step &&
                    wishlist.size >= WISHLIST_STEP * step
                Timber.tag(TAG).d(
                    "signedIn=%s owned+sold=%d wishlist=%d nextPrompt=%d engaged=%s",
                    signedIn, owned.size + sold.size, wishlist.size, step, engaged,
                )
                signedIn && engaged
            }
                .catch { Timber.tag(TAG).w(it, "rate-prompt observer failed") }
                .collect { eligible -> if (eligible && isDue()) _show.value = true }
        }
    }

    private fun isDue(): Boolean = !RatePrefs.done && RatePrefs.promptCount < MAX_PROMPTS

    /** "Rate now" in the prompt: opens the store listing and never asks again. */
    fun onRateNow(context: Context) {
        RatePrefs.done = true
        _show.value = false
        openStore(context)
    }

    /** "Not now": counts the prompt; the next one (if any) waits for the next threshold step. */
    fun onLater() {
        RatePrefs.lastPromptAt = System.currentTimeMillis()
        RatePrefs.promptCount = RatePrefs.promptCount + 1
        _show.value = false
    }

    /** "Don't ask again". */
    fun onNever() {
        RatePrefs.done = true
        _show.value = false
    }

    /**
     * Opens the app's Google Play listing — the Play app if installed (`market://`), else the web
     * store. Always the PROD listing: the dev flavour's id (`…brickwares.dev`) has no listing.
     */
    fun openStore(context: Context) {
        RatePrefs.done = true // been to the store → the prompt has done its job
        // The Play listing is the prod package; debug/dev variants carry a suffix (".debug" / ".dev").
        val pkg = BuildConfig.APPLICATION_ID.removeSuffix(".debug").removeSuffix(".dev")
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(web) }.onFailure { e -> Timber.tag(TAG).w(e, "no store handler") }
        }
    }

    private const val TAG = "RatePrompt"
}
