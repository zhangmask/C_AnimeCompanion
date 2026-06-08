package com.companion.chat.data.preferences

import com.companion.chat.data.local.dao.PreferenceDao
import com.companion.chat.data.local.entity.UserPreference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceRepositoryTest {

    @Test
    fun `新偏好写入 confidence 等于1`() = runBlocking {
        val fakeDao = FakePreferenceDao()
        val repository = PreferenceRepository(fakeDao, nowProvider = { 100L })

        repository.mergePreferences(
            listOf(ExtractedPreference(category = "style", content = " 喜欢简洁回答。 "))
        )

        assertEquals(1, fakeDao.preferences.size)
        assertEquals(1, fakeDao.preferences.single().confidence)
        assertEquals("喜欢简洁回答", fakeDao.preferences.single().content)
    }

    @Test
    fun `相同 category 加规范化内容再次出现时 confidence 加1`() = runBlocking {
        val fakeDao = FakePreferenceDao(
            mutableListOf(
                UserPreference(
                    id = 1L,
                    category = "style",
                    content = "喜欢简洁回答",
                    confidence = 1,
                    createdAt = 10L,
                    updatedAt = 10L
                )
            )
        )
        val repository = PreferenceRepository(fakeDao, nowProvider = { 200L })

        repository.mergePreferences(
            listOf(ExtractedPreference(category = "style", content = "  喜欢简洁回答  "))
        )

        assertEquals(1, fakeDao.preferences.size)
        assertEquals(2, fakeDao.preferences.single().confidence)
        assertEquals(200L, fakeDao.preferences.single().updatedAt)
    }

    @Test
    fun `getConfirmedPreferences 仅返回 confidence 大于等于3`() = runBlocking {
        val fakeDao = FakePreferenceDao(
            mutableListOf(
                UserPreference(id = 1L, category = "name", content = "小明", confidence = 2, createdAt = 1L, updatedAt = 1L),
                UserPreference(id = 2L, category = "style", content = "喜欢简洁回答", confidence = 3, createdAt = 2L, updatedAt = 2L),
                UserPreference(id = 3L, category = "habit", content = "喜欢晚上聊天", confidence = 5, createdAt = 3L, updatedAt = 3L)
            )
        )
        val repository = PreferenceRepository(fakeDao)

        val result = repository.getConfirmedPreferences()

        assertEquals(listOf(3L, 2L), result.map { it.id })
    }

    @Test
    fun `模型总结为空列表时不写入任何偏好`() = runBlocking {
        val fakeDao = FakePreferenceDao()
        val repository = PreferenceRepository(fakeDao)

        repository.mergePreferences(emptyList())

        assertTrue(fakeDao.preferences.isEmpty())
    }

    private class FakePreferenceDao(
        val preferences: MutableList<UserPreference> = mutableListOf()
    ) : PreferenceDao {

        private var nextId = (preferences.maxOfOrNull { it.id } ?: 0L) + 1L

        override suspend fun insert(preference: UserPreference): Long {
            preferences += preference.copy(id = nextId)
            return nextId++
        }

        override suspend fun update(preference: UserPreference) {
            val index = preferences.indexOfFirst { it.id == preference.id }
            if (index >= 0) {
                preferences[index] = preference
            }
        }

        override suspend fun getByCategory(category: String): List<UserPreference> {
            return preferences.filter { it.category == category }.sortedByDescending { it.updatedAt }
        }

        override suspend fun findExactMatch(category: String, content: String): UserPreference? {
            return preferences.firstOrNull { it.category == category && it.content.lowercase() == content.lowercase() }
        }

        override suspend fun getConfirmed(minimumConfidence: Int): List<UserPreference> {
            return preferences
                .filter { it.confidence >= minimumConfidence }
                .sortedByDescending { it.updatedAt }
        }
    }
}
