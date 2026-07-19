# 图像工作室 (Image Studio) 实现计划

## Context

当前生图功能直接嵌入聊天页面，存在以下问题：
1. 生图时没有使用角色人设（persona）和形象风格提示词（imageStylePrompt）
2. LLM 未加载时使用 fallback 提示词，生成结果与角色无关
3. 没有独立的生图界面，无法迭代修改、保存角色形象
4. 生图偶尔闪退（日志显示有时成功有时失败）

用户需要一个独立的图像工作室页面，类似对话页面但专为生成角色形象图片设计，支持文本/语音输入、引用修改、左右滑动画廊、长按保存/删除等功能。

## 整体架构

### 新增文件
```
ui/imagestudio/
├── ImageStudioScreen.kt          # 主屏幕 Composable
├── ImageStudioViewModel.kt       # ViewModel + UiState
└── components/
    ├── ImageStudioInputBar.kt    # 输入栏（文本+图片+语音+生成）
    ├── ImageMessageCard.kt       # 单条生图记录卡片
    └── RoleGalleryBar.kt         # 底部左右滑动角色形象画廊
```

### 修改文件
| 文件 | 改动 |
|------|------|
| `AppNavigation.kt` | 新增 `ImageStudioRoutes` 路由 |
| `MainActivity.kt` | 注册新路由 composable，ChatScreen 传入导航回调 |
| `ChatScreen.kt` | "生成图片"按钮改为导航到 ImageStudio |
| `ChatViewModel.kt` | 新增 `currentRoleCardId` 暴露给外部 |
| `RoleCardDao.kt` | 新增 `updateGalleryImageUris()` 方法 |
| `RoleCardRepository.kt` | 新增 `removeGalleryImage()` 方法 |
| `ImageGenerationConfig.kt` | `ImageGenerationRequest` 新增 `referencePrompt` 字段 |

## 详细设计

### 1. 导航路由

**`AppNavigation.kt`** 新增：
```kotlin
object ImageStudioRoutes {
    const val IMAGE_STUDIO = "image_studio/{roleId}"
    fun imageStudio(roleId: Long): String = "image_studio/$roleId"
}
```

**`MainActivity.kt`** 新增 composable：
```kotlin
composable(
    route = ImageStudioRoutes.IMAGE_STUDIO,
    arguments = listOf(navArgument("roleId") { type = NavType.LongType })
) { backStackEntry ->
    val roleId = backStackEntry.arguments?.getLong("roleId") ?: 0L
    ImageStudioScreen(
        roleId = roleId,
        onBack = { navController.popBackStack() }
    )
}
```

**`ChatScreen.kt`** 改动：
- 新增参数 `onNavigateToImageStudio: (Long) -> Unit`
- QuickActionsRow 的"生成此刻图片"和 ChatInputBar 的"生成图片"都调用此回调
- ChatScreen 调用处传入 `onNavigateToImageStudio = { roleId -> navController.navigate(ImageStudioRoutes.imageStudio(roleId)) }`

### 2. ImageStudioViewModel

**UiState**:
```kotlin
data class ImageStudioUiState(
    val roleCard: RoleCard? = null,
    val messages: List<ImageStudioMessage> = emptyList(),  // 生图历史
    val inputText: String = "",
    val selectedImages: List<Uri> = emptyList(),
    val referenceMessage: ImageStudioMessage? = null,      // 引用修改的参考
    val galleryImages: List<String> = emptyList(),         // 角色卡画廊
    val isGenerating: Boolean = false,
    val error: String? = null,
    val isVoiceListening: Boolean = false
)

data class ImageStudioMessage(
    val id: String,
    val prompt: String,           // 用户输入的提示词
    val fullPrompt: String,       // 实际发给引擎的完整提示词
    val imageUri: String? = null, // 生成结果（null=生成中）
    val timestamp: Long,
    val isError: Boolean = false,
    val errorMessage: String? = null
)
```

**核心方法**:
- `init(roleId)` — 加载角色卡，读取 galleryImageUris
- `generateImage()` — 构建提示词 → 调用引擎 → 添加到 messages
- `buildPrompt(userInput)` — 组合 `imageStylePrompt + persona + userInput + referencePrompt`
- `setReference(message)` — 设置引用修改参考
- `saveToGallery(imageUri)` — 调用 `roleCardRepository.appendGalleryImage()`
- `deleteGalleryImage(imageUri)` — 从角色卡 galleryImageUris 移除
- `toggleVoiceListening()` — 语音转文字填入输入框

**提示词构建逻辑**（替代现有 `buildImagePromptWithLLM`）:
```kotlin
fun buildPrompt(roleCard: RoleCard, userInput: String, reference: ImageStudioMessage?): String {
    val stylePrefix = roleCard.imageStylePrompt.ifBlank {
        "anime style, 2d illustration, vibrant colors, detailed, masterpiece"
    }
    val personaHint = roleCard.persona.take(150)
    val base = "$stylePrefix, $userInput"
    val withPersona = if (personaHint.isNotBlank()) "$base, character: $personaHint" else base
    return if (reference != null) {
        "$stylePrefix, based on (${reference.prompt}), modify: $userInput, character: $personaHint"
    } else {
        withPersona
    }
}
```

如果 LLM 就绪，可选使用 LLM 增强提示词（复用现有 `inferenceEngine` 逻辑），否则直接用上述组合。

### 3. ImageStudioScreen 布局

**视觉差异**（与聊天页面不同）:
- 深色背景（`surfaceContainer` 而非白色）
- 卡片式布局而非气泡
- 图片占主导，提示词文字在图片上方小字显示
- 无头像、无时间戳
- 顶部栏：角色名 + 返回按钮 + "图像工作室"标题

**布局结构**:
```
┌─────────────────────────────┐
│ TopBar: ← 角色名 图像工作室   │
├─────────────────────────────┤
│                             │
│  生图历史区（LazyColumn）     │
│  ┌───────────────────────┐  │
│  │ 提示词: xxx            │  │
│  │  ┌──────────────────┐ │  │
│  │  │   生成的图片      │ │  │
│  │  │   (长按出菜单)    │ │  │
│  │  └──────────────────┘ │  │
│  └───────────────────────┘  │
│                             │
├─────────────────────────────┤
│ 底部角色形象画廊（HorizontalPager）│
│ [上传] [img1] [img2] [img3] │
│ ← 左右滑动 →                 │
├─────────────────────────────┤
│ 输入栏: [📷] [文本框] [🎤] [生成] │
└─────────────────────────────┘
```

### 4. ImageStudioInputBar

参考 `ChatInputBar.kt` 但简化:
- 图片选择按钮（📷）
- 文本输入框
- 语音输入按钮（🎤，复用 `AndroidVoiceInputEngine`）
- 生成按钮（✨，主色调）
- 如有引用图片，显示引用预览条（可取消）

与聊天输入栏的差异:
- 无"建议回复"按钮
- 无"朗读"按钮
- 生成按钮始终显示（不像聊天页在文本/语音间切换）
- 风格更紧凑

### 5. ImageMessageCard

每条生图记录:
- 上方：提示词文字（小字，muted 色）
- 中间：生成的图片（圆角卡片，填满宽度）
- 生成中：显示进度指示器
- 错误：显示错误信息 + 重试按钮
- **长按图片** → 弹出菜单:
  - "引用修改" — 设置为 referenceMessage，滚动到输入栏
  - "保存为角色形象" — 调用 `saveToGallery()`
  - "删除" — 从 messages 移除

### 6. RoleGalleryBar

底部 HorizontalPager:
- 第 0 项：上传入口（`+` 图标，点击打开图片选择器）
- 第 1~N 项：角色卡 `galleryImageUris` 中的图片
- **长按画廊图片** → 弹出菜单:
  - "删除" — 调用 `deleteGalleryImage()`
  - "设为头像" — 可选功能
- 图片以圆角矩形展示，高度约 120dp
- 左右滑动浏览

数据来源: `roleCard.galleryImageUris`，删除后实时更新。

### 7. 数据持久化

- **生图历史**: 内存中保存（不持久化），退出页面后清除。后续可扩展为持久化会话。
- **角色形象画廊**: 通过 `RoleCardRepository` 持久化到 Room 数据库。
  - 保存: `appendGalleryImage(roleId, uri)` — 已有方法
  - 删除: 新增 `removeGalleryImage(roleId, uri)` — 需要实现

**`RoleCardDao.kt`** 新增:
```kotlin
@Query("UPDATE role_cards SET galleryImageUris = :uris, updatedAt = :now WHERE id = :id")
suspend fun updateGalleryImageUris(id: Long, uris: List<String>, now: Long = System.currentTimeMillis())
```

**`RoleCardRepository.kt`** 新增:
```kotlin
suspend fun removeGalleryImage(id: Long, imageUri: String): Boolean {
    val existing = roleCardDao.getById(id) ?: return false
    val nextGallery = existing.galleryImageUris.filter { it != imageUri }
    roleCardDao.update(existing.copy(galleryImageUris = nextGallery, updatedAt = nowProvider()))
    return true
}
```

### 8. 语音输入集成

复用 `AndroidVoiceInputEngine`:
- `ImageStudioViewModel` 持有 `voiceInputEngine` 引用
- `toggleVoiceListening()` 调用 `voiceInputEngine.startListening()` / `stopListening()`
- 识别结果填入 `inputText`（不自动发送，用户需手动点生成）
- 监听 `voiceInputEngine.events` Flow 处理结果

### 9. 闪退问题修复

日志显示生图有时成功有时闪退。可能原因:
- DreamLite 管线在第二次调用时内存未释放
- ONNX Runtime session 重复创建

修复方案:
- `LocalImageGenerationEngine` 中缓存 DreamLite 模型，避免重复加载
- 或在 `DreamLiteNative` 中添加 `releaseModel()` JNI 方法，每次生成后释放

在实现 ImageStudio 时，由于 ViewModel 持有引擎引用，可以更好地管理生命周期，减少重复加载导致的闪退。

## 实现步骤

1. **数据层改动** (RoleCardDao, RoleCardRepository)
2. **ViewModel** (ImageStudioViewModel)
3. **UI 组件** (InputBar, MessageCard, GalleryBar)
4. **主屏幕** (ImageStudioScreen)
5. **导航接入** (AppNavigation, MainActivity, ChatScreen)
6. **编译测试**

## 验证方式

1. 编译: `./gradlew assembleDebug`
2. 安装到手机: `adb install`
3. 测试流程:
   - 进入聊天页 → 点击"生成图片" → 跳转到图像工作室
   - 输入文字 → 点击生成 → 等待图片生成
   - 长按图片 → 选择"保存为角色形象" → 检查底部画廊出现新图
   - 长按画廊图片 → 选择"删除" → 检查图片消失
   - 长按图片 → 选择"引用修改" → 输入新文字 → 生成新图
   - 点击语音按钮 → 说话 → 检查文字填入输入框
   - 滑动画廊到最左 → 点击上传 → 选择图片 → 检查图片加入画廊
4. 检查日志: `adb shell "run-as com.companion.chat cat files/viewmodel_log.txt"`
5. 检查角色卡: 进入角色卡编辑 → 图片Tab → 确认 galleryImageUris 更新
