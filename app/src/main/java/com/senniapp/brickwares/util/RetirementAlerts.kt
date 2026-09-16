package com.senniapp.brickwares.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.senniapp.brickwares.MainActivity
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.local.RetirementAlertPrefs
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.ui.components.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Retirement alerts (Settings → Notifications): notifies when an item on the user's WISHLIST changes
 * status to Retired. Two triggers share one detector ([evaluate]):
 *  - **In-app**: the wishlist flow re-overlays the live catalog status onto every row each time the
 *    catalog (re)loads, so this object observes it while the process is alive.
 *  - **Background**: a daily [RetirementCheckWorker] (WorkManager) force-reloads the catalog and runs
 *    the same check — so users hear about a retirement without opening the app.
 * Against the persisted last check ([RetirementAlertPrefs]):
 *   newly retired = retired now ∩ was-on-the-wishlist-last-time − was-already-retired-last-time
 * which baselines silently on the first run, ignores sets wishlisted after they retired, and alerts
 * each retirement once. State is tracked even while alerts are off (so switching them on later only
 * reports FUTURE retirements); the toggle gates only the notification itself.
 *
 * Delivery: a system notification when the app is in the background, or — since notifying someone
 * already looking at the app is pointless — an in-app toast ([inAppNotice]) when it's in the foreground.
 */
object RetirementAlerts {
    private const val TAG = "RetirementAlerts"
    private const val CHANNEL_ID = "retirement_alerts"
    private const val NOTIFICATION_ID = 4101
    private const val WORK_NAME = "retirement_check"

    /** Intent extra on the notification's tap target, read by MainActivity to land on the Wishlist. */
    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_WISHLIST = "wishlist"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started = false

    private val _inAppNotice = MutableStateFlow<UiText?>(null)

    /** The retirement message to show inside the app (the foreground counterpart of the notification). */
    val inAppNotice: StateFlow<UiText?> = _inAppNotice.asStateFlow()

    fun clearInAppNotice() {
        _inAppNotice.value = null
    }

    /**
     * Creates the notification channel, schedules the daily background check, and starts the in-app
     * wishlist observer. Call once from Application.onCreate (after the local store is initialised).
     */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        createChannel(app)
        scheduleDailyCheck(app)
        scope.launch {
            CollectionRepositoryProvider.instance.getWishlistItems()
                .catch { e -> Timber.tag(TAG).e(e, "Wishlist observation failed") }
                .collect { items -> evaluate(app, items) }
        }
    }

    /**
     * The once-per-retirement diff (see the class doc). Idempotent: after a hit the newly-retired
     * items are recorded, so a repeat evaluation of the same data notifies nothing.
     */
    fun evaluate(context: Context, items: List<WishlistItem>) {
        // Only diff against AUTHORITATIVE statuses — before the user-scoped catalog overlay has loaded,
        // rows carry the status stored at add time, which may be stale in either direction (Decision 16).
        if (!CollectionRepositoryProvider.instance.catalogOverlayReady.value) return

        val current = items.map { it.setNumber }.toSet()
        val retiredNow = items.filter { it.status == Availability.RETIRED }.map { it.setNumber }.toSet()
        val lastWishlist = RetirementAlertPrefs.lastWishlist
        val lastRetired = RetirementAlertPrefs.lastRetired

        val newlyRetired = retiredNow.filter { it in lastWishlist && it !in lastRetired }
        // Alerts need an account (the wishlist is account data): never deliver while explicitly signed
        // out. An unresolved (Loading) session — e.g. a fresh background process — still delivers,
        // since the toggle can only have been switched on by a signed-in user.
        val signedOut = AuthRepository.authState.value is AuthState.SignedOut
        if (newlyRetired.isNotEmpty() && RetirementAlertPrefs.enabled && !signedOut) {
            val names = items.filter { it.setNumber in newlyRetired }.map { it.name }
            deliver(context, names)
        }

        RetirementAlertPrefs.lastWishlist = current
        RetirementAlertPrefs.lastRetired = retiredNow
    }

    /** Once a day, on a network connection; KEEP leaves an existing schedule untouched. */
    private fun scheduleDailyCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<RetirementCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_retirement),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notif_channel_retirement_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** The item's name when exactly one retired, else the count — in-app when visible, else a notification. */
    private fun deliver(context: Context, names: List<String>) {
        val message: UiText = if (names.size == 1) {
            UiText.Res(R.string.notif_retired_one, listOf(names.first()))
        } else {
            UiText.Res(R.string.notif_retired_many, listOf(names.size))
        }
        if (isAppInForeground()) {
            _inAppNotice.value = message
            return
        }
        if (!canPost(context)) {
            Timber.tag(TAG).i("Notification permission not granted — skipping ${names.size} retirement alert(s)")
            return
        }
        val text = if (names.size == 1) {
            context.getString(R.string.notif_retired_one, names.first())
        } else {
            context.getString(R.string.notif_retired_many, names.size)
        }
        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TAB, TAB_WISHLIST)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bw_heart)
            .setColor(0xFFFFD500.toInt())
            .setContentTitle(context.getString(R.string.notif_retired_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Whether the app has a visible activity (the process-wide lifecycle is at least STARTED). */
    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /** Android 13+ needs the runtime POST_NOTIFICATIONS grant; any version can have notifications disabled. */
    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
