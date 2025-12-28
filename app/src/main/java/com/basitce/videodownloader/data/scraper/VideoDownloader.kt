package com.basitce.videodownloader.data.scraper

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Video dosyasını indiren yönetici sınıf
 */
class VideoDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36")
                .header("Accept", "*/*")
                .header("Referer", "https://www.instagram.com/")
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * Video dosyasını indirir
     * @param videoUrl İndirilecek video URL'si
     * @param fileName Kaydedilecek dosya adı (.mp4 uzantısız)
     * @param onProgress İlerleme callback (0-100)
     * @return İndirilen dosyanın yolu veya hata
     */
    suspend fun downloadVideo(
        videoUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(videoUrl)
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("İndirme başarısız: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            val contentLength = body.contentLength()
            
            val safeFileName = fileName
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(50) + ".mp4"

            // Android 10+ için MediaStore kullan
            val filePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(body.byteStream(), safeFileName, contentLength, onProgress)
            } else {
                saveToExternalStorage(body.byteStream(), safeFileName, contentLength, onProgress)
            }

            Result.success(filePath)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Android 10+ için MediaStore'a kaydet
     */
    private fun saveToMediaStore(
        inputStream: InputStream,
        fileName: String,
        contentLength: Long,
        onProgress: (Int) -> Unit
    ): String {
        val resolver = context.contentResolver
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoDownloader")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("MediaStore URI oluşturulamadı")

        resolver.openOutputStream(uri)?.use { outputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    onProgress(progress.coerceIn(0, 100))
                }
            }
        }

        // İndirme tamamlandı, IS_PENDING'i kaldır
        contentValues.clear()
        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return uri.toString()
    }

    /**
     * Android 9 ve altı için harici depolamaya kaydet
     */
    @Suppress("DEPRECATION")
    private fun saveToExternalStorage(
        inputStream: InputStream,
        fileName: String,
        contentLength: Long,
        onProgress: (Int) -> Unit
    ): String {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appDir = File(moviesDir, "VideoDownloader")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val file = File(appDir, fileName)
        
        FileOutputStream(file).use { outputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    onProgress(progress.coerceIn(0, 100))
                }
            }
        }

        return file.absolutePath
    }
}
