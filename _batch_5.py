# ==== HomeScreen.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/home/HomeScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import com.companion.chat.ui.theme.BrandSecondaryContainer',
              'import com.companion.chat.ui.theme.BrandSecondaryContainer\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('title = { Text("发现") }', 'title = { Text(Strings.txt(StringsKey.tab_discover)) }')
c = c.replace('contentDescription = "排序"', 'contentDescription = Strings.txt(StringsKey.home_sort)')
c = c.replace('SortMenuItem("热门"', 'SortMenuItem(Strings.txt(StringsKey.home_sort_hot)')
c = c.replace('SortMenuItem("最新"', 'SortMenuItem(Strings.txt(StringsKey.home_sort_newest)')
c = c.replace('SortMenuItem("名称"', 'SortMenuItem(Strings.txt(StringsKey.home_sort_name)')
c = c.replace('placeholder = { Text("搜索角色、作者、标签") }', 'placeholder = { Text(Strings.txt(StringsKey.home_search_hint)) }')
c = c.replace('"创建你的角色"', 'Strings.txt(StringsKey.home_create_your_role)')
c = c.replace('"人设、头像、语音会保存到角色卡"', 'Strings.txt(StringsKey.home_create_hint)')
c = c.replace('Text("创建")', 'Text(Strings.txt(StringsKey.home_create))')
c = c.replace('text = "显示私密"', 'text = Strings.txt(StringsKey.home_show_mature)')
c = c.replace('contentDescription = "收藏"', 'contentDescription = Strings.txt(StringsKey.home_start_chat_btn)')
c = c.replace('text = "by ${item.role.author}",',
              'text = Strings.txt(StringsKey.home_by_author, item.role.author),')
c = c.replace('"by ${item.role.author} · 热度 ${item.role.heat}"',
              '"${Strings.txt(StringsKey.home_by_author, item.role.author)} · ${item.role.heat}"')
c = c.replace('label = { Text("已导入") }', 'label = { Text(Strings.txt(StringsKey.home_imported)) }')
c = c.replace('label = { Text("已解锁") }', 'label = { Text(Strings.txt(StringsKey.home_unlocked)) }')
c = c.replace('title = { Text(item?.role?.name ?: "角色详情") }',
              'title = { Text(item?.role?.name ?: Strings.txt(StringsKey.home_role_detail)) }')
c = c.replace('contentDescription = "返回"', 'contentDescription = Strings.txt(StringsKey.back)')
c = c.replace('Text("未找到角色")', 'Text(Strings.txt(StringsKey.home_not_found))')
c = c.replace('Text("开始聊天")', 'Text(Strings.txt(StringsKey.home_start_chat_btn))')
c = c.replace('Text(if (item.collection.isUnlocked) "已解锁" else "收藏解锁")',
              'Text(if (item.collection.isUnlocked) Strings.txt(StringsKey.home_unlocked) else Strings.txt(StringsKey.home_unlock_to_favorite))')
c = c.replace('Text(if (isGeneratingImage) "生成中" else "生成图片")',
              'Text(if (isGeneratingImage) Strings.txt(StringsKey.home_generating) else Strings.txt(StringsKey.home_generate_image))')
c = c.replace('Text("编辑角色卡")', 'Text(Strings.txt(StringsKey.home_edit_character))')
c = c.replace('text = "私密"', 'text = Strings.txt(StringsKey.home_mature_label)')
c = c.replace('DetailSection("人设摘要"', 'DetailSection(Strings.txt(StringsKey.detail_persona)')
c = c.replace('DetailSection("语音"', 'DetailSection(Strings.txt(StringsKey.detail_voice)')
c = c.replace('DetailSection("图片风格"', 'DetailSection(Strings.txt(StringsKey.detail_image_style)')
c = c.replace('"未配置"', 'Strings.txt(StringsKey.voice_not_configured)')

# Handle the label inside DetailSection comparison
c = c.replace('if (title == "语音") {', 'if (title == Strings.txt(StringsKey.detail_voice)) {')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("HomeScreen.kt done")

# ==== ChatScreen.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/chat/ChatScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('import kotlinx.coroutines.delay',
              'import kotlinx.coroutines.delay\nimport com.companion.chat.locale.LocalLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')
c = c.replace('contentDescription = "对话列表"', 'contentDescription = Strings.txt(StringsKey.drawer_title)')
c = c.replace('text = "对话"', 'text = Strings.txt(StringsKey.tab_chat)')
c = c.replace('is InferenceState.Idle -> "未连接"',
              'is InferenceState.Idle -> Strings.txt(StringsKey.chat_status_disconnected)')
c = c.replace('is InferenceState.Initializing -> "模型加载中"',
              'is InferenceState.Initializing -> Strings.txt(StringsKey.chat_status_loading)')
c = c.replace('is InferenceState.Ready -> "已就绪"',
              'is InferenceState.Ready -> Strings.txt(StringsKey.chat_status_ready)')
c = c.replace('is InferenceState.Generating -> "生成中"',
              'is InferenceState.Generating -> Strings.txt(StringsKey.chat_status_generating)')
c = c.replace('is InferenceState.Error -> "错误"',
              'is InferenceState.Error -> Strings.txt(StringsKey.chat_status_error)')
c = c.replace('text = "直接输入第一条消息，或从左上角打开会话列表。"',
              'text = Strings.txt(StringsKey.chat_empty_hint)')
c = c.replace('text = uiState.compressionMessage.ifBlank { "正在压缩上下文..." }',
              'text = uiState.compressionMessage.ifBlank { Strings.txt(StringsKey.chat_compressing) }')
c = c.replace('text = "继续聊聊"', 'text = Strings.txt(StringsKey.chat_quick_continue)')
c = c.replace('text = "生成此刻图片"', 'text = Strings.txt(StringsKey.chat_quick_generate_img)')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("ChatScreen.kt done")
