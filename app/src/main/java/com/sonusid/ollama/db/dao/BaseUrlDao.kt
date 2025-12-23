package com.sonusid.ollama.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sonusid.ollama.db.entity.BaseUrl

@Dao
interface BaseUrlDao {
    @Query("SELECT * FROM base_url")
    suspend fun getBaseUrls(): List<BaseUrl>

    @Query("SELECT * FROM base_url WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveBaseUrl(): BaseUrl?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseUrl(baseUrl: BaseUrl): Long

    @Update
    suspend fun updateBaseUrl(baseUrl: BaseUrl)

    @Query("UPDATE base_url SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE base_url SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Int)

    @Query("DELETE FROM base_url WHERE id = :id")
    suspend fun deleteBaseUrl(id: Int)

    @Query("DELETE FROM base_url")
    suspend fun clearBaseUrls()

    @Transaction
    suspend fun replaceBaseUrls(baseUrls: List<BaseUrl>) {
        clearBaseUrls()
        baseUrls.forEach { insertBaseUrl(it) }
    }
}
