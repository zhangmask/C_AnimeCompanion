package com.companion.chat.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * 当前界面语言的 CompositionLocal。
 *
 * 用法：
 *   val language = LocalLanguage.current
 *   val text = Strings.get(language, StringsKey.chat_title)
 *
 * 或更简洁地通过 [Strings.txt] 便捷函数：
 *   val text = Strings.txt(StringsKey.chat_title)
 *   （在已接入 [LocalLanguage] 的 Composable 中直接调用，自动读取当前语言）
 */
val LocalLanguage = compositionLocalOf { AppLanguage.DEFAULT }

/**
 * 在根 Composable 包裹此 provider，向整棵 UI 树注入当前语言。
 *
 * @param language 当前语言（通常来自 [LanguageRepository]）
 * @param content 子 UI 树
 */
@Composable
fun ProvideLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val remembered = remember(language) { language }
    CompositionLocalProvider(LocalLanguage provides remembered, content = content)
}
