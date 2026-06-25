# mem0 深度源码理解 —— 隐藏机制全公开

> **代码**: `mem0-main/mem0/` | 核心文件: `configs/prompts.py` (1062行), `memory/main.py` (3543行), `utils/scoring.py`

---

## 一、隐藏在 prompts.py 中的提取"圣经"

**README 上完全没有的内容**。`ADDITIVE_EXTRACTION_PROMPT` 是一个约 **600 行的系统提示**，是 mem0 V3 算法的真正核心。以下是其中最关键的设计决策：

### 1.1 提取哲学

```
- "When in doubt, extract." 宁可冗余不可遗漏（下游有去重）
- "Extract ALL dimensions" 不要只提取第一个话题
- 5-15条记忆/10条消息是正常值，少于3条说明漏了
```

### 1.2 12 个示例（每个都揭示一个隐藏规则）

| 示例 | 隐藏规则 |
|------|---------|
| #1 Marcus 晋升 | 多话题必须分开提取（职业/关系/家庭3条独立记忆） |
| #2 体育纪录片 | 用户的个人偏好 + 助手的推荐都要提取 |
| #3 今日问候 | 纯废话不提取 → `{"memory": []}` |
| #5 重复晋升 | Hash去重：已有内容跳过 |
| #7 模糊时间 | **用 Observation Date 而不是 Current Date** 解析时间！ |
| #8 法律案例 | **提取内容本身而不是动作**（不要"用户分享了一个案例"，而要提取具体案情） |
| #9 D&D 数据 | **数字必须完整保留**：4个木乃伊AC11 HP45不能简化为"几个敌人" |
| #10 记忆链接 | 新记忆通过 `linked_memory_ids` 关联已有记忆形成图 |
| #11 多话题长对话 | **不要被第一个话题主导**——5个消息5个话题需要5条记忆 |
| #12 多说话人 | assistant 角色如果说了个人事实（"Maria刚领养了猫"）必须同样提取 |

### 1.3 时间解析的隐藏逻辑

```python
Observation Date = 对话实际发生日期（关键锚点）
Current Date = 系统当前日期（可能相差数年）

规则: 所有时间引用必须用 Observation Date 解析
"yesterday" → Observation Date 的前一天
"recently" → Observation Date 附近
绝对不能使用 Current Date 解析时间引用
```

### 1.4 Agent 上下文后缀

```python
AGENT_CONTEXT_SUFFIX: 当 agent_id 存在且 user_id 不存在时追加
改变提取框架：
  "User likes X" → "Agent was informed that User likes X"
  "Assistant recommended Y" → "Agent recommended Y"
```

### 1.5 语言自适应

```python
# use_input_language=True 时，强制提取用输入语言输出
# 支持日语（省略主语补完）、中日韩文（保持敬语级别）
```

---

## 二、评分系统源码公式（`utils/scoring.py`）

### 2.1 BM25 归一化公式

```
normalize_bm25(raw) = 1 / (1 + exp(-steepness * (raw - midpoint)))

参数随查询长度自适应：
  ≤3个词: midpoint=5.0, steepness=0.7
  ≤6个词: midpoint=7.0, steepness=0.6
  ≤9个词: midpoint=9.0, steepness=0.5
  ≤15个词: midpoint=10.0, steepness=0.5
  >15个词: midpoint=12.0, steepness=0.5
```

### 2.2 实体提升公式

```
ENTITY_BOOST_WEIGHT = 0.5

boost = similarity × 0.5 × 1/(1 + 0.001×(linked_count-1)²)
         ↑实体相似度  ↑固定权重   ↑链接越多提升越小
```

### 2.3 最终评分公式

```
max_possible = 1.0(语义) [+ 1.0(BM25)] [+ 0.5(实体)]
             = 1.0 / 2.0 / 2.5 / 1.5

combined = (semantic + bm25 + entity_boost) / max_possible
最终得分 capped at 1.0

重要: 语义分数 < threshold 的记忆直接被排除
      即使BM25和实体提升很高也不会被召回
```

---

## 三、V3 Pipeline 8 个 Phase（`main.py:761-1081`）

### Phase 0: 上下文收集
```
session_scope = _build_session_scope(filters)  # user_id | agent_id | run_id
last_k_messages = db.get_last_messages(session_scope, limit=10)
```

### Phase 1: 已有记忆检索
```
search_filters = {user_id, agent_id, run_id}
existing = vector_store.search(query_embedding, top_k=10)

# 反幻觉映射：
uuid_mapping[str(idx)] = mem.id  # UUID→整数
existing_memories = [{"id": str(idx), "text": ...}]  # LLM看到整数ID
```

### Phase 2: 单次 LLM 提取
```
system_prompt = ADDITIVE_EXTRACTION_PROMPT + (AGENT_CONTEXT_SUFFIX 如果agent_scoped)
user_prompt = generate_additive_extraction_prompt(
    existing_memories, new_messages, last_k_messages, custom_instructions)

response = llm.generate_response(response_format={"type": "json_object"})

# 双保险JSON解析
try: json.loads(response)
except: extract_json(response)  # 正则提取
```

### Phase 3-5: 批量嵌入 + Hash 去重 + 构建记录
```
mem_embeddings = embed_batch(mem_texts, "add")  # 批量
mem_hash = md5(text.encode())                   # 去重
text_lemmatized = lemmatize_for_bm25(text)      # BM25词干化
```

### Phase 6: 批量持久化 + History
```
vector_store.insert(vectors=all_vectors, ids=all_ids, payloads=all_payloads)
# fallback: 逐条插入
db.batch_add_history(history_records)
```

### Phase 7: 批量实体链接（核心创新）
```
# 7a: 批量提取实体
all_entities = extract_entities_batch(all_texts)

# 7b: 全局去重
normalized_key = entity_text.strip().lower()  # 大小写不敏感

# 7c: 批量嵌入+搜索
entity_embeddings = embed_batch(entity_texts, "add")
existing_matches = entity_store.search_batch(...)

# 7d: 阈值0.95区分insert/update
if matches[0].score >= 0.95:
    # 更新已有实体：追加linked_memory_ids
else:
    # 新建实体
```

### Phase 8: 保存消息返回
```
db.save_messages(messages, session_scope)
return [{"id": mem_id, "memory": text, "event": "ADD"}]
```

---

## 四、Entity Store 关键设计

### Entity 相似度阈值 = 0.95
```
为什么是0.95？远高于常规向量检索阈值(0.5-0.7)
目的：确保只有真正相同的实体（"John Smith"="John Smith"）才合并
避免："John likes Python" 和 "John likes Java" 被认为是同一实体
```

### linked_memory_ids 增长控制
```
memory_count_weight = 1.0 / (1.0 + 0.001 * ((num_linked - 1) ** 2))
一个实体链接了100个记忆时，权重仅 1/(1+0.001*9801)=0.09
```

### 删除时的级联清理
```
删除记忆时:
1. 列出所有实体（top_k=10000）
2. 从每个实体的 linked_memory_ids 中移除该记忆ID
3. 如果列表为空 → 删除实体
4. 否则 → 重新嵌入 + 更新实体
```

---

## 五、Procedural Memory 机制

```
_create_procedural_memory: agent_id + memory_type="procedural" 时使用
- 用 PROCEDURAL_MEMORY_SYSTEM_PROMPT 作为 system 消息
- 追加用户消息
- LLM 生成过程性记忆文本
- metadata 中标记 memory_type
- 嵌入 + 存储
```

---

## 六、`_update_memory` 中的隐藏规则

```python
# actor_id 不可变（issue #4490）
if "actor_id" in existing_memory.payload:
    new_metadata["actor_id"] = existing_memory.payload["actor_id"]

# 更新时自动清理并重建实体链接
self._remove_memory_from_entity_store(memory_id, session_filters)
self._link_entities_for_memory(memory_id, data, session_filters)
```

---

## 七、Telemetry 系统（后台）

```python
# 使用独立的 vector_store 实例存储遥测数据
# collection_name = "mem0migrations"
# provider = faiss 或 qdrant
# path = ~/.mem0/migrations_faiss/ 或 migrations_qdrant/
```

---

## 评分机制隐藏参数总表

| 参数 | 值 | 作用 |
|------|-----|------|
| ENTITY_BOOST_WEIGHT | 0.5 | 实体提升最大权重 |
| Entity threshold | 0.5 | 实体搜索最低相似度 |
| Entity merge threshold | 0.95 | 判定是否同一实体 |
| Semantic over-fetch | max(top_k*4, 60) | 过采样数 |
| BM25 sigmoid midpoint | 5.0~12.0 | 随查询长度变化 |
| BM25 sigmoid steepness | 0.7~0.5 | 随查询长度变化 |
| Entity max count | 8 | 最多提取的实体数 |
| Entity search top_k | 500 | 实体搜索量 |
| Entity parallel workers | 4 | 多线程搜索数 |
| Memory text word range | 15-80 | 每条记忆字数 |
| Max batch entities | 8 | 每批最多处理实体 |
| Last k messages | 10 | 最近消息数 |
| Recently extracted | 20 | 最近提取数 |
