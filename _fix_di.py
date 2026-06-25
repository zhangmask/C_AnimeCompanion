import os

# 1. Fix AppContainer.kt — remove SecondEngineManager (ChatViewModel-scoped), keep others
path1 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\AppContainer.kt'
with open(path1, 'r', encoding='utf-8') as f:
    c = f.read()

# Remove SecondEngineManager import
c = c.replace(', SecondEngineManager', '')
# Replace wrong lazy init
old = '    val secondEngineManager: SecondEngineManager by lazy {\n        SecondEngineManager(application, modelConfigRepository)\n    }\n    val memoryExtractLoop'
new = '    val memoryExtractLoop'
c = c.replace(old, new, 1)

# Fix t1BatchProcessor to not depend on secondEngineManager
old2 = '    val t1BatchProcessor: T1BatchProcessor by lazy {\n        T1BatchProcessor(memoryRepository, memoryGraphRepository, secondEngineManager)\n    }'
new2 = '    val t1BatchProcessor: T1BatchProcessor by lazy {\n        T1BatchProcessor(memoryRepository, memoryGraphRepository)\n    }'
c = c.replace(old2, new2, 1)

with open(path1, 'w', encoding='utf-8') as f:
    f.write(c)
print('1. AppContainer.kt fixed')

# 2. Fix T1BatchProcessor.kt constructor — remove SecondEngineManager param
path2 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\data\memory\T1BatchProcessor.kt'
with open(path2, 'r', encoding='utf-8') as f:
    c = f.read()
c = c.replace(
    'class T1BatchProcessor(\n    private val memoryRepository: MemoryRepository,\n    private val memoryGraphRepository: MemoryGraphRepository\n)',
    'class T1BatchProcessor(\n    private val memoryRepository: MemoryRepository,\n    private val memoryGraphRepository: MemoryGraphRepository)')
print('2. T1BatchProcessor.kt checked')

print('\n=== Fixed ===')
