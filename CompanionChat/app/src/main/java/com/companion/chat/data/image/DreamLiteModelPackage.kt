package com.companion.chat.data.image

import org.json.JSONException
import org.json.JSONObject
import java.io.File

object DreamLiteModelPackage {
    const val DEFAULT_MODEL_RELATIVE_DIRECTORY = "models/image/dreamlite"
    const val CONFIG_FILE_NAME = "dreamlite_config.json"

    fun inspect(modelDirectory: String): DreamLiteModelStatus {
        val directoryPath = modelDirectory.trim()
        if (directoryPath.isBlank()) return DreamLiteModelStatus.DirectoryNotConfigured

        val directory = File(directoryPath)
        if (!directory.isDirectory) {
            return DreamLiteModelStatus.MissingFiles(listOf(CONFIG_FILE_NAME))
        }

        val configFile = File(directory, CONFIG_FILE_NAME)
        if (!configFile.isFile) {
            return DreamLiteModelStatus.MissingFiles(listOf(CONFIG_FILE_NAME))
        }

        val config = runCatching { JSONObject(configFile.readText()) }
            .getOrElse { error ->
                return DreamLiteModelStatus.InvalidConfig(error.message ?: "配置 JSON 解析失败")
            }

        val missingFields = REQUIRED_CONFIG_FIELDS.filter { field ->
            !config.has(field) || config.optString(field).isBlank()
        }
        if (missingFields.isNotEmpty()) {
            return DreamLiteModelStatus.InvalidConfig("缺少字段：${missingFields.joinToString()}")
        }

        val declaredFiles = parseRequiredFiles(config).getOrElse { error ->
            return DreamLiteModelStatus.InvalidConfig(error.message ?: "required_files 格式无效")
        }
        val missingFiles = declaredFiles.filterNot { File(directory, it).isFile }
        return if (missingFiles.isEmpty()) {
            DreamLiteModelStatus.Ready
        } else {
            DreamLiteModelStatus.MissingFiles(missingFiles)
        }
    }

    private fun parseRequiredFiles(config: JSONObject): Result<List<String>> = runCatching {
        if (!config.has("required_files")) return@runCatching emptyList()
        val array = config.getJSONArray("required_files")
        (0 until array.length()).map { index ->
            array.getString(index).trim()
        }.filter { it.isNotBlank() }
    }.recoverCatching { error ->
        if (error is JSONException) throw IllegalArgumentException("required_files 必须是字符串数组", error)
        throw error
    }

    private val REQUIRED_CONFIG_FIELDS = listOf("model_name", "runtime")
}

sealed class DreamLiteModelStatus {
    data object Ready : DreamLiteModelStatus()
    data object DirectoryNotConfigured : DreamLiteModelStatus()
    data class MissingFiles(val fileNames: List<String>) : DreamLiteModelStatus()
    data class InvalidConfig(val message: String) : DreamLiteModelStatus()
}
