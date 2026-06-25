import os, re

# 1. MemoryRepository.kt - use MemoryConfig constants
path1 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\data\memory\MemoryRepository.kt'
with open(path1, 'r', encoding='utf-8') as f:
    c = f.read()
c = c.replace('import com.companion.chat.data.local.dao.FtsQueryHelper\nimport com.companion.chat.data.local.dao.MemoryDao\nimport com.companion.chat.data.local.dao.MemoryEntityDao\nimport com.companion.chat.data.local.dao.MemoryLinkDao\nimport com.companion.chat.data.local.entity.Memory\nimport com.companion.chat.data.local.entity.MemoryEntity\nimport kotlinx.coroutines.flow.Flow',
    'import com.companion.chat.data.local.dao.FtsQueryHelper\nimport com.companion.chat.data.local.dao.MemoryDao\nimport com.companion.chat.data.local.entity.Memory\nimport kotlinx.coroutines.flow.Flow')
c = c.replace('            strength = 0.6,', '            strength = MemoryConfig.INITIAL_STRENGTH,')
c = c.replace('        threshold: Float = 0.85f,', '        threshold: Float = MemoryConfig.SEMANTIC_DEDUP_THRESHOLD,')
with open(path1, 'w', encoding='utf-8') as f:
    f.write(c)
print('1. MemoryRepository.kt OK')

# 2. MemoryPromptBuilder.kt - use MemoryConfig.DEFAULT_TOKEN_BUDGET
path2 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\data\memory\MemoryPromptBuilder.kt'
with open(path2, 'r', encoding='utf-8') as f:
    c = f.read()
c = c.replace('        tokenBudget: Int = 1200,', '        tokenBudget: Int = MemoryConfig.DEFAULT_TOKEN_BUDGET,')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(c)
print('2. MemoryPromptBuilder.kt OK')

# 3. MemoryLifecycleManager.kt - use MemoryConfig.CLEANUP_THRESHOLD
path3 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\data\memory\MemoryLifecycleManager.kt'
with open(path3, 'r', encoding='utf-8') as f:
    c = f.read()
c = c.replace('val cleaned = manager.cleanupWeakMemories(0.05)', 'val cleaned = manager.cleanupWeakMemories(MemoryConfig.CLEANUP_THRESHOLD)')
with open(path3, 'w', encoding='utf-8') as f:
    f.write(c)
print('3. MemoryLifecycleManager.kt OK')

# 4. CompanionChatApplication.kt - use MemoryConfig.CLEANUP_THRESHOLD
path4 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\CompanionChatApplication.kt'
with open(path4, 'r', encoding='utf-8') as f:
    c = f.read()
# Add MemoryConfig import
c = c.replace('import com.companion.chat.data.memory.MemoryLifecycleManager',
    'import com.companion.chat.data.memory.MemoryConfig\nimport com.companion.chat.data.memory.MemoryLifecycleManager')
with open(path4, 'w', encoding='utf-8') as f:
    f.write(c)
print('4. CompanionChatApplication.kt OK')

# 5. Clean up temp scripts (Phase 2.1)
import shutil
target_dir = r'C:\Users\72952\OneDrive\Desktop\ui'
count = 0
for f in os.listdir(target_dir):
    if f.startswith('_fix_') and f.endswith('.py'):
        os.remove(os.path.join(target_dir, f))
        count += 1
    elif f.startswith('fix_') and f.endswith('.py'):
        os.remove(os.path.join(target_dir, f))
        count += 1
    elif f in ('_run_build.py', '_check_errors.py', 'build_check.bat'):
        os.remove(os.path.join(target_dir, f))
        count += 1
print(f'5. Cleaned {count} temp files')

# Also clean locale temp file
locale_file = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\locale\_edit_strings.py'
if os.path.exists(locale_file):
    os.remove(locale_file)
    print('   locale _edit_strings.py removed')

print('\nDone')
