package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.companion.chat.data.local.entity.TtsAudioCacheEntity

@Dao
interface TtsAudioCacheDao {

    @Query("SELECT * FROM tts_audio_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getByKey(key: String): TtsAudioCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TtsAudioCacheEntity)

    @Query("DELETE FROM tts_audio_cache WHERE cacheKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM tts_audio_cache WHERE audioUri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT COUNT(*) FROM tts_audio_cache")
    suspend fun count(): Int
}
