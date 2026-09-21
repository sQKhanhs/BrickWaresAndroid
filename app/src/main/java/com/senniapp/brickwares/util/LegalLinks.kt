package com.senniapp.brickwares.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Public legal pages, hosted on the web (brickwares.app) rather than baked into the app, so the text can
 * be updated without an app release. Paths match the hosted files (privacy-policy.html /
 * terms-of-service.html, source in the OneDrive `brickwares-site` folder). Google Play also requires the
 * privacy URL in the store listing, and the answers on the Play Data Safety form must match what the
 * policy states.
 *
 * Linked from Settings → Privacy AND from the sign-in modal's acceptance line — the Terms say that
 * creating an account means agreeing to them, so the moment of sign-up has to point at them.
 */
object LegalLinks {
    const val PRIVACY_POLICY_URL = "https://brickwares.app/privacy-policy"
    const val TERMS_OF_SERVICE_URL = "https://brickwares.app/terms-of-service"

    /**
     * Opens [url] in a Chrome Custom Tab — an in-app browser overlay, so the user returns with one tap
     * (the tab's close/back button) instead of task-switching to a separate browser app. Falls back to
     * the default browser if no Custom Tabs provider is available.
     */
    fun open(context: Context, url: String) {
        // The pages are bilingual (EN/VI in one document, switched by site.js). `?lang=` opens them in
        // the language the APP is showing — which follows the in-app switcher, not the device locale —
        // and the site remembers it for the visitor's next page.
        val lang = if (context.resources.configuration.locales[0].language == "vi") "vi" else "en"
        val uri = Uri.parse(url).buildUpon().appendQueryParameter("lang", lang).build()
        runCatching {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
