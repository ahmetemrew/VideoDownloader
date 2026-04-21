package com.basitce.videodownloader.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object GallerySaver {

    suspend fun scanIntoGallery(context: Context, filePath: String): Uri? {
        val file = File(filePath)
        if (!file.exists()) {
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("video/*")
            ) { _, uri ->
                if (continuation.isActive) {
                    continuation.resume(uri)
                }
            }
        }
    }
}
