# Model Engines Are Technical Adapters

Inference, ASR, TTS, and image generation engines are technical ports and adapters, not core domain objects. The domain should name user-facing capability channels such as **Voice Interaction** and **Image Generation**, while implementations like LiteRT-LM, llama.cpp, SenseVoice, MOSS TTS, Stable Diffusion, and HTTP providers remain swappable infrastructure.
