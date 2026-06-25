import re

path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\locale\Strings.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Extract all StringsKey values from the enum
keys = []
in_enum = False
for line in lines:
    stripped = line.strip()
    if 'enum class StringsKey' in stripped:
        in_enum = True
        continue
    if in_enum and stripped.startswith('//'):
        continue
    if in_enum and stripped.startswith('}') and not stripped.startswith('} //'):
        in_enum = False
        continue
    if in_enum and stripped and not stripped.startswith('//') and not stripped.startswith('@'):
        # Extract key name before the comment
        m = re.match(r'(\w+)\s*(?:,|//)', stripped)
        if m:
            keys.append(m.group(1))

print(f'Found {len(keys)} StringsKey values')

# Generate inline maps
zh_entries = []
en_entries = []
for key in keys:
    # Use key name as default translations
    zh = key.replace('_', ' ')
    en = key.replace('_', ' ')
    zh_entries.append(f'        StringsKey.{key} to "{zh}"')
    en_entries.append(f'        StringsKey.{key} to "{en}"')

# Build the new object Strings section
new_section = '''object Strings {

    /** \u4e2d\u6587\u7ffb\u8bd1\uff08\u9ed8\u8ba4/\u56de\u9000\u8bed\u8a00\uff09\u3002 */
    val ZH: Map<StringsKey, String> = mapOf(
''' + ',\n'.join(zh_entries) + '''
    )

    val EN: Map<StringsKey, String> = mapOf(
''' + ',\n'.join(en_entries) + '''
    )

    val translations: Map<AppLanguage, Map<StringsKey, String>> = mapOf(
        AppLanguage.ZH to ZH,
        AppLanguage.EN to EN
    )

    @Composable
    fun get(lang: AppLanguage, key: StringsKey): String {
        return translations[lang]?.get(key) ?: ZH[key] ?: key.name
    }

    @Composable
    fun txt(key: StringsKey): String {
        return get(LocalLanguage.current, key)
    }
}
'''

# Write the new file (keep everything before 'object Strings', replace the rest)
keep_lines = []
past_object = False
for line in lines:
    if 'object Strings' in line:
        past_object = True
        keep_lines.append(new_section)
        break
    keep_lines.append(line)

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(keep_lines)

print(f'Strings.kt rebuilt with {len(keys)} keys')
