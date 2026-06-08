# DDD Boundary Design

This document records the target domain boundaries for Anime Companion after reading the current product docs, architecture plans, and Android code. It is a refactoring guide, not an immediate implementation mandate.

## Current Shape

The product is already organized around a strong domain idea: a local, privacy-first **AI 伴侣** with persistent identity, memory, preference learning, voice interaction, and optional task Skills.

The current code works, but much of the runtime orchestration is concentrated in `ChatViewModel`. That ViewModel currently coordinates UI state, message submission, RoleCard and Skill prompt composition, memory retrieval, preference injection, context rebuilding, model inference, persistence, voice behavior, image generation, and background learning.

## Target Boundaries

### companion

Owns the application flow for one AI companion turn.

Expected responsibility:

- Coordinate active **RoleCard**, active **Skill**, **Memory**, **UserPreference**, and **ConversationSession**
- Build the companion turn input
- Call the inference port
- Persist the resulting turn
- Trigger post-turn learning

Candidate future service:

- `CompanionRuntime`
- `CompanionTurnService`
- `CompanionConversationOrchestrator`

Recommended name: `CompanionRuntime`.

This module should not own UI state or concrete model implementations.

### conversation

Owns chat records.

Domain concepts:

- **ConversationSession**
- message

Current files that likely belong here:

- `data/model/ChatMessage.kt`
- `data/repository/ChatSessionRepository.kt`
- `data/local/entity/ConversationEntity.kt`
- `data/local/entity/MessageEntity.kt`
- `data/local/dao/ConversationDao.kt`
- `data/local/dao/MessageDao.kt`

Boundary rule:

- A **ConversationSession** is a chat record, not relationship progress.

### identity

Owns companion identity.

Domain concepts:

- **RoleCard**
- **DiscoverRoleCard**

Current files that likely belong here:

- `data/local/entity/RoleCard.kt`
- `data/role/RoleCardRepository.kt`
- `data/role/RoleCardPromptBuilder.kt`
- `data/discover/*`
- role management UI ViewModels and screens

Boundary rule:

- **RoleCard** defines who the **AI 伴侣** is.
- **DiscoverRoleCard** is only a browsable candidate until imported.
- Identity is not a Skill, skin, model runtime, voice engine, or image provider.

### memory

Owns remembered user continuity.

Domain concepts:

- **Memory**
- **Short-term Memory**
- **Long-term Memory**

Current files that likely belong here:

- `data/local/entity/Memory.kt`
- `data/local/dao/MemoryDao.kt`
- `data/memory/*`
- memory screen ViewModel and state

Boundary rule:

- Short-term and long-term are lifecycle layers of **Memory**, not separate domain object families.
- **Memory** is not **UserPreference**, chat history, or model summary.

### preference

Owns stable user preference signals.

Domain concepts:

- **UserPreference**

Current files that likely belong here:

- `data/local/entity/UserPreference.kt`
- `data/local/dao/PreferenceDao.kt`
- `data/preferences/*`
- `ui/chat/PreferenceLearningCoordinator.kt` after it is separated from UI concerns

Boundary rule:

- A one-off user statement may become **Memory** immediately.
- It becomes **UserPreference** only after repeated evidence or confirmation.
- A **UserPreference** may derive a **Memory**, but it is not itself a **Memory**.

### capability

Owns user-facing capability channels and task modes.

Domain concepts:

- **Skill**
- **Voice Interaction**
- **Image Generation**

Current files that likely belong here:

- `data/local/entity/Skill.kt`
- `data/local/dao/SkillDao.kt`
- `data/skill/SkillRepository.kt`
- capability-facing image and voice configuration models, where they express user-facing capability state

Boundary rule:

- **Skill** changes task behavior without replacing the **AI 伴侣**.
- **Voice Interaction** and **Image Generation** are capability channels, not companion identity.
- Concrete model providers do not belong to this domain layer.

### engine

Owns technical ports and adapters.

Current files that likely belong here:

- `data/engine/*`
- `engine/*`
- local inference adapters
- ASR adapters
- TTS adapters
- stable diffusion and HTTP provider adapters

Boundary rule:

- `InferenceEngine`, `VoiceInputEngine`, `VoiceOutputEngine`, and `ImageGenerationEngine` are ports or adapters.
- LiteRT-LM, llama.cpp, SenseVoice, MOSS TTS, Stable Diffusion, and HTTP providers are infrastructure details.

### ui

Owns Compose screens, UI state, and user actions.

Current files that likely belong here:

- `ui/*`
- `MainActivity.kt`
- navigation and theme files

Boundary rule:

- ViewModels translate user actions into application service calls.
- ViewModels should not own companion-turn orchestration long term.

## Recommended Refactoring Path

1. Keep the current package layout stable until product behavior is safe.
2. Extract a `CompanionRuntime` facade without moving persistence or engine code.
3. Move turn orchestration from `ChatViewModel` into `CompanionRuntime` behind a small API.
4. Split post-turn learning coordination out of UI and let `CompanionRuntime` trigger it.
5. Only after behavior is covered by tests, consider package moves toward `conversation`, `identity`, `memory`, `preference`, `capability`, and `engine`.

## First Candidate API

```kotlin
class CompanionRuntime {
    suspend fun submitTurn(request: CompanionTurnRequest): CompanionTurnResult
    suspend fun rebuildForIdentityChange(reason: String)
    suspend fun rebuildForSkillChange(reason: String)
}
```

The first extraction should be intentionally boring: preserve behavior, reduce `ChatViewModel` responsibility, and avoid changing persistence schemas.

## Non-goals

- Do not introduce **Relationship State** yet.
- Do not split short-term and long-term memory into separate model families.
- Do not make model runtimes part of the domain model.
- Do not move files mechanically before the orchestration boundary is tested.

## Related Domain Records

- `CONTEXT.md`
- `docs/adr/0001-context-window-is-application-orchestration.md`
- `docs/adr/0002-companion-runtime-as-future-application-service.md`
- `docs/adr/0003-relationship-state-is-future-first-class-model.md`
- `docs/adr/0004-model-engines-are-technical-adapters.md`
