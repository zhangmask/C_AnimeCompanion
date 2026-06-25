import os

# ==== VoiceSettingsScreen.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/settings/VoiceSettingsScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import com.companion.chat.data.voice.VoiceInputBackend',
              'import com.companion.chat.data.voice.VoiceInputBackend\nimport com.companion.chat.locale.AppLanguage\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('text = "语音设置",\n                        style = MaterialTheme.typography.titleLarge',
              'text = Strings.txt(StringsKey.voice_title),\n                        style = MaterialTheme.typography.titleLarge')
c = c.replace('contentDescription = "返回"', 'contentDescription = Strings.txt(StringsKey.back)')
c = c.replace('text = "语音设置",\n                style = MaterialTheme.typography.headlineSmall',
              'text = Strings.txt(StringsKey.voice_title),\n                style = MaterialTheme.typography.headlineSmall')
c = c.replace('text = "语音输入默认使用本地 SenseVoice。默认使用 moss-tts-nano ONNX 模型进行语音克隆，缺模型或缺参考音频时自动回退系统 TTS。"',
              'text = Strings.txt(StringsKey.voice_desc)')
c = c.replace('VoiceInfoRow("识别模式"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_recognition_mode)')
c = c.replace('VoiceInfoRow("识别后端"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_recognition_backend)')
c = c.replace('VoiceInfoRow("模型目录"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_model_directory)')
c = c.replace('VoiceInfoRow("模型状态"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_model_status)')
c = c.replace('VoiceInfoRow("云 ASR"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_cloud_asr_label)')
c = c.replace('VoiceInfoRow("云响应字段"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_cloud_response_field)')
c = c.replace('VoiceInfoRow("MOSS 目录"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_moss_directory)')
c = c.replace('VoiceInfoRow("MOSS 状态"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_moss_status)')
c = c.replace('VoiceInfoRow("本地克隆"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_local_clone)')
c = c.replace('VoiceInfoRow("输出模式"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_output_mode)')
c = c.replace('VoiceInfoRow("默认音色"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_default_timbre)')
c = c.replace('VoiceInfoRow("角色语音"', 'VoiceInfoRow(Strings.txt(StringsKey.voice_role_voice)')
c = c.replace('"未配置"', 'Strings.txt(StringsKey.voice_not_configured)')
c = c.replace('"已配置"', 'Strings.txt(StringsKey.voice_configured)')
c = c.replace('"MOSS TTS Nano（默认引擎）"', 'Strings.txt(StringsKey.voice_moss_nano_default)')
c = c.replace('"回退系统 TTS"', 'Strings.txt(StringsKey.voice_fallback_tts)')
c = c.replace('"MOSS 本地克隆（默认）"', 'Strings.txt(StringsKey.voice_clone_default)')
c = c.replace('"在角色管理中配置参考音频 URI、模式和显示名称"', 'Strings.txt(StringsKey.voice_role_voice_hint)')
c = c.replace('text = "语音输出"', 'text = Strings.txt(StringsKey.voice_output)')
c = c.replace('text = "AI 回复自动朗读"', 'text = Strings.txt(StringsKey.voice_auto_read)')
c = c.replace('text = "AI 开始回复 0.5 秒后，按句子自动朗读"', 'text = Strings.txt(StringsKey.voice_auto_read_desc)')

# Update displayName functions
c = c.replace(
    'private fun VoiceInputBackend.displayName(): String {',
    'private fun VoiceInputBackend.displayName(lang: AppLanguage): String {'
)
c = c.replace('VoiceInputBackend.LOCAL_SENSEVOICE -> "本地 SenseVoice ASR"',
              'VoiceInputBackend.LOCAL_SENSEVOICE -> Strings.get(lang, StringsKey.voice_local_sensevoice)')
c = c.replace('VoiceInputBackend.CLOUD_HTTP_ASR -> "云 HTTP ASR"',
              'VoiceInputBackend.CLOUD_HTTP_ASR -> Strings.get(lang, StringsKey.voice_cloud_http_asr)')

c = c.replace(
    'private fun LocalSenseVoiceModelStatus.displayName(): String {',
    'private fun LocalSenseVoiceModelStatus.displayName(lang: AppLanguage): String {'
)
c = c.replace('LocalSenseVoiceModelStatus.Ready -> "完整"',
              'LocalSenseVoiceModelStatus.Ready -> Strings.get(lang, StringsKey.voice_ready)')
c = c.replace('LocalSenseVoiceModelStatus.DirectoryNotConfigured -> "本地 SenseVoice 模型未配置"',
              'LocalSenseVoiceModelStatus.DirectoryNotConfigured -> Strings.get(lang, StringsKey.voice_local_not_configured)')
c = c.replace('is LocalSenseVoiceModelStatus.MissingFiles -> "文件缺失：${fileNames.joinToString()}"',
              'is LocalSenseVoiceModelStatus.MissingFiles -> Strings.get(lang, StringsKey.voice_missing_files, fileNames.joinToString())')

c = c.replace(
    'private fun MossTtsNanoModelStatus.displayName(): String {',
    'private fun MossTtsNanoModelStatus.displayName(lang: AppLanguage): String {'
)
c = c.replace('MossTtsNanoModelStatus.Ready -> "完整"',
              'MossTtsNanoModelStatus.Ready -> Strings.get(lang, StringsKey.voice_ready)')
c = c.replace('MossTtsNanoModelStatus.DirectoryNotConfigured -> "moss-tts-nano 模型未配置"',
              'MossTtsNanoModelStatus.DirectoryNotConfigured -> Strings.get(lang, StringsKey.voice_moss_not_configured)')
c = c.replace('is MossTtsNanoModelStatus.InvalidConfig -> "配置无效：$message"',
              'is MossTtsNanoModelStatus.InvalidConfig -> Strings.get(lang, StringsKey.voice_invalid_config, message)')
c = c.replace('is MossTtsNanoModelStatus.MissingFiles -> "文件缺失：${fileNames.joinToString()}"',
              'is MossTtsNanoModelStatus.MissingFiles -> Strings.get(lang, StringsKey.voice_missing_files, fileNames.joinToString())')

# Update call sites for displayName
c = c.replace('voiceInputConfig.backend.displayName()',
              'voiceInputConfig.backend.displayName(LocalLanguage.current)')
c = c.replace('localModelStatus.displayName()',
              'localModelStatus.displayName(LocalLanguage.current)')
c = c.replace('mossModelStatus.displayName()',
              'mossModelStatus.displayName(LocalLanguage.current)')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("VoiceSettingsScreen.kt done")

# ==== CharacterManagementScreen.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/settings/CharacterManagementScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import com.companion.chat.ui.chat.components.RoleCardEditorSheet',
              'import com.companion.chat.ui.chat.components.RoleCardEditorSheet\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('text = "角色管理"', 'text = Strings.txt(StringsKey.char_mgmt_title)')
c = c.replace('contentDescription = "返回"', 'contentDescription = Strings.txt(StringsKey.back)')
c = c.replace('contentDescription = "添加角色卡"', 'contentDescription = Strings.txt(StringsKey.char_mgmt_new)')
c = c.replace('text = "创建和切换陪伴角色卡"', 'text = Strings.txt(StringsKey.settings_sub_characters)')
c = c.replace('SectionTitle("当前激活")', 'SectionTitle(Strings.txt(StringsKey.char_mgmt_set_active))')
c = c.replace('SectionTitle("我的角色卡")', 'SectionTitle(Strings.txt(StringsKey.char_mgmt_title))')
c = c.replace('title = "还没有角色卡",\n                        description = "点击右上角“+”创建你的第一张角色卡。"',
              'title = Strings.txt(StringsKey.char_mgmt_empty),\n                        description = Strings.txt(StringsKey.drawer_no_character_hint)')
c = c.replace('contentDescription = "编辑"', 'contentDescription = Strings.txt(StringsKey.edit)')
c = c.replace('label = { Text("使用中") }', 'label = { Text(Strings.txt(StringsKey.drawer_active_tag)) }')
c = c.replace('text = "人设：${roleCard.persona}"',
              'text = Strings.txt(StringsKey.char_mgmt_persona_label, roleCard.persona)')
c = c.replace('text = "风格：${roleCard.speakingStyle}"',
              'text = Strings.txt(StringsKey.char_mgmt_style_label, roleCard.speakingStyle)')
c = c.replace('text = "图片：头像${if (roleCard.avatarImageUri.isNotBlank()) "已配置" else "未配置"}，图库 ${roleCard.galleryImageUris.size} 张"',
              'text = Strings.txt(StringsKey.char_mgmt_image_label, if (roleCard.avatarImageUri.isNotBlank()) Strings.txt(StringsKey.char_mgmt_avatar_configured) else Strings.txt(StringsKey.char_mgmt_avatar_missing), roleCard.galleryImageUris.size)')
c = c.replace('text = "语音：${roleCard.voiceDisplayName.ifBlank { roleCard.voiceMode }}"',
              'text = Strings.txt(StringsKey.char_mgmt_voice_label, roleCard.voiceDisplayName.ifBlank { roleCard.voiceMode })')
c = c.replace('Text("对话")', 'Text(Strings.txt(StringsKey.tab_chat))')
c = c.replace('Text("启用")', 'Text(Strings.txt(StringsKey.enable))')
c = c.replace('Text("编辑")', 'Text(Strings.txt(StringsKey.edit))')
c = c.replace('Text("删除")', 'Text(Strings.txt(StringsKey.delete))')
c = c.replace('title = { Text("删除角色卡") }', 'title = { Text(Strings.txt(StringsKey.role_delete_title)) }')
c = c.replace('text = { Text("确认删除"${roleCard.name}"吗？") }',
              'text = { Text(Strings.txt(StringsKey.char_mgmt_delete_confirm, roleCard.name)) }')
c = c.replace('Text("取消")', 'Text(Strings.txt(StringsKey.cancel))')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("CharacterManagementScreen.kt done")

# ==== SkillsManagementScreen.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/settings/SkillsManagementScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import com.companion.chat.data.local.entity.Skill',
              'import com.companion.chat.data.local.entity.Skill\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('title = { Text("Skills 管理"', 'title = { Text(Strings.txt(StringsKey.skills_title)')
c = c.replace('contentDescription = "返回"', 'contentDescription = Strings.txt(StringsKey.back)')
c = c.replace('contentDescription = "添加 Skill"', 'contentDescription = Strings.txt(StringsKey.char_mgmt_new)')
c = c.replace('text = "管理工作能力模板和自定义 skills"', 'text = Strings.txt(StringsKey.settings_sub_skills)')
c = c.replace('SkillsSectionTitle("当前激活")', 'SkillsSectionTitle(Strings.txt(StringsKey.char_mgmt_set_active))')
c = c.replace('SkillsSectionTitle("内置 Skill")', 'SkillsSectionTitle(Strings.txt(StringsKey.skills_title))')
c = c.replace('SkillsEmptyState("当前没有内置 Skill", "后续可在数据库初始化中补充。")',
              'SkillsEmptyState(Strings.txt(StringsKey.skills_empty), Strings.txt(StringsKey.skills_empty))')
c = c.replace('SkillsSectionTitle("我的 Skills")', 'SkillsSectionTitle(Strings.txt(StringsKey.settings_item_skills))')
c = c.replace('SkillsEmptyState("还没有自定义 Skills", "点击右上角"+"创建你的自定义 skill。")',
              'SkillsEmptyState(Strings.txt(StringsKey.skills_empty), Strings.txt(StringsKey.drawer_no_character_hint))')
c = c.replace('label = { Text("使用中") }', 'label = { Text(Strings.txt(StringsKey.drawer_active_tag)) }')
c = c.replace('text = "已使用 ${skill.usageCount} 次"',
              'text = Strings.txt(StringsKey.skills_used_count, skill.usageCount)')
c = c.replace('Text("启用")', 'Text(Strings.txt(StringsKey.enable))')
c = c.replace('Text("编辑")', 'Text(Strings.txt(StringsKey.edit))')
c = c.replace('Text("删除")', 'Text(Strings.txt(StringsKey.delete))')
c = c.replace('title = { Text("删除 Skill") }', 'title = { Text(Strings.txt(StringsKey.role_delete_title)) }')
c = c.replace('text = { Text("确认删除"${skill.name}"吗？") }',
              'text = { Text(Strings.txt(StringsKey.skills_delete_confirm, skill.name)) }')
c = c.replace('Text("取消")', 'Text(Strings.txt(StringsKey.cancel))')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("SkillsManagementScreen.kt done")
