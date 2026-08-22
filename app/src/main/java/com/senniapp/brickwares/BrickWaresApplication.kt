package com.senniapp.brickwares

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

/**
 * Application entry point. Provides the app-wide Coil [ImageLoader] with:
 *  - a network fetcher, so remote http(s) images (Brickset catalog images) load, and
 *  - animated-GIF support so hero/loading animations decode correctly
 *    (platform ImageDecoder on API 28+, Coil's GifDecoder on 26–27).
 * Because this is a custom loader, components must be added explicitly — Coil only
 * auto-registers artifact components (like coil-network-okhttp) for the default loader.
 */
class BrickWaresApplication : Application(), SingletonImageLoader.Factory {
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
