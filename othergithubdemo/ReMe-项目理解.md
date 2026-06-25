# ReMe 完整记忆管道

> **源码**: `reme/steps/evolve/` — `auto_memory.py`(222行), `auto_resource.py`(187行), `dream/extract.py`(171行), `dream/integrate.py`(92行), `dream/topics.py`(176行), `dream/finish.py`(70行)

---

## 一、记忆系统全景

```
┌──────────────────────────────────────────────────────────────────┐
│  R e M e  记 忆 管 道                                              │
│                                                                  │
│  触发方式:                                                        │
│  ├─ Agent after-reply hook → auto_memory                         │
│  ├─ 文件监控 watchfiles → auto_resource                          │
│  ├─ 定时任务 cron → auto_dream                                    │
│  │    ├─ dream_extract_step   (每天凌晨扫描)                       │
│  │    ├─ dream_integrate_step (逐条整合到digest)                   │
│  │    ├─ dream_topics_step    (生成interests.yaml)                 │
│  │    └─ dream_finish_step    (持久化catalog)                     │
│  └─ 按需调用 → proactive                                         │
│                                                                  │
│  数据流:                                                          │
│  session/ (原始对话JSONL) →                                       │
│  resource/ (原始资料) →                                           │
│  daily/ (日级Markdown卡片) →                                     │
│  digest/{personal,procedure,wiki}/ (长期记忆)                     │
│                                                                  │
│  索引系统:                                                        │
│  metadata/index/ → chunk + BM25 + embedding + wikilink           │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、auto_memory：对话→日级卡片（`auto_memory.py`）

### 2.1 触发器：Agent after-reply hook

```
调用方在 Agent 每次回复后调用:
  auto_memory(messages=本次对话, session_id=xxx)
```

### 2.2 执行流程（完整管道）

```python
async def execute(self):
    # === Step 1: 保存原始对话 ===
    # 路径: session/dialog/{session_id}.jsonl
    # 去重逻辑: 按 Msg.id 去重，按 created_at 排序
    
    # 去重算法:
    for msg in existing:
        by_id[msg.id] = msg
    for msg in messages:
        by_id[msg.id] = msg
    merged = sorted(by_id.values(), key=lambda m: m.created_at)
    
    # 增量追加检测:
    can_append = len(existing) <= len(merged) and all(
        merged[i].id == existing[i].id for i in range(len(existing))
    )
    
    # === Step 2: 清理敏感数据 ===
    # - base64图片被过滤
    # - 工具输出被截断(前后1024字符)
    
    # === Step 3: Agent 生成日级记忆卡片 ===
    # Agent 使用工具: read, edit, frontmatter_update, write
    result = await self.agent_wrapper.reply(
        self.prompt_format("auto_memory", {
            "messages": conversation,
            "session_id": session_id,
        }),
        system_prompt=self.prompt_format("auto_memory_system"),
        job_tools=self.agent_tools,
    )
    
    # === Step 4: 刷新日索引页 ===
    await refresh_day_index(self.file_store, day, daily_dir)
```

### 2.3 生成的记忆卡片格式

```markdown
---
name: "2024-01-01-对话摘要"
description: "与用户的日常对话"
tags: ["chat", "personal"]
created_at: "2024-01-01T10:00:00Z"
---

## 关键信息
- 用户提到了对Python编程的偏好

## 相关链接
- [[digest/personal/python_preference]]
- [[session/dialog/{session_id}.jsonl]]
```

---

## 三、auto_resource：资料→日级卡片（`auto_resource.py`）

### 3.1 触发器：文件监控 watchfiles

```
resource/{date}/{filename}.ext 文件变更时自动触发

变更类型: added / modified / deleted
```

### 3.2 执行流程

```python
async def execute(self):
    for each change in changes:
        file_path = self.to_workspace_relative(...)
        
        # 解析路径:
        # resource/2024-01-01/report.pdf → date=2024-01-01, filename=report.pdf
        date_str, filename = _parse_resource_path(file_path, resource_dir)
        note_stem = PurePosixPath(filename).stem  # "report"
        
        if change == deleted:
            # 删除对应的 daily/{date}/{note_stem}.md
        else:
            # 读取资源文件内容
            file_content = await aiofiles.open(abs_path, encoding="utf-8")
            
            # Agent 生成/更新 daily/{date}/{note_stem}.md
            result = await self.agent_wrapper.reply(
                user_message=self.prompt_format("user_message_create", {
                    "file_content": file_content,
                    "note_path": note_path,
                }),
                system_prompt=self.prompt_format("system_prompt"),
                job_tools=["read", "edit", "frontmatter_update", "write"],
            )
        
        # 刷新日索引
        await refresh_day_index(self.file_store, date_str, daily_dir)
```

---

## 四、auto_dream：日级→长期记忆（4 步管道）

### 4.1 整体流程

```
dream_extract_step      每天凌晨扫描 changed files → 提取记忆单元和主题
       ↓
dream_integrate_step    逐条整合到 digest/{bucket}/
       ↓
dream_topics_step       生成 daily/{date}/interests.yaml
       ↓
dream_finish_step       持久化 catalog，渲染结果
```

### 4.2 dream_extract_step：提取（`dream/extract.py`）

```python
class DreamExtractStep(BaseStep):
    def __init__(self, topic_session_id="interests", scan_days=2):
        # scan_days=2: 默认扫描过去2天的 daily 目录

    async def execute(self):
        # === Step 1: 确定扫描范围 ===
        day = today(self, date)  # 目标日期
        dates = recent_dates(day, scan_days=2)  # 过去2天
        
        # === Step 2: 检测文件变更 ===
        # 对比 metadata/catalog 中的 mtime
        nodes = await self.file_catalog.get_nodes()
        indexed = {n.path: n.st_mtime for n in nodes if ...}
        changed = [path for path in existing if indexed.get(path) != existing[path]]
        unchanged = [path for path in existing if indexed.get(path) == existing[path]]
        
        if not changed:
            return "No changed dream input"
        
        # === Step 3: LLM 提取 ===
        # Agent可调用工具: read（只能读取）
        result = await self.agent_wrapper.reply(
            self.prompt_format("extract_user_message", {
                "date": day,
                "dates_json": json.dumps(dates),
                "changed_paths_json": json.dumps(changed),
                "material_blob": pack_paths(workspace, changed),  # 读取文件内容
                    # 每文件最多60000字符
            }),
            system_prompt=self.prompt_format("extract_system_prompt", {
                "buckets": "procedure, personal, wiki",
            }),
            job_tools=["read"],
        )
        
        # === Step 4: 解析结构化的提取结果 ===
        meta = parse_structured_reply(result)
        # 支持 ```json / ```yaml / 纯文本解析
        state.units = meta.get("units", [])    # 记忆单元
        state.topics = meta.get("topics", [])  # 兴趣主题
        
        return f"Extracted {len(state.units)} unit(s), {len(state.topics)} topic(s)"
```

### 4.3 dream_integrate_step：整合（`dream/integrate.py`）

```python
class DreamIntegrateStep(BaseStep):
    async def execute(self):
        # 确保 digest 目录存在
        for bucket in DreamBucketEnum:  # procedure, personal, wiki
            (workspace / digest_dir / bucket.value).mkdir(parents=True, exist_ok=True)
        
        # 逐条整合每个记忆单元
        for i, unit in enumerate(state.units):
            await self._integrate_one(state, unit, i, workspace, digest_dir)
    
    async def _integrate_one(self, state, unit, index, workspace, digest_dir):
        # 确定 bucket（默认 wiki）
        try:
            bucket = DreamBucketEnum(unit.get("bucket")).value
        except ValueError:
            bucket = "wiki"
        
        # Agent 可调用工具: node_search, read, frontmatter_read, write, edit, frontmatter_update
        result = await self.agent_wrapper.reply(
            self.prompt_format("integrate_user_message", {
                "unit_name": unit.get("name"),
                "unit_bucket": bucket,
                "unit_summary": unit.get("summary"),
                "material_blob": pack_paths(workspace, unit.get("paths", [])),
            }),
            system_prompt=self.prompt_format(f"integrate_system_prompt_{bucket}", {
                "digest_dir": digest_dir,
            }),
            job_tools=list(_TOOLS),  # 6个工具
        )
        
        # 解析整合结果
        outcome = IntegrateOutcome.model_validate(parse_structured_reply(result))
        # 包含: action(create/update/skip), target_path
```

### 4.4 dream_topics_step：生成 interests.yaml

```python
class DreamTopicsStep(BaseStep):
    def __init__(self, topic_count=3, topic_diversity_days=7):
    
    async def execute(self):
        # 去重: 对比 same_day + recent(7天内) 的已有主题
        same_day = load_yaml_topics(abs_path)  # 当天已有主题
        recent = [topic for previous_day in previous_dates(target_day, 7)
                  for topic in load_yaml_topics(...)]  # 7天内
        
        # LLM 选择最优主题 (top-3)
        if llm_available and candidates:
            result = await self.agent_wrapper.reply(
                self.prompt_format("topics_user_message", {
                    "candidates_json": ...,
                    "same_day_json": ...,
                    "recent_topics_json": ...,
                }),
                system_prompt=self.prompt_format("topics_system_prompt"),
            )
        
        # 最终去重写入
        topics = _dedupe(selected, same_day, recent, count=3)
        write_yaml(abs_path, {"date": day, "topics": topics})
```

**interests.yaml 格式**:
```yaml
date: 2024-01-01
topics:
  - title: "Python编码偏好"
    reason: "用户多次提到对Python的偏好"
    evidence: "在对话中主动选择Python"
    keywords: ["python", "编程", "代码风格"]
    paths: ["daily/2024-01-01/session_xxx.md"]
```

### 4.5 dream_finish_step：持久化

```python
class DreamFinishStep(BaseStep):
    async def execute(self):
        checkpoint = [p for p in state.changed_paths if p not in set(state.failed_paths)]
        upserts = self._nodes(workspace, checkpoint + interest_paths + day_index_paths)
        if upserts:
            await self.file_catalog.upsert(upserts)
        if self.persist:
            await self.file_catalog.dump()
```

---

## 五、digest 长期记忆分类

```
digest/
├── personal/       # 个人事实: 画像、偏好、生活方式
├── procedure/      # 流程经验: "如何做某事" 的步骤
└── wiki/           # 知识节点: 事实性知识、概念
```

---

## 六、搜索系统

```
search(query):
  1. wikilink 遍历: 沿 [[链接]] 关系图展开（traverse step）
  2. BM25 关键词: 精确匹配索引
  3. Embedding 语义: 向量相似度
  
  融合: RRF (Reciprocal Rank Fusion)
  
  工具:
  - node_search: 按抽象名称搜索digest节点
  - search: 全工作空间混合检索
  - traverse: wikilink图谱遍历
```

---

## 七、索引更新循环

```python
index_update_loop:
  后台守护进程:
  1. 启动时全量扫描 daily/, digest/, resource/ 下的 .md 和 .jsonl
  2. 持续监听文件变更
  3. 更新: chunk分块 → BM25索引 → embedding向量 → wikilink图谱
```
