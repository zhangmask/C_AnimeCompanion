package com.companion.chat.data.memory

import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.MemoryEntity
import com.companion.chat.data.local.entity.MemoryLink
import com.companion.chat.data.local.entity.MetaMemory
import com.companion.chat.data.preferences.UnifiedExtractionParser
import com.companion.chat.data.preferences.UnifiedExtractionPromptBuilder

/**
 * 统一提取循环 — 单次 LLM 调用输出记忆 + 实体 + 链接 + 元记忆。
 *
 * 参考 OpenViking ExtractLoop + mem0 8-phase V3：
 * - 一次调用输出所有产物，避免三次独立 API 调用
 * - 后续去重 → 实体合并 → 批量写入
 */
class MemoryExtractLoop(
    private val memoryRepository: MemoryRepository,
    private val memoryGraphRepository: MemoryGraphRepository,
    private val promptBuilder: UnifiedExtractionPromptBuilder,
    private val parser: UnifiedExtractionParser
) {
    /**
     * 执行一次提取循环。
     */
    suspend fun execute(
        llmRawOutput: String,
        sessionId: String,
        roleCardId: Long? = null
    ): ExtractLoopResult {
        // 解析 LLM 原始输出 → 去重 → 批量写入
        val parseResult = parser.parseFull(llmRawOutput)

        // 去重与批量写入
        val storedMemories = mutableListOf<Memory>()
        val storedEntities = mutableListOf<MemoryEntity>()
        val storedLinks = mutableListOf<MemoryLink>()

        // 4a. 写入记忆（去重）
        for (extracted in parseResult.memories) {
            if (memoryRepository.findExactMatch(extracted.category, extracted.content) != null) continue
            val memory = memoryRepository.storeMemory(
                content = extracted.content,
                category = extracted.category,
                source = extracted.source,
                entityName = extracted.entityName,
                sessionId = sessionId,
                roleCardId = roleCardId
            )
            storedMemories.add(memory)
        }

        // 4b. 写入实体 + 建立关联
        for (extracted in parseResult.memories) {
            if (extracted.entityName != null) {
                val entity = memoryGraphRepository.findOrCreateEntity(
                    name = extracted.entityName,
                    type = "person"
                )
                storedEntities.add(entity)
                // 找到存储后的记忆 ID
                val storedMemory = storedMemories.find { it.content == extracted.content }
                if (storedMemory != null) {
                    memoryGraphRepository.linkEntityToMemory(entity.id, storedMemory.id)
                }
            }
        }

        // 4c. 写入链接
        for (link in parseResult.links) {
            if (link.fromMemoryIdx < storedMemories.size) {
                val fromMemory = storedMemories[link.fromMemoryIdx]
                // toEntityIdx 指向 entities 数组
                if (link.toEntityIdx < storedEntities.size) {
                    val toEntity = storedEntities[link.toEntityIdx]
                    // 找到该实体关联的所有记忆
                    val toMemories = memoryGraphRepository.getMemoriesForEntity(toEntity.id)
                    for (toMemory in toMemories) {
                        memoryGraphRepository.addLink(
                            fromId = fromMemory.id,
                            toId = toMemory.id,
                            linkType = link.linkType,
                            weight = link.weight
                        )
                        storedLinks.add(
                            MemoryLink(
                                fromId = fromMemory.id,
                                toId = toMemory.id,
                                linkType = link.linkType,
                                weight = link.weight,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }

        return ExtractLoopResult(
            memories = storedMemories,
            entities = storedEntities,
            links = storedLinks,
            metaMemories = parseResult.metaMemories.map {
                MetaMemory(
                    content = it.content,
                    category = it.category,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
    }
}

data class ExtractLoopResult(
    val memories: List<Memory> = emptyList(),
    val entities: List<MemoryEntity> = emptyList(),
    val links: List<MemoryLink> = emptyList(),
    val metaMemories: List<MetaMemory> = emptyList()
)
