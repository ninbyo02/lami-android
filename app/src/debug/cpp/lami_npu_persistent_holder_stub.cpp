#include <jni.h>
#include <stdio.h>

#define LAMI_NPU_HOLDER_STUB_VERSION "dev_only_standard_route_adapter_holder_stub_v1"
#define LAMI_NPU_HOLDER_STUB_REASON "dev_only_native_holder_stub_no_engine_create"
#define LAMI_NPU_HOLDER_STUB_NEXT_STEP "implement_native_create_close_without_decode"
#define LAMI_NPU_HOLDER_STUB_MAX_SUMMARY 4096

static jstring lami_npu_holder_stub_summary(
    JNIEnv* env,
    int create_called,
    int run_called,
    int close_called,
    int diagnostics_called
) {
    char summary[LAMI_NPU_HOLDER_STUB_MAX_SUMMARY];
    snprintf(
        summary,
        sizeof(summary),
        "holder_api_available=false\n"
        "native_holder_stub_available=true\n"
        "native_holder_stub_version=%s\n"
        "native_create_declared=true\n"
        "native_run_declared=true\n"
        "native_close_declared=true\n"
        "native_diagnostics_declared=true\n"
        "native_create_called=%s\n"
        "native_run_called=%s\n"
        "native_close_called=%s\n"
        "native_diagnostics_called=%s\n"
        "holder_create_supported=false\n"
        "holder_run_supported=false\n"
        "holder_close_supported=false\n"
        "holder_diagnostics_supported=true\n"
        "holder_id=unavailable\n"
        "engine_create_called=false\n"
        "model_assets_create_called=false\n"
        "npu_decode_called=false\n"
        "qnn_called=false\n"
        "status=not_implemented\n"
        "reason=%s\n"
        "persistent_multi_turn_possible=false\n"
        "recommended_next_step=%s",
        LAMI_NPU_HOLDER_STUB_VERSION,
        create_called ? "true" : "false",
        run_called ? "true" : "false",
        close_called ? "true" : "false",
        diagnostics_called ? "true" : "false",
        LAMI_NPU_HOLDER_STUB_REASON,
        LAMI_NPU_HOLDER_STUB_NEXT_STEP
    );
    return env->NewStringUTF(summary);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeCreateStandardRouteAdapterHolder(
    JNIEnv* env,
    jclass,
    jstring,
    jstring,
    jstring,
    jint
) {
    return lami_npu_holder_stub_summary(
        env,
        1,
        0,
        0,
        0
    );
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunStandardRouteAdapterHolderOnce(
    JNIEnv* env,
    jclass,
    jstring,
    jstring,
    jint
) {
    return lami_npu_holder_stub_summary(
        env,
        0,
        1,
        0,
        0
    );
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeCloseStandardRouteAdapterHolder(
    JNIEnv* env,
    jclass,
    jstring,
    jstring
) {
    return lami_npu_holder_stub_summary(
        env,
        0,
        0,
        1,
        0
    );
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeGetStandardRouteAdapterHolderDiagnostics(
    JNIEnv* env,
    jclass,
    jstring
) {
    return lami_npu_holder_stub_summary(
        env,
        0,
        0,
        0,
        1
    );
}
