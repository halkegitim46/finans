package com.kisisel.finans.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kisisel.finans.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: AppSettingEntity)

    @Query("SELECT settingValue FROM app_settings WHERE settingKey = :key")
    suspend fun get(key: String): String?

    @Query("SELECT settingValue FROM app_settings WHERE settingKey = :key")
    fun observe(key: String): Flow<String?>
}
