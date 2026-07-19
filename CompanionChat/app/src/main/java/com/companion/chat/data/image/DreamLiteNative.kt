package com.companion.chat.data.image

internal object DreamLiteNative {
    init {
        System.loadLibrary("companion_dreamlite")
    }

    /**
     * Load DreamLite models, run the full inference pipeline, and return the
     * result as a PNG-encoded byte array.
     *
     * @param modelDir  Absolute path to the directory containing all model files
     * @param prompt    Text prompt for image generation
     * @param width     Output image width in pixels (128–2048)
     * @param height    Output image height in pixels (128–2048)
     * @param steps     Number of denoising steps (1–50)
     * @param seed      Random seed for reproducibility
     * @param referenceLatentsPath  Path to saved latents file for img2img editing.
     *                              Empty string = text-to-image (pure noise).
     * @param strength  img2img strength (0=no change, 1=full regeneration, 0.6=moderate edit)
     * @param outputLatentsPath     Where to save final denoised latents for future reuse.
     *                              Empty string = don't save.
     * @return PNG image bytes
     */
    external fun generateImagePng(
        modelDir: String,
        prompt: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long,
        referenceLatentsPath: String,
        strength: Float,
        outputLatentsPath: String
    ): ByteArray

    /**
     * Returns basic system / device info for diagnostics.
     */
    fun systemInfo(): String = "DreamLite ONNX Runtime JNI (companion_dreamlite)"
}
