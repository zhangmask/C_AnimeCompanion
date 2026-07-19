#include <jni.h>
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <MNN/ErrorCode.hpp>
#include <MNN/expr/Module.hpp>
#include <MNN/expr/ExprCreator.hpp>
#include <MNN/expr/Executor.hpp>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>
#include <cmath>
#include <unordered_map>
#include <time.h>
#include <sched.h>
#include <sys/syscall.h>
#include <unistd.h>

#define TAG "MNN_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Bind current thread to big cores (CPU 4-7: 3×A720 @3.5GHz + 1×X4 @4.21GHz on 天玑9500).
// MNN's Power_High mode also sets affinity on its thread pool workers, but this
// guarantees the JNI calling thread itself runs on big cores too. Uses thread_local
// to call sched_setaffinity only once per thread (syscall ~1μs, negligible).
static thread_local bool t_boundToBigCores = false;
static void bindToBigCoresIfNeeded() {
    if (t_boundToBigCores) return;
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(4, &cpuset);
    CPU_SET(5, &cpuset);
    CPU_SET(6, &cpuset);
    CPU_SET(7, &cpuset);
    int ret = sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
    LOGI("MNN: bindToBigCores CPU4-7 ret=%d tid=%d", ret, (int)gettid());
    t_boundToBigCores = true;
}

// Verbose flag: when false, skip per-call LOGI in nativeRun to reduce logcat I/O
// overhead during tight loops (e.g. localCachedStep called 680+ times).
static bool g_verbose = true;

// High-resolution timer for profiling JNI phases
static inline double now_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec * 1000.0 + (double)ts.tv_nsec / 1e6;
}

struct TimingStats {
    double phase1 = 0;  // input name/dims/data collection + shape cache check
    double phase2 = 0;  // resizeSession
    double phase3 = 0;  // input data copy to tensors
    double runSess = 0; // runSession (model compute + framework)
    double output = 0;  // output tensor read + Java array creation
    long count = 0;
    void reset() { phase1 = phase2 = phase3 = runSess = output = 0; count = 0; }
    void log(const char* tag) const {
        if (count == 0) return;
        LOGI("TIMING[%s] calls=%ld p1=%.1f p2=%.1f p3=%.1f run=%.1f out=%.1f total/call=%.2fms",
             tag, count, phase1/count, phase2/count, phase3/count, runSess/count, output/count,
             (phase1+phase2+phase3+runSess+output)/count);
    }
};

struct MnnSessionHandle {
    MNN::Interpreter* interpreter;
    MNN::Session* session;
    // Shape cache: record last-seen dims for each input to skip resizeSession
    // when shapes are unchanged (prefill/decodeStep have stable shapes).
    std::unordered_map<std::string, std::vector<int>> lastInputShapes;
    // Max shape cache: track the maximum shape ever seen for each input.
    // If current shape ≤ max shape, the existing allocation is sufficient —
    // we can skip resizeSession (the expensive memory reallocation + kernel recompile).
    // This is critical for localCachedStep where KV cache grows by 1 each call (185 calls).
    std::unordered_map<std::string, std::vector<int>> maxInputShapes;
    bool shapeCacheValid = false;
    TimingStats timing;
};

// Set verbose flag (0=false=quiet, 1=true=verbose). Call before tight loops.
extern "C" JNIEXPORT void JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeSetVerbose(
    JNIEnv*, jobject, jboolean verbose) {
    g_verbose = verbose == JNI_TRUE;
    __android_log_print(ANDROID_LOG_INFO, TAG, "verbose=%d", g_verbose ? 1 : 0);
}

// Log and reset accumulated timing stats for a session
extern "C" JNIEXPORT void JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeLogTiming(
    JNIEnv* env, jobject, jlong handlePtr, jstring tagStr) {
    auto* h = reinterpret_cast<MnnSessionHandle*>(handlePtr);
    if (!h) return;
    const char* tag = tagStr ? env->GetStringUTFChars(tagStr, nullptr) : "session";
    h->timing.log(tag);
    h->timing.reset();
    if (tagStr) env->ReleaseStringUTFChars(tagStr, tag);
}

// Express handle for subgraph models (codec)
struct MnnExpressHandle {
    MNN::Express::Module* module;
};

// ============================================================
// createSessionExpress — uses Module::load for subgraph models
// backendType: 0=CPU, 3=OpenCL, 6=OpenGL, 7=Vulkan (see MNNForwardType.h)
// dynamic: true for models with control-flow / dynamic shapes
// ============================================================
static MNN::BackendConfig::PrecisionMode precisionFromInt(jint precision) {
    switch (precision) {
        case 0: return MNN::BackendConfig::PrecisionMode::Precision_Normal;
        case 2: return MNN::BackendConfig::PrecisionMode::Precision_Low;
        default: return MNN::BackendConfig::PrecisionMode::Precision_High;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeCreateSessionExpress3(
    JNIEnv* env, jobject, jstring modelPath, jobjectArray inputNames, jobjectArray outputNames,
    jint backendType, jboolean dynamic, jint precision) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("MNN Express: load %s backend=%d dynamic=%d precision=%d", path, backendType, dynamic ? 1 : 0, precision);

    jsize numInputs = inputNames ? env->GetArrayLength(inputNames) : 0;
    jsize numOutputs = outputNames ? env->GetArrayLength(outputNames) : 0;
    std::vector<std::string> inputs, outputs;
    std::vector<const char*> inputPtrs, outputPtrs;
    for (int i = 0; i < numInputs; i++) {
        auto jname = (jstring)env->GetObjectArrayElement(inputNames, i);
        const char* cname = env->GetStringUTFChars(jname, nullptr);
        inputPtrs.push_back(cname);
        inputs.emplace_back(cname);
    }
    for (int i = 0; i < numOutputs; i++) {
        auto jname = (jstring)env->GetObjectArrayElement(outputNames, i);
        const char* cname = env->GetStringUTFChars(jname, nullptr);
        outputPtrs.push_back(cname);
        outputs.emplace_back(cname);
    }

    auto precisionMode = precisionFromInt(precision);

    // 为 Express API（codec decode_step / decode_full 等 subgraph 模型）设置全局线程数
    static bool expressExecutorConfigured = false;
    if (!expressExecutorConfigured) {
        MNN::BackendConfig globalCfg;
        globalCfg.memory = MNN::BackendConfig::MemoryMode::Memory_High;
        globalCfg.power = MNN::BackendConfig::PowerMode::Power_High;
        globalCfg.precision = precisionMode;
        MNN::Express::Executor::getGlobalExecutor()->setGlobalExecutorConfig(
            static_cast<MNNForwardType>(backendType), globalCfg, 8);
        expressExecutorConfigured = true;
        LOGI("MNN Express: setGlobalExecutorConfig threads=8 backend=%d precision=%d", backendType, precision);
    }

    MNN::BackendConfig backendCfg;
    backendCfg.memory = MNN::BackendConfig::MemoryMode::Memory_High;
    backendCfg.power = MNN::BackendConfig::PowerMode::Power_High;
    backendCfg.precision = precisionMode;

    MNN::Express::Module::BackendInfo backendInfo;
    backendInfo.type = static_cast<MNNForwardType>(backendType);
    backendInfo.config = &backendCfg;

    MNN::Express::Module::Config cfg;
    cfg.dynamic = dynamic;
    cfg.shapeMutable = true;
    cfg.backend = &backendInfo;

    bindToBigCoresIfNeeded();
    auto mod = MNN::Express::Module::load(inputs, outputs, path, &cfg);

    for (int i = 0; i < numInputs; i++) {
        auto jname = (jstring)env->GetObjectArrayElement(inputNames, i);
        env->ReleaseStringUTFChars(jname, inputPtrs[i]);
        env->DeleteLocalRef(jname);
    }
    for (int i = 0; i < numOutputs; i++) {
        auto jname = (jstring)env->GetObjectArrayElement(outputNames, i);
        env->ReleaseStringUTFChars(jname, outputPtrs[i]);
        env->DeleteLocalRef(jname);
    }

    env->ReleaseStringUTFChars(modelPath, path);
    if (!mod) { LOGE("Module::load failed"); return 0; }
    return reinterpret_cast<jlong>(new MnnExpressHandle{mod});
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeCreateSessionExpress2(
    JNIEnv* env, jobject obj, jstring modelPath, jobjectArray inputNames, jobjectArray outputNames,
    jint backendType, jboolean dynamic) {
    return Java_com_companion_chat_engine_MossMnnJni_nativeCreateSessionExpress3(
        env, obj, modelPath, inputNames, outputNames, backendType, dynamic, 1);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeCreateSessionExpress(
    JNIEnv* env, jobject obj, jstring modelPath, jobjectArray inputNames, jobjectArray outputNames) {
    return Java_com_companion_chat_engine_MossMnnJni_nativeCreateSessionExpress3(
        env, obj, modelPath, inputNames, outputNames, 0, JNI_TRUE, 1);
}

extern "C" JNIEXPORT void JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeReleaseSessionExpress(
    JNIEnv*, jobject, jlong ptr) {
    delete reinterpret_cast<MnnExpressHandle*>(ptr);
}

// ============================================================
// nativeRunExpress — runs inference using MNN Express Module
// ============================================================
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeRunExpress(
    JNIEnv* env, jobject,
    jlong handlePtr,
    jobjectArray inputNames,
    jobjectArray inputTensors,
    jlongArray inputDims,
    jintArray inputDimOffsets,
    jobjectArray outputNames) {

    auto* h = reinterpret_cast<MnnExpressHandle*>(handlePtr);
    if (!h || !h->module) return nullptr;

    bindToBigCoresIfNeeded();

    jsize numInputs = inputNames ? env->GetArrayLength(inputNames) : 0;
    jsize numOutputs = outputNames ? env->GetArrayLength(outputNames) : 0;

    // Build input VARP list
    std::vector<MNN::Express::VARP> inputs;
    jlong* allDims = env->GetLongArrayElements(inputDims, nullptr);
    jint* offsets = env->GetIntArrayElements(inputDimOffsets, nullptr);
    for (int i = 0; i < numInputs; i++) {
        auto* dataArr = (jfloatArray)env->GetObjectArrayElement(inputTensors, i);
        jfloat* data = env->GetFloatArrayElements(dataArr, nullptr);
        jsize dataLen = env->GetArrayLength(dataArr);

        int start = offsets[i * 2];
        int len = offsets[i * 2 + 1];
        std::vector<int> dims(len);
        for (int d = 0; d < len; d++) dims[d] = static_cast<int>(allDims[start + d]);

        // Create input variable using _Input
        auto var = MNN::Express::_Input(dims, MNN::Express::NCHW, halide_type_of<float>());
        auto* varData = var->writeMap<float>();
        memcpy(varData, data, dataLen * sizeof(float));
        var->unMap();
        inputs.push_back(var);

        env->ReleaseFloatArrayElements(dataArr, data, JNI_ABORT);
        env->DeleteLocalRef(dataArr);
    }
    env->ReleaseLongArrayElements(inputDims, allDims, JNI_ABORT);
    env->ReleaseIntArrayElements(inputDimOffsets, offsets, JNI_ABORT);

    // Run forward
    auto outputs = h->module->onForward(inputs);

    // Build result array
    jclass floatArrClass = env->FindClass("[F");
    auto result = env->NewObjectArray(numOutputs, floatArrClass, nullptr);

    for (int i = 0; i < numOutputs && i < (int)outputs.size(); i++) {
        auto outData = outputs[i]->readMap<float>();
        auto outInfo = outputs[i]->getInfo();
        if (!outData || !outInfo) {
            env->SetObjectArrayElement(result, i, nullptr);
            continue;
        }
        int outSize = 1;
        for (auto d : outInfo->dim) outSize *= d;
        auto jarr = env->NewFloatArray(outSize);
        env->SetFloatArrayRegion(jarr, 0, outSize, const_cast<float*>(outData));
        env->SetObjectArrayElement(result, i, jarr);
        env->DeleteLocalRef(jarr);
    }

    env->DeleteLocalRef(floatArrClass);
    return result;
}

// ============================================================
// nativeRunExpressTyped — supports int32 inputs/outputs
// ============================================================
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeRunExpressTyped(
    JNIEnv* env, jobject,
    jlong handlePtr,
    jobjectArray inputNames,
    jobjectArray inputTensors,
    jlongArray inputDims,
    jintArray inputDimOffsets,
    jintArray inputTypes,
    jobjectArray outputNames) {

    auto* h = reinterpret_cast<MnnExpressHandle*>(handlePtr);
    if (!h || !h->module) return nullptr;

    bindToBigCoresIfNeeded();

    jsize numInputs = inputNames ? env->GetArrayLength(inputNames) : 0;
    jsize numOutputs = outputNames ? env->GetArrayLength(outputNames) : 0;

    // Build input VARP list
    std::vector<MNN::Express::VARP> inputs;
    jlong* allDims = env->GetLongArrayElements(inputDims, nullptr);
    jint* offsets = env->GetIntArrayElements(inputDimOffsets, nullptr);
    jint* types = env->GetIntArrayElements(inputTypes, nullptr);
    for (int i = 0; i < numInputs; i++) {
        auto* dataArr = (jfloatArray)env->GetObjectArrayElement(inputTensors, i);
        jfloat* data = env->GetFloatArrayElements(dataArr, nullptr);
        jsize dataLen = env->GetArrayLength(dataArr);

        int start = offsets[i * 2];
        int len = offsets[i * 2 + 1];
        int type = types[i];
        std::vector<int> dims(len);
        for (int d = 0; d < len; d++) dims[d] = static_cast<int>(allDims[start + d]);

        {
            std::string dimStr;
            for (size_t j = 0; j < dims.size(); j++) {
                if (j) dimStr += ",";
                dimStr += std::to_string(dims[j]);
            }
            LOGI("MNN ExpressTyped input[%d] type=%s dims=[%s] dataLen=%d",
                 i, type == 1 ? "int32" : "float32", dimStr.c_str(), (int)dataLen);
        }

        if (type == 1) {
            auto var = MNN::Express::_Input(dims, MNN::Express::NCHW, halide_type_of<int32_t>());
            auto* varData = var->writeMap<int32_t>();
            int varSize = var->getInfo() ? var->getInfo()->size : 1;
            for (int j = 0; j < static_cast<int>(dataLen) && j < varSize; j++) {
                varData[j] = static_cast<int32_t>(data[j]);
            }
            var->unMap();
            inputs.push_back(var);
        } else {
            auto var = MNN::Express::_Input(dims, MNN::Express::NCHW, halide_type_of<float>());
            auto* varData = var->writeMap<float>();
            memcpy(varData, data, dataLen * sizeof(float));
            var->unMap();
            inputs.push_back(var);
        }

        env->ReleaseFloatArrayElements(dataArr, data, JNI_ABORT);
        env->DeleteLocalRef(dataArr);
    }
    env->ReleaseLongArrayElements(inputDims, allDims, JNI_ABORT);
    env->ReleaseIntArrayElements(inputDimOffsets, offsets, JNI_ABORT);
    env->ReleaseIntArrayElements(inputTypes, types, JNI_ABORT);

    // Run forward
    auto outputs = h->module->onForward(inputs);
    LOGI("MNN ExpressTyped forward outputs=%d requested=%d", (int)outputs.size(), (int)numOutputs);

    // Build result array
    jclass floatArrClass = env->FindClass("[F");
    auto result = env->NewObjectArray(numOutputs, floatArrClass, nullptr);

    for (int i = 0; i < numOutputs && i < (int)outputs.size(); i++) {
        auto outInfo = outputs[i]->getInfo();
        if (!outInfo) {
            env->SetObjectArrayElement(result, i, nullptr);
            continue;
        }
        int outSize = 1;
        for (auto d : outInfo->dim) outSize *= d;
        auto jarr = env->NewFloatArray(outSize);

        if (outInfo->type == halide_type_of<int32_t>()) {
            auto outData = outputs[i]->readMap<int32_t>();
            if (outData) {
                std::vector<float> floatData(outSize);
                for (int j = 0; j < outSize; j++) floatData[j] = static_cast<float>(outData[j]);
                env->SetFloatArrayRegion(jarr, 0, outSize, floatData.data());
                LOGI("MNN ExpressTyped output[%d] int32 -> float size=%d", i, outSize);
            }
        } else {
            auto outData = outputs[i]->readMap<float>();
            if (outData) {
                env->SetFloatArrayRegion(jarr, 0, outSize, const_cast<float*>(outData));
                LOGI("MNN ExpressTyped output[%d] float size=%d", i, outSize);
            }
        }
        env->SetObjectArrayElement(result, i, jarr);
        env->DeleteLocalRef(jarr);
    }

    env->DeleteLocalRef(floatArrClass);
    return result;
}

// ============================================================
// createSession
// ============================================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeCreateSessionCpu(
    JNIEnv* env, jobject, jstring modelPath, jint numThread) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("MNN: load %s threads=%d (CPU forced)", path, numThread);

    auto* interp = MNN::Interpreter::createFromFile(path);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!interp) { LOGE("createFromFile failed"); return 0; }

    MNN::BackendConfig backendCfg;
    backendCfg.memory = MNN::BackendConfig::MemoryMode::Memory_High;
    backendCfg.power = MNN::BackendConfig::PowerMode::Power_High;
    backendCfg.precision = MNN::BackendConfig::PrecisionMode::Precision_High;

    MNN::ScheduleConfig cfg;
    cfg.numThread = numThread;
    cfg.type = MNN_FORWARD_CPU;
    cfg.backendConfig = &backendCfg;

    auto* sess = interp->createSession(cfg);
    if (!sess) { LOGE("createSession(CPU) failed"); delete interp; return 0; }

    return reinterpret_cast<jlong>(new MnnSessionHandle{interp, sess});
}

// ============================================================
// createSession2 — supports configurable backend (0=CPU, 7=Vulkan, 3=OpenCL)
// Precision_High forces FP32 on Vulkan (mUseFP16 = precision != Precision_High)
// ============================================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeCreateSession2(
    JNIEnv* env, jobject, jstring modelPath, jint numThread, jint backendType) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("MNN: load %s threads=%d backend=%d", path, numThread, backendType);

    auto* interp = MNN::Interpreter::createFromFile(path);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!interp) { LOGE("createFromFile failed"); return 0; }

    MNN::BackendConfig backendCfg;
    backendCfg.memory = MNN::BackendConfig::MemoryMode::Memory_High;
    backendCfg.power = MNN::BackendConfig::PowerMode::Power_High;
    // INT8 models: Precision_High ensures CPUBackend with INT8 Conv kernels + FP32 accumulation
    backendCfg.precision = MNN::BackendConfig::PrecisionMode::Precision_High;

    MNN::ScheduleConfig cfg;
    cfg.numThread = numThread;
    cfg.type = static_cast<MNNForwardType>(backendType);
    cfg.backendConfig = &backendCfg;
    LOGI("MNN: creating session backend=%d threads=%d precision=High(INT8/FP32)", backendType, numThread);

    bindToBigCoresIfNeeded();
    auto* sess = interp->createSession(cfg);
    if (!sess) {
        LOGE("createSession failed (backend=%d), falling back to CPU", backendType);
        cfg.type = MNN_FORWARD_CPU;
        sess = interp->createSession(cfg);
        if (!sess) { LOGE("CPU fallback also failed"); delete interp; return 0; }
    }

    return reinterpret_cast<jlong>(new MnnSessionHandle{interp, sess});
}

// Legacy: CPU-only (delegates to createSession2 with backend=0)
extern "C" JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeCreateSession(
    JNIEnv* env, jobject obj, jstring modelPath, jint numThread) {
    return Java_com_companion_chat_engine_MossMnnJni_nativeCreateSession2(
        env, obj, modelPath, numThread, 0);
}

// ============================================================
// createSession3 — supports configurable backend + precision
// precision: 0=Normal, 1=High(FP32/INT8 CPUBackend), 2=Low(FP16 GPU/Arm82Backend)
// ============================================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeCreateSession3(
    JNIEnv* env, jobject, jstring modelPath, jint numThread, jint backendType, jint precision) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("MNN: load %s threads=%d backend=%d precision=%d", path, numThread, backendType, precision);

    auto* interp = MNN::Interpreter::createFromFile(path);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!interp) { LOGE("createFromFile failed"); return 0; }

    MNN::BackendConfig backendCfg;
    backendCfg.memory = MNN::BackendConfig::MemoryMode::Memory_High;
    backendCfg.power = MNN::BackendConfig::PowerMode::Power_High;
    switch (precision) {
        case 0: backendCfg.precision = MNN::BackendConfig::PrecisionMode::Precision_Normal; break;
        case 1: backendCfg.precision = MNN::BackendConfig::PrecisionMode::Precision_High; break;
        case 2: backendCfg.precision = MNN::BackendConfig::PrecisionMode::Precision_Low; break;
        default: backendCfg.precision = MNN::BackendConfig::PrecisionMode::Precision_High; break;
    }

    MNN::ScheduleConfig cfg;
    cfg.numThread = numThread;
    cfg.type = static_cast<MNNForwardType>(backendType);
    cfg.backendConfig = &backendCfg;
    LOGI("MNN: creating session backend=%d threads=%d precision=%d", backendType, numThread, precision);

    bindToBigCoresIfNeeded();
    auto* sess = interp->createSession(cfg);
    if (!sess) {
        LOGE("createSession failed (backend=%d), falling back to CPU", backendType);
        cfg.type = MNN_FORWARD_CPU;
        sess = interp->createSession(cfg);
        if (!sess) { LOGE("CPU fallback also failed"); delete interp; return 0; }
    }

    return reinterpret_cast<jlong>(new MnnSessionHandle{interp, sess});
}

// ============================================================
// releaseSession
// ============================================================
extern "C" JNIEXPORT void JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeReleaseSession(
    JNIEnv*, jobject, jlong ptr) {
    auto* h = reinterpret_cast<MnnSessionHandle*>(ptr);
    if (!h) return;
    h->interpreter->releaseSession(h->session);
    delete h->interpreter;
    delete h;
    LOGI("MNN: released");
}

// ============================================================
// runSession: run inference with multiple inputs, read multiple outputs
// Input comes as two parallel arrays of tensor data + tensor metadata.
// Output is returned as float[][] (one float[] per requested output).
// ============================================================
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeRun(
    JNIEnv* env, jobject,
    jlong handlePtr,
    jobjectArray inputNames,    // String[] of input names
    jobjectArray inputTensors,  // float[][] of input data (all float32)
    jlongArray inputDims,       // flattened dims for each input: [d0,d1,...] for input0, [d0,d1,...] for input1, ...
    jintArray inputDimOffsets,  // start+len pairs: [start0,len0, start1,len1, ...]
    jobjectArray outputNames) { // String[] of output names to read

    auto* h = reinterpret_cast<MnnSessionHandle*>(handlePtr);
    if (!h || !h->session) return nullptr;

    bindToBigCoresIfNeeded();

    double t0 = now_ms();

    jsize numInputs = inputNames ? env->GetArrayLength(inputNames) : 0;
    jsize numOutputs = outputNames ? env->GetArrayLength(outputNames) : 0;

    // ── Phase 1: Collect input names, dims, and data pointers ──
    struct InputInfo {
        std::string name;       // copy of the UTF string (for later lookup)
        const char* cname;      // ORIGINAL pointer from GetStringUTFChars (for ReleaseStringUTFChars)
        std::vector<int> dims;
        float* data;
        jsize dataLen;
        jobject dataArrRef;
        jstring nameRef;
    };
    std::vector<InputInfo> inputInfos;
    inputInfos.reserve(numInputs);

    // Batch-fetch offsets and dims arrays ONCE (previously fetched inside loop per input).
    jint* offsets = inputDimOffsets ? env->GetIntArrayElements(inputDimOffsets, nullptr) : nullptr;
    jlong* allDims = inputDims ? env->GetLongArrayElements(inputDims, nullptr) : nullptr;

    bool shapesChanged = false;
    bool shapeExceededMax = false;  // true if any shape grew beyond previous max
    bool shapeShrank = false;       // true if any shape shrank below previous (e.g., KV cache reset)
    for (int i = 0; i < numInputs; i++) {
        auto* name = (jstring)env->GetObjectArrayElement(inputNames, i);
        auto* dataArr = (jfloatArray)env->GetObjectArrayElement(inputTensors, i);
        const char* cname = env->GetStringUTFChars(name, nullptr);

        int start = offsets[i * 2];
        int len = offsets[i * 2 + 1];
        std::vector<int> dims(len);
        for (int d = 0; d < len; d++) dims[d] = static_cast<int>(allDims[start + d]);

        // Shape cache: compare with last-seen dims to detect if shape changed.
        auto cacheIt = h->lastInputShapes.find(cname);
        if (cacheIt == h->lastInputShapes.end() || cacheIt->second != dims) {
            shapesChanged = true;
            // Check if shape shrank compared to last call (e.g., KV cache reset at new frame).
            // When shape shrinks, MNN's computation graph needs resizeSession to update
            // internal tensor sizes, otherwise BackendError may occur.
            if (cacheIt != h->lastInputShapes.end() && dims.size() == cacheIt->second.size()) {
                for (size_t d = 0; d < dims.size(); d++) {
                    if (dims[d] < cacheIt->second[d]) {
                        shapeShrank = true;
                        // Force resizeTensor to new (smaller) shape and reset maxInputShapes.
                        // Otherwise tensor keeps old (larger) shape with stale data:
                        // when empty data (dataLen==0) is provided, copy is skipped,
                        // leaving previous frame's KV cache in the tensor → wrong attention.
                        h->maxInputShapes[cname] = dims;
                        auto* tensor = h->interpreter->getSessionInput(h->session, cname);
                        if (tensor) h->interpreter->resizeTensor(tensor, dims);
                        if (g_verbose) LOGI("MNN: shape shrank for '%s', resizeTensor to clear stale data", cname);
                        break;
                    }
                }
            }
            if (g_verbose) LOGI("MNN: shape changed for '%s'", cname);
        }
        h->lastInputShapes[cname] = dims;

        // Max-shape tracking: if current shape ≤ max shape, skip resizeTensor.
        // For CPU backend, supportDynamicInputMemory=true means resizeSession's
        // allocMemory is skipped anyway, so this is just a minor optimization.
        // For Vulkan, this doesn't help much (allocMemory always runs), but we
        // use CPU for localCachedStep now, so this is fine.
        // NOTE: Pre-allocation padding was removed because the model computes
        // attention positions from tensor shape (Range(0, seqLen)), so padding
        // shifts the new_key to a wrong position and gets masked out by
        // Where(pos > pvl, -FLT_MAX, score).
        auto maxIt = h->maxInputShapes.find(cname);
        if (maxIt == h->maxInputShapes.end()) {
            shapeExceededMax = true;
            h->maxInputShapes[cname] = dims;
            auto* tensor = h->interpreter->getSessionInput(h->session, cname);
            if (tensor) h->interpreter->resizeTensor(tensor, dims);
        } else {
            const auto& maxDims = maxIt->second;
            bool exceeded = false;
            if (dims.size() != maxDims.size()) {
                exceeded = true;
            } else {
                for (size_t d = 0; d < dims.size(); d++) {
                    if (dims[d] > maxDims[d]) { exceeded = true; break; }
                }
            }
            if (exceeded) {
                shapeExceededMax = true;
                h->maxInputShapes[cname] = dims;
                auto* tensor = h->interpreter->getSessionInput(h->session, cname);
                if (tensor) h->interpreter->resizeTensor(tensor, dims);
            }
        }

        jfloat* data = env->GetFloatArrayElements(dataArr, nullptr);
        jsize dataLen = env->GetArrayLength(dataArr);

        inputInfos.push_back({std::string(cname), cname, dims, data, dataLen, dataArr, name});
    }
    if (offsets) env->ReleaseIntArrayElements(inputDimOffsets, offsets, JNI_ABORT);
    if (allDims) env->ReleaseLongArrayElements(inputDims, allDims, JNI_ABORT);

    double t1 = now_ms();
    h->timing.phase1 += (t1 - t0);

    // ── Phase 2: resize session (allocate buffers) ──
    // Call resizeSession when:
    //   1. First call (shapeCacheValid == false)
    //   2. A shape grew beyond the previous max (shapeExceededMax == true)
    //   3. A shape shrank below the previous (shapeShrank == true)
    if (shapeExceededMax || shapeShrank || !h->shapeCacheValid) {
        h->interpreter->resizeSession(h->session);
        h->shapeCacheValid = true;
        if (g_verbose) LOGI("MNN: resizeSession called (shapeExceededMax=%d shapeShrank=%d)", shapeExceededMax ? 1 : 0, shapeShrank ? 1 : 0);
    }

    double t2 = now_ms();
    h->timing.phase2 += (t2 - t1);

    // ── Phase 3: copy data after buffers are allocated ──
    // Direct copy: tensor shape matches input data shape (no padding).
    for (auto& info : inputInfos) {
        // Skip empty tensors (e.g., localCachedStep first call with past_key [1,0,12,64]).
        // resizeTensor in Phase 1 already set the correct shape; no data to copy.
        if (info.dataLen == 0) {
            env->ReleaseFloatArrayElements((jfloatArray)info.dataArrRef, info.data, JNI_ABORT);
            continue;
        }
        auto* tensor = h->interpreter->getSessionInput(h->session, info.name.c_str());
        if (!tensor) {
            auto* defaultInput = h->interpreter->getSessionInput(h->session, info.name.c_str());
            if (defaultInput) {
                auto dtype = defaultInput->getType();
                if (dtype.code == halide_type_int && dtype.bits == 32) {
                    std::vector<int32_t> intData(info.dataLen);
                    for (int j = 0; j < info.dataLen; j++) intData[j] = static_cast<int32_t>(info.data[j]);
                    auto* hostTensor = MNN::Tensor::create<int32_t>(info.dims, intData.data(), MNN::Tensor::CAFFE);
                    defaultInput->copyFromHostTensor(hostTensor);
                    delete hostTensor;
                } else if (dtype.code == halide_type_int && dtype.bits == 64) {
                    std::vector<int64_t> intData(info.dataLen);
                    for (int j = 0; j < info.dataLen; j++) intData[j] = static_cast<int64_t>(info.data[j]);
                    auto* hostTensor = MNN::Tensor::create<int64_t>(info.dims, intData.data(), MNN::Tensor::CAFFE);
                    defaultInput->copyFromHostTensor(hostTensor);
                    delete hostTensor;
                } else {
                    auto* hostTensor = MNN::Tensor::create<float>(info.dims, info.data, MNN::Tensor::CAFFE);
                    defaultInput->copyFromHostTensor(hostTensor);
                    delete hostTensor;
                }
            }
            env->ReleaseFloatArrayElements((jfloatArray)info.dataArrRef, info.data, JNI_ABORT);
            continue;
        }
        auto tensorType = tensor->getType();
        if (g_verbose) {
            const char* typeStr = "unknown";
            if (tensorType.code == halide_type_float) typeStr = (tensorType.bits == 32) ? "float32" : "float64";
            else if (tensorType.code == halide_type_int) typeStr = (tensorType.bits == 32) ? "int32" : (tensorType.bits == 64) ? "int64" : "int?";
            LOGI("MNN: input '%s' type=%s code=%d bits=%d", info.name.c_str(), typeStr, (int)tensorType.code, (int)tensorType.bits);
        }
        if (tensorType.code == halide_type_int && tensorType.bits == 32) {
            auto* dst = tensor->host<int32_t>();
            if (dst) {
                for (int j = 0; j < info.dataLen; j++) dst[j] = static_cast<int32_t>(info.data[j]);
            } else {
                std::vector<int32_t> intData(info.dataLen);
                for (int j = 0; j < info.dataLen; j++) intData[j] = static_cast<int32_t>(info.data[j]);
                auto* hostTensor = MNN::Tensor::create<int32_t>(info.dims, intData.data(), MNN::Tensor::CAFFE);
                tensor->copyFromHostTensor(hostTensor);
                delete hostTensor;
            }
        } else if (tensorType.code == halide_type_int && tensorType.bits == 64) {
            // int64 tensors (e.g., position_ids in nocumsum prefill) — convert float→int64
            auto* dst = tensor->host<int64_t>();
            if (dst) {
                for (int j = 0; j < info.dataLen; j++) dst[j] = static_cast<int64_t>(info.data[j]);
            } else {
                std::vector<int64_t> intData(info.dataLen);
                for (int j = 0; j < info.dataLen; j++) intData[j] = static_cast<int64_t>(info.data[j]);
                auto* hostTensor = MNN::Tensor::create<int64_t>(info.dims, intData.data(), MNN::Tensor::CAFFE);
                tensor->copyFromHostTensor(hostTensor);
                delete hostTensor;
            }
        } else {
            auto* dst = tensor->host<float>();
            if (dst) {
                memcpy(dst, info.data, info.dataLen * sizeof(float));
            } else {
                auto* hostTensor = MNN::Tensor::create<float>(info.dims, info.data, MNN::Tensor::CAFFE);
                tensor->copyFromHostTensor(hostTensor);
                delete hostTensor;
            }
        }
        env->ReleaseFloatArrayElements((jfloatArray)info.dataArrRef, info.data, JNI_ABORT);
    }

    // Release input name references (use original cname pointer, not std::string copy)
    for (auto& info : inputInfos) {
        env->ReleaseStringUTFChars(info.nameRef, info.cname);
        env->DeleteLocalRef(info.nameRef);
    }

    double t3 = now_ms();
    h->timing.phase3 += (t3 - t2);

    // ── Run ──
    auto code = h->interpreter->runSession(h->session);
    if (code != MNN::NO_ERROR) {
        LOGE("runSession failed: %d", code);
        return nullptr;
    }
    if (g_verbose) LOGI("MNN: runSession completed NO_ERROR");

    double t4 = now_ms();
    h->timing.runSess += (t4 - t3);

    // ── Read outputs ──
    // Always return float[][] to match the Kotlin signature, even for int32 tensors.
    jclass floatArrClass = env->FindClass("[F");
    auto result = env->NewObjectArray(numOutputs, floatArrClass, nullptr);

    for (int i = 0; i < numOutputs; i++) {
        auto* name = (jstring)env->GetObjectArrayElement(outputNames, i);
        const char* cname = env->GetStringUTFChars(name, nullptr);

        auto* tensor = h->interpreter->getSessionOutput(h->session, cname);
        if (!tensor) {
            LOGE("Output '%s' not found", cname);
            env->ReleaseStringUTFChars(name, cname);
            env->DeleteLocalRef(name);
            continue;
        }

        auto outShape = tensor->shape();
        int outSize = 1;
        for (auto d : outShape) outSize *= d;
        if (g_verbose) LOGI("MNN: output[%d] '%s' shape dims=%d outSize=%d", i, cname, (int)outShape.size(), outSize);

        auto host = new MNN::Tensor(tensor, MNN::Tensor::CAFFE);
        tensor->copyToHostTensor(host);
        auto outType = host->getType();

        auto jarr = env->NewFloatArray(outSize);
        if (outType.code == halide_type_int && outType.bits == 32) {
            auto* outData = host->host<int32_t>();
            std::vector<float> floatData(outSize);
            for (int j = 0; j < outSize; j++) floatData[j] = static_cast<float>(outData[j]);
            env->SetFloatArrayRegion(jarr, 0, outSize, floatData.data());
            if (g_verbose) {
                int32_t mn = outData[0], mx = outData[0];
                for (int j = 1; j < outSize; j++) { if (outData[j] < mn) mn = outData[j]; if (outData[j] > mx) mx = outData[j]; }
                LOGI("MNN: output[%d] '%s' int32 size=%d min=%d max=%d", i, cname, outSize, mn, mx);
            }
        } else {
            auto* outData = host->host<float>();
            env->SetFloatArrayRegion(jarr, 0, outSize, outData);
            if (g_verbose) {
                int nanCount = 0;
                float mn = outData[0], mx = outData[0], sum = 0;
                for (int j = 0; j < outSize; j++) {
                    if (std::isnan(outData[j])) { nanCount++; continue; }
                    if (outData[j] < mn) mn = outData[j];
                    if (outData[j] > mx) mx = outData[j];
                    sum += outData[j];
                }
                LOGI("MNN: output[%d] '%s' float size=%d min=%f max=%f mean=%f nanCount=%d", i, cname, outSize, mn, mx, sum/outSize, nanCount);
            }
        }
        env->SetObjectArrayElement(result, i, jarr);
        env->DeleteLocalRef(jarr);

        delete host;
        env->ReleaseStringUTFChars(name, cname);
        env->DeleteLocalRef(name);
    }

    double t5 = now_ms();
    h->timing.output += (t5 - t4);
    h->timing.count++;

    env->DeleteLocalRef(floatArrClass);
    return result;
}

// ============================================================
// getInputNames / getOutputNames
// ============================================================
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeGetInputNames(
    JNIEnv* env, jobject, jlong ptr) {
    auto* h = reinterpret_cast<MnnSessionHandle*>(ptr);
    if (!h || !h->session) return nullptr;
    auto names = h->interpreter->getSessionInputAll(h->session);
    jclass strClass = env->FindClass("java/lang/String");
    auto result = env->NewObjectArray(names.size(), strClass, nullptr);
    int i = 0;
    for (auto& [name, _] : names) {
        env->SetObjectArrayElement(result, i++, env->NewStringUTF(name.c_str()));
    }
    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeGetOutputNames(
    JNIEnv* env, jobject, jlong ptr) {
    auto* h = reinterpret_cast<MnnSessionHandle*>(ptr);
    if (!h || !h->session) return nullptr;
    auto names = h->interpreter->getSessionOutputAll(h->session);
    jclass strClass = env->FindClass("java/lang/String");
    auto result = env->NewObjectArray(names.size(), strClass, nullptr);
    int i = 0;
    for (auto& [name, _] : names) {
        env->SetObjectArrayElement(result, i++, env->NewStringUTF(name.c_str()));
    }
    return result;
}

// ============================================================
// getOutputShape: get the shape of a named output tensor
// ============================================================
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeGetOutputShape(
    JNIEnv* env, jobject, jlong ptr, jstring outputName) {
    auto* h = reinterpret_cast<MnnSessionHandle*>(ptr);
    if (!h || !h->session) return nullptr;

    const char* cname = env->GetStringUTFChars(outputName, nullptr);
    auto* tensor = h->interpreter->getSessionOutput(h->session, cname);
    env->ReleaseStringUTFChars(outputName, cname);

    if (!tensor) return nullptr;

    auto shape = tensor->shape();
    auto result = env->NewLongArray(shape.size());
    jlong* buf = new jlong[shape.size()];
    for (size_t i = 0; i < shape.size(); i++) buf[i] = shape[i];
    env->SetLongArrayRegion(result, 0, shape.size(), buf);
    delete[] buf;
    return result;
}

// ============================================================
// nativeGenerateAudioFrames: JNI 层一体化帧生成
// 将 Kotlin 层每帧 64ms 的 18 次 JNI 调用 + 采样 + KV 管理移入 C++,
// 消除跨语言开销。采样逻辑与 MossTtsSampling.kt 完全一致。
// ============================================================

#include <random>
#include <algorithm>
#include <set>

static std::mt19937& getRng() {
    static std::mt19937 rng(std::random_device{}());
    return rng;
}

// argmax over [offset, offset+size)
static int argmaxRange(const float* logits, int offset, int size) {
    int best = 0; float bestV = -std::numeric_limits<float>::infinity();
    for (int i = 0; i < size; i++) {
        float v = logits[offset + i];
        if (v > bestV) { bestV = v; best = i; }
    }
    return best;
}

// argmax with repetition penalty over [offset, offset+size)
static int argmaxWithRepRange(const float* logits, int offset, int size,
                              const std::set<int>& prevTokens, float rep) {
    int best = 0; float bestV = -std::numeric_limits<float>::infinity();
    bool applyPenalty = !prevTokens.empty() && rep != 1.0f;
    for (int i = 0; i < size; i++) {
        float v = logits[offset + i];
        if (applyPenalty && prevTokens.count(i)) {
            v = (v < 0) ? v * rep : v / rep;
        }
        if (v > bestV) { bestV = v; best = i; }
    }
    return best;
}

// apply repetition penalty in-place over [offset, offset+size)
static void applyRepPenaltyRange(float* logits, int offset, int size,
                                 const std::set<int>& prevTokens, float rep) {
    if (prevTokens.empty() || rep == 1.0f) return;
    for (int t : prevTokens) {
        if (t < 0 || t >= size) continue;
        int idx = offset + t;
        logits[idx] = (logits[idx] < 0) ? logits[idx] * rep : logits[idx] / rep;
    }
}

// sampleFromScores: temperature + top-k + top-p sampling over [offset, offset+size)
// Mirrors MossTtsSampling.sampleFromScoresRange exactly.
static int sampleFromScoresRange(float* logits, int offset, int size,
                                 float temp, int topK, float topP) {
    if (temp <= 0) return argmaxRange(logits, offset, size);
    // Temperature scaling into local buffer
    std::vector<float> scores(size);
    for (int i = 0; i < size; i++) scores[i] = logits[offset + i] / temp;

    // Top-K filtering via partial sort
    int effK = (topK > 0 && topK < size) ? topK : 0;
    std::vector<int> candIdx;
    std::vector<float> candScores;
    if (effK > 0) {
        // Find threshold = K-th largest
        std::vector<float> tmp = scores;
        std::nth_element(tmp.begin(), tmp.begin() + (effK - 1), tmp.end(), std::greater<float>());
        float threshold = tmp[effK - 1];
        for (int i = 0; i < size; i++) {
            if (scores[i] >= threshold) {
                candIdx.push_back(i);
                candScores.push_back(scores[i]);
                if ((int)candIdx.size() >= effK) break;
            }
        }
    } else {
        candIdx.resize(size);
        candScores.resize(size);
        for (int i = 0; i < size; i++) { candIdx[i] = i; candScores[i] = scores[i]; }
    }
    int candCount = (int)candIdx.size();

    // Sort candidates descending (insertion sort for small K)
    for (int i = 1; i < candCount; i++) {
        int ti = candIdx[i]; float ts = candScores[i];
        int j = i - 1;
        while (j >= 0 && candScores[j] < ts) {
            candIdx[j+1] = candIdx[j]; candScores[j+1] = candScores[j]; j--;
        }
        candIdx[j+1] = ti; candScores[j+1] = ts;
    }

    // Top-P (nucleus) filtering
    int keepCount = candCount;
    if (topP > 0 && topP < 1.0f && candCount > 0) {
        float maxV = -std::numeric_limits<float>::infinity();
        for (int i = 0; i < candCount; i++) if (candScores[i] > maxV) maxV = candScores[i];
        double sum = 0;
        std::vector<double> exps(candCount);
        for (int i = 0; i < candCount; i++) {
            exps[i] = std::exp((double)(candScores[i] - maxV));
            sum += exps[i];
        }
        double cum = 0;
        for (int i = 0; i < candCount; i++) {
            cum += exps[i] / sum;
            if (cum > topP) { keepCount = i + 1; break; }
        }
    }

    if (keepCount <= 0) return candCount > 0 ? candIdx[0] : 0;
    // Final softmax + random draw
    float maxV = -std::numeric_limits<float>::infinity();
    for (int i = 0; i < keepCount; i++) if (candScores[i] > maxV) maxV = candScores[i];
    double sum = 0;
    std::vector<double> probs(keepCount);
    for (int i = 0; i < keepCount; i++) {
        probs[i] = std::exp((double)(candScores[i] - maxV));
        sum += probs[i];
    }
    std::uniform_real_distribution<double> dist(0.0, sum);
    double draw = dist(getRng());
    for (int i = 0; i < keepCount; i++) {
        draw -= probs[i];
        if (draw <= 0) return candIdx[i];
    }
    return candIdx[0];
}

// Set input from float data, auto-converting to tensor's actual type.
// Mirrors nativeRun's Phase 3 type handling: int32/int64/float tensors all work.
static void setInputFromFloat(MNN::Interpreter* interp, MNN::Session* sess,
                               const char* name, const float* data, int len,
                               const std::vector<int>& dims) {
    auto* tensor = interp->getSessionInput(sess, name);
    if (!tensor) return;
    auto tensorType = tensor->getType();
    if (tensorType.code == halide_type_int && tensorType.bits == 32) {
        // int32 tensor: convert float→int32
        auto* dst = tensor->host<int32_t>();
        if (dst) {
            for (int j = 0; j < len; j++) dst[j] = static_cast<int32_t>(data[j]);
        } else {
            std::vector<int32_t> intData(len);
            for (int j = 0; j < len; j++) intData[j] = static_cast<int32_t>(data[j]);
            auto* host = MNN::Tensor::create<int32_t>(dims, intData.data(), MNN::Tensor::CAFFE);
            tensor->copyFromHostTensor(host);
            delete host;
        }
    } else if (tensorType.code == halide_type_int && tensorType.bits == 64) {
        // int64 tensor
        auto* dst = tensor->host<int64_t>();
        if (dst) {
            for (int j = 0; j < len; j++) dst[j] = static_cast<int64_t>(data[j]);
        } else {
            std::vector<int64_t> intData(len);
            for (int j = 0; j < len; j++) intData[j] = static_cast<int64_t>(data[j]);
            auto* host = MNN::Tensor::create<int64_t>(dims, intData.data(), MNN::Tensor::CAFFE);
            tensor->copyFromHostTensor(host);
            delete host;
        }
    } else {
        // float tensor
        auto* dst = tensor->host<float>();
        if (dst && len > 0) {
            memcpy(dst, data, len * sizeof(float));
        } else if (len > 0) {
            auto* host = MNN::Tensor::create<float>(dims, const_cast<float*>(data), MNN::Tensor::CAFFE);
            tensor->copyFromHostTensor(host);
            delete host;
        }
    }
}

// Set a scalar input via a float value (auto-converts to tensor's actual type).
// Equivalent to Kotlin's floatArrayOf(v.toFloat()) + nativeRun type conversion.
static void setScalarFloat(MNN::Interpreter* interp, MNN::Session* sess,
                            const char* name, float value) {
    float data[1] = {value};
    setInputFromFloat(interp, sess, name, data, 1, {1});
}

// Track shape changes for a session and call resizeTensor only when needed.
// Returns true if resizeSession is needed (shape grew or shrank).
static bool updateInputShape(MnnSessionHandle* h, const char* name,
                              const std::vector<int>& dims) {
    bool needResize = false;
    auto cacheIt = h->lastInputShapes.find(name);
    if (cacheIt == h->lastInputShapes.end() || cacheIt->second != dims) {
        if (cacheIt != h->lastInputShapes.end() && dims.size() == cacheIt->second.size()) {
            for (size_t d = 0; d < dims.size(); d++) {
                if (dims[d] < cacheIt->second[d]) {
                    h->maxInputShapes[name] = dims;
                    auto* tensor = h->interpreter->getSessionInput(h->session, name);
                    if (tensor) h->interpreter->resizeTensor(tensor, dims);
                    needResize = true;
                    break;
                }
            }
        }
    }
    h->lastInputShapes[name] = dims;
    auto maxIt = h->maxInputShapes.find(name);
    if (maxIt == h->maxInputShapes.end()) {
        h->maxInputShapes[name] = dims;
        auto* tensor = h->interpreter->getSessionInput(h->session, name);
        if (tensor) h->interpreter->resizeTensor(tensor, dims);
        needResize = true;
    } else {
        const auto& maxDims = maxIt->second;
        bool exceeded = false;
        if (dims.size() != maxDims.size()) {
            exceeded = true;
        } else {
            for (size_t d = 0; d < dims.size(); d++) {
                if (dims[d] > maxDims[d]) { exceeded = true; break; }
            }
        }
        if (exceeded) {
            h->maxInputShapes[name] = dims;
            auto* tensor = h->interpreter->getSessionInput(h->session, name);
            if (tensor) h->interpreter->resizeTensor(tensor, dims);
            needResize = true;
        }
    }
    return needResize;
}

// Call resizeSession only when shape cache indicates it's needed.
static void maybeResizeSession(MnnSessionHandle* h, bool shapeChanged) {
    if (shapeChanged || !h->shapeCacheValid) {
        h->interpreter->resizeSession(h->session);
        h->shapeCacheValid = true;
    }
}

// Read a float output tensor into a vector
static bool readFloatOutput(MNN::Interpreter* interp, MNN::Session* sess,
                           const char* name, std::vector<float>* out) {
    auto* tensor = interp->getSessionOutput(sess, name);
    if (!tensor) return false;
    auto* host = tensor->host<float>();
    int len = 1;
    auto shape = tensor->shape();
    for (auto d : shape) len *= d;
    out->resize(len);
    if (host) {
        memcpy(out->data(), host, len * sizeof(float));
    } else {
        auto* hostTensor = new MNN::Tensor(tensor, MNN::Tensor::CAFFE);
        tensor->copyToHostTensor(hostTensor);
        memcpy(out->data(), hostTensor->host<float>(), len * sizeof(float));
        delete hostTensor;
    }
    return true;
}

// Run localCachedStep: returns text_logits and/or audio_logits + new KV cache
struct LocalStepResult {
    std::vector<float> textLogits;
    std::vector<float> audioLogits;
    std::vector<float> outKey;
    std::vector<float> outValue;
};

static bool runLocalCachedStep(MnnSessionHandle* h,
                               const std::vector<float>& hidden,
                               int textToken, int audioToken, int ch, int st, int pvl,
                               const std::vector<float>& pastKey, const std::vector<float>& pastValue,
                               int ksl, int heads, int headDim,
                               bool needText, bool needAudio,
                               LocalStepResult* result) {
    auto* interp = h->interpreter;
    auto* sess = h->session;
    bool shapeChanged = false;

    // global_hidden: [1, hidden.size()] — shape stable across calls
    std::vector<int> hDims = {1, (int)hidden.size()};
    shapeChanged |= updateInputShape(h, "global_hidden", hDims);

    // scalars via float (auto-convert to tensor's actual type, matching nativeRun)
    shapeChanged |= updateInputShape(h, "text_token_id", {1});
    shapeChanged |= updateInputShape(h, "audio_token_id", {1});
    shapeChanged |= updateInputShape(h, "channel_index", {1});
    shapeChanged |= updateInputShape(h, "step_type", {1});
    shapeChanged |= updateInputShape(h, "past_valid_lengths", {1});

    // local_past_key_0 / local_past_value_0: [1, ksl, heads, headDim] — ksl grows each call
    std::vector<int> kvDims = {1, ksl, heads, headDim};
    shapeChanged |= updateInputShape(h, "local_past_key_0", kvDims);
    shapeChanged |= updateInputShape(h, "local_past_value_0", kvDims);

    // resizeSession only when shape grew/shrank (CPU backend is cheap but not free)
    maybeResizeSession(h, shapeChanged);

    // Now write data (after resizeSession allocates buffers)
    setInputFromFloat(interp, sess, "global_hidden", hidden.data(), hidden.size(), hDims);
    setScalarFloat(interp, sess, "text_token_id", (float)textToken);
    setScalarFloat(interp, sess, "audio_token_id", (float)audioToken);
    setScalarFloat(interp, sess, "channel_index", (float)ch);
    setScalarFloat(interp, sess, "step_type", (float)st);
    setScalarFloat(interp, sess, "past_valid_lengths", (float)pvl);

    int unit = heads * headDim;
    int kvLen = ksl * unit;
    if (ksl > 0) {
        setInputFromFloat(interp, sess, "local_past_key_0", pastKey.data(), kvLen, kvDims);
        setInputFromFloat(interp, sess, "local_past_value_0", pastValue.data(), kvLen, kvDims);
    }

    // runSession
    auto code = interp->runSession(sess);
    if (code != MNN::NO_ERROR) {
        LOGE("nativeGenerateAudioFrames: localCachedStep runSession failed: %d", code);
        return false;
    }

    // read outputs
    if (needText) {
        if (!readFloatOutput(interp, sess, "text_logits", &result->textLogits)) return false;
    }
    if (needAudio) {
        if (!readFloatOutput(interp, sess, "audio_logits", &result->audioLogits)) return false;
    }
    if (!readFloatOutput(interp, sess, "local_present_key_0", &result->outKey)) return false;
    if (!readFloatOutput(interp, sess, "local_present_value_0", &result->outValue)) return false;
    return true;
}

// Run decodeStep: returns new hidden + new KV cache (globalHeads layers)
struct DecodeStepResult {
    std::vector<float> hidden;  // last hidden (headDim*globalHeads)
    std::vector<std::vector<float>> kv; // [globalHeads*2] = 24 arrays
};

static bool runDecodeStep(MnnSessionHandle* h,
                         const int* frame, int nq, int pvl,
                         const std::vector<std::vector<float>>& kv,
                         int globalHeads, int globalHeadDim,
                         int audioPadTokenId, int audioAssistantSlotTokenId,
                         DecodeStepResult* result) {
    auto* interp = h->interpreter;
    auto* sess = h->session;
    int rw = nq + 1;
    int hs = globalHeads * globalHeadDim;

    // input_ids: float [1, 1, rw] — model tensor is int32, setInputFromFloat auto-converts
    std::vector<float> rd(rw, (float)audioPadTokenId);
    rd[0] = (float)audioAssistantSlotTokenId;
    for (int i = 0; i < nq; i++) rd[i+1] = (float)frame[i];

    // Shape tracking
    bool shapeChanged = false;
    std::vector<int> idDims = {1, 1, rw};
    shapeChanged |= updateInputShape(h, "input_ids", idDims);
    shapeChanged |= updateInputShape(h, "past_valid_lengths", {1});

    // past_key_i / past_value_i: [1, kvSeqLen, heads, headDim]
    int kvSeqLen = kv.empty() ? 1 : (kv[0].size() / (globalHeads * globalHeadDim));
    if (kv.empty() || kv[0].empty()) kvSeqLen = 1; // guard
    std::vector<int> kvDims = {1, kvSeqLen, globalHeads, globalHeadDim};
    for (int i = 0; i < globalHeads; i++) {
        char name[32];
        snprintf(name, sizeof(name), "past_key_%d", i);
        shapeChanged |= updateInputShape(h, name, kvDims);
        snprintf(name, sizeof(name), "past_value_%d", i);
        shapeChanged |= updateInputShape(h, name, kvDims);
    }

    maybeResizeSession(h, shapeChanged);

    // Write data after resizeSession
    setInputFromFloat(interp, sess, "input_ids", rd.data(), rw, idDims);
    setScalarFloat(interp, sess, "past_valid_lengths", (float)pvl);
    for (int i = 0; i < globalHeads; i++) {
        char name[32];
        snprintf(name, sizeof(name), "past_key_%d", i);
        setInputFromFloat(interp, sess, name, kv[i*2].data(), kv[i*2].size(), kvDims);
        snprintf(name, sizeof(name), "past_value_%d", i);
        setInputFromFloat(interp, sess, name, kv[i*2+1].data(), kv[i*2+1].size(), kvDims);
    }

    auto code = interp->runSession(sess);
    if (code != MNN::NO_ERROR) {
        LOGE("nativeGenerateAudioFrames: decodeStep runSession failed: %d", code);
        return false;
    }

    // read global_hidden
    std::vector<float> fullHidden;
    if (!readFloatOutput(interp, sess, "global_hidden", &fullHidden)) return false;
    // take last hs elements
    if ((int)fullHidden.size() < hs) return false;
    result->hidden.assign(fullHidden.end() - hs, fullHidden.end());

    // read present_key_i / present_value_i
    result->kv.resize(globalHeads * 2);
    for (int i = 0; i < globalHeads; i++) {
        char name[32];
        snprintf(name, sizeof(name), "present_key_%d", i);
        if (!readFloatOutput(interp, sess, name, &result->kv[i*2])) return false;
        snprintf(name, sizeof(name), "present_value_%d", i);
        if (!readFloatOutput(interp, sess, name, &result->kv[i*2+1])) return false;
    }
    return true;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_companion_chat_engine_MossMnnJni_nativeGenerateAudioFrames(
    JNIEnv* env, jobject,
    jlong localCachedStepHandle,
    jlong decodeStepHandle,
    jfloatArray initialH,
    jobjectArray initialKv,
    jint initialPvl,
    jint maxFrames,
    jint nq,
    jint asid,
    jint audioEndTokenId,
    jint audioPadTokenId,
    jint audioAssistantSlotTokenId,
    jint localHeads,
    jint localHeadDim,
    jint globalHeads,
    jint globalHeadDim,
    jboolean doSample,
    jfloat textTemp,
    jint textTopK,
    jfloat textTopP,
    jfloat audioTemp,
    jint audioTopK,
    jfloat audioTopP,
    jfloat audioRep) {

    bindToBigCoresIfNeeded();
    double t0 = now_ms();

    auto* localH = reinterpret_cast<MnnSessionHandle*>(localCachedStepHandle);
    auto* decodeH = reinterpret_cast<MnnSessionHandle*>(decodeStepHandle);
    if (!localH || !localH->session || !decodeH || !decodeH->session) {
        LOGE("nativeGenerateAudioFrames: invalid handles");
        return nullptr;
    }

    bool sample = doSample == JNI_TRUE;

    // Read initial hidden
    jsize hLen = env->GetArrayLength(initialH);
    jfloat* hData = env->GetFloatArrayElements(initialH, nullptr);
    std::vector<float> h(hData, hData + hLen);
    env->ReleaseFloatArrayElements(initialH, hData, JNI_ABORT);

    // Read initial KV cache (globalHeads*2 arrays)
    int nGlobalKv = env->GetArrayLength(initialKv);
    std::vector<std::vector<float>> kv(nGlobalKv);
    for (int i = 0; i < nGlobalKv; i++) {
        auto* arr = (jfloatArray)env->GetObjectArrayElement(initialKv, i);
        jsize len = env->GetArrayLength(arr);
        jfloat* data = env->GetFloatArrayElements(arr, nullptr);
        kv[i].assign(data, data + len);
        env->ReleaseFloatArrayElements(arr, data, JNI_ABORT);
        env->DeleteLocalRef(arr);
    }

    int pvl = initialPvl;
    int unit = localHeads * localHeadDim;

    // Per-channel previous token sets for repetition penalty
    std::vector<std::set<int>> pts(nq);

    // Output: int[][]
    jclass intArrClass = env->FindClass("[I");
    std::vector<std::vector<int>> frames;

    // Local KV cache (starts empty, like Kotlin lkv=null)
    std::vector<float> localKey, localValue;
    int localKsl = 0;

    for (int si = 0; si < maxFrames; si++) {
        // === Text step (st=0): lkv=null, lpvl=0 ===
        LocalStepResult textRes;
        std::vector<float> emptyKey, emptyValue;
        if (!runLocalCachedStep(localH, h, 0, 0, 0, 0, 0,
                                emptyKey, emptyValue, 0, localHeads, localHeadDim,
                                true, false, &textRes)) {
            LOGE("nativeGenerateAudioFrames: text step failed at si=%d", si);
            break;
        }
        // Sample text token (asid vs audioEndTokenId)
        int ntt;
        {
            float candScores[2] = {
                (asid < (int)textRes.textLogits.size()) ? textRes.textLogits[asid] : -std::numeric_limits<float>::infinity(),
                (audioEndTokenId < (int)textRes.textLogits.size()) ? textRes.textLogits[audioEndTokenId] : -std::numeric_limits<float>::infinity()
            };
            int candIds[2] = {asid, audioEndTokenId};
            if (!sample) {
                ntt = (candScores[0] >= candScores[1]) ? candIds[0] : candIds[1];
            } else {
                // sampleFromScores on 2 candidates
                float scaled[2] = {candScores[0] / textTemp, candScores[1] / textTemp};
                int effK = std::min((int)textTopK, 2);
                // both are candidates (only 2), sort descending
                int idx0 = 0, idx1 = 1;
                if (scaled[idx1] > scaled[idx0]) std::swap(idx0, idx1);
                // top-p
                float mx = std::max(scaled[idx0], scaled[idx1]);
                double e0 = std::exp((double)(scaled[idx0] - mx));
                double e1 = std::exp((double)(scaled[idx1] - mx));
                double sum = e0 + e1;
                int keepCount = 2;
                if (textTopP > 0 && textTopP < 1.0f) {
                    double cum = e0 / sum;
                    if (cum > textTopP) keepCount = 1;
                }
                double probs[2] = {e0 / sum, e1 / sum};
                std::uniform_real_distribution<double> dist(0.0, 1.0);
                double draw = dist(getRng());
                int localIdx[2] = {idx0, idx1};
                if (keepCount == 1) {
                    ntt = candIds[localIdx[0]];
                } else {
                    double acc = 0;
                    bool picked = false;
                    for (int k = 0; k < keepCount; k++) {
                        acc += probs[k];
                        if (draw <= acc) { ntt = candIds[localIdx[k]]; picked = true; break; }
                    }
                    if (!picked) ntt = candIds[localIdx[0]]; // fallback
                }
            }
        }
        if (ntt != asid) {
            LOGI("nativeGenerateAudioFrames: END at si=%d ntt=%d (END token)", si, ntt);
            break;
        }

        // === Audio ch0 step (st=1): use textRes.outKey/outValue as KV ===
        int lpvl = 1; // incremented after text step
        LocalStepResult a0Res;
        if (!runLocalCachedStep(localH, h, ntt, 0, 0, 1, lpvl,
                                textRes.outKey, textRes.outValue, (int)textRes.outKey.size()/unit, localHeads, localHeadDim,
                                false, true, &a0Res)) {
            LOGE("nativeGenerateAudioFrames: audio ch0 step failed at si=%d", si);
            break;
        }
        lpvl++;
        // localKv is now a0Res.outKey/outValue
        std::vector<float> curKey = a0Res.outKey, curValue = a0Res.outValue;

        std::vector<int> fr(nq);
        int audioVocabSize = a0Res.audioLogits.size() / nq;
        // Sample audio ch0
        if (!sample) {
            fr[0] = argmaxWithRepRange(a0Res.audioLogits.data(), 0, audioVocabSize, pts[0], audioRep);
        } else {
            applyRepPenaltyRange(a0Res.audioLogits.data(), 0, audioVocabSize, pts[0], audioRep);
            fr[0] = sampleFromScoresRange(a0Res.audioLogits.data(), 0, audioVocabSize, audioTemp, audioTopK, audioTopP);
        }
        pts[0].insert(fr[0]);
        int prev = fr[0];

        // === Audio ch1..nq-1 (st=2) ===
        for (int ch = 1; ch < nq; ch++) {
            LocalStepResult chRes;
            if (!runLocalCachedStep(localH, h, 0, prev, ch-1, 2, lpvl,
                                    curKey, curValue, (int)curKey.size()/unit, localHeads, localHeadDim,
                                    false, true, &chRes)) {
                LOGE("nativeGenerateAudioFrames: audio ch%d step failed at si=%d", ch, si);
                break;
            }
            lpvl++;
            curKey = chRes.outKey;
            curValue = chRes.outValue;
            int off = ch * audioVocabSize;
            if (!sample) {
                fr[ch] = argmaxWithRepRange(chRes.audioLogits.data(), off, audioVocabSize, pts[ch], audioRep);
            } else {
                applyRepPenaltyRange(chRes.audioLogits.data(), off, audioVocabSize, pts[ch], audioRep);
                fr[ch] = sampleFromScoresRange(chRes.audioLogits.data(), off, audioVocabSize, audioTemp, audioTopK, audioTopP);
            }
            pts[ch].insert(fr[ch]);
            prev = fr[ch];
        }

        frames.push_back(fr);

        // === decodeStep: get new h + new global KV ===
        DecodeStepResult decRes;
        if (!runDecodeStep(decodeH, fr.data(), nq, pvl, kv, globalHeads, globalHeadDim,
                           audioPadTokenId, audioAssistantSlotTokenId, &decRes)) {
            LOGE("nativeGenerateAudioFrames: decodeStep failed at si=%d", si);
            break;
        }
        h = decRes.hidden;
        kv = decRes.kv;
        pvl++;
    }

    double t1 = now_ms();
    LOGI("nativeGenerateAudioFrames: %d frames in %.1fms (%.1fms/frame)",
         (int)frames.size(), t1-t0, frames.empty() ? 0 : (t1-t0)/frames.size());

    // Convert to int[][]
    auto result = env->NewObjectArray((jsize)frames.size(), intArrClass, nullptr);
    for (size_t i = 0; i < frames.size(); i++) {
        jintArray arr = env->NewIntArray((jsize)frames[i].size());
        env->SetIntArrayRegion(arr, 0, (jsize)frames[i].size(), frames[i].data());
        env->SetObjectArrayElement(result, (jsize)i, arr);
        env->DeleteLocalRef(arr);
    }
    return result;
}
