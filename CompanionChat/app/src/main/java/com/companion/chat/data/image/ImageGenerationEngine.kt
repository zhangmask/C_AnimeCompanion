package com.companion.chat.data.image

import kotlinx.coroutines.flow.StateFlow

interface ImageGenerationEngine {
    val state: StateFlow<ImageGenerationState>

    suspend fun generate(
        prompt: String,
        config: ImageGenerationConfig,
        purpose: ImageGenerationPurpose = ImageGenerationPurpose.CHAT_SCENE
    ): Result<String>

    suspend fun generate(
        request: ImageGenerationRequest,
        config: ImageGenerationConfig
    ): Result<String> = generate(
        prompt = request.prompt,
        config = config,
        purpose = request.purpose
    )
}
