package com.companion.chat.data.voice

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class VoiceInputConfigRepositoryTest {

    @Test
    fun `默认使用本地 SenseVoice 识别`() {
        val repository = VoiceInputConfigRepository(
            sharedPreferences = FakeSharedPreferences(),
            defaultModelDirectoryProvider = { "/sdcard/Android/data/com.companion.chat/files/models/asr/sensevoice" }
        )

        val config = repository.getConfig()

        assertEquals(VoiceInputBackend.LOCAL_SENSEVOICE, config.backend)
        assertEquals("本地多语言识别", config.recognitionModeLabel)
        assertEquals(
            "/sdcard/Android/data/com.companion.chat/files/models/asr/sensevoice",
            config.localSenseVoiceModelDirectory
        )
    }

    @Test
    fun `旧系统识别后端自动迁移为本地 SenseVoice`() {
        val repository = VoiceInputConfigRepository(
            sharedPreferences = FakeSharedPreferences(mutableMapOf("backend" to "SYSTEM_SPEECH_RECOGNIZER"))
        )

        assertEquals(VoiceInputBackend.LOCAL_SENSEVOICE, repository.getConfig().backend)
    }

    @Test
    fun `旧本地多语言后端自动迁移为本地 SenseVoice`() {
        val repository = VoiceInputConfigRepository(
            sharedPreferences = FakeSharedPreferences(mutableMapOf("backend" to "LOCAL_MULTILINGUAL_ASR"))
        )

        assertEquals(VoiceInputBackend.LOCAL_SENSEVOICE, repository.getConfig().backend)
    }

    @Test
    fun `配置写入后可重新读取`() {
        val sharedPreferences = FakeSharedPreferences()
        val repository = VoiceInputConfigRepository(sharedPreferences)

        repository.updateConfig(
            VoiceInputConfig(
                backend = VoiceInputBackend.CLOUD_HTTP_ASR,
                localSenseVoiceModelDirectory = " /sdcard/models/sensevoice "
            )
        )

        val reloadedConfig = VoiceInputConfigRepository(sharedPreferences).getConfig()
        assertEquals(VoiceInputBackend.CLOUD_HTTP_ASR, reloadedConfig.backend)
        assertEquals("/sdcard/models/sensevoice", reloadedConfig.localSenseVoiceModelDirectory)
    }

    @Test
    fun `模型目录缺失时返回明确错误`() {
        val repository = VoiceInputConfigRepository(FakeSharedPreferences())
        val config = VoiceInputConfig(localSenseVoiceModelDirectory = "")

        assertEquals(
            LocalSenseVoiceModelStatus.DirectoryNotConfigured,
            repository.getLocalSenseVoiceModelStatus(config)
        )
    }

    @Test
    fun `模型文件缺失时列出缺失项`() {
        val directory = createTempDirectory().toFile()
        File(directory, "tokens.txt").writeText("a 1")
        val repository = VoiceInputConfigRepository(FakeSharedPreferences())

        val status = repository.getLocalSenseVoiceModelStatus(
            VoiceInputConfig(localSenseVoiceModelDirectory = directory.absolutePath)
        )

        assertEquals(
            LocalSenseVoiceModelStatus.MissingFiles(listOf("model.int8.onnx", "silero_vad.onnx")),
            status
        )
    }

    @Test
    fun `模型文件完整时返回 Ready`() {
        val directory = createTempDirectory().toFile()
        VoiceInputConfigRepository.REQUIRED_LOCAL_SENSEVOICE_FILES.forEach {
            File(directory, it).writeText("placeholder")
        }
        val repository = VoiceInputConfigRepository(FakeSharedPreferences())

        assertEquals(
            LocalSenseVoiceModelStatus.Ready,
            repository.getLocalSenseVoiceModelStatus(
                VoiceInputConfig(localSenseVoiceModelDirectory = directory.absolutePath)
            )
        )
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
                if (value == null) values.remove(key) else values[key] = value
            }
            pending.clear()
            clearRequested = false
        }
    }
}
