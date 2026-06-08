package com.companion.chat.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class StableDiffusionModelPackageTest {

    @Test
    fun `目录存在但配置缺失时返回缺失项`() {
        val directory = createTempDirectory().toFile()

        val status = StableDiffusionModelPackage.inspect(directory.absolutePath)

        assertEquals(
            StableDiffusionModelStatus.MissingFiles(listOf(StableDiffusionModelPackage.CONFIG_FILE_NAME)),
            status
        )
    }

    @Test
    fun `runtime 不是 stable diffusion cpp 时返回校验错误`() {
        val directory = createTempDirectory().toFile()
        File(directory, StableDiffusionModelPackage.CONFIG_FILE_NAME).writeText(
            """{"model_name":"sd15","runtime":"ncnn","model_path":"model.safetensors"}"""
        )

        val status = StableDiffusionModelPackage.inspect(directory.absolutePath)

        assertTrue(status is StableDiffusionModelStatus.InvalidConfig)
        assertTrue((status as StableDiffusionModelStatus.InvalidConfig).message.contains("stable-diffusion.cpp"))
    }

    @Test
    fun `配置完整且文件存在时返回运行配置`() {
        val directory = createTempDirectory().toFile()
        File(directory, "sd15.safetensors").writeText("fake")
        File(directory, "hyper.safetensors").writeText("fake")
        File(directory, StableDiffusionModelPackage.CONFIG_FILE_NAME).writeText(
            """
            {
              "model_name": "SD1.5 Hyper-SD",
              "runtime": "stable-diffusion.cpp",
              "model_path": "sd15.safetensors",
              "lora_paths": ["hyper.safetensors"],
              "required_files": ["sd15.safetensors", "hyper.safetensors"],
              "default_steps": 4,
              "default_width": 512,
              "default_height": 512,
              "default_cfg_scale": 1.0,
              "use_vulkan": true
            }
            """.trimIndent()
        )

        val status = StableDiffusionModelPackage.inspect(directory.absolutePath)

        assertTrue(status is StableDiffusionModelStatus.Ready)
        val config = (status as StableDiffusionModelStatus.Ready).config
        assertEquals("SD1.5 Hyper-SD", config.modelName)
        assertEquals(listOf(File(directory, "hyper.safetensors").absolutePath), config.loraPaths)
        assertEquals(4, config.defaultSteps)
    }
}
