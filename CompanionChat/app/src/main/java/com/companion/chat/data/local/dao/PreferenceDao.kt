package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.companion.chat.data.local.entity.UserPreference

@Dao
interface PreferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preference: UserPreference): Long

    @Update
    suspend fun update(preference: UserPreference)

    @Query("SELECT * FROM user_preferences WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getByCategory(category: String): List<UserPreference>

    @Query(
        """
        SELECT * FROM user_preferences
        WHERE category = :category AND LOWER(content) = LOWER(:content)
        LIMIT 1
        """
    )
    suspend fun findExactMatch(category: String, content: String): UserPreference?

    @Query("SELECT * FROM user_preferences WHERE confidence >= :minimumConfidence ORDER BY updatedAt DESC")
    suspend fun getConfirmed(minimumConfidence: Int = 3): List<UserPreference>
}
