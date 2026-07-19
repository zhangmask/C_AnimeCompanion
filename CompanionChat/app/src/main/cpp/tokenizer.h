#pragma once
#include <string>
#include <vector>
#include <unordered_map>
#include <cstdint>

// Simple byte-level BPE tokenizer compatible with Qwen2/GPT-2 tokenizers
class BpeTokenizer {
public:
    bool load(const std::string& vocab_path, const std::string& merges_path);

    // Encode text to token IDs (handles special tokens like <|im_start|>)
    std::vector<int64_t> encode(const std::string& text) const;

    // Get vocab size
    size_t vocab_size() const { return vocab_.size(); }

private:
    std::unordered_map<std::string, int64_t> vocab_;
    std::vector<std::pair<std::string, std::string>> merges_;
    std::unordered_map<std::string, int> merge_ranks_;

    // Special tokens (from added_tokens.json) — these are NOT BPE-processed
    std::unordered_map<std::string, int64_t> special_tokens_;

    // Byte-to-unicode mapping (GPT-2 style)
    static std::unordered_map<uint8_t, char32_t> byte_to_unicode_;
    static std::unordered_map<uint8_t, char32_t> init_byte_to_unicode();

    std::string bytes_to_unicode(const std::string& bytes) const;
    std::vector<std::string> bpe(const std::string& token) const;

    // Load added_tokens.json from the same directory as vocab.json
    void load_special_tokens(const std::string& vocab_path);
};
