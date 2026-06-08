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

static Ort::Env g_env{ORT_LOGGING_LEVEL_WARNING, "dreamlite"};

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

// Gaussian random with seed
static std::vector<float> randn(int64_t count, int seed) {
    std::mt19937 gen(seed);
    std::normal_distribution<float> dist(0.0f, 1.0f);
    std::vector<float> v(count);
    for (auto& x : v) x = dist(gen);
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
//  Prompt template  (Generate mode)
// ---------------------------------------------------------------------------

static std::string format_prompt(const std::string& user_prompt) {
    // Qwen3-VL chat template for generate mode
    std::string system_msg =
        "Describe the image by detailing the color, shape, size, texture, "
        "quantity, text, spatial relationships of the objects and background:";
    std::string user_msg = "[Generate]: " + user_prompt;

    std::string text =
        "<|im_start|>system\n" + system_msg + "<|im_end|>\n"
        "<|im_start|>user\n" + user_msg + "<|im_end|>\n"
        "<|im_start|>assistant\n";
    return text;
}

// ---------------------------------------------------------------------------
//  Scheduler: FlowMatchEulerDiscrete with dynamic time-shifting
// ---------------------------------------------------------------------------

struct SchedulerState {
    std::vector<float> sigmas;     // length = num_steps + 1
    std::vector<float> timesteps;  // length = num_steps (sigmas[0..N-1] * 1000)

    // Config
    static constexpr float base_shift = 0.5f;
    static constexpr float max_shift  = 1.16f;
    static constexpr int   base_seq_len = 256;
    static constexpr int   max_seq_len  = 4096;

    void init(int num_steps, int latent_h, int latent_w) {
        // image_seq_len = H*W / 4  (since latent channels=4 and we view as sequence)
        float image_seq_len = static_cast<float>(latent_h * latent_w) / 4.0f;

        // Linear interpolation for mu
        float m = (max_shift - base_shift) / (max_seq_len - base_seq_len);
        float b = base_shift - m * base_seq_len;
        float mu = image_seq_len * m + b;
        float exp_mu = std::exp(mu);

        // Base sigmas: linspace(1.0, 1/N, N)
        std::vector<float> base_sigmas(num_steps);
        for (int i = 0; i < num_steps; i++) {
            base_sigmas[i] = 1.0f - static_cast<float>(i) / num_steps;
        }

        // Apply exponential time-shift
        sigmas.resize(num_steps + 1);
        timesteps.resize(num_steps);
        for (int i = 0; i < num_steps; i++) {
            float s = base_sigmas[i];
            float inv_s_minus_1 = (1.0f / s) - 1.0f;
            float shifted = exp_mu / (exp_mu + std::pow(inv_s_minus_1, 1.0f));
            sigmas[i] = shifted;
            timesteps[i] = shifted * 1000.0f;
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

    // Session options — CPU (for INT4 text encoder)
    session_opts_ = std::make_unique<Ort::SessionOptions>();
    session_opts_->SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

    // Memory-mapped file loading: OS pages weights on demand instead of
    // loading the entire model into RAM.  Critical for 1.5 GB UNet on phone.
    session_opts_->AddConfigEntry("session.use_memory_pattern", "1");
    session_opts_->AddConfigEntry("session.use_arena", "1");

    // Allow MatMulNBits for INT4 text encoder
    session_opts_->AddConfigEntry("ep.dynamic_cpu_memory", "1");

    // Use 8 threads — Dimensity 9300+ has 8 powerful cores
    unsigned int threads = std::max(6u, std::min(8u, std::thread::hardware_concurrency()));
    session_opts_->SetIntraOpNumThreads(static_cast<int>(threads));

    // Session options with NNAPI acceleration (for UNet/VAE)
    session_opts_accel_ = std::make_unique<Ort::SessionOptions>();
    session_opts_accel_->SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
    session_opts_accel_->AddConfigEntry("session.use_memory_pattern", "1");
    session_opts_accel_->AddConfigEntry("session.use_arena", "1");
    session_opts_accel_->SetIntraOpNumThreads(static_cast<int>(threads));
    // NNAPI disabled: nnapi-reference fallback is slower than optimized CPU
    // and ANEURALNETWORKS_OP_FAILED on this model's complex attention ops.
    // Using optimized CPU with 6 threads instead.
    nnapi_available_ = false;
    std::cout << "[load] Using optimized CPU (" << threads << " threads)" << std::endl;

    std::string sep = "/";
#ifdef _WIN32
    sep = "\\";
#endif

    // Load tokenizer (small, always in memory)
    tokenizer_ = std::make_unique<BpeTokenizer>();
    auto vocab_path = model_dir + sep + "vocab.json";
    auto merges_path = model_dir + sep + "merges.txt";
    if (!tokenizer_->load(vocab_path, merges_path)) {
        std::cerr << "[load] Failed to load tokenizer from " << vocab_path << std::endl;
        return false;
    }
    std::cout << "[load] Tokenizer loaded (vocab_size=" << tokenizer_->vocab_size() << ")" << std::endl;
    std::cout << "[load] Sessions will be loaded lazily (sequential) to save memory" << std::endl;

    // Verify that all model files exist (but don't load them)
    auto te_path = model_dir + sep + "text_encoder_int4.onnx";
    auto unet_path = model_dir + sep + "unet_1024_fp32.onnx";
    auto vae_path = model_dir + sep + "vae_1024_fp32.onnx";
    for (const auto& p : {te_path, unet_path, vae_path}) {
        std::ifstream f(p);
        if (!f.good()) {
            std::cerr << "[load] Model file not found: " << p << std::endl;
            return false;
        }
    }

    return true;
}

// ---------------------------------------------------------------------------
//  Sequential session management (load → use → release)
// ---------------------------------------------------------------------------

void DreamLitePipeline::ensure_text_encoder() {
    if (!text_encoder_session_) {
        std::string sep = "/";
#ifdef _WIN32
        sep = "\\";
#endif
        auto path = model_dir_ + sep + "text_encoder_int4.onnx";
        std::cout << "[load] Text encoder: " << path << std::endl;
        Timer t;
        text_encoder_session_ = std::make_unique<Ort::Session>(g_env, ORT_PATH(path), *session_opts_);
        std::cout << "[load] Text encoder loaded in " << t.elapsed_ms() << " ms" << std::endl;
    }
}

void DreamLitePipeline::release_text_encoder() {
    text_encoder_session_.reset();
    std::cout << "[release] Text encoder released" << std::endl;
}

void DreamLitePipeline::ensure_unet() {
    if (!unet_session_) {
        std::string sep = "/";
#ifdef _WIN32
        sep = "\\";
#endif
        auto path = model_dir_ + sep + "unet_1024_fp32.onnx";
        std::cout << "[load] UNet: " << path << std::endl;
        Timer t;
        if (nnapi_available_) {
            try {
                unet_session_ = std::make_unique<Ort::Session>(g_env, ORT_PATH(path), *session_opts_accel_);
                std::cout << "[load] UNet loaded (NNAPI) in " << t.elapsed_ms() << " ms" << std::endl;
                return;
            } catch (const std::exception& e) {
                std::cout << "[load] UNet NNAPI failed: " << e.what() << ", falling back to CPU" << std::endl;
            }
        }
        unet_session_ = std::make_unique<Ort::Session>(g_env, ORT_PATH(path), *session_opts_);
        std::cout << "[load] UNet loaded (CPU) in " << t.elapsed_ms() << " ms" << std::endl;
    }
}

void DreamLitePipeline::release_unet() {
    unet_session_.reset();
    std::cout << "[release] UNet released" << std::endl;
}

void DreamLitePipeline::ensure_vae() {
    if (!vae_session_) {
        std::string sep = "/";
#ifdef _WIN32
        sep = "\\";
#endif
        auto path = model_dir_ + sep + "vae_1024_fp32.onnx";
        std::cout << "[load] VAE: " << path << std::endl;
        Timer t;
        if (nnapi_available_) {
            try {
                vae_session_ = std::make_unique<Ort::Session>(g_env, ORT_PATH(path), *session_opts_accel_);
                std::cout << "[load] VAE loaded (NNAPI) in " << t.elapsed_ms() << " ms" << std::endl;
                return;
            } catch (const std::exception& e) {
                std::cout << "[load] VAE NNAPI failed: " << e.what() << ", falling back to CPU" << std::endl;
            }
        }
        vae_session_ = std::make_unique<Ort::Session>(g_env, ORT_PATH(path), *session_opts_);
        std::cout << "[load] VAE loaded (CPU) in " << t.elapsed_ms() << " ms" << std::endl;
    }
}

void DreamLitePipeline::release_vae() {
    vae_session_.reset();
    std::cout << "[release] VAE released" << std::endl;
}

// ---------------------------------------------------------------------------
//  Stage 1: Encode prompt
// ---------------------------------------------------------------------------

std::vector<float> DreamLitePipeline::encode_prompt(const std::string& prompt, int& seq_len) {
    std::string formatted = format_prompt(prompt);

    // Tokenize
    auto token_ids = tokenizer_->encode(formatted);
    int total_len = static_cast<int>(token_ids.size());
    std::cout << "[encode] Tokens: " << total_len << std::endl;

    // Build attention mask (all 1s)
    std::vector<int64_t> attn_mask(total_len, 1);

    // Prepare input tensors
    std::array<int64_t, 2> input_shape = {1, total_len};
    auto mem_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    Ort::Value input_ids_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, token_ids.data(), total_len, input_shape.data(), 2);
    Ort::Value attn_mask_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, attn_mask.data(), total_len, input_shape.data(), 2);

    // Run text encoder
    const char* input_names[] = {"input_ids", "attention_mask"};
    const char* output_names[] = {"hidden_states"};

    Timer t;
    auto outputs = text_encoder_session_->Run(
        Ort::RunOptions{nullptr},
        input_names,
        std::array<Ort::Value, 2>{std::move(input_ids_tensor), std::move(attn_mask_tensor)}.data(),
        2,
        output_names, 1);
    std::cout << "[encode] Text encoder inference: " << t.elapsed_ms() << " ms" << std::endl;

    // Extract hidden states: shape [1, total_len, 2048]
    auto& hs = outputs[0];
    auto shape = get_shape(hs);
    int total_seq = static_cast<int>(shape[1]);
    int hidden_dim = static_cast<int>(shape[2]);

    // Get raw data (fp16 or fp32 depending on model)
    auto hs_info = hs.GetTensorTypeAndShapeInfo();
    auto elem_type = hs_info.GetElementType();

    // Drop first drop_idx_ tokens (34 for generate mode)
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
    float width, float height)
{
    // sample: [1, 4, H, W] -> concat with zeros -> [1, 4, H, 2*W]
    int latent_h = static_cast<int>(std::sqrt(sample.size() / 4.0f));  // approximate
    // Actually compute from config
    int H = 0, W = 0;
    {
        // sample has 4 channels, find H and W
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
            // Copy noise latent to left half
            std::memcpy(
                model_input.data() + (c * H + h) * concat_W,
                sample.data() + (c * H + h) * W,
                W * sizeof(float));
            // Right half stays zero (conditioning image = zeros)
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

    std::array<int64_t, 4> shape = {1, 4, H, W};
    Ort::Value latent_tensor = Ort::Value::CreateTensor<float>(
        mem_info, const_cast<float*>(latent.data()), latent_size, shape.data(), 4);

    const char* input_names[] = {"latent"};
    const char* output_names[] = {"image"};

    Timer t;
    auto outputs = vae_session_->Run(
        Ort::RunOptions{nullptr},
        input_names, &latent_tensor, 1,
        output_names, 1);
    std::cout << "[vae] VAE decode: " << t.elapsed_ms() << " ms" << std::endl;

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

ImageOutput DreamLitePipeline::generate(const GenerationConfig& config) {
    Timer total_timer;

    int latent_h = config.height / vae_scale_factor_;
    int latent_w = config.width / vae_scale_factor_;
    int latent_size = latent_channels_ * latent_h * latent_w;

    std::cout << "\n========== DreamLite Generate ==========" << std::endl;
    std::cout << "Prompt: \"" << config.prompt << "\"" << std::endl;
    std::cout << "Resolution: " << config.width << "x" << config.height << std::endl;
    std::cout << "Steps: " << config.num_steps << ", Seed: " << config.seed << std::endl;
    std::cout << "Latent: [1, " << latent_channels_ << ", " << latent_h << ", " << latent_w << "]" << std::endl;

    // ── Stage 1: Load text encoder, encode prompt, then release ──
    std::cout << "\n--- Stage 1: Text Encoding ---" << std::endl;
    ensure_text_encoder();
    int seq_len = 0;
    auto prompt_embeds = encode_prompt(config.prompt, seq_len);
    release_text_encoder();  // free ~400 MB before loading UNet

    // 2. Initialize scheduler
    SchedulerState scheduler;
    scheduler.init(config.num_steps, latent_h, latent_w);
    std::cout << "\nTimesteps: [";
    for (int i = 0; i < config.num_steps; i++) {
        std::cout << scheduler.timesteps[i];
        if (i < config.num_steps - 1) std::cout << ", ";
    }
    std::cout << "]" << std::endl;

    // 3. Initialize latents
    std::cout << "\n--- Stage 2: Denoising ---" << std::endl;
    auto latents = randn(latent_size, config.seed);

    // ── Load UNet for denoising loop, then release ──
    ensure_unet();
    for (int step = 0; step < config.num_steps; step++) {
        Timer step_timer;
        float sigma = scheduler.sigmas[step];
        float sigma_next = scheduler.sigmas[step + 1];
        float dt = sigma_next - sigma;

        std::cout << "  Step " << step << "/" << config.num_steps
                  << " (t=" << scheduler.timesteps[step]
                  << ", sigma=" << sigma << " -> " << sigma_next << "): ";

        // Run UNet
        auto noise_pred = run_unet(
            latents,
            scheduler.timesteps[step],
            prompt_embeds, seq_len,
            static_cast<float>(config.width),
            static_cast<float>(config.height));

        // Euler step: latents += dt * noise_pred
        for (int i = 0; i < latent_size; i++) {
            latents[i] += dt * noise_pred[i];
        }

        std::cout << step_timer.elapsed_ms() << " ms" << std::endl;
    }
    release_unet();  // free ~1.5 GB before loading VAE

    // ── Stage 3: Load VAE, decode, then release ──
    std::cout << "\n--- Stage 3: VAE Decode ---" << std::endl;
    ensure_vae();
    // scaling_factor=1.0, shift_factor=0.0 -> no pre-scaling needed
    auto decoded = run_vae_decode(latents);
    release_vae();

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
    return output;
}
