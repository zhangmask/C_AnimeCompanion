# Second Me 完整记忆管道

> **源码**: `lpm_kernel/L1/` (bio.py 786行, l1_generator.py 477行, prompt.py 306行), `lpm_kernel/L2/` (train.py 439行, data.py 780行, l2_generator.py 245行)

---

## 一、记忆系统全景

```
┌──────────────────────────────────────────────────────────────────┐
│  S e c o n d  M e  记 忆 管 道  (HMM)                             │
│                                                                  │
│  L0: 原始输入层                                                   │
│  ┌─────────────────────────────────────────┐                     │
│  │ Note(TEXT/MARKDOWN/PDF/LINK)            │                     │
│  │ Chunk[] → embedding[1536维]             │                     │
│  │ createTime, summary, insight, tags, topic│                     │
│  └──────────────────┬──────────────────────┘                     │
│                     ↓ LLM 提取                                    │
│  L1: 结构化记忆层                                                  │
│  ┌─────────────────────────────────────────┐                     │
│  │ Bio(5级置信度)                           │                     │
│  │ Shade(兴趣阴影→domainName/icon/timeline) │                     │
│  │ Topics(关键词聚类)                       │                     │
│  │ MonthlyTimeline(月度时间线)              │                     │
│  └──────────────────┬──────────────────────┘                     │
│                     ↓ 生成训练数据                                  │
│  L2: 模型微调层                                                    │
│  ┌─────────────────────────────────────────┐                     │
│  │ preference.json + diversity.json + selfqa.json                │
│  │ → merged.json → SFT训练(LoRA r=64)       │                     │
│  │ → 微调后的模型权重                          │                     │
│  └─────────────────────────────────────────┘                     │
│                                                                  │
│  推理: Me.bot(JUDGE/CONTEXT/MEMORY 三种CoT角色) → 个性化回复       │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、L0 数据模型（`L1/bio.py`）

### 2.1 Note 笔记模型

```python
class Note:
    noteId: int
    content: str
    createTime: str           # "%Y-%m-%d %H:%M:%S"
    memoryType: str           # TEXT | MARKDOWN | PDF | LINK
    embedding: [float; 1536]  # 默认1536维
    chunks: List[Chunk]       # 文档分块
    title: str
    summary: str
    insight: str              # LLM 生成的洞察
    tags: List[str]
    topic: str

class Chunk:
    id: int
    document_id: int
    content: str
    embedding: [float; 1536]
    tags: List[str]
    topic: str
```

### 2.2 记忆分类

```python
SUBJECT_NOTE_TYPE = [TEXT, MARKDOWN, PDF]    # 主观记忆（用户自己的内容）
OBJECT_NOTE_TYPE = [LINK]                     # 客观记忆（外部链接）

# 时间敏感的召回策略
MIN_MEMORIES_N = {RECENT: 3, EARLIER: 10}    # 最少保留数
TIME_RANGE = {RECENT: 86400, EARLIER: 604800} # 1天 vs 7天
```

### 2.3 Bio 模型（5 级置信度）

```python
class Bio:
    pass  # 用户的传记/画像

ConfidenceLevel: VERY_LOW(1) → LOW(2) → MEDIUM(3) → HIGH(4) → VERY_HIGH(5)
IMPORTANCE_TO_CONFIDENCE = {1→VERY_LOW, 2→LOW, ...}
```

---

## 三、L1 结构化记忆生成（`L1/l1_generator.py`）

### 3.1 6 种生成方法

```python
class L1Generator:
    # LLM 配置:
    temperature=0, top_p=0(→0.001 fallback), max_tokens=2000, timeout=45s
    
    # 1. 全局画像生成
    _global_bio_generate(bio) → 更新 Bio
    
    # 2. 状态画像生成
    _status_bio_generate(bio) → 更新 Bio
    
    # 3. 主题聚类
    _topic_generate(keyword, notes) → 按主题聚类笔记
    
    # 4. 兴趣阴影构建(初始)
    shade_build(shade_infos) → 从笔记中提取兴趣领域
    
    # 5. 兴趣阴影改进(增量)
    shade_improve(shade, notes) → 基于新笔记更新兴趣
    
    # 6. 兴趣阴影合并
    shade_merge(shade_infos) → 合并相似兴趣为更通用的领域
    
    # 7. 月度时间线
    monthly_timeline_generate(...) → 按月组织事件
```

### 3.2 Shade（兴趣阴影）数据结构

```python
# 从笔记分析提取的兴趣领域
ShadeInfo:
    domainName: str     # "Python编程"
    aspect: str         # "Code Wizard" (角色名)
    icon: str           # "🐍"
    domainDesc: str     # 一句话描述
    domainContent: str  # 详细内容(含子领域)
    domainTimelines:    # 时间线
      [{createTime, refMemoryId, description}]
```

### 3.3 Shade Merge 合并算法

```python
# 当多个 ShadeInfo 相似时合并
# 输入: 2+ 个 ShadeInfo（如 "Python编程" + "Java开发"）
# 输出: 合并后的 ShadeInfo（如 "软件开发"）
# 
# 合并过程:
# 1. 识别共同点
# 2. 提取更通用的兴趣领域
# 3. 合并时间线（保留全部）
# 4. 整合内容描述
```

### 3.4 置信度计算

```python
# 每个提取的记忆片段都有置信度:
# VERY_LOW(1): 猜测
# LOW(2): 弱证据
# MEDIUM(3): 一般可靠
# HIGH(4): 强证据
# VERY_HIGH(5): 用户明确陈述
```

---

## 四、L2 模型训练管道（`L2/l2_generator.py + train.py`）

### 4.1 L2 数据生成

```python
class L2Generator:
    def gen_subjective_data(self, note_list, basic_info, ...):
        # 1. 偏好QA生成
        processor = PreferenceQAGenerator(topics_path, global_bio)
        processor.process_clusters("preference.json")
        
        # 2. 多样性数据生成
        processor = DiversityDataGenerator(lang, is_cot)
        processor.generate_data(entities_path, note_list, ...)
        
        # 3. 自我QA生成
        selfqa = SelfQA(user_name, user_intro, global_bio, lang, is_cot)
        q_a_list = selfqa.generate_qa()
        
        # 4. 合并为merged.json
        merged = preference.json + diversity.json + selfqa.json
        
        # 5. 释放Ollama模型（释放VRAM给训练）
        self._release_ollama_models()
```

### 4.2 SFT 训练配置

```python
# 训练参数:
Model: Qwen2.5 系列
LoRA:
  r=64               # LoRA秩
  alpha=16           # LoRA缩放
  dropout=0.1        # Dropout
  target_modules:    # 目标模块
    q_proj, k_proj, v_proj, o_proj,
    down_proj, up_proj, gate_proj

量化:
  8bit 或 4bit (nf4)
  
可选:
  Flash Attention
  UnSloth 加速
  MLX 训练 (Mac M系列)

训练框架:
  SFTTrainer (TRL)
  DataCollatorForCompletionOnlyLM  # 只计算完成的token
```

### 4.3 三种 CoT 训练 Prompt

```python
# 1. JUDGE_COT_PROMPT: 管家角色
#    评估专家回复是否符合用户需求
#    格式: <think>...</think><answer>...</answer>

# 2. CONTEXT_COT_PROMPT: 需求增强器
#    将模糊需求补充个人背景
#    保持第一人称

# 3. MEMORY_COT_PROMPT: 回答者
#    基于用户背景回答问题
```

### 4.4 推理流水线

```python
# 用户提问 → Me.bot 推理:
# 1. 检索 L1 记忆碎片
# 2. 加载 L2 微调模型（模型已经把"你是谁"内化到参数中）
# 3. 使用训练好的 CoT prompt 模板
# 4. 生成个性化回复
```

---

## 五、GraphRAG 知识图谱构建

```python
# 配置: L2/data_pipeline/graphrag_indexing/
# 使用 Microsoft GraphRAG
settings.yaml:
  input:
    base_dir: 笔记目录
    file_pattern: ".*\\.md$"
  models:
    default_chat_model:
      api_key: sk-xxxxx
  
# 用途: 从笔记中提取实体和关系
# → 增强 L2 训练数据的多样性
```

---

## 六、全量配置参数

```python
# L1 配置:
bio_model_params = {
    "temperature": 0,
    "max_tokens": 2000,
    "top_p": 0,          # LLM 不支持时 fallback 到 0.001
    "frequency_penalty": 0,
    "presence_penalty": 0,
    "seed": 42,
    "timeout": 45,
}

# L2 训练配置:
model_args:
    lora_r: 64
    lora_alpha: 16
    lora_dropout: 0.1
    use_peft_lora: False
    use_8bit_quantization: False
    use_4bit_quantization: False
    use_unsloth: False
    use_cuda: False
```
