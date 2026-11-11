#include <dlfcn.h>
#include <jni.h>

#include <array>
#include <cstdint>
#include <cstring>
#include <memory>
#include <random>
#include <stdexcept>
#include <string>
#include <vector>

// Use the existing Sherpa dependency. The Java JNI from another ORT patch
// release links versioned ELF symbols and cannot share this runtime safely.
#define ORT_API_MANUAL_INIT
#include "ort27/onnxruntime_cxx_api.h"

namespace {
constexpr int kMaxTokens = 512;
constexpr int kMaxFrames = 600;
constexpr int kSamplesPerFrame = 600;
constexpr int kHarmonics = 9;

const char* InitializeOrt() {
  static const char* version = [] {
    void* library = dlopen("libonnxruntime.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) throw std::runtime_error("The packaged ONNX Runtime is unavailable");
    try {
      using GetApiBase = const OrtApiBase*(ORT_API_CALL*)();
      auto get_api_base = reinterpret_cast<GetApiBase>(dlsym(library, "OrtGetApiBase"));
      if (get_api_base == nullptr) throw std::runtime_error("ONNX Runtime C API is unavailable");
      const OrtApiBase* base = get_api_base();
      if (base == nullptr || std::strcmp(base->GetVersionString(), "1.27.1") != 0) {
        throw std::runtime_error("Kokoro requires the packaged ONNX Runtime 1.27.1");
      }
      const OrtApi* api = base->GetApi(ORT_API_VERSION);
      if (api == nullptr) throw std::runtime_error("ONNX Runtime API 27 is unavailable");
      Ort::InitApi(api);
      // Keep the shared library loaded while the C++ API table is in use.
      return base->GetVersionString();
    } catch (...) {
      dlclose(library);
      throw;
    }
  }();
  return version;
}

class FrameLimitError : public std::runtime_error {
 public:
  FrameLimitError() : std::runtime_error("Kokoro predicted duration exceeds its frame limit") {}
};

template <typename Result, typename Work>
Result JniCall(JNIEnv* env, Result failure, Work work) {
  try {
    return work();
  } catch (const FrameLimitError& e) {
    if (!env->ExceptionCheck()) {
      env->ThrowNew(env->FindClass("io/github/ninbyo02/lami/tts/LamiKokoroScratchFrameLimitException"), e.what());
    }
    return failure;
  } catch (const std::exception& e) {
    if (!env->ExceptionCheck()) {
      env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
    }
    return failure;
  }
}

std::string PathFromUtf8(JNIEnv* env, jbyteArray bytes) {
  std::string path(env->GetArrayLength(bytes), '\0');
  env->GetByteArrayRegion(bytes, 0, path.size(), reinterpret_cast<jbyte*>(path.data()));
  if (env->ExceptionCheck() || path.empty() || path.find('\0') != std::string::npos) {
    throw std::runtime_error("Invalid Kokoro model path");
  }
  return path;
}

template <typename T>
T& Handle(jlong handle) {
  if (handle == 0) throw std::runtime_error("Kokoro native session is closed");
  return *reinterpret_cast<T*>(static_cast<intptr_t>(handle));
}

void RequireTensor(const Ort::Value& value, ONNXTensorElementDataType type,
                   const std::vector<int64_t>& shape, const char* name) {
  auto info = value.GetTensorTypeAndShapeInfo();
  if (info.GetElementType() != type || info.GetShape() != shape) {
    throw std::runtime_error(std::string("Unexpected Kokoro tensor: ") + name);
  }
}

struct ScratchSession {
  Ort::Env env{ORT_LOGGING_LEVEL_WARNING, "LamiKokoroScratch"};
  Ort::Session text{nullptr};
  Ort::Session render{nullptr};
  const int max_frames;

  ScratchSession(const std::string& text_path, const std::string& render_path, int frames)
      : max_frames(frames) {
    if (max_frames < 1 || max_frames > kMaxFrames) {
      throw std::runtime_error("Invalid Kokoro frame limit");
    }
    Ort::SessionOptions options;
    options.SetIntraOpNumThreads(4);
    options.SetInterOpNumThreads(1);
    text = Ort::Session(env, text_path.c_str(), options);
    options.DisableCpuMemArena();
    options.DisableMemPattern();
    render = Ort::Session(env, render_path.c_str(), options);
    if (text.GetInputNames() != std::vector<std::string>{"ids"} ||
        text.GetOutputNames() != std::vector<std::string>{"durations", "prosody", "acoustic"} ||
        render.GetInputNames() != std::vector<std::string>{
            "prosody", "acoustic", "frame_tokens", "phase_noise", "gaussian_noise"} ||
        render.GetOutputNames() != std::vector<std::string>{"pcm"}) {
      throw std::runtime_error("Unexpected Kokoro graph signatures");
    }
  }

  jfloatArray Synthesize(JNIEnv* jni, jlongArray input, const Ort::RunOptions& run) {
    const int64_t token_count = jni->GetArrayLength(input);
    if (token_count < 3 || token_count > kMaxTokens) {
      throw std::runtime_error("Kokoro phoneme input exceeds its token limit");
    }
    std::vector<jlong> java_ids(token_count);
    jni->GetLongArrayRegion(input, 0, token_count, java_ids.data());
    if (jni->ExceptionCheck()) return nullptr;
    std::vector<int64_t> ids(java_ids.begin(), java_ids.end());
    if (ids.front() != 0 || ids.back() != 0) {
      throw std::runtime_error("Kokoro phoneme boundary tokens are missing");
    }

    auto memory = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    const int64_t id_shape[] = {1, token_count};
    auto input_ids = Ort::Value::CreateTensor<int64_t>(
        memory, ids.data(), ids.size(), id_shape, std::size(id_shape));
    const char* text_inputs[] = {"ids"};
    const char* text_outputs[] = {"durations", "prosody", "acoustic"};
    auto encoded = text.Run(run, text_inputs, &input_ids, 1, text_outputs, std::size(text_outputs));
    RequireTensor(encoded[0], ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, {token_count}, "durations");
    RequireTensor(encoded[1], ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, {1, 640, token_count}, "prosody");
    RequireTensor(encoded[2], ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, {1, 512, token_count}, "acoustic");

    std::vector<int64_t> frame_tokens;
    frame_tokens.reserve(max_frames);
    const auto* durations = encoded[0].GetTensorData<int64_t>();
    for (int64_t token = 0; token < token_count; ++token) {
      const int64_t remaining = max_frames - static_cast<int64_t>(frame_tokens.size());
      if (durations[token] < 0) throw std::runtime_error("Kokoro predicted a negative token duration");
      if (durations[token] > remaining) throw FrameLimitError();
      frame_tokens.insert(frame_tokens.end(), durations[token], token);
    }
    if (frame_tokens.empty()) throw std::runtime_error("Kokoro predicted an empty waveform");

    const int64_t frames = static_cast<int64_t>(frame_tokens.size());
    const int64_t samples = frames * kSamplesPerFrame;
    const int64_t frame_shape[] = {frames};
    const int64_t phase_shape[] = {1, kHarmonics};
    const int64_t noise_shape[] = {1, samples, kHarmonics};
    std::mt19937 rng(42);
    std::uniform_real_distribution<float> uniform(0.f, 1.f);
    std::normal_distribution<float> normal(0.f, 1.f);
    std::array<float, kHarmonics> phase{};
    for (auto& value : phase) value = uniform(rng);
    std::vector<float> noise(samples * kHarmonics);
    for (auto& value : noise) value = normal(rng);

    std::array<Ort::Value, 5> render_inputs = {
        std::move(encoded[1]), std::move(encoded[2]),
        Ort::Value::CreateTensor<int64_t>(memory, frame_tokens.data(), frame_tokens.size(), frame_shape, std::size(frame_shape)),
        Ort::Value::CreateTensor<float>(memory, phase.data(), phase.size(), phase_shape, std::size(phase_shape)),
        Ort::Value::CreateTensor<float>(memory, noise.data(), noise.size(), noise_shape, std::size(noise_shape)),
    };
    const char* render_names[] = {"prosody", "acoustic", "frame_tokens", "phase_noise", "gaussian_noise"};
    const char* render_outputs[] = {"pcm"};
    auto rendered = render.Run(run, render_names, render_inputs.data(), render_inputs.size(), render_outputs, 1);
    RequireTensor(rendered[0], ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, {samples}, "pcm");
    auto pcm = jni->NewFloatArray(samples);
    if (pcm != nullptr) jni->SetFloatArrayRegion(pcm, 0, samples, rendered[0].GetTensorData<float>());
    return pcm;
  }
};
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_tts_LamiKokoroScratchNative_version(JNIEnv* env, jobject) {
  return JniCall<jstring>(env, nullptr, [&] { return env->NewStringUTF(InitializeOrt()); });
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_ninbyo02_lami_tts_LamiKokoroScratchNative_load(
    JNIEnv* env, jobject, jbyteArray text, jbyteArray render, jint max_frames) {
  return JniCall<jlong>(env, 0, [&] {
    InitializeOrt();
    auto session = std::make_unique<ScratchSession>(PathFromUtf8(env, text), PathFromUtf8(env, render), max_frames);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(session.release()));
  });
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_ninbyo02_lami_tts_LamiKokoroScratchNative_newRun(JNIEnv* env, jobject) {
  return JniCall<jlong>(env, 0, [&] {
    InitializeOrt();
    return static_cast<jlong>(reinterpret_cast<intptr_t>(new Ort::RunOptions()));
  });
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_ninbyo02_lami_tts_LamiKokoroScratchNative_synthesize(
    JNIEnv* env, jobject, jlong session, jlong run, jlongArray ids) {
  return JniCall<jfloatArray>(env, nullptr, [&] {
    return Handle<ScratchSession>(session).Synthesize(env, ids, Handle<Ort::RunOptions>(run));
  });
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_ninbyo02_lami_tts_LamiKokoroScratchNative_cancelRun(JNIEnv* env, jobject, jlong run) {
  JniCall<bool>(env, false, [&] { Handle<Ort::RunOptions>(run).SetTerminate(); return true; });
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_ninbyo02_lami_tts_LamiKokoroScratchNative_releaseRun(JNIEnv*, jobject, jlong run) {
  delete reinterpret_cast<Ort::RunOptions*>(static_cast<intptr_t>(run));
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_ninbyo02_lami_tts_LamiKokoroScratchNative_release(JNIEnv*, jobject, jlong session) {
  delete reinterpret_cast<ScratchSession*>(static_cast<intptr_t>(session));
}
