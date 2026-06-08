#include <jni.h>

#include <android/log.h>
#include <stable-diffusion.h>

#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include <stb_image_write.h>

namespace {

constexpr const char * kTag = "CompanionSDJNI";

std::once_flag g_callbacks_once;

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

void sd_log_callback(sd_log_level_t level, const char * text, void *) {
    const int priority = level == SD_LOG_ERROR ? ANDROID_LOG_ERROR :
        level == SD_LOG_WARN ? ANDROID_LOG_WARN :
        level == SD_LOG_DEBUG ? ANDROID_LOG_DEBUG : ANDROID_LOG_INFO;
    __android_log_print(priority, kTag, "%s", text == nullptr ? "" : text);
}

void sd_progress_callback(int step, int steps, float time, void *) {
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "diffusion step %d/%d %.2fs",
        step,
        steps,
        time
    );
}

void ensure_callbacks() {
    std::call_once(g_callbacks_once, [] {
        sd_set_log_callback(sd_log_callback, nullptr);
        sd_set_progress_callback(sd_progress_callback, nullptr);
    });
}

struct SdCtxDeleter {
    void operator()(sd_ctx_t * ctx) const {
        if (ctx != nullptr) {
            free_sd_ctx(ctx);
        }
    }
};

using SdCtxPtr = std::unique_ptr<sd_ctx_t, SdCtxDeleter>;

struct ImageArray {
    sd_image_t * images = nullptr;
    int count = 0;

    ~ImageArray() {
        if (images != nullptr) {
            for (int i = 0; i < count; ++i) {
                free(images[i].data);
                images[i].data = nullptr;
            }
            free(images);
        }
    }
};

void png_write_callback(void * context, void * data, int size) {
    auto * bytes = static_cast<std::vector<uint8_t> *>(context);
    auto * begin = static_cast<uint8_t *>(data);
    bytes->insert(bytes->end(), begin, begin + size);
}

std::vector<uint8_t> encode_png(const sd_image_t & image) {
    if (image.data == nullptr || image.width == 0 || image.height == 0 || image.channel == 0) {
        throw std::runtime_error("stable-diffusion.cpp returned an empty image");
    }
    std::vector<uint8_t> png;
    const int stride = static_cast<int>(image.width * image.channel);
    const int ok = stbi_write_png_to_func(
        png_write_callback,
        &png,
        static_cast<int>(image.width),
        static_cast<int>(image.height),
        static_cast<int>(image.channel),
        image.data,
        stride
    );
    if (ok == 0 || png.empty()) {
        throw std::runtime_error("failed to encode Stable Diffusion result as PNG");
    }
    return png;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_companion_chat_data_image_StableDiffusionNative_generateTxt2ImgPng(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jstring vae_path,
    jstring taesd_path,
    jobjectArray lora_paths,
    jstring prompt,
    jstring negative_prompt,
    jint width,
    jint height,
    jint steps,
    jfloat cfg_scale,
    jlong seed,
    jboolean use_vulkan) {
    try {
        ensure_callbacks();

        const std::string model = to_string(env, model_path);
        if (model.empty()) {
            throw std::runtime_error("Stable Diffusion model_path is empty");
        }
        const std::string vae = to_string(env, vae_path);
        const std::string taesd = to_string(env, taesd_path);
        const std::string positive_prompt = to_string(env, prompt);
        const std::string negative = to_string(env, negative_prompt);
        const std::vector<std::string> lora_path_values = to_string_vector(env, lora_paths);

        sd_ctx_params_t ctx_params;
        sd_ctx_params_init(&ctx_params);
        ctx_params.model_path = model.c_str();
        ctx_params.vae_path = vae.empty() ? nullptr : vae.c_str();
        ctx_params.taesd_path = taesd.empty() ? nullptr : taesd.c_str();
        ctx_params.n_threads = static_cast<int>(std::max(2u, std::thread::hardware_concurrency()));
        ctx_params.rng_type = CPU_RNG;
        ctx_params.sampler_rng_type = CPU_RNG;
        ctx_params.lora_apply_mode = LORA_APPLY_AT_RUNTIME;
        ctx_params.keep_clip_on_cpu = use_vulkan == JNI_TRUE;
        ctx_params.keep_vae_on_cpu = false;
        ctx_params.flash_attn = true;
        ctx_params.diffusion_flash_attn = true;

        SdCtxPtr sd_ctx(new_sd_ctx(&ctx_params));
        if (sd_ctx == nullptr) {
            throw std::runtime_error("failed to load stable-diffusion.cpp context");
        }
        if (!sd_ctx_supports_image_generation(sd_ctx.get())) {
            throw std::runtime_error("loaded Stable Diffusion model does not support image generation");
        }

        std::vector<sd_lora_t> loras;
        loras.reserve(lora_path_values.size());
        for (const std::string & path : lora_path_values) {
            if (!path.empty()) {
                loras.push_back({false, 1.0f, path.c_str()});
            }
        }

        sd_img_gen_params_t gen_params;
        sd_img_gen_params_init(&gen_params);
        gen_params.loras = loras.empty() ? nullptr : loras.data();
        gen_params.lora_count = static_cast<uint32_t>(loras.size());
        gen_params.prompt = positive_prompt.c_str();
        gen_params.negative_prompt = negative.c_str();
        gen_params.width = std::clamp(static_cast<int>(width), 128, 2048);
        gen_params.height = std::clamp(static_cast<int>(height), 128, 2048);
        gen_params.sample_params.sample_steps = std::clamp(static_cast<int>(steps), 1, 50);
        gen_params.sample_params.sample_method = LCM_SAMPLE_METHOD;
        gen_params.sample_params.scheduler = LCM_SCHEDULER;
        gen_params.sample_params.guidance.txt_cfg = std::max(0.0f, static_cast<float>(cfg_scale));
        gen_params.seed = static_cast<int64_t>(seed);
        gen_params.batch_count = 1;
        // Tiling saves memory for large images, but on 512x512 mobile generation it
        // makes VAE decode much slower. Keep it only for larger outputs.
        gen_params.vae_tiling_params.enabled =
            gen_params.width > 512 || gen_params.height > 512;

        ImageArray results;
        results.count = 1;
        results.images = generate_image(sd_ctx.get(), &gen_params);
        if (results.images == nullptr) {
            throw std::runtime_error("stable-diffusion.cpp image generation failed");
        }

        const std::vector<uint8_t> png = encode_png(results.images[0]);
        jbyteArray output = env->NewByteArray(static_cast<jsize>(png.size()));
        if (output == nullptr) {
            throw std::runtime_error("failed to allocate Java PNG byte array");
        }
        env->SetByteArrayRegion(
            output,
            0,
            static_cast<jsize>(png.size()),
            reinterpret_cast<const jbyte *>(png.data())
        );
        return output;
    } catch (const std::exception & error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_companion_chat_data_image_StableDiffusionNative_systemInfo(JNIEnv * env, jobject) {
    ensure_callbacks();
    const char * info = sd_get_system_info();
    return env->NewStringUTF(info == nullptr ? "" : info);
}
