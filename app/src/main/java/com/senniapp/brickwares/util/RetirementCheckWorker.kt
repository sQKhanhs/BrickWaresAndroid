package com.senniapp.brickwares.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.senniapp.brickwares.data.local.RetirementAlertPrefs
import com.senniapp.brickwares.data.repository.CatalogRepositoryProvider
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import kotlinx.coroutines.flow.first

/**
 * The daily background retirement check (scheduled by [RetirementAlerts.start]). Force-reloads the
 * catalog so today's statuses are derived (a cached catalog keeps the statuses from when it loaded),
 * then runs the same once-per-retirement diff as the in-app path. WorkManager runs this even when the
 * app isn't open — the whole point of an alert. Does nothing (no network) while alerts are off.
 */
class RetirementCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!RetirementAlertPrefs.enabled) return Result.success()
        return try {
            val catalog = CatalogRepositoryProvider.instance
            catalog.reload()
            // The reload failed (kept the old/empty cache) → try again later rather than diff stale data.
            if (catalog.all().isEmpty()) return Result.retry()
            val items = CollectionRepositoryProvider.instance.getWishlistItems().first()
            RetirementAlerts.evaluate(applicationContext, items)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Retirement check failed", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "RetirementCheckWorker"
    }
}
