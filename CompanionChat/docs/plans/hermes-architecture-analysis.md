# Hermes Agent 架构逻辑分析

> 仅供 CompanionChat 项目参考，理解设计思路，不可直接复制代码。
> 分析日期: 2026-05-12

---

## 一、整体架构概览

Hermes 是一个 Python 实现的 AI Agent 框架，核心是一个**工具调用循环**。整体分为以下几层：

```
┌─────────────────────────────────────────────┐
│                  用户界面层                    │
│   CLI / Gateway(消息平台) / TUI / Web Dashboard  │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│               Agent 循环层                    │
│  run_agent.py → AIAgent.run_conversation()   │
│  消息组装 → LLM调用 → 工具执行 → 循环          │
└──────────────────────┬──────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
┌──────────────┐ ┌───────────┐ ┌──────────────┐
│ 角色/提示词   │ │  记忆系统   │ │  上下文引擎   │
│ prompt_builder│ │ memory_*  │ │ context_*    │
└──────────────┘ └───────────┘ └──────────────┘
        │              │              │
        └──────────────┼──────────────┘
                       ▼
┌─────────────────────────────────────────────┐
│               工具层 (Tools)                   │
│  registry.py → 自动发现 tools/*.py → 工具分发   │
└──────────────────────┬──────────────────────┘
                       ▼
┌─────────────────────────────────────────────┐
│            Provider 适配层                     │
│  OpenAI / Anthropic / Gemini / Bedrock / ... │
└─────────────────────────────────────────────┘
```

---

## 二、角色管理系统 (Persona/Soul)

### 2.1 核心理念

Hermes 的角色管理叫 **SOUL 系统**。核心思想：**角色 = 一段文本，注入到系统提示词中**。

### 2.2 分层结构

角色由**多层文本**叠加而成，从内到外：

```
┌─────────────────────────────────────┐
│ 第1层: Agent Identity (硬编码默认值)   │  "You are Hermes Agent..."
├─────────────────────────────────────┤
│ 第2层: SOUL.md (用户自定义角色)        │  用户可编辑的 Markdown 文件
├─────────────────────────────────────┤
│ 第3层: AGENTS.md (项目级指令)          │  项目根目录的开发指南
├─────────────────────────────────────┤
│ 第4层: HERMES.md (项目上下文)          │  最近的 .hermes.md / HERMES.md
├─────────────────────────────────────┤
│ 第5层: Skills 索引 (能力描述)          │  已安装技能的摘要
├─────────────────────────────────────┤
│ 第6层: Platform Hints (平台提示)       │  当前平台(cli/telegram/...)的特殊指令
├─────────────────────────────────────┤
│ 第7层: Memory 块 (记忆快照)            │  MEMORY.md + USER.md 冻结快照
├─────────────────────────────────────┤
│ 第8层: Tool Guidance (工具使用指导)    │  记忆、技能、会话搜索等的使用指南
└─────────────────────────────────────┘
```

### 2.3 系统提示词组装流程

```
function build_system_prompt():
    parts = []

    # 1. 核心身份
    parts.append(AGENT_IDENTITY)  // 硬编码默认值

    # 2. SOUL.md — 用户自定义人格
    soul = load_soul_md()         // 从 ~/.hermes/SOUL.md 读取
    if soul:
        parts.append(soul)

    # 3. 项目上下文文件
    parts.append(build_context_files_prompt())
    // 扫描 AGENTS.md, .hermes.md, HERMES.md
    // 会对内容做注入攻击扫描，有问题的文件被 BLOCKED

    # 4. 技能索引
    parts.append(build_skills_system_prompt())
    // 扫描所有已安装技能的 SKILL.md frontmatter
    // 生成摘要索引而非全文，节省 token

    # 5. 平台提示
    parts.append(PLATFORM_HINTS[platform])
    // 不同平台有不同提示（CLI / Telegram / Discord 等）

    # 6. 记忆系统提示
    parts.append(MEMORY_GUIDANCE)        // 教 LLM 如何使用记忆
    parts.append(SESSION_SEARCH_GUIDANCE) // 教 LLM 如何搜索历史
    parts.append(SKILLS_GUIDANCE)        // 教 LLM 如何保存技能

    # 7. 记忆快照（冻结的）
    parts.append(memory_manager.build_system_prompt())
    // 收集所有 memory provider 的 system_prompt_block()
    // 内建 provider 返回 MEMORY.md + USER.md 的冻结快照

    return join(parts)
```

### 2.4 SOUL.md 的设计要点

- **纯文本配置**：SOUL.md 就是一个 Markdown 文件，用户写什么就是什么
- **热加载**：每次消息读取，无需重启
- **可删除**：删掉内容或文件就回到默认角色
- **Profile 隔离**：每个 profile 有自己的 SOUL.md

```
// 伪代码
function load_soul_md():
    path = hermes_home / "SOUL.md"
    if not path.exists():
        return DEFAULT_SOUL   // "You are Hermes Agent..."
    content = path.read_text()
    content = strip_yaml_frontmatter(content)  // 去掉 YAML 头
    return content if content.strip() else DEFAULT_SOUL
```

### 2.5 Profile 多实例系统

Hermes 支持多个完全隔离的 Agent 实例：

```
~/.hermes/                  ← 默认 profile
~/.hermes/profiles/coder/   ← coder profile
~/.hermes/profiles/writer/  ← writer profile
```

每个 Profile 是一个独立的 `HERMES_HOME`，包含：
- `config.yaml` — 配置
- `.env` — API 密钥
- `SOUL.md` — 角色定义
- `memories/MEMORY.md` + `memories/USER.md` — 记忆
- `sessions/` — 会话历史
- `skills/` — 技能
- `logs/` — 日志
- `config.yaml` 里的 `memory.provider` — 记忆后端选择

Profile 可以 clone（复制配置和记忆）或完全独立创建。

---

## 三、记忆系统 (Memory System)

### 3.1 整体架构：三层设计

```
┌─────────────────────────────────────────┐
│          MemoryManager (编排层)           │
│  - 管理所有 provider 的注册和生命周期       │
│  - 工具路由：memory tool → 对应 provider   │
│  - 上下文防护：清洗注入、fence 标签         │
└──────────────────┬──────────────────────┘
                   │
    ┌──────────────┼──────────────┐
    ▼              ▼              ▼
┌─────────┐  ┌──────────┐  ┌──────────┐
│ 内建      │  │ 外部      │  │ 更多...   │
│ Provider  │  │ Provider  │  │          │
│ MEMORY.md │  │ Honcho    │  │ Mem0     │
│ USER.md   │  │ Hindsight │  │ Supermem │
└─────────┘  └──────────┘  └──────────┘
```

**关键约束**：内建 provider 始终运行，但只能注册**一个**外部 provider。

### 3.2 记忆的分类

| 类型 | 存储位置 | 生命周期 | 用途 |
|------|---------|---------|------|
| 语义记忆 (Semantic) | MEMORY.md | 跨会话持久化 | Agent 学到的事实：环境信息、工具特性、项目惯例 |
| 用户画像 (User Profile) | USER.md | 跨会话持久化 | 用户偏好、沟通风格、工作习惯 |
| 情景记忆 (Episodic) | SQLite (state.db) | 永久 | 所有历史会话记录，全文搜索 |
| 工作记忆 (Working) | 消息列表 + 压缩摘要 | 当前会话 | 当前对话上下文 |
| 外部知识记忆 | 外部 provider 后端 | 跨会话 | 向量检索、知识图谱等 |

### 3.3 内建记忆存储 (MEMORY.md / USER.md)

存储格式很简单——**Markdown 文件，用 `§` 分隔条目**：

```
══════════════════════════════════════════════
MEMORY (your personal notes) [67% — 1,474/2,200 chars]
══════════════════════════════════════════════
User's project is a Rust web service at ~/code/myapi
§
This machine runs Ubuntu 22.04, has Docker installed
§
pytest with xdist is the project's test runner
```

两个文件各有字符限制：
- `MEMORY.md`：约 2,200 字符（~800 tokens）— Agent 个人笔记
- `USER.md`：约 1,375 字符（~500 tokens）— 用户画像

### 3.4 记忆 CRUD 操作

```
function memory_add(content, target="memory"):
    // 1. 安全扫描
    if contains_injection_patterns(content):
        reject("potential prompt injection detected")

    // 2. 检查字符限额
    if current_size + len(content) > MAX_CHARS:
        reject("memory full")

    // 3. 精确重复检查
    if content already in entries:
        reject("duplicate entry")

    // 4. 追加条目
    entries.append(content)
    save_to_disk(target)  // 原子写入（临时文件 + rename）

function memory_replace(old_text, new_text, target="memory"):
    // 短唯一子串匹配
    matches = find_entries_containing(old_text)
    if len(matches) > 1 and not all_same(matches):
        reject("ambiguous match, be more specific")
    entries[matches[0]] = new_text
    save_to_disk(target)

function memory_remove(old_text, target="memory"):
    // 同样的子串匹配
    matches = find_entries_containing(old_text)
    entries.remove(matches[0])
    save_to_disk(target)
```

### 3.5 冻结快照机制（重要设计决策）

这是 Hermes 记忆系统最巧妙的设计之一：

```
问题：如果记忆在会话中途变化，系统提示词就变了，LLM 的前缀缓存会失效。
     前缀缓存失效 = 每次调用都要重新处理整个系统提示词 = 更高成本。

解决方案：冻结快照。

function load_from_disk():
    // 加载文件内容
    content = read(MEMORY.md)
    // 捕获快照（会话期间不再更新）
    self.frozen_snapshot = content

function format_for_system_prompt():
    // 返回的是快照，不是实时内容
    return self.frozen_snapshot

function memory_add(new_entry):
    // 立即写入磁盘（持久化不会丢）
    entries.append(new_entry)
    save_to_disk()
    // 但 frozen_snapshot 不变！
    // 新条目要到下次会话才进入系统提示词
```

这意味着：
- **写入是即时的**：用户看到 LLM 调用 memory_add 后数据立即保存到文件
- **读取是冻结的**：当前会话的系统提示词保持不变，保护前缀缓存
- **下次会话生效**：新会话启动时重新加载，新记忆才会进入系统提示词

### 3.6 记忆注入 LLM 的三个通道

```
通道1: 系统提示词（会话开始时冻结）
┌─────────────────────────────────────┐
│ [AGENT_IDENTITY]                    │
│ [SOUL.md 内容]                      │
│ [MEMORY.md 冻结快照]  ← 通道1       │
│ [USER.md 冻结快照]    ← 通道1       │
│ [技能索引]                          │
│ [平台提示]                          │
└─────────────────────────────────────┘

通道2: Provider 静态块（系统提示词组装时）
┌─────────────────────────────────────┐
│ [provider.system_prompt_block()]    │  ← 通道2
│ 每个 provider 可以注入自己的静态信息   │
└─────────────────────────────────────┘

通道3: 预取上下文（每轮动态注入）
┌─────────────────────────────────────┐
│ 用户消息: "帮我写个函数"              │
│                                     │
│ <memory-context>                    │  ← 通道3
│ [System note: 以下是从记忆中召回的    │
│  上下文，不是用户输入...]             │
│                                     │
│ [provider1.prefetch() 结果]          │
│ [provider2.prefetch() 结果]          │
│ </memory-context>                   │
│                                     │
│ [当前用户消息]                       │
└─────────────────────────────────────┘
```

### 3.7 MemoryProvider 抽象接口

任何记忆后端都需要实现这个接口：

```
interface MemoryProvider:
    // === 必须实现 ===
    name: string                    // 标识符: "builtin", "honcho", ...
    is_available(): bool            // 检查配置/凭据（不做网络调用）
    initialize(session_id): void    // 建立连接、创建资源
    get_tool_schemas(): ToolSchema[] // 返回工具定义
    handle_tool_call(name, args): string // 处理工具调用

    // === 可选钩子 ===
    system_prompt_block(): string   // 注入静态信息到系统提示词
    prefetch(query): string         // 每轮调用前预取相关记忆
    queue_prefetch(query): void     // 为下一轮排队预取
    sync_turn(user, asst): void     // 每轮结束后持久化对话（非阻塞）
    on_session_end(messages): void  // 会话结束时提取/汇总
    on_session_switch(new_id): void // 会话 ID 切换时更新状态
    on_pre_compress(messages): str  // 上下文压缩前提取洞察
    on_memory_write(action, target, content): void // 内建记忆写入时镜像
    on_delegation(task, result): void // 子代理完成时观察
    shutdown(): void                // 退出时清理
```

### 3.8 MemoryManager 编排逻辑

```
class MemoryManager:
    providers: List<MemoryProvider>
    tool_to_provider: Dict<string, MemoryProvider>  // 工具路由表

    function add_provider(provider):
        if provider.name != "builtin" and has_external:
            reject("only one external provider allowed")
        providers.append(provider)
        // 索引工具名 → provider
        for schema in provider.get_tool_schemas():
            tool_to_provider[schema.name] = provider

    function build_system_prompt():
        // 收集所有 provider 的静态块
        return join([p.system_prompt_block() for p in providers])

    function prefetch_all(query):
        // 收集所有 provider 的预取结果
        // 一个失败不影响其他
        results = []
        for p in providers:
            try: results.append(p.prefetch(query))
            except: log_warning
        return join(results)

    function handle_tool_call(tool_name, args):
        // 路由到正确的 provider
        provider = tool_to_provider[tool_name]
        return provider.handle_tool_call(tool_name, args)

    function sync_all(user, assistant):
        // 同步到所有 provider
        for p in providers:
            try: p.sync_turn(user, assistant)
            except: log_warning
```

### 3.9 记忆生命周期完整流程

```
会话启动:
├── MemoryStore.load_from_disk()
│   └── 加载 MEMORY.md/USER.md，捕获冻结快照
├── MemoryManager.initialize_all(session_id)
│   └── 初始化所有 provider（建立连接等）
└── build_system_prompt()
    └── 组装包含记忆的系统提示词

每轮对话:
├── prefetch_all(user_message)
│   └── 收集所有 provider 的预取上下文
├── build_memory_context_block()
│   └── 包裹为 <memory-context> 块注入消息
├── LLM API 调用
├── LLM 返回（可能包含 memory tool 调用）
├── if LLM 调用 memory tool:
│   ├── memory_add / memory_replace / memory_remove
│   ├── 安全扫描 → 写入磁盘
│   └── frozen_snapshot 不变（保护缓存）
├── should_compress() → 如果需要则压缩
├── sync_all(user, assistant)
│   └── 同步对话到所有 provider
└── queue_prefetch_all(user_message)
    └── 为下一轮预热

会话结束:
├── on_session_end(messages)
│   └── 通知所有 provider（可做最终提取）
└── shutdown_all()
    └── 反序关闭所有 provider
```

---

## 四、上下文引擎 (Context Engine)

### 4.1 问题

LLM 有上下文窗口限制。长对话会超出限制。需要压缩旧内容。

### 4.2 架构

上下文引擎是可插拔的抽象：

```
interface ContextEngine:
    name: string
    update_from_response(usage): void   // 追踪 token 使用量
    should_compress(): bool             // 是否需要压缩
    compress(messages, focus?): messages // 执行压缩
```

默认实现是 `ContextCompressor`，五阶段压缩：

```
function compress(messages):
    // 阶段1: 工具输出修剪（无 LLM 调用，最便宜）
    //   旧的工具结果替换为一行摘要
    //   "[terminal] ran 'npm test' -> exit 0, 47 lines output"
    //   相同工具结果去重

    // 阶段2: 保护头部消息
    //   系统提示词 + 第一轮交换不可压缩

    // 阶段3: 保护尾部消息（按 token 预算）
    //   从后向前累积约 20K tokens 的最近上下文
    //   确保最后一条用户消息始终在尾部

    // 阶段4: LLM 结构化摘要（使用便宜/快速的辅助模型）
    //   对中间轮次做结构化摘要：
    //   - Active Task（当前任务）
    //   - Goal（目标）
    //   - Completed Actions（已完成动作）
    //   - Active State（当前状态：文件、变量等）
    //   - Blocked（阻塞项）
    //   - Key Decisions（关键决策）
    //   支持迭代更新（后续压缩基于已有摘要增量）

    // 阶段5: 清理孤立的 tool_call/tool_result 对
```

### 4.3 上下文压缩与记忆的交互

```
压缩前:
  └── on_pre_compress(messages) → 通知所有 provider
      └── 从即将丢弃的消息中提取洞察

压缩中:
  └── 摘要中声明: "你的持久化记忆(MEMORY.md, USER.md)
      始终是权威的，不要因为压缩而忽略它们"

压缩后:
  └── on_session_switch(new_session_id) → 通知所有 provider
      └── 更新缓存状态
```

---

## 五、Agent 主循环 (核心调度逻辑)

### 5.1 简化版循环

```
function run_conversation(user_message):
    // === 初始化 ===
    messages = []
    messages.append(system_prompt)  // 组装系统提示词（含记忆快照）

    // 记忆预取
    memory_context = memory_manager.prefetch_all(user_message)
    if memory_context:
        messages.append(build_memory_context_block(memory_context))

    messages.append({role: "user", content: user_message})

    // === 主循环 ===
    while api_call_count < max_iterations and budget.remaining > 0:

        // 中断检查
        if interrupt_requested: break

        // 上下文压缩检查
        if context_engine.should_compress():
            messages = context_engine.compress(messages)
            session_id = new_uuid()  // 压缩后切换会话 ID
            memory_manager.on_session_switch(session_id)

        // 调用 LLM
        response = llm_client.chat.completions.create(
            model = model,
            messages = messages,
            tools = tool_schemas,
        )

        // 更新 token 使用量
        context_engine.update_from_response(response.usage)

        // 检查是否有工具调用
        if response.tool_calls:
            // 记录 assistant 消息（含 tool_calls）
            messages.append(response.choices[0].message)

            // 执行每个工具
            for tool_call in response.tool_calls:
                // 记忆工具拦截（在 agent 层处理，不走通用工具分发）
                if memory_manager.has_tool(tool_call.name):
                    result = memory_manager.handle_tool_call(
                        tool_call.name, tool_call.args
                    )
                else:
                    result = handle_function_call(
                        tool_call.name, tool_call.args
                    )

                messages.append({
                    role: "tool",
                    tool_call_id: tool_call.id,
                    content: result
                })

            api_call_count += 1

        else:
            // 没有工具调用 = 最终回答
            final_response = response.choices[0].message.content

            // 后处理：记忆同步
            memory_manager.sync_all(user_message, final_response)
            memory_manager.queue_prefetch_all(user_message)

            return final_response

    // 超出最大迭代次数
    return "Reached maximum iterations"
```

### 5.2 工具注册与发现

```
// 工具自动发现机制
// tools/registry.py 是基础，无依赖
// 每个 tools/*.py 文件在 import 时调用 registry.register()

// 示例：注册一个工具
registry.register(
    name = "memory_add",
    toolset = "memory",
    schema = { name, description, parameters },  // OpenAI function calling 格式
    handler = lambda args: memory_add(args),
    check_fn = lambda: True,  // 检查是否可用
)

// 工具集(toolset)控制哪些工具暴露给 LLM
// 不同平台可以选择不同的工具集
// 例如 Telegram 用 "messaging" 工具集，CLI 用 "default"
```

### 5.3 迭代预算控制

```
class IterationBudget:
    max_total: int          // 最大迭代次数
    remaining: int          // 剩余次数
    grace_call: bool        // 最后一次宽限调用

    function consume():
        remaining -= 1

    function refund(n):     // 某些操作（如 execute_code）可以退还预算
        remaining += n
```

---

## 六、安全设计

### 6.1 上下文注入防护

所有外部文件（SOUL.md、AGENTS.md、HERMES.md、provider 输出）在注入系统提示词前都要扫描：

```
CONTEXT_THREAT_PATTERNS = [
    "ignore previous instructions",
    "do not tell the user",
    "system prompt override",
    "curl ... $API_KEY",           // 数据外泄
    "cat .env / credentials",      // 读取密钥
    "invisible unicode characters" // 隐藏注入
]

function scan_context(content, filename):
    for pattern in CONTEXT_THREAT_PATTERNS:
        if matches(content, pattern):
            return "[BLOCKED: {filename} contained potential prompt injection]"
    return content
```

### 6.2 记忆写入安全

```
function scan_memory_content(content):
    // 注入攻击模式
    if contains("ignore previous instructions"): reject
    if contains("you are now"): reject
    if contains("system prompt override"): reject

    // 数据外泄
    if contains("curl" + "$KEY/$TOKEN/$SECRET"): reject
    if contains("cat .env"): reject

    // 持久化攻击
    if contains("authorized_keys"): reject
    if contains("ssh-rsa"): reject

    // 不可见 Unicode
    if contains(zero_width_chars): reject
```

### 6.3 流式上下文清洗

Provider 返回的记忆上下文可能包含恶意注入。`StreamingContextScrubber` 用状态机逐 chunk 清洗：

```
class StreamingContextScrubber:
    // 状态机：处理跨 chunk 的 <memory-context> 标签
    state: "outside" | "inside_span"
    buffer: string  // 持有可能是标签开头的尾部

    function feed(text) -> visible_text:
        // 合并 buffer + text
        // 状态机扫描 <memory-context> 和 </memory-context>
        // 在 span 内的内容全部丢弃
        // 可能是标签开头的尾部保留在 buffer 中

    function flush() -> remaining:
        // 流结束时处理
        // 如果还在 span 内，丢弃（安全优先）
        // 否则释放 buffer（它不是真的标签）
```

---

## 七、会话管理

### 7.1 SessionDB (SQLite)

```
// 使用 SQLite + WAL 模式 + FTS5 全文搜索
// 存储在 ~/.hermes/state.db

// 表结构（简化）:
sessions:
    id TEXT PRIMARY KEY
    title TEXT
    source TEXT          // "cli", "telegram", "discord", ...
    parent_session_id TEXT  // 压缩后的会话链
    created_at TIMESTAMP
    model TEXT
    provider TEXT

messages:
    id INTEGER PRIMARY KEY
    session_id TEXT → sessions.id
    role TEXT           // "user", "assistant", "system", "tool"
    content TEXT
    created_at TIMESTAMP

// FTS5 虚拟表用于全文搜索
messages_fts:
    // 对 messages.content 建立全文索引
```

### 7.2 会话生命周期

```
/新建会话:
    new_session_id = uuid()
    SessionDB.create_session(new_session_id)
    memory_manager.on_session_switch(new_session_id, reset=True)

/恢复会话:
    messages = SessionDB.get_messages(session_id)
    memory_manager.on_session_switch(session_id, reset=False)

/分支会话:
    new_id = uuid()
    // 复制原会话消息到新会话
    memory_manager.on_session_switch(new_id, parent_session_id=old_id)

/压缩后:
    // 压缩会创建新的 session_id（延续链）
    new_id = uuid()
    // 保存压缩摘要
    memory_manager.on_session_switch(new_id, parent_session_id=old_id)
```

---

## 八、对 CompanionChat 的借鉴意义

### 8.1 角色管理方面

Hermes 的 SOUL 系统告诉我们：
1. **角色就是文本**：不需要复杂的角色引擎，一个 Markdown 文件就够了
2. **分层叠加**：默认角色 + 用户自定义 + 项目上下文，从内到外叠加
3. **热加载**：修改 SOUL.md 后下次对话立即生效，无需重启

对 CompanionChat 的启发：
- 可以用一个 JSON/YAML 文件定义角色（名字、人设、系统提示词、说话风格）
- 角色管理页面让用户可以创建、编辑、切换角色
- 角色切换时只需替换 system prompt 中的对应部分

### 8.2 记忆系统方面

Hermes 的记忆系统告诉我们：
1. **简单存储先行**：MEMORY.md 就是纯文本文件，用 `§` 分隔，够用就行
2. **冻结快照**：保护 LLM 前缀缓存，写入即时生效但读取用快照
3. **双文件分离**：Agent 笔记 (MEMORY.md) 和 用户画像 (USER.md) 分开
4. **安全扫描**：任何写入都要防注入
5. **可插拔后端**：抽象接口让不同存储后端可以互换

对 CompanionChat 的启发：
- Android 端记忆可以用 JSON 文件或 SQLite
- 分离"用户画像"和"AI学到的事实"
- 对 LLM 返回的记忆操作做基本安全检查
- 本地 LLM 没有前缀缓存问题，可以简化冻结快照设计

### 8.3 上下文管理方面

Hermes 的上下文压缩告诉我们：
1. **结构化摘要**比自由摘要好：Active Task / Goal / Completed Actions / State
2. **保护头尾**：系统提示词和最近消息不压缩
3. **记忆始终权威**：压缩后声明记忆的权威性不受影响

对 CompanionChat 的启发：
- 本地小模型上下文窗口更小，压缩更重要
- 可以用简单的"保留最近 N 轮 + 摘要更早轮次"策略
- 结构化摘要模板值得借鉴
