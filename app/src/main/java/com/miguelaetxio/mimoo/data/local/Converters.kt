package com.miguelaetxio.mimoo.data.local

import androidx.room.TypeConverter
import com.miguelaetxio.mimoo.data.local.entity.DownloadStatus

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        DownloadStatus.valueOf(value)
}
