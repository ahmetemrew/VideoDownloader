package com.basitce.videodownloader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadProfile
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.data.model.Platform
import com.basitce.videodownloader.data.model.VideoQuality

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

    @TypeConverter
    fun fromDownloadProfile(profile: DownloadProfile): String = profile.name

    @TypeConverter
    fun toDownloadProfile(value: String): DownloadProfile = DownloadProfile.valueOf(value)
}

@Database(
    entities = [DownloadItem::class],
    version = 3,
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
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "video_downloader_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
