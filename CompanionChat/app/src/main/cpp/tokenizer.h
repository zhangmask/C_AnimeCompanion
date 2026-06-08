#pragma once
#include <string>
#include <vector>
#include <unordered_map>
#include <cstdint>

// Simple byte-level BPE tokenizer compatible with Qwen2/GPT-2 tokenizers
class BpeTokenizer {
public:
    bool load(const std::string& vocab_path, const std::string& merges_path);

    // Encode text to token IDs
    std::vector<int64_t> encode(const std::string& text) const;

    // Get vocab size
    size_t vocab_size() const { return vocab_.size(); }

    // Special token IDs
    int64_t pad_token_id = 151643;   // <|endoftext|>
    int64_t im_start_id  = 151644;   // <|im_start|>
    int64_t im_end_id    = 151645;   // <|im_end|>

private:
    std::unordered_map<std::string, int64_t> vocab_;
    std::vector<std::pair<std::string, std::string>> merges_;
    std::unordered_map<std::string, int> merge_ranks_;

    // Byte-to-unicode mapping (GPT-2 style)
    static std::unordered_map<uint8_t, char32_t> byte_to_unicode_;
    static std::unordered_map<uint8_t, char32_t> init_byte_to_unicode();

    std::string bytes_to_unicode(const std::string& bytes) const;
    std::vector<std::string> bpe(const std::string& token) const;
};
