package com.gusanitolabs.robia.data.local

import androidx.room.TypeConverter
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentSyncStatus

class RobiaConverters {
    @TypeConverter
    fun colorLabelToString(value: DisplayColorLabel?): String? = value?.name

    @TypeConverter
    fun stringToColorLabel(value: String?): DisplayColorLabel? =
        value?.let { DisplayColorLabel.valueOf(it) }

    @TypeConverter
    fun garmentSyncStatusToString(value: GarmentSyncStatus?): String? = value?.name

    @TypeConverter
    fun stringToGarmentSyncStatus(value: String?): GarmentSyncStatus? =
        value?.let { GarmentSyncStatus.valueOf(it) }
}
