import os, re

target = r"C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\settings\RoleCardEditorDialog.kt"
with open(target, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines, 1):
    if 'sectionLabel' in line or 'sectionLabel' in line.encode('latin-1','ignore').decode('latin-1'):
        print(f"L{i}: {line.rstrip()}")
print("---")
# also search for any 'fun' near line 66-70 or any local val returning label
for i, line in enumerate(lines, 1):
    if 'label' in line.lower() and ('fun ' in line or 'val ' in line or '=' in line):
        print(f"L{i}: {line.rstrip()}")
