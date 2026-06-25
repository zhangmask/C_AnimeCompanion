package com.companion.chat.data.preferences

import com.companion.chat.data.local.dao.PreferenceDao
import com.companion.chat.data.local.entity.UserPreference

class PreferenceRepository(
    private val preferenceDao: PreferenceDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun mergePreferences(preferences: List<ExtractedPreference>, roleCardId: Long? = null) {
        preferences.forEach { preference ->
            val normalizedCategory = preference.category.trim().lowercase()
            val normalizedContent = normalizeContent(preference.content)
            if (normalizedCategory.isBlank() || normalizedContent.isBlank()) {
                return@forEach
            }

            val now = nowProvider()
            val existing = preferenceDao.findExactMatchForRole(normalizedCategory, normalizedContent, roleCardId)
            if (existing == null) {
                preferenceDao.insert(
                    UserPreference(
                        category = normalizedCategory,
                        content = normalizedContent,
                        confidence = 1,
                        roleCardId = roleCardId,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else {
                preferenceDao.update(
                    existing.copy(
                        confidence = existing.confidence + 1,
                        updatedAt = now
                    )
                )
            }
        }
    }

    suspend fun getConfirmedPreferences(minimumConfidence: Int = 3): List<UserPreference> {
        return preferenceDao.getConfirmed(minimumConfidence)
    }

    suspend fun getConfirmedPreferencesForRole(minimumConfidence: Int = 3, roleCardId: Long?): List<UserPreference> {
        return preferenceDao.getConfirmedForRole(minimumConfidence, roleCardId)
    }

    fun normalizeContent(content: String): String {
        return content
            .trim()
            .replace(EDGE_PUNCTUATION_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .lowercase()
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val EDGE_PUNCTUATION_REGEX = Regex("^[\\p{Punct}，。！？；：、“”‘’（）《》【】]+|[\\p{Punct}，。！？；：、“”‘’（）《》【】]+$")
    }
}
