#include "pipeline.h"
#include "tokenizer.h"

#include <onnxruntime_cxx_api.h>
#include <nnapi_provider_factory.h>
#include <cmath>
#include <random>
#include <chrono>
#include <algorithm>
#include <numeric>
#include <cstring>
#include <iostream>
#include <fstream>
#include <thread>
#include <cstdarg>
#include <cstdio>

// stb_image: lightweight single-header PNG/JPEG decoder used to load the
// reference image for vision-based edit mode (img2img). Loaded image is
// resized to 256×256, normalized to [-1,1], and patchified into the
// pixel_values [1,256,1536] tensor expected by vision_encoder.onnx.
#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_PNG
#define STBI_NO_FAILURE_STRINGS  // keep binary small; we don't use stbi_failure_reason
#include <stb_image.h>
// stb_image_resize: single-header image resizer. STB_IMAGE_RESIZE_IMPLEMENTATION
// must be defined in exactly one translation unit to generate the implementation.
#define STB_IMAGE_RESIZE_IMPLEMENTATION
#include <stb_image_resize.h>

#ifdef __ANDROID__
#include <malloc.h>
#include <dlfcn.h>   // dlsym (for runtime mallopt M_PURGE)
#include <sys/resource.h>  // getrusage
#include <fcntl.h>   // open (for posix_fadvise)
#include <unistd.h>  // close, usleep
#include <sched.h>   // sched_yield

// Android's bionic libc doesn't export malloc_trim. Instead, scudo allocator
// (Android 12+) supports mallopt(M_PURGE, 0) to force-release freed pages.
// M_PURGE = 10 in bionic. We dlsym it at runtime for compatibility with
// older Android versions where it may not exist.
static void android_purge_allocator() {
    // mallopt is declared in <malloc.h> on bionic
    int (*mallopt_fn)(int, int) = (int(*)(int,int))dlsym(RTLD_DEFAULT, "mallopt");
    if (mallopt_fn) {
        // M_PURGE = 10 (bionic), forces scudo to release all free pages
        mallopt_fn(10, 0);
        // M_DECAY_TIME = 9 (bionic), set to 0 to disable deferred decay so
        // scudo releases freed memory immediately instead of batching. This
        // is critical for reducing VmSize growth across generations: with
        // default decay time, scudo holds freed pages for ~10s before
        // releasing; setting to 0 forces immediate release on free().
        mallopt_fn(9, 0);
    }
}

// Evict a file's pages from the kernel page cache. ORT reads ~2.2GB of model
// files (.onnx + .onnx.data) per generation, and the kernel caches these in
// Cached memory. After several generations, Cached grows to ~3.6GB, leaving
// MemFree as low as 600MB. When ORT then tries to load the text encoder
// (~400MB allocation), MemFree drops below the LMK threshold and the app is
// killed. By calling posix_fadvise(POSIX_FADV_DONTNEED) on model files after
// each generation, we release ~2.2GB of cache back to MemFree.
static void evict_file_cache(const std::string& path) {
    if (path.empty()) return;
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return;
    posix_fadvise(fd, 0, 0, POSIX_FADV_DONTNEED);
    close(fd);
}
#endif

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "DreamLitePipeline", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "DreamLitePipeline", __VA_ARGS__)
#else
#define LOGI(...) printf(__VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif

// File-based logging (logcat buffer is too small / C++ logs get evicted on this device)
#ifdef __ANDROID__
static const char* PIPELINE_LOG_PATH = "/data/data/com.companion.chat/files/pipeline_log.txt";
static void flog(const char* fmt, ...) {
    FILE* f = fopen(PIPELINE_LOG_PATH, "a");
    if (!f) return;
    va_list ap;
    va_start(ap, fmt);
    vfprintf(f, fmt, ap);
    fprintf(f, "\n");
    va_end(ap);
    fclose(f);
}
static void flog_clear() {
    FILE* f = fopen(PIPELINE_LOG_PATH, "w");
    if (f) fclose(f);
}
#else
static void flog(const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vprintf(fmt, ap);
    printf("\n");
    va_end(ap);
}
static void flog_clear() {}
#endif

// DEBUG_ALIGN: force WSL reference prompt + seed=42 for stage-by-stage comparison.
// Also gates save_bin debug file writes — must be defined BEFORE save_bin below.
#define DEBUG_ALIGN 0

// Save float array to binary file (for stage-by-stage alignment comparison)
// Gated by DEBUG_ALIGN: in production (DEBUG_ALIGN=0), save_bin is a no-op to
// avoid ~5MB of synchronous disk I/O per generation that adds memory pressure
// and can contribute to OOM kills during long reference-modification prompts.
#ifdef __ANDROID__
static void save_bin(const char* name, const float* data, size_t count) {
#if DEBUG_ALIGN
    std::string path = std::string("/data/data/com.companion.chat/files/dbg_") + name + ".bin";
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) { flog("[save_bin] FAILED to open %s", path.c_str()); return; }
    fwrite(data, sizeof(float), count, f);
    fclose(f);
    flog("[save_bin] %s: %zu floats -> %s", name, count, path.c_str());
#else
    (void)name; (void)data; (void)count;
#endif
}
static void save_bin(const char* name, const std::vector<float>& v) {
    save_bin(name, v.data(), v.size());
}
#else
static void save_bin(const char* name, const float* data, size_t count) {
    std::string path = std::string("dbg_") + name + ".bin";
    FILE* f = fopen(path.c_str(), "wb");
    if (f) { fwrite(data, sizeof(float), count, f); fclose(f); }
}
static void save_bin(const char* name, const std::vector<float>& v) {
    save_bin(name, v.data(), v.size());
}
#endif

#if DEBUG_ALIGN
static const char* WSL_REF_PROMPT =
    "a serene mountain lake at sunrise, crystal clear water reflecting the peaks, "
    "soft golden light, photorealistic";
static const int64_t WSL_REF_SEED = 42;
#endif

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
static std::wstring to_wide(const std::string& s) {
    int len = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), -1, nullptr, 0);
    std::wstring ws(len, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), -1, &ws[0], len);
    if (!ws.empty() && ws.back() == L'\0') ws.pop_back();
    return ws;
}
#define ORT_PATH(s) to_wide(s).c_str()
#else
#define ORT_PATH(s) (s).c_str()
#endif

// ---------------------------------------------------------------------------
//  Helpers
// ---------------------------------------------------------------------------

// ORT environment: created lazily and destroyed in full_reset() after each
// generation. This returns ALL ORT internal state to the OS — thread-pool
// reservations (~256MB virtual per thread × 4 threads × 3 sessions),
// allocator state, memory patterns, and virtual address mappings.
//
// Earlier code kept the Env persistent (to avoid a silent kill during
// Ort::Session construction on Gen 2). That was a workaround for the ORT
// state accumulation problem: keeping Env persistent meant ORT's internal
// state accumulated across generations, eventually causing a silent process
// kill on Gen 2+ during Ort::Session construction. By destroying the Env
// in full_reset() (along with sessions + session_opts + double
// mallopt(M_PURGE) + sched_yield + 500ms usleep + posix_fadvise), we give
// the OS enough time to reclaim all ORT virtual memory before the next
// generation starts. The Env is then lazily recreated by get_env() on the
// next generate() call.
//
// The 500ms usleep in full_reset() is CRITICAL: without it, the next
// Ort::Session constructor can race with ongoing kernel memory reclaim,
// causing the same silent kill that the persistent-Env workaround was
// trying to avoid. The usleep gives the kernel time to complete VM unmap.
static std::unique_ptr<Ort::Env> g_env_ptr;

static Ort::Env& get_env() {
    if (!g_env_ptr) {
        g_env_ptr = std::make_unique<Ort::Env>(ORT_LOGGING_LEVEL_WARNING, "dreamlite");
    }
    return *g_env_ptr;
}

static std::vector<int64_t> get_shape(const Ort::Value& v) {
    auto info = v.GetTensorTypeAndShapeInfo();
    return info.GetShape();
}

// Simple timer
struct Timer {
    std::chrono::high_resolution_clock::time_point start;
    Timer() : start(std::chrono::high_resolution_clock::now()) {}
    double elapsed_ms() const {
        auto now = std::chrono::high_resolution_clock::now();
        return std::chrono::duration<double, std::milli>(now - start).count();
    }
};

// numpy-compatible Gaussian RNG.
// std::mt19937 seeding matches numpy's rk_seed exactly (same 1812433253 constant),
// but numpy's rk_double uses TWO uint32 (53-bit mantissa) and rk_gauss uses polar
// Box-Muller — std::normal_distribution does NOT match.  We reimplement both so
// the generated noise is byte-identical to np.random.randn(seed).
static std::vector<float> numpy_randn(int64_t count, int64_t seed) {
    std::mt19937 gen(static_cast<uint32_t>(seed));
    bool has_gauss = false;
    double gauss = 0.0;

    auto rk_double = [&gen]() -> double {
        uint64_t a = static_cast<uint64_t>(gen());  // first uint32
        a <<= 32;
        a |= static_cast<uint64_t>(gen());           // second uint32
        a >>= 11;                                     // 53-bit mantissa
        return static_cast<double>(a) * (1.0 / 9007199254740992.0);  // 1/2^53
    };

    auto rk_gauss = [&]() -> double {
        if (has_gauss) {
            has_gauss = false;
            return gauss;
        }
        double x1, x2, r2;
        do {
            x1 = 2.0 * rk_double() - 1.0;
            x2 = 2.0 * rk_double() - 1.0;
            r2 = x1 * x1 + x2 * x2;
        } while (r2 >= 1.0 || r2 == 0.0);
        double f = std::sqrt(-2.0 * std::log(r2) / r2);
        gauss = f * x1;
        has_gauss = true;
        return f * x2;
    };

    std::vector<float> v(count);
    for (auto& x : v) x = static_cast<float>(rk_gauss());
    return v;
}

// fp32 -> fp16 conversion
static inline uint16_t fp32_to_fp16(float f) {
    uint32_t bits;
    std::memcpy(&bits, &f, 4);
    uint32_t sign = (bits >> 31) & 1;
    int32_t  exp  = ((bits >> 23) & 0xff) - 127 + 15;
    uint32_t mant = bits & 0x7fffff;

    if (exp <= 0) {
        if (exp < -10) return static_cast<uint16_t>(sign << 15);
        mant = (mant | 0x800000) >> (1 - exp);
        return static_cast<uint16_t>((sign << 15) | (mant >> 13));
    } else if (exp >= 31) {
        return static_cast<uint16_t>((sign << 15) | (0x1f << 10));
    }
    return static_cast<uint16_t>((sign << 15) | (exp << 10) | (mant >> 13));
}

static std::vector<uint16_t> to_fp16(const std::vector<float>& v) {
    std::vector<uint16_t> out(v.size());
    for (size_t i = 0; i < v.size(); i++) out[i] = fp32_to_fp16(v[i]);
    return out;
}

static std::vector<uint16_t> to_fp16(const float* data, size_t count) {
    std::vector<uint16_t> out(count);
    for (size_t i = 0; i < count; i++) out[i] = fp32_to_fp16(data[i]);
    return out;
}

// ---------------------------------------------------------------------------
//  Prompt template  (Generate / Edit modes)
// ---------------------------------------------------------------------------

static std::string format_prompt(const std::string& user_prompt, bool is_edit) {
    if (is_edit) {
        // Edit mode (vision-based img2img) — matches Python
        // pipeline_dreamlite_mobile.py + test_mobile_models.py:
        //   - system message asks the model to describe the input image's
        //     key features and apply the user's modification instruction
        //   - user message wraps the [Edit] instruction with vision tokens
        //     <|vision_start|><|image_pad|><|vision_end|>. The single
        //     <|image_pad|> here is a PLACEHOLDER — encode_prompt_edit()
        //     tokenizes this string then expands the placeholder into 64
        //     image_pad tokens (nvis = 256/16 * 256/16 / (2*2) = 64) before
        //     feeding to text_encoder_edit.onnx.
        //   - drop_idx = 64 (the 64 vision tokens are dropped from the
        //     final prompt_embeds, leaving only the text-instruction tokens
        //     for the UNet's cross-attention).
        std::string system_msg =
            "Describe the key features of the input image (color, shape, size, "
            "texture, objects, background), then explain how the user's text "
            "instruction should alter or modify the image. Generate a new image "
            "that meets the user's requirements while maintaining consistency "
            "with the original input where appropriate.";
        std::string edit_text =
            "[Edit]: A diptych with two side-by-side images of the same scene. "
            "Compared to the right side, the left one has " + user_prompt;
        return "<|im_start|>system\n" + system_msg + "<|im_end|>\n"
               "<|im_start|>user\n"
               "<|vision_start|><|image_pad|><|vision_end|>" + edit_text + "<|im_end|>\n"
               "<|im_start|>assistant\n";
    }
    // Generate mode (text-to-image): drop_idx=34
    std::string system_msg =
        "Describe the image by detailing the color, shape, size, texture, "
        "quantity, text, spatial relationships of the objects and background:";
    std::string user_msg = "[Generate]: " + user_prompt;
    return "<|im_start|>system\n" + system_msg + "<|im_end|>\n"
           "<|im_start|>user\n" + user_msg + "<|im_end|>\n"
           "<|im_start|>assistant\n";
}

// ---------------------------------------------------------------------------
//  Scheduler: FlowMatchEulerDiscrete with dynamic time-shifting
// ---------------------------------------------------------------------------

struct SchedulerState {
    std::vector<float> sigmas;     // length = num_steps + 1
    std::vector<float> timesteps;  // length = num_steps (sigmas[0..N-1] * 1000)

    // Config
    static constexpr float base_shift = 0.5f;
    static constexpr float max_shift  = 1.16f;  // from infer_edit.py / pipeline_dreamlite.py
    static constexpr int   base_seq_len = 256;
    static constexpr int   max_seq_len  = 4096;

    void init(int num_steps, int latent_h, int latent_w, bool use_linspace = false) {
        // image_seq_len = H*W / 4  (since latent channels=4 and we view as sequence)
        float image_seq_len = static_cast<float>(latent_h * latent_w) / 4.0f;

        // Linear interpolation for mu (matching infer_edit.py calculate_shift)
        float m = (max_shift - base_shift) / (max_seq_len - base_seq_len);
        float b = base_shift - m * base_seq_len;
        float mu = image_seq_len * m + b;
        float exp_mu = std::exp(mu);

        sigmas.resize(num_steps + 1);
        timesteps.resize(num_steps);
        if (use_linspace) {
            // Edit (img2img) mode: linspace sigmas + shifted timesteps.
            // Matches infer_edit.py exactly (verified to produce correct
            // img2img results). dt is uniform across steps.
            for (int i = 0; i < num_steps; i++) {
                float s = 1.0f - static_cast<float>(i) / num_steps;
                sigmas[i] = s;
                float t = mu * s / (1.0f + (mu - 1.0f) * s);
                timesteps[i] = t * 1000.0f;
            }
        } else {
            // Generate (txt2img) mode: shifted sigmas + shifted timesteps.
            // Matches pipeline_dreamlite.py (FlowMatchEulerDiscreteScheduler
            // with use_dynamic_shifting=True). dt is non-uniform (small early,
            // large late) — previously verified to produce good txt2img.
            for (int i = 0; i < num_steps; i++) {
                float s = 1.0f - static_cast<float>(i) / num_steps;
                float inv_s_minus_1 = (1.0f / s) - 1.0f;
                float shifted = exp_mu / (exp_mu + std::pow(inv_s_minus_1, 1.0f));
                sigmas[i] = shifted;
                timesteps[i] = shifted * 1000.0f;
            }
        }
        sigmas[num_steps] = 0.0f;  // terminal sigma
    }
};

// ---------------------------------------------------------------------------
//  DreamLitePipeline implementation
// ---------------------------------------------------------------------------

DreamLitePipeline::DreamLitePipeline() = default;
DreamLitePipeline::~DreamLitePipeline() = default;

bool DreamLitePipeline::load(const std::string& model_dir) {
    model_dir_ = model_dir;

    // Session options (created via init_session_options for reuse after full_reset)
    init_session_options();
    nnapi_available_ = false;

    std::string sep = "/";
#ifdef _WIN32
    sep = "\\";
#endif

    // Load tokenizer (small, always in memory)
    // Support both flat layout (vocab.json) and HuggingFace layout (tokenizer/vocab.json)
    tokenizer_ = std::make_unique<BpeTokenizer>();
    auto vocab_path = model_dir + sep + "vocab.json";
    auto merges_path = model_dir + sep + "merges.txt";
    if (!tokenizer_->load(vocab_path, merges_path)) {
        // Try HuggingFace tokenizer subdirectory
        vocab_path = model_dir + sep + "tokenizer" + sep + "vocab.json";
        merges_path = model_dir + sep + "tokenizer" + sep + "merges.txt";
        if (!tokenizer_->load(vocab_path, merges_path)) {
            std::cerr << "[load] Failed to load tokenizer from both layouts" << std::endl;
            return false;
        }
    }
    std::cout << "[load] Tokenizer loaded (vocab_size=" << tokenizer_->vocab_size() << ")" << std::endl;
    std::cout << "[load] Sessions will be loaded lazily (sequential) to save memory" << std::endl;

    // Verify that all model files exist (but don't load them)
    // Layout 1: custom INT4 names
    auto te_path = model_dir + sep + "text_encoder_int4.onnx";
    auto unet_path = model_dir + sep + "unet_1024_fp32.onnx";
    auto vae_path = model_dir + sep + "vae_1024_fp32.onnx";
    // Layout 2: standard HuggingFace diffusers ONNX layout
    auto te_path_hf = model_dir + sep + "text_encoder" + sep + "model.onnx";
    auto unet_path_hf = model_dir + sep + "unet" + sep + "model.onnx";
    auto vae_path_hf = model_dir + sep + "vae_decoder" + sep + "model.onnx";
    // Layout 3: DreamLite official export (guanfang/zhuanhuan) flat names
    auto te_path_dl = model_dir + sep + "text_encoder_generate.onnx";
    auto unet_path_dl = model_dir + sep + "unet.onnx";
    auto vae_path_dl = model_dir + sep + "vae_decoder.onnx";

    auto file_exists = [](const std::string& p) {
        std::ifstream f(p);
        return f.good();
    };

    if (file_exists(te_path) && file_exists(unet_path) && file_exists(vae_path)) {
        std::cout << "[load] Using custom INT4 model filenames" << std::endl;
    } else if (file_exists(te_path_hf) && file_exists(unet_path_hf) && file_exists(vae_path_hf)) {
        te_path = te_path_hf;
        unet_path = unet_path_hf;
        vae_path = vae_path_hf;
        std::cout << "[load] Using HuggingFace standard layout" << std::endl;
    } else if (file_exists(te_path_dl) && file_exists(unet_path_dl) && file_exists(vae_path_dl)) {
        te_path = te_path_dl;
        unet_path = unet_path_dl;
        vae_path = vae_path_dl;
        std::cout << "[load] Using DreamLite official export layout" << std::endl;
    } else {
        for (const auto& p : {te_path, unet_path, vae_path,
                               te_path_hf, unet_path_hf, vae_path_hf,
                               te_path_dl, unet_path_dl, vae_path_dl}) {
            if (!file_exists(p)) {
                std::cerr << "[load] Model file not found: " << p << std::endl;
            }
        }
        return false;
    }

    // Store resolved paths for lazy loading
    te_path_ = te_path;
    unet_path_ = unet_path;
    vae_path_ = vae_path;

    // Optional: vision_encoder.onnx + text_encoder_edit.onnx for real
    // vision-based img2img edit mode. If absent, edit mode is disabled and
    // requests with reference_latents_path will return an error.
    auto vis_path = model_dir + sep + "vision_encoder.onnx";
    auto te_edit_path = model_dir + sep + "text_encoder_edit.onnx";
    if (file_exists(vis_path)) {
        vis_path_ = vis_path;
        std::cout << "[load] Vision encoder found: " << vis_path << std::endl;
    } else {
        std::cout << "[load] Vision encoder NOT found — img2img edit mode disabled" << std::endl;
    }
    if (file_exists(te_edit_path)) {
        te_edit_path_ = te_edit_path;
        std::cout << "[load] Text encoder (edit) found: " << te_edit_path << std::endl;
    } else {
        std::cout << "[load] Text encoder (edit) NOT found — img2img edit mode disabled" << std::endl;
    }

    return true;
}

// ---------------------------------------------------------------------------
//  Sequential session management (load → use → release)
// ---------------------------------------------------------------------------

void DreamLitePipeline::init_session_options() {
    // use_arena=0: disable ORT's memory arena so that when a session is
    // destroyed, its memory is returned to the OS via free(). With the arena
    // enabled (default), destroyed sessions leave memory pooled in the arena
    // and never return it to the OS — causing OOM on consecutive generations.
    //
    // threads=4: Each ORT thread gets a 256MB scudo:primary_reserve virtual
    // mapping; with 3 sessions × 4 threads = 12 ORT threads. We destroy the
    // Env in full_reset() to return ALL ORT state (including thread-pool
    // reservations) to the OS after each generation, so virtual address space
    // does not accumulate across generations. This allows us to keep 4 threads
    // for performance (~10s/UNet step) while avoiding the Gen 2 silent kill
    // that came from ORT internal state accumulation.
    unsigned int threads = 4u;

    // CPU session options (for text encoder / UNet / VAE)
    session_opts_ = std::make_unique<Ort::SessionOptions>();
    session_opts_->SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
    session_opts_->AddConfigEntry("session.use_memory_pattern", "1");
    session_opts_->AddConfigEntry("session.use_arena", "0");
    session_opts_->AddConfigEntry("ep.dynamic_cpu_memory", "1");
    session_opts_->SetIntraOpNumThreads(static_cast<int>(threads));

    // NNAPI session options (currently unused — NNAPI disabled)
    session_opts_accel_ = std::make_unique<Ort::SessionOptions>();
    session_opts_accel_->SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
    session_opts_accel_->AddConfigEntry("session.use_memory_pattern", "1");
    session_opts_accel_->AddConfigEntry("session.use_arena", "0");
    session_opts_accel_->SetIntraOpNumThreads(static_cast<int>(threads));

    std::cout << "[load] Session options created (" << threads << " threads)" << std::endl;
}

void DreamLitePipeline::full_reset() {
    // Destroy ORT sessions + session options + Ort::Env to return ALL
    // ORT-managed memory to the OS. Sessions hold model weights, kernels,
    // memory patterns; session_opts_ hold patterns that accumulate across
    // create/destroy cycles; the Env holds the ORT thread-pool, allocator
    // state, and virtual address reservations (~256MB per ORT thread).
    //
    // Destroying the Env is CRITICAL for unlimited consecutive generations:
    // if the Env is kept persistent, ORT's internal state accumulates across
    // generations and eventually causes a silent process kill during
    // Ort::Session construction on Gen 2+. By destroying the Env here (after
    // sessions are already destroyed), we release all ORT virtual address
    // space. The Env is lazily recreated by get_env() on the next generate().
    //
    // The 500ms usleep below gives the kernel time to complete VM unmap
    // before the next generation starts, avoiding a race that can cause
    // the same silent kill during Env recreation + Session construction.
    text_encoder_session_.reset();
    vision_encoder_session_.reset();
    text_encoder_edit_session_.reset();
    unet_session_.reset();
    vae_session_.reset();
    session_opts_.reset();
    session_opts_accel_.reset();
    // Destroy the Env LAST (after sessions), so ORT's internal teardown
    // logic can cleanly detach sessions from the Env before the Env itself
    // is destroyed. This order matches ORT's expected destruction sequence.
    g_env_ptr.reset();
#ifdef __ANDROID__
    // Aggressive scudo cleanup: call purge 4 times with sched_yield between.
    // The first purge marks freed scudo pages as purgeable; subsequent
    // purges (after yields) force scudo to actually munmap them, returning
    // virtual address space to the OS. We also set M_DECAY_TIME=0 (in
    // android_purge_allocator) to disable deferred decay. This multi-pass
    // approach is critical: a single purge leaves many pages in a
    // "pending release" state; multiple passes with yields give scudo's
    // background release logic time to complete. This directly targets the
    // Gen 2 silent kill: scudo's per-thread primary_reserve virtual mappings
    // (256MB each × 12 ORT threads = 3GB) must be released before Gen 2
    // starts, otherwise VmSize stays at ~17GB and the next Ort::Session
    // constructor triggers a silent kill (likely vivo kernel VM limit).
    for (int i = 0; i < 4; i++) {
        android_purge_allocator();
        sched_yield();
        usleep(50 * 1000);  // 50ms between passes
    }
    // Evict model file pages from kernel page cache. ORT reads ~2.2GB of
    // model files (.onnx + .onnx.data) per generation; without this, Cached
    // grows to ~5GB and MemFree drops below 2GB, triggering our safety abort
    // on the next generation. Evicting here (after sessions are destroyed)
    // is safe — no file handles are open by ORT, so no race with active
    // mmap reads. We evict both the .onnx and the .onnx.data external
    // weights file.
    evict_file_cache(te_path_);
    evict_file_cache(te_path_ + ".data");
    evict_file_cache(unet_path_);
    evict_file_cache(unet_path_ + ".data");
    evict_file_cache(vae_path_);
    evict_file_cache(vae_path_ + ".data");
    // Final purge after fadvise (file cache eviction may have freed some
    // scudo buffers used during the last session's file I/O).
    android_purge_allocator();
    // Longer sleep (1500ms) to let the kernel complete page cache reclaim
    // and scudo virtual memory unmap. The Gen 2 silent kill appears to be
    // caused by virtual address space not being fully reclaimed before the
    // next Ort::Session constructor starts; giving the kernel more time to
    // complete VM unmap is critical. 1500ms is empirically chosen to be
    // long enough for scudo to release per-thread primary_reserve mappings
    // while not being too long to noticeably delay the user.
    usleep(1500 * 1000);
    // Log memory after full reset
    {
        FILE* f = fopen("/proc/meminfo", "r");
        if (f) {
            char line[256];
            long mem_free = -1, cached = -1;
            while (fgets(line, sizeof(line), f)) {
                if (sscanf(line, "MemFree: %ld kB", &mem_free) == 1) continue;
                if (sscanf(line, "Cached: %ld kB", &cached) == 1) continue;
            }
            fclose(f);
            flog("[reset] full_reset done. MemFree=%ld kB (%.1f MB)  Cached=%ld kB (%.1f MB)",
                 mem_free, mem_free / 1024.0, cached, cached / 1024.0);
        }
        // Also log VmSize to track virtual address space growth across gens
        FILE* sf = fopen("/proc/self/status", "r");
        if (sf) {
            char line[256];
            long vm_size = -1, vm_peak = -1;
            while (fgets(line, sizeof(line), sf)) {
                if (sscanf(line, "VmSize: %ld kB", &vm_size) == 1) continue;
                if (sscanf(line, "VmPeak: %ld kB", &vm_peak) == 1) continue;
            }
            fclose(sf);
            flog("[reset] VmSize=%ld kB (%.1f GB)  VmPeak=%ld kB (%.1f GB)",
                 vm_size, vm_size / 1048576.0, vm_peak, vm_peak / 1048576.0);
        }
    }
#endif
    std::cout << "[reset] full_reset: sessions + session_opts + env destroyed" << std::endl;
}

void DreamLitePipeline::ensure_text_encoder() {
    if (!session_opts_) init_session_options();
    if (!text_encoder_session_) {
        auto& path = te_path_;
        flog("[ensure_text_encoder] loading model: %s", path.c_str());
#ifdef __ANDROID__
        // Dump detailed process memory stats before the (potentially fatal)
        // Ort::Session constructor. The 3rd-generation silent kill happens
        // here, so we need VmSize/VmRSS/VmPeak/Threads to diagnose whether
        // it's virtual address space exhaustion, thread leak, or RSS growth.
        {
            FILE* f = fopen("/proc/self/status", "r");
            if (f) {
                char line[256];
                while (fgets(line, sizeof(line), f)) {
                    if (strstr(line, "VmSize") || strstr(line, "VmRSS") ||
                        strstr(line, "VmPeak") || strstr(line, "VmHWM") ||
                        strstr(line, "Threads") || strstr(line, "VmLck")) {
                        // strip trailing newline
                        size_t n = strlen(line);
                        if (n > 0 && line[n-1] == '\n') line[n-1] = 0;
                        flog("[proc] %s", line);
                    }
                }
                fclose(f);
            }
            // Also log RLIMIT_AS (address space limit)
            FILE* lf = fopen("/proc/self/limits", "r");
            if (lf) {
                char line[512];
                while (fgets(line, sizeof(line), lf)) {
                    if (strstr(line, "Max address space")) {
                        size_t n = strlen(line);
                        if (n > 0 && line[n-1] == '\n') line[n-1] = 0;
                        flog("[limit] %s", line);
                        break;
                    }
                }
                fclose(lf);
            }
            // Summarize /proc/self/maps to identify VM accumulation sources.
            // Group by category (heap, stack, mmap file, anon) and log the
            // top 10 largest mappings. This is critical for diagnosing the
            // 16GB+ VmSize that causes the device reboot on Gen 4+.
            {
                FILE* mf = fopen("/proc/self/maps", "r");
                if (mf) {
                    char line[512];
                    unsigned long total_vm = 0;
                    unsigned long total_anon = 0;
                    unsigned long total_file = 0;
                    int num_mappings = 0;
                    struct BigMap { unsigned long size; char desc[128]; };
                    BigMap big[10];
                    for (int i = 0; i < 10; i++) { big[i].size = 0; big[i].desc[0] = 0; }
                    while (fgets(line, sizeof(line), mf)) {
                        unsigned long start, end;
                        char perms[8];
                        int parsed = sscanf(line, "%lx-%lx %7s", &start, &end, perms);
                        if (parsed < 3) continue;
                        unsigned long sz = (end - start) / 1024;  // KB
                        total_vm += sz;
                        num_mappings++;
                        // Determine if anon or file-backed
                        char* nl = strchr(line, '\n');
                        if (nl) *nl = 0;
                        const char* path = strrchr(line, ' ');
                        if (path) path++;
                        if (path && (strstr(path, "[heap]") || strstr(path, "[stack") || strstr(path, "[anon") || path[0] == 0 || (line + strlen(line) - path) < 2)) {
                            total_anon += sz;
                        } else {
                            total_file += sz;
                        }
                        // Track top 10 largest
                        for (int i = 0; i < 10; i++) {
                            if (sz > big[i].size) {
                                // Shift down
                                for (int j = 9; j > i; j--) {
                                    big[j] = big[j-1];
                                }
                                big[i].size = sz;
                                // Extract description (last token or perms+path)
                                const char* desc_start = strrchr(line, ' ');
                                if (!desc_start || strlen(desc_start) < 2) desc_start = perms;
                                else desc_start++;
                                strncpy(big[i].desc, desc_start, 127);
                                big[i].desc[127] = 0;
                                break;
                            }
                        }
                    }
                    fclose(mf);
                    flog("[maps] %d mappings, total=%lu KB (%.1f GB), anon=%lu KB, file=%lu KB",
                         num_mappings, total_vm, total_vm/1048576.0, total_anon, total_file);
                    for (int i = 0; i < 10 && big[i].size > 0; i++) {
                        flog("[maps] #%d: %lu KB (%.1f GB) - %s", i+1, big[i].size, big[i].size/1048576.0, big[i].desc);
                    }
                }
            }
        }
#endif
        Timer t;
        text_encoder_session_ = std::make_unique<Ort::Session>(get_env(), ORT_PATH(path), *session_opts_);
        flog("[ensure_text_encoder] loaded in %.0f ms", t.elapsed_ms());
    }
}

void DreamLitePipeline::release_text_encoder() {
    // Destroy text_encoder session to free ~400MB before loading UNet.
    //
    // This is CRITICAL: UNet needs ~2.5GB RSS. Without releasing text_encoder
    // first, physical memory runs out during UNet denoising, causing a
    // kernel-level crash and device reboot (verified: text_encoder kept
    // resident + MemFree=1.1GB + Cached=5GB → device reboot during UNet
    // Step 1, uptime reset to 0).
    //
    // Trade-off: re-creating Ort::Session on Gen 2+ triggers an intermittent
    // silent process kill (no tombstone, no LMK log, MemFree>4GB). We accept
    // this trade-off because:
    //   - The silent kill is recoverable (app restarts, user retries)
    //   - The device reboot from text_encoder persistence is NOT recoverable
    //     (entire device restarts, all user state lost)
    text_encoder_session_.reset();
#ifdef __ANDROID__
    // Force allocator to return freed memory to OS. Without this, ORT's
    // session destruction leaves ~400MB pooled in the allocator's free lists,
    // causing OOM on subsequent generations.
    android_purge_allocator();
#endif
    std::cout << "[release] Text encoder released (purge done)" << std::endl;
    flog("[release] text_encoder released + purge");
}

// ---------------------------------------------------------------------------
//  Vision encoder + text_encoder_edit session management (img2img edit only)
//
//  Loaded together at the start of edit mode, used by encode_prompt_edit(),
//  then released before UNet loads (matching the txt2img text-encoder pattern).
//  Combined footprint ~1.5GB (vision ~600MB + text_encoder_edit ~900MB), so
//  both must be released before UNet (2.5GB) loads to avoid OOM.
// ---------------------------------------------------------------------------

void DreamLitePipeline::ensure_vision_encoder() {
    if (!session_opts_) init_session_options();
    if (!vision_encoder_session_ && !vis_path_.empty()) {
        auto& path = vis_path_;
        flog("[ensure_vision_encoder] loading model: %s", path.c_str());
        Timer t;
        vision_encoder_session_ = std::make_unique<Ort::Session>(get_env(), ORT_PATH(path), *session_opts_);
        flog("[ensure_vision_encoder] loaded in %.0f ms", t.elapsed_ms());
    }
}

void DreamLitePipeline::release_vision_encoder() {
    vision_encoder_session_.reset();
#ifdef __ANDROID__
    android_purge_allocator();
#endif
    flog("[release] vision_encoder released + purge");
}

void DreamLitePipeline::ensure_text_encoder_edit() {
    if (!session_opts_) init_session_options();
    if (!text_encoder_edit_session_ && !te_edit_path_.empty()) {
        auto& path = te_edit_path_;
        flog("[ensure_text_encoder_edit] loading model: %s", path.c_str());
        // text_encoder_edit.onnx has the same INT4 quantization as
        // text_encoder_generate.onnx and triggers the same
        // SimplifiedLayerNormFusion graph error under ORT_ENABLE_ALL.
        // Use ORT_ENABLE_BASIC to match the verified-working Python flow
        // (test_mobile_models.py uses te_opts with ORT_ENABLE_BASIC).
        // Note: Ort::SessionOptions has a deleted copy constructor, so we
        // build a fresh options object instead of copying *session_opts_.
        Ort::SessionOptions opts;
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_BASIC);
        opts.AddConfigEntry("session.use_memory_pattern", "1");
        opts.AddConfigEntry("session.use_arena", "0");
        opts.AddConfigEntry("ep.dynamic_cpu_memory", "1");
        opts.SetIntraOpNumThreads(4);
        Timer t;
        text_encoder_edit_session_ = std::make_unique<Ort::Session>(get_env(), ORT_PATH(path), opts);
        flog("[ensure_text_encoder_edit] loaded in %.0f ms", t.elapsed_ms());
    }
}

void DreamLitePipeline::release_text_encoder_edit() {
    text_encoder_edit_session_.reset();
#ifdef __ANDROID__
    android_purge_allocator();
#endif
    flog("[release] text_encoder_edit released + purge");
}

void DreamLitePipeline::ensure_unet() {
    if (!session_opts_) init_session_options();
    if (!unet_session_) {
        auto& path = unet_path_;
        std::cout << "[load] UNet: " << path << std::endl;
        Timer t;
        if (nnapi_available_) {
            try {
                unet_session_ = std::make_unique<Ort::Session>(get_env(), ORT_PATH(path), *session_opts_accel_);
                std::cout << "[load] UNet loaded (NNAPI) in " << t.elapsed_ms() << " ms" << std::endl;
                return;
            } catch (const std::exception& e) {
                std::cout << "[load] UNet NNAPI failed: " << e.what() << ", falling back to CPU" << std::endl;
            }
        }
        unet_session_ = std::make_unique<Ort::Session>(get_env(), ORT_PATH(path), *session_opts_);
        std::cout << "[load] UNet loaded (CPU) in " << t.elapsed_ms() << " ms" << std::endl;
    }
}

void DreamLitePipeline::release_unet() {
    unet_session_.reset();
#ifdef __ANDROID__
    android_purge_allocator();
#endif
    std::cout << "[release] UNet released (purge done)" << std::endl;
    flog("[release] unet released + purge");
}

void DreamLitePipeline::ensure_vae() {
    if (!session_opts_) init_session_options();
    if (!vae_session_) {
        auto& path = vae_path_;
        std::cout << "[load] VAE: " << path << std::endl;
        Timer t;
        if (nnapi_available_) {
            try {
                vae_session_ = std::make_unique<Ort::Session>(get_env(), ORT_PATH(path), *session_opts_accel_);
                std::cout << "[load] VAE loaded (NNAPI) in " << t.elapsed_ms() << " ms" << std::endl;
                return;
            } catch (const std::exception& e) {
                std::cout << "[load] VAE NNAPI failed: " << e.what() << ", falling back to CPU" << std::endl;
            }
        }
        vae_session_ = std::make_unique<Ort::Session>(get_env(), ORT_PATH(path), *session_opts_);
        std::cout << "[load] VAE loaded (CPU) in " << t.elapsed_ms() << " ms" << std::endl;
    }
}

void DreamLitePipeline::release_vae() {
    vae_session_.reset();
#ifdef __ANDROID__
    android_purge_allocator();
#endif
    std::cout << "[release] VAE released (purge done)" << std::endl;
    flog("[release] vae released + purge");
}

// ---------------------------------------------------------------------------
//  Stage 1: Encode prompt
// ---------------------------------------------------------------------------

// Vision preprocessing constants (matches test_mobile_models.py):
//   SZ=256 (image size), P=16 (patch size), T=2 (temporal patch size),
//   SM=2 (spatial merge size).
//   hg=wg=SZ/P=16 patches per axis, nvis=hg*wg/(SM*SM)=64 vision tokens,
//   pdim=3*T*P*P=1536 (per-patch feature dim).
namespace {
constexpr int VIS_SZ = 256;
constexpr int VIS_P  = 16;
constexpr int VIS_T  = 2;
constexpr int VIS_SM = 2;
constexpr int VIS_HG = VIS_SZ / VIS_P;          // 16
constexpr int VIS_WG = VIS_SZ / VIS_P;          // 16
constexpr int VIS_NPATCH = VIS_HG * VIS_WG;     // 256
constexpr int VIS_PDIM = 3 * VIS_T * VIS_P * VIS_P;  // 1536
constexpr int VIS_NVIS = VIS_NPATCH / (VIS_SM * VIS_SM);  // 64
constexpr int VIS_HIDDEN = 2048;                // text encoder hidden size
}

// Compute 3D M-RoPE position_ids tensor [3, 1, seq_len] for text_encoder_edit.
// Ported from test_mobile_models.py:compute_position_ids.
// Layout: dim 0 = temporal, dim 1 = height, dim 2 = width.
//   - text tokens before vision (indices [0, pad_pos)): positions 0..pad_pos-1
//     broadcast across all 3 dims.
//   - vision tokens (indices [pad_pos, pad_pos + nvis)): 3D positions:
//       dim 0 (t): all same value (current_pos, since t=1)
//       dim 1 (h): repeat each h value SM*SM times (= w_count), tile by t_count
//       dim 2 (w): cycle 0..w_count-1, tiled h_count * t_count times
//   - text tokens after vision: positions continue from current_pos + h_count.
static void compute_position_ids(int64_t* pos_out, int seq_len, int pad_pos, int nvis,
                                 const int64_t grid_thw[3], int spatial_merge_size) {
    // pos_out is shape [3, 1, seq_len], row-major: pos_out[dim * seq_len + idx]
    int t = static_cast<int>(grid_thw[0]);
    int h = static_cast<int>(grid_thw[1]) / spatial_merge_size;
    int w = static_cast<int>(grid_thw[2]) / spatial_merge_size;
    std::memset(pos_out, 0, sizeof(int64_t) * 3 * seq_len);

    int current_pos = 0;
    int text_len1 = pad_pos;
    if (text_len1 > 0) {
        for (int i = 0; i < text_len1; i++) {
            int p = current_pos + i;
            pos_out[0 * seq_len + i] = p;
            pos_out[1 * seq_len + i] = p;
            pos_out[2 * seq_len + i] = p;
        }
        current_pos += text_len1;
    }

    int vis_start = pad_pos;
    int vis_len = nvis;
    // pos_t: pos_t[i] = (i / (h*w)) + current_pos  (np.arange(t) repeated h*w times)
    // pos_h: pos_h[i] = (i / w) % h + current_pos  (np.arange(h) repeated w times, tiled t)
    // pos_w: pos_w[i] = i % w + current_pos         (np.arange(w) tiled h*t times)
    for (int i = 0; i < vis_len; i++) {
        pos_out[0 * seq_len + vis_start + i] = (i / (h * w)) + current_pos;
        pos_out[1 * seq_len + vis_start + i] = ((i / w) % h) + current_pos;
        pos_out[2 * seq_len + vis_start + i] = (i % w) + current_pos;
    }
    current_pos += std::max(static_cast<int>(grid_thw[1]), static_cast<int>(grid_thw[2])) / spatial_merge_size;

    int text_start = vis_start + vis_len;
    int text_len2 = seq_len - text_start;
    if (text_len2 > 0) {
        for (int i = 0; i < text_len2; i++) {
            int p = current_pos + i;
            pos_out[0 * seq_len + text_start + i] = p;
            pos_out[1 * seq_len + text_start + i] = p;
            pos_out[2 * seq_len + text_start + i] = p;
        }
    }
}

std::vector<float> DreamLitePipeline::encode_prompt_edit(
    const std::string& user_prompt,
    const std::string& reference_latents_path,
    int& seq_len) {

    flog("[encode_edit] step1: vision preprocessing start");

    // Derive reference PNG path from latents path:
    //   ".../image_123.latents.bin" → ".../image_123.png"
    std::string ref_png_path = reference_latents_path;
    const std::string suffix = ".latents.bin";
    if (ref_png_path.size() > suffix.size() &&
        ref_png_path.compare(ref_png_path.size() - suffix.size(), suffix.size(), suffix) == 0) {
        ref_png_path = ref_png_path.substr(0, ref_png_path.size() - suffix.size()) + ".png";
    } else {
        flog("[encode_edit] WARNING: reference_latents_path does not end with .latents.bin: %s",
             reference_latents_path.c_str());
        ref_png_path = reference_latents_path + ".png";  // best-effort fallback
    }
    flog("[encode_edit] reference PNG path: %s", ref_png_path.c_str());

    // ── Load reference PNG (force RGB) ──
    int img_w = 0, img_h = 0, img_c = 0;
    unsigned char* img_data = stbi_load(ref_png_path.c_str(), &img_w, &img_h, &img_c, 3);
    if (!img_data) {
        flog("[encode_edit] ERROR: stbi_load failed for %s", ref_png_path.c_str());
        seq_len = 0;
        return {};
    }
    flog("[encode_edit] loaded PNG: %dx%d (channels=%d original)", img_w, img_h, img_c);

    // ── Resize to 256x256 using stb_image_resize ──
    // PIL.Image.LANCZOS produces the highest-quality downscale; this version
    // of stb_image_resize.h doesn't have STBIR_FILTER_LANCZOS3, so we use
    // STBIR_FILTER_CATMULLROM (cubic, closest available) with sRGB colorspace
    // to match PIL's default behavior.
    unsigned char* resized = nullptr;
    unsigned char* src_for_extract = img_data;
    if (img_w != VIS_SZ || img_h != VIS_SZ) {
        resized = static_cast<unsigned char*>(malloc(VIS_SZ * VIS_SZ * 3));
        if (!resized) {
            flog("[encode_edit] ERROR: malloc failed for resized image");
            stbi_image_free(img_data);
            seq_len = 0;
            return {};
        }
        int ok = stbir_resize_uint8_generic(
            img_data, img_w, img_h, 0,
            resized, VIS_SZ, VIS_SZ, 0,
            /*num_channels=*/3, /*alpha_channel=*/STBIR_ALPHA_CHANNEL_NONE, /*flags=*/0,
            STBIR_EDGE_CLAMP, STBIR_FILTER_CATMULLROM, STBIR_COLORSPACE_SRGB,
            /*alloc_context=*/nullptr);
        if (!ok) {
            // Fallback to sRGB bilinear (built-in default) if CATMULLROM fails.
            stbir_resize_uint8_srgb(img_data, img_w, img_h, 0,
                                    resized, VIS_SZ, VIS_SZ, 0,
                                    /*num_channels=*/3, /*alpha_channel=*/STBIR_ALPHA_CHANNEL_NONE, /*flags=*/0);
            flog("[encode_edit] CATMULLROM resize failed, fell back to sRGB bilinear");
        }
        src_for_extract = resized;
    }

    // ── Build pixel_values [1, 256, 1536] fp32 ──
    // Matches test_mobile_models.py encode_edit:
    //   img_arr = normalized to [-1,1] (HWC, 256x256x3)
    //   img_t = img_arr.transpose(2,0,1)           # CHW [3,256,256]
    //   im2 = np.tile(img_t[np.newaxis], (2,1,1,1))  # TCHW [2,3,256,256]
    //   pv = im2.reshape(T,C,hg,P,wg,P).transpose(2,4,1,0,3,5)
    //         .reshape(hg*wg, C*T*P*P)[np.newaxis]   # [1, 256, 1536]
    //
    // Per-patch layout (C*T*P*P = 1536):
    //   for c in 0..2: for t in 0..1: for pi in 0..15: for pj in 0..15:
    //     value = img_arr[hgi*P + pi, wgi*P + pj, c]  (same for both t)
    std::vector<float> pixel_values(1 * VIS_NPATCH * VIS_PDIM, 0.0f);
    // Pre-normalize into a float buffer [3, 256, 256] = img_t
    std::vector<float> img_t(3 * VIS_SZ * VIS_SZ, 0.0f);
    for (int c = 0; c < 3; c++) {
        for (int y = 0; y < VIS_SZ; y++) {
            for (int x = 0; x < VIS_SZ; x++) {
                // HWC input → CHW output; normalize: (v/255 - 0.5) / 0.5 = v/127.5 - 1
                unsigned char px = src_for_extract[(y * VIS_SZ + x) * 3 + c];
                img_t[c * VIS_SZ * VIS_SZ + y * VIS_SZ + x] =
                    static_cast<float>(px) / 127.5f - 1.0f;
            }
        }
    }
    // Build patches
    for (int hgi = 0; hgi < VIS_HG; hgi++) {
        for (int wgi = 0; wgi < VIS_WG; wgi++) {
            int patch_idx = hgi * VIS_WG + wgi;
            int base = patch_idx * VIS_PDIM;
            for (int c = 0; c < 3; c++) {
                for (int t = 0; t < VIS_T; t++) {
                    for (int pi = 0; pi < VIS_P; pi++) {
                        for (int pj = 0; pj < VIS_P; pj++) {
                            // value = img_t[c, hgi*P + pi, wgi*P + pj]
                            float v = img_t[c * VIS_SZ * VIS_SZ +
                                            (hgi * VIS_P + pi) * VIS_SZ +
                                            (wgi * VIS_P + pj)];
                            int idx_in_patch = ((c * VIS_T + t) * VIS_P + pi) * VIS_P + pj;
                            pixel_values[base + idx_in_patch] = v;
                        }
                    }
                }
            }
        }
    }
    if (resized) free(resized);
    stbi_image_free(img_data);

    // Log pixel_values statistics
    {
        float pvm = 0, pvs = 0, pvmin = 1e30f, pvmax = -1e30f;
        for (auto v : pixel_values) { pvm += v; pvmin = std::min(pvmin, v); pvmax = std::max(pvmax, v); }
        pvm /= pixel_values.size();
        for (auto v : pixel_values) pvs += (v - pvm) * (v - pvm);
        pvs = std::sqrt(pvs / pixel_values.size());
        flog("[encode_edit] pixel_values: count=%zu mean=%.4f std=%.4f min=%.4f max=%.4f",
             pixel_values.size(), pvm, pvs, pvmin, pvmax);
    }

    // ── Run vision_encoder ──
    // Inputs: pixel_values [1, 256, 1536] fp32, image_grid_thw [1, 3] int64
    // Output: image_embeds [64, 2048] (fp16 in the zhuanhuan_fp16 export)
    auto mem_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    std::array<int64_t, 3> pv_shape = {1, VIS_NPATCH, VIS_PDIM};  // [1, 256, 1536]
    Ort::Value pv_tensor = Ort::Value::CreateTensor<float>(
        mem_info, pixel_values.data(), pixel_values.size(), pv_shape.data(), 3);

    std::array<int64_t, 3> grid_thw_data = {1, VIS_HG, VIS_WG};  // [1, 16, 16]
    std::array<int64_t, 2> grid_shape = {1, 3};
    Ort::Value grid_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, grid_thw_data.data(), 3, grid_shape.data(), 2);

    const char* vis_input_names[] = {"pixel_values", "image_grid_thw"};
    const char* vis_output_names[] = {"image_embeds"};

    flog("[encode_edit] step1: vision_encoder Run start");
    Timer vis_timer;
    auto vis_outputs = vision_encoder_session_->Run(
        Ort::RunOptions{nullptr},
        vis_input_names,
        std::array<Ort::Value, 2>{std::move(pv_tensor), std::move(grid_tensor)}.data(),
        2,
        vis_output_names, 1);
    flog("[encode_edit] step1: vision_encoder Run done, %zu ms", vis_timer.elapsed_ms());

    // Extract image_embeds: shape [64, 2048], may be fp16 or fp32
    auto& ie = vis_outputs[0];
    auto ie_shape = get_shape(ie);
    int ie_rows = static_cast<int>(ie_shape[0]);    // 64
    int ie_cols = static_cast<int>(ie_shape[1]);    // 2048
    flog("[encode_edit] image_embeds shape: [%d, %d]", ie_rows, ie_cols);

    auto ie_info = ie.GetTensorTypeAndShapeInfo();
    auto ie_type = ie_info.GetElementType();
    // text_encoder_edit.onnx expects image_embeds as fp16 input. Convert
    // whatever vision_encoder returns to fp16 for the next stage.
    std::vector<uint16_t> image_embeds_fp16(ie_rows * ie_cols, 0);
    if (ie_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16) {
        const uint16_t* p = reinterpret_cast<const uint16_t*>(ie.GetTensorRawData());
        std::memcpy(image_embeds_fp16.data(), p, image_embeds_fp16.size() * sizeof(uint16_t));
    } else if (ie_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT) {
        const float* p = ie.GetTensorData<float>();
        for (size_t i = 0; i < image_embeds_fp16.size(); i++) {
            image_embeds_fp16[i] = fp32_to_fp16(p[i]);
        }
    } else {
        flog("[encode_edit] ERROR: unsupported image_embeds element type: %d", ie_type);
        seq_len = 0;
        return {};
    }

    // Release vision_encoder now that we have image_embeds (frees ~600MB)
    release_vision_encoder();
    flog("[encode_edit] released vision_encoder after extracting image_embeds");

    // ── Build edit prompt + tokenize ──
    flog("[encode_edit] step2: format_prompt + tokenize start");
    std::string formatted = format_prompt(user_prompt, /*is_edit=*/true);
    auto token_ids = tokenizer_->encode(formatted);
    int total_len = static_cast<int>(token_ids.size());
    flog("[encode_edit] step2: tokenize done, tokens=%d (before image_pad expansion)", total_len);

    // Find the single <|image_pad|> placeholder position.
    // <|image_pad|> token id = 151655 (from added_tokens.json). Look it up
    // via the tokenizer's special_tokens_? We can't access it from here, so
    // hardcode the well-known Qwen3VL ID. (Tokenizer loads this from
    // added_tokens.json on the device.)
    constexpr int64_t IMAGE_PAD_ID = 151655;
    int pad_pos = -1;
    for (int i = 0; i < total_len; i++) {
        if (token_ids[i] == IMAGE_PAD_ID) { pad_pos = i; break; }
    }
    if (pad_pos < 0) {
        flog("[encode_edit] ERROR: <|image_pad|> token not found in prompt (id=%lld)",
             (long long)IMAGE_PAD_ID);
        seq_len = 0;
        return {};
    }
    flog("[encode_edit] image_pad placeholder at position %d", pad_pos);

    // Expand: replace single image_pad at pad_pos with VIS_NVIS (64) image_pad tokens.
    std::vector<int64_t> expanded_ids;
    expanded_ids.reserve(total_len - 1 + VIS_NVIS);
    expanded_ids.insert(expanded_ids.end(), token_ids.begin(), token_ids.begin() + pad_pos);
    for (int i = 0; i < VIS_NVIS; i++) expanded_ids.push_back(IMAGE_PAD_ID);
    expanded_ids.insert(expanded_ids.end(), token_ids.begin() + pad_pos + 1, token_ids.end());
    token_ids = std::move(expanded_ids);
    total_len = static_cast<int>(token_ids.size());
    flog("[encode_edit] after expansion: tokens=%d (vision span [%d, %d))",
         total_len, pad_pos, pad_pos + VIS_NVIS);

    // Build attention mask (all 1s)
    std::vector<int64_t> attn_mask(total_len, 1);

    // ── Compute position_ids [3, 1, seq_len] ──
    int64_t grid_thw_arr[3] = {1, VIS_HG, VIS_WG};  // [1, 16, 16]
    std::vector<int64_t> position_ids(3 * 1 * total_len, 0);
    compute_position_ids(position_ids.data(), total_len, pad_pos, VIS_NVIS,
                         grid_thw_arr, VIS_SM);

    // ── Run text_encoder_edit ──
    // Inputs: input_ids [1, seq_len], attention_mask [1, seq_len],
    //         position_ids [3, 1, seq_len], image_embeds [nvis, 2048] (fp16)
    std::array<int64_t, 2> ids_shape = {1, total_len};
    Ort::Value ids_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, token_ids.data(), total_len, ids_shape.data(), 2);

    Ort::Value attn_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, attn_mask.data(), total_len, ids_shape.data(), 2);

    std::array<int64_t, 3> pos_shape = {3, 1, total_len};
    Ort::Value pos_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, position_ids.data(), 3 * total_len, pos_shape.data(), 3);

    std::array<int64_t, 2> ie_shape_arr = {VIS_NVIS, VIS_HIDDEN};  // [64, 2048]
    // IMPORTANT: text_encoder_edit.onnx expects image_embeds as FLOAT16 tensor.
    // CreateTensor<uint16_t> would be interpreted as UINT16 (a different ONNX
    // type), causing "Unexpected input data type. Actual: (tensor(uint16)),
    // expected: (tensor(float16))" runtime error. Use the non-template overload
    // that takes an explicit element type.
    Ort::Value ie_tensor = Ort::Value::CreateTensor(
        mem_info, image_embeds_fp16.data(), image_embeds_fp16.size() * sizeof(uint16_t),
        ie_shape_arr.data(), 2, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16);

    const char* te_input_names[] = {"input_ids", "attention_mask", "position_ids", "image_embeds"};
    const char* te_output_names[] = {"hidden_states"};

    flog("[encode_edit] step3: text_encoder_edit Run start, seq_len=%d", total_len);
    Timer te_timer;
    auto te_outputs = text_encoder_edit_session_->Run(
        Ort::RunOptions{nullptr},
        te_input_names,
        std::array<Ort::Value, 4>{std::move(ids_tensor), std::move(attn_tensor),
                                  std::move(pos_tensor), std::move(ie_tensor)}.data(),
        4,
        te_output_names, 1);
    flog("[encode_edit] step3: text_encoder_edit Run done, %zu ms", te_timer.elapsed_ms());

    // Extract hidden_states: shape [1, total_len, 2048] (fp16)
    auto& hs = te_outputs[0];
    auto shape = get_shape(hs);
    int total_seq = static_cast<int>(shape[1]);
    int hidden_dim = static_cast<int>(shape[2]);

    auto hs_info = hs.GetTensorTypeAndShapeInfo();
    auto elem_type = hs_info.GetElementType();

    // Drop first drop_idx_ (=64) tokens — these are the vision tokens plus
    // the system prompt's prefix tokens. The remaining tokens are the
    // user-instruction embeddings that the UNet cross-attends to.
    int drop = std::min(drop_idx_, total_seq);
    seq_len = total_seq - drop;

    std::vector<float> result(seq_len * hidden_dim);
    if (elem_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16) {
        const uint16_t* fp16_data = reinterpret_cast<const uint16_t*>(hs.GetTensorRawData());
        for (int i = 0; i < seq_len; i++) {
            for (int j = 0; j < hidden_dim; j++) {
                uint16_t h = fp16_data[(drop + i) * hidden_dim + j];
                // Inline fp16→fp32 conversion (same as encode_prompt)
                uint32_t sign = (h >> 15) & 1;
                uint32_t exp  = (h >> 10) & 0x1f;
                uint32_t mant = h & 0x3ff;
                uint32_t f32;
                if (exp == 0) {
                    if (mant == 0) {
                        f32 = sign << 31;
                    } else {
                        exp = 1;
                        while (!(mant & 0x400)) { mant <<= 1; exp--; }
                        mant &= 0x3ff;
                        f32 = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
                    }
                } else if (exp == 31) {
                    f32 = (sign << 31) | (0xff << 23) | (mant << 13);
                } else {
                    f32 = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
                }
                float fv;
                std::memcpy(&fv, &f32, 4);
                result[i * hidden_dim + j] = fv;
            }
        }
    } else {
        const float* f32_data = hs.GetTensorData<float>();
        std::memcpy(result.data(), f32_data + drop * hidden_dim, seq_len * hidden_dim * sizeof(float));
    }

    flog("[encode_edit] done: seq_len=%d hidden_dim=%d", seq_len, hidden_dim);
    return result;
}

std::vector<float> DreamLitePipeline::encode_prompt(const std::string& prompt, int& seq_len) {
    flog("[encode] step1: format_prompt start (edit_mode=%d)", edit_mode_ ? 1 : 0);
    std::string formatted = format_prompt(prompt, edit_mode_);
    flog("[encode] step1: format_prompt done, len=%zu", formatted.size());

    // Tokenize
    flog("[encode] step2: tokenizer encode start");
    auto token_ids = tokenizer_->encode(formatted);
    int total_len = static_cast<int>(token_ids.size());
    flog("[encode] step2: tokenizer encode done, tokens=%d", total_len);

    // Safety cap: text_encoder's internal self-attention is O(N^2) in memory.
    // Normal prompts produce ~60-90 tokens. Reference-modification prompts
    // (stylePrefix + "based on (...)" + userInput) can push this higher.
    // At N=512, attention alone is 512*512*4 bytes = 1 MB per layer; with
    // many layers + INT4 weights + fp16 activations, the encoder can OOM-kill
    // the process. Hard-cap at 256 tokens (well above the 34-token drop_idx
    // and the ~90-token reference-modification case) to bound memory.
    constexpr int MAX_TOKENS = 256;
    if (total_len > MAX_TOKENS) {
        flog("[encode] Token count %d exceeds cap %d, truncating (prompt may have been too long)",
             total_len, MAX_TOKENS);
        LOGI("[encode] Token count %d exceeds cap %d, truncating", total_len, MAX_TOKENS);
        token_ids.resize(MAX_TOKENS);
        total_len = MAX_TOKENS;
    }

    // Log first 15 token IDs via flog for crash diagnosis
    {
        std::string first_ids;
        for (int i = 0; i < std::min(15, total_len); i++) {
            first_ids += std::to_string(token_ids[i]);
            if (i < std::min(15, total_len) - 1) first_ids += ",";
        }
        flog("[encode] step2: first 15 token IDs: %s", first_ids.c_str());
    }

    std::cout << "[encode] Tokens: " << total_len << std::endl;
    LOGI("[encode] Tokens: %d", total_len);
    // Log first 15 token IDs to verify special token handling
    LOGI("[encode] First 15 token IDs:");
    for (int i = 0; i < std::min(15, total_len); i++) {
        LOGI("  [%d] = %lld", i, (long long)token_ids[i]);
    }
    // Log ALL token IDs in chunks for full comparison with WSL
    {
        std::string all_ids;
        for (int i = 0; i < total_len; i++) {
            all_ids += std::to_string(token_ids[i]);
            if (i < total_len - 1) all_ids += ",";
        }
        // Log in chunks of ~900 chars (logcat limit)
        size_t pos = 0;
        int chunk = 0;
        while (pos < all_ids.size()) {
            size_t end = std::min(pos + 900, all_ids.size());
            LOGI("[encode] ALL_TOKENS[%d]: %.*s", chunk, (int)(end - pos), all_ids.c_str() + pos);
            pos = end;
            chunk++;
        }
    }
    // Log full prompt in chunks
    {
        size_t pos = 0;
        int chunk = 0;
        while (pos < formatted.size()) {
            size_t end = std::min(pos + 900, formatted.size());
            LOGI("[encode] PROMPT[%d]: %.*s", chunk, (int)(end - pos), formatted.c_str() + pos);
            pos = end;
            chunk++;
        }
    }

    // Build attention mask (all 1s)
    std::vector<int64_t> attn_mask(total_len, 1);

    // Prepare input tensors
    std::array<int64_t, 2> input_shape = {1, total_len};
    auto mem_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    flog("[encode] step3: creating tensors, total_len=%d", total_len);

    Ort::Value input_ids_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, token_ids.data(), total_len, input_shape.data(), 2);
    Ort::Value attn_mask_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, attn_mask.data(), total_len, input_shape.data(), 2);

    // Run text encoder
    const char* input_names[] = {"input_ids", "attention_mask"};
    const char* output_names[] = {"hidden_states"};

    flog("[encode] step4: text_encoder Run start");
    Timer t;
    auto outputs = text_encoder_session_->Run(
        Ort::RunOptions{nullptr},
        input_names,
        std::array<Ort::Value, 2>{std::move(input_ids_tensor), std::move(attn_mask_tensor)}.data(),
        2,
        output_names, 1);
    flog("[encode] step4: text_encoder Run done, %zu ms", t.elapsed_ms());
    std::cout << "[encode] Text encoder inference: " << t.elapsed_ms() << " ms" << std::endl;

    // Extract hidden states: shape [1, total_len, 2048]
    auto& hs = outputs[0];
    auto shape = get_shape(hs);
    int total_seq = static_cast<int>(shape[1]);
    int hidden_dim = static_cast<int>(shape[2]);

    // Get raw data (fp16 or fp32 depending on model)
    auto hs_info = hs.GetTensorTypeAndShapeInfo();
    auto elem_type = hs_info.GetElementType();

    // Drop first drop_idx_ tokens (34 for generate mode, 64 for edit mode).
    // drop_idx_ is set at the start of generate() based on edit_mode_.
    int drop = std::min(drop_idx_, total_seq);
    seq_len = total_seq - drop;

    std::vector<float> result(seq_len * hidden_dim);

    if (elem_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16) {
        // Convert fp16 to fp32
        const uint16_t* fp16_data = reinterpret_cast<const uint16_t*>(hs.GetTensorRawData());
        for (int i = 0; i < seq_len; i++) {
            for (int j = 0; j < hidden_dim; j++) {
                uint16_t h = fp16_data[(drop + i) * hidden_dim + j];
                // fp16 to fp32 conversion
                uint32_t sign = (h >> 15) & 1;
                uint32_t exp  = (h >> 10) & 0x1f;
                uint32_t mant = h & 0x3ff;
                uint32_t f32;
                if (exp == 0) {
                    if (mant == 0) {
                        f32 = sign << 31;
                    } else {
                        // Denormalized
                        exp = 1;
                        while (!(mant & 0x400)) { mant <<= 1; exp--; }
                        mant &= 0x3ff;
                        f32 = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
                    }
                } else if (exp == 31) {
                    f32 = (sign << 31) | (0xff << 23) | (mant << 13);
                } else {
                    f32 = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
                }
                float fv;
                std::memcpy(&fv, &f32, 4);
                result[i * hidden_dim + j] = fv;
            }
        }
    } else {
        // Already fp32
        const float* f32_data = hs.GetTensorData<float>();
        std::memcpy(result.data(), f32_data + drop * hidden_dim, seq_len * hidden_dim * sizeof(float));
    }

    std::cout << "[encode] Prompt embeds: [" << seq_len << ", " << hidden_dim << "]" << std::endl;
    return result;
}

// ---------------------------------------------------------------------------
//  Stage 2: UNet denoising step
// ---------------------------------------------------------------------------

std::vector<float> DreamLitePipeline::run_unet(
    const std::vector<float>& sample,
    float timestep,
    const std::vector<float>& encoder_hidden_states,
    int seq_len,
    float width, float height,
    const float* image_latents_data)
{
    // sample: [1, 4, H, W] -> concat with image_latents (or zeros) -> [1, 4, H, 2*W]
    // In edit mode (image_latents_data != nullptr), the right half is the
    // reference image's latents — this is the REAL img2img conditioning that
    // lets the UNet "see" the original image and follow the modification
    // instruction. In generate mode the right half is zeros.
    int H = 0, W = 0;
    {
        int total = static_cast<int>(sample.size());
        int hw = total / 4;
        H = static_cast<int>(std::sqrt(hw));
        W = H;  // assume square
    }
    int concat_W = W * 2;

    // Build model input: [1, 4, H, concat_W]
    std::vector<float> model_input(1 * 4 * H * concat_W, 0.0f);
    for (int c = 0; c < 4; c++) {
        for (int h = 0; h < H; h++) {
            // Left half: noisy latents
            std::memcpy(
                model_input.data() + (c * H + h) * concat_W,
                sample.data() + (c * H + h) * W,
                W * sizeof(float));
            // Right half: image_latents (edit mode) or zeros (generate mode)
            if (image_latents_data != nullptr) {
                std::memcpy(
                    model_input.data() + (c * H + h) * concat_W + W,
                    image_latents_data + (c * H + h) * W,
                    W * sizeof(float));
            }
            // else: right half already zero-initialized
        }
    }

    // Prepare tensors
    auto mem_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    // sample: [1, 4, H, concat_W]
    std::array<int64_t, 4> sample_shape = {1, 4, H, concat_W};
    Ort::Value sample_tensor = Ort::Value::CreateTensor<float>(
        mem_info, model_input.data(), model_input.size(), sample_shape.data(), 4);

    // timestep: [1]
    std::array<float, 1> ts_data = {timestep};
    std::array<int64_t, 1> ts_shape = {1};
    Ort::Value ts_tensor = Ort::Value::CreateTensor<float>(
        mem_info, ts_data.data(), 1, ts_shape.data(), 1);

    // encoder_hidden_states: [1, seq_len, 2048]
    std::array<int64_t, 3> enc_shape = {1, seq_len, hidden_size_};
    Ort::Value enc_tensor = Ort::Value::CreateTensor<float>(
        mem_info, const_cast<float*>(encoder_hidden_states.data()),
        encoder_hidden_states.size(), enc_shape.data(), 3);

    // encoder_attention_mask: [1, seq_len]  (FLOAT)
    std::vector<float> enc_mask(seq_len, 1.0f);
    std::array<int64_t, 2> mask_shape = {1, seq_len};
    Ort::Value mask_tensor = Ort::Value::CreateTensor<float>(
        mem_info, enc_mask.data(), seq_len, mask_shape.data(), 2);

    // time_ids: [1, 2] = [width, height]
    std::array<float, 2> time_ids_data = {width, height};
    std::array<int64_t, 2> time_ids_shape = {1, 2};
    Ort::Value time_ids_tensor = Ort::Value::CreateTensor<float>(
        mem_info, time_ids_data.data(), 2, time_ids_shape.data(), 2);

    // Run UNet
    const char* input_names[] = {"sample", "timestep", "encoder_hidden_states",
                                  "encoder_attention_mask", "time_ids"};
    const char* output_names[] = {"noise_pred"};

    std::array<Ort::Value, 5> inputs = {
        std::move(sample_tensor), std::move(ts_tensor), std::move(enc_tensor),
        std::move(mask_tensor), std::move(time_ids_tensor)
    };

    auto outputs = unet_session_->Run(
        Ort::RunOptions{nullptr},
        input_names, inputs.data(), 5,
        output_names, 1);

    // Extract noise prediction and crop to original width
    auto& np = outputs[0];
    auto np_shape = get_shape(np);
    auto np_info = np.GetTensorTypeAndShapeInfo();
    auto elem_type = np_info.GetElementType();

    int out_H = static_cast<int>(np_shape[2]);
    int out_W = static_cast<int>(np_shape[3]);

    // Crop: take only first W columns
    std::vector<float> noise(4 * out_H * W);

    if (elem_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16) {
        const uint16_t* fp16_data = reinterpret_cast<const uint16_t*>(np.GetTensorRawData());
        for (int c = 0; c < 4; c++) {
            for (int h = 0; h < out_H; h++) {
                for (int w = 0; w < W; w++) {
                    uint16_t half = fp16_data[(c * out_H + h) * out_W + w];
                    // fp16 -> fp32
                    uint32_t sign = (half >> 15) & 1;
                    uint32_t exp  = (half >> 10) & 0x1f;
                    uint32_t mant = half & 0x3ff;
                    uint32_t f32;
                    if (exp == 0) {
                        f32 = (mant == 0) ? (sign << 31) : 0;
                        if (mant != 0) {
                            exp = 1;
                            while (!(mant & 0x400)) { mant <<= 1; exp--; }
                            mant &= 0x3ff;
                            f32 = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
                        }
                    } else if (exp == 31) {
                        f32 = (sign << 31) | (0xff << 23) | (mant << 13);
                    } else {
                        f32 = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
                    }
                    float fv;
                    std::memcpy(&fv, &f32, 4);
                    noise[(c * out_H + h) * W + w] = fv;
                }
            }
        }
    } else {
        const float* f32_data = np.GetTensorData<float>();
        for (int c = 0; c < 4; c++) {
            for (int h = 0; h < out_H; h++) {
                std::memcpy(noise.data() + (c * out_H + h) * W,
                            f32_data + (c * out_H + h) * out_W,
                            W * sizeof(float));
            }
        }
    }

    return noise;
}

// ---------------------------------------------------------------------------
//  Stage 4: VAE decode
// ---------------------------------------------------------------------------

std::vector<float> DreamLitePipeline::run_vae_decode(const std::vector<float>& latent) {
    auto mem_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    int latent_size = static_cast<int>(latent.size());
    int hw = latent_size / 4;
    int H = static_cast<int>(std::sqrt(hw));
    int W = H;

    // The ONNX VAE decoder (zhuanhuan/vae_decoder.onnx) includes an internal
    // unscale_latents step: (latents - 0.5) / 3.0.  To feed it correctly we must
    // pre-scale the raw denoised latents: scaled = latents * 3 + 0.5, so that
    // the internal unscale recovers the original values.
    std::vector<float> scaled_latent(latent_size);
    for (int i = 0; i < latent_size; i++) {
        scaled_latent[i] = latent[i] * 3.0f + 0.5f;
    }
    // Log scaled latent stats for VAE input alignment check
    {
        float sm = 0, ss = 0;
        for (auto v : scaled_latent) sm += v;
        sm /= scaled_latent.size();
        for (auto v : scaled_latent) ss += (v - sm) * (v - sm);
        ss = std::sqrt(ss / scaled_latent.size());
        flog("[vae] Input to VAE (scaled *3+0.5): mean=%.6f std=%.6f", sm, ss);
    }
    save_bin("vae_input_scaled", scaled_latent);

    std::array<int64_t, 4> shape = {1, 4, H, W};
    Ort::Value latent_tensor = Ort::Value::CreateTensor<float>(
        mem_info, scaled_latent.data(), latent_size, shape.data(), 4);

    const char* input_names[] = {"latents"};
    const char* output_names[] = {"image"};

    Timer t;
    auto outputs = vae_session_->Run(
        Ort::RunOptions{nullptr},
        input_names, &latent_tensor, 1,
        output_names, 1);
    std::cout << "[vae] VAE decode: " << t.elapsed_ms() << " ms" << std::endl;
    LOGI("[vae] VAE decode: %.0f ms, latent scaled (*3+0.5)", t.elapsed_ms());

    auto& img = outputs[0];
    auto img_shape = get_shape(img);
    auto img_info = img.GetTensorTypeAndShapeInfo();
    auto elem_type = img_info.GetElementType();

    int C = static_cast<int>(img_shape[1]);  // 3
    int oH = static_cast<int>(img_shape[2]);
    int oW = static_cast<int>(img_shape[3]);

    // Convert to fp32 if needed
    std::vector<float> pixels(C * oH * oW);
    if (elem_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16) {
        const uint16_t* fp16_data = reinterpret_cast<const uint16_t*>(img.GetTensorRawData());
        int total = C * oH * oW;
        for (int i = 0; i < total; i++) {
            uint16_t half = fp16_data[i];
            uint32_t sign = (half >> 15) & 1;
            uint32_t exp  = (half >> 10) & 0x1f;
            uint32_t mant = half & 0x3ff;
            uint32_t f32;
            if (exp == 0) {
                if (mant == 0) { f32 = sign << 31; }
                else {
                    exp = 1;
                    while (!(mant & 0x400)) { mant <<= 1; exp--; }
                    mant &= 0x3ff;
                    f32 = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
                }
            } else if (exp == 31) {
                f32 = (sign << 31) | (0xff << 23) | (mant << 13);
            } else {
                f32 = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
            }
            float fv;
            std::memcpy(&fv, &f32, 4);
            pixels[i] = fv;
        }
    } else {
        const float* f32_data = img.GetTensorData<float>();
        std::memcpy(pixels.data(), f32_data, pixels.size() * sizeof(float));
    }

    // Post-process: denormalize (x * 0.5 + 0.5), clamp [0, 1]
    // Convert from [C, H, W] to [H, W, C] — stay in float, no uint8 quantization
    std::vector<float> result(oH * oW * 3);
    for (int h = 0; h < oH; h++) {
        for (int w = 0; w < oW; w++) {
            for (int c = 0; c < 3; c++) {
                float v = pixels[c * oH * oW + h * oW + w];
                v = v * 0.5f + 0.5f;   // denormalize
                v = std::max(0.0f, std::min(1.0f, v));
                result[(h * oW + w) * 3 + c] = v;
            }
        }
    }

    return result;
}

// ---------------------------------------------------------------------------
//  Main generate function
// ---------------------------------------------------------------------------

ImageOutput DreamLitePipeline::generate(const GenerationConfig& config_in) {
    Timer total_timer;

    // Determine mode: edit (real img2img) if reference latents provided,
    // otherwise generate (text-to-image). edit_mode_ is read by
    // format_prompt() and encode_prompt() to select template + drop_idx.
    edit_mode_ = !config_in.reference_latents_path.empty();
    drop_idx_ = edit_mode_ ? 64 : 34;
    flog("[gen] mode=%s drop_idx=%d",
         edit_mode_ ? "edit (img2img)" : "generate (txt2img)", drop_idx_);

    // No generation limit: full_reset() now destroys the Ort::Env (in addition
    // to sessions + session_opts) after each generation, returning ALL ORT
    // internal state (thread-pool, allocator, memory patterns, virtual address
    // reservations) to the OS. The Env is lazily recreated by get_env() on the
    // next generation. This eliminates the ORT state accumulation that
    // previously caused silent process kill on Gen 2. Users can now generate
    // unlimited images consecutively (each ~85s).

#ifdef __ANDROID__
    // Pre-generation purge: force scudo to release any pending free pages
    // from the previous generation before starting a new one. This is a
    // belt-and-suspenders measure alongside full_reset()'s purge.
    android_purge_allocator();
    sched_yield();
    android_purge_allocator();
    // Brief sleep (300ms) to let kernel complete any pending VM unmap.
    usleep(300 * 1000);
#endif

#if DEBUG_ALIGN
    // Override with WSL reference prompt + seed for stage-by-stage alignment
    GenerationConfig config = config_in;
    config.prompt = WSL_REF_PROMPT;
    config.seed = WSL_REF_SEED;
    flog("[DEBUG_ALIGN] OVERRIDDEN prompt=\"%s\" seed=%lld", config.prompt.c_str(), (long long)config.seed);
#else
    const GenerationConfig& config = config_in;
#endif

    int latent_h = config.height / vae_scale_factor_;
    int latent_w = config.width / vae_scale_factor_;
    int latent_size = latent_channels_ * latent_h * latent_w;

    flog_clear();
    flog("========== DreamLite Generate ==========");
    flog("Prompt: \"%s\"", config.prompt.c_str());
    flog("Resolution: %dx%d  Steps: %d  Seed: %lld",
         config.width, config.height, config.num_steps, (long long)config.seed);
    flog("Latent: [1, %d, %d, %d]  size=%d", latent_channels_, latent_h, latent_w, latent_size);

#ifdef __ANDROID__
    // Log available memory + purge freed pages before starting.
    // This helps diagnose OOM crashes and returns any freed-but-uncommitted
    // pages from previous generations back to the OS.
    {
        FILE* f = fopen("/proc/meminfo", "r");
        if (f) {
            char line[256];
            long mem_avail = -1, mem_free = -1, cached = -1;
            while (fgets(line, sizeof(line), f)) {
                if (sscanf(line, "MemAvailable: %ld kB", &mem_avail) == 1) continue;
                if (sscanf(line, "MemFree: %ld kB", &mem_free) == 1) continue;
                if (sscanf(line, "Cached: %ld kB", &cached) == 1) continue;
            }
            fclose(f);
            flog("[mem] MemFree=%ld kB (%.1f MB)  MemAvailable=%ld kB (%.1f MB)  Cached=%ld kB (%.1f MB)",
                 mem_free, mem_free / 1024.0, mem_avail, mem_avail / 1024.0, cached, cached / 1024.0);

            // If available memory is too low, abort gracefully instead of
            // letting the kernel panic / reboot the device. Generation peak
            // RSS is ~3GB (text_encoder ~400MB + UNet ~2.5GB + VAE ~200MB).
            //
            // We use MemAvailable (not MemFree) because MemAvailable includes
            // reclaimable Cached pages — the kernel will auto-reclaim Cached
            // (model file pages) under pressure. A pure MemFree check would
            // falsely abort when Cached is high but reclaimable.
            //
            // Threshold: 3GB MemAvailable. Below this, even with full Cached
            // reclaim, generation peak allocations cannot be satisfied and
            // the device reboots (verified: MemFree=3GB + text_encoder not
            // released → UNet denoising crash rebooted the entire device).
            // Return empty output so Kotlin layer can show an error message.
            if (mem_avail > 0 && mem_avail < 3072 * 1024) {
                flog("[ERROR] MemAvailable too low (%ld kB < 3GB), aborting generation to prevent device reboot", mem_avail);
                ImageOutput empty;
                empty.width = 0;
                empty.height = 0;
                empty.error_message = "设备可用内存不足(低于3GB)，请关闭后台应用后重试";
                return empty;
            }
        }
        android_purge_allocator();
    }
#endif

    std::cout << "\n========== DreamLite Generate ==========" << std::endl;
    std::cout << "Prompt: \"" << config.prompt << "\"" << std::endl;
    std::cout << "Resolution: " << config.width << "x" << config.height << std::endl;
    std::cout << "Steps: " << config.num_steps << ", Seed: " << config.seed << std::endl;
    std::cout << "Latent: [1, " << latent_channels_ << ", " << latent_h << ", " << latent_w << "]" << std::endl;

    // ── Stage 1: Load text encoder, encode prompt ──
    // Text encoder IS released after encoding (below) to free ~400MB before
    // loading UNet. This is critical because UNet needs ~2.5GB RSS and
    // physical memory is tight (12GB device, ~3-5GB free).
    //
    // In edit mode (img2img), we load vision_encoder + text_encoder_edit
    // instead of text_encoder_generate. Vision_encoder is released inside
    // encode_prompt_edit() right after image_embeds is extracted.
    // text_encoder_edit is released here after encoding, matching the
    // txt2img pattern (free ~900MB before UNet loads).
    std::cout << "\n--- Stage 1: Text Encoding ---" << std::endl;
    int seq_len = 0;
    std::vector<float> prompt_embeds;
    if (edit_mode_) {
        // Vision-based edit mode: require both vision_encoder and
        // text_encoder_edit. Abort gracefully if missing — return an error
        // so the Kotlin layer can prompt the user to push the edit models.
        if (vis_path_.empty() || te_edit_path_.empty()) {
            flog("[ERROR] edit mode requires vision_encoder.onnx + text_encoder_edit.onnx, "
                 "but one or both are missing (vis_path=%s te_edit_path=%s)",
                 vis_path_.empty() ? "EMPTY" : vis_path_.c_str(),
                 te_edit_path_.empty() ? "EMPTY" : te_edit_path_.c_str());
            ImageOutput empty;
            empty.error_message = "缺少 vision_encoder.onnx 或 text_encoder_edit.onnx，无法进行图生图";
            return empty;
        }
        flog("[gen] stage1: ensure_vision_encoder start");
        ensure_vision_encoder();
        flog("[gen] stage1: ensure_vision_encoder done");
        flog("[gen] stage1: ensure_text_encoder_edit start");
        ensure_text_encoder_edit();
        flog("[gen] stage1: ensure_text_encoder_edit done");
        flog("[gen] stage1: encode_prompt_edit start");
        prompt_embeds = encode_prompt_edit(config.prompt, config.reference_latents_path, seq_len);
        flog("[gen] stage1: encode_prompt_edit done, seq_len=%d", seq_len);
        if (prompt_embeds.empty() || seq_len == 0) {
            flog("[ERROR] encode_prompt_edit returned empty embeds (seq_len=%d)", seq_len);
            ImageOutput empty;
            empty.error_message = "图生图编码失败（参考图可能损坏或 vision_encoder 推理出错）";
            return empty;
        }
        // Release text_encoder_edit to free ~900MB before loading UNet.
        // vision_encoder was already released inside encode_prompt_edit.
        release_text_encoder_edit();
        flog("[gen] released text_encoder_edit before loading UNet");
    } else {
        flog("[gen] stage1: ensure_text_encoder start");
        ensure_text_encoder();
        flog("[gen] stage1: ensure_text_encoder done");
        flog("[gen] stage1: encode_prompt start");
        prompt_embeds = encode_prompt(config.prompt, seq_len);
        flog("[gen] stage1: encode_prompt done, seq_len=%d", seq_len);
    }

    // Log prompt embedding stats (fp16 conversion precision check)
    {
        float pem = 0, pes = 0, pemin = 1e30f, pemax = -1e30f;
        for (auto v : prompt_embeds) { pem += v; pemin = std::min(pemin, v); pemax = std::max(pemax, v); }
        pem /= prompt_embeds.size();
        for (auto v : prompt_embeds) pes += (v - pem) * (v - pem);
        pes = std::sqrt(pes / prompt_embeds.size());
        flog("[text] prompt_embeds: seq_len=%d total=%zu mean=%.6f std=%.6f min=%.4f max=%.4f",
             seq_len, prompt_embeds.size(), pem, pes, pemin, pemax);
        flog("[text] prompt_embeds first8: %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f",
             prompt_embeds[0], prompt_embeds[1], prompt_embeds[2], prompt_embeds[3],
             prompt_embeds[4], prompt_embeds[5], prompt_embeds[6], prompt_embeds[7]);
    }
    save_bin("prompt_embeds", prompt_embeds);

    // 2. Initialize scheduler
    SchedulerState scheduler;
    scheduler.init(config.num_steps, latent_h, latent_w, /*use_linspace=*/edit_mode_);
    std::string ts_str = "Timesteps: [";
    for (int i = 0; i < config.num_steps; i++) {
        ts_str += std::to_string(scheduler.timesteps[i]);
        if (i < config.num_steps - 1) ts_str += ", ";
    }
    ts_str += "]";
    flog("[sched] %s", ts_str.c_str());
    std::cout << "\n" << ts_str << std::endl;

    // 3. Initialize latents (numpy-compatible RNG for exact WSL alignment)
    std::cout << "\n--- Stage 2: Denoising ---" << std::endl;
    auto noise = numpy_randn(latent_size, config.seed);

    // ── Real img2img (vision-based edit mode) ──
    // In edit mode the reference image's saved latents are loaded as
    // image_latents and fed to the UNet as the right-half conditioning input
    // [noise | image_latents]. The denoising latents start from pure noise
    // and the UNet runs the full schedule from step 0 — no SDEdit mixing.
    //
    // image_latents domain: RAW (UNet output / vae_encoder output domain).
    //   - .latents.bin stores RAW denoised latents from a previous generation.
    //   - test_mobile_models.py does `src_lat = (vae_enc_output - 0.5) / 3.0`
    //     to convert SCALED → RAW before passing to UNet.
    //   - Our .latents.bin is already RAW, so NO scaling is needed here.
    //   - Previously we applied `*3+0.5` (RAW→SCALED), which was WRONG and
    //     produced noise output (verified via Python comparison).
    //
    // In generate mode (no reference), image_latents stays empty and run_unet
    // receives nullptr → right half is zeros (matching the original behavior).
    int start_step = 0;
    std::vector<float> latents = std::move(noise);  // always pure noise
    std::vector<float> image_latents;  // empty in generate mode
    const float* image_latents_ptr = nullptr;
    if (!config.reference_latents_path.empty()) {
        flog("[img2img] Loading reference latents (RAW, no scaling): %s",
             config.reference_latents_path.c_str());
        FILE* f = fopen(config.reference_latents_path.c_str(), "rb");
        if (f) {
            // .latents.bin stores RAW denoised latents. Pass directly to UNet
            // without any scaling — matches test_mobile_models.py:
            //   src_lat = vae_enc.run(...)["image"]    # RAW output
            //   src_lat = (src_lat - 0.5) / 3.0        # SCALED → RAW
            //   # src_lat is now RAW; passed to UNet directly.
            // Our .latents.bin was saved after VAE decode (already RAW), so
            // no conversion needed.
            image_latents.resize(latent_size);
            size_t rd = fread(image_latents.data(), sizeof(float), latent_size, f);
            fclose(f);
            if (rd == static_cast<size_t>(latent_size)) {
                image_latents_ptr = image_latents.data();
                float im = 0, is = 0;
                for (auto v : image_latents) im += v;
                im /= image_latents.size();
                for (auto v : image_latents) is += (v - im) * (v - im);
                is = std::sqrt(is / image_latents.size());
                flog("[img2img] image_latents loaded (RAW): %d floats, mean=%.4f std=%.4f",
                     latent_size, im, is);
            } else {
                flog("[img2img] ERROR: latents file size mismatch (got %zu, expected %d), "
                     "cannot run edit mode", rd, latent_size);
                ImageOutput empty;
                empty.error_message = "参考图 latents 文件损坏，无法进行图生图";
                return empty;
            }
        } else {
            flog("[img2img] ERROR: cannot open %s, cannot run edit mode",
                 config.reference_latents_path.c_str());
            ImageOutput empty;
            empty.error_message = "参考图 latents 文件无法打开，无法进行图生图";
            return empty;
        }
    }
    save_bin("latents_initial", latents);
    save_bin("image_latents", image_latents);

    // Log initial noise statistics for RNG alignment verification
    {
        float n_mean = 0, n_min = 1e30f, n_max = -1e30f;
        for (auto v : latents) { n_mean += v; n_min = std::min(n_min, v); n_max = std::max(n_max, v); }
        n_mean /= latents.size();
        float n_var = 0;
        for (auto v : latents) n_var += (v - n_mean) * (v - n_mean);
        float n_std = std::sqrt(n_var / latents.size());
        // First 8 values for byte-level comparison with WSL np.random.randn
        flog("[rng] numpy_randn seed=%lld count=%d mean=%.6f std=%.6f min=%.4f max=%.4f",
             (long long)config.seed, latent_size, n_mean, n_std, n_min, n_max);
        flog("[rng] first8: %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f",
             latents[0], latents[1], latents[2], latents[3],
             latents[4], latents[5], latents[6], latents[7]);
        LOGI("[rng] numpy_randn seed=%lld count=%lld mean=%.6f std=%.6f min=%.4f max=%.4f",
             (long long)config.seed, (long long)latent_size, n_mean, n_std, n_min, n_max);
        std::cout << "[rng] Initial noise: mean=" << n_mean << " std=" << n_std
                  << " min=" << n_min << " max=" << n_max << std::endl;
    }

    // ── Load UNet for denoising loop, then release ──
    // Release text encoder first to free memory before loading UNet.
    // With use_arena=0, ORT returns this memory to the OS.
    // This is CRITICAL: UNet needs ~2.5GB RSS (1.5GB weights + 1GB
    // intermediate tensors). Without releasing text_encoder first,
    // physical memory runs out during UNet denoising, causing a
    // kernel-level crash and device reboot.
    //
    // In edit mode, text_encoder_edit was already released above (after
    // encode_prompt_edit returned), and text_encoder_generate was never
    // loaded — so only release in generate mode.
    if (!edit_mode_) {
        release_text_encoder();
        flog("[gen] released text_encoder before loading UNet");
    }
    ensure_unet();
    for (int step = start_step; step < config.num_steps; step++) {
        Timer step_timer;
        float sigma = scheduler.sigmas[step];
        float sigma_next = scheduler.sigmas[step + 1];
        float dt = sigma_next - sigma;

        std::cout << "  Step " << step << "/" << config.num_steps
                  << " (t=" << scheduler.timesteps[step]
                  << ", sigma=" << sigma << " -> " << sigma_next << "): ";

        // Log latent stats BEFORE this step (for alignment debugging)
        {
            float lm = 0, ls = 0;
            for (auto v : latents) lm += v;
            lm /= latents.size();
            for (auto v : latents) ls += (v - lm) * (v - lm);
            ls = std::sqrt(ls / latents.size());
            flog("[denoise] Step %d IN  latent: mean=%.6f std=%.6f", step, lm, ls);
        }

        // Run UNet (edit mode: pass image_latents for right-half conditioning;
        // generate mode: pass nullptr → right half is zeros)
        auto noise_pred = run_unet(
            latents,
            scheduler.timesteps[step],
            prompt_embeds, seq_len,
            static_cast<float>(config.width),
            static_cast<float>(config.height),
            image_latents_ptr);

        // Log noise_pred stats
        {
            float npm = 0, nps = 0, npmin = 1e30f, npmax = -1e30f;
            for (auto v : noise_pred) { npm += v; npmin = std::min(npmin, v); npmax = std::max(npmax, v); }
            npm /= noise_pred.size();
            for (auto v : noise_pred) nps += (v - npm) * (v - npm);
            nps = std::sqrt(nps / noise_pred.size());
            flog("[denoise] Step %d OUT noise_pred: mean=%.6f std=%.6f min=%.4f max=%.4f",
                 step, npm, nps, npmin, npmax);
        }
        { char nm[32]; snprintf(nm, sizeof(nm), "noise_pred_step%d", step); save_bin(nm, noise_pred); }

        // Euler step: latents += dt * noise_pred
        for (int i = 0; i < latent_size; i++) {
            latents[i] += dt * noise_pred[i];
        }
        { char nm[32]; snprintf(nm, sizeof(nm), "latents_step%d", step); save_bin(nm, latents); }

        double step_ms = step_timer.elapsed_ms();
        std::cout << step_ms << " ms" << std::endl;
        flog("[denoise] Step %d/%d: t=%.4f sigma=%.4f->%.4f dt=%.4f  %.0f ms",
             step, config.num_steps, scheduler.timesteps[step], sigma, sigma_next, dt, step_ms);
        LOGI("[denoise] Step %d/%d: t=%.1f sigma=%.4f->%.4f dt=%.4f  %.0f ms",
             step, config.num_steps, scheduler.timesteps[step], sigma, sigma_next, dt, step_ms);
    }
    release_unet();  // free ~1.5 GB before loading VAE

    // Save final latents for potential img2img reuse
    if (!config.output_latents_path.empty()) {
        FILE* f = fopen(config.output_latents_path.c_str(), "wb");
        if (f) {
            fwrite(latents.data(), sizeof(float), latent_size, f);
            fclose(f);
            flog("[img2img] Saved final latents (%d floats) to %s",
                 latent_size, config.output_latents_path.c_str());
        } else {
            flog("[img2img] WARNING: failed to save latents to %s",
                 config.output_latents_path.c_str());
        }
    }

    // ── Stage 3: Load VAE, decode, then release ──
    std::cout << "\n--- Stage 3: VAE Decode ---" << std::endl;
    ensure_vae();
    // scaling_factor=1.0, shift_factor=0.0 -> no pre-scaling needed
    auto decoded = run_vae_decode(latents);
    release_vae();

    // Log latent and decoded image statistics
    float lat_mean = 0, lat_std = 0;
    for (auto v : latents) lat_mean += v;
    lat_mean /= latents.size();
    for (auto v : latents) lat_std += (v - lat_mean) * (v - lat_mean);
    lat_std = std::sqrt(lat_std / latents.size());
    flog("[vae] Final latent (raw, pre-scale): mean=%.6f std=%.6f", lat_mean, lat_std);
    LOGI("[vae] Latent stats: mean=%.4f std=%.4f", lat_mean, lat_std);

    float img_mean = 0, img_std = 0;
    for (auto v : decoded) img_mean += v;
    img_mean /= decoded.size();
    for (auto v : decoded) img_std += (v - img_mean) * (v - img_mean);
    img_std = std::sqrt(img_std / decoded.size());
    flog("[vae] Decoded image (pre-uint8): mean=%.6f std=%.6f", img_mean, img_std);
    LOGI("[vae] Decoded image stats (pre-uint8): mean=%.4f std=%.4f", img_mean, img_std);
    save_bin("decoded_image", decoded);

    // Build output
    int img_h = config.height;
    int img_w = config.width;
    ImageOutput output;
    output.width = img_w;
    output.height = img_h;
    output.pixels.resize(img_h * img_w * 3);

    // Convert float [0,1] to uint8 [0,255]
    for (int i = 0; i < img_h * img_w * 3; i++) {
        float v = (i < static_cast<int>(decoded.size())) ? decoded[i] : 0.0f;
        output.pixels[i] = static_cast<uint8_t>(std::max(0.0f, std::min(1.0f, v)) * 255.0f + 0.5f);
    }

    std::cout << "\n========== Done in " << total_timer.elapsed_ms() << " ms ==========" << std::endl;
    flog("[generate] Total: %.0f ms", total_timer.elapsed_ms());
    LOGI("[generate] Total: %.0f ms", total_timer.elapsed_ms());

    // Full reset: destroy ALL ORT state (sessions + session options + env)
    // to return every byte of ORT-managed memory to the OS. This prevents
    // memory accumulation across generations that causes LMK to kill the app
    // on the 3rd+ consecutive generation. The env and session options are
    // lazily recreated by the next ensure_* call.
    full_reset();

    return output;
}
