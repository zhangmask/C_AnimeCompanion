p = r'C:/Users/72952/OneDrive/Desktop/ui/CompanionChat/app/src/main/java/com/companion/chat/ui/chat/components/RoleCardEditorSheet.kt'
t = open(p, encoding='utf-8').read()
# Add imports
imp = '''import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey'''
if 'import com.companion.chat.locale.LocalLanguage' not in t:
    lines = t.split('\n')
    last = max(i for i, l in enumerate(lines) if l.startswith('import '))
    lines.insert(last + 1, imp)
    t = '\n'.join(lines)
repls = [
    ('val tabs = listOf("基础", "人设", "图片", "语音")', 'val tabs = listOf(Strings.txt(StringsKey.role_tab_basic), Strings.txt(StringsKey.role_tab_persona), Strings.txt(StringsKey.role_tab_image), Strings.txt(StringsKey.role_tab_voice))'),
    ('text = if (isEditing) "编辑角色卡" else "创建角色卡"', 'text = if (isEditing) Strings.txt(StringsKey.role_edit_card_title) else Strings.txt(StringsKey.role_create_card_title)'),
    ('                                "关闭",', '                                Strings.txt(StringsKey.close),'),
    ('label = "名称"', 'label = Strings.txt(StringsKey.role_field_name)'),
    ('placeholder = "给角色取个名字"', 'placeholder = Strings.txt(StringsKey.role_name_placeholder)'),
    ('label = "简介"', 'label = Strings.txt(StringsKey.role_field_description)'),
    ('placeholder = "温柔治愈的邻家女孩"', 'placeholder = Strings.txt(StringsKey.role_desc_placeholder)'),
    ('label = "头像图标"', 'label = Strings.txt(StringsKey.role_avatar_icon)'),
    ('placeholder = "图标标识符，如 person、star、heart"', 'placeholder = Strings.txt(StringsKey.role_avatar_icon_hint)'),
    ('text = "头像预览"', 'text = Strings.txt(StringsKey.role_avatar_preview)'),
    ('contentDescription = "头像"', 'contentDescription = Strings.txt(StringsKey.role_field_avatar)'),
    ('text = if (name.isNotBlank()) name else "未命名"', 'text = if (name.isNotBlank()) name else Strings.txt(StringsKey.role_unnamed)'),
    ('label = "开场白"', 'label = Strings.txt(StringsKey.role_field_opening)'),
    ('placeholder = "你好呀~今天想聊什么呢？"', 'placeholder = Strings.txt(StringsKey.role_opening_placeholder)'),
    ('label = "核心人设 *"', 'label = Strings.txt(StringsKey.role_persona_core)'),
    ('placeholder = "描述角色的核心性格和行为特点"', 'placeholder = Strings.txt(StringsKey.role_persona_core_hint)'),
    ('label = "说话风格"', 'label = Strings.txt(StringsKey.role_field_speaking_style)'),
    ('placeholder = "温暖亲切，偶尔撒娇，喜欢用语气词"', 'placeholder = Strings.txt(StringsKey.role_speaking_style_hint)'),
    ('label = "背景故事"', 'label = Strings.txt(StringsKey.role_background_story)'),
    ('placeholder = "角色的来历和背景设定"', 'placeholder = Strings.txt(StringsKey.role_background_hint)'),
    ('label = "规则"', 'label = Strings.txt(StringsKey.role_field_rules)'),
    ('placeholder = "角色必须遵守的行为规则和约束"', 'placeholder = Strings.txt(StringsKey.role_rules_hint)'),
    ('label = "禁忌"', 'label = Strings.txt(StringsKey.role_field_taboos)'),
    ('placeholder = "角色绝对不能触碰的话题或行为"', 'placeholder = Strings.txt(StringsKey.role_taboos_hint)'),
    ('label = "示例对话"', 'label = Strings.txt(StringsKey.role_field_example_dialogue)'),
    ('placeholder = "用户: 你好\\n角色: 你好呀~很高兴见到你！"', 'placeholder = Strings.txt(StringsKey.role_example_dialogue_hint)'),
    ('text = "头像图片"', 'text = Strings.txt(StringsKey.role_avatar_image)'),
    ('contentDescription = "头像预览"', 'contentDescription = Strings.txt(StringsKey.role_avatar_preview)'),
    ('"移除头像"', 'Strings.txt(StringsKey.role_remove_avatar)'),
    ('text = "选择头像图片"', 'text = Strings.txt(StringsKey.role_pick_avatar_image)'),
    ('label = "相册图片 URI（逗号分隔）"', 'label = Strings.txt(StringsKey.role_gallery_uri)'),
    ('label = "图片风格提示词"', 'label = Strings.txt(StringsKey.role_field_image_style)'),
    ('text = "语音模式"', 'text = Strings.txt(StringsKey.role_field_voice_mode)'),
    ('label = { Text("系统 TTS") }', 'label = { Text(Strings.txt(StringsKey.role_voice_mode_system)) }'),
    ('label = { Text("MOSS 本地克隆") }', 'label = { Text(Strings.txt(StringsKey.role_voice_mode_clone)) }'),
    ('text = "已上传的语音片段"', 'text = Strings.txt(StringsKey.role_uploaded_clips)'),
    ('text = "暂无语音片段，请先上传一段参考音频（WAV 格式最佳）。"', 'text = Strings.txt(StringsKey.role_no_clips_hint)'),
    ('contentDescription = if (isPlaying) "停止" else "播放"', 'contentDescription = if (isPlaying) Strings.txt(StringsKey.role_stop) else Strings.txt(StringsKey.role_play)'),
    ('contentDescription = "已选中"', 'contentDescription = Strings.txt(StringsKey.selected)'),
    ('text = "上传新语音片段"', 'text = Strings.txt(StringsKey.role_upload_new_clip)'),
    ('text = "选中的语音片段将作为该角色的默认语音。克隆后端不可用时会自动回退系统 TTS。"', 'text = Strings.txt(StringsKey.role_clone_note)'),
    ('label = "语音显示名称"', 'label = Strings.txt(StringsKey.role_field_voice_display)'),
    ('placeholder = "温柔女声 / 磁性男声"', 'placeholder = Strings.txt(StringsKey.role_voice_display_hint)'),
    ('label = "语音包 URI"', 'label = Strings.txt(StringsKey.role_voice_package_uri)'),
    ('placeholder = "自动从选中片段填入，也可手动输入"', 'placeholder = Strings.txt(StringsKey.role_voice_package_hint)'),
    ('text = "未配置时将使用默认 MOSS 音色"', 'text = Strings.txt(StringsKey.role_default_moss_note)'),
    ('Text("取消", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF49454F))', 'Text(Strings.txt(StringsKey.cancel), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF49454F))'),
    ('"保存"', 'Strings.txt(StringsKey.save)'),
]
cnt = 0
nf = []
for old, new in repls:
    if old in t:
        t = t.replace(old, new, 1); cnt += 1
    else:
        nf.append(old[:50])
open(p, 'w', encoding='utf-8').write(t)
print('Replaced:', cnt, '/', len(repls))
if nf:
    for x in nf: print(' NF:', x)
