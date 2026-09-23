package com.senniapp.brickwares.data.repository

import android.os.Build
import androidx.annotation.StringRes
import com.senniapp.brickwares.BuildConfig
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.local.InstallId
import com.senniapp.brickwares.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.Locale

/**
 * Settings → Send feedback. Submits through the `submit_feedback` RPC (the only way into the
 * `feedback` table — validation, spam rate limits and the caller's identity/IP hash are applied
 * server-side, see the migration). Works signed in or out; signed-out submissions carry the per-install
 * [InstallId] as their rate-limit key. Device context (app/OS version, model, locale) rides along so a
 * report like "it's slow" is actionable without asking.
 */
/** The category a user tags their feedback with (stored so feedback can be sorted). [wire] matches the DB check. */
enum class FeedbackCategory(val wire: String, @StringRes val labelRes: Int) {
    BUG("bug", R.string.feedback_category_bug),
    FEATURE("feature", R.string.feedback_category_feature),
    OTHER("other", R.string.feedback_category_other),
}

class FeedbackRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    enum class Result { SENT, RATE_LIMITED, INVALID, FAILED }

    suspend fun send(message: String, contactEmail: String?, category: FeedbackCategory): Result {
        val params = buildJsonObject {
            put("p_message", message.trim())
            put("p_contact_email", contactEmail?.trim()?.ifBlank { null })
            put("p_app_version", BuildConfig.VERSION_NAME)
            put("p_os_version", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            put("p_device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("p_locale", Locale.getDefault().toLanguageTag())
            put("p_install_id", InstallId.value)
            put("p_category", category.wire)
        }
        return try {
            withTimeout(TIMEOUT_MS) { client.postgrest.rpc("submit_feedback", params) }
            Result.SENT
        } catch (e: TimeoutCancellationException) {
            // A timeout is a network failure, not a real navigate-away cancellation. It's a
            // CancellationException subtype, so catch it BEFORE the rethrow below or it would unwind
            // silently and the caller would never see a Result — treat it as an ordinary failure.
            Timber.tag(TAG).w(e, "submit_feedback timed out")
            Result.FAILED
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            val text = e.message.orEmpty()
            when {
                "feedback_rate_limited" in text -> Result.RATE_LIMITED
                "feedback_invalid" in text -> Result.INVALID
                else -> {
                    Timber.tag(TAG).w(e, "submit_feedback rejected")
                    Result.FAILED
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "submit_feedback failed")
            Result.FAILED
        }
    }

    private companion object {
        const val TAG = "Feedback"
        const val TIMEOUT_MS = 15_000L
    }
}
