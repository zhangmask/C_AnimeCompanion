package com.companion.chat.locale

import androidx.compose.runtime.Composable

/**
 * UI 字符串键。每个键对应一条用户可见文案。
 *
 * 新增文案只需在此枚举加一个值，并在 [Strings.ZH] / [Strings.EN] 两个 map 里
 * 各加一条对应翻译即可。未来新增语言只需新增一个 map，无需改动调用方。
 */
enum class StringsKey {
    // ── 通用 ──
    back,                  // 返回 / Back
    close,                 // 关闭 / Close
    confirm,               // 确认 / Confirm
    cancel,                // 取消 / Cancel
    save,                  // 保存 / Save
    delete,                // 删除 / Delete
    edit,                  // 编辑 / Edit
    refresh,               // 刷新 / Refresh
    search,                // 搜索 / Search
    filter,                // 筛选 / Filter
    settings,              // 设置 / Settings
    enable,                // 启用 / Enable
    disable,               // 禁用 / Disable
    configure,             // 配置 / Configure
    selected,              // 已选中 / Selected
    loading,               // 加载中 / Loading

    // ── 底部导航 ──
    tab_discover,          // 发现 / Discover
    tab_chat,              // 对话 / Chat
    tab_memory,            // 记忆 / Memory
    tab_settings,          // 设置 / Settings

    // ── ChatScreen ──
    chat_title,            // 对话 / Chat
    chat_status_disconnected,   // 未连接 / Disconnected
    chat_status_loading,        // 模型加载中 / Loading model
    chat_status_ready,          // 已就绪 / Ready
    chat_status_generating,     // 生成中 / Generating
    chat_status_error,          // 错误 / Error
    chat_empty_hint,            // 直接输入第一条消息，或从左上角打开会话列表。 / Type your first message, or open sessions from top-left.
    chat_compressing,           // 正在压缩上下文… / Compressing context…

    // ── ChatInputBar ──
    input_placeholder,          // 输入消息… / Type a message…
    input_voice,                // 语音输入 / Voice input
    input_send,                 // 发送 / Send
    input_stop,                 // �停止 / Stop
    input_read_aloud,           // 朗读 / Read aloud
    input_stop_reading,         // 停止朗读 / Stop reading
    input_pick_image,           // 选择图片 / Pick image
    input_generate_image,       // 生成图片 / Generate image

    // ── ConversationDrawerSheet ──
    drawer_title,               // 对话列表 / Sessions
    drawer_search_hint,         // 搜索对话或角色 / Search sessions or characters
    drawer_filter_today,        // 今天 / Today
    drawer_filter_yesterday,    // 昨天 / Yesterday
    drawer_filter_week,         // 本周 / This week
    drawer_filter_month,        // 本月 / This month
    drawer_filter_all,          // 全部 / All
    drawer_filter_title,        // 筛选对话 / Filter sessions
    drawer_filter_time_section, // 时间 / Time
    drawer_empty_no_match,      // 未找到匹配的对话 / No matching sessions
    drawer_empty_period,        // 该时间段内暂无对话 / No sessions in this period
    drawer_empty_none,          // 暂无对话 / No sessions yet
    drawer_new_chat,            // 新建对话 / New chat
    drawer_choose_character,    // 选择角色 / Choose character
    drawer_create_new_character,// 创建新角色 / Create new character
    drawer_new_character_hint,  // 自定义人设、头像和语音 / Custom persona, avatar and voice
    drawer_existing_characters, // 已有角色 / Existing characters
    drawer_no_character_hint,   // 还没有角色卡，先创建一个吧 / No character cards yet, create one first
    drawer_active_tag,          // 使用中 / Active
    drawer_blank_chat,          // 空白对话（不使用角色） / Blank chat (no character)
    drawer_edit_title,          // 编辑标题 / Edit title
    drawer_delete_session,      // 删除会话 / Delete session
    drawer_msg_count_suffix,    // 条消息 / messages
    drawer_time_just_now,       // 刚刚 / just now
    drawer_time_min_ago,        // 分钟前 / min ago
    drawer_time_hour_ago,       // 小时前 / h ago
    drawer_time_day_ago,        // 天前 / d ago
    drawer_filter_label,     // 筛选: %s / Filter: %s

    // ── MessageBubble ──
    msg_copy,                   // 复制 / Copy
    msg_copied,                 // 已复制 / Copied
    msg_regenerate,             // 重新生成 / Regenerate
    msg_delete,                 // 删除 / Delete
    msg_avatar_me,              // 我 / Me
    msg_avatar_assistant,       // 助手 / Assistant
    msg_image_loading,          // 加载图片… / Loading image…
    msg_image_failed,           // 图片加载失败 / Image failed to load

    // ── HomeScreen ──
    home_title,                 // 首页 / Home
    home_start_chat,            // 开始对话 / Start chat
    home_recent,                // 最近对话 / Recent sessions
    home_no_recent,             // 暂无对话 / No sessions yet
    home_my_characters,         // 我的角色 / My characters
    home_no_character,          // 还没有角色，去创建一个 / No characters yet, create one

    // ── MemoryScreen ──
    memory_title,               // 记忆管理 / Memory management
    memory_add,                 // 新增记忆 / Add memory
    memory_edit,                // 编辑记忆 / Edit memory
    memory_delete_title,        // 删除记忆 / Delete memory
    memory_delete_confirm,      // 确认删除这条记忆吗？ / Delete this memory?
    memory_search_hint,         // 搜索记忆内容… / Search memory content…
    memory_filter,              // 筛选 / Filter
    memory_filter_title,        // 筛选记忆 / Filter memories
    memory_filter_confirm,      // 确认筛选 / Confirm filter
    memory_filter_label,        // 筛选: %s / Filter: %s
    memory_unknown_role,        // 未知角色 / Unknown character
    memory_role_prefix,         // 角色: %s / Character: %s
    memory_loading_title,       // 正在加载记忆 / Loading memories
    memory_loading_msg,         // 请稍候… / Please wait…
    memory_empty_title,         // 还没有记忆 / No memories yet
    memory_empty_msg,           // 在对话里说"记住..."，或点右上角新增一条。 / Say "remember..." in chat, or tap top-right to add one.
    memory_updated_at,          // 更新时间：%s / Updated: %s
    memory_edit_action,         // 编辑记忆 / Edit memory
    memory_delete_action,       // 删除记忆 / Delete memory
    memory_promote_action,      // 提升为长期记忆 / Promote to long-term
    memory_field_content,       // 记忆内容 / Memory content
    memory_global,              // 全局记忆 / Global memory
    memory_all_roles,           // 全部角色 / All characters
    memory_tab_content,         // 内容 / Content
    memory_tab_category,        // 分类 / Category
    memory_tab_layer,           // 层级 / Layer
    memory_tab_role,            // 角色 / Character
    memory_layer_all,           // 全部 / All
    memory_layer_short,         // 短期 / Short-term
    memory_layer_long,          // 长期 / Long-term
    memory_cat_all,             // 全部 / All
    memory_cat_fact,            // 事实 / Fact
    memory_cat_preference,      // 偏好 / Preference
    memory_cat_event,           // 事件 / Event
    memory_cat_relation,        // 关系 / Relation
    memory_cat_time,            // 时间 / Time
    memory_cat_other,           // 其他 / Other

    // ── 记忆系统（改造 v2 新增）──
    memory_retrieved_title,     // 从记忆中检索到的与当前对话相关的信息
    memory_persistent_title,    // 长期记忆中的关键信息
    memory_user_note,           // 以下内容均为用户本人的记忆...
    memory_summary_title,       // 快速摘要
    memory_meta_section_title,  // 元记忆提示
    memory_category_fact,       // 事实
    memory_category_preference, // 偏好
    memory_category_event,      // 事件
    memory_category_behavior,   // 行为
    memory_category_knowledge,  // 知识
    memory_category_skill,      // 技能
    memory_category_relation,   // 关系
    memory_category_other,      // 其他
    memory_strength_label,      // 强度
    memory_strength_temporary,  // 临时
    memory_strength_short_term, // 短期
    memory_strength_long_term,  // 长期

    // ── SettingsScreen ──
    settings_title,             // 设置 / Settings
    settings_section_general,   // 通用 / General
    settings_item_language,     // 语言 / Language
    settings_item_dark_mode,    // 深色模式 / Dark mode
    settings_section_chat,      // 对话 / Chat
    settings_item_characters,   // 角色管理 / Characters
    settings_item_model,        // 模型配置 / Model config
    settings_item_voice,        // 语音设置 / Voice settings
    settings_item_memory,       // 记忆设置 / Memory settings
    settings_item_image,        // 图像生成 / Image generation
    settings_item_personalization,// 个性化 / Personalization
    settings_section_account,   // 账户 / Account
    settings_item_user_profile, // 用户资料 / User profile
    settings_item_skills,       // 技能管理 / Skills
    settings_section_about,     // 关于 / About
    settings_item_privacy,      // 隐私 / Privacy
    settings_item_about,        // 关于 / About

    // ── SettingsScreen 额外 ──
    settings_section_characters,// 角色 / Characters
    settings_section_memory,    // 记忆 / Memory
    settings_section_model,     // 模型 / Model
    settings_section_voice,    // 语音 / Voice
    settings_section_appearance,// 外观 / Appearance
    settings_item_context_window, // 上下文窗口大小 / Context window size
    settings_hint_nickname,     // 设置昵称 / Set nickname
    settings_hint_bio,          // 设置你的个人信息... / Set your personal info...
    settings_sub_characters,    // 创建和切换陪伴角色卡 / Create and switch character cards
    settings_sub_skills,        // 管理工作能力模板和自定义 skills / Manage skill templates and custom skills
    settings_sub_memory,        // 查看、编辑和提升短期记忆 / View, edit, and promote short-term memories
    settings_item_auto_learn,   // 自动学习偏好 / Auto learn preferences
    settings_sub_learn_on,      // 后台总结最近对话并逐步学习用户偏好 / Background summary learns user preferences
    settings_sub_learn_off,     // 已关闭后台偏好总结... / Background preference summary disabled...
    settings_sub_model,         // 选择模型、GPU/CPU 后端 / Select model, GPU/CPU backend
    settings_sub_context,       // 当前保留最近 %d 轮对话 / Currently keeping last %d rounds
    settings_sub_image,         // 配置联网图片生成 HTTP 接口 / Configure online image generation HTTP API
    settings_sub_voice,         // 语音输入输出、语速语调 / Voice I/O, speed, and pitch
    profile_avatar_desc,        // 用户头像 / User avatar
    profile_change_avatar,      // 更换头像 / Change avatar
    profile_edit_profile,       // 编辑个人资料 / Edit profile

    // ── AboutScreen ──
    about_title,                // 关于 / About
    about_version,              // 版本 / Version
    about_intro,                // 本地 AI 伴侣，完全离线运行 / Local AI companion, fully offline
    about_license,              // 开源协议 / Open source license
    about_source,               // 源代码 / Source code
    about_feedback,             // 反馈 / Feedback

    // ── DarkMode ──
    dark_mode_title,            // 深色模式 / Dark mode
    dark_mode_follow_system,    // 跟随系统 / Follow system
    dark_mode_on,               // 开启 / On
    dark_mode_off,              // 关闭 / Off

    // ── LanguageSettingsScreen ──
    language_title,             // 语言 / Language
    language_settings_title,    // 语言设置 / Language settings
    language_choose_hint,       // 选择界面语言 / Choose interface language
    language_zh,                // 中文 / Chinese
    language_en,                // 英文 / English
    language_switch_hint,       // 切换后全应用界面立即生效。 / Switch takes effect immediately across the whole app.

    // ── CharacterManagement ──
    char_mgmt_title,            // 角色管理 / Characters
    char_mgmt_new,              // 新建角色 / New character
    char_mgmt_delete_confirm,   // 确定删除该角色？ / Delete this character?
    char_mgmt_set_active,       // 设为使用中 / Set active
    char_mgmt_empty,            // 还没有角色，点击新建创建一个 / No characters yet, tap new to create one

    // ── ModelConfig ──
    model_title,                // 模型配置 / Model config
    model_path,                 // 模型路径 / Model path
    model_pick,                 // 选择模型 / Pick model
    model_quantization,         // 量化方式 / Quantization
    model_context_length,       // 上下文长度 / Context length
    model_threads,              // 线程数 / Threads
    model_gpu,                  // GPU 加速 / GPU acceleration
    model_reset,                // 重置默认 / Reset to default
    model_load,                 // 加载模型 / Load model
    model_unload,               // 卸载模型 / Unload model
    model_loaded,               // 已加载 / Loaded
    model_not_loaded,           // 未加载 / Not loaded


    // ── ModelConfigScreen 额外 ──
    model_context_window,       // 上下文窗口大小 / Context window size
    model_context_desc,         // 当前保留最近 %d 轮完整对话... / Currently keeping last %d rounds...
    model_backend,              // 推理后端 / Inference backend
    model_backend_llama_desc,   // 默认文本后端，读取外部 GGUF uncensor 模型。 / Default text backend, loads external GGUF model.
    model_backend_litert_desc,  // 可选多模态后端，继续支持图片输入链路。 / Optional multimodal backend for image input.
    model_backend_mnn_desc,     // MNN 推理框架，ARM 优化，支持 Qwen3.5 多模态 INT4 量化模型。 / MNN inference framework with ARM optimization, supports Qwen3.5 multimodal INT4 quantized models.
    model_gpu_desc,             // 使用 GPU 加速推理（LiteRT 后端） / Use GPU for inference (LiteRT backend)
    model_path_hint,            // 留空使用默认路径：%s / Leave empty to use default path: %s
    model_status_ready,         // 已就绪 / Ready
    model_status_missing,       // 缺失 / Missing
    model_apply,                // 应用模型配置 / Apply config
    model_context_hint,         // 建议范围 3~20... / Recommended range 3~20...
    context_retain_label,       // 保留最近 %d 轮 / Keep last %d rounds
    context_compress_hint,      // 压缩阈值约为 %d 条消息 / Compression threshold ~%d messages
    model_ready_generate,       // 模型就绪，可以生成图片 / Model ready, can generate images
    model_dir_not_configured,   // 模型目录未配置 / Model directory not configured
    model_invalid_config,       // 配置无效：%s / Invalid config: %s
    model_missing_files,        // 文件缺失：%s / Missing files: %s
    model_package_ready,        // 模型包已就绪：%s / Model package ready: %s

    // ── ImageGen 配置 ──
    image_provider_config,      // 图片生成 Provider 配置 / Image generation provider config
    image_http_desc,            // 使用通用 HTTP 图片接口，配置可真实生成图片 / Uses generic HTTP image API
    image_local_sd_desc,        // stable-diffusion.cpp + Vulkan，本地私有出图 / stable-diffusion.cpp + Vulkan, local private generation
    image_dreamlite_desc,       // 端侧接入框架已准备，等待官方权重/端侧包 / On-device framework ready, awaiting official weights
    image_http_title,           // HTTP 联网生成 / HTTP online generation
    image_local_sd_title,       // 本地 SD1.5 Hyper-SD / Local SD1.5 Hyper-SD
    image_dreamlite_title,      // 本地 DreamLite / Local DreamLite
    image_status_sd,            // Stable Diffusion 状态：%s / Stable Diffusion status: %s
    image_status_dreamlite,     // DreamLite 状态：%s / DreamLite status: %s
    image_local_width,          // 本地宽度 / Local width
    image_local_height,         // 本地高度 / Local height
    image_local_steps,          // 本地 Steps / Local Steps
    image_local_cfg,            // 本地 CFG Scale / Local CFG Scale
    image_local_seed,           // 本地 Seed（留空随机） / Local Seed (leave empty for random)
    image_enable_vulkan,        // 启用 Vulkan / Enable Vulkan
    image_template_hint,        // 模板支持 {{model}} 与 {{prompt}}... / Template supports {{model}} and {{prompt}}...
    image_mmproj_status,        // 图片 projector：%s / Image projector: %s

    // ── VoiceSettings ──
    voice_title,                // 语音设置 / Voice settings
    voice_output,               // 语音输出 / Voice output
    voice_auto_read,            // 自动朗读回复 / Auto read replies
    voice_speed,                // 语速 / Speed
    voice_pitch,                // 音调 / Pitch
    voice_select,               // 选择语音 / Select voice
    voice_test,                 // 测试语音 / Test voice
    voice_cloud_asr,            // 云端语音识别 / Cloud ASR

    // ── UserProfile ──
    profile_title,              // 用户资料 / User profile
    profile_nickname,           // 昵称 / Nickname
    profile_avatar,             // 头像 / Avatar
    profile_birthday,           // 生日 / Birthday
    profile_gender,             // 性别 / Gender
    profile_gender_male,        // 男 / Male
    profile_gender_female,      // 女 / Female
    profile_gender_other,       // 其他 / Other
    profile_interests,          // 兴趣 / Interests

    // ── SkillsManagement ──
    skills_title,               // 技能管理 / Skills
    skills_empty,               // 暂无可用技能 / No skills available

    // ── RoleCardEditor (基础字段，Sheet/Dialog 共用) ──
    role_edit_title,            // 编辑角色 / Edit character
    role_create_title,          // 创建角色 / Create character
    role_field_name,            // 名称 / Name
    role_field_description,     // 描述 / Description
    role_field_persona,         // 人设 / Persona
    role_field_speaking_style,  // 说话风格 / Speaking style
    role_field_background,      // 背景 / Background
    role_field_rules,           // 规则 / Rules
    role_field_taboos,          // 禁忌 / Taboos
    role_field_opening,         // 开场白 / Opening message
    role_field_example_dialogue,// 示例对话 / Example dialogue
    role_field_avatar,          // 头像 / Avatar
    role_field_gallery,         // 图库 / Gallery
    role_field_image_style,     // 图像风格提示 / Image style prompt
    role_field_voice_profile,   // 语音档案 / Voice profile
    role_field_voice_mode,      // 语音模式 / Voice mode
    role_field_voice_display,   // 语音显示名 / Voice display name
    role_delete_title,          // 删除角色 / Delete character
    role_delete_confirm,        // 确定删除该角色？ / Delete this character?
    role_name_required,         // 请输入名称 / Please enter a name
    role_pick_image,            // 选择图片 / Pick image
    // RoleCardEditorSheet 专用
    role_tab_basic,             // 基础 / Basic
    role_tab_persona,           // 人设 / Persona
    role_tab_image,             // 图片 / Image
    role_tab_voice,             // 语音 / Voice
    role_edit_card_title,       // 编辑角色卡 / Edit character card
    role_create_card_title,     // 创建角色卡 / Create character card
    role_avatar_icon,           // 头像图标 / Avatar icon
    role_avatar_icon_hint,      // 图标标识符，如 person、star、heart / Icon identifier, e.g. person, star, heart
    role_avatar_preview,        // 头像预览 / Avatar preview
    role_unnamed,               // 未命名 / Unnamed
    role_persona_core,          // 核心人设 * / Core persona *
    role_persona_core_hint,     // 描述角色的核心性格和行为特点 / Describe core personality and behavior
    role_speaking_style_hint,   // 温暖亲切，偶尔撒娇，喜欢用语气词 / Warm and friendly, occasionally playful
    role_background_story,      // 背景故事 / Background story
    role_background_hint,       // 角色的来历和背景设定 / Character origin and background
    role_rules_hint,            // 角色必须遵守的行为规则和约束 / Behavior rules and constraints
    role_taboos_hint,           // 角色绝对不能触碰的话题或行为 / Topics or behaviors strictly forbidden
    role_example_dialogue_hint, // 用户: 你好\n角色: 你好呀~很高兴见到你！ / User: Hi\nCharacter: Hi~ nice to meet you!
    role_avatar_image,          // 头像图片 / Avatar image
    role_remove_avatar,         // 移除头像 / Remove avatar
    role_pick_avatar_image,     // 选择头像图片 / Pick avatar image
    role_gallery_uri,           // 相册图片 URI（逗号分隔） / Gallery image URIs (comma separated)
    role_voice_mode_system,     // 系统 TTS / System TTS
    role_voice_mode_clone,      // MOSS 本地克隆 / MOSS local clone
    role_uploaded_clips,        // 已上传的语音片段 / Uploaded voice clips
    role_no_clips_hint,         // 暂无语音片段，请先上传一段参考音频（WAV 格式最佳）。 / No voice clips yet, upload a reference audio first (WAV recommended).
    role_stop,                  // 停止 / Stop
    role_play,                  // 播放 / Play
    role_upload_new_clip,       // 上传新语音片段 / Upload new voice clip
    role_clone_note,            // 选中的语音片段将作为该角色的默认语音。克隆后端不可用时会自动回退系统 TTS。 / Selected clip becomes default voice. Falls back to system TTS if clone backend unavailable.
    role_voice_display_hint,    // 温柔女声 / 磁性男声 / Gentle female / Magnetic male
    role_voice_package_uri,     // 语音包 URI / Voice package URI
    role_voice_package_hint,    // 自动从选中片段填入，也可手动输入 / Auto-filled from selected clip, or input manually
    role_default_moss_note,     // 未配置时将使用默认 MOSS 音色 / Uses default MOSS voice when not configured
    role_name_placeholder,      // 给角色取个名字 / Give the character a name
    role_desc_placeholder,      // 温柔治愈的邻家女孩 / Gentle healing girl next door
    role_opening_placeholder,   // 你好呀~今天想聊什么呢？ / Hi~ what shall we talk about today?
    role_field_tags,            // 标签 / Tags
    role_tags_add_hint,        // 添加标签 / Add tag

    // ── ChatViewModel Toast/Snackbar ──
    toast_permission_denied,    // 权限被拒绝 / Permission denied
    toast_model_load_failed,    // 模型加载失败 / Model load failed
    toast_generate_failed,      // 生成失败 / Generation failed
    toast_saved,                // 保存成功 / Saved
    toast_deleted,              // 已删除 / Deleted
    toast_network_error,        // 网络错误 / Network error
    snackbar_image_failed,      // 图片生成失败 / Image generation failed
    default_session_title,      // 新对话 / New chat
    // ChatViewModel 额外面向用户的字符串
    toast_moss_fallback,        // 检测到 MOSS 延迟过大，已回退到系统 TTS / MOSS latency too high, fell back to system TTS
    err_model_not_loaded,       // 模型未加载，请在设置中配置模型路径。 / Model not loaded, please configure model path in settings.
    err_inference,              // 推理出错: %s / Inference error: %s
    err_init_exception,         // 初始化异常: %s / Init exception: %s
    hint_input_msg,             // 输入消息… / Type a message…
    hint_suggestion_ready,      // 对话建议已生成，可修改后发送 / Suggestion generated, edit and send
    hint_suggestion_loading,    // 生成对话建议中… / Generating suggestion…
    hint_suggestion_failed,     // 建议生成失败，请重试 / Suggestion failed, please retry
    msg_compressing_context,    // 正在压缩上下文，请稍候… / Compressing context, please wait…

    // ── VoiceSettingsScreen 额外 ──
    voice_recognition_mode,     // 识别模式 / Recognition mode
    voice_recognition_backend,  // 识别后端 / Recognition backend
    voice_model_directory,      // 模型目录 / Model directory
    voice_model_status,         // 模型状态 / Model status
    voice_cloud_asr_label,      // 云 ASR / Cloud ASR
    voice_cloud_response_field, // 云响应字段 / Cloud response field
    voice_moss_directory,       // MOSS 目录 / MOSS directory
    voice_moss_status,          // MOSS 状态 / MOSS status
    voice_mnn_directory,        // MNN 目录 / MNN directory
    voice_mnn_status,           // MNN 状态 / MNN status
    voice_mnn_not_configured,   // MNN 未配置 / MNN not configured
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
    voice_interrupt_on_new,     // 发消息打断语音 / Interrupt on new msg
    voice_interrupt_on_new_desc, // 发送新消息时... / When sending a new message...
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
    chat_delete_message_confirm, // 确定删除这条消息？ / Delete this message?

    // ── 消息长按悬浮工具栏 ──
    msg_action_quote,           // 引用 / Quote
    msg_action_speak,           // 播放 / Play
    msg_action_pause,           // 暂停 / Pause
    msg_action_delete,          // 删除 / Delete
    msg_select_hint,            // 选择文字后点击上方按钮 / Select text then tap an action above
    quote_label,                // 引用 / Quote
    quote_from_user,            // 引用了我的消息 / Quoting my message
    quote_from_assistant,       // 引用了助手的消息 / Quoting assistant's message
    quote_clear,                // 取消引用 / Cancel quote
    quote_edit_title,           // 编辑引用片段 / Edit quote snippet
    quote_edit_hint,            // 调整或删除不需要的文字，确认后作为引用 / Trim or remove unwanted text, confirm to quote
    quote_locate,               // 定位到原消息 / Locate original message
    quote_no_text,              // 该消息只有图片，没有可引用的文字 / This message has only images, no text to quote
    scroll_to_bottom,           // 回到最新消息 / Scroll to latest message

    // ── CharacterManagement 额外 ──
    char_mgmt_persona_label,    // 人设：%s / Persona: %s
    char_mgmt_style_label,      // 风格：%s / Style: %s
    char_mgmt_image_label,      // 图片：头像%s，图库 %d 张 / Image: avatar %s, gallery %d images
    char_mgmt_voice_label,      // 语音：%s / Voice: %s
    char_mgmt_avatar_configured,// 已配置 / Configured
    char_mgmt_avatar_missing,   // 未配置 / Not configured

    // ── UserProfileScreen 额外 ──
    profile_tab_personality,    // 个性 / Personality
    profile_change_avatar_hint, // 修改头像 / Change avatar
    profile_click_to_change,    // 点击修改头像 / Tap to change avatar
    profile_name_placeholder,   // 给自己取个名字 / Give yourself a name
    profile_gender_placeholder, // 男 / 女 / 其他 / Male / Female / Other
    profile_age_label,          // 年龄 / Age
    profile_age_placeholder,    // 你的年龄 / Your age
    profile_bio_label,          // 个性签名 / Bio
    profile_bio_placeholder,    // 一句话介绍自己 / Describe yourself in one line
    profile_intro_label,        // 个人介绍 / Introduction
    profile_intro_placeholder,  // 详细介绍一下自己，让 AI 更了解你 / Introduce yourself in detail so AI knows you better
    profile_tags_placeholder,   // 用逗号分隔，如：阅读, 音乐, 游戏 / Comma separated, e.g. reading, music, gaming
    profile_important_label,    // 重要信息 / Important info
    profile_important_placeholder, // 希望 AI 记住的重要事情... / Important things for AI to remember...
    profile_help_text,          // 这些信息会帮助 AI... / This info helps AI understand you...

    // ── SkillsManagement 额外 ──
    skills_used_count,          // 已使用 %d 次 / Used %d times
    skills_delete_confirm,      // 确认删除"%s"吗？ / Delete "%s"?
    skills_custom_empty_title,  // 还没有自定义 Skills / No custom skills yet
    skills_custom_create_hint,  // 点击右上角"+"创建你的自定义 skill。 / Tap "+" at top right to create your custom skill.

    // ── DetailSection ──
    detail_persona,             // 人设摘要 / Persona summary
    detail_voice,               // 语音 / Voice
    detail_image_style,         // 图片风格 / Image style

    // ── DiscoverViewModel ──
    discover_import_failed,     // 导入角色失败 / Import character failed
    discover_image_added,       // 图片已加入角色图库 / Image added to character gallery
    discover_image_generated,   // 图片已生成: %s / Image generated: %s

    // ── VoiceDrivenChatPolicy ──
    voice_policy_no_text,       // 未识别到文本 / No text recognized
    voice_policy_generating,    // 正在生成回复，请稍后再说 / Generating reply, please wait
    voice_policy_not_ready,     // 模型未就绪，语音内容已保留在输入框 / Model not ready, voice content kept in input box
    toast_record_permission_denied, // 缺少录音权限，无法使用语音输入 / Microphone permission denied
}

/**
 * UI 字符串表。按 [AppLanguage] 提供翻译。
 *
 * 可扩展：新增语言只需在 [translations] 里加一个 `AppLanguage.XX to mapOf(...)`，
 * 提供 [StringsKey] 全部键的翻译即可，无需改动调用方。
 *
 * 注意：每个语言的 map 必须覆盖全部 [StringsKey]，缺失键会在 [get] 时回退到中文。
 */
object Strings {

    /** 中文翻译（默认/回退语言）。 */
    val ZH: Map<StringsKey, String> = mapOf(
        StringsKey.back to "返回",
        StringsKey.close to "关闭",
        StringsKey.confirm to "确认",
        StringsKey.cancel to "取消",
        StringsKey.save to "保存",
        StringsKey.delete to "删除",
        StringsKey.edit to "编辑",
        StringsKey.refresh to "刷新",
        StringsKey.search to "搜索",
        StringsKey.filter to "筛选",
        StringsKey.settings to "设置",
        StringsKey.enable to "启用",
        StringsKey.disable to "禁用",
        StringsKey.configure to "配置",
        StringsKey.selected to "已选中",
        StringsKey.loading to "加载中",
        StringsKey.tab_discover to "发现",
        StringsKey.tab_chat to "对话",
        StringsKey.tab_memory to "记忆",
        StringsKey.tab_settings to "设置",
        StringsKey.chat_title to "对话",
        StringsKey.chat_status_disconnected to "未连接",
        StringsKey.chat_status_loading to "模型加载中",
        StringsKey.chat_status_ready to "已就绪",
        StringsKey.chat_status_generating to "生成中",
        StringsKey.chat_status_error to "错误",
        StringsKey.chat_empty_hint to "直接输入第一条消息，或从左上角打开会话列表。",
        StringsKey.chat_compressing to "正在压缩上下文…",
        StringsKey.input_placeholder to "输入消息…",
        StringsKey.input_voice to "语音输入",
        StringsKey.input_send to "发送",
        StringsKey.input_stop to "�停止",
        StringsKey.input_read_aloud to "朗读",
        StringsKey.input_stop_reading to "停止朗读",
        StringsKey.input_pick_image to "选择图片",
        StringsKey.input_generate_image to "生成图片",
        StringsKey.drawer_title to "对话列表",
        StringsKey.drawer_search_hint to "搜索对话或角色",
        StringsKey.drawer_filter_today to "今天",
        StringsKey.drawer_filter_yesterday to "昨天",
        StringsKey.drawer_filter_week to "本周",
        StringsKey.drawer_filter_month to "本月",
        StringsKey.drawer_filter_all to "全部",
        StringsKey.drawer_filter_title to "筛选对话",
        StringsKey.drawer_filter_time_section to "时间",
        StringsKey.drawer_empty_no_match to "未找到匹配的对话",
        StringsKey.drawer_empty_period to "该时间段内暂无对话",
        StringsKey.drawer_empty_none to "暂无对话",
        StringsKey.drawer_new_chat to "新建对话",
        StringsKey.drawer_choose_character to "选择角色",
        StringsKey.drawer_new_character_hint to "自定义人设、头像和语音",
        StringsKey.drawer_existing_characters to "已有角色",
        StringsKey.drawer_no_character_hint to "还没有角色卡，先创建一个吧",
        StringsKey.drawer_active_tag to "使用中",
        StringsKey.drawer_blank_chat to "空白对话（不使用角色）",
        StringsKey.drawer_edit_title to "编辑标题",
        StringsKey.drawer_delete_session to "删除会话",
        StringsKey.drawer_msg_count_suffix to "条消息",
        StringsKey.drawer_time_just_now to "刚刚",
        StringsKey.drawer_time_min_ago to "分钟前",
        StringsKey.drawer_time_hour_ago to "小时前",
        StringsKey.drawer_time_day_ago to "天前",
        StringsKey.drawer_filter_label to "筛选: %s",
        StringsKey.msg_copy to "复制",
        StringsKey.msg_copied to "已复制",
        StringsKey.msg_regenerate to "重新生成",
        StringsKey.msg_delete to "删除",
        StringsKey.msg_avatar_me to "我",
        StringsKey.msg_avatar_assistant to "助手",
        StringsKey.msg_image_loading to "加载图片…",
        StringsKey.msg_image_failed to "图片加载失败",
        StringsKey.home_title to "首页",
        StringsKey.home_start_chat to "开始对话",
        StringsKey.home_recent to "最近对话",
        StringsKey.home_no_recent to "暂无对话",
        StringsKey.home_my_characters to "我的角色",
        StringsKey.home_no_character to "还没有角色，去创建一个",
        StringsKey.memory_title to "记忆管理",
        StringsKey.memory_add to "新增记忆",
        StringsKey.memory_edit to "编辑记忆",
        StringsKey.memory_delete_title to "删除记忆",
        StringsKey.memory_delete_confirm to "确认删除这条记忆吗？",
        StringsKey.memory_search_hint to "搜索记忆内容…",
        StringsKey.memory_filter to "筛选",
        StringsKey.memory_filter_title to "筛选记忆",
        StringsKey.memory_filter_confirm to "确认筛选",
        StringsKey.memory_filter_label to "筛选: %s",
        StringsKey.memory_unknown_role to "未知角色",
        StringsKey.memory_role_prefix to "角色: %s",
        StringsKey.memory_loading_title to "正在加载记忆",
        StringsKey.memory_loading_msg to "请稍候…",
        StringsKey.memory_empty_title to "还没有记忆",
        StringsKey.memory_empty_msg to "在对话里说\"记住...\"，或点右上角新增一条。",
        StringsKey.memory_updated_at to "更新时间：%s",
        StringsKey.memory_edit_action to "编辑记忆",
        StringsKey.memory_delete_action to "删除记忆",
        StringsKey.memory_promote_action to "提升为长期记忆",
        StringsKey.memory_field_content to "记忆内容",
        StringsKey.memory_global to "全局记忆",
        StringsKey.memory_all_roles to "全部角色",
        StringsKey.memory_tab_content to "内容",
        StringsKey.memory_tab_category to "分类",
        StringsKey.memory_tab_layer to "层级",
        StringsKey.memory_tab_role to "角色",
        StringsKey.memory_layer_all to "全部",
        StringsKey.memory_layer_short to "短期",
        StringsKey.memory_layer_long to "长期",
        StringsKey.memory_cat_all to "全部",
        StringsKey.memory_cat_fact to "事实",
        StringsKey.memory_cat_preference to "偏好",
        StringsKey.memory_cat_event to "事件",
        StringsKey.memory_cat_relation to "关系",
        StringsKey.memory_cat_time to "时间",
        StringsKey.memory_cat_other to "其他",
        StringsKey.memory_retrieved_title to "从记忆中检索到的与当前对话相关的信息",
        StringsKey.memory_persistent_title to "长期记忆中的关键信息",
        StringsKey.memory_user_note to "以下内容均为用户本人的记忆...",
        StringsKey.memory_summary_title to "快速摘要",
        StringsKey.memory_meta_section_title to "元记忆提示",
        StringsKey.memory_category_fact to "事实",
        StringsKey.memory_category_preference to "偏好",
        StringsKey.memory_category_event to "事件",
        StringsKey.memory_category_behavior to "行为",
        StringsKey.memory_category_knowledge to "知识",
        StringsKey.memory_category_skill to "技能",
        StringsKey.memory_category_relation to "关系",
        StringsKey.memory_category_other to "其他",
        StringsKey.memory_strength_label to "强度",
        StringsKey.memory_strength_temporary to "临时",
        StringsKey.memory_strength_short_term to "短期",
        StringsKey.memory_strength_long_term to "长期",
        StringsKey.settings_title to "设置",
        StringsKey.settings_section_general to "通用",
        StringsKey.settings_item_language to "语言",
        StringsKey.settings_item_dark_mode to "深色模式",
        StringsKey.settings_section_chat to "对话",
        StringsKey.settings_item_characters to "角色管理",
        StringsKey.settings_item_model to "模型配置",
        StringsKey.settings_item_voice to "语音设置",
        StringsKey.settings_item_memory to "记忆设置",
        StringsKey.settings_item_image to "图像生成",
        StringsKey.settings_section_account to "账户",
        StringsKey.settings_item_user_profile to "用户资料",
        StringsKey.settings_item_skills to "技能管理",
        StringsKey.settings_section_about to "关于",
        StringsKey.settings_item_privacy to "隐私",
        StringsKey.settings_item_about to "关于",
        StringsKey.settings_section_memory to "记忆",
        StringsKey.settings_section_model to "模型",
        StringsKey.settings_section_voice to "语音",
        StringsKey.settings_item_context_window to "上下文窗口大小",
        StringsKey.settings_hint_nickname to "设置昵称",
        StringsKey.settings_hint_bio to "设置你的个人信息...",
        StringsKey.settings_sub_characters to "创建和切换陪伴角色卡",
        StringsKey.settings_sub_skills to "管理工作能力模板和自定义 skills",
        StringsKey.settings_sub_memory to "查看、编辑和提升短期记忆",
        StringsKey.settings_item_auto_learn to "自动学习偏好",
        StringsKey.settings_sub_learn_on to "后台总结最近对话并逐步学习用户偏好",
        StringsKey.settings_sub_learn_off to "已关闭后台偏好总结...",
        StringsKey.settings_sub_model to "选择模型、GPU",
        StringsKey.settings_sub_context to "当前保留最近 %d 轮对话",
        StringsKey.settings_sub_image to "配置联网图片生成 HTTP 接口",
        StringsKey.settings_sub_voice to "语音输入输出、语速语调",
        StringsKey.profile_avatar_desc to "用户头像",
        StringsKey.profile_change_avatar to "更换头像",
        StringsKey.profile_edit_profile to "编辑个人资料",
        StringsKey.about_title to "关于",
        StringsKey.about_version to "版本",
        StringsKey.about_intro to "本地 AI 伴侣，完全离线运行",
        StringsKey.about_license to "开源协议",
        StringsKey.about_source to "源代码",
        StringsKey.about_feedback to "反馈",
        StringsKey.dark_mode_title to "深色模式",
        StringsKey.dark_mode_follow_system to "跟随系统",
        StringsKey.dark_mode_on to "开启",
        StringsKey.dark_mode_off to "关闭",
        StringsKey.language_title to "语言",
        StringsKey.language_settings_title to "语言设置",
        StringsKey.language_choose_hint to "选择界面语言",
        StringsKey.language_zh to "中文",
        StringsKey.language_en to "英文",
        StringsKey.language_switch_hint to "切换后全应用界面立即生效。",
        StringsKey.char_mgmt_title to "角色管理",
        StringsKey.char_mgmt_new to "新建角色",
        StringsKey.char_mgmt_delete_confirm to "确定删除该角色？",
        StringsKey.char_mgmt_set_active to "设为使用中",
        StringsKey.char_mgmt_empty to "还没有角色，点击新建创建一个",
        StringsKey.model_title to "模型配置",
        StringsKey.model_path to "模型路径",
        StringsKey.model_pick to "选择模型",
        StringsKey.model_quantization to "量化方式",
        StringsKey.model_context_length to "上下文长度",
        StringsKey.model_threads to "线程数",
        StringsKey.model_gpu to "GPU 加速",
        StringsKey.model_reset to "重置默认",
        StringsKey.model_load to "加载模型",
        StringsKey.model_unload to "卸载模型",
        StringsKey.model_loaded to "已加载",
        StringsKey.model_not_loaded to "未加载",
        StringsKey.model_context_window to "上下文窗口大小",
        StringsKey.model_context_desc to "当前保留最近 %d 轮完整对话...",
        StringsKey.model_backend to "推理后端",
        StringsKey.model_backend_llama_desc to "默认文本后端，读取外部 GGUF uncensor 模型。",
        StringsKey.model_backend_litert_desc to "可选多模态后端，继续支持图片输入链路。",
        StringsKey.model_backend_mnn_desc to "MNN 推理框架，ARM 优化，支持 Qwen3.5 多模态 INT4 量化模型",
        StringsKey.model_gpu_desc to "使用 GPU 加速推理（LiteRT 后端）",
        StringsKey.model_path_hint to "留空使用默认路径：%s",
        StringsKey.model_status_ready to "已就绪",
        StringsKey.model_status_missing to "缺失",
        StringsKey.model_apply to "应用模型配置",
        StringsKey.model_context_hint to "建议范围 3~20...",
        StringsKey.context_retain_label to "保留最近 %d 轮",
        StringsKey.context_compress_hint to "压缩阈值约为 %d 条消息",
        StringsKey.model_ready_generate to "模型就绪，可以生成图片",
        StringsKey.model_dir_not_configured to "模型目录未配置",
        StringsKey.model_invalid_config to "配置无效：%s",
        StringsKey.model_missing_files to "文件缺失：%s",
        StringsKey.model_package_ready to "模型包已就绪：%s",
        StringsKey.image_provider_config to "图片生成 Provider 配置",
        StringsKey.image_http_desc to "使用通用 HTTP 图片接口，配置可真实生成图片",
        StringsKey.image_local_sd_desc to "stable-diffusion.cpp + Vulkan，本地私有出图",
        StringsKey.image_dreamlite_desc to "端侧接入框架已准备，等待官方权重",
        StringsKey.image_http_title to "HTTP 联网生成",
        StringsKey.image_local_sd_title to "本地 SD1.5 Hyper-SD",
        StringsKey.image_dreamlite_title to "本地 DreamLite",
        StringsKey.image_status_sd to "Stable Diffusion 状态：%s",
        StringsKey.image_status_dreamlite to "DreamLite 状态：%s",
        StringsKey.image_local_width to "本地宽度",
        StringsKey.image_local_height to "本地高度",
        StringsKey.image_local_steps to "本地 Steps",
        StringsKey.image_local_cfg to "本地 CFG Scale",
        StringsKey.image_local_seed to "本地 Seed（留空随机）",
        StringsKey.image_enable_vulkan to "启用 Vulkan",
        StringsKey.image_template_hint to "模板支持 {{model}} 与 {{prompt}}...",
        StringsKey.image_mmproj_status to "图片 projector：%s",
        StringsKey.voice_title to "语音设置",
        StringsKey.voice_output to "语音输出",
        StringsKey.voice_auto_read to "自动朗读回复",
        StringsKey.voice_speed to "语速",
        StringsKey.voice_pitch to "音调",
        StringsKey.voice_select to "选择语音",
        StringsKey.voice_test to "测试语音",
        StringsKey.voice_cloud_asr to "云端语音识别",
        StringsKey.profile_title to "用户资料",
        StringsKey.profile_nickname to "昵称",
        StringsKey.profile_avatar to "头像",
        StringsKey.profile_birthday to "生日",
        StringsKey.profile_gender to "性别",
        StringsKey.profile_gender_male to "男",
        StringsKey.profile_gender_female to "女",
        StringsKey.profile_gender_other to "其他",
        StringsKey.profile_interests to "兴趣",
        StringsKey.skills_title to "技能管理",
        StringsKey.skills_empty to "暂无可用技能",
        StringsKey.role_edit_title to "编辑角色",
        StringsKey.role_create_title to "创建角色",
        StringsKey.role_field_name to "名称",
        StringsKey.role_field_description to "描述",
        StringsKey.role_field_persona to "人设",
        StringsKey.role_field_speaking_style to "说话风格",
        StringsKey.role_field_background to "背景",
        StringsKey.role_field_rules to "规则",
        StringsKey.role_field_taboos to "禁忌",
        StringsKey.role_field_opening to "开场白",
        StringsKey.role_field_avatar to "头像",
        StringsKey.role_field_gallery to "图库",
        StringsKey.role_field_image_style to "图像风格提示",
        StringsKey.role_field_voice_profile to "语音档案",
        StringsKey.role_field_voice_mode to "语音模式",
        StringsKey.role_field_voice_display to "语音显示名",
        StringsKey.role_delete_title to "删除角色",
        StringsKey.role_delete_confirm to "确定删除该角色？",
        StringsKey.role_name_required to "请输入名称",
        StringsKey.role_pick_image to "选择图片",
        StringsKey.role_tab_basic to "基础",
        StringsKey.role_tab_persona to "人设",
        StringsKey.role_tab_image to "图片",
        StringsKey.role_tab_voice to "语音",
        StringsKey.role_edit_card_title to "编辑角色卡",
        StringsKey.role_create_card_title to "创建角色卡",
        StringsKey.role_avatar_icon to "头像图标",
        StringsKey.role_avatar_icon_hint to "图标标识符，如 person、star、heart",
        StringsKey.role_avatar_preview to "头像预览",
        StringsKey.role_unnamed to "未命名",
        StringsKey.role_persona_core to "核心人设 *",
        StringsKey.role_persona_core_hint to "描述角色的核心性格和行为特点",
        StringsKey.role_speaking_style_hint to "温暖亲切，偶尔撒娇，喜欢用语气词",
        StringsKey.role_background_story to "背景故事",
        StringsKey.role_background_hint to "角色的来历和背景设定",
        StringsKey.role_rules_hint to "角色必须遵守的行为规则和约束",
        StringsKey.role_taboos_hint to "角色绝对不能触碰的话题或行为",
        StringsKey.role_example_dialogue_hint to "用户: 你好\n角色: 你好呀~很高兴见到你！",
        StringsKey.role_avatar_image to "头像图片",
        StringsKey.role_remove_avatar to "移除头像",
        StringsKey.role_pick_avatar_image to "选择头像图片",
        StringsKey.role_gallery_uri to "相册图片 URI（逗号分隔）",
        StringsKey.role_voice_mode_system to "系统 TTS",
        StringsKey.role_voice_mode_clone to "MOSS 本地克隆",
        StringsKey.role_uploaded_clips to "已上传的语音片段",
        StringsKey.role_no_clips_hint to "暂无语音片段，请先上传一段参考音频（WAV 格式最佳）。",
        StringsKey.role_stop to "停止",
        StringsKey.role_play to "播放",
        StringsKey.role_upload_new_clip to "上传新语音片段",
        StringsKey.role_clone_note to "选中的语音片段将作为该角色的默认语音。克隆后端不可用时会自动回退系统 TTS。",
        StringsKey.role_voice_display_hint to "温柔女声",
        StringsKey.role_voice_package_uri to "语音包 URI",
        StringsKey.role_voice_package_hint to "自动从选中片段填入，也可手动输入",
        StringsKey.role_default_moss_note to "未配置时将使用默认 MOSS 音色",
        StringsKey.role_name_placeholder to "给角色取个名字",
        StringsKey.role_desc_placeholder to "温柔治愈的邻家女孩",
        StringsKey.role_opening_placeholder to "你好呀~今天想聊什么呢？",
        StringsKey.role_field_tags to "标签",
        StringsKey.role_tags_add_hint to "添加标签，回车确认",
        StringsKey.toast_permission_denied to "权限被拒绝",
        StringsKey.toast_model_load_failed to "模型加载失败",
        StringsKey.toast_generate_failed to "生成失败",
        StringsKey.toast_saved to "保存成功",
        StringsKey.toast_deleted to "已删除",
        StringsKey.toast_network_error to "网络错误",
        StringsKey.snackbar_image_failed to "图片生成失败",
        StringsKey.default_session_title to "新对话",
        StringsKey.toast_moss_fallback to "检测到 MOSS 延迟过大，已回退到系统 TTS",
        StringsKey.err_model_not_loaded to "模型未加载，请在设置中配置模型路径。",
        StringsKey.err_inference to "推理出错: %s",
        StringsKey.err_init_exception to "初始化异常: %s",
        StringsKey.hint_input_msg to "输入消息…",
        StringsKey.hint_suggestion_ready to "对话建议已生成，可修改后发送",
        StringsKey.hint_suggestion_loading to "生成对话建议中…",
        StringsKey.hint_suggestion_failed to "建议生成失败，请重试",
        StringsKey.msg_compressing_context to "正在压缩上下文，请稍候…",
        StringsKey.voice_recognition_mode to "识别模式",
        StringsKey.voice_recognition_backend to "识别后端",
        StringsKey.voice_model_directory to "模型目录",
        StringsKey.voice_model_status to "模型状态",
        StringsKey.voice_cloud_asr_label to "云 ASR",
        StringsKey.voice_cloud_response_field to "云响应字段",
        StringsKey.voice_moss_directory to "MOSS 目录",
        StringsKey.voice_moss_status to "MOSS 状态",
        StringsKey.voice_mnn_directory to "MNN 目录",
        StringsKey.voice_mnn_status to "MNN 状态",
        StringsKey.voice_mnn_not_configured to "MNN 模型未配置",
        StringsKey.voice_local_clone to "本地克隆",
        StringsKey.voice_output_mode to "输出模式",
        StringsKey.voice_default_timbre to "默认音色",
        StringsKey.voice_role_voice to "角色语音",
        StringsKey.voice_not_configured to "未配置",
        StringsKey.voice_configured to "已配置",
        StringsKey.voice_local_sensevoice to "本地 SenseVoice ASR",
        StringsKey.voice_cloud_http_asr to "云 HTTP ASR",
        StringsKey.voice_moss_nano_default to "MOSS TTS Nano（默认引擎）",
        StringsKey.voice_fallback_tts to "回退系统 TTS",
        StringsKey.voice_clone_default to "MOSS 本地克隆（默认）",
        StringsKey.voice_role_voice_hint to "在角色管理中配置...",
        StringsKey.voice_desc to "语音输入默认使用...",
        StringsKey.voice_interrupt_on_new to "发消息打断语音",
        StringsKey.voice_interrupt_on_new_desc to "发送新消息时，立即打断正在播放的语音，只播放最新回复",
        StringsKey.voice_auto_read_desc to "AI 开始回复 0.5 秒后...",
        StringsKey.voice_ready to "完整",
        StringsKey.voice_local_not_configured to "本地 SenseVoice 模型未配置",
        StringsKey.voice_moss_not_configured to "moss-tts-nano 模型未配置",
        StringsKey.voice_invalid_config to "配置无效：%s",
        StringsKey.voice_missing_files to "文件缺失：%s",
        StringsKey.home_discover to "发现",
        StringsKey.home_sort to "排序",
        StringsKey.home_sort_hot to "热门",
        StringsKey.home_sort_newest to "最新",
        StringsKey.home_sort_name to "名称",
        StringsKey.home_search_hint to "搜索角色、作者、标签",
        StringsKey.home_create_your_role to "创建你的角色",
        StringsKey.home_create_hint to "人设、头像、语音会保存到角色卡",
        StringsKey.home_create to "创建",
        StringsKey.home_show_mature to "显示私密",
        StringsKey.home_role_detail to "角色详情",
        StringsKey.home_not_found to "未找到角色",
        StringsKey.home_by_author to "by %s",
        StringsKey.home_imported to "已导入",
        StringsKey.home_unlocked to "已解锁",
        StringsKey.home_start_chat_btn to "开始聊天",
        StringsKey.home_unlock_to_favorite to "收藏解锁",
        StringsKey.home_generating to "生成中",
        StringsKey.home_generate_image to "生成图片",
        StringsKey.home_edit_character to "编辑角色卡",
        StringsKey.home_mature_label to "私密",
        StringsKey.chat_quick_continue to "继续聊聊",
        StringsKey.chat_quick_generate_img to "生成此刻图片",
        StringsKey.chat_delete_message_confirm to "确定删除这条消息？",
        StringsKey.msg_action_quote to "引用",
        StringsKey.msg_action_speak to "播放",
        StringsKey.msg_action_pause to "暂停",
        StringsKey.msg_action_delete to "删除",
        StringsKey.msg_select_hint to "选择文字后点击上方按钮",
        StringsKey.quote_label to "引用",
        StringsKey.quote_from_user to "引用了我的消息",
        StringsKey.quote_from_assistant to "引用了助手的消息",
        StringsKey.quote_clear to "取消引用",
        StringsKey.quote_edit_title to "编辑引用片段",
        StringsKey.quote_edit_hint to "调整或删除不需要的文字，确认后作为引用",
        StringsKey.quote_locate to "定位到原消息",
        StringsKey.quote_no_text to "该消息只有图片，没有可引用的文字",
        StringsKey.scroll_to_bottom to "回到最新消息",
        StringsKey.char_mgmt_persona_label to "人设：%s",
        StringsKey.char_mgmt_style_label to "风格：%s",
        StringsKey.char_mgmt_image_label to "图片：头像%s，图库 %d 张",
        StringsKey.char_mgmt_voice_label to "语音：%s",
        StringsKey.char_mgmt_avatar_missing to "未配置",
        StringsKey.profile_tab_personality to "个性",
        StringsKey.profile_change_avatar_hint to "修改头像",
        StringsKey.profile_click_to_change to "点击修改头像",
        StringsKey.profile_name_placeholder to "给自己取个名字",
        StringsKey.profile_gender_placeholder to "男",
        StringsKey.profile_age_label to "年龄",
        StringsKey.profile_age_placeholder to "你的年龄",
        StringsKey.profile_bio_label to "个性签名",
        StringsKey.profile_bio_placeholder to "一句话介绍自己",
        StringsKey.profile_intro_label to "个人介绍",
        StringsKey.profile_intro_placeholder to "详细介绍一下自己，让 AI 更了解你",
        StringsKey.profile_tags_placeholder to "用逗号分隔，如：阅读, 音乐, 游戏",
        StringsKey.profile_important_label to "重要信息",
        StringsKey.profile_important_placeholder to "希望 AI 记住的重要事情...",
        StringsKey.profile_help_text to "这些信息会帮助 AI...",
        StringsKey.skills_used_count to "已使用 %d 次",
        StringsKey.skills_delete_confirm to "确认删除\"%s\"吗？",
        StringsKey.skills_custom_empty_title to "还没有自定义 Skills",
        StringsKey.skills_custom_create_hint to "点击右上角\"+\"创建你的自定义 skill。",
        StringsKey.detail_persona to "人设摘要",
        StringsKey.detail_voice to "语音",
        StringsKey.detail_image_style to "图片风格",
        StringsKey.discover_import_failed to "导入角色失败",
        StringsKey.discover_image_added to "图片已加入角色图库",
        StringsKey.discover_image_generated to "图片已生成: %s",
        StringsKey.voice_policy_no_text to "未识别到文本",
        StringsKey.voice_policy_generating to "正在生成回复，请稍后再说",
        StringsKey.voice_policy_not_ready to "模型未就绪，语音内容已保留在输入框",
        StringsKey.toast_record_permission_denied to "缺少录音权限，无法使用语音输入",
    
        StringsKey.settings_section_characters to "角色",
        StringsKey.settings_section_appearance to "外观",
        StringsKey.settings_item_personalization to "个性化",
        StringsKey.drawer_create_new_character to "创建新角色",
)

    val EN: Map<StringsKey, String> = mapOf(
        StringsKey.back to "Back",
        StringsKey.close to "Close",
        StringsKey.confirm to "Confirm",
        StringsKey.cancel to "Cancel",
        StringsKey.save to "Save",
        StringsKey.delete to "Delete",
        StringsKey.edit to "Edit",
        StringsKey.refresh to "Refresh",
        StringsKey.search to "Search",
        StringsKey.filter to "Filter",
        StringsKey.settings to "Settings",
        StringsKey.enable to "Enable",
        StringsKey.disable to "Disable",
        StringsKey.configure to "Configure",
        StringsKey.selected to "Selected",
        StringsKey.loading to "Loading",
        StringsKey.tab_discover to "Discover",
        StringsKey.tab_chat to "Chat",
        StringsKey.tab_memory to "Memory",
        StringsKey.tab_settings to "Settings",
        StringsKey.chat_title to "Chat",
        StringsKey.chat_status_disconnected to "Disconnected",
        StringsKey.chat_status_loading to "Loading model",
        StringsKey.chat_status_ready to "Ready",
        StringsKey.chat_status_generating to "Generating",
        StringsKey.chat_status_error to "Error",
        StringsKey.chat_empty_hint to "Type your first message, or open sessions from top-left.",
        StringsKey.chat_compressing to "Compressing context…",
        StringsKey.input_placeholder to "Type a message…",
        StringsKey.input_voice to "Voice input",
        StringsKey.input_send to "Send",
        StringsKey.input_stop to "Stop",
        StringsKey.input_read_aloud to "Read aloud",
        StringsKey.input_stop_reading to "Stop reading",
        StringsKey.input_pick_image to "Pick image",
        StringsKey.input_generate_image to "Generate image",
        StringsKey.drawer_title to "Sessions",
        StringsKey.drawer_search_hint to "Search sessions or characters",
        StringsKey.drawer_filter_today to "Today",
        StringsKey.drawer_filter_yesterday to "Yesterday",
        StringsKey.drawer_filter_week to "This week",
        StringsKey.drawer_filter_month to "This month",
        StringsKey.drawer_filter_all to "All",
        StringsKey.drawer_filter_title to "Filter sessions",
        StringsKey.drawer_filter_time_section to "Time",
        StringsKey.drawer_empty_no_match to "No matching sessions",
        StringsKey.drawer_empty_period to "No sessions in this period",
        StringsKey.drawer_empty_none to "No sessions yet",
        StringsKey.drawer_new_chat to "New chat",
        StringsKey.drawer_choose_character to "Choose character",
        StringsKey.drawer_new_character_hint to "Custom persona, avatar and voice",
        StringsKey.drawer_existing_characters to "Existing characters",
        StringsKey.drawer_no_character_hint to "No character cards yet, create one first",
        StringsKey.drawer_active_tag to "Active",
        StringsKey.drawer_blank_chat to "Blank chat (no character)",
        StringsKey.drawer_edit_title to "Edit title",
        StringsKey.drawer_delete_session to "Delete session",
        StringsKey.drawer_msg_count_suffix to "messages",
        StringsKey.drawer_time_just_now to "just now",
        StringsKey.drawer_time_min_ago to "min ago",
        StringsKey.drawer_time_hour_ago to "h ago",
        StringsKey.drawer_time_day_ago to "d ago",
        StringsKey.drawer_filter_label to "Filter: %s",
        StringsKey.msg_copy to "Copy",
        StringsKey.msg_copied to "Copied",
        StringsKey.msg_regenerate to "Regenerate",
        StringsKey.msg_delete to "Delete",
        StringsKey.msg_avatar_me to "Me",
        StringsKey.msg_avatar_assistant to "Assistant",
        StringsKey.msg_image_loading to "Loading image…",
        StringsKey.msg_image_failed to "Image failed to load",
        StringsKey.home_title to "Home",
        StringsKey.home_start_chat to "Start chat",
        StringsKey.home_recent to "Recent sessions",
        StringsKey.home_no_recent to "No sessions yet",
        StringsKey.home_my_characters to "My characters",
        StringsKey.home_no_character to "No characters yet, create one",
        StringsKey.memory_title to "Memory management",
        StringsKey.memory_add to "Add memory",
        StringsKey.memory_edit to "Edit memory",
        StringsKey.memory_delete_title to "Delete memory",
        StringsKey.memory_delete_confirm to "Delete this memory?",
        StringsKey.memory_search_hint to "Search memory content…",
        StringsKey.memory_filter to "Filter",
        StringsKey.memory_filter_title to "Filter memories",
        StringsKey.memory_filter_confirm to "Confirm filter",
        StringsKey.memory_filter_label to "Filter: %s",
        StringsKey.memory_unknown_role to "Unknown character",
        StringsKey.memory_role_prefix to "Character: %s",
        StringsKey.memory_loading_title to "Loading memories",
        StringsKey.memory_loading_msg to "Please wait…",
        StringsKey.memory_empty_title to "No memories yet",
        StringsKey.memory_empty_msg to "Say \"remember...\" in chat, or tap top-right to add one.",
        StringsKey.memory_updated_at to "Updated: %s",
        StringsKey.memory_edit_action to "Edit memory",
        StringsKey.memory_delete_action to "Delete memory",
        StringsKey.memory_promote_action to "Promote to long-term",
        StringsKey.memory_field_content to "Memory content",
        StringsKey.memory_global to "Global memory",
        StringsKey.memory_all_roles to "All characters",
        StringsKey.memory_tab_content to "Content",
        StringsKey.memory_tab_category to "Category",
        StringsKey.memory_tab_layer to "Layer",
        StringsKey.memory_tab_role to "Character",
        StringsKey.memory_layer_all to "All",
        StringsKey.memory_layer_short to "Short-term",
        StringsKey.memory_layer_long to "Long-term",
        StringsKey.memory_cat_all to "All",
        StringsKey.memory_cat_fact to "Fact",
        StringsKey.memory_cat_preference to "Preference",
        StringsKey.memory_cat_event to "Event",
        StringsKey.memory_cat_relation to "Relation",
        StringsKey.memory_cat_time to "Time",
        StringsKey.memory_cat_other to "Other",
        StringsKey.memory_retrieved_title to "从记忆中检索到的与当前对话相关的信息",
        StringsKey.memory_persistent_title to "长期记忆中的关键信息",
        StringsKey.memory_user_note to "以下内容均为用户本人的记忆...",
        StringsKey.memory_summary_title to "快速摘要",
        StringsKey.memory_meta_section_title to "元记忆提示",
        StringsKey.memory_category_fact to "事实",
        StringsKey.memory_category_preference to "偏好",
        StringsKey.memory_category_event to "事件",
        StringsKey.memory_category_behavior to "行为",
        StringsKey.memory_category_knowledge to "知识",
        StringsKey.memory_category_skill to "技能",
        StringsKey.memory_category_relation to "关系",
        StringsKey.memory_category_other to "其他",
        StringsKey.memory_strength_label to "强度",
        StringsKey.memory_strength_temporary to "Temporary",
        StringsKey.memory_strength_short_term to "Short-term",
        StringsKey.memory_strength_long_term to "Long-term",
        StringsKey.settings_title to "Settings",
        StringsKey.settings_section_general to "General",
        StringsKey.settings_item_language to "Language",
        StringsKey.settings_item_dark_mode to "Dark mode",
        StringsKey.settings_section_chat to "Chat",
        StringsKey.settings_item_characters to "Characters",
        StringsKey.settings_item_model to "Model config",
        StringsKey.settings_item_voice to "Voice settings",
        StringsKey.settings_item_memory to "Memory settings",
        StringsKey.settings_item_image to "Image generation",
        StringsKey.settings_section_account to "Account",
        StringsKey.settings_item_user_profile to "User profile",
        StringsKey.settings_item_skills to "Skills",
        StringsKey.settings_section_about to "About",
        StringsKey.settings_item_privacy to "Privacy",
        StringsKey.settings_item_about to "About",
        StringsKey.settings_section_memory to "Memory",
        StringsKey.settings_section_model to "Model",
        StringsKey.settings_section_voice to "Voice",
        StringsKey.settings_item_context_window to "Context window size",
        StringsKey.settings_hint_nickname to "Set nickname",
        StringsKey.settings_hint_bio to "Set your personal info...",
        StringsKey.settings_sub_characters to "Create and switch character cards",
        StringsKey.settings_sub_skills to "Manage skill templates and custom skills",
        StringsKey.settings_sub_memory to "View, edit, and promote short-term memories",
        StringsKey.settings_item_auto_learn to "Auto learn preferences",
        StringsKey.settings_sub_learn_on to "Background summary learns user preferences",
        StringsKey.settings_sub_learn_off to "Background preference summary disabled...",
        StringsKey.settings_sub_model to "Select model, GPU/CPU backend",
        StringsKey.settings_sub_context to "Currently keeping last %d rounds",
        StringsKey.settings_sub_image to "Configure online image generation HTTP API",
        StringsKey.settings_sub_voice to "Voice I/O, speed, and pitch",
        StringsKey.profile_avatar_desc to "User avatar",
        StringsKey.profile_change_avatar to "Change avatar",
        StringsKey.profile_edit_profile to "Edit profile",
        StringsKey.about_title to "About",
        StringsKey.about_version to "Version",
        StringsKey.about_intro to "Local AI companion, fully offline",
        StringsKey.about_license to "Open source license",
        StringsKey.about_source to "Source code",
        StringsKey.about_feedback to "Feedback",
        StringsKey.dark_mode_title to "Dark mode",
        StringsKey.dark_mode_follow_system to "Follow system",
        StringsKey.dark_mode_on to "On",
        StringsKey.dark_mode_off to "Off",
        StringsKey.language_title to "Language",
        StringsKey.language_settings_title to "Language settings",
        StringsKey.language_choose_hint to "Choose interface language",
        StringsKey.language_zh to "Chinese",
        StringsKey.language_en to "English",
        StringsKey.language_switch_hint to "Switch takes effect immediately across the whole app.",
        StringsKey.char_mgmt_title to "Characters",
        StringsKey.char_mgmt_new to "New character",
        StringsKey.char_mgmt_delete_confirm to "Delete this character?",
        StringsKey.char_mgmt_set_active to "Set active",
        StringsKey.char_mgmt_empty to "No characters yet, tap new to create one",
        StringsKey.model_title to "Model config",
        StringsKey.model_path to "Model path",
        StringsKey.model_pick to "Pick model",
        StringsKey.model_quantization to "Quantization",
        StringsKey.model_context_length to "Context length",
        StringsKey.model_threads to "Threads",
        StringsKey.model_gpu to "GPU acceleration",
        StringsKey.model_reset to "Reset to default",
        StringsKey.model_load to "Load model",
        StringsKey.model_unload to "Unload model",
        StringsKey.model_loaded to "Loaded",
        StringsKey.model_not_loaded to "Not loaded",
        StringsKey.model_context_window to "Context window size",
        StringsKey.model_context_desc to "Currently keeping last %d rounds...",
        StringsKey.model_backend to "Inference backend",
        StringsKey.model_backend_llama_desc to "Default text backend, loads external GGUF model.",
        StringsKey.model_backend_litert_desc to "Optional multimodal backend for image input.",
        StringsKey.model_backend_mnn_desc to "MNN inference framework with ARM optimization, supports Qwen3.5 multimodal INT4 quantized models",
        StringsKey.model_gpu_desc to "Use GPU for inference (LiteRT backend)",
        StringsKey.model_path_hint to "Leave empty to use default path: %s",
        StringsKey.model_status_ready to "Ready",
        StringsKey.model_status_missing to "Missing",
        StringsKey.model_apply to "Apply config",
        StringsKey.model_context_hint to "Recommended range 3~20...",
        StringsKey.context_retain_label to "Keep last %d rounds",
        StringsKey.context_compress_hint to "Compression threshold ~%d messages",
        StringsKey.model_ready_generate to "Model ready, can generate images",
        StringsKey.model_dir_not_configured to "Model directory not configured",
        StringsKey.model_invalid_config to "Invalid config: %s",
        StringsKey.model_missing_files to "Missing files: %s",
        StringsKey.model_package_ready to "Model package ready: %s",
        StringsKey.image_provider_config to "Image generation provider config",
        StringsKey.image_http_desc to "Uses generic HTTP image API",
        StringsKey.image_local_sd_desc to "stable-diffusion.cpp + Vulkan, local private generation",
        StringsKey.image_dreamlite_desc to "端侧包 / On-device framework ready, awaiting official weights",
        StringsKey.image_http_title to "HTTP online generation",
        StringsKey.image_local_sd_title to "Local SD1.5 Hyper-SD",
        StringsKey.image_dreamlite_title to "Local DreamLite",
        StringsKey.image_status_sd to "Stable Diffusion status: %s",
        StringsKey.image_status_dreamlite to "DreamLite status: %s",
        StringsKey.image_local_width to "Local width",
        StringsKey.image_local_height to "Local height",
        StringsKey.image_local_steps to "Local Steps",
        StringsKey.image_local_cfg to "Local CFG Scale",
        StringsKey.image_local_seed to "Local Seed (leave empty for random)",
        StringsKey.image_enable_vulkan to "Enable Vulkan",
        StringsKey.image_template_hint to "Template supports {{model}} and {{prompt}}...",
        StringsKey.image_mmproj_status to "Image projector: %s",
        StringsKey.voice_title to "Voice settings",
        StringsKey.voice_output to "Voice output",
        StringsKey.voice_auto_read to "Auto read replies",
        StringsKey.voice_speed to "Speed",
        StringsKey.voice_pitch to "Pitch",
        StringsKey.voice_select to "Select voice",
        StringsKey.voice_test to "Test voice",
        StringsKey.voice_cloud_asr to "Cloud ASR",
        StringsKey.profile_title to "User profile",
        StringsKey.profile_nickname to "Nickname",
        StringsKey.profile_avatar to "Avatar",
        StringsKey.profile_birthday to "Birthday",
        StringsKey.profile_gender to "Gender",
        StringsKey.profile_gender_male to "Male",
        StringsKey.profile_gender_female to "Female",
        StringsKey.profile_gender_other to "Other",
        StringsKey.profile_interests to "Interests",
        StringsKey.skills_title to "Skills",
        StringsKey.skills_empty to "No skills available",
        StringsKey.role_edit_title to "Edit character",
        StringsKey.role_create_title to "Create character",
        StringsKey.role_field_name to "Name",
        StringsKey.role_field_description to "Description",
        StringsKey.role_field_persona to "Persona",
        StringsKey.role_field_speaking_style to "Speaking style",
        StringsKey.role_field_background to "Background",
        StringsKey.role_field_rules to "Rules",
        StringsKey.role_field_taboos to "Taboos",
        StringsKey.role_field_opening to "Opening message",
        StringsKey.role_field_avatar to "Avatar",
        StringsKey.role_field_gallery to "Gallery",
        StringsKey.role_field_image_style to "Image style prompt",
        StringsKey.role_field_voice_profile to "Voice profile",
        StringsKey.role_field_voice_mode to "Voice mode",
        StringsKey.role_field_voice_display to "Voice display name",
        StringsKey.role_delete_title to "Delete character",
        StringsKey.role_delete_confirm to "Delete this character?",
        StringsKey.role_name_required to "Please enter a name",
        StringsKey.role_pick_image to "Pick image",
        StringsKey.role_tab_basic to "Basic",
        StringsKey.role_tab_persona to "Persona",
        StringsKey.role_tab_image to "Image",
        StringsKey.role_tab_voice to "Voice",
        StringsKey.role_edit_card_title to "Edit character card",
        StringsKey.role_create_card_title to "Create character card",
        StringsKey.role_avatar_icon to "Avatar icon",
        StringsKey.role_avatar_icon_hint to "Icon identifier, e.g. person, star, heart",
        StringsKey.role_avatar_preview to "Avatar preview",
        StringsKey.role_unnamed to "Unnamed",
        StringsKey.role_persona_core to "Core persona *",
        StringsKey.role_persona_core_hint to "Describe core personality and behavior",
        StringsKey.role_speaking_style_hint to "Warm and friendly, occasionally playful",
        StringsKey.role_background_story to "Background story",
        StringsKey.role_background_hint to "Character origin and background",
        StringsKey.role_rules_hint to "Behavior rules and constraints",
        StringsKey.role_taboos_hint to "Topics or behaviors strictly forbidden",
        StringsKey.role_example_dialogue_hint to "User: Hi\nCharacter: Hi~ nice to meet you!",
        StringsKey.role_avatar_image to "Avatar image",
        StringsKey.role_remove_avatar to "Remove avatar",
        StringsKey.role_pick_avatar_image to "Pick avatar image",
        StringsKey.role_gallery_uri to "Gallery image URIs (comma separated)",
        StringsKey.role_voice_mode_system to "System TTS",
        StringsKey.role_voice_mode_clone to "MOSS local clone",
        StringsKey.role_uploaded_clips to "Uploaded voice clips",
        StringsKey.role_no_clips_hint to "No voice clips yet, upload a reference audio first (WAV recommended).",
        StringsKey.role_stop to "Stop",
        StringsKey.role_play to "Play",
        StringsKey.role_upload_new_clip to "Upload new voice clip",
        StringsKey.role_clone_note to "Selected clip becomes default voice. Falls back to system TTS if clone backend unavailable.",
        StringsKey.role_voice_display_hint to "磁性男声 / Gentle female / Magnetic male",
        StringsKey.role_voice_package_uri to "Voice package URI",
        StringsKey.role_voice_package_hint to "Auto-filled from selected clip, or input manually",
        StringsKey.role_default_moss_note to "Uses default MOSS voice when not configured",
        StringsKey.role_name_placeholder to "Give the character a name",
        StringsKey.role_desc_placeholder to "Gentle healing girl next door",
        StringsKey.role_opening_placeholder to "Hi~ what shall we talk about today?",
        StringsKey.role_field_tags to "Tags",
        StringsKey.role_tags_add_hint to "Add tag, press enter to confirm",
        StringsKey.toast_permission_denied to "Permission denied",
        StringsKey.toast_model_load_failed to "Model load failed",
        StringsKey.toast_generate_failed to "Generation failed",
        StringsKey.toast_saved to "Saved",
        StringsKey.toast_deleted to "Deleted",
        StringsKey.toast_network_error to "Network error",
        StringsKey.snackbar_image_failed to "Image generation failed",
        StringsKey.default_session_title to "New chat",
        StringsKey.toast_moss_fallback to "MOSS latency too high, fell back to system TTS",
        StringsKey.err_model_not_loaded to "Model not loaded, please configure model path in settings.",
        StringsKey.err_inference to "Inference error: %s",
        StringsKey.err_init_exception to "Init exception: %s",
        StringsKey.hint_input_msg to "Type a message…",
        StringsKey.hint_suggestion_ready to "Suggestion generated, edit and send",
        StringsKey.hint_suggestion_loading to "Generating suggestion…",
        StringsKey.hint_suggestion_failed to "Suggestion failed, please retry",
        StringsKey.msg_compressing_context to "Compressing context, please wait…",
        StringsKey.voice_recognition_mode to "Recognition mode",
        StringsKey.voice_recognition_backend to "Recognition backend",
        StringsKey.voice_model_directory to "Model directory",
        StringsKey.voice_model_status to "Model status",
        StringsKey.voice_cloud_asr_label to "Cloud ASR",
        StringsKey.voice_cloud_response_field to "Cloud response field",
        StringsKey.voice_moss_directory to "MOSS directory",
        StringsKey.voice_moss_status to "MOSS status",
        StringsKey.voice_mnn_directory to "MNN directory",
        StringsKey.voice_mnn_status to "MNN status",
        StringsKey.voice_mnn_not_configured to "MNN model not configured",
        StringsKey.voice_local_clone to "Local clone",
        StringsKey.voice_output_mode to "Output mode",
        StringsKey.voice_default_timbre to "Default voice",
        StringsKey.voice_role_voice to "Character voice",
        StringsKey.voice_not_configured to "Not configured",
        StringsKey.voice_configured to "Configured",
        StringsKey.voice_local_sensevoice to "Local SenseVoice ASR",
        StringsKey.voice_cloud_http_asr to "Cloud HTTP ASR",
        StringsKey.voice_moss_nano_default to "MOSS TTS Nano (default engine)",
        StringsKey.voice_fallback_tts to "Fallback to system TTS",
        StringsKey.voice_clone_default to "MOSS local clone (default)",
        StringsKey.voice_role_voice_hint to "Configure in character management...",
        StringsKey.voice_desc to "Voice input uses local SenseVoice...",
        StringsKey.voice_interrupt_on_new to "Interrupt on new msg",
        StringsKey.voice_interrupt_on_new_desc to "When sending a new message, immediately stop current TTS and only play the latest response",
        StringsKey.voice_auto_read_desc to "Auto-read 0.5s after AI starts replying",
        StringsKey.voice_ready to "Complete",
        StringsKey.voice_local_not_configured to "Local SenseVoice model not configured",
        StringsKey.voice_moss_not_configured to "moss-tts-nano model not configured",
        StringsKey.voice_invalid_config to "Invalid config: %s",
        StringsKey.voice_missing_files to "Missing files: %s",
        StringsKey.home_discover to "Discover",
        StringsKey.home_sort to "Sort",
        StringsKey.home_sort_hot to "Hot",
        StringsKey.home_sort_newest to "Newest",
        StringsKey.home_sort_name to "Name",
        StringsKey.home_search_hint to "Search characters, authors, tags",
        StringsKey.home_create_your_role to "Create your character",
        StringsKey.home_create_hint to "Persona, avatar, voice saved to card",
        StringsKey.home_create to "Create",
        StringsKey.home_show_mature to "Show mature",
        StringsKey.home_role_detail to "Character details",
        StringsKey.home_not_found to "Character not found",
        StringsKey.home_by_author to "by %s",
        StringsKey.home_imported to "Imported",
        StringsKey.home_unlocked to "Unlocked",
        StringsKey.home_start_chat_btn to "Start chat",
        StringsKey.home_unlock_to_favorite to "Unlock to favorite",
        StringsKey.home_generating to "Generating",
        StringsKey.home_generate_image to "Generate image",
        StringsKey.home_edit_character to "Edit character card",
        StringsKey.home_mature_label to "Mature",
        StringsKey.chat_quick_continue to "Continue chatting",
        StringsKey.chat_quick_generate_img to "Generate",
        StringsKey.chat_delete_message_confirm to "Delete this message?",
        StringsKey.msg_action_quote to "Quote",
        StringsKey.msg_action_speak to "Play",
        StringsKey.msg_action_pause to "Pause",
        StringsKey.msg_action_delete to "Delete",
        StringsKey.msg_select_hint to "Select text then tap an action above",
        StringsKey.quote_label to "Quote",
        StringsKey.quote_from_user to "Quoting my message",
        StringsKey.quote_from_assistant to "Quoting assistant's message",
        StringsKey.quote_clear to "Cancel quote",
        StringsKey.quote_edit_title to "Edit quote snippet",
        StringsKey.quote_edit_hint to "Trim or remove unwanted text, confirm to quote",
        StringsKey.quote_locate to "Locate original message",
        StringsKey.quote_no_text to "This message has only images, no text to quote",
        StringsKey.scroll_to_bottom to "Scroll to latest",
        StringsKey.char_mgmt_persona_label to "Persona: %s",
        StringsKey.char_mgmt_style_label to "Style: %s",
        StringsKey.char_mgmt_image_label to "Image: avatar %s, gallery %d images",
        StringsKey.char_mgmt_voice_label to "Voice: %s",
        StringsKey.char_mgmt_avatar_missing to "Not configured",
        StringsKey.profile_tab_personality to "Personality",
        StringsKey.profile_change_avatar_hint to "Change avatar",
        StringsKey.profile_click_to_change to "Tap to change avatar",
        StringsKey.profile_name_placeholder to "Give yourself a name",
        StringsKey.profile_gender_placeholder to "女 / 其他 / Male / Female / Other",
        StringsKey.profile_age_label to "Age",
        StringsKey.profile_age_placeholder to "Your age",
        StringsKey.profile_bio_label to "Bio",
        StringsKey.profile_bio_placeholder to "Describe yourself in one line",
        StringsKey.profile_intro_label to "Introduction",
        StringsKey.profile_intro_placeholder to "Introduce yourself in detail so AI knows you better",
        StringsKey.profile_tags_placeholder to "Comma separated, e.g. reading, music, gaming",
        StringsKey.profile_important_label to "Important info",
        StringsKey.profile_important_placeholder to "Important things for AI to remember...",
        StringsKey.profile_help_text to "This info helps AI understand you...",
        StringsKey.skills_used_count to "Used %d times",
        StringsKey.skills_delete_confirm to "Delete \"%s\"?",
        StringsKey.skills_custom_empty_title to "No custom skills yet",
        StringsKey.skills_custom_create_hint to "Tap \"+\" at top right to create your custom skill.",
        StringsKey.detail_persona to "Persona summary",
        StringsKey.detail_voice to "Voice",
        StringsKey.detail_image_style to "Image style",
        StringsKey.discover_import_failed to "Import character failed",
        StringsKey.discover_image_added to "Image added to character gallery",
        StringsKey.discover_image_generated to "Image generated: %s",
        StringsKey.voice_policy_no_text to "No text recognized",
        StringsKey.voice_policy_generating to "Generating reply, please wait",
        StringsKey.voice_policy_not_ready to "Model not ready, voice content kept in input box",
        StringsKey.toast_record_permission_denied to "Microphone permission denied",
    
        StringsKey.settings_section_characters to "Characters",
        StringsKey.settings_section_appearance to "Appearance",
        StringsKey.settings_item_personalization to "Personalization",
        StringsKey.drawer_create_new_character to "Create new character",
)

    val translations: Map<AppLanguage, Map<StringsKey, String>> = mapOf(
        AppLanguage.ZH to ZH,
        AppLanguage.EN to EN
    )

    fun get(lang: AppLanguage, key: StringsKey): String {
        return translations[lang]?.get(key) ?: ZH[key] ?: key.name
    }

    fun get(lang: AppLanguage, key: StringsKey, vararg args: Any?): String {
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
    }
}