#include <jni.h>
#include <chrono>
#include <stdexcept>
#include <string>
#include <vector>
#include "sentencepiece_processor.h"

namespace {
constexpr jsize kMaxModel = 16 * 1024 * 1024;
constexpr jsize kMaxText = 4 * 1024 * 1024;
std::string Bytes(JNIEnv* env, jbyteArray array, jsize limit) {
  if (!array) throw std::invalid_argument("null-buffer");
  const auto size = env->GetArrayLength(array);
  if (size > limit) throw std::invalid_argument("buffer-too-large");
  std::string bytes(static_cast<size_t>(size), '\0');
  if (size) env->GetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
  if (env->ExceptionCheck()) throw std::runtime_error("copy-failed");
  return bytes;
}
using Clock = std::chrono::steady_clock;
jlong Ns(Clock::time_point start) {
  return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - start).count();
}
}

// Owns the processor only for this call; never accepts or exposes a native handle.
extern "C" JNIEXPORT jlongArray JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_StandaloneSentencePieceJni_countPair(
    JNIEnv* env, jobject, jbyteArray model, jbyteArray input, jbyteArray output) {
  try {
    const auto serialized = Bytes(env, model, kMaxModel);
    const auto prompt = Bytes(env, input, kMaxText);
    const auto response = Bytes(env, output, kMaxText);
    jlong values[4];
    {
      sentencepiece::SentencePieceProcessor processor;
      auto started = Clock::now();
      if (!processor.LoadFromSerializedProto(serialized).ok()) throw std::runtime_error("load-failed");
      values[2] = Ns(started);
      started = Clock::now();
      std::vector<int> ids;
      if (!processor.Encode(prompt, &ids).ok()) throw std::runtime_error("encode-failed");
      values[0] = static_cast<jlong>(ids.size());
      ids.clear();
      if (!processor.Encode(response, &ids).ok()) throw std::runtime_error("encode-failed");
      values[1] = static_cast<jlong>(ids.size());
      values[3] = Ns(started);
    }  // Processor freed before reporting results; also freed during exceptions.
    auto result = env->NewLongArray(4);
    if (result) env->SetLongArrayRegion(result, 0, 4, values);
    return result;
  } catch (...) {
    if (!env->ExceptionCheck()) {
      auto cls = env->FindClass("java/lang/IllegalStateException");
      if (cls) { env->ThrowNew(cls, "tokenizer-only-count-failed"); env->DeleteLocalRef(cls); }
    }
    return nullptr;
  }
}
