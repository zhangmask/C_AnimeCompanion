path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\locale\Strings.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()
c = c.replace(
    '    @Composable\n    fun get(lang: AppLanguage, key: StringsKey): String {\n        return translations[lang]?.get(key) ?: ZH[key] ?: key.name\n    }',
    '    fun get(lang: AppLanguage, key: StringsKey): String {\n        return translations[lang]?.get(key) ?: ZH[key] ?: key.name\n    }')
with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('OK')
