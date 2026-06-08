package com.companion.chat.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class MossTtsNanoModelPackageTest {

    @Test
    fun `模型目录完整时返回 Ready`() {
        val directory = createTempDirectory().toFile()
        MossTtsNanoModelPackage.REQUIRED_MODEL_FILES.forEach { relativePath ->
            File(directory, relativePath).apply {
                parentFile?.mkdirs()
                writeText(modelFileContent(relativePath))
            }
        }

        assertEquals(
            MossTtsNanoModelStatus.Ready,
            MossTtsNanoModelPackage.inspect(directory.absolutePath)
        )
    }

    @Test
    fun `模型目录缺失时列出必要文件`() {
        val directory = createTempDirectory().toFile()

        assertEquals(
            MossTtsNanoModelStatus.MissingFiles(MossTtsNanoModelPackage.REQUIRED_MODEL_FILES),
            MossTtsNanoModelPackage.inspect(directory.absolutePath)
        )
    }

    private fun modelFileContent(relativePath: String): String {
        return when (relativePath) {
            MossTtsNanoModelPackage.TTS_META_FILE_NAME -> """
                {
                  "files": {
                    "prefill": "moss_tts_prefill.onnx",
                    "decode_step": "moss_tts_decode_step.onnx",
                    "local_decoder": "moss_tts_local_decoder.onnx",
                    "local_cached_step": "moss_tts_local_cached_step.onnx",
                    "local_fixed_sampled_frame": "moss_tts_local_fixed_sampled_frame.onnx"
                  }
                }
            """.trimIndent()
            MossTtsNanoModelPackage.CODEC_META_FILE_NAME -> """
                {
                  "files": {
                    "encode": "moss_audio_tokenizer_encode.onnx",
                    "decode_full": "moss_audio_tokenizer_decode_full.onnx",
                    "decode_step": "moss_audio_tokenizer_decode_step.onnx"
                  },
                  "codec_config": {
                    "sample_rate": 48000,
                    "channels": 2
                  }
                }
            """.trimIndent()
            else -> "model"
        }
    }
}
