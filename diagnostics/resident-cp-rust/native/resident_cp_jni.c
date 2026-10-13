#include <jni.h>
#include "resident_cp_ffi.h"

static void failed(JNIEnv *env, int32_t status) {
    if (!status || (*env)->ExceptionCheck(env)) return;
    jclass type = (*env)->FindClass(env, status == 1 ? "java/lang/IllegalArgumentException" : "java/lang/IllegalStateException");
    if (type) (*env)->ThrowNew(env, type, "Resident CP diagnostic native operation failed");
}

JNIEXPORT jlong JNICALL Java_io_github_ninbyo02_lami_tts_ResidentCpRustJni_nativeCreate(
    JNIEnv *env, jobject owner, jbyteArray bytes, jstring backend, jstring system, jstring rpc) {
    (void)owner;
    if (!bytes || !backend || !system || !rpc) { failed(env, 1); return 0; }
    jsize length = (*env)->GetArrayLength(env, bytes);
    if (length <= 0 || length > 512 * 1024 * 1024) { failed(env, 1); return 0; }
    jbyte *data = (*env)->GetByteArrayElements(env, bytes, NULL);
    const char *b = data ? (*env)->GetStringUTFChars(env, backend, NULL) : NULL;
    const char *s = b ? (*env)->GetStringUTFChars(env, system, NULL) : NULL;
    const char *r = s ? (*env)->GetStringUTFChars(env, rpc, NULL) : NULL;
    uint64_t handle = 0;
    int32_t status = data && b && s && r ? lami_cp_create((const uint8_t *)data, (size_t)length, b, s, r, &handle) : 4;
    if (r) (*env)->ReleaseStringUTFChars(env, rpc, r);
    if (s) (*env)->ReleaseStringUTFChars(env, system, s);
    if (b) (*env)->ReleaseStringUTFChars(env, backend, b);
    if (data) (*env)->ReleaseByteArrayElements(env, bytes, data, JNI_ABORT);
    failed(env, status);
    return status ? 0 : (jlong)handle;
}

JNIEXPORT void JNICALL Java_io_github_ninbyo02_lami_tts_ResidentCpRustJni_nativeReset(JNIEnv *env, jobject owner, jlong handle) {
    (void)owner; failed(env, lami_cp_reset((uint64_t)handle));
}
JNIEXPORT void JNICALL Java_io_github_ninbyo02_lami_tts_ResidentCpRustJni_nativeCancel(JNIEnv *env, jobject owner, jlong handle) {
    (void)owner; failed(env, lami_cp_cancel((uint64_t)handle));
}
JNIEXPORT void JNICALL Java_io_github_ninbyo02_lami_tts_ResidentCpRustJni_nativeRelease(JNIEnv *env, jobject owner, jlong handle) {
    (void)owner; failed(env, lami_cp_release((uint64_t)handle));
}
JNIEXPORT void JNICALL Java_io_github_ninbyo02_lami_tts_ResidentCpRustJni_nativeStep(
    JNIEnv *env, jobject owner, jlong handle, jint position, jfloatArray input, jfloatArray output) {
    (void)owner;
    if (!input || !output || (*env)->GetArrayLength(env, input) != 1024 || (*env)->GetArrayLength(env, output) != 1024) {
        failed(env, 1); return;
    }
    float values[1024], result[1024];
    (*env)->GetFloatArrayRegion(env, input, 0, 1024, values);
    if ((*env)->ExceptionCheck(env)) return;
    int32_t status = lami_cp_step((uint64_t)handle, position, values, result, 1024);
    if (status) { failed(env, status); return; }
    (*env)->SetFloatArrayRegion(env, output, 0, 1024, result);
}
JNIEXPORT jlongArray JNICALL Java_io_github_ninbyo02_lami_tts_ResidentCpRustJni_nativeStats(JNIEnv *env, jobject owner, jlong handle) {
    (void)owner;
    uint64_t stats[7];
    int32_t status = lami_cp_stats((uint64_t)handle, stats, 7);
    if (status) { failed(env, status); return NULL; }
    jlong values[7];
    for (size_t i = 0; i < 7; ++i) values[i] = (jlong)stats[i];
    jlongArray output = (*env)->NewLongArray(env, 7);
    if (output) (*env)->SetLongArrayRegion(env, output, 0, 7, values);
    return output;
}
