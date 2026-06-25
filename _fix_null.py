path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\data\memory\MemoryRepository.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()
# Fix embedding null safety
c = c.replace(
    '            val embedding = embeddingEngine(content)\n            for (memory in existingMemories) {\n                val existingEmbedding = embeddingEngine(memory.content)\n                val similarity = cosineSimilarity(embedding, existingEmbedding)',
    '            val embedding = embeddingEngine(content) ?: return null\n            for (memory in existingMemories) {\n                val existingEmbedding = embeddingEngine(memory.content) ?: continue\n                val similarity = cosineSimilarity(embedding, existingEmbedding)')
with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('Done')
