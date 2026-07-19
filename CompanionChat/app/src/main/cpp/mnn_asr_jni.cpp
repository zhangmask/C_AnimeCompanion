#include <jni.h>
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <MNN/ErrorCode.hpp>
#include <MNN/expr/Module.hpp>
#include <MNN/expr/ExprCreator.hpp>
#include <MNN/expr/Executor.hpp>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>
#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <algorithm>

#define TAG "MNN_ASR_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// File-based logging (logcat buffer is too small on this device)
static const char* ASR_LOG_PATH = "/data/data/com.companion.chat/files/asr_jni_log.txt";
static void flog(const char* fmt, ...) {
    FILE* f = fopen(ASR_LOG_PATH, "a");
    if (!f) return;
    va_list ap;
    va_start(ap, fmt);
    vfprintf(f, fmt, ap);
    fprintf(f, "\n");
    va_end(ap);
    fclose(f);
}

// ── Radix-2 FFT (in-place, length must be power of 2) ──
static void fft(std::vector<float>& real, std::vector<float>& imag) {
    int n = real.size();
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) {
            j ^= bit;
        }
        j ^= bit;
        if (i < j) {
            std::swap(real[i], real[j]);
            std::swap(imag[i], imag[j]);
        }
    }
    for (int len = 2; len <= n; len <<= 1) {
        float ang = -2.0f * M_PI / len;
        float wlen_r = cosf(ang), wlen_i = sinf(ang);
        for (int i = 0; i < n; i += len) {
            float w_r = 1.0f, w_i = 0.0f;
            for (int j = 0; j < len / 2; j++) {
                float u_r = real[i + j], u_i = imag[i + j];
                float v_r = real[i + j + len/2] * w_r - imag[i + j + len/2] * w_i;
                float v_i = real[i + j + len/2] * w_i + imag[i + j + len/2] * w_r;
                real[i + j] = u_r + v_r;
                imag[i + j] = u_i + v_i;
                real[i + j + len/2] = u_r - v_r;
                imag[i + j + len/2] = u_i - v_i;
                float nw_r = w_r * wlen_r - w_i * wlen_i;
                w_i = w_r * wlen_i + w_i * wlen_r;
                w_r = nw_r;
            }
        }
    }
}

// ── Mel filterbank (HTK formula) ──
static float hzToMel(float hz) { return 2595.0f * log10f(1.0f + hz / 700.0f); }
static float melToHz(float mel) { return 700.0f * (powf(10.0f, mel / 2595.0f) - 1.0f); }

static std::vector<std::vector<float>> buildMelFilterbank(int n_fft, int n_mels, int sr, float fmin, float fmax) {
    int n_freqs = n_fft / 2 + 1;
    float mel_min = hzToMel(fmin);
    float mel_max = hzToMel(fmax);
    float mel_step = (mel_max - mel_min) / (n_mels + 1);

    std::vector<float> mel_points(n_mels + 2);
    for (int i = 0; i < n_mels + 2; i++) {
        mel_points[i] = melToHz(mel_min + i * mel_step);
    }

    std::vector<std::vector<float>> filterbank(n_mels, std::vector<float>(n_freqs, 0.0f));
    for (int m = 0; m < n_mels; m++) {
        float left = mel_points[m];
        float center = mel_points[m + 1];
        float right = mel_points[m + 2];
        for (int k = 0; k < n_freqs; k++) {
            float freq = (float)k * sr / n_fft;
            if (freq >= left && freq <= center) {
                filterbank[m][k] = (freq - left) / (center - left + 1e-10f);
            } else if (freq > center && freq <= right) {
                filterbank[m][k] = (right - freq) / (right - center + 1e-10f);
            }
        }
    }
    return filterbank;
}

// ── CMVN normalization arrays (from SenseVoice ONNX metadata) ──
// neg_mean and inv_stddev, each 560-dim (80 mels × 7 stacked frames)
static const float CMVN_NEG_MEAN[560] = {
    -8.311879f, -8.600912f, -9.615928f, -10.435950f, -11.212920f, -11.883330f, -12.362430f, -12.637060f, -12.881800f, -12.830660f,
    -12.891030f, -12.956660f, -13.197630f, -13.405980f, -13.491130f, -13.554600f, -13.556390f, -13.519150f, -13.682840f, -13.532890f,
    -13.421070f, -13.655190f, -13.507130f, -13.752510f, -13.767150f, -13.874080f, -13.731090f, -13.704120f, -13.560730f, -13.534880f,
    -13.548950f, -13.562280f, -13.594080f, -13.620470f, -13.641980f, -13.661090f, -13.626690f, -13.582970f, -13.573870f, -13.473900f,
    -13.530630f, -13.483480f, -13.610470f, -13.647160f, -13.715460f, -13.791840f, -13.906140f, -14.030980f, -14.182050f, -14.358810f,
    -14.484190f, -14.601720f, -14.705910f, -14.833620f, -14.921220f, -15.006220f, -15.051220f, -15.031190f, -14.990280f, -14.923020f,
    -14.869270f, -14.826910f, -14.797200f, -14.769090f, -14.713560f, -14.612770f, -14.516960f, -14.422520f, -14.364050f, -14.304510f,
    -14.231610f, -14.198510f, -14.166330f, -14.156490f, -14.105040f, -13.995180f, -13.795620f, -13.399600f, -12.776700f, -11.712080f,
    -8.311879f, -8.600912f, -9.615928f, -10.435950f, -11.212920f, -11.883330f, -12.362430f, -12.637060f, -12.881800f, -12.830660f,
    -12.891030f, -12.956660f, -13.197630f, -13.405980f, -13.491130f, -13.554600f, -13.556390f, -13.519150f, -13.682840f, -13.532890f,
    -13.421070f, -13.655190f, -13.507130f, -13.752510f, -13.767150f, -13.874080f, -13.731090f, -13.704120f, -13.560730f, -13.534880f,
    -13.548950f, -13.562280f, -13.594080f, -13.620470f, -13.641980f, -13.661090f, -13.626690f, -13.582970f, -13.573870f, -13.473900f,
    -13.530630f, -13.483480f, -13.610470f, -13.647160f, -13.715460f, -13.791840f, -13.906140f, -14.030980f, -14.182050f, -14.358810f,
    -14.484190f, -14.601720f, -14.705910f, -14.833620f, -14.921220f, -15.006220f, -15.051220f, -15.031190f, -14.990280f, -14.923020f,
    -14.869270f, -14.826910f, -14.797200f, -14.769090f, -14.713560f, -14.612770f, -14.516960f, -14.422520f, -14.364050f, -14.304510f,
    -14.231610f, -14.198510f, -14.166330f, -14.156490f, -14.105040f, -13.995180f, -13.795620f, -13.399600f, -12.776700f, -11.712080f,
    -8.311879f, -8.600912f, -9.615928f, -10.435950f, -11.212920f, -11.883330f, -12.362430f, -12.637060f, -12.881800f, -12.830660f,
    -12.891030f, -12.956660f, -13.197630f, -13.405980f, -13.491130f, -13.554600f, -13.556390f, -13.519150f, -13.682840f, -13.532890f,
    -13.421070f, -13.655190f, -13.507130f, -13.752510f, -13.767150f, -13.874080f, -13.731090f, -13.704120f, -13.560730f, -13.534880f,
    -13.548950f, -13.562280f, -13.594080f, -13.620470f, -13.641980f, -13.661090f, -13.626690f, -13.582970f, -13.573870f, -13.473900f,
    -13.530630f, -13.483480f, -13.610470f, -13.647160f, -13.715460f, -13.791840f, -13.906140f, -14.030980f, -14.182050f, -14.358810f,
    -14.484190f, -14.601720f, -14.705910f, -14.833620f, -14.921220f, -15.006220f, -15.051220f, -15.031190f, -14.990280f, -14.923020f,
    -14.869270f, -14.826910f, -14.797200f, -14.769090f, -14.713560f, -14.612770f, -14.516960f, -14.422520f, -14.364050f, -14.304510f,
    -14.231610f, -14.198510f, -14.166330f, -14.156490f, -14.105040f, -13.995180f, -13.795620f, -13.399600f, -12.776700f, -11.712080f,
    -8.311879f, -8.600912f, -9.615928f, -10.435950f, -11.212920f, -11.883330f, -12.362430f, -12.637060f, -12.881800f, -12.830660f,
    -12.891030f, -12.956660f, -13.197630f, -13.405980f, -13.491130f, -13.554600f, -13.556390f, -13.519150f, -13.682840f, -13.532890f,
    -13.421070f, -13.655190f, -13.507130f, -13.752510f, -13.767150f, -13.874080f, -13.731090f, -13.704120f, -13.560730f, -13.534880f,
    -13.548950f, -13.562280f, -13.594080f, -13.620470f, -13.641980f, -13.661090f, -13.626690f, -13.582970f, -13.573870f, -13.473900f,
    -13.530630f, -13.483480f, -13.610470f, -13.647160f, -13.715460f, -13.791840f, -13.906140f, -14.030980f, -14.182050f, -14.358810f,
    -14.484190f, -14.601720f, -14.705910f, -14.833620f, -14.921220f, -15.006220f, -15.051220f, -15.031190f, -14.990280f, -14.923020f,
    -14.869270f, -14.826910f, -14.797200f, -14.769090f, -14.713560f, -14.612770f, -14.516960f, -14.422520f, -14.364050f, -14.304510f,
    -14.231610f, -14.198510f, -14.166330f, -14.156490f, -14.105040f, -13.995180f, -13.795620f, -13.399600f, -12.776700f, -11.712080f,
    -8.311879f, -8.600912f, -9.615928f, -10.435950f, -11.212920f, -11.883330f, -12.362430f, -12.637060f, -12.881800f, -12.830660f,
    -12.891030f, -12.956660f, -13.197630f, -13.405980f, -13.491130f, -13.554600f, -13.556390f, -13.519150f, -13.682840f, -13.532890f,
    -13.421070f, -13.655190f, -13.507130f, -13.752510f, -13.767150f, -13.874080f, -13.731090f, -13.704120f, -13.560730f, -13.534880f,
    -13.548950f, -13.562280f, -13.594080f, -13.620470f, -13.641980f, -13.661090f, -13.626690f, -13.582970f, -13.573870f, -13.473900f,
    -13.530630f, -13.483480f, -13.610470f, -13.647160f, -13.715460f, -13.791840f, -13.906140f, -14.030980f, -14.182050f, -14.358810f,
    -14.484190f, -14.601720f, -14.705910f, -14.833620f, -14.921220f, -15.006220f, -15.051220f, -15.031190f, -14.990280f, -14.923020f,
    -14.869270f, -14.826910f, -14.797200f, -14.769090f, -14.713560f, -14.612770f, -14.516960f, -14.422520f, -14.364050f, -14.304510f,
    -14.231610f, -14.198510f, -14.166330f, -14.156490f, -14.105040f, -13.995180f, -13.795620f, -13.399600f, -12.776700f, -11.712080f,
    -8.311879f, -8.600912f, -9.615928f, -10.435950f, -11.212920f, -11.883330f, -12.362430f, -12.637060f, -12.881800f, -12.830660f,
    -12.891030f, -12.956660f, -13.197630f, -13.405980f, -13.491130f, -13.554600f, -13.556390f, -13.519150f, -13.682840f, -13.532890f,
    -13.421070f, -13.655190f, -13.507130f, -13.752510f, -13.767150f, -13.874080f, -13.731090f, -13.704120f, -13.560730f, -13.534880f,
    -13.548950f, -13.562280f, -13.594080f, -13.620470f, -13.641980f, -13.661090f, -13.626690f, -13.582970f, -13.573870f, -13.473900f,
    -13.530630f, -13.483480f, -13.610470f, -13.647160f, -13.715460f, -13.791840f, -13.906140f, -14.030980f, -14.182050f, -14.358810f,
    -14.484190f, -14.601720f, -14.705910f, -14.833620f, -14.921220f, -15.006220f, -15.051220f, -15.031190f, -14.990280f, -14.923020f,
    -14.869270f, -14.826910f, -14.797200f, -14.769090f, -14.713560f, -14.612770f, -14.516960f, -14.422520f, -14.364050f, -14.304510f,
    -14.231610f, -14.198510f, -14.166330f, -14.156490f, -14.105040f, -13.995180f, -13.795620f, -13.399600f, -12.776700f, -11.712080f,
    -8.311879f, -8.600912f, -9.615928f, -10.435950f, -11.212920f, -11.883330f, -12.362430f, -12.637060f, -12.881800f, -12.830660f,
    -12.891030f, -12.956660f, -13.197630f, -13.405980f, -13.491130f, -13.554600f, -13.556390f, -13.519150f, -13.682840f, -13.532890f,
    -13.421070f, -13.655190f, -13.507130f, -13.752510f, -13.767150f, -13.874080f, -13.731090f, -13.704120f, -13.560730f, -13.534880f,
    -13.548950f, -13.562280f, -13.594080f, -13.620470f, -13.641980f, -13.661090f, -13.626690f, -13.582970f, -13.573870f, -13.473900f,
    -13.530630f, -13.483480f, -13.610470f, -13.647160f, -13.715460f, -13.791840f, -13.906140f, -14.030980f, -14.182050f, -14.358810f,
    -14.484190f, -14.601720f, -14.705910f, -14.833620f, -14.921220f, -15.006220f, -15.051220f, -15.031190f, -14.990280f, -14.923020f,
    -14.869270f, -14.826910f, -14.797200f, -14.769090f, -14.713560f, -14.612770f, -14.516960f, -14.422520f, -14.364050f, -14.304510f,
    -14.231610f, -14.198510f, -14.166330f, -14.156490f, -14.105040f, -13.995180f, -13.795620f, -13.399600f, -12.776700f, -11.712080f,
};

static const float CMVN_INV_STDDEV[560] = {
    0.155775f, 0.154484f, 0.152738f, 0.151872f, 0.150603f, 0.148926f, 0.147067f, 0.144706f, 0.143631f, 0.144357f,
    0.145185f, 0.145516f, 0.145282f, 0.144572f, 0.143920f, 0.143587f, 0.143602f, 0.143878f, 0.144209f, 0.144884f,
    0.145476f, 0.145663f, 0.146268f, 0.146739f, 0.147272f, 0.147664f, 0.148091f, 0.148374f, 0.148884f, 0.149364f,
    0.149709f, 0.150038f, 0.150292f, 0.150539f, 0.150679f, 0.150710f, 0.150599f, 0.150544f, 0.150594f, 0.150813f,
    0.150957f, 0.151240f, 0.151462f, 0.151619f, 0.151616f, 0.151556f, 0.151497f, 0.151398f, 0.151261f, 0.151076f,
    0.151060f, 0.151043f, 0.151077f, 0.151117f, 0.151192f, 0.151023f, 0.150805f, 0.150588f, 0.150349f, 0.150237f,
    0.150173f, 0.150076f, 0.150006f, 0.149978f, 0.150057f, 0.150266f, 0.150469f, 0.150533f, 0.150551f, 0.150533f,
    0.150427f, 0.150244f, 0.149967f, 0.149712f, 0.149466f, 0.149310f, 0.149368f, 0.149550f, 0.149974f, 0.150965f,
    0.155775f, 0.154484f, 0.152738f, 0.151872f, 0.150603f, 0.148926f, 0.147067f, 0.144706f, 0.143631f, 0.144357f,
    0.145185f, 0.145516f, 0.145282f, 0.144572f, 0.143920f, 0.143587f, 0.143602f, 0.143878f, 0.144209f, 0.144884f,
    0.145476f, 0.145663f, 0.146268f, 0.146739f, 0.147272f, 0.147664f, 0.148091f, 0.148374f, 0.148884f, 0.149364f,
    0.149709f, 0.150038f, 0.150292f, 0.150539f, 0.150679f, 0.150710f, 0.150599f, 0.150544f, 0.150594f, 0.150813f,
    0.150957f, 0.151240f, 0.151462f, 0.151619f, 0.151616f, 0.151556f, 0.151497f, 0.151398f, 0.151261f, 0.151076f,
    0.151060f, 0.151043f, 0.151077f, 0.151117f, 0.151192f, 0.151023f, 0.150805f, 0.150588f, 0.150349f, 0.150237f,
    0.150173f, 0.150076f, 0.150006f, 0.149978f, 0.150057f, 0.150266f, 0.150469f, 0.150533f, 0.150551f, 0.150533f,
    0.150427f, 0.150244f, 0.149967f, 0.149712f, 0.149466f, 0.149310f, 0.149368f, 0.149550f, 0.149974f, 0.150965f,
    0.155775f, 0.154484f, 0.152738f, 0.151872f, 0.150603f, 0.148926f, 0.147067f, 0.144706f, 0.143631f, 0.144357f,
    0.145185f, 0.145516f, 0.145282f, 0.144572f, 0.143920f, 0.143587f, 0.143602f, 0.143878f, 0.144209f, 0.144884f,
    0.145476f, 0.145663f, 0.146268f, 0.146739f, 0.147272f, 0.147664f, 0.148091f, 0.148374f, 0.148884f, 0.149364f,
    0.149709f, 0.150038f, 0.150292f, 0.150539f, 0.150679f, 0.150710f, 0.150599f, 0.150544f, 0.150594f, 0.150813f,
    0.150957f, 0.151240f, 0.151462f, 0.151619f, 0.151616f, 0.151556f, 0.151497f, 0.151398f, 0.151261f, 0.151076f,
    0.151060f, 0.151043f, 0.151077f, 0.151117f, 0.151192f, 0.151023f, 0.150805f, 0.150588f, 0.150349f, 0.150237f,
    0.150173f, 0.150076f, 0.150006f, 0.149978f, 0.150057f, 0.150266f, 0.150469f, 0.150533f, 0.150551f, 0.150533f,
    0.150427f, 0.150244f, 0.149967f, 0.149712f, 0.149466f, 0.149310f, 0.149368f, 0.149550f, 0.149974f, 0.150965f,
    0.155775f, 0.154484f, 0.152738f, 0.151872f, 0.150603f, 0.148926f, 0.147067f, 0.144706f, 0.143631f, 0.144357f,
    0.145185f, 0.145516f, 0.145282f, 0.144572f, 0.143920f, 0.143587f, 0.143602f, 0.143878f, 0.144209f, 0.144884f,
    0.145476f, 0.145663f, 0.146268f, 0.146739f, 0.147272f, 0.147664f, 0.148091f, 0.148374f, 0.148884f, 0.149364f,
    0.149709f, 0.150038f, 0.150292f, 0.150539f, 0.150679f, 0.150710f, 0.150599f, 0.150544f, 0.150594f, 0.150813f,
    0.150957f, 0.151240f, 0.151462f, 0.151619f, 0.151616f, 0.151556f, 0.151497f, 0.151398f, 0.151261f, 0.151076f,
    0.151060f, 0.151043f, 0.151077f, 0.151117f, 0.151192f, 0.151023f, 0.150805f, 0.150588f, 0.150349f, 0.150237f,
    0.150173f, 0.150076f, 0.150006f, 0.149978f, 0.150057f, 0.150266f, 0.150469f, 0.150533f, 0.150551f, 0.150533f,
    0.150427f, 0.150244f, 0.149967f, 0.149712f, 0.149466f, 0.149310f, 0.149368f, 0.149550f, 0.149974f, 0.150965f,
    0.155775f, 0.154484f, 0.152738f, 0.151872f, 0.150603f, 0.148926f, 0.147067f, 0.144706f, 0.143631f, 0.144357f,
    0.145185f, 0.145516f, 0.145282f, 0.144572f, 0.143920f, 0.143587f, 0.143602f, 0.143878f, 0.144209f, 0.144884f,
    0.145476f, 0.145663f, 0.146268f, 0.146739f, 0.147272f, 0.147664f, 0.148091f, 0.148374f, 0.148884f, 0.149364f,
    0.149709f, 0.150038f, 0.150292f, 0.150539f, 0.150679f, 0.150710f, 0.150599f, 0.150544f, 0.150594f, 0.150813f,
    0.150957f, 0.151240f, 0.151462f, 0.151619f, 0.151616f, 0.151556f, 0.151497f, 0.151398f, 0.151261f, 0.151076f,
    0.151060f, 0.151043f, 0.151077f, 0.151117f, 0.151192f, 0.151023f, 0.150805f, 0.150588f, 0.150349f, 0.150237f,
    0.150173f, 0.150076f, 0.150006f, 0.149978f, 0.150057f, 0.150266f, 0.150469f, 0.150533f, 0.150551f, 0.150533f,
    0.150427f, 0.150244f, 0.149967f, 0.149712f, 0.149466f, 0.149310f, 0.149368f, 0.149550f, 0.149974f, 0.150965f,
    0.155775f, 0.154484f, 0.152738f, 0.151872f, 0.150603f, 0.148926f, 0.147067f, 0.144706f, 0.143631f, 0.144357f,
    0.145185f, 0.145516f, 0.145282f, 0.144572f, 0.143920f, 0.143587f, 0.143602f, 0.143878f, 0.144209f, 0.144884f,
    0.145476f, 0.145663f, 0.146268f, 0.146739f, 0.147272f, 0.147664f, 0.148091f, 0.148374f, 0.148884f, 0.149364f,
    0.149709f, 0.150038f, 0.150292f, 0.150539f, 0.150679f, 0.150710f, 0.150599f, 0.150544f, 0.150594f, 0.150813f,
    0.150957f, 0.151240f, 0.151462f, 0.151619f, 0.151616f, 0.151556f, 0.151497f, 0.151398f, 0.151261f, 0.151076f,
    0.151060f, 0.151043f, 0.151077f, 0.151117f, 0.151192f, 0.151023f, 0.150805f, 0.150588f, 0.150349f, 0.150237f,
    0.150173f, 0.150076f, 0.150006f, 0.149978f, 0.150057f, 0.150266f, 0.150469f, 0.150533f, 0.150551f, 0.150533f,
    0.150427f, 0.150244f, 0.149967f, 0.149712f, 0.149466f, 0.149310f, 0.149368f, 0.149550f, 0.149974f, 0.150965f,
    0.155775f, 0.154484f, 0.152738f, 0.151872f, 0.150603f, 0.148926f, 0.147067f, 0.144706f, 0.143631f, 0.144357f,
    0.145185f, 0.145516f, 0.145282f, 0.144572f, 0.143920f, 0.143587f, 0.143602f, 0.143878f, 0.144209f, 0.144884f,
    0.145476f, 0.145663f, 0.146268f, 0.146739f, 0.147272f, 0.147664f, 0.148091f, 0.148374f, 0.148884f, 0.149364f,
    0.149709f, 0.150038f, 0.150292f, 0.150539f, 0.150679f, 0.150710f, 0.150599f, 0.150544f, 0.150594f, 0.150813f,
    0.150957f, 0.151240f, 0.151462f, 0.151619f, 0.151616f, 0.151556f, 0.151497f, 0.151398f, 0.151261f, 0.151076f,
    0.151060f, 0.151043f, 0.151077f, 0.151117f, 0.151192f, 0.151023f, 0.150805f, 0.150588f, 0.150349f, 0.150237f,
    0.150173f, 0.150076f, 0.150006f, 0.149978f, 0.150057f, 0.150266f, 0.150469f, 0.150533f, 0.150551f, 0.150533f,
    0.150427f, 0.150244f, 0.149967f, 0.149712f, 0.149466f, 0.149310f, 0.149368f, 0.149550f, 0.149974f, 0.150965f,
};

// ── Mel feature extraction (kaldi-style) ──
static std::vector<float> extractMelFeatures(const float* audio, int num_samples, int& num_frames) {
    const int frame_length = 400;   // 25ms @ 16kHz
    const int frame_shift = 160;    // 10ms
    const int n_fft = 512;
    const int n_mels = 80;
    const float preemph = 0.97f;

    num_frames = 1 + (num_samples - frame_length) / frame_shift;
    if (num_frames <= 0) num_frames = 1;

    // Povey window (kaldi default): pow(0.5 - 0.5*cos(2*pi*n/N), 0.85)
    // Note: Povey uses N (not N-1) in the denominator, unlike Hamming/Hanning
    std::vector<float> window(frame_length);
    for (int i = 0; i < frame_length; i++) {
        window[i] = powf(0.5f - 0.5f * cosf(2.0f * M_PI * i / frame_length), 0.85f);
    }

    auto mel_fb = buildMelFilterbank(n_fft, n_mels, 16000, 20.0f, 7600.0f);

    std::vector<float> features(num_frames * n_mels, 0.0f);
    std::vector<float> frame_buf(frame_length);
    std::vector<float> fft_real(n_fft, 0.0f);
    std::vector<float> fft_imag(n_fft, 0.0f);
    std::vector<float> power_spec(n_fft / 2 + 1);

    float last_sample = 0.0f;  // cached last sample of previous frame (for pre-emphasis)

    for (int f = 0; f < num_frames; f++) {
        // snip-edges=true: frame starts at f*frame_shift (no centering)
        int start = f * frame_shift;

        // Step 1: Extract raw samples
        for (int j = 0; j < frame_length; j++) {
            int idx = start + j;
            frame_buf[j] = (idx >= 0 && idx < num_samples) ? audio[idx] : 0.0f;
        }

        // Step 2: Remove DC offset (kaldi default: remove-dc-offset=true)
        float mean = 0.0f;
        for (int j = 0; j < frame_length; j++) mean += frame_buf[j];
        mean /= frame_length;
        for (int j = 0; j < frame_length; j++) frame_buf[j] -= mean;

        // Save last sample (before pre-emphasis) for next frame
        float last_raw = frame_buf[frame_length - 1];

        // Step 3: Pre-emphasis (kaldi: iterate backwards, use last_sample for window[0])
        for (int j = frame_length - 1; j > 0; j--) {
            frame_buf[j] -= preemph * frame_buf[j - 1];
        }
        frame_buf[0] -= preemph * last_sample;
        last_sample = last_raw;

        // Step 4: Apply Povey window
        std::fill(fft_real.begin(), fft_real.end(), 0.0f);
        std::fill(fft_imag.begin(), fft_imag.end(), 0.0f);
        for (int j = 0; j < frame_length; j++) {
            fft_real[j] = frame_buf[j] * window[j];
        }

        fft(fft_real, fft_imag);

        for (int k = 0; k < n_fft / 2 + 1; k++) {
            power_spec[k] = fft_real[k] * fft_real[k] + fft_imag[k] * fft_imag[k];
        }

        for (int m = 0; m < n_mels; m++) {
            float val = 0.0f;
            for (int k = 0; k < n_fft / 2 + 1; k++) {
                val += power_spec[k] * mel_fb[m][k];
            }
            features[f * n_mels + m] = logf(val + 1e-10f);
        }
    }

    return features;
}

// ── LFR stacking: 7-frame stack with shift=6, then CMVN normalization ──
// (T, 80) → (T_stacked, 560) with CMVN applied
static std::vector<float> stackFrames(const std::vector<float>& mel, int num_frames,
                                       int n_mels, int& num_stacked) {
    const int stack_size = 7;    // lfr_window_size (m)
    const int shift = 6;          // lfr_window_shift (n)
    const int output_dim = n_mels * stack_size;  // 560

    if (num_frames <= 0) {
        num_stacked = 0;
        return {};
    }

    // Number of stacked output frames: 1 + (num_frames - 7) / 6
    if (num_frames <= stack_size) {
        num_stacked = 1;
    } else {
        num_stacked = 1 + (num_frames - stack_size) / shift;
    }

    std::vector<float> stacked(num_stacked * output_dim, 0.0f);
    for (int t = 0; t < num_stacked; t++) {
        for (int s = 0; s < stack_size; s++) {
            int src_t = t * shift + s;
            if (src_t < 0) src_t = 0;
            if (src_t >= num_frames) src_t = num_frames - 1;
            memcpy(&stacked[t * output_dim + s * n_mels],
                   &mel[src_t * n_mels], n_mels * sizeof(float));
        }
        // Apply CMVN: normalized = (feature + neg_mean) * inv_stddev
        for (int d = 0; d < output_dim; d++) {
            stacked[t * output_dim + d] =
                (stacked[t * output_dim + d] + CMVN_NEG_MEAN[d]) * CMVN_INV_STDDEV[d];
        }
    }
    return stacked;
}

// ── Tokens & CTC decoding ──
static std::vector<std::string> g_tokens;

// Parse tokens from string content (bypasses native file I/O on scoped storage)
static bool loadTokensFromString(const std::string& content) {
    g_tokens.clear();
    std::istringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        if (line.empty()) continue;
        size_t sp = line.rfind(' ');
        if (sp != std::string::npos) {
            g_tokens.push_back(line.substr(0, sp));
        } else {
            g_tokens.push_back(line);
        }
    }
    LOGI("Loaded %zu tokens from string", g_tokens.size());
    return !g_tokens.empty();
}

static bool isSpecialToken(const std::string& token) {
    return token.size() >= 2 && token[0] == '<' && token[token.size() - 1] == '>';
}

static std::string ctcGreedyDecode(const float* logits, int T, int vocab_size) {
    std::string result;
    int prev_id = -1;
    for (int t = 0; t < T; t++) {
        const float* row = logits + t * vocab_size;
        int best_id = 0;
        float best_val = row[0];
        for (int v = 1; v < vocab_size; v++) {
            if (row[v] > best_val) {
                best_val = row[v];
                best_id = v;
            }
        }
        if (best_id != prev_id && best_id != 0) {
            if (best_id < (int)g_tokens.size()) {
                std::string token = g_tokens[best_id];
                if (isSpecialToken(token)) {
                    prev_id = best_id;
                    continue;
                }
                size_t pos = token.find('\xe2\x96\x81');
                if (pos != std::string::npos) {
                    token.replace(pos, 3, " ");
                }
                result += token;
            }
        }
        prev_id = best_id;
    }
    size_t start = result.find_first_not_of(' ');
    if (start > 0 && start != std::string::npos) result = result.substr(start);
    return result;
}

// ── ASR Engine handle ──
struct MnnAsrHandle {
    MNN::Express::Module* module = nullptr;
    std::shared_ptr<MNN::Express::Executor::RuntimeManager> rtMgr;
};

static MnnAsrHandle* g_handle = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_companion_chat_engine_MnnAsrJni_nativeInitFromBytes(
    JNIEnv* env, jclass, jbyteArray modelBytes, jstring tokensContent) {
    LOGI("MNN ASR init from bytes: model=%d bytes", (int)env->GetArrayLength(modelBytes));
    flog("[nativeInitFromBytes] model=%d bytes", (int)env->GetArrayLength(modelBytes));

    // Parse tokens from string content
    const char* tcontent = env->GetStringUTFChars(tokensContent, nullptr);
    bool tokensOk = loadTokensFromString(std::string(tcontent));
    env->ReleaseStringUTFChars(tokensContent, tcontent);
    if (!tokensOk) {
        LOGE("Failed to parse tokens from string");
        flog("[nativeInitFromBytes] FAILED: tokens parse error");
        return JNI_FALSE;
    }
    flog("[nativeInitFromBytes] tokens loaded: %zu", g_tokens.size());

    // Release previous handle if exists
    if (g_handle) {
        delete g_handle->module;
        delete g_handle;
        g_handle = nullptr;
    }

    // Get model bytes
    jsize modelLen = env->GetArrayLength(modelBytes);
    jbyte* modelData = env->GetByteArrayElements(modelBytes, nullptr);
    flog("[nativeInitFromBytes] modelData ptr=%p, len=%d", modelData, (int)modelLen);

    try {
        // Create ScheduleConfig for CPU backend
        MNN::ScheduleConfig scheduleConfig;
        scheduleConfig.type = MNN_FORWARD_CPU;
        scheduleConfig.numThread = 4;
        scheduleConfig.backupType = MNN_FORWARD_CPU;

        MNN::BackendConfig backendCfg;
        backendCfg.memory = MNN::BackendConfig::MemoryMode::Memory_High;
        backendCfg.power = MNN::BackendConfig::PowerMode::Power_High;
        backendCfg.precision = MNN::BackendConfig::PrecisionMode::Precision_Normal;
        scheduleConfig.backendConfig = &backendCfg;

        // Create RuntimeManager (PC Python creates one internally; we need one too)
        auto rtMgr = std::shared_ptr<MNN::Express::Executor::RuntimeManager>(
            MNN::Express::Executor::RuntimeManager::createRuntimeManager(scheduleConfig));
        flog("[nativeInitFromBytes] RuntimeManager created: %p", rtMgr.get());

        // Module config: try dynamic=false first (PC default), fall back to dynamic=true
        std::vector<std::string> inputs = {"x", "x_length", "language", "text_norm"};
        std::vector<std::string> outputs = {"logits"};

        MNN::Express::Module::Config cfg;
        cfg.dynamic = false;
        cfg.shapeMutable = true;
        cfg.backend = nullptr;

        flog("[nativeInitFromBytes] calling Module::load (dynamic=false) with RuntimeManager...");
        auto* mod = MNN::Express::Module::load(
            inputs, outputs,
            reinterpret_cast<const uint8_t*>(modelData), modelLen,
            rtMgr, &cfg);
        flog("[nativeInitFromBytes] Module::load (dynamic=false) returned %p", mod);

        if (mod == nullptr) {
            flog("[nativeInitFromBytes] dynamic=false failed, trying dynamic=true...");
            cfg.dynamic = true;
            mod = MNN::Express::Module::load(
                inputs, outputs,
                reinterpret_cast<const uint8_t*>(modelData), modelLen,
                rtMgr, &cfg);
            flog("[nativeInitFromBytes] Module::load (dynamic=true) returned %p", mod);
        }
        env->ReleaseByteArrayElements(modelBytes, modelData, JNI_ABORT);

        if (mod == nullptr) {
            LOGE("Module::load from buffer returned null");
            flog("[nativeInitFromBytes] FAILED: Module::load returned null");
            return JNI_FALSE;
        }

        // Log model info
        auto* modInfo = mod->getInfo();
        if (modInfo) {
            flog("[nativeInitFromBytes] model version=%s, bizCode=%s, defaultFormat=%d",
                 modInfo->version.c_str(), modInfo->bizCode.c_str(), (int)modInfo->defaultFormat);
            flog("[nativeInitFromBytes] inputNames=%zu, outputNames=%zu",
                 modInfo->inputNames.size(), modInfo->outputNames.size());
            for (size_t i = 0; i < modInfo->inputs.size(); i++) {
                auto& inp = modInfo->inputs[i];
                std::string dims;
                for (size_t d = 0; d < inp.dim.size(); d++) {
                    dims += std::to_string(inp.dim[d]);
                    if (d < inp.dim.size() - 1) dims += "x";
                }
                flog("[nativeInitFromBytes] model input[%zu]: dims=[%s], type.code=%d, type.bits=%d, order=%d",
                     i, dims.c_str(), (int)inp.type.code, (int)inp.type.bits, (int)inp.order);
            }
        } else {
            flog("[nativeInitFromBytes] WARNING: getInfo returned null");
        }

        g_handle = new MnnAsrHandle{mod, rtMgr};
        LOGI("MNN ASR module loaded successfully from buffer");
        flog("[nativeInitFromBytes] SUCCESS: module loaded");
    } catch (const std::exception& e) {
        LOGE("MNN ASR module load exception: %s", e.what());
        flog("[nativeInitFromBytes] EXCEPTION: %s", e.what());
        env->ReleaseByteArrayElements(modelBytes, modelData, JNI_ABORT);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_companion_chat_engine_MnnAsrJni_nativeTranscribe(
    JNIEnv* env, jclass, jfloatArray audioData, jint sampleRate) {
    if (g_handle == nullptr || g_handle->module == nullptr) {
        flog("[nativeTranscribe] FAILED: g_handle=%p, module=null", g_handle);
        return env->NewStringUTF("");
    }

    jsize num_samples = env->GetArrayLength(audioData);
    jfloat* audio = env->GetFloatArrayElements(audioData, nullptr);

    LOGI("Transcribe: %d samples, sr=%d", num_samples, sampleRate);
    flog("[nativeTranscribe] samples=%d, sr=%d", num_samples, sampleRate);

    // Resample to 16kHz if needed, and scale to int16 range [-32768, 32767]
    // (SenseVoice normalize_samples=0 expects int16-range audio, NOT [-1,1])
    std::vector<float> audio_16k;
    if (sampleRate != 16000) {
        double ratio = 16000.0 / sampleRate;
        int new_len = (int)(num_samples * ratio);
        audio_16k.resize(new_len);
        for (int i = 0; i < new_len; i++) {
            double src_idx = i / ratio;
            int lo = (int)src_idx;
            int hi = (lo + 1 < num_samples) ? lo + 1 : lo;
            float frac = (float)(src_idx - lo);
            audio_16k[i] = (audio[lo] * (1.0f - frac) + audio[hi] * frac) * 32767.0f;
        }
    } else {
        audio_16k.resize(num_samples);
        for (int i = 0; i < num_samples; i++) {
            audio_16k[i] = audio[i] * 32767.0f;
        }
    }

    // Mel feature extraction
    int num_frames = 0;
    std::vector<float> mel = extractMelFeatures(audio_16k.data(), audio_16k.size(), num_frames);
    LOGI("Mel features: %d frames x 80", num_frames);
    flog("[nativeTranscribe] mel features: %d frames x 80", num_frames);

    // Log audio statistics for debugging
    {
        float amin = audio_16k[0], amax = audio_16k[0], asum = 0;
        for (int i = 0; i < (int)audio_16k.size(); i++) {
            if (audio_16k[i] < amin) amin = audio_16k[i];
            if (audio_16k[i] > amax) amax = audio_16k[i];
            asum += audio_16k[i];
        }
        flog("[nativeTranscribe] audio stats: min=%.1f max=%.1f mean=%.1f samples=%d",
             amin, amax, asum / audio_16k.size(), (int)audio_16k.size());
    }
    // Log first mel frame values
    if (num_frames > 0) {
        flog("[nativeTranscribe] mel[0]: %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f",
             mel[0], mel[1], mel[2], mel[3], mel[4], mel[5], mel[6], mel[7], mel[8], mel[9]);
    }

    // 7-frame stacking
    int num_stacked = 0;
    std::vector<float> stacked = stackFrames(mel, num_frames, 80, num_stacked);
    LOGI("Stacked: %d frames x 560", num_stacked);
    flog("[nativeTranscribe] stacked: %d frames x 560", num_stacked);

    // Log first stacked+CMVN frame values
    if (num_stacked > 0) {
        flog("[nativeTranscribe] stacked_cmvn[0]: %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f",
             stacked[0], stacked[1], stacked[2], stacked[3], stacked[4],
             stacked[5], stacked[6], stacked[7], stacked[8], stacked[9]);
    }

    int T = num_stacked;
    int input_dim = 560;

    // Create MNN input tensors using _Const (matches PC Python MNN.expr.const)
    std::vector<MNN::Express::VARP> inputs;

    // x: [1, T, 560] float
    {
        auto var = MNN::Express::_Const(stacked.data(), {1, T, input_dim}, MNN::Express::NCHW, halide_type_of<float>());
        inputs.push_back(var);
    }

    // x_length: [1] int32 (model expects type.code=0, bits=32)
    {
        int32_t x_len = (int32_t)T;
        auto var = MNN::Express::_Const(&x_len, {1}, MNN::Express::NCHW, halide_type_of<int32_t>());
        inputs.push_back(var);
    }

    // language: [1] int32 (0=auto)
    {
        int32_t lang = 0;
        auto var = MNN::Express::_Const(&lang, {1}, MNN::Express::NCHW, halide_type_of<int32_t>());
        inputs.push_back(var);
    }

    // text_norm: [1] int32 (14=withitn)
    {
        int32_t norm = 14;
        auto var = MNN::Express::_Const(&norm, {1}, MNN::Express::NCHW, halide_type_of<int32_t>());
        inputs.push_back(var);
    }

    // Run inference
    LOGI("Before onForward: T=%d, input_dim=%d", T, input_dim);
    flog("[nativeTranscribe] before onForward: T=%d", T);
    // Log input shapes for debugging
    for (size_t i = 0; i < inputs.size(); i++) {
        auto info = inputs[i]->getInfo();
        if (info) {
            std::string dims;
            for (size_t d = 0; d < info->dim.size(); d++) {
                dims += std::to_string(info->dim[d]);
                if (d < info->dim.size() - 1) dims += "x";
            }
            flog("[nativeTranscribe] input[%zu]: dims=[%s], type=%d", i, dims.c_str(), (int)info->type.code);
        } else {
            flog("[nativeTranscribe] input[%zu]: getInfo returned null", i);
        }
    }
    auto outputs = g_handle->module->onForward(inputs);
    LOGI("After onForward: outputs.size=%zu", outputs.size());
    flog("[nativeTranscribe] after onForward: outputs.size=%zu", outputs.size());
    if (outputs.empty()) {
        LOGE("MNN ASR inference returned empty output");
        flog("[nativeTranscribe] FAILED: empty output");
        env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);
        return env->NewStringUTF("");
    }

    auto logits_var = outputs[0];
    // Try getInfo first to see if the output VARP is valid
    auto preInfo = logits_var->getInfo();
    flog("[nativeTranscribe] pre-readMap getInfo=%p", preInfo);
    if (preInfo) {
        std::string dims;
        for (size_t d = 0; d < preInfo->dim.size(); d++) {
            dims += std::to_string(preInfo->dim[d]);
            if (d < preInfo->dim.size() - 1) dims += "x";
        }
        flog("[nativeTranscribe] output dims=[%s], type.code=%d, type.bits=%d before readMap",
             dims.c_str(), (int)preInfo->type.code, (int)preInfo->type.bits);
    }
    // readMap forces computation in Module API
    flog("[nativeTranscribe] calling readMap...");
    const float* logits = logits_var->readMap<float>();
    flog("[nativeTranscribe] readMap returned %p", logits);
    if (logits == nullptr) {
        LOGE("readMap returned null - computation failed");
        // Get ErrorCode from the expression
        auto exprPair = logits_var->expr();
        if (exprPair.first) {
            auto errCode = MNN::Express::Executor::getGlobalExecutor()->computeInfo(exprPair.first.get());
            flog("[nativeTranscribe] computeInfo ErrorCode=%d (0=OK, 1=OOM, 2=NOT_SUPPORT, 3=COMPUTE_SIZE, 4=NO_EXECUTION, 5=INVALID_VALUE)",
                 (int)errCode);
        }
        // Check Tensor host pointer
        auto* tensor = logits_var->getTensor();
        if (tensor) {
            flog("[nativeTranscribe] tensor=%p, host=%p, buffer.host=%p",
                 tensor, tensor->host<float>(), tensor->buffer().host);
        }
        flog("[nativeTranscribe] FAILED: readMap returned null");
        env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);
        return env->NewStringUTF("");
    }
    auto info = logits_var->getInfo();
    flog("[nativeTranscribe] getInfo returned %p", info);
    if (info == nullptr) {
        LOGE("Failed to get output info after readMap");
        flog("[nativeTranscribe] FAILED: getInfo returned null");
        env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);
        return env->NewStringUTF("");
    }

    int out_T = info->dim.size() >= 2 ? info->dim[1] : T;
    int vocab_size = info->dim.size() >= 3 ? info->dim[2] : (int)g_tokens.size();
    LOGI("Output: T=%d, vocab=%d, ndim=%zu", out_T, vocab_size, info->dim.size());
    flog("[nativeTranscribe] output: T=%d, vocab=%d, ndim=%zu", out_T, vocab_size, info->dim.size());

    // Log first few logits values for debugging
    if (out_T > 0 && vocab_size > 0) {
        flog("[nativeTranscribe] first 5 logits[0]: %f %f %f %f %f",
             logits[0], logits[1 % vocab_size], logits[2 % vocab_size],
             logits[3 % vocab_size], logits[4 % vocab_size]);

        // Find top-5 predictions for first 5 output frames (including 4 special token positions)
        for (int t = 0; t < 5 && t < out_T; t++) {
            const float* row = logits + t * vocab_size;
            // Simple top-5 selection
            int top_id[5] = {0, 0, 0, 0, 0};
            float top_val[5] = {-1e30f, -1e30f, -1e30f, -1e30f, -1e30f};
            for (int v = 0; v < vocab_size; v++) {
                float val = row[v];
                for (int k = 4; k >= 0; k--) {
                    if (val > top_val[k]) {
                        if (k < 4) {
                            top_val[k + 1] = top_val[k];
                            top_id[k + 1] = top_id[k];
                        }
                        top_val[k] = val;
                        top_id[k] = v;
                        break;
                    }
                }
            }
            flog("[nativeTranscribe] top5[%d]: %d(%.2f,'%s') %d(%.2f,'%s') %d(%.2f,'%s') %d(%.2f,'%s') %d(%.2f,'%s')",
                 t,
                 top_id[0], top_val[0], top_id[0] < (int)g_tokens.size() ? g_tokens[top_id[0]].c_str() : "?",
                 top_id[1], top_val[1], top_id[1] < (int)g_tokens.size() ? g_tokens[top_id[1]].c_str() : "?",
                 top_id[2], top_val[2], top_id[2] < (int)g_tokens.size() ? g_tokens[top_id[2]].c_str() : "?",
                 top_id[3], top_val[3], top_id[3] < (int)g_tokens.size() ? g_tokens[top_id[3]].c_str() : "?",
                 top_id[4], top_val[4], top_id[4] < (int)g_tokens.size() ? g_tokens[top_id[4]].c_str() : "?");
        }
    }

    // CTC greedy decode
    std::string text = ctcGreedyDecode(logits, out_T, vocab_size);
    LOGI("Decoded: %s", text.c_str());
    flog("[nativeTranscribe] decoded: '%s'", text.c_str());

    logits_var->unMap();
    env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_companion_chat_engine_MnnAsrJni_nativeRelease(JNIEnv*, jclass) {
    if (g_handle) {
        delete g_handle->module;
        delete g_handle;
        g_handle = nullptr;
    }
    g_tokens.clear();
    LOGI("MNN ASR released");
}

}  // extern "C"
