# MetaMem 完整记忆管道

> **源码**: `train_metamem.py`(1025行) + `eval_metamem.py`(659行) + `construct_memory.py`(229行)

---

## 一、记忆系统全景

```
┌──────────────────────────────────────────────────────────────────┐
│  M e t a M e m  记 忆 管 道                                        │
│                                                                  │
│  【记忆构建层】                                                    │
│  longmemeval.json → LightMem(Qdrant向量库) → related_memories     │
│                                                                  │
│  【元记忆训练层】                                                   │
│  Step 0: meta_memories={}                                        │
│    ├─ Rollout: 用空元记忆回答问题 → 采样5个答案                      │
│    ├─ 验证: Judge LLM打分 → 计算reward                            │
│    └─ 更新: 对比正确/错误答案 → 生成 M0, M1, ...                    │
│                                                                  │
│  Step 1: meta_memories={M0, M1, ...}                             │
│    ├─ Rollout: 用M0,M1回答问题 → 采样                              │
│    ├─ 验证: Judge LLM打分                                         │
│    └─ 更新: 生成新的add/update/delete操作 → M0', M1', M2'          │
│                                                                  │
│  Step N: ... (持续迭代)                                           │
│                                                                  │
│  【推理层】                                                        │
│  PROBLEM_WITH_META_MEMORY_TEMPLATE                               │
│  (meta_memories注入到system prompt中)                              │
│  → LLM回答 → verify_answer判分                                    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、记忆构建详细流程

### 2.1 数据输入格式

```json
{
  "question_id": "xxx",
  "question": "根据过去几天的对话，用户的健康状况发生了什么变化？",
  "ground_truth": "用户感冒了，吃了药后好转",
  "question_type": "multi-session",
  "haystack_sessions": [
    [
      {"role": "user", "content": "我今天感觉不舒服"},
      {"role": "assistant", "content": "建议多休息"}
    ],
    [
      {"role": "user", "content": "吃了药好多了"},
      {"role": "assistant", "content": "很高兴听到你好转"}
    ]
  ],
  "haystack_dates": ["2024-01-01", "2024-01-02"]
}
```

### 2.2 construct_memory.py 记忆构建流程

```
for each item in 数据集:
    # 每个 question_id 创建独立的 Qdrant collection
    lightmem = load_lightmem(collection_name=qid)
    
    for session, timestamp in zip(sessions, timestamps):
        # 清理：移除开头的非user消息（保证每轮从user开始）
        while session and session[0]["role"] != "user":
            session.pop(0)
        
        for each turn (user + assistant):
            msg["time_stamp"] = timestamp  # 注入时间戳
            # 最后1 turn强制分段和提取
            result = lightmem.add_memory(
                messages=turn_messages,
                force_segment=is_last_turn,
                force_extract=is_last_turn
            )
    
    # 检索top-20相关记忆
    related_memories = lightmem.retrieve(item["question"], limit=20)
```

### 2.3 LightMem 配置（被文档完全忽略的细节）

```python
config = {
    "pre_compress": True,        # 先用LLMLingua-2压缩消息
    "pre_compressor": {          # 预压缩器配置
        "model_name": "llmlingua-2",
        "llmlingua_config": {
            "use_llmlingua2": True,  # 使用v2版本
            "device_map": "cuda",    # GPU加速
        }
    },
    "topic_segment": True,       # 主题分段
    "precomp_topic_shared": True, # 共享预压缩和分段结果
    "messages_use": "hybrid",     # 混合模式(原始+压缩)
    "metadata_generate": True,   # 生成元数据
    "text_summary": True,        # 生成文本摘要
    "memory_manager": {          # 记忆管理器=LLM
        "model_name": "openai",
        "model": args.llm_model,     # 如 qwen3-30b
        "max_tokens": 16000,
    },
    "extract_threshold": 0.1,   # 提取阈值(低于0.1不提取)
    "index_strategy": "embedding",
    "text_embedder": {           # embedding模型
        "model": "all-MiniLM-L6-v2",
        "embedding_dims": 384,
    },
    "retrieve_strategy": "embedding",
    "embedding_retriever": {     # Qdrant向量检索
        "model_name": "qdrant",
        "embedding_model_dims": 384,
        "path": f"{args.qdrant_dir}/{collection_name}",
    },
    "update": "offline",         # 离线更新模式
}
```

---

## 三、训练管道（完整的 3 步更新循环）

### 3.1 Rollout 采样（`train_metamem.py:348-435`）

```
输入: formatted_batch = [{prompt, problem, memories, groundtruth, task}]
      每个问题 × num_samples(5) 份 → 高温采样(T=0.7)

处理:
  task_queue = asyncio.Queue()
  for each sample (16 workers 并发):
      response = llm.chat(prompt, temperature=0.7, max_tokens=4096)
      reward = verify_answer(sample, sample["groundtruth"])
      
      # 重试机制:
      if error and retry_count <= 3:
          await task_queue.put(sample)  # 重新入队
      elif error and retry_count > 3:
          reward = 0  # 超过3次失败 → 0分
      
      rollouts[sample["runid"]] = sample
      # 每次写入完整文件
      with open(rollout_filename, "w") as f:
          for r in rollouts:
              f.write(json.dumps(r) + "\n")

统计:
  avg_reward = sum(all_rewards) / len(all_rewards)
  num_samples
```

### 3.2 Prompt 注入模板

**Step 0**（无元记忆）:
```
Answer the following question based on the retrieved memory fragments.
Question: {question}
Retrieved Memory Fragments: {memories}
Think step by step and provide your answer.
```

**Step N**（有元记忆）— **`PROBLEM_WITH_META_MEMORY_TEMPLATE`**:
```
Meta-Memory Guidelines（README完全没提到的内容）:
- 每条元记忆都是一个"如何利用记忆"的策略
- 需要先 review 元记忆指导
- 分析检索到的记忆片段并识别相关信息
- 应用元记忆策略来综合记忆中的信息
- 如果记忆不足或有冲突，按元记忆指导处理
- Think step by step
```

### 3.3 MetaMemoryUpdater.run() 三步更新

#### Step 1: 过滤问题

```python
if only_partial_correct:
    scores = [r.get("reward", 0) for r in group]
    avg = sum(scores) / len(scores)
    if 0 < avg < 1:
        filtered_problems[problem] = group  # 只保留部分正确的
```

**过滤掉的**:
- avg=0: 全错 → 没有成功经验可学
- avg=1: 全对 → 没有犯错，缺乏对比分析价值
- 只有 `0 < avg < 1`: 既有成功也有失败 → 可以从对比中学习

#### Step 2: 轨迹摘要

```python
TRAJECTORY_SUMMARY_TEMPLATE:
  分析4个方面:
  1. 记忆利用分析: 用了哪些记忆？忽略了哪些？
  2. 推理过程: 推理步骤、逻辑错误
  3. 关键决策点: 成功/失败的关键因素
  4. 经验教训: 可泛化的记忆利用洞察
```

#### Step 3: 提取元记忆更新

```python
META_MEMORY_UPDATE_TEMPLATE:
  
  "对比同一个问题多次尝试的成功/失败模式"
  "每条元记忆必须是:"
  "- 可泛化的策略（不是领域特定知识）"
  "- 可操作的指导（30词以内单句）"
  "- 关于如何利用记忆（不是记忆内容本身）"
  
  输出格式:
  ```json
  [
    {"operation": "add", "content": "当记忆包含时间信息时，优先使用最新数据"},
    {"operation": "update", "id": "M0", "content": "改进版策略"},
    {"operation": "delete", "id": "M1"}
  ]
  ```
```

#### Step 4: 合并消解

```python
BATCH_META_MEMORY_UPDATE_TEMPLATE:
  
  合并原则:
  1. 合并相似提议: 相同原则合并为更通用的表述
  2. 解决冲突: 矛盾时分析哪个更通用
  3. 避免冗余: 与已有重叠则更新已有而非新增
  4. 质量控制: 必须关于记忆利用策略、必须可泛化
  5. 稳定性: 优先更新而不是删除后新增
```

---

## 四、评估管道

### 4.1 evaluate_single_step 流程

```
输入: test_data + meta_memories
处理:
  1. 格式化元记忆
  2. 构建PROBLEM_WITH_META_MEMORY_TEMPLATE prompt
  3. rollout_dataset(..., temperature=0)  # 评估用temperature=0，确定性输出
  4. 按task类型统计准确率
  
输出:
  {
    "total": total,
    "correct": correct,
    "accuracy": accuracy,
    "task_accuracy": {
      "multi-session": 0.85,
      "temporal-reasoning": 0.72,
      ...
    },
    "num_meta_memories": len(meta_memories)
  }
```

### 4.2 按任务类型评估

```
evaluate_all_steps:
  for step in range(max_steps + 1):
      查找 step_{step}/meta_memories.json
      如果不存在 → 回溯找最近的有元记忆的step
      执行evaluate_single_step
      记录 {step: results}
```

---

## 五、元记忆全生命周期

```
初始: meta_memories = {} (Step 0)

训练: 
  Step 0: Rollout(无元记忆) → 对比5个答案 → 生成M0, M1
  Step 1: Rollout(有M0,M1) → 对比5个答案 → 生成M2, 更新M0
  Step N: ...

每条元记忆的格式:
  M0: "一条关于如何使用记忆的30词策略"
  
元记忆文件路径:
  data/memory/train/{experiment_name}/step_{N}/meta_memories.json

stats.json 断点续传:
  {
    "step_0": {"epoch": 0, "batch": 0, "rollout": {...}, "complete": true},
    "step_1": {...}
  }
```
