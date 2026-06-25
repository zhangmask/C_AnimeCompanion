path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\chat\ChatViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Fix 1: replace contextConfigRepository.getLanguage() with languageRepo.getLanguage()
c = c.replace(
    'appLanguage = contextConfigRepository.getLanguage()',
    'appLanguage = languageRepo.getLanguage()')

# Fix 2: change const val DEFALT_BASE_SYSTEM_PROMPT to val
c = c.replace(
    'companion object {\n        private const val DEFAULT_BASE_SYSTEM_PROMPT =',
    'companion object {\n        private val DEFAULT_BASE_SYSTEM_PROMPT =')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('Fixed')
