import re

path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\locale\Strings.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Find where the broken JSON loading code starts (after enum ends)
enum_section = []
maps_section = []
rest_section = []

current = 'enum'
for line in lines:
    if current == 'enum':
        enum_section.append(line)
        if line.strip() == '}':  # end of enum
            current = 'between'
    elif current == 'between':
        if 'object Strings' in line or 'val ZH' in line:
            current = 'maps_missing'
            # Skip everything until we find something that looks like a function
        else:
            enum_section.append(line)
    elif current == 'maps_missing':
        if 'fun get(' in line or 'fun txt(' in line or 'fun txt' in line:
            rest_section.append(line)
            current = 'rest'
    elif current == 'rest':
        rest_section.append(line)

# Now we need to add back the ZH and EN maps
# Read the original JSON files to get the translations
import json, os
assets_dir = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\assets'

zh_json_path = os.path.join(assets_dir, 'strings_zh.json')
en_json_path = os.path.join(assets_dir, 'strings_en.json')

zh_map = {}
en_map = {}
if os.path.exists(zh_json_path):
    with open(zh_json_path, 'r', encoding='utf-8') as f:
        zh_map = json.load(f)
if os.path.exists(en_json_path):
    with open(en_json_path, 'r', encoding='utf-8') as f:
        en_map = json.load(f)

if not zh_map or not en_map:
    print(f'JSON files are empty ({len(zh_map)} ZH, {len(en_map)} EN entries)')
    print('Cannot restore Strings.kt without translation data')
else:
    print(f'Found {len(zh_map)} ZH and {len(en_map)} EN entries')
    
print(f'\nEnum section: {len(enum_section)} lines')
print(f'Rest section: {len(rest_section)} lines')
print(f'Current file has {len(lines)} lines')
