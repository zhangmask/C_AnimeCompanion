# Hindsight 完整记忆管道

> **所属**: Vectorize.io | **论文**: arXiv:2512.12818 | **API**: hindsight-client (Python/Node.js)

---

## 一、记忆系统全景

```
┌──────────────────────────────────────────────────────────────────┐
│  H i n d s i g h t  记 忆 管 道                                     │
│                                                                  │
│  3 个原子操作:                                                     │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐                           │
│  │ Retain  │  │ Recall  │  │ Reflect │                           │
│  │ (存入)   │  │ (检索)   │  │ (反思)   │                           │
│  └────┬────┘  └────┬────┘  └────┬────┘                           │
│       │            │            │                                 │
│       ▼            ▼            ▼                                 │
│  ┌─────────────────────────────────────┐                          │
│  │          Memory Bank                │                          │
│  │  ┌──────────┐ ┌──────────┐         │                          │
│  │  │  World   │ │Experiences│         │                          │
│  │  │ (世界事实) │ │  (经验)   │         │                          │
│  │  └──────────┘ └──────────┘         │                          │
│  │  ┌──────────────────────────┐      │                          │
│  │  │    Mental Models         │      │                          │
│  │  │    (心智模型 — Reflect生成) │      │                          │
│  │  └──────────────────────────┘      │                          │
│  └─────────────────────────────────────┘                          │
│                                                                  │
│  存储架构:                                                         │
│  ├─ 稠密向量 (Dense Embedding)                                    │
│  ├─ 稀疏向量 (BM25 Keyword)                                       │
│  ├─ 实体关系图 (Entity Graph)                                      │
│  └─ 时间索引 (Temporal Index)                                     │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、Retain 完整流程

```python
# 客户端调用
client.retain(
    bank_id="my-bank",          # 记忆银行（隔离单元）
    content="Alice works at Google as a software engineer",
    context="career update",    # 上下文标签
    timestamp="2025-06-15T10:00:00Z",  # 时间锚点
    metadata={"source": "conversation_123"}  # 自定义元数据
)
```

### Retain 服务端管道

```
retain(content):
  │
  ├── 1. LLM 提取
  │   ├── 关键事实: "Alice is a software engineer at Google"
  │   ├── 时间数据: "2025-06-15 (promotion time)"
  │   ├── 实体: ["Alice" (PERSON), "Google" (ORG)]
  │   └── 关系: ("Alice" → "works_at" → "Google")
  │
  ├── 2. 规范化
  │   ├── 标准化实体: "Alice" → canonical form
  │   ├── 时间序列化: 相对时间→绝对时间
  │   └── 元数据附加
  │
  └── 3. 多路径索引
      ├── 稠密向量: Embedding model → 向量
      ├── 稀疏向量: BM25 → 关键词索引
      ├── 实体图: Entities → Relationships → Graph
      └── 时间索引: timestamp → sorted list
```

---

## 三、Recall 完整流程

```python
# 客户端调用
results = client.recall(
    bank_id="my-bank",
    query="What does Alice do?"
)
```

### Recall 服务端管道（4 路并行）

```
recall(query):
  │
  ├── 1. Semantic (语义检索)
  │   └── embedding → 向量相似度搜索
  │
  ├── 2. Keyword (关键词检索)
  │   └── BM25 → 精确匹配索引
  │
  ├── 3. Graph (图谱检索)
  │   └── 提取查询实体 → 遍历实体关系图 → 链接记忆
  │
  ├── 4. Temporal (时间检索)
  │   └── 解析时间引用 → 时间范围过滤
  │
  └── 5. 融合排序
      ├── RRF (Reciprocal Rank Fusion): 合并4路结果
      ├── Cross-encoder: 二次重排序
      └── Token 预算裁剪 → 最终结果
```

---

## 四、Reflect 完整流程

```python
# 客户端调用
insights = client.reflect(
    bank_id="my-bank",
    query="What should I know about Alice?"
)
```

### Reflect 服务端管道

```
reflect(query):
  │
  ├── 1. 检索相关记忆 (调用 Recall)
  │   └── 获取所有与 Alice 相关的记忆
  │
  ├── 2. LLM 深度分析
  │   ├── 发现记忆之间的新连接
  │   ├── 识别模式/趋势
  │   ├── 生成新洞察
  │   └── 更新 Mental Models
  │
  └── 3. 返回分析结果
```

---

## 五、三层记忆类型

| 层 | 内容 | 创建方式 | 类似人类记忆 |
|----|------|---------|------------|
| **World** | "The stove gets hot" | 从用户输入提取 | 语义记忆 |
| **Experiences** | "I touched the stove and it hurt" | 从Agent经历提取 | 情景记忆 |
| **Mental Models** | "Hot stoves cause burns—be careful" | Reflect 生成 | 程序性记忆 |

---

## 六、Bank 隔离机制

```python
# 每个 Bank 独立存储
bank_1 = "user_alice"     # → Alice 的记忆
bank_2 = "user_bob"       # → Bob 的记忆
bank_3 = "project_alpha"  # → 项目 Alpha 的记忆

# Bank 间完全隔离
client.recall(bank_id="user_alice", query="...")  # 只返回 Alice 的记忆
```

Bank 内支持 metadata 过滤 → 实现更细粒度的隔离（按用户/会话/场景）。

---

## 七、部署

| 方式 | 依赖 | 启动 |
|------|------|------|
| Docker Server | 内置 PG0 | `docker run hindsight` |
| Docker Compose | PostgreSQL | `docker compose up` |
| Python Embedded | 无外置 | `HindsightServer()` |
| Python Client | 网络 | `HindsightClient()` |
