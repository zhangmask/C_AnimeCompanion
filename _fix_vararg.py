path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\locale\Strings.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Add vararg overloads
old_funcs = '''    fun get(lang: AppLanguage, key: StringsKey): String {
        return translations[lang]?.get(key) ?: ZH[key] ?: key.name
    }

    @Composable
    fun txt(key: StringsKey): String {
        return get(LocalLanguage.current, key)
    }'''

new_funcs = '''    fun get(lang: AppLanguage, key: StringsKey): String {
        return translations[lang]?.get(key) ?: ZH[key] ?: key.name
    }

    fun get(lang: AppLanguage, key: StringsKey, vararg args: String): String {
        val format = get(lang, key)
        return format.format(*args)
    }

    @Composable
    fun txt(key: StringsKey): String {
        return get(LocalLanguage.current, key)
    }

    @Composable
    fun txt(key: StringsKey, vararg args: String): String {
        return get(LocalLanguage.current, key, *args)
    }'''

c = c.replace(old_funcs, new_funcs, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('OK')
