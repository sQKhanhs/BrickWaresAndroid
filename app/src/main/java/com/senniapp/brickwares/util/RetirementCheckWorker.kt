package com.senniapp.brickwares.util

import android.content.Context
import timber.log.Timber
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.senniapp.brickwares.data.local.RetirementAlertPrefs
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import kotlinx.coroutines.flow.first

/**
 * The daily background retirement check (scheduled by [RetirementAlerts.start]). Force-refreshes the
 * user's referenced catalog so today's statuses are derived (the stored status is from add-time), then
 * runs the same once-per-retirement diff as the in-app path. WorkManager runs this even when the app
 * isn't open — the whole point of an alert. Does nothing (no network) while alerts are off.
 */
class RetirementCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!RetirementAlertPrefs.enabled) return Result.success()
        return try {
            val repo = CollectionRepositoryProvider.instance
            // Pull today's catalog status for the referenced sets first (throws if offline → retry, so we
            // never diff stale/denormalized status), then read the freshly-overlaid wishlist.
            repo.refreshReferencedCatalog()
            val items = repo.getWishlistItems().first()
            RetirementAlerts.evaluate(applicationContext, items)
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Retirement check failed")
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "RetirementCheckWorker"
    }
}
