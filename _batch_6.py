import os

# ==== ChatInputBar.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/chat/components/ChatInputBar.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import com.companion.chat.ui.theme.BrandOutlineLight',
              'import com.companion.chat.ui.theme.BrandOutlineLight\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('inputHint: String = "输入消息..."', 'inputHint: String = Strings.txt(StringsKey.hint_input_msg)')
c = c.replace('contentDescription = "上传图片"', 'contentDescription = Strings.txt(StringsKey.input_pick_image)')
c = c.replace('contentDescription = if (isSuggesting) "正在生成建议" else "获取对话建议"',
              'contentDescription = if (isSuggesting) Strings.txt(StringsKey.hint_suggestion_loading) else Strings.txt(StringsKey.input_generate_image)')
c = c.replace('contentDescription = if (isVoiceSpeaking) "停止播放" else "朗读最近回复"',
              'contentDescription = if (isVoiceSpeaking) Strings.txt(StringsKey.input_stop_reading) else Strings.txt(StringsKey.input_read_aloud)')
c = c.replace('contentDescription = "发送"', 'contentDescription = Strings.txt(StringsKey.input_send)')
c = c.replace('contentDescription = "选中的图片"', 'contentDescription = Strings.txt(StringsKey.drawer_search_hint)')
c = c.replace('contentDescription = "移除图片"', 'contentDescription = Strings.txt(StringsKey.close)')
c = c.replace('active -> "停止语音输入"\n                isVoiceAutoSending -> "正在发送语音"\n                isGenerating -> "正在生成回复"\n                else -> "开始语音输入"',
              'active -> Strings.txt(StringsKey.input_stop)\n                isVoiceAutoSending -> Strings.txt(StringsKey.input_voice)\n                isGenerating -> Strings.txt(StringsKey.chat_status_generating)\n                else -> Strings.txt(StringsKey.input_voice)')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("ChatInputBar.kt done")

# ==== MessageBubble.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/chat/components/MessageBubble.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import java.util.Locale',
              'import java.util.Locale\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('Text("关闭")', 'Text(Strings.txt(StringsKey.close))')
c = c.replace('contentDescription = "图片预览"', 'contentDescription = Strings.txt(StringsKey.msg_image_loading)')
c = c.replace('contentDescription = if (isUser) "用户头像" else "AI头像"',
              'contentDescription = if (isUser) Strings.txt(StringsKey.msg_avatar_me) else Strings.txt(StringsKey.msg_avatar_assistant)')
c = c.replace('contentDescription = if (isUser) "用户" else "AI"',
              'contentDescription = if (isUser) Strings.txt(StringsKey.msg_avatar_me) else Strings.txt(StringsKey.msg_avatar_assistant)')
c = c.replace('contentDescription = "消息图片"', 'contentDescription = Strings.txt(StringsKey.msg_image_loading)')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("MessageBubble.kt done")

# ==== RoleCardEditorDialog.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/settings/RoleCardEditorDialog.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import com.companion.chat.data.voice.VoiceClipScanner',
              'import com.companion.chat.data.voice.VoiceClipScanner\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')

# Enum labels
c = c.replace('BASIC("基础")', 'BASIC(/*"基础"*/ String)')
c = c.replace('PERSONA("人设")', 'PERSONA(/*"人设"*/ String)')
c = c.replace('IMAGE("图片")', 'IMAGE(/*"图片"*/ String)')
c = c.replace('VOICE("语音")', 'VOICE(/*"语音"*/ String)')

# Title
c = c.replace('text = if (roleCard == null) "新建角色卡" else "编辑角色卡"',
              'text = if (roleCard == null) Strings.txt(StringsKey.role_create_card_title) else Strings.txt(StringsKey.role_edit_card_title)')
c = c.replace('Icon(Icons.Default.Close, "关闭")', 'Icon(Icons.Default.Close, Strings.txt(StringsKey.close))')

# Tab labels - use section.label which comes from enum
# The tabs are displayed via section.label in the Tab composable
# I'll replace the enum section labels with localized values

# But actually, the enum has fixed labels. The Tab uses section.label.
# Let me use a different approach - render the tabs with Strings.txt
# Change: text = { Text(section.label) } -> text = { Text(getSectionLabel(section)) }
# Or simpler: make the labels dynamic

# For now, replace the section label enum to use the corresponding keys
c = c.replace(
    'private enum class RoleEditorSection(val label: String) {\n    BASIC("基础"),\n    PERSONA("人设"),\n    IMAGE("图片"),\n    VOICE("语音")\n}',
    'private fun sectionLabel(section: RoleEditorSection): String = when (section) {\n    RoleEditorSection.BASIC -> Strings.txt(StringsKey.role_tab_basic)\n    RoleEditorSection.PERSONA -> Strings.txt(StringsKey.role_tab_persona)\n    RoleEditorSection.IMAGE -> Strings.txt(StringsKey.role_tab_image)\n    RoleEditorSection.VOICE -> Strings.txt(StringsKey.role_tab_voice)\n}\n\nprivate enum class RoleEditorSection { BASIC, PERSONA, IMAGE, VOICE }'
)
c = c.replace('section.label', 'sectionLabel(section)')

# BasicSection labels
c = c.replace('RoleCardField("名称"', 'RoleCardField(Strings.txt(StringsKey.role_field_name)')
c = c.replace('RoleCardField("简介"', 'RoleCardField(Strings.txt(StringsKey.role_field_description)')
c = c.replace('RoleCardField("头像/图标标识"', 'RoleCardField(Strings.txt(StringsKey.role_avatar_icon)')
c = c.replace('RoleCardField("开场白"', 'RoleCardField(Strings.txt(StringsKey.role_field_opening)')

# PersonaSection labels
c = c.replace('RoleCardField("核心人设"', 'RoleCardField(Strings.txt(StringsKey.role_persona_core)')
c = c.replace('RoleCardField("说话风格"', 'RoleCardField(Strings.txt(StringsKey.role_field_speaking_style)')
c = c.replace('RoleCardField("背景设定"', 'RoleCardField(Strings.txt(StringsKey.role_background_story)')
c = c.replace('RoleCardField("行为规则"', 'RoleCardField(Strings.txt(StringsKey.role_field_rules)')
c = c.replace('RoleCardField("禁止项"', 'RoleCardField(Strings.txt(StringsKey.role_field_taboos)')
c = c.replace('RoleCardField("示例对话"', 'RoleCardField(Strings.txt(StringsKey.role_field_example_dialogue)')

# ImageSection
c = c.replace('text = "头像图片"', 'text = Strings.txt(StringsKey.role_avatar_image)')
c = c.replace('contentDescription = "头像预览"', 'contentDescription = Strings.txt(StringsKey.role_avatar_preview)')
c = c.replace('"移除头像"', 'Strings.txt(StringsKey.role_remove_avatar)')
c = c.replace('text = "  选择头像图片"', 'text = "  " + Strings.txt(StringsKey.role_pick_avatar_image)')
c = c.replace('RoleCardField("图库图片 URI（一行一个）"', 'RoleCardField(Strings.txt(StringsKey.role_gallery_uri)')
c = c.replace('RoleCardField("图片风格提示词"', 'RoleCardField(Strings.txt(StringsKey.role_field_image_style)')

# VoiceSection
c = c.replace('text = "语音模式"', 'text = Strings.txt(StringsKey.role_field_voice_mode)')
c = c.replace('"系统 TTS"', 'Strings.txt(StringsKey.role_voice_mode_system)')
c = c.replace('"MOSS 本地克隆"', 'Strings.txt(StringsKey.role_voice_mode_clone)')
c = c.replace('text = "已上传的语音片段"', 'text = Strings.txt(StringsKey.role_uploaded_clips)')
c = c.replace('text = "暂无语音片段，请先上传一段参考音频（WAV 格式最佳）。"',
              'text = Strings.txt(StringsKey.role_no_clips_hint)')
c = c.replace('contentDescription = if (isPlaying) "停止" else "播放"',
              'contentDescription = if (isPlaying) Strings.txt(StringsKey.role_stop) else Strings.txt(StringsKey.role_play)')
c = c.replace('contentDescription = "已选中"', 'contentDescription = Strings.txt(StringsKey.selected)')
c = c.replace('text = "  上传新语音片段"', 'text = "  " + Strings.txt(StringsKey.role_upload_new_clip)')
c = c.replace('text = "选中的语音片段将作为该角色的默认语音。克隆后端不可用时会自动回退系统 TTS。"',
              'text = Strings.txt(StringsKey.role_clone_note)')
c = c.replace('RoleCardField("语音显示名称"', 'RoleCardField(Strings.txt(StringsKey.role_field_voice_display)')
c = c.replace('RoleCardField("语音参考音频 URI"', 'RoleCardField(Strings.txt(StringsKey.role_voice_package_uri)')

# Buttons
c = c.replace('Text("取消")', 'Text(Strings.txt(StringsKey.cancel))')
c = c.replace('Text("保存")', 'Text(Strings.txt(StringsKey.save))')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("RoleCardEditorDialog.kt done")

# ==== SkillEditorDialog.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/settings/SkillEditorDialog.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import com.companion.chat.data.local.entity.Skill',
              'import com.companion.chat.data.local.entity.Skill\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('text = if (skill == null) "新建 Skill" else "编辑 Skill"',
              'text = if (skill == null) Strings.txt(StringsKey.role_create_title) else Strings.txt(StringsKey.role_edit_title)')
c = c.replace('label = { Text("名称") }', 'label = { Text(Strings.txt(StringsKey.role_field_name)) }')
c = c.replace('label = { Text("简介") }', 'label = { Text(Strings.txt(StringsKey.role_field_description)) }')
c = c.replace('Text("保存")', 'Text(Strings.txt(StringsKey.save))')
c = c.replace('Text("取消")', 'Text(Strings.txt(StringsKey.cancel))')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("SkillEditorDialog.kt done")

# ==== DarkModeSettingsScreen.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/settings/DarkModeSettingsScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import com.companion.chat.ui.theme.BrandPrimaryContainer',
              'import com.companion.chat.ui.theme.BrandPrimaryContainer\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('title = { Text("深色模式") }', 'title = { Text(Strings.txt(StringsKey.dark_mode_title)) }')
c = c.replace('contentDescription = "返回"', 'contentDescription = Strings.txt(StringsKey.back)')
c = c.replace('"system" to "跟随系统"', '"system" to Strings.txt(StringsKey.dark_mode_follow_system)')
c = c.replace('"light" to "浅色模式"', '"light" to Strings.txt(StringsKey.dark_mode_off)')
c = c.replace('"dark" to "深色模式"', '"dark" to Strings.txt(StringsKey.dark_mode_on)')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("DarkModeSettingsScreen.kt done")

# ==== AboutScreen.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/settings/AboutScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import androidx.compose.ui.unit.dp',
              'import androidx.compose.ui.unit.dp\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('text = "关于"', 'text = Strings.txt(StringsKey.about_title)')
c = c.replace('contentDescription = "返回"', 'contentDescription = Strings.txt(StringsKey.back)')
c = c.replace('text = "版本 0.1.0"', 'text = Strings.txt(StringsKey.about_version) + " 0.1.0"')
c = c.replace('text = "你的私人 AI 伙伴\\n基于 LiteRT-LM 本地推理引擎"',
              'text = Strings.txt(StringsKey.about_intro)')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("AboutScreen.kt done")

# ==== DiscoverViewModel.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/home/DiscoverViewModel.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import kotlinx.coroutines.launch',
              'import kotlinx.coroutines.launch\nimport com.companion.chat.locale.AppLanguage\nimport com.companion.chat.locale.LanguageRepository\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')

# Add language helper
c = c.replace('private val _uiState',
              'private val languageRepo = LanguageRepository(getApplication())\n    private fun tr(key: StringsKey, vararg args: Any): String = Strings.get(languageRepo.getLanguage(), key, *args)\n\n    private val _uiState')

c = c.replace('error.message ?: "导入角色失败"',
              'error.message ?: tr(StringsKey.discover_import_failed)')
c = c.replace('message = if (attached) "图片已加入角色图库" else "图片已生成: $uri"',
              'message = if (attached) tr(StringsKey.discover_image_added) else tr(StringsKey.discover_image_generated, uri)')
c = c.replace('error.message ?: "图片生成失败"',
              'error.message ?: tr(StringsKey.snackbar_image_failed)')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("DiscoverViewModel.kt done")

# ==== AppNavigation.kt (Screen enum) ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/navigation/AppNavigation.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import androidx.compose.ui.graphics.vector.ImageVector',
              'import androidx.compose.ui.graphics.vector.ImageVector\nimport com.companion.chat.locale.StringsKey')

c = c.replace('label = "发现"', 'labelKey = StringsKey.tab_discover')
c = c.replace('label = "对话"', 'labelKey = StringsKey.tab_chat')
c = c.replace('label = "记忆"', 'labelKey = StringsKey.tab_memory')
c = c.replace('label = "设置"', 'labelKey = StringsKey.tab_settings')
c = c.replace('val label: String,', 'val labelKey: StringsKey,')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("AppNavigation.kt done")
