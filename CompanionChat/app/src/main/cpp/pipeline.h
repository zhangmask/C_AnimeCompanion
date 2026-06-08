#pragma once
#include <string>
#include <vector>
#include <memory>
#include <cstdint>

#include <onnxruntime_cxx_api.h>

struct GenerationConfig {
    int width = 1024;
    int height = 1024;
    int num_steps = 4;
    int seed = 42;
    std::string prompt;
};

struct ImageOutput {
    std::vector<uint8_t> pixels;  // RGB, row-major, H*W*3
    int width;
    int height;
};

class DreamLitePipeline {
public:
    DreamLitePipeline();
    ~DreamLitePipeline();

    bool load(const std::string& model_dir);
    ImageOutput generate(const GenerationConfig& config);

private:
    // ONNX Runtime sessions
    std::unique_ptr<Ort::SessionOptions> session_opts_;        // CPU options (text encoder)
    std::unique_ptr<Ort::SessionOptions> session_opts_accel_;   // NNAPI options (UNet/VAE)
    std::unique_ptr<Ort::Session> text_encoder_session_;
    std::unique_ptr<Ort::Session> unet_session_;
    std::unique_ptr<Ort::Session> vae_session_;
    bool nnapi_available_ = false;

    // Tokenizer
    std::unique_ptr<class BpeTokenizer> tokenizer_;

    // Model directory for lazy loading
    std::string model_dir_;

    // Model config
    int hidden_size_ = 2048;
    int latent_channels_ = 4;
    int vae_scale_factor_ = 8;
    int drop_idx_ = 34;

    // Sequential session management (reduce peak memory)
    void ensure_text_encoder();
    void release_text_encoder();
    void ensure_unet();
    void release_unet();
    void ensure_vae();
    void release_vae();

    // Pipeline stages
    std::vector<float> encode_prompt(const std::string& prompt, int& seq_len);
    std::vector<float> run_unet(
        const std::vector<float>& sample,
        float timestep,
        const std::vector<float>& encoder_hidden_states,
        int seq_len,
        float width, float height);
    std::vector<float> run_vae_decode(const std::vector<float>& latent);
};
