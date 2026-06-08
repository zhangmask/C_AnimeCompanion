package com.companion.chat.data.voice

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAsrConfigRepositoryTest {

    @Test
    fun `默认云 ASR 未配置`() {
        val repository = CloudAsrConfigRepository(FakeSharedPreferences())

        val config = repository.getConfig()

        assertFalse(config.isConfigured)
        assertEquals("audio", config.requestFieldName)
        assertEquals("text", config.responseTextFieldPath)
        assertEquals(CloudAsrConfig.DEFAULT_TIMEOUT_MILLIS, config.timeoutMillis)
    }

    @Test
    fun `配置写入后可读取`() {
        val sharedPreferences = FakeSharedPreferences()
        val repository = CloudAsrConfigRepository(sharedPreferences)

        repository.updateConfig(
            CloudAsrConfig(
                baseUrl = " https://example.com/asr ",
                apiKey = " token ",
                requestFieldName = "file",
                responseTextFieldPath = "data.result.text",
                timeoutMillis = 45_000
            )
        )

        val config = CloudAsrConfigRepository(sharedPreferences).getConfig()
        assertTrue(config.isConfigured)
        assertEquals("https://example.com/asr", config.baseUrl)
        assertEquals("token", config.apiKey)
        assertEquals("file", config.requestFieldName)
        assertEquals("data.result.text", config.responseTextFieldPath)
        assertEquals(45_000, config.timeoutMillis)
    }

    @Test
    fun `非法超时被裁剪到允许范围`() {
        val repository = CloudAsrConfigRepository(FakeSharedPreferences())

        repository.updateConfig(CloudAsrConfig(timeoutMillis = 5))
        assertEquals(CloudAsrConfig.MIN_TIMEOUT_MILLIS, repository.getConfig().timeoutMillis)

        repository.updateConfig(CloudAsrConfig(timeoutMillis = 999_999))
        assertEquals(CloudAsrConfig.MAX_TIMEOUT_MILLIS, repository.getConfig().timeoutMillis)
    }

    private class FakeSharedPreferences(
        private val values: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(values)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private class FakeEditor(
        private val values: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { pending[key.orEmpty()] = value }
        override fun remove(key: String?): SharedPreferences.Editor = apply { pending[key.orEmpty()] = null }
        override fun clear(): SharedPreferences.Editor = apply { values.clear() }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            pending.clear()
        }
    }
}
