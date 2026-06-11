#include <jni.h>

#include <android/log.h>
#include <llama.h>
#include <ggml.h>
#include <mtmd.h>
#include <mtmd-helper.h>

#include <atomic>
#include <algorithm>
#include <climits>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char * kTag = "CompanionLlamaJNI";
constexpr const char * kEndOfTurn = "<end_of_turn>";
constexpr const char * kStartOfTurn = "<start_of_turn>";
constexpr int32_t kDecodeSafetyTokens = 8;

struct LlamaRuntime {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    mtmd_context * mtmd = nullptr;
    const llama_vocab * vocab = nullptr;
    std::string system_prompt;
    std::atomic_bool canceled{false};
};

std::once_flag g_backend_once;

void log_info(const std::string & message) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", message.c_str());
}

double elapsed_ms(int64_t start_us, int64_t end_us) {
    return static_cast<double>(end_us - start_us) / 1000.0;
}

std::string fixed_2(double value) {
    char buffer[64];
    std::snprintf(buffer, sizeof(buffer), "%.2f", value);
    return std::string(buffer);
}

void throw_java(JNIEnv * env, const char * class_name, const std::string & message) {
    jclass clazz = env->FindClass(class_name);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message.c_str());
    }
}

std::string to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return "";
    }
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return "";
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<std::string> to_string_vector(JNIEnv * env, jobjectArray values) {
    std::vector<std::string> result;
    if (values == nullptr) {
        return result;
    }
    const jsize size = env->GetArrayLength(values);
    result.reserve(static_cast<size_t>(size));
    for (jsize i = 0; i < size; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        result.push_back(to_string(env, value));
        env->DeleteLocalRef(value);
    }
    return result;
}

std::vector<std::vector<uint8_t>> to_byte_vectors(JNIEnv * env, jobjectArray values) {
    std::vector<std::vector<uint8_t>> result;
    if (values == nullptr) {
        return result;
    }
    const jsize size = env->GetArrayLength(values);
    result.reserve(static_cast<size_t>(size));
    for (jsize i = 0; i < size; ++i) {
        auto value = static_cast<jbyteArray>(env->GetObjectArrayElement(values, i));
        if (value == nullptr) {
            result.emplace_back();
            continue;
        }
        const jsize length = env->GetArrayLength(value);
        std::vector<uint8_t> bytes(static_cast<size_t>(length));
        env->GetByteArrayRegion(
            value,
            0,
            length,
            reinterpret_cast<jbyte *>(bytes.data())
        );
        env->DeleteLocalRef(value);
        result.push_back(std::move(bytes));
    }
    return result;
}

LlamaRuntime * from_handle(jlong handle) {
    return reinterpret_cast<LlamaRuntime *>(static_cast<intptr_t>(handle));
}

bool abort_callback(void * data) {
    auto * runtime = static_cast<LlamaRuntime *>(data);
    return runtime != nullptr && runtime->canceled.load();
}

std::string apply_chat_template(LlamaRuntime * runtime,
                                const std::vector<std::string> & roles,
                                const std::vector<std::string> & contents) {
    std::vector<std::string> role_storage;
    std::vector<std::string> content_storage;
    std::vector<llama_chat_message> chat;
    role_storage.reserve(roles.size() + 1);
    content_storage.reserve(contents.size() + 1);
    chat.reserve(contents.size() + 1);

    if (!runtime->system_prompt.empty()) {
        role_storage.emplace_back("system");
        content_storage.push_back(runtime->system_prompt);
        chat.push_back({role_storage.back().c_str(), content_storage.back().c_str()});
    }

    for (size_t i = 0; i < roles.size(); ++i) {
        if (contents[i].empty()) {
            continue;
        }
        role_storage.push_back(roles[i]);
        content_storage.push_back(contents[i]);
        chat.push_back({role_storage.back().c_str(), content_storage.back().c_str()});
    }

    if (chat.empty()) {
        throw std::runtime_error("聊天历史为空");
    }

    const char * tmpl = llama_model_chat_template(runtime->model, nullptr);
    const char * selected_tmpl = tmpl;
    int32_t needed = llama_chat_apply_template(selected_tmpl, chat.data(), chat.size(), true, nullptr, 0);
    if (needed < 0) {
        log_info("模型 chat_template 不受当前 llama.cpp 支持，回退到 gemma");
        selected_tmpl = "gemma";
        needed = llama_chat_apply_template(selected_tmpl, chat.data(), chat.size(), true, nullptr, 0);
    }
    if (needed < 0) {
        log_info("gemma chat template 不可用，回退到 chatml");
        selected_tmpl = "chatml";
        needed = llama_chat_apply_template(selected_tmpl, chat.data(), chat.size(), true, nullptr, 0);
    }
    if (needed < 0) {
        throw std::runtime_error("无法应用模型聊天模板");
    }

    std::string prompt(static_cast<size_t>(needed) + 1, '\0');
    const int32_t written = llama_chat_apply_template(
        selected_tmpl,
        chat.data(),
        chat.size(),
        true,
        prompt.data(),
        needed + 1
    );
    if (written < 0) {
        throw std::runtime_error("聊天模板格式化失败");
    }
    prompt.resize(static_cast<size_t>(written));
    return prompt;
}

std::vector<llama_token> tokenize_prompt(LlamaRuntime * runtime, const std::string & prompt) {
    int32_t count = llama_tokenize(
        runtime->vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        nullptr,
        0,
        true,
        true
    );
    if (count == INT32_MIN) {
        throw std::runtime_error("prompt tokenization overflow");
    }
    if (count < 0) {
        count = -count;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    const int32_t actual = llama_tokenize(
        runtime->vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        tokens.data(),
        count,
        true,
        true
    );
    if (actual < 0) {
        throw std::runtime_error("prompt tokenization failed");
    }
    tokens.resize(static_cast<size_t>(actual));
    return tokens;
}

std::vector<char> token_to_piece(LlamaRuntime * runtime, llama_token token) {
    std::vector<char> buffer(32);
    int32_t size = llama_token_to_piece(
        runtime->vocab,
        token,
        buffer.data(),
        static_cast<int32_t>(buffer.size()),
        0,
        false
    );
    if (size < 0) {
        buffer.resize(static_cast<size_t>(-size));
        size = llama_token_to_piece(
            runtime->vocab,
            token,
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            0,
            false
        );
    }
    if (size < 0) {
        throw std::runtime_error("token detokenization failed");
    }
    buffer.resize(static_cast<size_t>(size));
    return buffer;
}

bool contains_turn_marker(const std::string & value) {
    return value.find(kEndOfTurn) != std::string::npos ||
        value.find(kStartOfTurn) != std::string::npos;
}

void emit_token(JNIEnv * env, jobject callback, jmethodID on_token, const std::vector<char> & piece) {
    if (piece.empty()) {
        return;
    }
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(piece.size()));
    if (bytes == nullptr) {
        throw std::runtime_error("无法分配 token 字节数组");
    }
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(piece.size()), reinterpret_cast<const jbyte *>(piece.data()));
    env->CallVoidMethod(callback, on_token, bytes);
    env->DeleteLocalRef(bytes);
    if (env->ExceptionCheck()) {
        throw std::runtime_error("token 回调失败");
    }
}

void emit_performance_log(JNIEnv * env, jobject callback, jmethodID on_log, const std::string & message) {
    log_info(message);
    if (on_log == nullptr) {
        return;
    }
    jstring java_message = env->NewStringUTF(message.c_str());
    if (java_message == nullptr) {
        return;
    }
    env->CallVoidMethod(callback, on_log, java_message);
    env->DeleteLocalRef(java_message);
    if (env->ExceptionCheck()) {
        throw std::runtime_error("性能日志回调失败");
    }
}

llama_sampler * create_sampler(float temperature, int32_t top_k, float top_p) {
    llama_sampler_chain_params params = llama_sampler_chain_default_params();
    llama_sampler * chain = llama_sampler_chain_init(params);
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return chain;
}

void generate_from_context(JNIEnv * env,
                           LlamaRuntime * runtime,
                           jobject callback,
                           jmethodID on_token,
                           jmethodID on_log,
                           int32_t prompt_positions,
                           int32_t max_tokens,
                           float temperature,
                           int32_t top_k,
                           float top_p,
                           int64_t t_start) {
    const uint32_t n_ctx = llama_n_ctx(runtime->ctx);
    const int32_t available_tokens = static_cast<int32_t>(
        std::max<int64_t>(
            0,
            static_cast<int64_t>(n_ctx) -
                static_cast<int64_t>(prompt_positions) -
                static_cast<int64_t>(kDecodeSafetyTokens)
        )
    );
    const int32_t generation_limit = std::min<int32_t>(
        std::max<int32_t>(max_tokens, 0),
        available_tokens
    );
    if (generation_limit <= 0) {
        emit_performance_log(
            env,
            callback,
            on_log,
            "生成提前结束: context 剩余 token 不足, prompt_positions=" +
                std::to_string(prompt_positions) +
                ", context_size=" + std::to_string(n_ctx)
        );
        return;
    }

    llama_sampler * sampler = create_sampler(temperature, top_k, top_p);
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler_guard(sampler, llama_sampler_free);

    llama_token next = llama_sampler_sample(sampler, runtime->ctx, -1);
    int32_t n_past = prompt_positions;
    int32_t generated_tokens = 0;
    int64_t t_first_token = 0;
    for (int32_t i = 0; i < generation_limit; ++i) {
        if (runtime->canceled.load() || llama_vocab_is_eog(runtime->vocab, next)) {
            break;
        }
        llama_sampler_accept(sampler, next);

        if (generated_tokens == 0) {
            t_first_token = llama_time_us();
            emit_performance_log(
                env,
                callback,
                on_log,
                "性能: first_token_ms=" + fixed_2(elapsed_ms(t_start, t_first_token))
            );
        }
        std::vector<char> piece = token_to_piece(runtime, next);
        const std::string piece_text(piece.begin(), piece.end());
        if (contains_turn_marker(piece_text)) {
            break;
        }
        emit_token(env, callback, on_token, piece);
        generated_tokens += 1;

        llama_batch batch = llama_batch_init(1, 0, 1);
        batch.n_tokens = 1;
        batch.token[0] = next;
        batch.pos[0] = n_past++;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;
        const int32_t result = llama_decode(runtime->ctx, batch);
        llama_batch_free(batch);
        if (result != 0) {
            emit_performance_log(
                env,
                callback,
                on_log,
                "生成提前结束: token decode 返回 " + std::to_string(result) +
                    ", generated_tokens=" + std::to_string(generated_tokens) +
                    ", generation_limit=" + std::to_string(generation_limit)
            );
            break;
        }
        next = llama_sampler_sample(sampler, runtime->ctx, -1);
    }
    const int64_t t_end = llama_time_us();
    const double generation_ms = generated_tokens > 0 && t_first_token > 0
        ? elapsed_ms(t_first_token, t_end)
        : 0.0;
    const double tokens_per_second = generation_ms > 0.0
        ? static_cast<double>(generated_tokens) * 1000.0 / generation_ms
        : 0.0;
    emit_performance_log(
        env,
        callback,
        on_log,
        "性能: generated_tokens=" + std::to_string(generated_tokens) +
            ", total_ms=" + fixed_2(elapsed_ms(t_start, t_end)) +
            ", generation_ms=" + fixed_2(generation_ms) +
            ", tokens_per_second=" + fixed_2(tokens_per_second)
    );
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_companion_chat_engine_LlamaCppNative_loadModel(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jstring mmproj_path,
    jint context_size,
    jstring system_prompt,
    jboolean use_gpu
) {
    try {
        std::call_once(g_backend_once, [] {
            llama_backend_init();
        });

        std::string path = to_string(env, model_path);
        std::string projector_path = to_string(env, mmproj_path);
        auto runtime = std::make_unique<LlamaRuntime>();
        runtime->system_prompt = to_string(env, system_prompt);

        llama_model_params model_params = llama_model_default_params();
        // GPU 加速：根据参数决定是否启用
        model_params.n_gpu_layers = use_gpu ? 99 : 0;
        log_info(std::string("GPU 加速: ") + (use_gpu ? "启用" : "禁用"));

        runtime->model = llama_model_load_from_file(path.c_str(), model_params);
        if (runtime->model == nullptr) {
            throw std::runtime_error("llama_model_load_from_file failed");
        }

        llama_context_params context_params = llama_context_default_params();
        context_params.n_ctx = static_cast<uint32_t>(context_size);
        context_params.n_batch = static_cast<uint32_t>(std::min(std::max(context_size, 1), 1024));
        context_params.n_ubatch = context_params.n_batch;
        context_params.n_threads = static_cast<int32_t>(std::max(2u, std::thread::hardware_concurrency()));
        context_params.n_threads_batch = context_params.n_threads;
        context_params.abort_callback = abort_callback;
        context_params.abort_callback_data = runtime.get();
        context_params.embeddings = false;
        // KV 缓存 GPU Offload
        context_params.offload_kqv = use_gpu;
        // Flash Attention
        context_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
        // KV 缓存量化：使用 Q8_0 量化减少内存使用
        context_params.type_k = GGML_TYPE_Q8_0;
        context_params.type_v = GGML_TYPE_Q8_0;

        runtime->ctx = llama_init_from_model(runtime->model, context_params);
        if (runtime->ctx == nullptr) {
            throw std::runtime_error("llama_init_from_model failed");
        }

        runtime->vocab = llama_model_get_vocab(runtime->model);
        if (runtime->vocab == nullptr) {
            throw std::runtime_error("模型 vocab 不可用");
        }

        if (!projector_path.empty()) {
            mtmd_context_params mtmd_params = mtmd_context_params_default();
            mtmd_params.use_gpu = false;
            mtmd_params.n_threads = context_params.n_threads;
            mtmd_params.print_timings = false;
            mtmd_params.warmup = false;
            runtime->mtmd = mtmd_init_from_file(projector_path.c_str(), runtime->model, mtmd_params);
            if (runtime->mtmd == nullptr) {
                log_info("mmproj 加载失败，GGUF 图片输入将不可用: " + projector_path);
            } else {
                log_info("mmproj model loaded");
            }
        }

        log_info("llama.cpp model loaded");
        return static_cast<jlong>(reinterpret_cast<intptr_t>(runtime.release()));
    } catch (const std::exception & e) {
        throw_java(env, "java/lang/IllegalStateException", e.what());
        return 0L;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_companion_chat_engine_LlamaCppNative_generate(
    JNIEnv * env,
    jobject,
    jlong handle,
    jobjectArray roles_array,
    jobjectArray contents_array,
    jint max_tokens,
    jfloat temperature,
    jint top_k,
    jfloat top_p,
    jobject callback
) {
    LlamaRuntime * runtime = from_handle(handle);
    if (runtime == nullptr || runtime->ctx == nullptr || runtime->model == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "llama.cpp runtime is not initialized");
        return;
    }

    try {
        const std::vector<std::string> roles = to_string_vector(env, roles_array);
        const std::vector<std::string> contents = to_string_vector(env, contents_array);
        if (roles.size() != contents.size()) {
            throw std::runtime_error("roles 与 contents 数量不一致");
        }

        jclass callback_class = env->GetObjectClass(callback);
        jmethodID on_token = env->GetMethodID(callback_class, "onTokenBytes", "([B)V");
        jmethodID on_log = env->GetMethodID(callback_class, "onPerformanceLog", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(callback_class);
        if (on_token == nullptr) {
            throw std::runtime_error("找不到 token 回调方法");
        }

        runtime->canceled.store(false);
        llama_memory_clear(llama_get_memory(runtime->ctx), true);

        const int64_t t_start = llama_time_us();
        const std::string prompt = apply_chat_template(runtime, roles, contents);
        std::vector<llama_token> prompt_tokens = tokenize_prompt(runtime, prompt);
        const int64_t t_prompt_ready = llama_time_us();
        const uint32_t n_ctx = llama_n_ctx(runtime->ctx);
        if (prompt_tokens.size() + 1 >= n_ctx) {
            throw std::runtime_error("聊天上下文超过模型 contextSize 限制");
        }

        emit_performance_log(
            env,
            callback,
            on_log,
            "性能: prompt_tokens=" + std::to_string(prompt_tokens.size()) +
                ", context_size=" + std::to_string(n_ctx) +
                ", template_tokenize_ms=" + fixed_2(elapsed_ms(t_start, t_prompt_ready))
        );

        const int32_t n_batch = static_cast<int32_t>(std::max(1u, llama_n_batch(runtime->ctx)));
        for (size_t offset = 0; offset < prompt_tokens.size(); offset += static_cast<size_t>(n_batch)) {
            if (runtime->canceled.load()) {
                return;
            }
            const int32_t count = static_cast<int32_t>(
                std::min(static_cast<size_t>(n_batch), prompt_tokens.size() - offset)
            );
            llama_batch batch = llama_batch_get_one(prompt_tokens.data() + offset, count);
            const int32_t result = llama_decode(runtime->ctx, batch);
            if (result != 0) {
                throw std::runtime_error("prompt decode failed: " + std::to_string(result));
            }
        }
        const int64_t t_prompt_decoded = llama_time_us();
        emit_performance_log(
            env,
            callback,
            on_log,
            "性能: prompt_decode_ms=" + fixed_2(elapsed_ms(t_prompt_ready, t_prompt_decoded))
        );

        generate_from_context(
            env,
            runtime,
            callback,
            on_token,
            on_log,
            static_cast<int32_t>(prompt_tokens.size()),
            max_tokens,
            temperature,
            top_k,
            top_p,
            t_start
        );
    } catch (const std::exception & e) {
        if (!env->ExceptionCheck()) {
            throw_java(env, "java/lang/RuntimeException", e.what());
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_companion_chat_engine_LlamaCppNative_generateMultimodal(
    JNIEnv * env,
    jobject,
    jlong handle,
    jstring prompt_text,
    jobjectArray image_bytes_array,
    jint max_tokens,
    jfloat temperature,
    jint top_k,
    jfloat top_p,
    jobject callback
) {
    LlamaRuntime * runtime = from_handle(handle);
    if (runtime == nullptr || runtime->ctx == nullptr || runtime->model == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "llama.cpp runtime is not initialized");
        return;
    }
    if (runtime->mtmd == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "mmproj runtime is not initialized");
        return;
    }

    try {
        jclass callback_class = env->GetObjectClass(callback);
        jmethodID on_token = env->GetMethodID(callback_class, "onTokenBytes", "([B)V");
        jmethodID on_log = env->GetMethodID(callback_class, "onPerformanceLog", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(callback_class);
        if (on_token == nullptr) {
            throw std::runtime_error("找不到 token 回调方法");
        }

        runtime->canceled.store(false);
        llama_memory_clear(llama_get_memory(runtime->ctx), true);

        const int64_t t_start = llama_time_us();
        std::string prompt = to_string(env, prompt_text);
        std::vector<std::vector<uint8_t>> image_bytes = to_byte_vectors(env, image_bytes_array);
        if (image_bytes.empty()) {
            throw std::runtime_error("图片输入为空");
        }

        std::vector<std::unique_ptr<mtmd_bitmap, decltype(&mtmd_bitmap_free)>> bitmaps;
        std::vector<const mtmd_bitmap *> bitmap_ptrs;
        bitmaps.reserve(image_bytes.size());
        bitmap_ptrs.reserve(image_bytes.size());
        for (const auto & bytes : image_bytes) {
            if (bytes.empty()) {
                throw std::runtime_error("图片字节为空");
            }
            mtmd_bitmap * bitmap = mtmd_helper_bitmap_init_from_buf(
                runtime->mtmd,
                bytes.data(),
                bytes.size()
            );
            if (bitmap == nullptr) {
                throw std::runtime_error("图片预处理失败");
            }
            bitmaps.emplace_back(bitmap, mtmd_bitmap_free);
            bitmap_ptrs.push_back(bitmap);
        }

        mtmd_input_text text {
            prompt.c_str(),
            true,
            true
        };
        std::unique_ptr<mtmd_input_chunks, decltype(&mtmd_input_chunks_free)> chunks(
            mtmd_input_chunks_init(),
            mtmd_input_chunks_free
        );
        if (!chunks) {
            throw std::runtime_error("无法创建多模态 chunks");
        }
        int32_t tokenize_result = mtmd_tokenize(
            runtime->mtmd,
            chunks.get(),
            &text,
            bitmap_ptrs.data(),
            bitmap_ptrs.size()
        );
        if (tokenize_result != 0) {
            throw std::runtime_error("多模态 tokenize 失败: " + std::to_string(tokenize_result));
        }

        const int32_t n_batch = static_cast<int32_t>(std::max(1u, llama_n_batch(runtime->ctx)));
        llama_pos new_n_past = 0;
        int32_t eval_result = mtmd_helper_eval_chunks(
            runtime->mtmd,
            runtime->ctx,
            chunks.get(),
            0,
            0,
            n_batch,
            true,
            &new_n_past
        );
        if (eval_result != 0) {
            throw std::runtime_error("多模态 prompt eval 失败: " + std::to_string(eval_result));
        }
        const int64_t t_prompt_decoded = llama_time_us();
        emit_performance_log(
            env,
            callback,
            on_log,
            "性能: multimodal_prompt_positions=" + std::to_string(new_n_past) +
                ", context_size=" + std::to_string(llama_n_ctx(runtime->ctx)) +
                ", multimodal_eval_ms=" + fixed_2(elapsed_ms(t_start, t_prompt_decoded))
        );

        generate_from_context(
            env,
            runtime,
            callback,
            on_token,
            on_log,
            static_cast<int32_t>(new_n_past),
            max_tokens,
            temperature,
            top_k,
            top_p,
            t_start
        );
    } catch (const std::exception & e) {
        if (!env->ExceptionCheck()) {
            throw_java(env, "java/lang/RuntimeException", e.what());
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_companion_chat_engine_LlamaCppNative_cancel(JNIEnv *, jobject, jlong handle) {
    LlamaRuntime * runtime = from_handle(handle);
    if (runtime != nullptr) {
        runtime->canceled.store(true);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_companion_chat_engine_LlamaCppNative_releaseModel(JNIEnv *, jobject, jlong handle) {
    LlamaRuntime * runtime = from_handle(handle);
    if (runtime == nullptr) {
        return;
    }
    runtime->canceled.store(true);
    if (runtime->mtmd != nullptr) {
        mtmd_free(runtime->mtmd);
    }
    if (runtime->ctx != nullptr) {
        llama_free(runtime->ctx);
    }
    if (runtime->model != nullptr) {
        llama_model_free(runtime->model);
    }
    delete runtime;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_companion_chat_engine_LlamaCppNative_systemInfo(JNIEnv * env, jobject) {
    return env->NewStringUTF("llama.cpp CPU-only arm64-v8a runtime");
}
