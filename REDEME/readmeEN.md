# Anime Companion

> Side-AI Relational Infrastructure — Giving Everyone a Truly Understanding, Always-Present, and Completely Private AI Companion

## Inspiration

Current mainstream AI products cannot meet users' needs for long-term companionship, and they fall into two categories, neither of which can truly satisfy the core demands of sustained intimacy and privacy.

Cloud AI has three inherent flaws: privacy is fundamentally unguaranteed because all conversations, preferences, and sensitive data are handed over to third-party servers; relationships cannot continue because data belongs to the platform, preventing free export or cross-app migration, so changing services means rebuilding all memories; and usage scenarios are limited because it must stay online, is subject to censorship, and cannot form a unique personality that truly belongs to you.

Meanwhile, local applications suffer from critical capability defects. Products that simply port large models onto phones can only manage basic conversations. They lack long-term memory capabilities, have no power consumption control, and offer no personality customization — making them toys for geeks rather than tools for daily life.

We asked ourselves: why can't everyone have an AI companion that truly understands them, remembers their experiences, and lives entirely on their own device?

## What it does

Anime Companion is an AI companion application that runs 100% locally on your phone. All computation, inference, and storage are completed on your own device — your chat logs, exclusive memories, personal preferences, voice chats, and image generation are always calculated and stored only on your phone, never uploaded to any external servers.

At the same time, we also provide optional cloud API support, such as Qwen Cloud, for users who want stronger models, more real-time TTS/ASR, image generation, video, and other capabilities. When using cloud APIs, users take responsibility for the security of their own chat data.

It is not a generic chatbot, but a truly exclusive companion that establishes a long-term stable relationship with you. It remembers your preferences and shared experiences, understands you better the more you chat, grows with you, and does not frequently lose memory. Other components such as ASR, TTS, and image generation models all run locally without relying on cloud services, so you can chat anytime even when offline.

Key capabilities include:
- **Dual-runtime inference scheduling**: high-end devices use llama.cpp for throughput, mainstream devices use LiteRT-LM for smoothness and heat dissipation.
- **Dynamic context compression**: early history is compressed and rebuilt to keep long conversations responsive.
- **Hierarchical memory engine**: short-term and long-term memory layers automatically extract and organize preferences, habits, and important moments.
- **Power consumption control**: dynamic inference scheduling and background-only memory maintenance prevent overheating and battery drain.
- **Role-aware voice**: automatic selection between system TTS and MOSS voice cloning based on character cards.
- **Local image generation**: Stable Diffusion-based local inference for companion avatars without internet access.
- **Character card system**: each character has independent personality settings, speaking style, memory, and relationships.

## How we built it

The project is built around a self-developed side-AI infrastructure optimized for smartphones:

- **Core reasoning engine**: qwen3.5 handles companionship generation and character expression. High-end devices route through llama.cpp for maximum throughput, while mainstream devices use LiteRT-LM for better thermal and battery performance.
- **Local ASR**: SenseVoice + Sherpa-ONNX provides on-device speech recognition.
- **Local TTS**: MOSS TTS Nano handles speech synthesis and character voice cloning entirely on-device.
- **Local image generation**: Stable Diffusion via stable-diffusion.cpp enables offline avatar creation.
- **Memory system**: FTS4 full-text search combined with semantic normalization, dual-channel rule/LLM extraction, and reference-count-based priority promotion.
- **Context management**: dynamic context compression keeps long conversations fast without losing coherence.
- **Power management**: foreground processes handle user interaction only, while memory writeback and preference learning run in the background during idle periods.
- **Android deep adaptation**: inference is bound to a foreground service to prevent system killing, coroutine scheduling enables safe suspension, and arm64-v8a native optimizations such as XNNPACK improve performance.

## Challenges we ran into

- **Running full relational AI on smartphones**: Cloud-only relationship engines could not be simply ported. We had to redesign context management, memory extraction, and inference scheduling to fit within mobile thermal and battery constraints.
- **Balancing performance and power consumption**: Raw inference overheats devices and drains batteries. Implementing dynamic runtime switching between llama.cpp and LiteRT-LM required extensive profiling across device tiers.
- **Long-term memory without context bloat**: Simply stacking conversation history causes slowdowns and memory loss. Designing a dynamic context compression mechanism that preserves what matters while discarding redundancy was difficult.
- **Keeping everything offline**: Integrating ASR, TTS, image generation, and LLM inference without any cloud dependency required careful model selection, quantization, and native optimization.
- **Consistent personality across modalities**: Ensuring the same character personality is reflected in text, voice, and image generation required unified character-card metadata and role-aware routing across all subsystems.

## Accomplishments that we're proud of

- We successfully compressed cloud-grade relational AI capabilities into ordinary smartphones, proving that a true companion AI can run fully locally.
- We built a dual-runtime inference system that automatically adapts to device capabilities, optimizing both performance and thermals.
- We implemented a hierarchical memory system with short-term and long-term layers, automatic extraction, and reference-counted prioritization — the companion genuinely improves with continued use.
- We achieved complete offline operation: ASR, TTS, image generation, and conversation all run without any external server.
- We created a reusable side-AI infrastructure that can serve elderly companionship, early childhood education, mental health, in-car interaction, and more.

## What we learned

- **Privacy is a physical guarantee, not a policy promise**: Users trust local AI far more when they know data never leaves their device.
- **Power management is the real mobile barrier**: Inference accuracy alone is not enough; dynamic scheduling and idle-time background processing are essential for daily usability.
- **Memory quality determines companionship quality**: A companion that forgets is worse than no companion at all. Long-term memory design is the hardest and most important problem in relational AI.
- **Dual runtime strategy matters**: One-size-fits-all inference does not work across the fragmented Android ecosystem. Device-class-aware routing is necessary.
- **Character consistency crosses modalities**: Personality must be preserved not only in text generation but also in voice selection and image generation for true immersion.

## What's next for Anime Companion

- **Stage 1: App** — Today, Anime Companion is already fully running on phones, validating side inference, memory systems, and voice cloning in real-world use.
- **Stage 2: Terminals** — The phone becomes the computing center, while smart helmets, earphones, and glasses act as interaction terminals. The same companion system delivers different experience forms. Imagine hearing "turn left at the next intersection; you mentioned you like that coffee shop last time" while riding your bike.
- **Stage 3: Infrastructure** — The underlying relationship engine, memory system, and character runtime will be opened as infrastructure for elderly companionship, early childhood education, mental health, language learning, and in-car companions. We are not just building an App — we are defining the technology stack for side relational AI.
