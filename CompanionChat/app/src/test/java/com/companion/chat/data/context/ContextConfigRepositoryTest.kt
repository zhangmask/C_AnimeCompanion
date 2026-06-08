package com.companion.chat.data.context

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextConfigRepositoryTest {

    @Test
    fun `自动学习偏好默认开启`() {
        val repository = ContextConfigRepository(FakeSharedPreferences())

        assertTrue(repository.getAutoPreferenceLearningEnabled())
    }

    @Test
    fun `关闭自动学习偏好后重新读取仍为false`() {
        val sharedPreferences = FakeSharedPreferences()
        val repository = ContextConfigRepository(sharedPreferences)

        repository.updateAutoPreferenceLearningEnabled(false)

        val reloadedRepository = ContextConfigRepository(sharedPreferences)
        assertFalse(reloadedRepository.getAutoPreferenceLearningEnabled())
    }

    @Test
    fun `自动学习偏好设置不影响已有上下文配置读取`() {
        val sharedPreferences = FakeSharedPreferences(
            mutableMapOf(
                "retained_rounds" to 7,
                "compression_buffer" to 12,
                "summary_max_chars" to 180,
                "summary_timeout_millis" to 12_345L
            )
        )
        val repository = ContextConfigRepository(sharedPreferences)

        repository.updateAutoPreferenceLearningEnabled(false)
        val settings = repository.getSettings()

        assertEquals(7, settings.retainedRounds)
        assertEquals(12, settings.compressionBuffer)
        assertEquals(180, settings.summaryMaxChars)
        assertEquals(12_345L, settings.summaryTimeoutMillis)
    }

    private class FakeSharedPreferences(
        private val values: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {

        override fun getAll(): MutableMap<String, *> = values

        override fun getString(key: String?, defValue: String?): String? {
            return values[key] as? String ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (values[key] as? MutableSet<String>) ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return values[key] as? Int ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            return values[key] as? Long ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            return values[key] as? Float ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return values[key] as? Boolean ?: defValue
        }

        override fun contains(key: String?): Boolean {
            return values.containsKey(key)
        }

        override fun edit(): SharedPreferences.Editor = FakeEditor(values)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }

    private class FakeEditor(
        private val values: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {

        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = values
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = null
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) {
                values.clear()
            }
            pending.forEach { (key, value) ->
                if (value == null) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
            }
            pending.clear()
            clearRequested = false
        }
    }
}
