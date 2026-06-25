package com.companion.chat.locale

import android.content.Context
import android.content.SharedPreferences

/**
 * 持久化当前界面语言选择。基于 SharedPreferences，与项目其他 repository 模式一致。
 *
 * 可扩展：新增语言无需改动本类，[AppLanguage.fromCode] 自动处理。
 */
class LanguageRepository(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取当前语言，未设置时返回 [AppLanguage.DEFAULT]。 */
    fun getLanguage(): AppLanguage =
        AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null))

    /** 持久化语言选择。 */
    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    companion object {
        private const val PREFS_NAME = "locale_prefs"
        private const val KEY_LANGUAGE = "app_language"
    }
}
