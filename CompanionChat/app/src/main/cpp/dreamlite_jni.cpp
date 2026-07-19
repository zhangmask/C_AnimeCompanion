#include <jni.h>

#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#include "pipeline.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"

namespace {

constexpr const char *kTag = "DreamLiteJNI";

std::once_flag g_init_once;

void throw_java(JNIEnv *env, const char *class_name, const std::string &message) {
    jclass clazz = env->FindClass(class_name);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message.c_str());
    }
}

std::string to_string(JNIEnv *env, jstring value) {
    if (value == nullptr) return "";
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void png_write_callback(void *context, void *data, int size) {
    auto *bytes = static_cast<std::vector<uint8_t> *>(context);
    auto *begin = static_cast<uint8_t *>(data);
    bytes->insert(bytes->end(), begin, begin + size);
}

std::vector<uint8_t> encode_png(const ImageOutput &image) {
    if (image.pixels.empty() || image.width == 0 || image.height == 0) {
        // If pipeline set a friendly error_message (e.g. generation limit reached,
        // memory too low), propagate it; otherwise fall back to a generic message.
        throw std::runtime_error(
            image.error_message.empty()
                ? "DreamLite pipeline returned an empty image"
                : image.error_message);
    }
    std::vector<uint8_t> png;
    const int stride = image.width * 3;
    int ok = stbi_write_png_to_func(
        png_write_callback,
        &png,
        image.width,
        image.height,
        3,
        image.pixels.data(),
        stride
    );
    if (ok == 0 || png.empty()) {
        throw std::runtime_error("failed to encode DreamLite result as PNG");
    }
    return png;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_companion_chat_data_image_DreamLiteNative_generateImagePng(
    JNIEnv *env,
    jobject,
    jstring model_dir,
    jstring prompt,
    jint width,
    jint height,
    jint steps,
    jlong seed,
    jstring reference_latents_path,
    jfloat strength,
    jstring output_latents_path) {
    try {
        const std::string model_path = to_string(env, model_dir);
        if (model_path.empty()) {
            throw std::runtime_error("DreamLite model_dir is empty");
        }
        const std::string prompt_text = to_string(env, prompt);
        if (prompt_text.empty()) {
            throw std::runtime_error("DreamLite prompt is empty");
        }

        __android_log_print(ANDROID_LOG_INFO, kTag,
            "Loading models from: %s", model_path.c_str());

        // Create and load pipeline
        DreamLitePipeline pipeline;
        if (!pipeline.load(model_path)) {
            throw std::runtime_error("Failed to load DreamLite models from: " + model_path);
        }

        __android_log_print(ANDROID_LOG_INFO, kTag,
            "Generating image: %dx%d, steps=%d, seed=%lld",
            (int)width, (int)height, (int)steps, (long long)seed);

        // Configure generation
        GenerationConfig config;
        config.prompt = prompt_text;
        config.width = std::clamp((int)width, 128, 2048);
        config.height = std::clamp((int)height, 128, 2048);
        config.num_steps = std::clamp((int)steps, 1, 50);
        config.seed = seed;  // jlong (64-bit) → int64_t, no truncation
        config.reference_latents_path = to_string(env, reference_latents_path);
        config.strength = strength;
        config.output_latents_path = to_string(env, output_latents_path);

        // Generate
        ImageOutput output = pipeline.generate(config);

        __android_log_print(ANDROID_LOG_INFO, kTag,
            "Image generated: %dx%d, pixels=%zu",
            output.width, output.height, output.pixels.size());

        // Encode as PNG
        std::vector<uint8_t> png = encode_png(output);

        __android_log_print(ANDROID_LOG_INFO, kTag,
            "PNG encoded: %zu bytes", png.size());

        // Return as Java byte array
        jbyteArray result = env->NewByteArray(static_cast<jsize>(png.size()));
        if (result == nullptr) {
            throw std::runtime_error("failed to allocate Java PNG byte array");
        }
        env->SetByteArrayRegion(
            result, 0,
            static_cast<jsize>(png.size()),
            reinterpret_cast<const jbyte *>(png.data())
        );
        return result;
    } catch (const std::exception &error) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Error: %s", error.what());
        throw_java(env, "java/lang/IllegalStateException", error.what());
        return nullptr;
    }
}
