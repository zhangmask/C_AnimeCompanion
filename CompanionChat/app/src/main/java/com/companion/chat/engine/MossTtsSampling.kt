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

    /** 对 logits 应用重复惩罚（原地修改，避免分配）。使用调用方预先构建的 Set，避免每次重建。 */
    fun applyRepetitionPenaltyInPlace(
        logits: FloatArray,
        previousTokenSet: Set<Int>,
        repetitionPenalty: Float
    ) {
        if (previousTokenSet.isEmpty() || repetitionPenalty == 1f) return
        for (tokenId in previousTokenSet) {
            if (tokenId < 0 || tokenId >= logits.size) continue
            logits[tokenId] = if (logits[tokenId] < 0) {
                logits[tokenId] * repetitionPenalty
            } else {
                logits[tokenId] / repetitionPenalty
            }
        }
    }

    /** 旧版：接受 List<Int>，内部转 Set。保留给 ONNX runtime 使用。 */
    fun applyRepetitionPenaltyInPlace(
        logits: FloatArray,
        previousTokenIds: List<Int>,
        repetitionPenalty: Float
    ) {
        if (previousTokenIds.isEmpty() || repetitionPenalty == 1f) return
        applyRepetitionPenaltyInPlace(logits, previousTokenIds.toSet(), repetitionPenalty)
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
     * 优化版：top-K 用 min-heap O(n·log K)，top-P 只处理 top-K 个候选，避免对全量 logits 排序。
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
        val n = logits.size
        val scores = FloatArray(n) { logits[it] / temperature }

        // Top-K filtering: use a min-heap of size K to find the K-th largest value.
        // O(n·log K) instead of O(n·log n) for full sort.
        val effectiveTopK = if (topK > 0 && topK < n) topK else 0
        if (effectiveTopK > 0) {
            val heap = java.util.PriorityQueue<Float>(effectiveTopK)
            for (s in scores) {
                if (heap.size < effectiveTopK) {
                    heap.add(s)
                } else if (s > heap.peek()) {
                    heap.poll()
                    heap.add(s)
                }
            }
            val threshold = heap.peek()!!
            for (i in 0 until n) {
                if (scores[i] < threshold) scores[i] = Float.NEGATIVE_INFINITY
            }
        }

        // Collect candidate indices (non -inf) — at most effectiveTopK elements
        val candIdx = IntArray(if (effectiveTopK > 0) effectiveTopK else n)
        val candScores = FloatArray(if (effectiveTopK > 0) effectiveTopK else n)
        var candCount = 0
        for (i in 0 until n) {
            if (scores[i] != Float.NEGATIVE_INFINITY) {
                if (candCount >= candIdx.size) {
                    // Shouldn't happen if topK worked, but guard anyway
                    break
                }
                candIdx[candCount] = i
                candScores[candCount] = scores[i]
                candCount++
            }
        }

        // Sort candidates by score descending (only candCount elements, not n)
        // Insertion sort for small K (typically 25): O(K^2) but faster than general sort for tiny K
        if (candCount > 1) {
            for (i in 1 until candCount) {
                val ti = candIdx[i]; val ts = candScores[i]
                var j = i - 1
                while (j >= 0 && candScores[j] < ts) {
                    candIdx[j + 1] = candIdx[j]; candScores[j + 1] = candScores[j]; j--
                }
                candIdx[j + 1] = ti; candScores[j + 1] = ts
            }
        }

        // Top-P (nucleus) filtering on sorted candidates only
        val keepCount: Int
        if (topP > 0 && topP < 1f && candCount > 0) {
            // Softmax over candidates
            var maxVal = Float.NEGATIVE_INFINITY
            for (i in 0 until candCount) if (candScores[i] > maxVal) maxVal = candScores[i]
            var sum = 0.0
            val exps = DoubleArray(candCount)
            for (i in 0 until candCount) {
                exps[i] = exp((candScores[i] - maxVal).toDouble())
                sum += exps[i]
            }
            var cumulative = 0.0
            var keep = candCount
            for (i in 0 until candCount) {
                cumulative += exps[i] / sum
                if (cumulative > topP) { keep = i + 1; break }
            }
            keepCount = keep
        } else {
            keepCount = candCount
        }

        // Final softmax + random draw over kept candidates
        if (keepCount <= 0) return if (candCount > 0) candIdx[0] else argmax(scores)
        var maxVal = Float.NEGATIVE_INFINITY
        for (i in 0 until keepCount) if (candScores[i] > maxVal) maxVal = candScores[i]
        var sum = 0.0
        val probs = DoubleArray(keepCount)
        for (i in 0 until keepCount) {
            probs[i] = exp((candScores[i] - maxVal).toDouble())
            sum += probs[i]
        }
        var draw = Random.nextDouble() * sum
        for (i in 0 until keepCount) {
            draw -= probs[i]
            if (draw <= 0) return candIdx[i]
        }
        return candIdx[0]
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
        android.util.Log.i("MossTtsSampling", "sampleAssistantTextToken: doSample=$doSample temp=$textTemperature asid[$assistantSlotTokenId]=${candidateScores[0]} end[$audioEndTokenId]=${candidateScores[1]} -> ${candidateIds[sampledIndex]}")
        return candidateIds[sampledIndex]
    }

    /**
     * 从 audio logits 中采样一个 audio token，应用重复惩罚。
     * 直接在 audioLogits 的 [offset, offset+vocabSize) 区间上操作，避免 copyOfRange。
     */
    fun sampleAudioToken(
        audioLogits: FloatArray,
        offset: Int,
        vocabSize: Int,
        previousTokenSet: Set<Int>,
        doSample: Boolean,
        audioRepetitionPenalty: Float,
        audioTemperature: Float,
        audioTopK: Int,
        audioTopP: Float
    ): Int {
        if (!doSample) {
            return argmaxWithRepetitionPenalty(audioLogits, offset, vocabSize, previousTokenSet, audioRepetitionPenalty)
        }
        applyRepetitionPenaltyInPlace(audioLogits, offset, vocabSize, previousTokenSet, audioRepetitionPenalty)
        return sampleFromScoresRange(audioLogits, offset, vocabSize, doSample, audioTemperature, audioTopK, audioTopP)
    }

    /** 旧版：接受独立的 audioLogits 数组 + List<Int>。保留给 ONNX runtime 使用。 */
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
        applyRepetitionPenaltyInPlace(audioLogits, previousTokenIds, audioRepetitionPenalty)
        return sampleFromScores(audioLogits, doSample, audioTemperature, audioTopK, audioTopP)
    }

    /** 带重复惩罚的 argmax，限定在 [offset, offset+size) 区间。 */
    private fun argmaxWithRepetitionPenalty(
        logits: FloatArray, offset: Int, size: Int,
        previousTokenSet: Set<Int>, repetitionPenalty: Float
    ): Int {
        var bestIndex = 0
        var bestValue = Float.NEGATIVE_INFINITY
        val applyPenalty = previousTokenSet.isNotEmpty() && repetitionPenalty != 1f
        for (i in 0 until size) {
            var score = logits[offset + i]
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

    /** 对 logits 区间 [offset, offset+size) 应用重复惩罚（原地修改）。 */
    private fun applyRepetitionPenaltyInPlace(
        logits: FloatArray, offset: Int, size: Int,
        previousTokenSet: Set<Int>, repetitionPenalty: Float
    ) {
        if (previousTokenSet.isEmpty() || repetitionPenalty == 1f) return
        for (tokenId in previousTokenSet) {
            if (tokenId < 0 || tokenId >= size) continue
            val idx = offset + tokenId
            logits[idx] = if (logits[idx] < 0) logits[idx] * repetitionPenalty else logits[idx] / repetitionPenalty
        }
    }

    /** sampleFromScores 的区间版本，避免 copyOfRange。 */
    private fun sampleFromScoresRange(
        logits: FloatArray, offset: Int, size: Int,
        doSample: Boolean, temperature: Float, topK: Int, topP: Float
    ): Int {
        if (!doSample) {
            var bestIdx = 0; var bestVal = Float.NEGATIVE_INFINITY
            for (i in 0 until size) {
                if (logits[offset + i] > bestVal) { bestVal = logits[offset + i]; bestIdx = i }
            }
            return bestIdx
        }
        require(temperature > 0)

        val n = size
        val scores = FloatArray(n) { logits[offset + it] / temperature }

        val effectiveTopK = if (topK > 0 && topK < n) topK else 0
        if (effectiveTopK > 0) {
            val heap = java.util.PriorityQueue<Float>(effectiveTopK)
            for (s in scores) {
                if (heap.size < effectiveTopK) heap.add(s)
                else if (s > heap.peek()) { heap.poll(); heap.add(s) }
            }
            val threshold = heap.peek()!!
            for (i in 0 until n) {
                if (scores[i] < threshold) scores[i] = Float.NEGATIVE_INFINITY
            }
        }

        val candIdx = IntArray(if (effectiveTopK > 0) effectiveTopK else n)
        val candScores = FloatArray(if (effectiveTopK > 0) effectiveTopK else n)
        var candCount = 0
        for (i in 0 until n) {
            if (scores[i] != Float.NEGATIVE_INFINITY) {
                if (candCount >= candIdx.size) break
                candIdx[candCount] = i
                candScores[candCount] = scores[i]
                candCount++
            }
        }

        if (candCount > 1) {
            for (i in 1 until candCount) {
                val ti = candIdx[i]; val ts = candScores[i]
                var j = i - 1
                while (j >= 0 && candScores[j] < ts) {
                    candIdx[j + 1] = candIdx[j]; candScores[j + 1] = candScores[j]; j--
                }
                candIdx[j + 1] = ti; candScores[j + 1] = ts
            }
        }

        val keepCount: Int
        if (topP > 0 && topP < 1f && candCount > 0) {
            var maxVal = Float.NEGATIVE_INFINITY
            for (i in 0 until candCount) if (candScores[i] > maxVal) maxVal = candScores[i]
            var sum = 0.0
            val exps = DoubleArray(candCount)
            for (i in 0 until candCount) { exps[i] = exp((candScores[i] - maxVal).toDouble()); sum += exps[i] }
            var cumulative = 0.0
            var keep = candCount
            for (i in 0 until candCount) {
                cumulative += exps[i] / sum
                if (cumulative > topP) { keep = i + 1; break }
            }
            keepCount = keep
        } else {
            keepCount = candCount
        }

        if (keepCount <= 0) return if (candCount > 0) candIdx[0] else 0
        var maxVal = Float.NEGATIVE_INFINITY
        for (i in 0 until keepCount) if (candScores[i] > maxVal) maxVal = candScores[i]
        var sum = 0.0
        val probs = DoubleArray(keepCount)
        for (i in 0 until keepCount) { probs[i] = exp((candScores[i] - maxVal).toDouble()); sum += probs[i] }
        var draw = Random.nextDouble() * sum
        for (i in 0 until keepCount) {
            draw -= probs[i]
            if (draw <= 0) return candIdx[i]
        }
        return candIdx[0]
    }
}
