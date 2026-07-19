cmake_minimum_required(VERSION 3.22.1)

project(companion_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(LLAMA_CPP_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")
set(STABLE_DIFFUSION_CPP_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/stable-diffusion.cpp")
set(VULKAN_HEADERS_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/Vulkan-Headers")
set(SPIRV_HEADERS_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/SPIRV-Headers")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build llama.cpp as static libraries" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "Skip llama.cpp common tools" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Skip llama.cpp tests" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "Skip llama.cpp tools" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Skip llama.cpp examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Skip llama.cpp server" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "Disable host-native flags for Android cross builds" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP" FORCE)
set(GGML_BLAS OFF CACHE BOOL "Disable BLAS" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "Disable llamafile" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan backend for Android CPU-only builds" FORCE)
if (CMAKE_MAKE_PROGRAM)
    get_filename_component(CMAKE_MAKE_PROGRAM_DIR "${CMAKE_MAKE_PROGRAM}" DIRECTORY)
    set(ENV{PATH} "${CMAKE_MAKE_PROGRAM_DIR}:$ENV{PATH}")
endif()
if (ANDROID AND CMAKE_MAKE_PROGRAM)
    find_program(COMPANION_HOST_C_COMPILER NAMES cc gcc clang)
    find_program(COMPANION_HOST_CXX_COMPILER NAMES c++ g++ clang++)
    if (COMPANION_HOST_C_COMPILER AND COMPANION_HOST_CXX_COMPILER)
        set(COMPANION_VULKAN_HOST_TOOLCHAIN "${CMAKE_CURRENT_BINARY_DIR}/companion-vulkan-shaders-host-toolchain.cmake")
        file(WRITE "${COMPANION_VULKAN_HOST_TOOLCHAIN}"
            "set(CMAKE_BUILD_TYPE Release)\n"
            "set(CMAKE_C_FLAGS -O2)\n"
            "set(CMAKE_CXX_FLAGS -O2)\n"
            "set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)\n"
            "set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY NEVER)\n"
            "set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE NEVER)\n"
            "set(CMAKE_C_COMPILER \"${COMPANION_HOST_C_COMPILER}\")\n"
            "set(CMAKE_CXX_COMPILER \"${COMPANION_HOST_CXX_COMPILER}\")\n"
            "set(CMAKE_MAKE_PROGRAM \"${CMAKE_MAKE_PROGRAM}\" CACHE FILEPATH \"\" FORCE)\n"
        )
        set(GGML_VULKAN_SHADERS_GEN_TOOLCHAIN "${COMPANION_VULKAN_HOST_TOOLCHAIN}" CACHE FILEPATH "Host toolchain for Vulkan shader generator" FORCE)
    endif()
endif()
if (ANDROID)
    if (EXISTS "${VULKAN_HEADERS_DIR}/include/vulkan/vulkan.hpp")
        include_directories("${VULKAN_HEADERS_DIR}/include")
    elseif (DEFINED ANDROID_NDK)
        include_directories("${ANDROID_NDK}/sources/third_party/vulkan/src/include")
    endif()
    if (EXISTS "${SPIRV_HEADERS_DIR}/include/spirv/unified1/spirv.hpp")
        include_directories("${SPIRV_HEADERS_DIR}/include")
    elseif (DEFINED ANDROID_NDK)
        include_directories("${ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers/include")
    endif()
endif()

add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")
if (NOT DEFINED LLAMA_INSTALL_VERSION)
    set(LLAMA_INSTALL_VERSION "0.0.0")
endif()

find_package(Threads REQUIRED)

set(MTMD_DIR "${LLAMA_CPP_DIR}/tools/mtmd")
add_library(mtmd STATIC
    "${MTMD_DIR}/mtmd.cpp"
    "${MTMD_DIR}/mtmd-audio.cpp"
    "${MTMD_DIR}/mtmd-image.cpp"
    "${MTMD_DIR}/mtmd-helper.cpp"
    "${MTMD_DIR}/clip.cpp"
    "${MTMD_DIR}/models/cogvlm.cpp"
    "${MTMD_DIR}/models/conformer.cpp"
    "${MTMD_DIR}/models/dotsocr.cpp"
    "${MTMD_DIR}/models/gemma4a.cpp"
    "${MTMD_DIR}/models/gemma4v.cpp"
    "${MTMD_DIR}/models/glm4v.cpp"
    "${MTMD_DIR}/models/granite-speech.cpp"
    "${MTMD_DIR}/models/hunyuanocr.cpp"
    "${MTMD_DIR}/models/internvl.cpp"
    "${MTMD_DIR}/models/kimivl.cpp"
    "${MTMD_DIR}/models/kimik25.cpp"
    "${MTMD_DIR}/models/nemotron-v2-vl.cpp"
    "${MTMD_DIR}/models/llama4.cpp"
    "${MTMD_DIR}/models/llava.cpp"
    "${MTMD_DIR}/models/minicpmv.cpp"
    "${MTMD_DIR}/models/paddleocr.cpp"
    "${MTMD_DIR}/models/pixtral.cpp"
    "${MTMD_DIR}/models/qwen2vl.cpp"
    "${MTMD_DIR}/models/qwen3vl.cpp"
    "${MTMD_DIR}/models/qwen3a.cpp"
    "${MTMD_DIR}/models/step3vl.cpp"
    "${MTMD_DIR}/models/siglip.cpp"
    "${MTMD_DIR}/models/whisper-enc.cpp"
    "${MTMD_DIR}/models/deepseekocr.cpp"
    "${MTMD_DIR}/models/mobilenetv5.cpp"
    "${MTMD_DIR}/models/youtuvl.cpp"
    "${MTMD_DIR}/models/yasa2.cpp"
)

target_include_directories(mtmd PUBLIC "${MTMD_DIR}")
target_include_directories(mtmd PRIVATE "${LLAMA_CPP_DIR}" "${LLAMA_CPP_DIR}/vendor")
target_link_libraries(mtmd PUBLIC ggml llama)
target_link_libraries(mtmd PRIVATE Threads::Threads)
target_compile_features(mtmd PRIVATE cxx_std_17)

if (NOT MSVC)
    target_compile_options(mtmd PRIVATE -Wno-cast-qual)
endif()

if (ANDROID)
    target_compile_options(mtmd PRIVATE -Wno-missing-prototypes)
endif()

add_library(companion_llama SHARED llama_jni.cpp)

target_include_directories(companion_llama PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/tools/mtmd"
)

target_link_libraries(companion_llama
    llama
    mtmd
    android
    log
)

if (EXISTS "${STABLE_DIFFUSION_CPP_DIR}/CMakeLists.txt")
    set(SD_BUILD_EXAMPLES OFF CACHE BOOL "Skip stable-diffusion.cpp examples" FORCE)
    set(SD_WEBP OFF CACHE BOOL "Disable stable-diffusion.cpp WebP support" FORCE)
    set(SD_WEBM OFF CACHE BOOL "Disable stable-diffusion.cpp WebM support" FORCE)
    set(SD_VULKAN OFF CACHE BOOL "Disable stable-diffusion.cpp Vulkan backend for Android CPU-only builds" FORCE)
    set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "Build stable-diffusion.cpp as static library" FORCE)
    set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "Build stable-diffusion.cpp ggml as static library" FORCE)
    set(SD_USE_SYSTEM_GGML OFF CACHE BOOL "Use bundled stable-diffusion.cpp ggml" FORCE)
    add_subdirectory("${STABLE_DIFFUSION_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/stable-diffusion.cpp")

    add_library(companion_sd SHARED stable_diffusion_jni.cpp)
    target_include_directories(companion_sd PRIVATE
        "${STABLE_DIFFUSION_CPP_DIR}/include"
        "${STABLE_DIFFUSION_CPP_DIR}/thirdparty"
    )
    target_link_libraries(companion_sd
        stable-diffusion
        android
        log
    )
else()
    message(FATAL_ERROR "stable-diffusion.cpp is missing at ${STABLE_DIFFUSION_CPP_DIR}")
endif()

# --- Shared path definitions (must come before any usage) ---
set(THIRD_PARTY_UI_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../..")
set(ONNXRUNTIME_ANDROID_HEADERS "${CMAKE_CURRENT_LIST_DIR}/../../../../../sdk/onnxruntime-android/headers")

# --- MOSS TTS SentencePiece Tokenizer JNI ---
# sentencepiece 在项目根目录的 third_party/ 下 (4 层 ../)
set(SENTENCEPIECE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/sentencepiece")

if (EXISTS "${SENTENCEPIECE_DIR}/CMakeLists.txt")
    # Build sentencepiece from source
    set(SPM_ENABLE_SHARED OFF CACHE BOOL "Disable shared libs" FORCE)
    set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "Disable tcmalloc" FORCE)
    set(SPM_BUILD_TEST OFF CACHE BOOL "Disable tests" FORCE)
    set(SPM_BUILD_EXAMPLES OFF CACHE BOOL "Disable examples" FORCE)
    add_subdirectory("${SENTENCEPIECE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/sentencepiece")

    add_library(moss_tts_tokenizer SHARED moss_tts_tokenizer.cpp)
    target_compile_definitions(moss_tts_tokenizer PRIVATE HAS_SENTENCEPIECE=1)
    target_include_directories(moss_tts_tokenizer PRIVATE
        "${SENTENCEPIECE_DIR}/src"
    )
    target_link_libraries(moss_tts_tokenizer
        sentencepiece-static
        android
        log
    )
    message(STATUS "MOSS TTS Tokenizer: sentencepiece found, building full JNI")
else()
    # Build stub without sentencepiece (character-level fallback)
    add_library(moss_tts_tokenizer SHARED moss_tts_tokenizer.cpp)
    target_link_libraries(moss_tts_tokenizer
        android
        log
    )
    message(WARNING "MOSS TTS Tokenizer: sentencepiece not found at ${SENTENCEPIECE_DIR}, building stub. Add sentencepiece source to third_party/sentencepiece/ for full support.")
endif()

# --- DreamLite ONNX Runtime JNI ---

if (EXISTS "${ONNXRUNTIME_ANDROID_HEADERS}/onnxruntime_cxx_api.h")
    # Locate libonnxruntime.so (from AAR extraction or local copy)
    set(ONNXRUNTIME_ANDROID_LIB_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../../sdk/onnxruntime-android/jni/${ANDROID_ABI}")

    find_library(ONNXRUNTIME_LIB onnxruntime
        PATHS "${ONNXRUNTIME_ANDROID_LIB_DIR}"
        NO_DEFAULT_PATH
    )

    if (NOT ONNXRUNTIME_LIB AND EXISTS "${ONNXRUNTIME_ANDROID_LIB_DIR}/libonnxruntime.so")
        add_library(onnxruntime SHARED IMPORTED)
        set_target_properties(onnxruntime PROPERTIES
            IMPORTED_LOCATION "${ONNXRUNTIME_ANDROID_LIB_DIR}/libonnxruntime.so")
        set(ONNXRUNTIME_LIB onnxruntime)
    endif()

    if (ONNXRUNTIME_LIB)
        add_library(companion_dreamlite SHARED
            dreamlite_jni.cpp
            pipeline.cpp
            tokenizer.cpp
        )
        target_include_directories(companion_dreamlite PRIVATE
            "${ONNXRUNTIME_ANDROID_HEADERS}"
            "${THIRD_PARTY_UI_DIR}/third_party"
            "${THIRD_PARTY_UI_DIR}/third_party/stable-diffusion.cpp/thirdparty"
            "${CMAKE_CURRENT_SOURCE_DIR}"
        )
        target_link_libraries(companion_dreamlite
            ${ONNXRUNTIME_LIB}
            android
            log
        )
        message(STATUS "DreamLite JNI: ONNX Runtime found, building libcompanion_dreamlite.so")

        # --- DreamLite standalone worker executable ---
        # Runs ONE image generation in a fresh process and writes the PNG to a
        # file path. The parent (LocalImageGenerationEngine) spawns this via
        # ProcessBuilder for each generation; when the worker process exits,
        # the OS reclaims ALL memory (including ORT scudo virtual mappings
        # that mallopt cannot release). This guarantees a clean memory state
        # for every generation, enabling unlimited consecutive generations
        # with 4 ORT threads.
        #
        # Named libdreamlite_worker.so so AGP packages it in lib/<abi>/ and
        # (with useLegacyPackaging=true) extracts it to nativeLibraryDir where
        # ProcessBuilder can exec it. The .so suffix is cosmetic — this is a
        # PIE executable, not a shared library.
        add_executable(dreamlite_worker_bin
            dreamlite_worker.cpp
            pipeline.cpp
            tokenizer.cpp
        )
        set_target_properties(dreamlite_worker_bin PROPERTIES
            OUTPUT_NAME "dreamlite_worker"
            PREFIX "lib"
            SUFFIX ".so"
        )
        target_include_directories(dreamlite_worker_bin PRIVATE
            "${ONNXRUNTIME_ANDROID_HEADERS}"
            "${THIRD_PARTY_UI_DIR}/third_party"
            "${THIRD_PARTY_UI_DIR}/third_party/stable-diffusion.cpp/thirdparty"
            "${CMAKE_CURRENT_SOURCE_DIR}"
        )
        target_link_libraries(dreamlite_worker_bin
            ${ONNXRUNTIME_LIB}
            android
            log
        )
        message(STATUS "DreamLite Worker: building libdreamlite_worker.so (standalone executable)")
    else()
        message(WARNING "DreamLite JNI: libonnxruntime.so not found, skipping companion_dreamlite")
    endif()
else()
    message(WARNING "DreamLite JNI: ONNX Runtime headers not found at ${ONNXRUNTIME_ANDROID_HEADERS}, skipping companion_dreamlite")
endif()

# ── MNN JNI (MOSS TTS inference) ──
# Only build for arm64-v8a (our target); libMNN.so is not available for other ABIs
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(MNN_INCLUDE_DIR "C:/Users/72952/OneDrive/Desktop/ui/reference/MNN/include")
    set(MNN_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

    add_library(MNN_SHARED SHARED IMPORTED)
    set_target_properties(MNN_SHARED PROPERTIES
        IMPORTED_LOCATION "${MNN_LIB_DIR}/libMNN.so")

    add_library(mnn_jni SHARED
        mnn_jni.cpp
    )
    target_include_directories(mnn_jni PRIVATE
        "${MNN_INCLUDE_DIR}"
        "${MNN_INCLUDE_DIR}/MNN"
        "${MNN_INCLUDE_DIR}/expr"
    )
    target_link_libraries(mnn_jni
        MNN_SHARED
        android
        log
    )
    # libmnn_jni.so 将被 gradle 自动打包；旧预编译版在后，新编译版在前
    message(STATUS "MNN JNI: compiling mnn_jni.cpp from source, linked against libMNN.so")

    # ── MNN LLM JNI (LLM 推理 + 多模态视觉) ──
    set(MNN_LLM_INCLUDE_DIR "C:/Users/72952/OneDrive/Desktop/ui/reference/MNN/transformers/llm/engine/include")
    set(MNN_CV_INCLUDE_DIR "C:/Users/72952/OneDrive/Desktop/ui/reference/MNN/tools/cv/include")

    add_library(mnn_llm_jni SHARED
        mnn_llm_jni.cpp
    )
    target_include_directories(mnn_llm_jni PRIVATE
        "${MNN_INCLUDE_DIR}"
        "${MNN_INCLUDE_DIR}/MNN"
        "${MNN_INCLUDE_DIR}/expr"
        "${MNN_LLM_INCLUDE_DIR}"
        "${MNN_CV_INCLUDE_DIR}"
    )
    target_link_libraries(mnn_llm_jni
        MNN_SHARED
        android
        log
    )
    message(STATUS "MNN LLM JNI: compiling mnn_llm_jni.cpp, linked against libMNN.so (LLM + vision)")

    # ── MNN ASR JNI (SenseVoice 语音识别) ──
    add_library(mnn_asr_jni SHARED
        mnn_asr_jni.cpp
    )
    target_include_directories(mnn_asr_jni PRIVATE
        "${MNN_INCLUDE_DIR}"
        "${MNN_INCLUDE_DIR}/MNN"
        "${MNN_INCLUDE_DIR}/expr"
    )
    target_link_libraries(mnn_asr_jni
        MNN_SHARED
        android
        log
    )
    message(STATUS "MNN ASR JNI: compiling mnn_asr_jni.cpp for SenseVoice ASR")
endif()
