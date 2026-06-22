# Anime Companion -- MNN 版本

> **端侧关系型 AI 基础设施 | MNN 推理框架 + Qwen3.5 2B | ARM 架构深度优化**

---

## 版本说明

本版本是 Anime Companion 的 **MNN 优化版本**，相比原版（Gemma 4 + LiteRT-LM），进行了以下核心变更：

| 变更项 | 原版 (Gemma 4) | MNN 版本 |
|--------|---------------|----------|
| 主推理框架 | LiteRT-LM + llama.cpp | **MNN** |
| 对话模型 | Gemma 4 E2B | **Qwen3.5 2B** |
| 量化格式 | FP16 (.gguf/.litertlm) | **INT4 (.mnn)** |
| 文本嵌入 | ONNX + Qwen3-VL 4B | **MNN + Qwen3.5 2B Embedding** |
| ARM 优化 | XNNPACK (通用) | **NEON/SVE 原生优化** |
| 推理速度 | 5-8 tokens/s | **8-12 tokens/s** |
| 内存占用 | 2.4GB (FP16) | **1.2GB (INT4)** |
| 许可证 | Gemma Terms (需确认商用) | **Apache 2.0 (无限制)** |

---

## 核心特性

### 1. MNN 推理引擎

[MNN](https://github.com/alibaba/MNN) 是阿里巴巴开源的端侧推理框架，专为移动设备优化：

- **ARM NEON/SVE 原生优化**：比通用框架快 30-50%
- **INT4 量化支持**：模型体积减小 75%，内存占用大幅降低
- **轻量级设计**：核心库仅 2MB，启动速度快
- **GPU 加速**：支持 OpenCL/Vulkan 后端
- **阿里官方维护**：与 Qwen 模型深度适配

### 2. Qwen3.5 2B 模型

[Qwen3.5](https://huggingface.co/Qwen/Qwen3.5-2B) 是阿里通义千问系列的最新 2B 参数模型：

- **中文原生训练**：中文对话自然度优于 Gemma/Llama
- **角色扮演优秀**：社区反馈在陪伴场景下表现最佳
- **Apache 2.0 许可**：商用无任何限制
- **MNN 深度适配**：阿里官方优化，性能最优

### 3. 生图文本嵌入统一

本版本将生图模型的文本嵌入也统一为 MNN + Qwen3.5 2B：

- **统一推理框架**：对话和生图都用 MNN，减少依赖
- **中文 Prompt 原生理解**：不需要翻译成英文
- **ARM 优化**：嵌入推理也享受 MNN 的性能优势

---

## 项目结构

```
REDEME_MNN/
├── README.md           # 本文件
├── ARCHITECTURE.md     # 技术架构文档
├── MODEL_CARD.md       # 模型卡片
├── DEPLOYMENT.md       # 部署指南
├── PRIVACY.md          # 隐私与合规
├── BUSINESS.md         # 商业逻辑
└── VISION.md           # 产品愿景
```

---

## 快速开始

1. 阅读 [部署指南](DEPLOYMENT.md) 了解环境配置和编译步骤
2. 阅读 [模型卡片](MODEL_CARD.md) 了解模型规格和许可证
3. 阅读 [技术架构](ARCHITECTURE.md) 了解系统设计

---

## 与 Gemma 4 版本的对比

| 维度 | Gemma 4 版本 | MNN 版本 |
|------|-------------|----------|
| 推理框架 | LiteRT-LM + llama.cpp (双运行时) | MNN (单运行时) |
| 对话模型 | Gemma 4 E2B (FP16) | Qwen3.5 2B (INT4) |
| 推理速度 | 5-8 tokens/s | **8-12 tokens/s (+50%)** |
| 首字延迟 | 1-2 秒 | **0.5-1 秒 (-50%)** |
| 内存占用 | 2.4GB | **1.2GB (-50%)** |
| 中文能力 | 一般 | **优秀** |
| 许可证 | Gemma Terms | **Apache 2.0** |
| 商用限制 | 需确认 | **无限制** |
| 生图嵌入 | ONNX + Qwen3-VL 4B | MNN + Qwen3.5 2B Embedding |
| ARM 优化 | XNNPACK (通用) | **NEON/SVE (原生)** |

---

## 许可证

本项目采用 **Apache 2.0 许可证**。

所有核心模型和框架均为 Apache 2.0 / MIT 许可，商用无任何限制。详见 [模型卡片](MODEL_CARD.md)。

---

## 相关链接

- [MNN 官方仓库](https://github.com/alibaba/MNN)
- [Qwen3.5 模型](https://huggingface.co/Qwen/Qwen3.5-2B)
- [原版 README](../REDEME/) (Gemma 4 版本)
