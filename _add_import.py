path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\chat\ChatViewModel.kt'
with open(path, 'rb') as f:
    data = f.read()
import_line = b'import com.companion.chat.companion.PreferenceLearningCoordinator\n'
new_import = b'import com.companion.chat.companion.PreferenceLearningCoordinator\nimport com.companion.chat.data.memory.MemoryExtractLoop\n'
if import_line in data:
    data = data.replace(import_line, new_import, 1)
    with open(path, 'wb') as f:
        f.write(data)
    print('OK')
else:
    print('NOT FOUND')
