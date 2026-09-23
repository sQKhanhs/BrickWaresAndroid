package com.senniapp.brickwares.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes [bitmap] (the rendered share card) to a cache file and opens the Android share sheet with it
 * as an image attachment. The file is exposed read-only via the app's [FileProvider] (authority
 * `<applicationId>.fileprovider`, see the manifest + res/xml/file_paths.xml). Returns false if the
 * image couldn't be written or no app could handle the share. Call from the main thread — the file
 * write hops to IO internally, then the chooser is started on the caller's (main) context.
 */
suspend fun shareCollectionImage(context: Context, bitmap: Bitmap, chooserTitle: String): Boolean {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "brickwares-collection.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    } ?: return false

    return runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
        true
    }.getOrDefault(false)
}

/**
 * Saves [bitmap] (the rendered share card) to the device gallery under Pictures/BrickWares via
 * MediaStore, so it shows up in Photos. Permission-free on Android 10+ (scoped storage); on API 26-28
 * it needs WRITE_EXTERNAL_STORAGE (declared with maxSdkVersion in the manifest and requested by the
 * caller) — without it the insert fails and this returns false. Call from the main thread; the write
 * hops to IO internally.
 */
suspend fun saveCollectionImage(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "BrickWares-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BrickWares")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
        ?: return@withContext false
    // Success = the stream opened AND compress() reported true. A null stream OR a false compress (e.g.
    // disk full) is a failure — without checking compress, a failed write left a 0-byte image (and, on
    // Android 10+, a stuck IS_PENDING=1 row) while the caller said "Saved".
    val ok = runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
    }.getOrDefault(false)
    if (!ok) {
        // Delete the orphaned row (the 0-byte / still-pending entry) so a failed save leaves nothing behind.
        runCatching { resolver.delete(uri, null, null) }
        return@withContext false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) }
    }
    true
}
