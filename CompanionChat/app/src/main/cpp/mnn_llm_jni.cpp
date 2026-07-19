#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <streambuf>
#include <ostream>
#include <sstream>
#include <memory>
#include <atomic>
#include <sched.h>
#include <unistd.h>
#include <sys/syscall.h>

#include "llm/llm.hpp"
#include "cv/imgcodecs.hpp"
#include "MNN/expr/Expr.hpp"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "MnnLlmJni", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MnnLlmJni", __VA_ARGS__)

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
    LOGI("bindToBigCores CPU4-7 ret=%d tid=%d", ret, (int)syscall(SYS_gettid));
    t_boundToBigCores = true;
}

class LlmStreamBuffer : public std::streambuf {
public:
    using CallBack = std::function<void(const char*, size_t)>;
    explicit LlmStreamBuffer(CallBack cb) : callback_(std::move(cb)) {}
protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (callback_ && n > 0) callback_(s, (size_t)n);
        return n;
    }
    int_type overflow(int_type c) override {
        if (c != traits_type::eof() && callback_) {
            char ch = (char)c;
            callback_(&ch, 1);
        }
        return c;
    }
private:
    CallBack callback_;
};

struct LlmHandle {
    MNN::Transformer::Llm* llm = nullptr;
    std::atomic<bool> stop_requested{false};
    std::atomic<bool> generating{false};
};

static JavaVM* g_jvm = nullptr;
static jclass g_callback_class = nullptr;
static jmethodID g_on_token_method = nullptr;

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass local = env->FindClass("com/companion/chat/engine/MnnLlmJni$TokenCallback");
    if (local) {
        g_callback_class = (jclass)env->NewGlobalRef(local);
        g_on_token_method = env->GetMethodID(g_callback_class, "onToken", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(local);
    }
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        if (g_callback_class) env->DeleteGlobalRef(g_callback_class);
    }
    g_callback_class = nullptr;
    g_on_token_method = nullptr;
}

static bool callTokenCallback(JNIEnv* env, jobject callback, const std::string& text) {
    if (!g_on_token_method || !callback) return false;
    if (text.empty()) return false;
    jstring jstr = env->NewStringUTF(text.c_str());
    jboolean cancel = env->CallBooleanMethod(callback, g_on_token_method, jstr);
    env->DeleteLocalRef(jstr);
    return cancel == JNI_TRUE;
}

// Returns the length of an incomplete UTF-8 sequence at the end of the string.
// 0 means the string ends on a complete character boundary.
static size_t utf8IncompleteTailLength(const std::string& s) {
    size_t len = s.size();
    if (len == 0) return 0;
    unsigned char last = (unsigned char)s[len - 1];
    // Last byte is a continuation byte — walk back to find the lead byte
    if ((last & 0xC0) == 0x80) {
        // Scan backwards for the lead byte (11xxxxxx)
        for (size_t i = 1; i <= 3 && i < len; i++) {
            unsigned char c = (unsigned char)s[len - 1 - i];
            if ((c & 0xC0) != 0x80) {
                // c is a lead byte
                size_t expected = 0;
                if ((c & 0xE0) == 0xC0) expected = 2;
                else if ((c & 0xF0) == 0xE0) expected = 3;
                else if ((c & 0xF8) == 0xF0) expected = 4;
                if (expected > 0 && i + 1 < expected) {
                    return i + 1; // incomplete
                }
                return 0; // complete or invalid — treat as complete
            }
        }
        return 1; // all continuation bytes with no lead — drop one
    }
    // Last byte is a lead byte for a multi-byte sequence
    if ((last & 0xE0) == 0xC0) return 1; // needs 2 bytes, only 1 present
    if ((last & 0xF0) == 0xE0) return 1; // needs 3 bytes, only 1 present
    if ((last & 0xF8) == 0xF0) return 1; // needs 4 bytes, only 1 present
    return 0; // single-byte ASCII — complete
}

static void runGenerate(LlmHandle* handle, JNIEnv* env, jobject callback,
                        const MNN::Transformer::ChatMessages& messages,
                        int max_tokens, bool has_images,
                        const MNN::Transformer::MultimodalPrompt* multimodal) {
    bindToBigCoresIfNeeded();
    handle->stop_requested = false;
    handle->generating = true;

    std::string utf8_pending; // buffers incomplete UTF-8 bytes across chunks

    LlmStreamBuffer stream_buffer([&](const char* str, size_t len) {
        if (handle->stop_requested) return;
        utf8_pending.append(str, len);
        size_t incomplete = utf8IncompleteTailLength(utf8_pending);
        size_t send_len = utf8_pending.size() - incomplete;
        if (send_len == 0) return;
        std::string ready = utf8_pending.substr(0, send_len);
        utf8_pending = utf8_pending.substr(send_len);
        bool cancel = callTokenCallback(env, callback, ready);
        if (cancel) {
            handle->stop_requested = true;
        }
    });
    std::ostream ostream(&stream_buffer);

    auto* llm = handle->llm;
    try {
        if (has_images && multimodal) {
            llm->response(*multimodal, &ostream, nullptr, 0);
        } else {
            llm->response(messages, &ostream, nullptr, 0);
        }
        int gen_count = 0;
        while (!handle->stop_requested && !llm->stoped() && gen_count < max_tokens) {
            llm->generate(1);
            gen_count++;
        }
    } catch (const std::exception& e) {
        LOGE("generate error: %s", e.what());
        callTokenCallback(env, callback, std::string("[生成错误: ") + e.what() + "]");
    } catch (...) {
        LOGE("generate unknown error");
        callTokenCallback(env, callback, "[生成未知错误]");
    }

    // Flush any remaining buffered UTF-8 bytes
    if (!utf8_pending.empty() && !handle->stop_requested) {
        callTokenCallback(env, callback, utf8_pending);
        utf8_pending.clear();
    }

    handle->generating = false;
    auto* context = llm->getContext();
    if (context) {
        LOGI("PERF | prefill: %d tok in %.2fs (%.1f t/s) | decode: %d tok in %.2fs (%.1f t/s)",
             context->prompt_len,
             context->prefill_us / 1e6f,
             context->prefill_us > 0 ? context->prompt_len / (context->prefill_us / 1e6f) : 0,
             context->gen_seq_len,
             context->decode_us / 1e6f,
             context->decode_us > 0 ? context->gen_seq_len / (context->decode_us / 1e6f) : 0);
    }
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_MnnLlmJni_nativeCreate(JNIEnv* env, jobject, jstring configPath) {
    if (!configPath) {
        LOGE("nativeCreate: configPath is null");
        return 0;
    }
    const char* path = env->GetStringUTFChars(configPath, nullptr);
    std::string config_path(path);
    env->ReleaseStringUTFChars(configPath, path);

    LOGI("nativeCreate: config_path=%s", config_path.c_str());

    auto* handle = new LlmHandle();
    try {
        handle->llm = MNN::Transformer::Llm::createLLM(config_path);
        if (!handle->llm) {
            LOGE("createLLM returned null");
            delete handle;
            return 0;
        }
        if (!handle->llm->load()) {
            LOGE("llm->load() failed");
            MNN::Transformer::Llm::destroy(handle->llm);
            delete handle;
            return 0;
        }
        handle->llm->set_config("{\"jinja\":{\"context\":{\"enable_thinking\":false}}}");
        LOGI("nativeCreate: success");
        return reinterpret_cast<jlong>(handle);
    } catch (const std::exception& e) {
        LOGE("nativeCreate error: %s", e.what());
        if (handle->llm) MNN::Transformer::Llm::destroy(handle->llm);
        delete handle;
        return 0;
    } catch (...) {
        LOGE("nativeCreate unknown error");
        if (handle->llm) MNN::Transformer::Llm::destroy(handle->llm);
        delete handle;
        return 0;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_companion_chat_engine_MnnLlmJni_nativeGenerate(JNIEnv* env, jobject,
        jlong handle_ptr, jobjectArray roles, jobjectArray contents,
        jint max_tokens, jfloat temperature, jint top_k, jfloat top_p,
        jobject callback) {
    auto* handle = reinterpret_cast<LlmHandle*>(handle_ptr);
    if (!handle || !handle->llm) {
        LOGE("nativeGenerate: invalid handle");
        return JNI_FALSE;
    }

    jsize count = env->GetArrayLength(roles);
    MNN::Transformer::ChatMessages messages;
    messages.reserve(count);
    for (jsize i = 0; i < count; i++) {
        jstring jrole = (jstring)env->GetObjectArrayElement(roles, i);
        jstring jcontent = (jstring)env->GetObjectArrayElement(contents, i);
        const char* role = env->GetStringUTFChars(jrole, nullptr);
        const char* content = env->GetStringUTFChars(jcontent, nullptr);
        messages.emplace_back(std::string(role), std::string(content));
        env->ReleaseStringUTFChars(jrole, role);
        env->ReleaseStringUTFChars(jcontent, content);
        env->DeleteLocalRef(jrole);
        env->DeleteLocalRef(jcontent);
    }

    std::ostringstream config_stream;
    config_stream << "{\"temperature\":" << temperature
                  << ",\"topK\":" << top_k
                  << ",\"topP\":" << top_p
                  << ",\"max_new_tokens\":" << max_tokens << "}";
    handle->llm->set_config(config_stream.str());

    runGenerate(handle, env, callback, messages, max_tokens, false, nullptr);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_companion_chat_engine_MnnLlmJni_nativeGenerateWithImages(JNIEnv* env, jobject,
        jlong handle_ptr, jobjectArray roles, jobjectArray contents,
        jobjectArray image_bytes, jint max_tokens,
        jfloat temperature, jint top_k, jfloat top_p,
        jobject callback) {
    auto* handle = reinterpret_cast<LlmHandle*>(handle_ptr);
    if (!handle || !handle->llm) {
        LOGE("nativeGenerateWithImages: invalid handle");
        return JNI_FALSE;
    }

    jsize count = env->GetArrayLength(roles);
    MNN::Transformer::ChatMessages messages;
    messages.reserve(count);
    for (jsize i = 0; i < count; i++) {
        jstring jrole = (jstring)env->GetObjectArrayElement(roles, i);
        jstring jcontent = (jstring)env->GetObjectArrayElement(contents, i);
        const char* role = env->GetStringUTFChars(jrole, nullptr);
        const char* content = env->GetStringUTFChars(jcontent, nullptr);
        messages.emplace_back(std::string(role), std::string(content));
        env->ReleaseStringUTFChars(jrole, role);
        env->ReleaseStringUTFChars(jcontent, content);
        env->DeleteLocalRef(jrole);
        env->DeleteLocalRef(jcontent);
    }

    jsize img_count = env->GetArrayLength(image_bytes);
    MNN::Transformer::MultimodalPrompt multimodal;
    if (!messages.empty()) {
        const auto& last_msg = messages.back();
        multimodal.prompt_template = last_msg.second;
    }
    for (jsize i = 0; i < img_count; i++) {
        jbyteArray jbytes = (jbyteArray)env->GetObjectArrayElement(image_bytes, i);
        if (!jbytes) continue;
        jsize size = env->GetArrayLength(jbytes);
        jbyte* data = env->GetByteArrayElements(jbytes, nullptr);
        if (data && size > 0) {
            std::vector<uint8_t> buf(reinterpret_cast<uint8_t*>(data),
                                     reinterpret_cast<uint8_t*>(data) + size);
            auto image_var = MNN::CV::imdecode(buf, MNN::CV::IMREAD_COLOR);
            if (image_var.get() != nullptr) {
                MNN::Transformer::PromptImagePart part;
                part.image_data = image_var;
                part.width = 0;
                part.height = 0;
                std::string key = "image_" + std::to_string(i);
                multimodal.images[key] = part;
                LOGI("image %zu decoded: %s", (size_t)i, key.c_str());
            } else {
                LOGE("imdecode failed for image %zu", (size_t)i);
            }
        }
        env->ReleaseByteArrayElements(jbytes, data, JNI_ABORT);
        env->DeleteLocalRef(jbytes);
    }

    std::ostringstream config_stream;
    config_stream << "{\"temperature\":" << temperature
                  << ",\"topK\":" << top_k
                  << ",\"topP\":" << top_p
                  << ",\"max_new_tokens\":" << max_tokens << "}";
    handle->llm->set_config(config_stream.str());

    if (multimodal.images.empty()) {
        LOGI("no valid images, falling back to text-only");
        runGenerate(handle, env, callback, messages, max_tokens, false, nullptr);
    } else {
        runGenerate(handle, env, callback, messages, max_tokens, true, &multimodal);
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_companion_chat_engine_MnnLlmJni_nativeReset(JNIEnv* env, jobject, jlong handle_ptr) {
    auto* handle = reinterpret_cast<LlmHandle*>(handle_ptr);
    if (!handle || !handle->llm) return;
    try {
        handle->llm->reset();
        LOGI("nativeReset: KV cache cleared");
    } catch (const std::exception& e) {
        LOGE("nativeReset error: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_com_companion_chat_engine_MnnLlmJni_nativeCancel(JNIEnv* env, jobject, jlong handle_ptr) {
    auto* handle = reinterpret_cast<LlmHandle*>(handle_ptr);
    if (!handle) return;
    handle->stop_requested = true;
    LOGI("nativeCancel: stop_requested=true");
}

JNIEXPORT void JNICALL
Java_com_companion_chat_engine_MnnLlmJni_nativeRelease(JNIEnv* env, jobject, jlong handle_ptr) {
    auto* handle = reinterpret_cast<LlmHandle*>(handle_ptr);
    if (!handle) return;
    if (handle->llm) {
        try {
            MNN::Transformer::Llm::destroy(handle->llm);
        } catch (...) {}
        handle->llm = nullptr;
    }
    delete handle;
    LOGI("nativeRelease: handle destroyed");
}

}
