package com.companion.chat

import android.app.Application
import com.companion.chat.data.context.ContextConfigRepository
import com.companion.chat.data.context.DefaultContextManager
import com.companion.chat.data.context.PromptAssembler
import com.companion.chat.data.discover.DiscoverRoleRepository
import com.companion.chat.data.engine.ModelConfigRepository
import com.companion.chat.data.image.HttpImageGenerationEngine
import com.companion.chat.data.image.ImageGenerationConfigRepository
import com.companion.chat.data.image.ImageGenerationEngineSelector
import com.companion.chat.data.image.LocalImageGenerationEngine
import com.companion.chat.data.local.CompanionDatabase
import com.companion.chat.data.embedding.OnnxEmbeddingEngine
import com.companion.chat.data.embedding.VectorRetriever
import com.companion.chat.data.memory.MemoryDecayManager
import com.companion.chat.data.memory.MemoryExtractLoop
import com.companion.chat.data.memory.MemoryGraphRepository
import com.companion.chat.data.memory.MemoryPromptBuilder
import com.companion.chat.data.memory.MemoryRepository
import com.companion.chat.data.memory.PprRetriever
import com.companion.chat.data.memory.T1BatchProcessor
import com.companion.chat.data.preferences.PreferenceMemoryDeriver
import com.companion.chat.data.preferences.PreferenceRepository
import com.companion.chat.data.preferences.UnifiedExtractionParser
import com.companion.chat.data.preferences.UnifiedExtractionPromptBuilder
import com.companion.chat.data.repository.ChatSessionRepository
import com.companion.chat.data.profile.UserProfileRepository
import com.companion.chat.data.role.RoleCardPromptBuilder
import com.companion.chat.data.role.RoleCardRepository
import com.companion.chat.data.skill.SkillRepository
import com.companion.chat.data.voice.CloudAsrConfigRepository
import com.companion.chat.data.voice.VoiceCloneConfigRepository
import com.companion.chat.data.voice.VoiceInputConfigRepository
import com.companion.chat.data.voice.VoiceOutputSettingsRepository
import com.companion.chat.engine.AndroidVoiceInputEngine
import com.companion.chat.engine.AndroidVoiceOutputEngine
import com.companion.chat.engine.InferenceEngineFactory
import com.companion.chat.engine.LocalAudioPlaybackEngine
import com.companion.chat.engine.MossTtsNanoVoiceCloneEngine
import com.companion.chat.engine.RoleAwareVoiceOutputEngine

class AppContainer(
    private val application: Application
) {
    val database: CompanionDatabase by lazy { CompanionDatabase.getInstance(application) }

    val modelConfigRepository: ModelConfigRepository by lazy { ModelConfigRepository(application) }
    val contextConfigRepository: ContextConfigRepository by lazy { ContextConfigRepository(application) }
    val imageGenerationConfigRepository: ImageGenerationConfigRepository by lazy {
        ImageGenerationConfigRepository(application)
    }
    val voiceInputConfigRepository: VoiceInputConfigRepository by lazy { VoiceInputConfigRepository(application) }
    val cloudAsrConfigRepository: CloudAsrConfigRepository by lazy { CloudAsrConfigRepository(application) }
    val voiceCloneConfigRepository: VoiceCloneConfigRepository by lazy { VoiceCloneConfigRepository(application) }
    val voiceOutputSettingsRepository: VoiceOutputSettingsRepository by lazy { VoiceOutputSettingsRepository(application) }

    val chatSessionRepository: ChatSessionRepository by lazy { ChatSessionRepository(application, database) }
    val memoryRepository: MemoryRepository by lazy { MemoryRepository(database.memoryDao()) }
    val preferenceRepository: PreferenceRepository by lazy { PreferenceRepository(database.preferenceDao()) }
    val roleCardRepository: RoleCardRepository by lazy { RoleCardRepository(database.roleCardDao()) }
    val skillRepository: SkillRepository by lazy { SkillRepository(database.skillDao()) }
    val userProfileRepository: UserProfileRepository by lazy {
        UserProfileRepository(application)
    }
    val discoverRoleRepository: DiscoverRoleRepository by lazy {
        DiscoverRoleRepository(
            context = application,
            roleCardRepository = roleCardRepository
        )
    }

    val inferenceEngineFactory: InferenceEngineFactory by lazy { InferenceEngineFactory(application) }
    val voiceInputEngine: AndroidVoiceInputEngine by lazy { AndroidVoiceInputEngine(application) }
    val androidVoiceOutputEngine: AndroidVoiceOutputEngine by lazy { AndroidVoiceOutputEngine(application) }
    val localAudioPlaybackEngine: LocalAudioPlaybackEngine by lazy { LocalAudioPlaybackEngine(application) }
    val mossTtsNanoVoiceCloneEngine: MossTtsNanoVoiceCloneEngine by lazy {
        MossTtsNanoVoiceCloneEngine(
            context = application,
            modelDirectoryProvider = { voiceCloneConfigRepository.getConfig().mossModelDirectory }
        )
    }
    val voiceOutputEngine: RoleAwareVoiceOutputEngine by lazy {
        RoleAwareVoiceOutputEngine(
            fallbackEngine = androidVoiceOutputEngine,
            roleCardRepository = roleCardRepository,
            cloneEngine = mossTtsNanoVoiceCloneEngine,
            localAudioPlaybackEngine = localAudioPlaybackEngine,
            defaultReferenceAudioProvider = { voiceCloneConfigRepository.getDefaultReferenceAudioUri() }
        )
    }

    val imageGenerationEngine: HttpImageGenerationEngine by lazy { HttpImageGenerationEngine(application) }
    val imageGenerationEngineSelector: ImageGenerationEngineSelector by lazy {
        ImageGenerationEngineSelector(
            httpEngine = imageGenerationEngine,
            localEngine = LocalImageGenerationEngine(application)
        )
    }

    val embeddingEngine: OnnxEmbeddingEngine by lazy { OnnxEmbeddingEngine(application) }
    val vectorRetriever: VectorRetriever by lazy { VectorRetriever(embeddingEngine) }

    val contextManager: DefaultContextManager by lazy {
        DefaultContextManager(inferenceEngineProvider = null)
    }
    val promptAssembler: PromptAssembler by lazy { PromptAssembler() }
    val roleCardPromptBuilder: RoleCardPromptBuilder by lazy { RoleCardPromptBuilder() }
    val memoryPromptBuilder: MemoryPromptBuilder by lazy { MemoryPromptBuilder() }
    val memoryGraphRepository: MemoryGraphRepository by lazy {
        MemoryGraphRepository(database.memoryLinkDao(), database.memoryEntityDao())
    }
    val pprRetriever: PprRetriever by lazy {
        PprRetriever(database.memoryDao(), database.memoryLinkDao(), database.memoryEntityDao())
    }
    val memoryDecayManager: MemoryDecayManager by lazy { MemoryDecayManager(database.memoryDao()) }
    val memoryExtractLoop: MemoryExtractLoop by lazy {
        MemoryExtractLoop(
            memoryRepository = memoryRepository,
            memoryGraphRepository = memoryGraphRepository,
            promptBuilder = unifiedExtractionPromptBuilder,
            parser = unifiedExtractionParser
        )
    }
    val t1BatchProcessor: T1BatchProcessor by lazy {
        T1BatchProcessor(memoryRepository)
    }
    val unifiedExtractionPromptBuilder: UnifiedExtractionPromptBuilder by lazy {
        UnifiedExtractionPromptBuilder()
    }
    val unifiedExtractionParser: UnifiedExtractionParser by lazy { UnifiedExtractionParser() }
    val preferenceMemoryDeriver: PreferenceMemoryDeriver by lazy { PreferenceMemoryDeriver() }
    // MemoryExtractLoop instantiated in ChatViewModel scope

    // 以下为遗留 DI，后续逐步迁移


    /** 应用启动时预热关键模型 */
    suspend fun warmUp() {
        // 预热 MOSS TTS 语音克隆模型
        try {
            mossTtsNanoVoiceCloneEngine.warmUp()
        } catch (e: Exception) {
            android.util.Log.e("AppContainer", "MOSS TTS 预热失败: ${e.message}", e)
        }
    }
}

val Application.appContainer: AppContainer
    get() = (this as CompanionChatApplication).appContainer
