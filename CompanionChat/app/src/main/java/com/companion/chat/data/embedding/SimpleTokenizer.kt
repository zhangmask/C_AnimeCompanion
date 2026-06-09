package com.companion.chat.data.embedding

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 简化的 BPE Tokenizer
 * 支持中文和英文分词
 */
class SimpleTokenizer(private val context: Context) {

    private var vocab: Map<String, Int> = emptyMap()
    private var invVocab: Map<Int, String> = emptyMap()
    private val unkTokenId = 100  // [UNK]
    private val clsTokenId = 101  // [CLS]
    private val sepTokenId = 102  // [SEP]
    private val padTokenId = 0    // [PAD]

    /**
     * 加载词表文件
     */
    fun loadVocab(vocabPath: String) {
        val vocabMap = mutableMapOf<String, Int>()
        try {
            context.assets.open(vocabPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var index = 0
                    reader.forEachLine { line ->
                        vocabMap[line.trim()] = index
                        index++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vocab = vocabMap
        invVocab = vocabMap.entries.associate { (k, v) -> v to k }
    }

    /**
     * 对文本进行 tokenize
     * @return token ID 列表
     */
    fun tokenize(text: String, maxLength: Int = 512): List<Int> {
        if (vocab.isEmpty()) return emptyList()

        val tokens = mutableListOf<Int>()
        tokens.add(clsTokenId)  // 开始标记

        // 简化的分词逻辑：按字符分割，查找词表
        val chars = text.toCharArray()
        var i = 0
        while (i < chars.size && tokens.size < maxLength - 1) {
            val char = chars[i]
            val charStr = char.toString()

            // 尝试匹配更长的词
            var matched = false
            for (len in 4 downTo 2) {
                if (i + len <= chars.size) {
                    val word = String(chars, i, len).lowercase()
                    val id = vocab[word]
                    if (id != null) {
                        tokens.add(id)
                        i += len
                        matched = true
                        break
                    }
                }
            }

            if (!matched) {
                // 单字符匹配
                val id = vocab[charStr.lowercase()]
                if (id != null) {
                    tokens.add(id)
                } else {
                    // 尝试添加 ## 前缀（BERT 风格）
                    val hashId = vocab["##${charStr.lowercase()}"]
                    if (hashId != null) {
                        tokens.add(hashId)
                    } else {
                        tokens.add(unkTokenId)
                    }
                }
                i++
            }
        }

        tokens.add(sepTokenId)  // 结束标记

        // 填充到 maxLength
        while (tokens.size < maxLength) {
            tokens.add(padTokenId)
        }

        return tokens.take(maxLength)
    }

    /**
     * 获取词表大小
     */
    fun vocabSize(): Int = vocab.size
}
