package com.senniapp.brickwares.util

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * Warms Coil's disk cache with images a screen is about to need, so they render from local storage
 * instead of one network round trip each. Built for the Search tab's theme icons: ~170 tiny PNGs that
 * are cheap in bytes (~1 MB total) but expensive in latency — from Vietnam, Cloudflare's edge is
 * typically Singapore / Hong Kong, ~0.3–1 s per request even on a cache HIT — so fetching them all in
 * the background as soon as the catalog loads makes the theme browse appear fully drawn on first open.
 *
 * Requests skip the memory cache (they'd only evict images that are actually on screen) and rely on
 * Coil's disk cache, which persists across launches — after the first run this is a no-op per icon.
 * Initialised with the application context from `Application.onCreate`; inert until then.
 */
object ImagePrefetcher {
    @Volatile
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** Enqueues [urls] for background download into the disk cache. Duplicates are harmless (cache hits). */
    fun warm(urls: Collection<String>) {
        val context = app ?: return
        val loader = SingletonImageLoader.get(context)
        urls.forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
            )
        }
    }
}
