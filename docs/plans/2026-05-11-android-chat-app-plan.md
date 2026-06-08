# 私人陪伴对话 App - 实施计划

> **日期:** 2026-05-11
> **设计文档:** [2026-05-11-android-chat-app-design.md](./2026-05-11-android-chat-app-design.md)

## 环境信息

| 项目 | 版本 |
|---|---|
| Android SDK | D:\AndroidstudioSDK |
| Build Tools | 36.1.0 |
| Platform | android-36 |
| Java | 25 |
| Gradle | Wrapper（自动下载） |
| AGP | 8.9.3 |
| Kotlin | 2.1.20 |
| Compose BOM | 2025.05.01 |

## 任务拆分

### Task 1: 创建 Android 项目骨架

**创建文件:**
- `CompanionChat/build.gradle.kts`（根）
- `CompanionChat/settings.gradle.kts`
- `CompanionChat/gradle.properties`
- `CompanionChat/gradle/wrapper/gradle-wrapper.properties`
- `CompanionChat/gradlew` / `CompanionChat/gradlew.bat`
- `CompanionChat/app/build.gradle.kts`
- `CompanionChat/app/src/main/AndroidManifest.xml`
- `CompanionChat/app/proguard-rules.pro`

**操作:** 手动创建标准 Android 项目结构，使用 Gradle Wrapper

---

### Task 2: 创建主题和基础 UI 组件

**创建文件:**
- `app/src/main/java/com/companion/chat/ui/theme/Color.kt`
- `app/src/main/java/com/companion/chat/ui/theme/Type.kt`
- `app/src/main/java/com/companion/chat/ui/theme/Theme.kt`

**内容:** Material 3 主题定义，暗色/亮色方案

---

### Task 3: 创建数据模型和抽象接口

**创建文件:**
- `app/src/main/java/com/companion/chat/data/model/ChatMessage.kt`
- `app/src/main/java/com/companion/chat/data/engine/InferenceEngine.kt`
- `app/src/main/java/com/companion/chat/data/engine/VoiceInputEngine.kt`
- `app/src/main/java/com/companion/chat/data/engine/VoiceOutputEngine.kt`

---

### Task 4: 搭建导航和 MainActivity

**创建文件:**
- `app/src/main/java/com/companion/chat/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/companion/chat/MainActivity.kt`

**内容:** 底部导航栏 + NavHost，4 个 Tab 切换

---

### Task 5: 实现 4 个页面

**创建文件:**
- `app/src/main/java/com/companion/chat/ui/home/HomeScreen.kt`
- `app/src/main/java/com/companion/chat/ui/chat/ChatScreen.kt`
- `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- `app/src/main/java/com/companion/chat/ui/chat/components/MessageBubble.kt`
- `app/src/main/java/com/companion/chat/ui/chat/components/ChatInputBar.kt`
- `app/src/main/java/com/companion/chat/ui/chat/components/TypingIndicator.kt`
- `app/src/main/java/com/companion/chat/ui/memory/MemoryScreen.kt`
- `app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt`

---

### Task 6: 编译验证

**操作:** `gradlew.bat assembleDebug`
