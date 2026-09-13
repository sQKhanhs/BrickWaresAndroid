package com.senniapp.brickwares.util

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.senniapp.brickwares.BuildConfig
import com.senniapp.brickwares.data.local.AnalyticsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Observability (Arch Decision 13): **Timber** logging everywhere, plus **Firebase Crashlytics** (crashes
 * and non-fatals — always on) and **Firebase Analytics** (usage — strictly opt-in).
 *
 *  - Logging: a [CrashlyticsTree] in every build forwards WARN+ messages as Crashlytics breadcrumbs and
 *    any attached throwable as a **non-fatal** (`recordException`) — so the exceptions the app
 *    deliberately swallows (catalog load, sync, auth, the retirement check) surface in the console
 *    instead of vanishing. Debug builds ALSO plant a [Timber.DebugTree] (Logcat, auto-tagged), so dev
 *    runs report to Crashlytics too — handy for verifying the pipeline (Settings → Developer). Crash
 *    reports themselves (uncaught exceptions) are captured by the SDK in any build once enabled.
 *  - Crashlytics is ALWAYS on: crash logs are the diagnostics a solo-maintained app needs, and opt-in
 *    crash reporting yields almost no data (opt-in rates are tiny). Disclosed in the privacy policy.
 *  - Consent: the Settings → Privacy "Usage analytics" toggle ([AnalyticsPrefs], default OFF) gates
 *    Firebase ANALYTICS only. Its auto-collection is disabled in the manifest, so no usage data is sent
 *    before the user agrees; every change is applied live via [applyConsent]. No PII is ever logged.
 *  - Firebase-optional: the SDKs only initialise when `app/google-services.json` is present (see
 *    app/build.gradle.kts). Every Firebase call here is guarded on [firebaseReady], so a build without
 *    the file still logs normally and just skips reporting.
 */
object Observability {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Whether Firebase initialised (a google-services.json was bundled). Read once at [init]. */
    @Volatile
    private var firebaseReady = false

    /** Call once from `Application.onCreate`, after [AnalyticsPrefs.init]. */
    fun init(app: Application) {
        appContext = app
        firebaseReady = FirebaseApp.getApps(app).isNotEmpty()
        // Crashlytics tree in every build (reports from dev runs too); Logcat tree only in debug.
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.plant(CrashlyticsTree())
        // Crashlytics is always on. Set it EXPLICITLY rather than relying on the manifest default: the
        // SDK persists this flag across launches, so a device that ran an earlier build with consent
        // off would otherwise stay disabled.
        if (firebaseReady) FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        applyConsent(app, AnalyticsPrefs.consent)
        // Follow the Settings toggle for the life of the process.
        scope.launch { AnalyticsPrefs.consentFlow.collect { applyConsent(app, it) } }
        Timber.i("Observability ready — firebase=%s, analyticsConsent=%s", firebaseReady, AnalyticsPrefs.consent)
    }

    /** Enables or disables Firebase ANALYTICS collection to match the user's consent (Crashlytics is always on). */
    fun applyConsent(context: Context, enabled: Boolean) {
        if (!firebaseReady) return
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
    }

    /** Application context for event logging; set in [init]. */
    @Volatile
    private var appContext: Context? = null

    /**
     * Records that a catalog detail page was viewed — Firebase's standard `view_item` event, so the
     * console's Events / Engagement reports answer "which sets are looked at, how often, and when"
     * (the SDK stamps the time). Carries only catalog data (the set / minifig id, its name and theme):
     * no user identity, and nothing at all is sent unless the user opted in to usage analytics
     * (collection is disabled otherwise, so the SDK drops the event). [kind] is "set" or "minifig".
     */
    fun logItemViewed(kind: String, id: String, name: String, theme: String?) {
        Timber.tag("Analytics").d("view_item %s %s (%s)", kind, id, theme)
        val context = appContext ?: return
        if (!firebaseReady) return
        FirebaseAnalytics.getInstance(context).logEvent(
            FirebaseAnalytics.Event.VIEW_ITEM,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.CONTENT_TYPE, kind)
                putString(FirebaseAnalytics.Param.ITEM_ID, id)
                putString(FirebaseAnalytics.Param.ITEM_NAME, name.take(100))
                if (!theme.isNullOrBlank()) putString(FirebaseAnalytics.Param.ITEM_CATEGORY, theme)
            },
        )
    }

    /**
     * Crashlytics Timber tree (planted in every build): WARN+ lines become breadcrumbs (visible on the
     * next crash / non-fatal report) and a logged throwable is recorded as a non-fatal. Everything below
     * WARN is ignored. Inert until Firebase is ready (no google-services.json → nothing to report to).
     */
    private class CrashlyticsTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (!firebaseReady) return
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("${tag ?: "App"}: $message")
            if (t != null) crashlytics.recordException(t)
        }
    }
}
