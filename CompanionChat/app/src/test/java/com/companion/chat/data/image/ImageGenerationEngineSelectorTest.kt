package com.companion.chat.data.image

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationEngineSelectorTest {

    @Test
    fun `显式 HTTP provider 且配置存在时选择 HTTP`() {
        val selector = ImageGenerationEngineSelector(FakeImageEngine("http://image.png"), FakeImageEngine("local://image.png"))

        val provider = selector.chooseProvider(
            ImageGenerationConfig(
                baseUrl = "https://example.com",
                provider = ImageGenerationProvider.HTTP
            )
        )

        assertEquals(ImageGenerationProvider.HTTP, provider)
    }

    @Test
    fun `显式本地 provider 会返回本地不可用错误`() = runBlocking {
        val selector = ImageGenerationEngineSelector(FakeImageEngine("http://image.png"), LocalImageGenerationEngine())

        val result = selector.generate(
            request = ImageGenerationRequest(prompt = "avatar"),
            config = ImageGenerationConfig(provider = ImageGenerationProvider.LOCAL_DREAMLITE)
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("DreamLite 模型目录未配置"))
    }

    @Test
    fun `显式 Stable Diffusion provider 会选择本地引擎`() = runBlocking {
        val selector = ImageGenerationEngineSelector(FakeImageEngine("http://image.png"), FakeImageEngine("local://sd.png"))

        val result = selector.generate(
            request = ImageGenerationRequest(prompt = "avatar"),
            config = ImageGenerationConfig(
                baseUrl = "https://example.com",
                provider = ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP
            )
        )

        assertEquals(ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP, selector.chooseProvider(
            ImageGenerationConfig(
                baseUrl = "https://example.com",
                provider = ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP
            )
        ))
        assertEquals("local://sd.png", result.getOrThrow())
    }

    private class FakeImageEngine(private val uri: String) : ImageGenerationEngine {
        override val state: StateFlow<ImageGenerationState> = MutableStateFlow(ImageGenerationState.Idle)

        override suspend fun generate(
            prompt: String,
            config: ImageGenerationConfig,
            purpose: ImageGenerationPurpose
        ): Result<String> = Result.success(uri)
    }
}
