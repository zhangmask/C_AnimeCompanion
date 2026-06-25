import io
p = r"C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\settings\RoleCardEditorDialog.kt"
with open(p, 'r', encoding='utf-8') as f:
    src = f.read()
# print lines 60-75 with repr to see actual bytes
lines = src.split('\n')
for i in range(59, 75):
    print(f"L{i+1}: {repr(lines[i])}")
print("---")
# find sectionLabel usage context
idx = src.find('sectionLabel')
while idx >= 0:
    print("CTX:", repr(src[max(0,idx-80):idx+80]))
    idx = src.find('sectionLabel', idx+1)
    break
