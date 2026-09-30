package com.senniapp.brickwares.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Opening web pages from inside the app. */
object WebLinks {
    /**
     * Opens [uri] in a Chrome Custom Tab — an in-app browser overlay, so the user returns with one tap
     * (the tab's close/back button) instead of task-switching to a separate browser app. Falls back to
     * the default browser if no Custom Tabs provider is available, and does nothing if neither can open it.
     */
    fun open(context: Context, uri: Uri) {
        runCatching {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** [open] for an untrusted link (e.g. from catalog text): only http(s), surrounding whitespace ignored. */
    fun openWebUrl(context: Context, url: String) {
        val u = url.trim()
        if (u.startsWith("https://", ignoreCase = true) || u.startsWith("http://", ignoreCase = true)) open(context, Uri.parse(u))
    }
}
