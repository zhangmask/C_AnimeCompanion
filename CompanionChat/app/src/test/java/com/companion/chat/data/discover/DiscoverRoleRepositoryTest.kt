package com.companion.chat.data.discover

import android.content.SharedPreferences
import com.companion.chat.data.local.dao.RoleCardDao
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.role.RoleCardRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverRoleRepositoryTest {

    @Test
    fun `seed 查询 标签 分级与排序能过滤发现角色`() {
        val repository = DiscoverRoleRepository(InMemorySharedPreferences())

        val defaultItems = repository.getRoleItems()
        assertFalse(defaultItems.any { it.role.contentRating == ContentRating.MATURE })

        val matureItems = repository.getRoleItems(includeMature = true)
        assertTrue(matureItems.any { it.role.contentRating == ContentRating.MATURE })

        val femaleItems = repository.getRoleItems(selectedTag = "女性", includeMature = true)
        assertTrue(femaleItems.isNotEmpty())
        assertTrue(femaleItems.all { "女性" in it.role.tags })

        val newest = repository.getRoleItems(includeMature = true, sortMode = RoleSortMode.NEWEST)
        assertEquals(newest.sortedByDescending { it.role.createdAt }.map { it.role.id }, newest.map { it.role.id })

        val queryItems = repository.getRoleItems(query = "Mira", includeMature = true)
        assertEquals(listOf("mira-adventure"), queryItems.map { it.role.id })
    }

    @Test
    fun `收藏和解锁状态会持久化`() {
        val prefs = InMemorySharedPreferences()
        val repository = DiscoverRoleRepository(prefs)

        repository.toggleFavorite("xia-urban")
        assertTrue(repository.getCollection("xia-urban").isFavorite)

        val reloaded = DiscoverRoleRepository(prefs)
        assertTrue(reloaded.getCollection("xia-urban").isFavorite)

        reloaded.unlock("xia-urban")
        assertTrue(reloaded.getCollection("xia-urban").isUnlocked)
        assertTrue(reloaded.getCollection("xia-urban").isFavorite)
    }

    @Test
    fun `复制发现角色会映射到现有 RoleCard 字段且不会重复导入`() = runBlocking {
        val dao = FakeRoleCardDao()
        val repository = DiscoverRoleRepository(
            sharedPreferences = InMemorySharedPreferences(),
            roleCardRepository = RoleCardRepository(dao, nowProvider = { 100L })
        )

        val firstId = repository.copyToMyRoleCard("xia-urban")
        val secondId = repository.copyToMyRoleCard("xia-urban")

        assertEquals(firstId, secondId)
        assertEquals(1, dao.roleCards.size)
        val roleCard = dao.roleCards.single()
        assertEquals("小夏", roleCard.name)
        assertTrue(roleCard.persona.contains("小夏"))
        assertEquals("soft urban anime portrait, warm phone-light, natural expression", roleCard.imageStylePrompt)
        assertEquals("SYSTEM_TTS", roleCard.voiceMode)
        assertNotNull(repository.getCollection("xia-urban").importedRoleCardId)
    }

    @Test
    fun `发现角色生成图片会追加到已导入角色图库`() = runBlocking {
        val dao = FakeRoleCardDao()
        val repository = DiscoverRoleRepository(
            sharedPreferences = InMemorySharedPreferences(),
            roleCardRepository = RoleCardRepository(dao, nowProvider = { 100L })
        )

        val importedId = repository.copyToMyRoleCard("chen-nocturne")
        val attached = repository.attachGeneratedImage("chen-nocturne", "file:///scene.png")

        assertTrue(attached)
        val roleCard = dao.getById(importedId)!!
        assertEquals(listOf("file:///scene.png"), roleCard.galleryImageUris)
        assertEquals("file:///scene.png", roleCard.avatarImageUri)
    }

    private class FakeRoleCardDao(
        val roleCards: MutableList<RoleCard> = mutableListOf()
    ) : RoleCardDao {
        private var nextId = 1L

        override suspend fun insert(roleCard: RoleCard): Long {
            val inserted = roleCard.copy(id = nextId++)
            roleCards += inserted
            return inserted.id
        }

        override suspend fun update(roleCard: RoleCard) {
            roleCards.replaceAll { if (it.id == roleCard.id) roleCard else it }
        }

        override suspend fun delete(roleCard: RoleCard) {
            roleCards.removeAll { it.id == roleCard.id }
        }

        override suspend fun getAll(): List<RoleCard> = roleCards

        override suspend fun getActive(): RoleCard? = roleCards.firstOrNull { it.isActive }

        override suspend fun getById(id: Long): RoleCard? = roleCards.firstOrNull { it.id == id }

        override suspend fun deactivateAll(): Int {
            roleCards.replaceAll { it.copy(isActive = false) }
            return roleCards.size
        }

        override suspend fun activate(id: Long, now: Long): Int {
            val index = roleCards.indexOfFirst { it.id == id }
            if (index < 0) return 0
            roleCards[index] = roleCards[index].copy(isActive = true, updatedAt = now)
            return 1
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { values[key.orEmpty()] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { values[key.orEmpty()] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { values[key.orEmpty()] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { values[key.orEmpty()] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { values[key.orEmpty()] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { values.remove(key) }
            override fun clear(): SharedPreferences.Editor = apply { values.clear() }
            override fun commit(): Boolean = true
            override fun apply() = Unit
        }
    }
}
