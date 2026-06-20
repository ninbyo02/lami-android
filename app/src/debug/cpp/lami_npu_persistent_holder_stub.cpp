#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>

#define LAMI_NPU_HOLDER_STUB_VERSION "dev_only_standard_route_adapter_holder_create_close_v1"
#define LAMI_NPU_HOLDER_NEXT_STEP "review_create_close_device_result_then_implement_run_once_without_multi_turn"
#define LAMI_NPU_HOLDER_MAX_SUMMARY 8192
#define LAMI_NPU_HOLDER_MAX_TEXT 512
#define LAMI_NPU_HOLDER_ID "native-holder-1"

typedef struct LamiNpuHolderState {
    int holder_open;
    int holder_generation;
    char holder_id[LAMI_NPU_HOLDER_MAX_TEXT];
    int create_requested_count;
    int create_called_count;
    int create_succeeded_count;
    int close_requested_count;
    int close_called_count;
    int close_succeeded_count;
    int diagnostics_called_count;
    int run_requested_count;
    int run_called_count;
    int run_succeeded_count;
    int double_close_observed;
    int fatal_latch;
    char fatal_reason[LAMI_NPU_HOLDER_MAX_TEXT];
    char last_status[LAMI_NPU_HOLDER_MAX_TEXT];
    char last_error[LAMI_NPU_HOLDER_MAX_TEXT];
} LamiNpuHolderState;

static pthread_mutex_t g_lami_npu_holder_mutex = PTHREAD_MUTEX_INITIALIZER;
static LamiNpuHolderState g_lami_npu_holder_state = {
    0,
    0,
    "unavailable",
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    "none",
    "not_created",
    "none",
};

static const char* lami_npu_bool(int value) {
    return value ? "true" : "false";
}

static int lami_npu_jstring_to_c(
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

static void lami_npu_set_last(const char* status, const char* error) {
    snprintf(g_lami_npu_holder_state.last_status, sizeof(g_lami_npu_holder_state.last_status), "%s", status);
    snprintf(g_lami_npu_holder_state.last_error, sizeof(g_lami_npu_holder_state.last_error), "%s", error);
}

static void lami_npu_set_fatal(const char* reason) {
    g_lami_npu_holder_state.fatal_latch = 1;
    snprintf(g_lami_npu_holder_state.fatal_reason, sizeof(g_lami_npu_holder_state.fatal_reason), "%s", reason);
}

static jstring lami_npu_holder_summary(
    JNIEnv* env,
    int create_requested,
    int create_called,
    int create_succeeded,
    int close_requested,
    int close_called,
    int close_succeeded,
    int diagnostics_called,
    int run_requested,
    int run_called,
    int run_supported,
    int run_decode_reached,
    const char* status,
    const char* reason
) {
    char summary[LAMI_NPU_HOLDER_MAX_SUMMARY];
    const char* holder_id = g_lami_npu_holder_state.holder_id[0] == '\0'
        ? "unavailable"
        : g_lami_npu_holder_state.holder_id;
    const int restart_app_recommended = g_lami_npu_holder_state.fatal_latch;
    snprintf(
        summary,
        sizeof(summary),
        "test_name=NPU Persistent Holder Create Close Probe\n"
        "holder_api_available=true\n"
        "native_holder_stub_available=true\n"
        "native_holder_create_close_available=true\n"
        "native_holder_stub_version=%s\n"
        "native_create_declared=true\n"
        "native_run_declared=true\n"
        "native_close_declared=true\n"
        "native_diagnostics_declared=true\n"
        "holder_create_requested=%s\n"
        "holder_create_called=%s\n"
        "holder_create_succeeded=%s\n"
        "holder_create_count=%d\n"
        "holder_create_success_count=%d\n"
        "holder_id=%s\n"
        "holder_open=%s\n"
        "open_holder=%s\n"
        "holder_close_requested=%s\n"
        "holder_close_called=%s\n"
        "holder_close_succeeded=%s\n"
        "holder_close_count=%d\n"
        "holder_close_success_count=%d\n"
        "holder_double_close_safe=true\n"
        "holder_double_close_observed=%s\n"
        "holder_open_before_run=%s\n"
        "run_once_requested=%s\n"
        "run_once_called=%s\n"
        "run_once_count=%d\n"
        "holder_fatal_latch=%s\n"
        "holder_fatal_reason=%s\n"
        "last_status=%s\n"
        "last_error=%s\n"
        "engine_factory_create_called=false\n"
        "engine_create_called=false\n"
        "model_assets_create_called=false\n"
        "engine_settings_create_called=false\n"
        "npu_decode_called=false\n"
        "generate_called=false\n"
        "qnn_decode_called=false\n"
        "qnn_called=false\n"
        "run_once_supported=%s\n"
        "run_once_succeeded=%s\n"
        "run_once_reason=%s\n"
        "run_decode_reached=%s\n"
        "native_run_called=%s\n"
        "native_create_called=%s\n"
        "native_close_called=%s\n"
        "native_diagnostics_called=%s\n"
        "persistent_multi_turn_possible=false\n"
        "restart_app_recommended=%s\n"
        "status=%s\n"
        "reason=%s\n"
        "holder_native_create_level=app_jni_holder_lifecycle_only_pre_engine_create\n"
        "engine_create_availability=unavailable\n"
        "model_assets_create_availability=unavailable\n"
        "recommended_next_step=%s",
        LAMI_NPU_HOLDER_STUB_VERSION,
        lami_npu_bool(create_requested),
        lami_npu_bool(create_called),
        lami_npu_bool(create_succeeded),
        g_lami_npu_holder_state.create_called_count,
        g_lami_npu_holder_state.create_succeeded_count,
        holder_id,
        lami_npu_bool(g_lami_npu_holder_state.holder_open),
        lami_npu_bool(g_lami_npu_holder_state.holder_open),
        lami_npu_bool(close_requested),
        lami_npu_bool(close_called),
        lami_npu_bool(close_succeeded),
        g_lami_npu_holder_state.close_called_count,
        g_lami_npu_holder_state.close_succeeded_count,
        lami_npu_bool(g_lami_npu_holder_state.double_close_observed),
        lami_npu_bool(g_lami_npu_holder_state.holder_open),
        lami_npu_bool(run_requested),
        lami_npu_bool(run_called),
        g_lami_npu_holder_state.run_called_count,
        lami_npu_bool(g_lami_npu_holder_state.fatal_latch),
        g_lami_npu_holder_state.fatal_reason,
        g_lami_npu_holder_state.last_status,
        g_lami_npu_holder_state.last_error,
        lami_npu_bool(run_supported),
        lami_npu_bool(g_lami_npu_holder_state.run_succeeded_count > 0),
        reason,
        lami_npu_bool(run_decode_reached),
        lami_npu_bool(run_called),
        lami_npu_bool(create_called),
        lami_npu_bool(close_called),
        lami_npu_bool(diagnostics_called),
        lami_npu_bool(restart_app_recommended),
        status,
        reason,
        LAMI_NPU_HOLDER_NEXT_STEP
    );
    return env->NewStringUTF(summary);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeCreateStandardRouteAdapterHolder(
    JNIEnv* env,
    jclass,
    jstring model_path,
    jstring,
    jstring,
    jint
) {
    char model_path_chars[LAMI_NPU_HOLDER_MAX_TEXT];
    int lock_result;
    if (!lami_npu_jstring_to_c(env, model_path, model_path_chars, sizeof(model_path_chars))) {
        return env->NewStringUTF("status=error\nreason=model_path_jstring_conversion_failed");
    }
    lock_result = pthread_mutex_trylock(&g_lami_npu_holder_mutex);
    if (lock_result != 0) {
        return lami_npu_holder_summary(
            env,
            1,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            "busy",
            "holder_create_or_close_already_in_progress"
        );
    }
    g_lami_npu_holder_state.create_requested_count++;
    if (g_lami_npu_holder_state.fatal_latch) {
        lami_npu_set_last("blocked", "holder_fatal_latch_active");
        jstring result = lami_npu_holder_summary(
            env,
            1,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            "blocked",
            "holder_fatal_latch_active"
        );
        pthread_mutex_unlock(&g_lami_npu_holder_mutex);
        return result;
    }
    if (g_lami_npu_holder_state.holder_open) {
        lami_npu_set_last("blocked", "holder_already_open");
        jstring result = lami_npu_holder_summary(
            env,
            1,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            "blocked",
            "holder_already_open"
        );
        pthread_mutex_unlock(&g_lami_npu_holder_mutex);
        return result;
    }
    if (model_path_chars[0] == '\0') {
        lami_npu_set_fatal("invalid_model_path");
        lami_npu_set_last("failed", "invalid_model_path");
        jstring result = lami_npu_holder_summary(
            env,
            1,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            "failed",
            "invalid_model_path"
        );
        pthread_mutex_unlock(&g_lami_npu_holder_mutex);
        return result;
    }

    g_lami_npu_holder_state.create_called_count++;
    g_lami_npu_holder_state.create_succeeded_count++;
    g_lami_npu_holder_state.holder_open = 1;
    g_lami_npu_holder_state.holder_generation++;
    snprintf(g_lami_npu_holder_state.holder_id, sizeof(g_lami_npu_holder_state.holder_id), "%s", LAMI_NPU_HOLDER_ID);
    lami_npu_set_last("created", "none");
    jstring result = lami_npu_holder_summary(
        env,
        1,
        1,
        1,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        "created",
        "app_jni_holder_lifecycle_created_without_engine_create"
    );
    pthread_mutex_unlock(&g_lami_npu_holder_mutex);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunStandardRouteAdapterHolderOnce(
    JNIEnv* env,
    jclass,
    jstring holder_id,
    jstring,
    jint
) {
    char holder_id_chars[LAMI_NPU_HOLDER_MAX_TEXT];
    if (!lami_npu_jstring_to_c(env, holder_id, holder_id_chars, sizeof(holder_id_chars))) {
        return env->NewStringUTF("status=error\nreason=holder_id_jstring_conversion_failed");
    }
    if (pthread_mutex_trylock(&g_lami_npu_holder_mutex) != 0) {
        return lami_npu_holder_summary(
            env,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1,
            1,
            0,
            0,
            "busy",
            "holder_create_or_close_already_in_progress"
        );
    }
    g_lami_npu_holder_state.run_requested_count++;
    if (g_lami_npu_holder_state.fatal_latch) {
        lami_npu_set_last("blocked", "holder_fatal_latch_active");
        jstring result = lami_npu_holder_summary(
            env,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1,
            0,
            0,
            0,
            "blocked",
            "holder_fatal_latch_active"
        );
        pthread_mutex_unlock(&g_lami_npu_holder_mutex);
        return result;
    }
    if (!g_lami_npu_holder_state.holder_open) {
        lami_npu_set_last("blocked", "holder_not_open");
        jstring result = lami_npu_holder_summary(
            env,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1,
            0,
            0,
            0,
            "blocked",
            "holder_not_open"
        );
        pthread_mutex_unlock(&g_lami_npu_holder_mutex);
        return result;
    }
    if (
        holder_id_chars[0] == '\0' ||
        strcmp(holder_id_chars, "unavailable") == 0 ||
        strcmp(holder_id_chars, g_lami_npu_holder_state.holder_id) != 0
    ) {
        lami_npu_set_last("blocked", "holder_id_mismatch");
        jstring result = lami_npu_holder_summary(
            env,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1,
            0,
            0,
            0,
            "blocked",
            "holder_id_mismatch"
        );
        pthread_mutex_unlock(&g_lami_npu_holder_mutex);
        return result;
    }
    if (g_lami_npu_holder_state.run_called_count > 0) {
        lami_npu_set_last("blocked", "run_once_already_consumed");
        jstring result = lami_npu_holder_summary(
            env,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1,
            0,
            0,
            0,
            "blocked",
            "run_once_already_consumed"
        );
        pthread_mutex_unlock(&g_lami_npu_holder_mutex);
        return result;
    }
    g_lami_npu_holder_state.run_called_count++;
    lami_npu_set_last("run_ready", "holder_open_existing_one_shot_decode_may_run_once");
    jstring result = lami_npu_holder_summary(
        env,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        1,
        1,
        1,
        0,
        "run_ready",
        "holder_open_existing_one_shot_decode_may_run_once"
    );
    pthread_mutex_unlock(&g_lami_npu_holder_mutex);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeCloseStandardRouteAdapterHolder(
    JNIEnv* env,
    jclass,
    jstring holder_id,
    jstring
) {
    char holder_id_chars[LAMI_NPU_HOLDER_MAX_TEXT];
    if (!lami_npu_jstring_to_c(env, holder_id, holder_id_chars, sizeof(holder_id_chars))) {
        return env->NewStringUTF("status=error\nreason=holder_id_jstring_conversion_failed");
    }
    if (pthread_mutex_trylock(&g_lami_npu_holder_mutex) != 0) {
        return lami_npu_holder_summary(
            env,
            0,
            0,
            0,
            1,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            "busy",
            "holder_create_or_close_already_in_progress"
        );
    }
    g_lami_npu_holder_state.close_requested_count++;
    g_lami_npu_holder_state.close_called_count++;
    if (!g_lami_npu_holder_state.holder_open) {
        g_lami_npu_holder_state.double_close_observed = 1;
        g_lami_npu_holder_state.close_succeeded_count++;
        lami_npu_set_last("closed_noop", "holder_already_closed");
        jstring result = lami_npu_holder_summary(
            env,
            0,
            0,
            0,
            1,
            1,
            1,
            0,
            0,
            0,
            0,
            0,
            "closed_noop",
            "holder_already_closed"
        );
        pthread_mutex_unlock(&g_lami_npu_holder_mutex);
        return result;
    }
    if (
        holder_id_chars[0] != '\0' &&
        strcmp(holder_id_chars, "unavailable") != 0 &&
        strcmp(holder_id_chars, g_lami_npu_holder_state.holder_id) != 0
    ) {
        lami_npu_set_last("blocked", "holder_id_mismatch");
        jstring result = lami_npu_holder_summary(
            env,
            0,
            0,
            0,
            1,
            1,
            0,
            0,
            0,
            0,
            0,
            0,
            "blocked",
            "holder_id_mismatch"
        );
        pthread_mutex_unlock(&g_lami_npu_holder_mutex);
        return result;
    }
    g_lami_npu_holder_state.holder_open = 0;
    snprintf(g_lami_npu_holder_state.holder_id, sizeof(g_lami_npu_holder_state.holder_id), "unavailable");
    g_lami_npu_holder_state.close_succeeded_count++;
    lami_npu_set_last("closed", "none");
    jstring result = lami_npu_holder_summary(
        env,
        0,
        0,
        0,
        1,
        1,
        1,
        0,
        0,
        0,
        0,
        0,
        "closed",
        "holder_closed_without_decode"
    );
    pthread_mutex_unlock(&g_lami_npu_holder_mutex);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeGetStandardRouteAdapterHolderDiagnostics(
    JNIEnv* env,
    jclass,
    jstring
) {
    if (pthread_mutex_trylock(&g_lami_npu_holder_mutex) != 0) {
        return lami_npu_holder_summary(
            env,
            0,
            0,
            0,
            0,
            0,
            0,
            1,
            0,
            0,
            0,
            0,
            "busy",
            "holder_create_or_close_already_in_progress"
        );
    }
    g_lami_npu_holder_state.diagnostics_called_count++;
    jstring result = lami_npu_holder_summary(
        env,
        0,
        0,
        0,
        0,
        0,
        0,
        1,
        0,
        0,
        0,
        0,
        g_lami_npu_holder_state.last_status,
        g_lami_npu_holder_state.last_error
    );
    pthread_mutex_unlock(&g_lami_npu_holder_mutex);
    return result;
}
