package com.senniapp.brickwares

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.senniapp.brickwares.data.local.AppGraph

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
        AppGraph.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
