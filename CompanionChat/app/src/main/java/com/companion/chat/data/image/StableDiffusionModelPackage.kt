package com.companion.chat.data.image

import org.json.JSONException
import org.json.JSONObject
import java.io.File

object StableDiffusionModelPackage {
    const val DEFAULT_MODEL_RELATIVE_DIRECTORY = "models/image/sd15-hypersd"
    const val CONFIG_FILE_NAME = "sd_config.json"

    fun inspect(modelDirectory: String): StableDiffusionModelStatus {
        val directoryPath = modelDirectory.trim()
        if (directoryPath.isBlank()) return StableDiffusionModelStatus.DirectoryNotConfigured

        val directory = File(directoryPath)
        if (!directory.isDirectory) {
            return StableDiffusionModelStatus.MissingFiles(listOf(CONFIG_FILE_NAME))
        }

        val configFile = File(directory, CONFIG_FILE_NAME)
        if (!configFile.isFile) {
            return StableDiffusionModelStatus.MissingFiles(listOf(CONFIG_FILE_NAME))
        }

        val config = runCatching { JSONObject(configFile.readText()) }
            .getOrElse { error ->
                return StableDiffusionModelStatus.InvalidConfig(error.message ?: "配置 JSON 解析失败")
            }

        val missingFields = REQUIRED_CONFIG_FIELDS.filter { field ->
            !config.has(field) || config.optString(field).isBlank()
        }
        if (missingFields.isNotEmpty()) {
            return StableDiffusionModelStatus.InvalidConfig("缺少字段：${missingFields.joinToString()}")
        }

        if (config.optString("runtime") != "stable-diffusion.cpp") {
            return StableDiffusionModelStatus.InvalidConfig("runtime 必须是 stable-diffusion.cpp")
        }

        val declaredFiles = parseRequiredFiles(config).getOrElse { error ->
            return StableDiffusionModelStatus.InvalidConfig(error.message ?: "required_files 格式无效")
        }
        val requiredFiles = (declaredFiles + config.optString("model_path"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val missingFiles = requiredFiles.filterNot { File(directory, it).isFile }

        return if (missingFiles.isEmpty()) {
            StableDiffusionModelStatus.Ready(parseConfig(directory, config))
        } else {
            StableDiffusionModelStatus.MissingFiles(missingFiles)
        }
    }

    private fun parseConfig(directory: File, config: JSONObject): StableDiffusionRuntimeConfig {
        val loraPaths = parseStringArray(config, "lora_paths").getOrDefault(emptyList())
        return StableDiffusionRuntimeConfig(
            modelName = config.optString("model_name"),
            modelPath = File(directory, config.optString("model_path")).absolutePath,
            vaePath = config.optString("vae_path").takeIf { it.isNotBlank() }
                ?.let { File(directory, it).absolutePath }
                .orEmpty(),
            taesdPath = config.optString("taesd_path").takeIf { it.isNotBlank() }
                ?.let { File(directory, it).absolutePath }
                .orEmpty(),
            loraPaths = loraPaths.map { File(directory, it).absolutePath },
            defaultSteps = config.optInt("default_steps", 4).coerceIn(1, 50),
            defaultWidth = config.optInt("default_width", 512).coerceIn(128, 2048),
            defaultHeight = config.optInt("default_height", 512).coerceIn(128, 2048),
            defaultCfgScale = config.optDouble("default_cfg_scale", 1.0).toFloat().coerceIn(0f, 30f),
            defaultSeed = if (config.has("default_seed")) config.optLong("default_seed") else null,
            useVulkan = config.optBoolean("use_vulkan", true)
        )
    }

    private fun parseRequiredFiles(config: JSONObject): Result<List<String>> =
        parseStringArray(config, "required_files")

    private fun parseStringArray(config: JSONObject, key: String): Result<List<String>> = runCatching {
        if (!config.has(key)) return@runCatching emptyList()
        val array = config.getJSONArray(key)
        (0 until array.length()).map { index ->
            array.getString(index).trim()
        }.filter { it.isNotBlank() }
    }.recoverCatching { error ->
        if (error is JSONException) throw IllegalArgumentException("$key 必须是字符串数组", error)
        throw error
    }

    private val REQUIRED_CONFIG_FIELDS = listOf("model_name", "runtime", "model_path")
}

data class StableDiffusionRuntimeConfig(
    val modelName: String,
    val modelPath: String,
    val vaePath: String,
    val taesdPath: String,
    val loraPaths: List<String>,
    val defaultSteps: Int,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val defaultCfgScale: Float,
    val defaultSeed: Long?,
    val useVulkan: Boolean
)

sealed class StableDiffusionModelStatus {
    data class Ready(val config: StableDiffusionRuntimeConfig) : StableDiffusionModelStatus()
    data object DirectoryNotConfigured : StableDiffusionModelStatus()
    data class MissingFiles(val fileNames: List<String>) : StableDiffusionModelStatus()
    data class InvalidConfig(val message: String) : StableDiffusionModelStatus()
}
