package com.companion.chat.engine

import kotlin.math.exp
import kotlin.random.Random

/**
 * MOSS-TTS-Nano 采样与重复惩罚工具。
 * 移植自 browser_onnx_runtime.js 的 sampleFromScores / applyRepetitionPenalty 等。
 */
object MossTtsSampling {

    /** Greedy argmax。 */
    fun argmax(values: FloatArray): Int {
        var bestIndex = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in values.indices) {
            if (values[i] > bestValue) {
                bestValue = values[i]
                bestIndex = i
            }
        }
        return bestIndex
    }

    /** 带重复惩罚的 argmax。 */
    fun argmaxWithRepetitionPenalty(
        logits: FloatArray,
        previousTokenSet: Set<Int>,
        repetitionPenalty: Float
    ): Int {
        var bestIndex = 0
        var bestValue = Float.NEGATIVE_INFINITY
        val applyPenalty = previousTokenSet.isNotEmpty() && repetitionPenalty != 1f
        for (i in logits.indices) {
            var score = logits[i]
            if (applyPenalty && i in previousTokenSet) {
                score = if (score < 0) score * repetitionPenalty else score / repetitionPenalty
            }
            if (score > bestValue) {
                bestValue = score
                bestIndex = i
            }
        }
        return bestIndex
    }

    /** 对 logits 应用重复惩罚（返回新数组）。 */
    fun applyRepetitionPenalty(
        logits: FloatArray,
        previousTokenIds: List<Int>,
        repetitionPenalty: Float
    ): FloatArray {
        if (previousTokenIds.isEmpty() || repetitionPenalty == 1f) return logits.copyOf()
        val result = logits.copyOf()
        val uniqueIds = previousTokenIds.toSet()
        for (tokenId in uniqueIds) {
            if (tokenId < 0 || tokenId >= result.size) continue
            result[tokenId] = if (result[tokenId] < 0) {
                result[tokenId] * repetitionPenalty
            } else {
                result[tokenId] / repetitionPenalty
            }
        }
        return result
    }

    /** Softmax 归一化。 */
    fun softmax(values: FloatArray): DoubleArray {
        var maxVal = Float.NEGATIVE_INFINITY
        for (v in values) if (v > maxVal) maxVal = v
        val exps = DoubleArray(values.size)
        var sum = 0.0
        for (i in values.indices) {
            val e = exp((values[i] - maxVal).toDouble())
            exps[i] = e
            sum += e
        }
        for (i in exps.indices) exps[i] /= sum
        return exps
    }

    /**
     * 通用采样函数：支持 temperature、top-k、top-p。
     * 如果 doSample=false，使用 argmax。
     */
    fun sampleFromScores(
        logits: FloatArray,
        doSample: Boolean,
        temperature: Float = 1.0f,
        topK: Int = 0,
        topP: Float = 0f
    ): Int {
        if (!doSample) return argmax(logits)
        require(temperature > 0) { "temperature 必须为正数" }

        // Temperature scaling
        val scores = FloatArray(logits.size) { logits[it] / temperature }

        // Top-K filtering
        if (topK > 0 && topK < scores.size) {
            val sorted = scores.sortedDescending()
            val threshold = sorted[topK - 1]
            for (i in scores.indices) {
                if (scores[i] < threshold) scores[i] = Float.NEGATIVE_INFINITY
            }
        }

        // Top-P (nucleus) filtering
        if (topP > 0 && topP < 1f) {
            val indexed = scores.mapIndexed { i, s -> i to s }.sortedByDescending { it.second }
            val sortedScores = FloatArray(indexed.size) { indexed[it].second }
            val sortedProbs = softmax(sortedScores)
            val removeMask = BooleanArray(indexed.size)
            var cumulative = 0.0
            for (i in indexed.indices) {
                cumulative += sortedProbs[i]
                if (cumulative > topP) removeMask[i] = true
            }
            // Shift mask right by 1 (keep the first token that exceeds topP)
            for (i in removeMask.size - 1 downTo 1) {
                removeMask[i] = removeMask[i - 1]
            }
            if (removeMask.isNotEmpty()) removeMask[0] = false
            for (i in indexed.indices) {
                if (removeMask[i]) scores[indexed[i].first] = Float.NEGATIVE_INFINITY
            }
        }

        // Final softmax + random draw
        val probs = softmax(scores)
        var draw = Random.nextDouble()
        for (i in probs.indices) {
            draw -= probs[i]
            if (draw <= 0) return i
        }
        return argmax(scores)
    }

    /**
     * 从 text logits 中采样 assistant text token。
     * 只在 [assistantSlotTokenId, audioEndTokenId] 两个候选中选。
     */
    fun sampleAssistantTextToken(
        textLogits: FloatArray,
        assistantSlotTokenId: Int,
        audioEndTokenId: Int,
        doSample: Boolean,
        textTemperature: Float,
        textTopK: Int,
        textTopP: Float
    ): Int {
        val candidateIds = intArrayOf(assistantSlotTokenId, audioEndTokenId)
        val candidateScores = floatArrayOf(
            if (assistantSlotTokenId < textLogits.size) textLogits[assistantSlotTokenId] else Float.NEGATIVE_INFINITY,
            if (audioEndTokenId < textLogits.size) textLogits[audioEndTokenId] else Float.NEGATIVE_INFINITY
        )
        val sampledIndex = sampleFromScores(
            candidateScores, doSample, textTemperature,
            minOf(textTopK, candidateScores.size), textTopP
        )
        return candidateIds[sampledIndex]
    }

    /**
     * 从 audio logits 中采样一个 audio token，应用重复惩罚。
     */
    fun sampleAudioToken(
        audioLogits: FloatArray,
        previousTokenIds: List<Int>,
        previousTokenSet: Set<Int>,
        doSample: Boolean,
        audioRepetitionPenalty: Float,
        audioTemperature: Float,
        audioTopK: Int,
        audioTopP: Float
    ): Int {
        if (!doSample) {
            return argmaxWithRepetitionPenalty(audioLogits, previousTokenSet, audioRepetitionPenalty)
        }
        val penalized = applyRepetitionPenalty(audioLogits, previousTokenIds, audioRepetitionPenalty)
        return sampleFromScores(penalized, doSample, audioTemperature, audioTopK, audioTopP)
    }
}
