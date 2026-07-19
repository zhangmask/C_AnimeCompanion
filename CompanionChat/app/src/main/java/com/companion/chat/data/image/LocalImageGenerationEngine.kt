package com.companion.chat.data.image

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class LocalImageGenerationEngine(
    context: Context? = null
) : ImageGenerationEngine {

    private val imageFileStore = context?.let { ImageFileStore(it) }
    private val nativeLibraryDir: String? = context?.applicationInfo?.nativeLibraryDir
    private val cacheDir: File? = context?.cacheDir
    private val _state = MutableStateFlow<ImageGenerationState>(ImageGenerationState.Idle)
    override val state: StateFlow<ImageGenerationState> = _state.asStateFlow()

    override suspend fun generate(
        prompt: String,
        config: ImageGenerationConfig,
        purpose: ImageGenerationPurpose
    ): Result<String> {
        return generate(
            request = ImageGenerationRequest(prompt = prompt, purpose = purpose),
            config = config
        )
    }

    override suspend fun generate(
        request: ImageGenerationRequest,
        config: ImageGenerationConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext when (config.provider) {
            ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP -> generateStableDiffusion(request, config)
            else -> generateDreamLite(request, config)
        }
    }

    private fun generateDreamLite(
        request: ImageGenerationRequest,
        config: ImageGenerationConfig
    ): Result<String> {
        val store = imageFileStore
        if (store == null) {
            val error = "DreamLite 需要 Android Context 才能保存图片"
            _state.value = ImageGenerationState.Error(error)
            return Result.failure(IllegalStateException(error))
        }
        if (request.prompt.isBlank()) {
            val error = "DreamLite 提示词不能为空"
            _state.value = ImageGenerationState.Error(error)
            return Result.failure(IllegalArgumentException(error))
        }

        when (val status = DreamLiteModelPackage.inspect(config.localModelPath)) {
            DreamLiteModelStatus.Ready -> Unit
            DreamLiteModelStatus.DirectoryNotConfigured -> {
                val error = "DreamLite 模型目录未配置"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
            is DreamLiteModelStatus.InvalidConfig -> {
                val error = "DreamLite 配置无效：${status.message}"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
            is DreamLiteModelStatus.MissingFiles -> {
                val error = "DreamLite 模型文件缺失：${status.fileNames.joinToString()}"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
        }

        _state.value = ImageGenerationState.Generating
        return runCatching {
            // DreamLite UNet trained for 1024×1024 pixels (sample_size=128, vae_scale_factor=8).
            val width = 1024
            val height = 1024
            // Edit mode (img2img) runs full denoising from step 0 with
            // [noise | image_latents] conditioning — same step count as
            // txt2img. The previous 16-step + strength=0.15 SDEdit config is
            // no longer used because real edit mode does not skip steps.
            val isImg2Img = request.referenceLatentsPath.isNotEmpty()
            val steps = if (isImg2Img) {
                // Edit mode: same as txt2img (4 steps). Allow override via config.
                config.localSteps.takeIf { it > 0 } ?: 4
            } else {
                config.localSteps.takeIf { it > 0 } ?: 4
            }
            val seed = config.localSeed ?: System.currentTimeMillis()

            // Pre-allocate output image file so we can derive the latents path
            // for img2img reuse before the native call.
            val imageFile = store.nextImageFile(request.purpose)
            val outputLatentsPath = store.latentsPathFor(imageFile)

            // Run generation in a separate child process for memory isolation.
            //
            // ORT's scudo allocator leaves ~2.5GB of virtual address space
            // mappings that cannot be released by mallopt or by destroying the
            // Ort::Env. These mappings accumulate across generations and trigger
            // a system-level LMK kill on Gen 2+. By running each generation in
            // a fresh process (libdreamlite_worker.so), the OS kernel reclaims
            // ALL memory when the worker exits — physical RAM, virtual address
            // space, scudo mappings, ORT thread-pool reservations. This enables
            // unlimited consecutive generations with 4 ORT threads.
            val libDir = nativeLibraryDir
                ?: throw IllegalStateException("Cannot locate native library directory")
            val worker = File(libDir, "libdreamlite_worker.so")
            if (!worker.exists()) {
                throw IllegalStateException("DreamLite worker not found: ${worker.absolutePath}")
            }
            if (!worker.canExecute()) {
                worker.setExecutable(true, true)
            }

            // Use a temp file in the app cache dir for the worker's PNG output,
            // then copy to the final destination after the worker exits. The
            // .err sibling file is used by the worker to report error messages.
            val cache = cacheDir
                ?: throw IllegalStateException("Cannot locate app cache directory")
            val tempPng = File.createTempFile("dreamlite_out_", ".png", cache)
            val errFile = File(tempPng.absolutePath + ".err")
            try {
                val command = listOf(
                    worker.absolutePath,
                    config.localModelPath,
                    request.prompt,
                    width.toString(),
                    height.toString(),
                    steps.toString(),
                    seed.toString(),
                    request.referenceLatentsPath,
                    request.strength.toString(),
                    outputLatentsPath,
                    tempPng.absolutePath
                )

                val pb = ProcessBuilder(command).redirectErrorStream(true)
                // The worker is a standalone executable that dynamically links
                // libc++_shared.so and libonnxruntime.so. These live in the
                // app's nativeLibraryDir; add it to the linker search path.
                pb.environment()["LD_LIBRARY_PATH"] = libDir

                val process = pb.start()

                // Drain stdout/stderr on a background thread to prevent the
                // pipe buffer from filling up and blocking the worker.
                val outputText = StringBuilder()
                val drainThread = Thread {
                    try {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            outputText.appendLine(line)
                        }
                    } catch (_: Throwable) {
                        // Best-effort drain; the worker's exit code is the
                        // authoritative success/failure signal.
                    }
                }
                drainThread.isDaemon = true
                drainThread.start()

                // Generation typically takes ~85s with 4 threads; allow a
                // generous 5-minute timeout for slower devices.
                val finished = process.waitFor(5, TimeUnit.MINUTES)
                if (!finished) {
                    process.destroyForcibly()
                    throw IllegalStateException("DreamLite worker timed out after 5 minutes")
                }
                drainThread.join(2_000)

                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    val errMsg = if (errFile.exists()) {
                        errFile.readText()
                    } else {
                        "DreamLite worker failed (exit $exitCode)\n$outputText"
                    }
                    throw IllegalStateException(errMsg)
                }

                if (!tempPng.exists() || tempPng.length() == 0L) {
                    throw IllegalStateException(
                        "DreamLite worker produced no output\n$outputText"
                    )
                }

                // Copy the temp PNG to the final destination file.
                tempPng.copyTo(imageFile, overwrite = true)
                val uri = imageFile.toURI().toString()
                _state.value = ImageGenerationState.Success(uri)
                uri
            } finally {
                tempPng.delete()
                errFile.delete()
            }
        }.onFailure { error ->
            _state.value = ImageGenerationState.Error(error.message ?: "DreamLite 出图失败")
        }
    }

    private fun generateStableDiffusion(
        request: ImageGenerationRequest,
        config: ImageGenerationConfig
    ): Result<String> {
        val store = imageFileStore
        if (store == null) {
            val error = "本地 Stable Diffusion 需要 Android Context 才能保存图片"
            _state.value = ImageGenerationState.Error(error)
            return Result.failure(IllegalStateException(error))
        }
        if (request.prompt.isBlank()) {
            val error = "本地 Stable Diffusion 提示词不能为空"
            _state.value = ImageGenerationState.Error(error)
            return Result.failure(IllegalArgumentException(error))
        }

        val runtimeConfig = when (val status = StableDiffusionModelPackage.inspect(config.localModelPath)) {
            is StableDiffusionModelStatus.Ready -> status.config
            StableDiffusionModelStatus.DirectoryNotConfigured -> {
                val error = "Stable Diffusion 模型目录未配置"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
            is StableDiffusionModelStatus.InvalidConfig -> {
                val error = "Stable Diffusion 配置无效：${status.message}"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
            is StableDiffusionModelStatus.MissingFiles -> {
                val error = "Stable Diffusion 模型文件缺失：${status.fileNames.joinToString()}"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
        }

        _state.value = ImageGenerationState.Generating
        return runCatching {
            val pngBytes = StableDiffusionNative.generateTxt2ImgPng(
                modelPath = runtimeConfig.modelPath,
                vaePath = runtimeConfig.vaePath,
                taesdPath = runtimeConfig.taesdPath,
                loraPaths = runtimeConfig.loraPaths.toTypedArray(),
                prompt = request.prompt,
                negativePrompt = request.negativePrompt,
                width = config.localWidth.takeIf { it > 0 } ?: runtimeConfig.defaultWidth,
                height = config.localHeight.takeIf { it > 0 } ?: runtimeConfig.defaultHeight,
                steps = config.localSteps.takeIf { it > 0 } ?: runtimeConfig.defaultSteps,
                cfgScale = config.localCfgScale.takeIf { it >= 0f } ?: runtimeConfig.defaultCfgScale,
                seed = config.localSeed ?: runtimeConfig.defaultSeed ?: -1L,
                useVulkan = config.localUseVulkan && runtimeConfig.useVulkan
            )
            val uri = store.saveBytes(pngBytes, request.purpose)
            _state.value = ImageGenerationState.Success(uri)
            uri
        }.onFailure { error ->
            _state.value = ImageGenerationState.Error(error.message ?: "本地 Stable Diffusion 出图失败")
        }
    }
}
