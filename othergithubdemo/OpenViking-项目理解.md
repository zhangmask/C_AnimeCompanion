# OpenViking 完整记忆管道

> **源码**: `session/session.py` (3442行), `session/memory/extract_loop.py` (852行), `session/memory/merge_op/*.py`

---

## 一、记忆系统全景

```
┌────────────────────────────────────────────────────────────────────────┐
│  O p e n V i k i n g  记 忆 管 道                                       │
│                                                                        │
│  【触发】Session.commit_async()                                           │
│    └─ 条件: messages存在 且 超出keep_recent_count窗口                      │
│                                                                        │
│  【Phase 1: 归档】（分布式锁保护）                                         │
│    messages → archive_NNN/messages.jsonl                                 │
│    retain部分 → 写回 live messages.jsonl                                  │
│                                                                        │
│  【Phase 2: 三路并发记忆提取】（asyncio.gather）                            │
│    ├─ 归档摘要 → Working Memory (7段Markdown)                            │
│    ├─ 长程记忆 → SessionExtractContextProvider                           │
│    │   └─ types: profile, preferences, entities, events 等               │
│    └─ 执行记忆 → AgentTrajectory + AgentExperience                       │
│        └─ types: trajectories, experiences                               │
│                                                                        │
│  【Commit标记】                                                          │
│    全部成功 → .done                                                      │
│    任一失败 → .failed.json + 跳过该归档                                    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 二、Session 初始化完整数据流

### 2.1 Session 构造与加载

```python
class Session:
    def __init__(self, viking_fs, user, session_id, 
                 auto_commit_threshold=8000):
        self._messages: List[Message] = []
        self._session_uri = canonical_session_uri(ctx, session_id)
        # URI格式: viking://sessions/{account}/{user}/{session_id}/
        
    async def load(self):
        # 从 messages.jsonl 恢复消息
        content = await self._viking_fs.read_file(
            f"{self._session_uri}/messages.jsonl")
        self._messages = [Message.from_dict(json.loads(line)) for line in ...]
        
        # 从 history/ 目录恢复压缩索引
        history_items = await self._viking_fs.ls(f"{self._session_uri}/history")
        self._compression.compression_index = max(archive_N 的 N)
        
        # 从 .meta.json 恢复元数据
        self._meta = SessionMeta.from_dict(json.loads(meta_content))
```

### 2.2 会话目录结构

```
viking://sessions/{account}/{user}/{session_id}/
├── messages.jsonl      # 当前 live 消息
├── .meta.json           # 会话元数据
├── .abstract.md         # L0: 一句话摘要
├── .overview.md         # L1: 会话结构概览
├── history/             # 历史归档
│   └── archive_NNN/
│       ├── messages.jsonl  # 归档消息
│       ├── .abstract.md    # L0
│       ├── .overview.md    # L1 = Working Memory
│       ├── .meta.json      # Token统计
│       ├── .done           # 完成标记
│       └── .failed.json    # 失败标记
└── tools/
    └── {tool_id}/tool.json  # 工具调用结果
```

---

## 三、Commit 完整流程

### 3.1 commit_async 边界条件

```python
async def commit_async(self, keep_recent_count=0):
    # 边界1: 无消息 → 快速返回
    if not self._messages:
        return {"status": "accepted", "archived": False}
    
    # 边界2: 全部在keep窗口内 → 不归档
    keep_recent_count = max(0, keep_recent_count)
    if keep_recent_count > 0 and total <= keep_recent_count:
        self._meta.pending_tokens = 0
        return {"status": "accepted", "archived": False, 
                "reason": "all_within_keep_window"}
    
    # 边界3: 正常归档
    self._compression.compression_index += 1
    archive_uri = f"{self._session_uri}/history/archive_{index:03d}"
    
    # WM v2: 将 pending_tokens 重置为 0
    # 因为所有待归档消息都被清除了
    self._meta.pending_tokens = 0
    self._meta.keep_recent_count = keep_recent_count
```

### 3.2 Phase 2 三路并发提取

```python
async def _run_memory_extraction(self, ...):
    # 串行化: 等待前一个归档完成
    await self._wait_for_previous_archive_done(archive_index)
    
    # Redo log 保护
    if redo_enabled:
        await redo_log.write_pending_async(redo_task_id, {...})
    
    # 恢复外部化工具输出
    extraction_messages = await self._hydrate_tool_outputs_for_extraction(messages)
    
    # 三路并发提取
    extraction_tasks = []
    
    # 1. 归档摘要(always)
    extraction_tasks.append(_run_archive_summary)
    
    # 2. 长程记忆(用户记忆)
    if long_term_has_work:
        extraction_tasks.append(_run_long_term_memory_extraction)
    
    # 3. 执行记忆(Agent记忆)
    if execution_memory_has_work:
        extraction_tasks.append(_run_execution_memory_extraction)
    
    results = await asyncio.gather(*extraction_tasks, return_exceptions=True)
    
    # 任一失败 → .failed.json
    for label, result in zip(labels, results):
        if isinstance(result, Exception):
            raise  # → _write_failed_marker
    
    # 全部成功 → .done
    await self._write_done_file(archive_uri, first_message_id, last_message_id)
```

---

## 四、Working Memory 生成完整流程

### 4.1 _generate_archive_summary_async 双分支

```python
async def _generate_archive_summary_async(self, messages, latest_archive_overview):
    # 格式化消息
    formatted = "\n".join(self._format_message_for_wm(m) for m in messages)
    # 每行: [user]: 内容 / [tool:name (status)] 输出 / [context] 摘要
    
    # 检测是否已有 WM v2 格式
    _is_wm_v2 = latest_archive_overview and any(
        f"## {s}" in latest_archive_overview for s in WM_SEVEN_SECTIONS
    )
    
    if not latest_archive_overview or not _is_wm_v2:
        # 分支A: 全量创建
        prompt = render_prompt("compression.ov_wm_v2", {
            "messages": formatted,
            "latest_archive_overview": prior_overview or "",
        })
        return await vlm.get_completion_async(prompt)
    
    else:
        # 分支B: 增量更新
        # 计算各 section 大小警告
        reminders = Session._build_wm_section_reminders(latest_archive_overview)
        
        update_prompt = render_prompt("compression.ov_wm_v2_update", {
            "messages": formatted,
            "latest_archive_overview": latest_archive_overview,
            "wm_section_reminders": reminders,  # <-- 注入尺寸超限警告
        })
        
        resp = await vlm.get_completion_async(
            prompt=update_prompt,
            tools=[WM_UPDATE_TOOL],
            tool_choice={
                "type": "function",
                "function": {"name": "update_working_memory"},
            },  # <-- 强制调用 tool
        )
```

### 4.2 update_working_memory Tool Schema

```python
WM_UPDATE_TOOL = {
    "type": "function",
    "function": {
        "name": "update_working_memory",
        "parameters": {
            "required": ["sections"],
            "properties": {
                "sections": {
                    "type": "object",
                    "required": [  # 全部7段必须出现
                        "Session Title", "Current State", "Task & Goals",
                        "Key Facts & Decisions", "Files & Context",
                        "Errors & Corrections", "Open Issues"
                    ],
                    "properties": {
                        section_name: {  # 每段都是一个 oneOf
                            "oneOf": [
                                {"op": "KEEP"},
                                {"op": "UPDATE", "content": "..."},
                                {"op": "APPEND", "items": [...]},
                            ]
                        }
                        for section_name in WM_SEVEN_SECTIONS
                    }
                }
            }
        }
    }
}
```

### 4.3 _merge_wm_sections 逐段合并

```python
def _merge_wm_sections(old_wm, ops):
    old_sections = Session._parse_wm_sections(old_wm)
    parts = ["# Working Memory", ""]
    
    for header in WM_SEVEN_SECTIONS:
        full_header = f"## {header}"
        op = ops.get(header)
        old_content = old_sections.get(full_header, "")
        
        # ====== 5 个 Guard（仅在 old_content 存在时触发）======
        if old_content:
            if header == "Session Title":
                # Guard 1: 有意义词重叠 >= 1
                op = _wm_enforce_title_stability(op, old_content)
            elif header == "Key Facts & Decisions":
                # Guard 2: 15% 条目数 + 70% 锚点覆盖
                op = _wm_enforce_key_facts_consolidation(op, old_content)
            elif header in _WM_APPEND_ONLY_SECTIONS:
                # Guard 3: UPDATE → APPEND 降级
                op = _wm_enforce_append_only(header, op, old_content)
            elif header == "Files & Context":
                # Guard 4: 路径不丢失
                op = _wm_enforce_files_no_regression(op, old_content)
            elif header == "Open Issues":
                # Guard 5: 恢复被删除条目
                op = _wm_enforce_open_issues_resolved(op, old_content)
        
        # ====== 执行操作 ======
        if op is None or op.op == "KEEP":
            new_content = old_content
        elif op.op == "UPDATE":
            new_content = op.content
        elif op.op == "APPEND":
            appended = "\n".join(f"- {item}" for item in op.items)
            new_content = f"{old_content}\n{appended}" if old_content else appended
        
        parts.append(f"{full_header}\n{new_content}\n")
```

---

## 五、长程记忆提取完整流程

### 5.1 SessionExtractContextProvider.prefetch

```python
async def prefetch(self):
    # 1. 构建搜索查询（从会话最后N条消息）
    query = self._build_prefetch_search_query()
    # 从最后几条消息中提取关键词
    
    # 2. 搜索相关已有记忆（针对每个memory type）
    for schema in self.get_memory_schemas(ctx):
        search_uris = self.list_search_uris(user_space)
        candidate_uris = await self.search_files(
            query=query, search_uris=search_uris, limit=10)
        
        for uri in candidate_uris:
            result = await self.read_file(uri)
            # 缓存读取结果到 _read_file_contents
    
    # 3. 构建prefetch消息
    prefetch_messages = [self._build_conversation_message()]
    for uri, content in self._read_file_contents.items():
        add_tool_call_pair_to_messages(
            messages=prefetch_messages,
            tool_name="read", params={"uri": uri},
            result=content,
        )
    
    prefetch_messages.append({
        "role": "user",
        "content": "You have already read the conversation and existing memories. "
                   "Based on the schema definitions, decide what to do."
    })
```

### 5.2 ExtractLoop 执行

```python
class ExtractLoop:
    async def run(self):
        provider = self._provider
        await provider.prepare_extraction_messages()
        messages = await provider.prefetch()
        
        while True:
            response = await self._call_llm(messages, tools)
            
            if response.has_tool_calls:
                for tc in response.tool_calls:
                    result = await provider.execute_tool(tc)
                    add_tool_call_pair_to_messages(messages, tc, result)
            else:
                # 无 tool_call → 解析最终操作
                break
        
        # 解析操作
        operations = self.resolve_operations(messages)
        
        # 验证
        self._check_unread_existing_files(operations)
        self._validate_patch_operations(operations)
        
        # 写入
        await self.finalize_operations(operations)
```

---

## 六、Experience 提取（两阶段）

### 6.1 Phase 1: Trajectory 提取

```python
class AgentTrajectoryContextProvider(SessionExtractContextProvider):
    def get_memory_schemas(self, ctx):
        return [registry.get("trajectories")]  # add_only 模式
    
    def instruction(self):
        return "Extract execution trajectories from the conversation"
```

### 6.2 Phase 2: Experience 提取（完整决策树）

```python
class AgentExperienceContextProvider(SessionExtractContextProvider):
    async def prefetch(self):
        # 1. 用轨迹摘要搜索已有经验
        candidate_uris = await self.search_files(
            query=self.trajectory_summary[:500],
            search_uris=[experience_dir],
            limit=5,
        )
        
        # 2. 加载 source_trajectories（top-3的体验）
        for exp_uri in candidate_uris[:3]:
            mf = self._read_file_contents.get(exp_uri)
            for link in mf.links:
                if link.link_type == "derived_from":
                    source_trajs.append(await viking_fs.read_file(link.to_uri))
        
        # 3. 构建消息
        messages = [
            new_trajectory,           # 新轨迹
            candidate_experiences,    # top-5 候选
            source_trajectories,      # top-3 的源轨迹
        ]
        
        # 4. LLM 决策
        # 同 experience_name → UPDATE
        # 新 experience_name → CREATE
        # supersedes → 旧经验删除+继承
```

---

## 七、合并操作精确实现

### 7.1 PatchOp.apply 逻辑

```python
def apply(self, current_value, patch_value):
    if self._field_type != FieldType.STRING:
        return patch_value  # 非字符串直接替换
    
    if current_value is None:
        return self._extract_replace_when_no_original(patch_value)
        # 无原始内容 → 提取 replace 值
    
    current_str = current_value
    
    if isinstance(patch_value, StrPatch):
        valid_blocks = [b for b in patch_value.blocks if b.search]
        # 过滤空 search 的 block
        if valid_blocks:
            return apply_str_patch(current_str, StrPatch(blocks=valid_blocks))
        return current_value
    
    if patch_value is None or patch_value == "":
        return current_value  # 空值不修改
    
    return patch_value  # 兜底: 全量替换
```

### 7.2 apply_str_patch 实现

```python
def apply_str_patch(current_text, patch):
    for block in patch.blocks:
        if block.search not in current_text:
            raise ValueError(f"SEARCH not found: {block.search[:50]}")
        current_text = current_text.replace(block.search, block.replace, 1)
    return current_text
```

---

## 八、9种记忆类型 Yaml 核心字段一览

| 类型 | YAML路径 | field个数 | merge_op | 唯一键 | 提取关键词 |
|------|---------|-----------|----------|--------|-----------|
| profile | `memories/profile.md` | 1(content) | patch | - | 身份属性，5-8条，<30词，带时间戳 |
| preferences | `memories/preferences/{user}/{topic}.md` | 3(user,topic,content) | patch/immutable | topic | 偏好主题，3-8条，≤800字符 |
| entities | `memories/entities/{category}/{name}.md` | 3 | patch/immutable | category+name | Zettelkasten卡片 |
| events | `memories/events/{y}/{m}/{d}/{name}.md` | 5 | patch/immutable | event_name | 原子事件，add_only |
| trajectories | `memories/trajectories/{name}_{ts}.md` | 5 | patch/immutable | name+ts | 操作契约，11节，add_only |
| experiences | `memories/experiences/{name}.md` | 3 | replace/immutable | name | Situation/Approach/Reflect |
| identity | `memories/identity.md` | 1 | patch | - | 身份信息 |
| skills | `memories/skills.md` | 1 | patch | - | 技能记录 |
| tools | `memories/tools.md` | 1 | patch | - | 工具使用记录 |
