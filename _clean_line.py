path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\memory\MemoryScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Replace lines 894 and 895 (0-indexed: 893, 894) with clean text
# Line 893 (0-idx) currently: "                            }"  - closes LazyRow
# Line 894 (0-idx) currently: "                        2 -> {"
# Need to ensure there's no hidden chars

lines[893] = '                            }\n'
lines[894] = '                        2 -> {\n'

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines)
print('Lines cleaned')
