#include <jni.h>

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_ninbyo02_lami_tts_LamiVoiceMatrixKernels_logits(
    JNIEnv* env, jobject, jobject buffer, jfloatArray hidden, jint rows) {
    auto* weights = static_cast<const float*>(env->GetDirectBufferAddress(buffer));
    if (!weights || rows <= 0 || env->GetArrayLength(hidden) != 1024 ||
        env->GetDirectBufferCapacity(buffer) != static_cast<jlong>(rows) * 1024) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid voice matrix buffer");
        return nullptr;
    }
    jfloatArray result = env->NewFloatArray(rows);
    if (!result) return nullptr;
    float* input = env->GetFloatArrayElements(hidden, nullptr);
    if (!input) return nullptr;
    float* scores = env->GetFloatArrayElements(result, nullptr);
    if (!scores) { env->ReleaseFloatArrayElements(hidden, input, JNI_ABORT); return nullptr; }
    // Interleave independent rows; preserve the addition order within each row.
    int r = 0;
    for (; r + 3 < rows; r += 4) {
        const float* a = weights + r * 1024;
        const float* b = a + 1024;
        const float* c = b + 1024;
        const float* d = c + 1024;
        float sa = 0, sb = 0, sc = 0, sd = 0;
        for (int j = 0; j < 1024; ++j) {
            const float x = input[j];
            sa += a[j] * x;
            sb += b[j] * x;
            sc += c[j] * x;
            sd += d[j] * x;
        }
        scores[r] = sa;
        scores[r + 1] = sb;
        scores[r + 2] = sc;
        scores[r + 3] = sd;
    }
    for (; r < rows; ++r) {
        float score = 0.0f;
        for (int j = 0; j < 1024; ++j) score += weights[r * 1024 + j] * input[j];
        scores[r] = score;
    }
    env->ReleaseFloatArrayElements(hidden, input, JNI_ABORT);
    env->ReleaseFloatArrayElements(result, scores, 0);
    return result;
}
