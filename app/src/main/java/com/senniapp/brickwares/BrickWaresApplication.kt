package com.senniapp.brickwares

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

/**
 * Application entry point. Provides the app-wide Coil [ImageLoader] with animated-GIF
 * support so hero/loading animations (and later product images) decode correctly.
 * Uses the platform ImageDecoder on API 28+, falling back to Coil's GifDecoder on 26–27.
 */
class BrickWaresApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
