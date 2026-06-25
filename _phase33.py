import os, json, re

# 1. Create assets directory
assets_dir = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\assets'
os.makedirs(assets_dir, exist_ok=True)

# 2. Read Strings.kt and extract ZH/EN maps
strings_path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\locale\Strings.kt'
with open(strings_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Extract ZH map entries (between "val ZH =" and "val EN =")
zh_start = content.find('val ZH =')
zh_end = content.find('val EN =')
zh_section = content[zh_start:zh_end]

zh_entries = {}
for line in zh_section.split('\n'):
    line = line.strip()
    m = re.match(r'StringsKey\.(\w+)\s+to\s+"((?:[^"\\]|\\.)*)"', line)
    if m:
        key = m.group(1)
        val = m.group(2).replace('\\"', '"')
        zh_entries[key] = val

# Extract EN map entries
en_start = content.find('val EN =')
en_section = content[en_start:]
en_end = en_section.find('\n    )') if '    )' in en_section else len(en_section)
en_section = en_section[:en_end]

en_entries = {}
for line in en_section.split('\n'):
    line = line.strip()
    m = re.match(r'StringsKey\.(\w+)\s+to\s+"((?:[^"\\]|\\.)*)"', line)
    if m:
        key = m.group(1)
        val = m.group(2).replace('\\"', '"').replace("\\'", "'")
        en_entries[key] = val

# 3. Write JSON files
with open(os.path.join(assets_dir, 'strings_zh.json'), 'w', encoding='utf-8') as f:
    json.dump(zh_entries, f, ensure_ascii=False, indent=2)
print(f'ZH: {len(zh_entries)} keys')

with open(os.path.join(assets_dir, 'strings_en.json'), 'w', encoding='utf-8') as f:
    json.dump(en_entries, f, ensure_ascii=False, indent=2)
print(f'EN: {len(en_entries)} keys')

# 4. Update Strings.kt — replace inline maps with JSON loading
old_zh_start = '    val ZH: Map<StringsKey, String> = mapOf(\n'
old_zh_end = '\n    val EN: Map<StringsKey, String> = mapOf(\n'

new_loading = '''
    private val cache = mutableMapOf<AppLanguage, MutableMap<StringsKey, String>>()
    private val context: android.content.Context? = null  // set by init

    fun init(ctx: android.content.Context) {
        load(ctx, AppLanguage.ZH)
        load(ctx, AppLanguage.EN)
    }

    private fun load(ctx: android.content.Context, lang: AppLanguage) {
        val fileName = when (lang) {
            AppLanguage.ZH -> "strings_zh.json"
            AppLanguage.EN -> "strings_en.json"
        }
        try {
            val input = ctx.assets.open(fileName)
            val json = input.bufferedReader().use { it.readText() }
            val raw: Map<String, String> = com.google.gson.Gson().fromJson(json, Map::class.java)
            val map = mutableMapOf<StringsKey, String>()
            raw.forEach { (k, v) ->
                try { map[StringsKey.valueOf(k)] = v } catch (_: Exception) {}
            }
            cache[lang] = map
        } catch (e: Exception) {
            android.util.Log.e("Strings", "Failed to load $fileName", e)
        }
    }

    fun get(lang: AppLanguage, key: StringsKey): String {
        return cache[lang]?.get(key) ?: key.name
    }
'''

# Replace the inline ZH and EN maps with a loading mechanism
# Keep enum, remove maps
zh_map_start = content.find('    val ZH: Map<StringsKey, String> = mapOf(')
en_map_end = content.find('    }', content.find('    val EN: Map<StringsKey, String> = mapOf('))
# Find the closing } of EN map (the last } before the next section)
if en_map_end > 0:
    # Remove from zh_map_start to after en_map_end
    # We need to find where the next section starts
    next_section = content.find('\n    fun', en_map_end)
    if next_section < 0:
        next_section = content.find('\n    @', en_map_end)
    if next_section < 0:
        next_section = len(content)
    
    new_content = content[:zh_map_start] + new_loading + content[en_map_end+5:]  # +5 for the }
    
    with open(strings_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print('Strings.kt updated with JSON loading')
else:
    print('Could not find EN map end')

print('\nPhase 3.3 done')
