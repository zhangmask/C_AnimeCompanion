package com.companion.chat.locale

/**
 * 应用界面语言枚举。
 *
 * 可扩展：新增语言只需在此添加一个枚举值，并在 [Strings] 字符串表里
 * 为该语言提供一份翻译即可，无需改动调用方代码。
 */
enum class AppLanguage(val code: String, val displayName: String) {
    ZH("zh", "中文"),
    EN("en", "English");

    companion object {
        /** 默认语言（未设置或解析失败时使用）。 */
        val DEFAULT: AppLanguage = ZH

        /** 从持久化的 code 字符串还原为 [AppLanguage]，无法匹配时返回 [DEFAULT]。 */
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}
