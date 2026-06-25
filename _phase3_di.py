import os

# 1. AppContainer.kt — add SecondEngineManager, memoryExtractLoop, t1BatchProcessor
path1 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\AppContainer.kt'
with open(path1, 'r', encoding='utf-8') as f:
    c = f.read()

# Add imports
c = c.replace(
    'import com.companion.chat.data.memory.MemoryDecayManager\nimport com.companion.chat.data.memory.MemoryGraphRepository\nimport com.companion.chat.data.memory.MemoryPromptBuilder\nimport com.companion.chat.data.memory.MemoryRepository\nimport com.companion.chat.data.memory.PprRetriever\nimport com.companion.chat.data.preferences.PreferenceMemoryDeriver',
    'import com.companion.chat.data.memory.MemoryDecayManager\nimport com.companion.chat.data.memory.MemoryExtractLoop\nimport com.companion.chat.data.memory.MemoryGraphRepository\nimport com.companion.chat.data.memory.MemoryPromptBuilder\nimport com.companion.chat.data.memory.MemoryRepository\nimport com.companion.chat.data.memory.PprRetriever\nimport com.companion.chat.data.memory.T1BatchProcessor\nimport com.companion.chat.data.preferences.PreferenceMemoryDeriver\nimport com.companion.chat.data.preferences.SecondEngineManager')

# Add lazy inits
old_di = '    val memoryDecayManager: MemoryDecayManager by lazy { MemoryDecayManager(database.memoryDao()) }\n    // T1BatchProcessor requires SecondEngineManager from ChatViewModel scope\n    val unifiedExtractionPromptBuilder'
new_di = '    val memoryDecayManager: MemoryDecayManager by lazy { MemoryDecayManager(database.memoryDao()) }\n    val secondEngineManager: SecondEngineManager by lazy {\n        SecondEngineManager(application, modelConfigRepository)\n    }\n    val memoryExtractLoop: MemoryExtractLoop by lazy {\n        MemoryExtractLoop(\n            memoryRepository = memoryRepository,\n            memoryGraphRepository = memoryGraphRepository,\n            promptBuilder = unifiedExtractionPromptBuilder,\n            parser = unifiedExtractionParser\n        )\n    }\n    val t1BatchProcessor: T1BatchProcessor by lazy {\n        T1BatchProcessor(memoryRepository, memoryGraphRepository, secondEngineManager)\n    }\n    val unifiedExtractionPromptBuilder'

if old_di in c:
    c = c.replace(old_di, new_di, 1)
    with open(path1, 'w', encoding='utf-8') as f:
        f.write(c)
    print('1. AppContainer.kt OK')
else:
    print('1. AppContainer.kt FAILED')

# 2. CompanionChatApplication.kt — use container.t1BatchProcessor
path2 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\CompanionChatApplication.kt'
with open(path2, 'r', encoding='utf-8') as f:
    c = f.read()
c = c.replace(
    '            t1BatchProcessor = null,',
    '            t1BatchProcessor = appContainer.t1BatchProcessor,')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(c)
print('2. CompanionChatApplication.kt OK')

# 3. ChatViewModel.kt — use container.memoryExtractLoop
path3 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\chat\ChatViewModel.kt'
with open(path3, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace(
    '        memoryExtractLoop = MemoryExtractLoop(\n            memoryRepository = memoryRepository,\n            memoryGraphRepository = container.memoryGraphRepository,\n            promptBuilder = unifiedExtractionPromptBuilder,\n            parser = unifiedExtractionParser\n        ),',
    '        memoryExtractLoop = container.memoryExtractLoop,')
with open(path3, 'w', encoding='utf-8') as f:
    f.write(c)
print('3. ChatViewModel.kt OK')

print('\n=== Phase 3.1 DONE ===')
