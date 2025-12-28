package com.basitce.videodownloader.data.db

import android.content.Context
import androidx.room.*
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.data.model.Platform
import com.basitce.videodownloader.data.model.VideoQuality

/**
 * Room type converters for enums
 */
class Converters {
    @TypeConverter
    fun fromPlatform(platform: Platform): String = platform.name

    @TypeConverter
    fun toPlatform(value: String): Platform = Platform.valueOf(value)

    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

    @TypeConverter
    fun fromVideoQuality(quality: VideoQuality): String = quality.name

    @TypeConverter
    fun toVideoQuality(value: String): VideoQuality = VideoQuality.valueOf(value)
}

@Database(
    entities = [DownloadItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "video_downloader_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
