import os

# === STAGE 1: Add missing keys to Strings.kt ===
fp = 'CompanionChat/app/src/main/java/com/companion/chat/locale/Strings.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

# VoiceSettings + HomeScreen + remaining keys
new_keys = '''
    // ── VoiceSettingsScreen 额外 ──
    voice_recognition_mode,     // 识别模式 / Recognition mode
    voice_recognition_backend,  // 识别后端 / Recognition backend
    voice_model_directory,      // 模型目录 / Model directory
    voice_model_status,         // 模型状态 / Model status
    voice_cloud_asr_label,      // 云 ASR / Cloud ASR
    voice_cloud_response_field, // 云响应字段 / Cloud response field
    voice_moss_directory,       // MOSS 目录 / MOSS directory
    voice_moss_status,          // MOSS 状态 / MOSS status
    voice_local_clone,          // 本地克隆 / Local clone
    voice_output_mode,          // 输出模式 / Output mode
    voice_default_timbre,       // 默认音色 / Default voice
    voice_role_voice,           // 角色语音 / Character voice
    voice_not_configured,       // 未配置 / Not configured
    voice_configured,           // 已配置 / Configured
    voice_local_sensevoice,     // 本地 SenseVoice ASR / Local SenseVoice ASR
    voice_cloud_http_asr,       // 云 HTTP ASR / Cloud HTTP ASR
    voice_moss_nano_default,    // MOSS TTS Nano（默认引擎） / MOSS TTS Nano (default engine)
    voice_fallback_tts,         // 回退系统 TTS / Fallback to system TTS
    voice_clone_default,        // MOSS 本地克隆（默认） / MOSS local clone (default)
    voice_role_voice_hint,      // 在角色管理中配置... / Configure in character management...
    voice_desc,                 // 语音输入默认使用... / Voice input uses local SenseVoice...
    voice_auto_read_desc,       // AI 开始回复 0.5 秒后... / Auto-read 0.5s after AI starts replying
    voice_ready,                // 完整 / Complete
    voice_local_not_configured, // 本地 SenseVoice 模型未配置 / Local SenseVoice model not configured
    voice_moss_not_configured,  // moss-tts-nano 模型未配置 / moss-tts-nano model not configured
    voice_invalid_config,       // 配置无效：%s / Invalid config: %s
    voice_missing_files,        // 文件缺失：%s / Missing files: %s

    // ── HomeScreen 额外 ──
    home_discover,              // 发现 / Discover
    home_sort,                  // 排序 / Sort
    home_sort_hot,              // 热门 / Hot
    home_sort_newest,           // 最新 / Newest
    home_sort_name,             // 名称 / Name
    home_search_hint,           // 搜索角色、作者、标签 / Search characters, authors, tags
    home_create_your_role,      // 创建你的角色 / Create your character
    home_create_hint,           // 人设、头像、语音会保存到角色卡 / Persona, avatar, voice saved to card
    home_create,                // 创建 / Create
    home_show_mature,           // 显示私密 / Show mature
    home_role_detail,           // 角色详情 / Character details
    home_not_found,             // 未找到角色 / Character not found
    home_by_author,             // by %s / by %s
    home_imported,              // 已导入 / Imported
    home_unlocked,              // 已解锁 / Unlocked
    home_start_chat_btn,        // 开始聊天 / Start chat
    home_unlock_to_favorite,    // 收藏解锁 / Unlock to favorite
    home_generating,            // 生成中 / Generating
    home_generate_image,        // 生成图片 / Generate image
    home_edit_character,        // 编辑角色卡 / Edit character card
    home_mature_label,          // 私密 / Mature

    // ── ChatScreen 额外 ──
    chat_quick_continue,        // 继续聊聊 / Continue chatting
    chat_quick_generate_img,    // 生成此刻图片 / Generate scene image

    // ── CharacterManagement 额外 ──
    char_mgmt_persona_label,    // 人设：%s / Persona: %s
    char_mgmt_style_label,      // 风格：%s / Style: %s
    char_mgmt_image_label,      // 图片：头像%s，图库 %d 张 / Image: avatar %s, gallery %d images
    char_mgmt_voice_label,      // 语音：%s / Voice: %s
    char_mgmt_avatar_configured,// 已配置 / Configured
    char_mgmt_avatar_missing,   // 未配置 / Not configured

    // ── SkillsManagement 额外 ──
    skills_used_count,          // 已使用 %d 次 / Used %d times
    skills_delete_confirm,      // 确认删除"%s"吗？ / Delete "%s"?

    // ── DetailSection ──
    detail_persona,             // 人设摘要 / Persona summary
    detail_voice,               // 语音 / Voice
    detail_image_style,         // 图片风格 / Image style
'''
c = c.replace(
    '\n    // ── DiscoverViewModel ──\n    discover_import_failed,',
    new_keys + '\n    // ── DiscoverViewModel ──\n    discover_import_failed,'
)
with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("Stage 1: Keys added")
