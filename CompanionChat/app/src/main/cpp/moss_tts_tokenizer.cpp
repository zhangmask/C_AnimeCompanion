/**
 * MOSS-TTS-Nano SentencePiece JNI 分词器。
 *
 * 包装 sentencepiece C++ 库，提供 encode(text) -> int[] 功能。
 * 需要在 CMake 中编译 sentencepiece 并链接此 JNI 库。
 *
 * 构建步骤:
 * 1. 将 sentencepiece 源码放到 third_party/sentencepiece/
 *    (https://github.com/google/sentencepiece)
 * 2. CMakeLists.txt 已配置编译（见 companion_moss_tts target）
 * 3. Kotlin 侧通过 System.loadLibrary("moss_tts_tokenizer") 加载
 *
 * 当 HAS_SENTENCEPIECE 未定义时（sentencepiece 源码不存在），
 * 编译为字符级 fallback 实现，保证 APK 仍可构建。
 */
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

// sentencepiece 头文件（仅在 CMake 检测到 sentencepiece 时包含）
#if defined(HAS_SENTENCEPIECE)
#include "sentencepiece_processor.h"
#endif

#define LOG_TAG "MossTtsTokenizer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════
//  字符级 fallback 编码（不依赖 sentencepiece）
// ═══════════════════════════════════════════════════════

static jintArray charLevelFallback(JNIEnv* env, jstring text) {
    const char* str = env->GetStringUTFChars(text, nullptr);
    std::string s(str);
    env->ReleaseStringUTFChars(text, str);

    // 按 UTF-32 code point 编码（简单的字符级映射）
    std::vector<jint> ids;
    const char* p = s.c_str();
    const char* end = p + s.size();
    while (p < end) {
        // Decode UTF-8 to code point
        uint32_t cp = 0;
        unsigned char c = static_cast<unsigned char>(*p);
        int bytes = 0;
        if (c < 0x80) { cp = c; bytes = 1; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; bytes = 2; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; bytes = 3; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; bytes = 4; }
        else { cp = c; bytes = 1; } // invalid byte, take as-is

        for (int i = 1; i < bytes && (p + i) < end; i++) {
            cp = (cp << 6) | (static_cast<unsigned char>(p[i]) & 0x3F);
        }
        p += bytes;
        ids.push_back(static_cast<jint>(cp));
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(ids.size()));
    if (result && !ids.empty()) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(ids.size()), ids.data());
    }
    return result;
}

// ═══════════════════════════════════════════════════════
//  HAS_SENTENCEPIECE: 完整的 SentencePiece JNI 实现
// ═══════════════════════════════════════════════════════
#if defined(HAS_SENTENCEPIECE)

struct TokenizerHandle {
    sentencepiece::SentencePieceProcessor* processor = nullptr;
    bool initialized = false;
    std::string model_path;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_SentencePieceTokenizer_nativeCreate(
    JNIEnv* env, jobject thiz, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading SentencePiece model: %s", path);

    auto* handle = new TokenizerHandle();
    handle->model_path = std::string(path);

    auto* processor = new sentencepiece::SentencePieceProcessor();
    auto status = processor->Load(path);
    if (!status.ok()) {
        LOGE("Failed to load SentencePiece model: %s", status.ToString().c_str());
        delete processor;
        delete handle;
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }
    handle->processor = processor;
    handle->initialized = true;
    LOGI("SentencePiece model loaded successfully");

    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(handle);
}

// Load model from serialized proto bytes (bypasses native file I/O on Android
// scoped storage where fopen may fail on app-specific external dirs).
JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_SentencePieceTokenizer_nativeCreateFromBytes(
    JNIEnv* env, jobject thiz, jbyteArray model_bytes) {
    jsize len = env->GetArrayLength(model_bytes);
    jbyte* data = env->GetByteArrayElements(model_bytes, nullptr);
    LOGI("Loading SentencePiece model from bytes: %d bytes", (int)len);

    auto* handle = new TokenizerHandle();
    handle->model_path = "<from-bytes>";

    auto* processor = new sentencepiece::SentencePieceProcessor();
    absl::string_view serialized(reinterpret_cast<const char*>(data), len);
    auto status = processor->LoadFromSerializedProto(serialized);
    env->ReleaseByteArrayElements(model_bytes, data, JNI_ABORT);

    if (!status.ok()) {
        LOGE("Failed to load SentencePiece model from bytes: %s", status.ToString().c_str());
        delete processor;
        delete handle;
        return 0;
    }
    handle->processor = processor;
    handle->initialized = true;
    LOGI("SentencePiece model loaded successfully from bytes");

    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT jintArray JNICALL
Java_com_companion_chat_engine_SentencePieceTokenizer_nativeEncode(
    JNIEnv* env, jobject thiz, jlong handle_ptr, jstring text) {
    auto* handle = reinterpret_cast<TokenizerHandle*>(handle_ptr);
    if (!handle || !handle->initialized) {
        return charLevelFallback(env, text);
    }

    const char* str = env->GetStringUTFChars(text, nullptr);
    std::vector<int> ids;
    handle->processor->Encode(str, &ids);
    env->ReleaseStringUTFChars(text, str);

    jintArray result = env->NewIntArray(static_cast<jsize>(ids.size()));
    if (result && !ids.empty()) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(ids.size()), ids.data());
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_companion_chat_engine_SentencePieceTokenizer_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong handle_ptr) {
    auto* handle = reinterpret_cast<TokenizerHandle*>(handle_ptr);
    if (handle) {
        delete handle->processor;
        delete handle;
    }
    LOGI("SentencePiece tokenizer destroyed");
}

} // extern "C"

// ═══════════════════════════════════════════════════════
//  无 sentencepiece: 字符级 stub 实现
// ═══════════════════════════════════════════════════════
#else // !HAS_SENTENCEPIECE

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_SentencePieceTokenizer_nativeCreate(
    JNIEnv* env, jobject thiz, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGE("SentencePiece not compiled in; falling back to char-level tokenizer (model_path=%s)", path);
    env->ReleaseStringUTFChars(model_path, path);
    // Return non-zero sentinel so Kotlin side doesn't throw RuntimeException
    return 1;
}

JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_SentencePieceTokenizer_nativeCreateFromBytes(
    JNIEnv* env, jobject thiz, jbyteArray model_bytes) {
    LOGE("SentencePiece not compiled in; falling back to char-level tokenizer (from-bytes)");
    return 1;
}

JNIEXPORT jintArray JNICALL
Java_com_companion_chat_engine_SentencePieceTokenizer_nativeEncode(
    JNIEnv* env, jobject thiz, jlong handle_ptr, jstring text) {
    (void)handle_ptr;
    return charLevelFallback(env, text);
}

JNIEXPORT void JNICALL
Java_com_companion_chat_engine_SentencePieceTokenizer_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong handle_ptr) {
    (void)env;
    (void)thiz;
    (void)handle_ptr;
    LOGI("Stub tokenizer destroyed (no-op)");
}

} // extern "C"

#endif // HAS_SENTENCEPIECE
