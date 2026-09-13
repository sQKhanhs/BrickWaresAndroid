package com.senniapp.brickwares

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.senniapp.brickwares.data.local.AnalyticsPrefs
import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.local.CurrencyPrefs
import com.senniapp.brickwares.data.local.InstallId
import com.senniapp.brickwares.data.local.LocalePrefs
import com.senniapp.brickwares.data.local.RetirementAlertPrefs
import com.senniapp.brickwares.data.local.ThemeFavoritesPrefs
import com.senniapp.brickwares.util.ImagePrefetcher
import com.senniapp.brickwares.util.Observability
import com.senniapp.brickwares.util.RetirementAlerts
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * Application entry point.
 *  - Initializes the local store (Room + DataStore) before any ViewModel/repository needs it.
 *  - Provides the app-wide Coil [ImageLoader] with a network fetcher (so remote http(s) catalog
 *    images load) and animated-GIF support (platform ImageDecoder on API 28+, Coil's GifDecoder on
 *    26–27). Because this is a custom loader, components must be added explicitly.
 */
class BrickWaresApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        LocalePrefs.init(this)
        ThemeFavoritesPrefs.init(this)
        CurrencyPrefs.init(this)
        RetirementAlertPrefs.init(this)
        AnalyticsPrefs.init(this)
        InstallId.init(this)
        // Timber + (opt-in, Firebase-optional) Crashlytics/Analytics — before anything that might log.
        Observability.init(this)
        AppGraph.init(this)
        // Watches the wishlist for items that change to Retired and notifies (Settings → Notifications).
        RetirementAlerts.start(this)
        // Background image warm-up (Search theme icons) — needs the app context for Coil requests.
        ImagePrefetcher.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            // Fade thumbnails in as they load, instead of popping — smoother list scrolling.
            .crossfade(true)
            .components {
                // A dedicated OkHttp client with a wider per-host limit. OkHttp's default allows only
                // 5 in-flight requests per host, and nearly every image comes from two hosts (our R2
                // domain + Rebrickable's CDN). On a high-latency path (Vietnam → Cloudflare's SIN/HKG
                // edges, ~0.3–1 s per request even when cached) a screen of 15 thumbnails would fill in
                // three slow waves; 16 per host lets a whole screen load in one.
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
                                .build()
                        },
                    ),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
