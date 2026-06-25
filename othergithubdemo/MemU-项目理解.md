# memU 完整记忆管道

> **代码**: `src/memu/` + `src/lib.rs` (Rust核心)

---

## 一、记忆系统全景

```
┌──────────────────────────────────────────────────────────────────┐
│  m e m U  记 忆 管 道                                              │
│                                                                  │
│  memorize(source) — 编译原始资源到工作空间                            │
│    Ingest → Preprocess → Extract → Organize → Persist             │
│    ↓                ↓               ↓                              │
│  Resource     MemoryItem     MemoryCategory                        │
│  (源文件)       (记忆文件)       (文件夹+摘要)                        │
│                                                                  │
│  retrieve(query) — 从工作空间检索                                   │
│    Route → Rank → Trace                                           │
│    ↓         ↓        ↓                                           │
│  文件夹    记忆文件    溯源到原始资源                                  │
│                                                                  │
│  工作空间输出:                                                      │
│  workspace/                                                        │
│  ├── INDEX.md    ← 全局地图                                          │
│  ├── MEMORY.md   ← 整合记忆                                          │
│  └── skill/{name}/SKILL.md  ← 技能                                 │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、memorize() 五步编译管线

### 2.1 Ingest（摄入）

```python
resource = Resource(
    url=source.url,
    modality=detect_modality(source),  # chat|document|image|video|audio|code|log
    local_path=store_file(source),
)
```

### 2.2 Preprocess（预处理）

```python
match resource.modality:
    case "chat":
        text = parse_chat(source)       # 对话解析
    case "document":
        text = parse_document(source)   # 文档解析
    case "image":
        text = caption_image(source)    # VLM 图片字幕
    case "video":
        text = caption_video(source)    # VLM 视频理解
    case "audio":
        text = transcribe_audio(source) # ASR 语音转写
    case "code":
        text = parse_code(source)       # 代码解析
    case "log":
        text = parse_log(source)        # 日志解析
```

### 2.3 Extract（提取 - LLM）

```python
# LLM 从预处理后的文本中提取类型化的记忆
memory_items = llm.extract(text)

# 6 种 MemoryItem 类型:
profile:    "用户偏好简洁的代码风格"
event:      "团队决定将发布推迟到6月3日"
knowledge:  "Python 3.13 新增了 JIT 编译"
behavior:   "用户总是先搜索再提问"
skill:      "用仓库范围搜索避免遗漏配置"
tool:       "search_files API 支持 glob 模式"
```

### 2.4 Organize（组织）

```python
for item in memory_items:
    # 分类到文件夹
    category = find_or_create_category(item)
    category.add_item(item)
    
    # 交叉链接
    link_related_items(item)
    
    # 更新文件夹摘要
    category.update_summary()

# 输出: MemoryCategory[]
```

### 2.5 Persist（持久化）

```python
database.save(resource)          # 保存原始资源
database.save(memory_items)      # 保存记忆文件
database.save(categories)        # 保存文件夹+摘要
vector_store.embed(items)        # 嵌入向量
```

---

## 三、retrieve() 三步检索管线

### 3.1 Route（路由）

```python
# 确定搜索范围
categories = find_categories(
    scope={
        "user": user_id,      # 用户级
        "agent": agent_id,    # Agent级
        "session": session_id, # 会话级
        "task": task_id,       # 任务级
    }
)
```

### 3.2 Rank（排序）

```python
# 多路搜索 + 融合
semantic = vector_search(query, categories)  # 语义
keyword = bm25_search(query, categories)      # 关键词
ranked = fusion_sort(semantic + keyword)      # 融合
```

### 3.3 Trace（溯源）

```python
for item in ranked:
    item.source = load_resource(item.source_id)
    # 每个记忆都可以追溯到原始文件
```

---

## 四、数据模型

```python
MemoryCategory (文件夹):
    id: UUID
    name: str                     # 分类名
    description: str              # 描述
    summary: str                  # LLM 更新的摘要
    embedding: [float]            # 分类向量
    items: List[MemoryItem]       # 子项

MemoryItem (文件):
    id: UUID
    memory_type: str              # profile|event|knowledge|behavior|skill|tool
    summary: str                  # 记忆摘要
    extra: Dict                   # 扩展数据
    happened_at: datetime         # 发生时间
    embedding: [float]            # 记忆向量
    source: Resource              # 原始资源

Resource (源文件):
    id: UUID
    url: str
    modality: str
    local_path: str
    caption: str
    embedding: [float]

CategoryItem (链接):
    category_id: UUID
    item_id: UUID
    # 将文件挂载到文件夹下
```

---

## 五、工作空间输出

```
workspace/
├── INDEX.md     ← 自动生成的全局索引
│                  包含: 所有类别、文件路径、摘要
│                  用途: Agent 了解"我知道什么"
│
├── MEMORY.md    ← 自动生成的整合记忆
│                  包含: profile + preferences + goals + events
│                  用途: Agent 了解"用户是谁"
│
└── skill/
    └── {name}/
        └── SKILL.md  ← 提取的技能
                        包含: 工具使用模式、成功路径
                        用途: Agent 知道"怎么做"
```
