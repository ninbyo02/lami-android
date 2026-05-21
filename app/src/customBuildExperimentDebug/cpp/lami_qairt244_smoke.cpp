#include <android/log.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

#define QAIRT244_SMOKE_TAG "QAIRT244_SMOKE"
#define QAIRT244_SMOKE_MARKER "qairt244_app_jni_smoke_v1"
#define QAIRT244_SMOKE_MAX_TEXT 1024
#define QAIRT244_SMOKE_MAX_PATH 1024

static long long qairt244_smoke_now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return ((long long)ts.tv_sec * 1000LL) + (ts.tv_nsec / 1000000LL);
}

static long qairt244_smoke_tid(void) {
#if defined(__ANDROID__)
    return (long)syscall(__NR_gettid);
#else
    return 0;
#endif
}

static int qairt244_smoke_jstring_to_c(
    JNIEnv* env,
    jstring value,
    char* out,
    size_t out_size
) {
    const char* chars;
    if (out == NULL || out_size == 0) return 0;
    out[0] = '\0';
    if (value == NULL) return 1;
    chars = env->GetStringUTFChars(value, NULL);
    if (chars == NULL) return 0;
    snprintf(out, out_size, "%s", chars);
    env->ReleaseStringUTFChars(value, chars);
    return 1;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244AppJniSmoke_nativeRun(
    JNIEnv* env,
    jclass,
    jstring output_path,
    jstring run_id
) {
    char output_path_chars[QAIRT244_SMOKE_MAX_PATH];
    char run_id_chars[QAIRT244_SMOKE_MAX_TEXT];
    char line[QAIRT244_SMOKE_MAX_TEXT];
    FILE* file;
    long long now_ms = qairt244_smoke_now_ms();

    if (!qairt244_smoke_jstring_to_c(env, output_path, output_path_chars, sizeof(output_path_chars))) {
        return env->NewStringUTF("error=output_path-conversion-failed");
    }
    if (!qairt244_smoke_jstring_to_c(env, run_id, run_id_chars, sizeof(run_id_chars))) {
        return env->NewStringUTF("error=run_id-conversion-failed");
    }

    snprintf(
        line,
        sizeof(line),
        QAIRT244_SMOKE_MARKER
        " native entry pid=%d tid=%ld epochMs=%lld runId=%s outputPathNull=%d",
        getpid(),
        qairt244_smoke_tid(),
        now_ms,
        run_id_chars[0] == '\0' ? "-" : run_id_chars,
        output_path == NULL
    );

    __android_log_print(ANDROID_LOG_ERROR, QAIRT244_SMOKE_TAG, "%s", line);

    file = fopen(output_path_chars, "w");
    if (file == NULL) {
        char error_line[QAIRT244_SMOKE_MAX_TEXT];
        snprintf(error_line, sizeof(error_line), "error=file-open-failed marker=%s", QAIRT244_SMOKE_MARKER);
        __android_log_print(ANDROID_LOG_ERROR, QAIRT244_SMOKE_TAG, "%s", error_line);
        return env->NewStringUTF(error_line);
    }
    fprintf(file, "%s\n", line);
    fflush(file);
    fclose(file);

    return env->NewStringUTF(line);
}
