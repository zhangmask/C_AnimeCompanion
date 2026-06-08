# Stage0 Stage1 Data Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 补齐阶段0遗漏的数据层骨架，并完成阶段1会话存储迁移、即时写入、级联删除与真机验证。

**Architecture:** 先补齐 Room 数据库结构，包括阶段0清单要求的实体、DAO、数据库回调和初始化 SQL；再将旧 JSON 迁移逻辑拆分为独立 `DataMigration` 组件，并把 `ChatViewModel` 的会话读写统一收口到 Room 仓储。验证顺序遵循用户要求：编译、卸载旧 app、安装新 app、推送模型、执行真机测试。

**Tech Stack:** Kotlin, Android, Jetpack Compose, Room, KSP, SQLite, Gradle, ADB

---

### Task 1: 盘点数据库现状并补实体字段

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/local/entity/ConversationEntity.kt`
- Modify: `app/src/main/java/com/companion/chat/data/model/ChatMessage.kt`
- Check: `COMPANIONCHAT_TEST_CHECKLIST.md`

**Step 1: 对照清单核对字段缺口**

- 确认 `ConversationEntity` 是否缺 `updatedAt`
- 确认领域模型是否需要同步保留该时间戳

**Step 2: 最小修改补字段**

- 为 `ConversationEntity` 增加 `updatedAt`
- 为 `ConversationSession` 增加对应字段或给出兼容默认值

**Step 3: 调整映射代码**

- 更新 Entity 和领域模型双向映射
- 保证旧逻辑在未显式传值时仍能工作

**Step 4: 编译检查**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

### Task 2: 补齐阶段0缺失的实体骨架

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/local/entity/Memory.kt`
- Create: `app/src/main/java/com/companion/chat/data/local/entity/UserPreference.kt`
- Create: `app/src/main/java/com/companion/chat/data/local/entity/Skill.kt`
- Modify: `app/src/main/java/com/companion/chat/data/local/CompanionDatabase.kt`

**Step 1: 按清单创建 3 个实体**

- `Memory`
- `UserPreference`
- `Skill`

**Step 2: 注册到 `CompanionDatabase`**

- 将 3 个实体加入 `@Database(entities = [...])`

**Step 3: 保持只做结构**

- 不提前接 UI
- 不提前写业务层逻辑

**Step 4: 编译检查**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过，Room 生成代码正常

### Task 3: 补齐阶段0缺失 DAO

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/local/dao/ConversationDao.kt`
- Create: `app/src/main/java/com/companion/chat/data/local/dao/MessageDao.kt`
- Create: `app/src/main/java/com/companion/chat/data/local/dao/MemoryDao.kt`
- Create: `app/src/main/java/com/companion/chat/data/local/dao/PreferenceDao.kt`
- Create: `app/src/main/java/com/companion/chat/data/local/dao/SkillDao.kt`
- Modify: `app/src/main/java/com/companion/chat/data/local/CompanionDatabase.kt`

**Step 1: 拆分会话和消息 DAO**

- `ConversationDao` 负责会话查询和删除
- `MessageDao` 负责按会话查询消息和写入消息

**Step 2: 创建阶段0清单要求的 DAO 接口**

- `MemoryDao`
- `PreferenceDao`
- `SkillDao`

**Step 3: 在数据库中暴露 DAO**

- 为每个 DAO 增加抽象方法

**Step 4: 编译检查**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

### Task 4: 增加 FTS5 与数据库初始化回调

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/local/CompanionDatabase.kt`

**Step 1: 定义内置技能初始数据**

- 4 条内置技能内容与设计文档保持一致

**Step 2: 增加 `RoomDatabase.Callback.onCreate`**

- 创建 `memories_fts`
- 创建 3 个同步触发器
- 预填充技能数据

**Step 3: 确保 SQL 幂等和首次安装可用**

- 仅在数据库首次创建时执行

**Step 4: 编译检查**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

### Task 5: 拆出独立迁移组件

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/migration/DataMigration.kt`
- Modify: `app/src/main/java/com/companion/chat/data/repository/ChatSessionRepository.kt`

**Step 1: 抽出迁移职责**

- 检测 `conversations.json`
- 检测 `.bak`
- 解析旧 JSON
- 导入 Room
- 成功后备份
- 失败时保留原文件并记录日志

**Step 2: 仓储收口为读写层**

- `ChatSessionRepository` 只保留会话读写和初始化编排

**Step 3: 保持行为兼容**

- 应用启动仍能执行初始化

**Step 4: 编译检查**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

### Task 6: 完成阶段1 ViewModel 数据闭环

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/companion/chat/data/repository/ChatSessionRepository.kt`

**Step 1: 发送消息即时落库**

- 生成完成后立即持久化当前会话

**Step 2: 新建、切换、改标题保持 Room 一致**

- 复用仓储方法
- 避免只更新内存态不更新数据库

**Step 3: 补删除会话能力**

- 删除会话时删除 `conversations`
- 依赖外键级联或显式删除同步删除 `messages`
- 删除后自动切换到合理会话或空状态

**Step 4: 编译检查**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

### Task 7: 做针对性验证

**Files:**
- Check: `app/build.gradle.kts`
- Check: `app/src/main/AndroidManifest.xml`
- Check: `app/src/main/java/com/companion/chat/CompanionChatApplication.kt`

**Step 1: 运行编译**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 成功产出 debug APK

**Step 2: 检查最近改动诊断**

Run: IDE diagnostics on modified files
Expected: 无新增错误

**Step 3: 真机部署**

Run:
- 卸载旧包
- 安装新包
- 推送模型

Expected: 新包可启动，模型文件到位

**Step 4: 真机验收**

- 注入旧 `conversations.json`
- 验证迁移成功和 `.bak`
- 验证新建会话、发送消息、切换会话、删除会话
- 杀进程重启检查持久化

**Step 5: 记录结果**

- 标记阶段0/阶段1本轮已完成和未完成项
