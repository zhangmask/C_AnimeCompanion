package com.companion.chat.engine

import android.util.Log

object MossMnnJni {
    private const val TAG = "MossMnnJni"
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            try {
                System.loadLibrary("MNN")
                System.loadLibrary("mnn_jni")
                loaded = true
                Log.i(TAG, "MNN JNI loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load MNN: ${e.message}")
            }
        }
    }

    fun isLoaded(): Boolean = loaded

    // Create inference session (auto: Vulkan→CPU)
    external fun nativeCreateSession(modelPath: String, numThread: Int): Long

    // Create session with explicit backend (0=CPU, 3=OpenCL, 7=Vulkan)
    external fun nativeCreateSession2(modelPath: String, numThread: Int, backendType: Int): Long

    // Create session with explicit backend + precision (0=Normal/FP16, 1=High/FP32, 2=Low/BF16)
    external fun nativeCreateSession3(modelPath: String, numThread: Int, backendType: Int, precision: Int): Long

    // Create CPU-only session (for codec models with subgraphs)
    external fun nativeCreateSessionCpu(modelPath: String, numThread: Int): Long

    // Express session for subgraph models
    external fun nativeCreateSessionExpress(modelPath: String, inputNames: Array<String>, outputNames: Array<String>): Long
    external fun nativeCreateSessionExpress2(modelPath: String, inputNames: Array<String>, outputNames: Array<String>, backendType: Int, dynamic: Boolean): Long
    external fun nativeCreateSessionExpress3(modelPath: String, inputNames: Array<String>, outputNames: Array<String>, backendType: Int, dynamic: Boolean, precision: Int): Long
    external fun nativeReleaseSessionExpress(handlePtr: Long)
    external fun nativeRunExpress(
        handlePtr: Long,
        inputNames: Array<String>,
        inputTensors: Array<FloatArray>,
        inputDims: LongArray,
        inputDimOffsets: IntArray,
        outputNames: Array<String>
    ): Array<FloatArray>?

    /**
     * Run inference with explicit input dtypes.
     * inputTypes: 0=float32, 1=int32. Data is still passed as float[] for JNI simplicity;
     * int32 inputs are cast to int32 values inside native code.
     */
    external fun nativeRunExpressTyped(
        handlePtr: Long,
        inputNames: Array<String>,
        inputTensors: Array<FloatArray>,
        inputDims: LongArray,
        inputDimOffsets: IntArray,
        inputTypes: IntArray,
        outputNames: Array<String>
    ): Array<FloatArray>?

    // Release session
    external fun nativeReleaseSession(handlePtr: Long)

    // Run inference: multi-input → multi-output
    // inputNames: names of input tensors
    // inputTensors: float data for each input
    // inputDims: flattened dims of all inputs concatenated
    // inputDimOffsets: [start0, len0, start1, len1, ...] offsets into inputDims
    // outputNames: names of outputs to read
    // Returns: float[][] matching outputNames order
    external fun nativeRun(
        handlePtr: Long,
        inputNames: Array<String>,
        inputTensors: Array<FloatArray>,
        inputDims: LongArray,
        inputDimOffsets: IntArray,
        outputNames: Array<String>
    ): Array<FloatArray>?

    // Get input/output tensor names
    external fun nativeGetInputNames(handlePtr: Long): Array<String>?
    external fun nativeGetOutputNames(handlePtr: Long): Array<String>?

    // Get output tensor shape
    external fun nativeGetOutputShape(handlePtr: Long, outputName: String): LongArray?

    // Set verbose flag (false=quiet for tight loops, true=verbose for debugging)
    external fun nativeSetVerbose(verbose: Boolean)

    // Log and reset accumulated per-call timing stats for a session
    external fun nativeLogTiming(handlePtr: Long, tag: String)

    /**
     * JNI 层一体化帧生成：将 Kotlin 层每帧 64ms 的 18 次 JNI 调用 + 采样 + KV 管理移入 C++。
     * 采样逻辑与 MossTtsSampling.kt 完全一致。
     * 返回 int[][] (每帧的 nq 个 audio token)，或 null 表示出错。
     */
    external fun nativeGenerateAudioFrames(
        localCachedStepHandle: Long,
        decodeStepHandle: Long,
        initialH: FloatArray,
        initialKv: Array<FloatArray>,
        initialPvl: Int,
        maxFrames: Int,
        nq: Int,
        asid: Int,
        audioEndTokenId: Int,
        audioPadTokenId: Int,
        audioAssistantSlotTokenId: Int,
        localHeads: Int,
        localHeadDim: Int,
        globalHeads: Int,
        globalHeadDim: Int,
        doSample: Boolean,
        textTemp: Float,
        textTopK: Int,
        textTopP: Float,
        audioTemp: Float,
        audioTopK: Int,
        audioTopP: Float,
        audioRep: Float
    ): Array<IntArray>?
}
