path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\locale\Strings.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace(
    '''    fun get(lang: AppLanguage, key: StringsKey, vararg args: String): String {
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
    }''',
    '''    fun get(lang: AppLanguage, key: StringsKey, vararg args: Any?): String {
        val format = get(lang, key)
        return format.format(*args)
    }

    @Composable
    fun txt(key: StringsKey): String {
        return get(LocalLanguage.current, key)
    }

    @Composable
    fun txt(key: StringsKey, vararg args: Any?): String {
        return get(LocalLanguage.current, key, *args)
    }''')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('OK')
