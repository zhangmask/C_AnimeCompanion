#include "tokenizer.h"
#include <fstream>
#include <sstream>
#include <algorithm>
#include <regex>
#include <nlohmann/json.hpp>

using json = nlohmann::json;

// GPT-2 style byte-to-unicode mapping
std::unordered_map<uint8_t, char32_t> BpeTokenizer::byte_to_unicode_ = BpeTokenizer::init_byte_to_unicode();

std::unordered_map<uint8_t, char32_t> BpeTokenizer::init_byte_to_unicode() {
    std::unordered_map<uint8_t, char32_t> m;
    int n = 0;
    for (int b = 0; b < 256; b++) {
        if ((b >= 33 && b <= 126) || (b >= 161 && b <= 172) || (b >= 174 && b <= 255)) {
            m[b] = static_cast<char32_t>(b);
        } else {
            m[b] = static_cast<char32_t>(256 + n);
            n++;
        }
    }
    return m;
}

static std::string utf8_encode_char(char32_t cp) {
    std::string result;
    if (cp < 0x80) {
        result += static_cast<char>(cp);
    } else if (cp < 0x800) {
        result += static_cast<char>(0xC0 | (cp >> 6));
        result += static_cast<char>(0x80 | (cp & 0x3F));
    } else if (cp < 0x10000) {
        result += static_cast<char>(0xE0 | (cp >> 12));
        result += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        result += static_cast<char>(0x80 | (cp & 0x3F));
    } else {
        result += static_cast<char>(0xF0 | (cp >> 18));
        result += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
        result += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        result += static_cast<char>(0x80 | (cp & 0x3F));
    }
    return result;
}

static std::string encode_bytes_to_unicode(const std::string& bytes,
    const std::unordered_map<uint8_t, char32_t>& b2u) {
    std::string result;
    for (unsigned char b : bytes) {
        auto it = b2u.find(b);
        if (it != b2u.end()) {
            result += utf8_encode_char(it->second);
        }
    }
    return result;
}

std::vector<std::string> BpeTokenizer::bpe(const std::string& token) const {
    // Split token into UTF-8 code points
    std::vector<std::string> word;
    for (size_t i = 0; i < token.size(); ) {
        unsigned char c = token[i];
        size_t len = 1;
        if ((c & 0x80) == 0) len = 1;
        else if ((c & 0xE0) == 0xC0) len = 2;
        else if ((c & 0xF0) == 0xE0) len = 3;
        else if ((c & 0xF8) == 0xF0) len = 4;
        word.push_back(token.substr(i, len));
        i += len;
    }
    if (word.size() <= 1) return word;

    while (true) {
        int best_rank = INT32_MAX;
        int best_idx = -1;
        for (size_t i = 0; i + 1 < word.size(); i++) {
            std::string pair = word[i] + " " + word[i + 1];
            auto it = merge_ranks_.find(pair);
            if (it != merge_ranks_.end() && it->second < best_rank) {
                best_rank = it->second;
                best_idx = static_cast<int>(i);
            }
        }
        if (best_idx < 0) break;
        std::string merged = word[best_idx] + word[best_idx + 1];
        std::vector<std::string> new_word;
        for (size_t i = 0; i < word.size(); i++) {
            if (static_cast<int>(i) == best_idx) {
                new_word.push_back(merged);
                i++;
            } else {
                new_word.push_back(word[i]);
            }
        }
        word = std::move(new_word);
        if (word.size() <= 1) break;
    }
    return word;
}

bool BpeTokenizer::load(const std::string& vocab_path, const std::string& merges_path) {
    {
        std::ifstream f(vocab_path);
        if (!f.is_open()) return false;
        json j;
        f >> j;
        for (auto& [key, val] : j.items()) {
            vocab_[key] = val.get<int64_t>();
        }
    }
    {
        std::ifstream f(merges_path);
        if (!f.is_open()) return false;
        std::string line;
        int rank = 0;
        while (std::getline(f, line)) {
            if (line.empty() || line[0] == '#') continue;
            auto pos = line.find(' ');
            if (pos == std::string::npos) continue;
            merge_ranks_[line] = rank++;
        }
    }
    // Load special tokens from added_tokens.json (same directory as vocab.json)
    load_special_tokens(vocab_path);
    return true;
}

void BpeTokenizer::load_special_tokens(const std::string& vocab_path) {
    // Derive added_tokens.json path from vocab.json path
    auto slash = vocab_path.find_last_of("/\\");
    std::string dir = (slash != std::string::npos) ? vocab_path.substr(0, slash) : ".";
    std::string added_path = dir + "/added_tokens.json";

    std::ifstream f(added_path);
    if (!f.is_open()) {
        // Fallback: hardcode essential special tokens for Qwen2/3
        special_tokens_["<|im_start|>"] = 151644;
        special_tokens_["<|im_end|>"]   = 151645;
        special_tokens_["<|endoftext|>"] = 151643;
        return;
    }

    json j;
    f >> j;
    for (auto& [key, val] : j.items()) {
        special_tokens_[key] = val.get<int64_t>();
    }
}

// GPT-2 pre-tokenizer pattern (simplified)
// Matches: words, numbers, punctuation, whitespace
static std::vector<std::string> pretokenize(const std::string& text) {
    std::vector<std::string> pieces;
    // Simple split: whitespace boundaries, but keep leading spaces
    // GPT-2 pattern: 's|'t|'re|'ve|'m|'ll|'d|[\w]+|[^\w\s]|\s+(?!\S)|\s+
    // Simplified: split by spaces, attach leading space to next word
    bool prev_was_space = false;
    std::string current;

    for (size_t i = 0; i < text.size(); i++) {
        char c = text[i];
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            if (!current.empty()) {
                pieces.push_back(current);
                current.clear();
            }
            // Accumulate spaces (they'll be prepended to next word)
            current += c;
        } else {
            current += c;
        }
    }
    if (!current.empty()) {
        pieces.push_back(current);
    }
    return pieces;
}

std::vector<int64_t> BpeTokenizer::encode(const std::string& text) const {
    std::vector<int64_t> tokens;

    // ── Step 1: Split text by special tokens ──
    // Special tokens (e.g. <|im_start|>, <|im_end|>) must be emitted as single
    // token IDs, NOT broken down by BPE.  We scan the text left-to-right, finding
    // the earliest matching special token at each position.
    std::vector<std::pair<bool, std::string>> segments; // (is_special, text)

    if (special_tokens_.empty()) {
        segments.push_back({false, text});
    } else {
        size_t pos = 0;
        while (pos < text.size()) {
            // Find the earliest special token match starting at or after `pos`
            size_t best_pos = std::string::npos;
            std::string best_tok;
            for (const auto& [tok, _] : special_tokens_) {
                size_t found = text.find(tok, pos);
                if (found != std::string::npos && (best_pos == std::string::npos || found < best_pos)) {
                    best_pos = found;
                    best_tok = tok;
                }
            }
            if (best_pos == std::string::npos) {
                // No more special tokens — rest is regular text
                if (pos < text.size()) {
                    segments.push_back({false, text.substr(pos)});
                }
                break;
            }
            // Regular text before the special token
            if (best_pos > pos) {
                segments.push_back({false, text.substr(pos, best_pos - pos)});
            }
            // The special token itself
            segments.push_back({true, best_tok});
            pos = best_pos + best_tok.size();
        }
    }

    // ── Step 2: Process each segment ──
    static const int64_t pad_id = 151643;

    for (const auto& [is_special, seg] : segments) {
        if (is_special) {
            // Emit special token ID directly
            auto it = special_tokens_.find(seg);
            if (it != special_tokens_.end()) {
                tokens.push_back(it->second);
            }
            continue;
        }

        // Regular text: split into words by whitespace, prepend leading spaces
        std::vector<std::string> words;
        std::string current_word;
        bool in_word = false;
        std::string pending_spaces;

        for (size_t i = 0; i < seg.size(); i++) {
            char c = seg[i];
            bool is_ws = (c == ' ' || c == '\t' || c == '\n' || c == '\r');

            if (is_ws) {
                if (in_word) {
                    words.push_back(current_word);
                    current_word.clear();
                    in_word = false;
                }
                pending_spaces += c;
            } else {
                if (!in_word) {
                    if (!pending_spaces.empty()) {
                        current_word = pending_spaces;
                        pending_spaces.clear();
                    } else {
                        current_word.clear();
                    }
                    in_word = true;
                }
                current_word += c;
            }
        }
        if (in_word) {
            words.push_back(current_word);
        } else if (!pending_spaces.empty()) {
            // Preserve trailing whitespace (e.g., "\n" between <|im_end|> and <|im_start|>).
            // Without this, newlines after special tokens are silently dropped,
            // causing token misalignment with HuggingFace tokenizer.
            words.push_back(pending_spaces);
        }

        for (const auto& word : words) {
            std::string unicode_word = encode_bytes_to_unicode(word, byte_to_unicode_);
            auto bpe_tokens = bpe(unicode_word);
            for (const auto& tok : bpe_tokens) {
                auto it = vocab_.find(tok);
                if (it != vocab_.end()) {
                    tokens.push_back(it->second);
                } else {
                    tokens.push_back(pad_id);
                }
            }
        }
    }

    return tokens;
}
