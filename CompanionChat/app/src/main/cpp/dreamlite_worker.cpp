// dreamlite_worker.cpp — standalone native executable that runs one DreamLite
// image generation and writes the PNG result to a file path.
//
// Purpose: process-level memory isolation for consecutive image generations.
// ORT's scudo allocator leaves ~2.5GB of virtual address space mappings that
// cannot be released by mallopt(M_PURGE) or by destroying the Ort::Env. These
// mappings accumulate across generations and eventually trigger a system-level
// LMK kill on Gen 2+.
//
// By running each generation in a fresh process (this executable), the OS
// kernel reclaims ALL memory — physical RAM, virtual address space, scudo
// mappings, ORT thread-pool reservations — when the process exits. This
// guarantees a clean memory state for every generation, allowing unlimited
// consecutive generations with 4 ORT threads.
//
// Invocation:
//   dreamlite_worker <model_dir> <prompt> <width> <height> <steps> <seed> \
//                    <ref_latents_path> <strength> <out_latents_path> <out_png_path>
//
// Output:
//   - On success: writes PNG to <out_png_path>, exits 0.
//   - On failure: writes error message to <out_png_path>.err, exits 1.
//
// The parent process (LocalImageGenerationEngine) spawns this worker via
// ProcessBuilder, waits for it to exit, then reads the PNG file.

#include "pipeline.h"

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "DreamLiteWorker", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "DreamLiteWorker", __VA_ARGS__)
#else
#define LOGI(...) printf(__VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif

static void write_error_file(const std::string& png_path, const std::string& message) {
    std::ofstream err(png_path + ".err");
    if (err) {
        err << message;
    }
}

int main(int argc, char** argv) {
    if (argc < 11) {
        fprintf(stderr,
            "Usage: %s <model_dir> <prompt> <width> <height> <steps> <seed>"
            " <ref_latents> <strength> <out_latents> <out_png>\n",
            argv[0]);
        return 2;
    }

    const std::string model_dir    = argv[1];
    const std::string prompt       = argv[2];
    const int width                = std::atoi(argv[3]);
    const int height               = std::atoi(argv[4]);
    const int steps                = std::atoi(argv[5]);
    const long long seed           = std::atoll(argv[6]);
    const std::string ref_latents  = argv[7];
    const float strength           = std::strtof(argv[8], nullptr);
    const std::string out_latents  = argv[9];
    const std::string out_png      = argv[10];

    LOGI("DreamLiteWorker started: %dx%d steps=%d seed=%lld",
         width, height, steps, seed);

    DreamLitePipeline pipeline;
    if (!pipeline.load(model_dir)) {
        const std::string msg = "Failed to load DreamLite models from: " + model_dir;
        LOGE("%s", msg.c_str());
        write_error_file(out_png, msg);
        return 1;
    }

    GenerationConfig config;
    config.prompt                  = prompt;
    config.width                   = std::clamp(width, 128, 2048);
    config.height                  = std::clamp(height, 128, 2048);
    config.num_steps               = std::clamp(steps, 1, 50);
    config.seed                    = static_cast<int64_t>(seed);
    config.reference_latents_path  = ref_latents;
    config.strength                = strength;
    config.output_latents_path     = out_latents;

    ImageOutput output = pipeline.generate(config);

    if (output.pixels.empty() || output.width == 0 || output.height == 0) {
        const std::string msg = output.error_message.empty()
            ? "DreamLite pipeline returned an empty image"
            : output.error_message;
        LOGE("%s", msg.c_str());
        write_error_file(out_png, msg);
        return 1;
    }

    // Encode as PNG and write directly to the output file.
    const int stride = output.width * 3;
    int ok = stbi_write_png(
        out_png.c_str(),
        output.width,
        output.height,
        3,
        output.pixels.data(),
        stride
    );
    if (!ok) {
        const std::string msg = "Failed to encode PNG to: " + out_png;
        LOGE("%s", msg.c_str());
        write_error_file(out_png, msg);
        return 1;
    }

    LOGI("DreamLiteWorker done: %dx%d -> %s", output.width, output.height, out_png.c_str());
    return 0;
}
