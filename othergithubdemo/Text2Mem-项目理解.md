# Text2Mem 完整记忆管道

> **代码**: `text2mem-main/`

---

## 一、核心设计

```
自然语言记忆指令
    │ (欠指定: scope/timing/permissions不明确)
    ├── Clarify（可选预消歧）
    │   解决模糊指令
    │
    └── Operation Schema IR (JSON)
    │   {stage, op, target, args, meta}
    │   → 12 规范动词之一
    │
    ├── Validator
    │   ├── JSON Schema 结构校验
    │   ├── Pydantic v2 类型校验
    │   └── Safety Invariants
    │       (Delete 需要确认, Lock 检查权限)
    │
    ├── Parser
    │   └── 规范化 → 类型化内部对象
    │
    └── Adapter
        ├── Model Service (Mock/Ollama/OpenAI)
        └── Storage (SQLite/Postgres)
```

---

## 二、12 规范操作分类

| Stage | 操作 | IR 示例 |
|-------|------|---------|
| **ENC** (编码) | Encode | `{"op":"Encode","args":{"payload":{"text":"...","type":"event"}}}` |
| | Retrieve | `{"op":"Retrieve","target":{"search":{"query":"...","limit":5}}}` |
| | Summarize | `{"op":"Summarize","target":{"ids":["1"]},"args":{"focus":"brief"}}` |
| **STG** (存储治理) | Update | `{"op":"Update","target":{"ids":["1"]},"args":{"payload":{...}}}` |
| | Label | `{"op":"Label","target":{"ids":["1"]},"args":{"tags":["important"]}}` |
| | Promote | `{"op":"Promote","target":{"ids":["1"]},"args":{"level":"high"}}` |
| | Demote | `{"op":"Demote","target":{"ids":["1"]},"args":{"level":"low"}}` |
| | Merge | `{"op":"Merge","target":{"source_ids":["1","2"],"target_id":"3"}}` |
| | Split | `{"op":"Split","target":{"id":"1"},"args":{"parts":[{"text":"..."}]}}` |
| | Delete | `{"op":"Delete","target":{"ids":["1"]}}` |
| | Lock | `{"op":"Lock","target":{"ids":["1"]}}` |
| | Expire | `{"op":"Expire","target":{"ids":["1"]},"args":{"ttl":86400}}` |
