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
    int64_t seed = 42;
    std::string prompt;

    // img2img (real edit mode — vision-based edit): when reference_latents_path
    // is non-empty, the pipeline runs in "edit" task mode (matching Python
    // pipeline_dreamlite_mobile.py).
    //
    // Vision-based edit flow (matches test_mobile_models.py — the verified
    // working reference):
    //   1. Load reference PNG (path derived from reference_latents_path by
    //      replacing ".latents.bin" → ".png"), resize to 256×256, normalize
    //      to [-1,1], extract patches → pixel_values [1,256,1536].
    //   2. vision_encoder.onnx: pixel_values + image_grid_thw → image_embeds
    //      [64, 2048] (fp16).
    //   3. text_encoder_edit.onnx: input_ids + attention_mask + position_ids
    //      (3D M-RoPE [3,1,seq_len]) + image_embeds → hidden_states. Drop
    //      first 64 tokens (drop_idx=64) → prompt_embeds.
    //   4. Load .latents.bin as image_latents (RAW domain — no scaling).
    //      Concatenate [noise | image_latents] as UNet conditioning input.
    //   5. Run 4-step denoising with linspace sigma schedule + shifted
    //      timesteps (matching infer_edit.py).
    //
    // The reference image is fed BOTH to the vision tower (semantic
    // conditioning via prompt_embeds) AND to the UNet (pixel conditioning
    // via image_latents). The blind-edit approach (text_encoder_generate
    // only, no vision input) produces noise — verified via
    // test_mobile_models.py comparison (blind edit std=39.91 = noise;
    // vision edit std=66.55 = real image).
    //
    // `strength` is ignored in edit mode (kept for API compat).
    std::string reference_latents_path;
    float strength = 0.6f;

    // Where to save the final denoised latents (for future img2img reuse).
    // Empty = don't save.
    std::string output_latents_path;
};

struct ImageOutput {
    std::vector<uint8_t> pixels;  // RGB, row-major, H*W*3
    int width;
    int height;
    // Non-empty when generate() refuses to run (e.g. generation limit reached,
    // memory too low). When set, width==0 && height==0 && pixels empty.
    std::string error_message;
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
    std::unique_ptr<Ort::Session> text_encoder_session_;        // text_encoder_generate.onnx (txt2img)
    std::unique_ptr<Ort::Session> vision_encoder_session_;      // vision_encoder.onnx (img2img edit only)
    std::unique_ptr<Ort::Session> text_encoder_edit_session_;   // text_encoder_edit.onnx (img2img edit only)
    std::unique_ptr<Ort::Session> unet_session_;
    std::unique_ptr<Ort::Session> vae_session_;
    bool nnapi_available_ = false;

    // Tokenizer
    std::unique_ptr<class BpeTokenizer> tokenizer_;

    // Model directory for lazy loading
    std::string model_dir_;

    // Resolved model file paths (set during load)
    std::string te_path_;
    std::string te_edit_path_;     // text_encoder_edit.onnx (empty if not present)
    std::string vis_path_;         // vision_encoder.onnx (empty if not present)
    std::string unet_path_;
    std::string vae_path_;

    // Model config
    int hidden_size_ = 2048;
    int latent_channels_ = 4;
    int vae_scale_factor_ = 8;
    int drop_idx_ = 34;  // 34 for generate mode, 64 for edit mode (set per-call in generate())

    // Edit mode flag — set at the start of generate() based on
    // config.reference_latents_path. Read by format_prompt() and encode_prompt()
    // to select the appropriate prompt template and drop_idx.
    bool edit_mode_ = false;

    // Sequential session management (reduce peak memory)
    void ensure_text_encoder();
    void release_text_encoder();
    void ensure_text_encoder_edit();
    void release_text_encoder_edit();
    void ensure_vision_encoder();
    void release_vision_encoder();
    void ensure_unet();
    void release_unet();
    void ensure_vae();
    void release_vae();

    // Recreate session options (frees ORT memory patterns accumulated during
    // inference). Called lazily by ensure_* after a full_reset.
    void init_session_options();

    // Destroy ALL ORT state (sessions, session options, env) to return every
    // byte of ORT-managed memory to the OS. The env and session options are
    // lazily recreated by the next ensure_* call. Without this, ORT's internal
    // memory patterns and thread-pool state accumulate across generations,
    // eventually causing LMK to kill the app on the 3rd+ generation.
    void full_reset();

    // Pipeline stages
    std::vector<float> encode_prompt(const std::string& prompt, int& seq_len);
    // Vision-based edit encode (matches test_mobile_models.py encode_edit):
    //   1. Load reference PNG (path = latents_path with ".latents.bin"→".png"),
    //      resize to 256×256, normalize to [-1,1], extract patches → pixel_values.
    //   2. vision_encoder → image_embeds [64, 2048].
    //   3. Build edit prompt with 64× <|image_pad|>, tokenize, expand image_pad.
    //   4. compute_position_ids (3D M-RoPE [3,1,seq_len]).
    //   5. text_encoder_edit → hidden_states, drop first 64 tokens.
    // reference_latents_path is used to derive the PNG path; returns prompt
    // embeds [seq_len, 2048] (fp32) and sets seq_len.
    std::vector<float> encode_prompt_edit(const std::string& user_prompt,
                                          const std::string& reference_latents_path,
                                          int& seq_len);
    // image_latents_data: when non-null (edit mode), concatenated as the right
    // half of UNet input [noise | image_latents]. When null (generate mode),
    // the right half is zeros. Size must be latent_size (1*4*H*W floats).
    // NOTE: image_latents_data must be in RAW domain (vae_encoder output,
    // without *3+0.5 scaling) — matches test_mobile_models.py.
    std::vector<float> run_unet(
        const std::vector<float>& sample,
        float timestep,
        const std::vector<float>& encoder_hidden_states,
        int seq_len,
        float width, float height,
        const float* image_latents_data = nullptr);
    std::vector<float> run_vae_decode(const std::vector<float>& latent);
};
