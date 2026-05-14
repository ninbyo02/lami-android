#include <android/log.h>
#include <elf.h>
#include <dlfcn.h>
#include <errno.h>
#include <glob.h>
#include <jni.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define QNN_DIRECT_MAX_TEXT 4096
#define QNN_DIRECT_MAX_PATH 1024
#define QNN_DIRECT_MAX_LINE 4096
#define QNN_DIRECT_MAX_MAP_LINES 160
#define QNN_DIRECT_MAX_NEEDED 96
#define QNN_DIRECT_MAX_NAME 160
#define QNN_DIRECT_LOG_TAG "QnnDirectProbeNative"

typedef struct QnnDirectLibrary {
    const char* name;
    void* handle;
    int loaded;
} QnnDirectLibrary;

typedef struct QnnDirectFastRpcStatus {
    int found;
    int loaded;
} QnnDirectFastRpcStatus;

typedef struct QnnDirectNeededList {
    char entries[QNN_DIRECT_MAX_NEEDED][QNN_DIRECT_MAX_NAME];
    int count;
    char rpath[QNN_DIRECT_MAX_TEXT];
    char runpath[QNN_DIRECT_MAX_TEXT];
} QnnDirectNeededList;

static long long qnn_direct_now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return ((long long)ts.tv_sec * 1000LL) + (ts.tv_nsec / 1000000LL);
}

static void qnn_direct_sanitize(char* value) {
    size_t i;
    if (value == NULL) return;
    for (i = 0; value[i] != '\0'; ++i) {
        if (value[i] == '\n' || value[i] == '\r') value[i] = ' ';
    }
}

static void qnn_direct_append_line(const char* path, const char* line) {
    FILE* file;
    if (path == NULL || line == NULL) return;
    file = fopen(path, "a");
    if (file != NULL) {
        fprintf(file, "%lld %s\n", qnn_direct_now_ms(), line);
        fflush(file);
        fclose(file);
    }
    __android_log_print(ANDROID_LOG_INFO, QNN_DIRECT_LOG_TAG, "%s", line);
}

static void qnn_direct_appendf(const char* path, const char* format, ...) {
    char line[QNN_DIRECT_MAX_TEXT];
    va_list args;
    va_start(args, format);
    vsnprintf(line, sizeof(line), format, args);
    va_end(args);
    qnn_direct_sanitize(line);
    qnn_direct_append_line(path, line);
}

static void qnn_direct_write_last_run(
    const char* path,
    const char* run_id,
    const char* stage,
    const char* result
) {
    FILE* file;
    if (path == NULL || run_id == NULL || stage == NULL) return;
    file = fopen(path, "w");
    if (file == NULL) return;
    fprintf(file, "runId=%s\n", run_id);
    fprintf(file, "pid=%d\n", getpid());
    fprintf(file, "startedAtEpochMs=unknown-native\n");
    fprintf(file, "lastUpdatedAtEpochMs=%lld\n", qnn_direct_now_ms());
    fprintf(file, "lastStage=%s\n", stage);
    if (result != NULL) fprintf(file, "result=%s\n", result);
    fflush(file);
    fclose(file);
}

static void qnn_direct_stage(
    const char* result_path,
    const char* last_run_path,
    const char* run_id,
    const char* stage
) {
    qnn_direct_appendf(result_path, "QNN_DIRECT_STAGE nativeStage=%s", stage);
    qnn_direct_write_last_run(last_run_path, run_id, stage, NULL);
}

static int qnn_direct_jstring_to_c(
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

static void qnn_direct_join_path(
    const char* dir,
    const char* name,
    char* out,
    size_t out_size
) {
    size_t length;
    if (out == NULL || out_size == 0) return;
    if (dir == NULL) dir = "";
    if (name == NULL) name = "";
    length = strlen(dir);
    if (length > 0 && dir[length - 1] == '/') {
        snprintf(out, out_size, "%s%s", dir, name);
    } else {
        snprintf(out, out_size, "%s/%s", dir, name);
    }
}

static const char* qnn_direct_dlerror_text(void) {
    const char* error = dlerror();
    return error == NULL ? "none" : error;
}

static int qnn_direct_contains(const char* text, const char* needle) {
    if (text == NULL || needle == NULL) return 0;
    return strstr(text, needle) != NULL;
}

static int qnn_direct_name_in_list(QnnDirectNeededList* list, const char* value) {
    int i;
    if (list == NULL || value == NULL || value[0] == '\0') return 1;
    for (i = 0; i < list->count; ++i) {
        if (strcmp(list->entries[i], value) == 0) return 1;
    }
    return 0;
}

static void qnn_direct_add_needed(QnnDirectNeededList* list, const char* value) {
    if (list == NULL || value == NULL || value[0] == '\0') return;
    if (qnn_direct_name_in_list(list, value)) return;
    if (list->count >= QNN_DIRECT_MAX_NEEDED) return;
    snprintf(list->entries[list->count], sizeof(list->entries[list->count]), "%s", value);
    list->count++;
}

static long long qnn_direct_vaddr_to_offset64(
    const Elf64_Phdr* phdrs,
    int phnum,
    uint64_t vaddr
) {
    int i;
    for (i = 0; i < phnum; ++i) {
        uint64_t start;
        uint64_t end;
        if (phdrs[i].p_type != PT_LOAD) continue;
        start = phdrs[i].p_vaddr;
        end = start + phdrs[i].p_filesz;
        if (vaddr >= start && vaddr < end) {
            return (long long)(phdrs[i].p_offset + (vaddr - start));
        }
    }
    return -1;
}

static long long qnn_direct_vaddr_to_offset32(
    const Elf32_Phdr* phdrs,
    int phnum,
    uint32_t vaddr
) {
    int i;
    for (i = 0; i < phnum; ++i) {
        uint32_t start;
        uint32_t end;
        if (phdrs[i].p_type != PT_LOAD) continue;
        start = phdrs[i].p_vaddr;
        end = start + phdrs[i].p_filesz;
        if (vaddr >= start && vaddr < end) {
            return (long long)(phdrs[i].p_offset + (vaddr - start));
        }
    }
    return -1;
}

static int qnn_direct_level_at_least(const char* actual, const char* expected) {
    if (strcmp(expected, "symbols") == 0) return 1;
    if (strcmp(expected, "system") == 0) {
        return strcmp(actual, "system") == 0 || strcmp(actual, "backend") == 0 || strcmp(actual, "device") == 0;
    }
    if (strcmp(expected, "backend") == 0) {
        return strcmp(actual, "backend") == 0 || strcmp(actual, "device") == 0;
    }
    if (strcmp(expected, "device") == 0) return strcmp(actual, "device") == 0;
    return 0;
}

static void qnn_direct_append_maps(const char* result_path, const char* reason) {
    FILE* file = fopen("/proc/self/maps", "r");
    char line[QNN_DIRECT_MAX_LINE];
    int matched = 0;
    int emitted = 0;
    if (file == NULL) {
        qnn_direct_appendf(result_path, "QNN_DIRECT_ERROR procMaps reason=%s class=fopen errno=%d", reason, errno);
        return;
    }
    while (fgets(line, sizeof(line), file) != NULL) {
        char lower[QNN_DIRECT_MAX_LINE];
        size_t i;
        snprintf(lower, sizeof(lower), "%s", line);
        for (i = 0; lower[i] != '\0'; ++i) {
            if (lower[i] >= 'A' && lower[i] <= 'Z') lower[i] = (char)(lower[i] - 'A' + 'a');
        }
        if (
            strstr(lower, "libqnn") != NULL ||
            strstr(lower, "litertdispatch") != NULL ||
            strstr(lower, "qairt_native_runtime") != NULL ||
            strstr(lower, "/data/local/tmp/qairt") != NULL
        ) {
            matched++;
            if (emitted < QNN_DIRECT_MAX_MAP_LINES) {
                qnn_direct_sanitize(line);
                qnn_direct_appendf(
                    result_path,
                    "QNN_DIRECT_STAGE nativeProcMap reason=%s line=%d value=%s",
                    reason,
                    emitted,
                    line
                );
                emitted++;
            }
        }
    }
    fclose(file);
    qnn_direct_appendf(result_path, "QNN_DIRECT_STAGE nativeProcMaps reason=%s matchedLines=%d", reason, matched);
}

static void qnn_direct_append_fastrpc_file(const char* result_path, const char* path) {
    struct stat st;
    char canonical[QNN_DIRECT_MAX_PATH];
    char link_target[QNN_DIRECT_MAX_PATH];
    ssize_t link_size;
    int stat_ok = stat(path, &st) == 0;
    const char* canonical_result = "unavailable";
    const char* readlink_result = "not-symlink-or-unavailable";

    canonical[0] = '\0';
    link_target[0] = '\0';
    if (realpath(path, canonical) != NULL) {
        canonical_result = canonical;
    }
    link_size = readlink(path, link_target, sizeof(link_target) - 1);
    if (link_size >= 0) {
        link_target[link_size] = '\0';
        readlink_result = link_target;
    }

    qnn_direct_appendf(
        result_path,
        "QNN_DIRECT_FASTRPC_FILE path=%s exists=%s canRead=%s canExecute=%s size=%lld canonical=%s readlink=%s",
        path,
        stat_ok ? "true" : "false",
        access(path, R_OK) == 0 ? "true" : "false",
        access(path, X_OK) == 0 ? "true" : "false",
        stat_ok ? (long long)st.st_size : 0LL,
        canonical_result,
        readlink_result
    );
}

static void qnn_direct_append_dep_file(const char* result_path, const char* dependency, const char* path) {
    struct stat st;
    char canonical[QNN_DIRECT_MAX_PATH];
    int stat_ok = stat(path, &st) == 0;
    const char* canonical_result = "unavailable";
    canonical[0] = '\0';
    if (realpath(path, canonical) != NULL) {
        canonical_result = canonical;
    }
    qnn_direct_appendf(
        result_path,
        "QNN_DIRECT_DEP_FILE dependency=%s path=%s exists=%s canRead=%s size=%lld canonical=%s",
        dependency,
        path,
        stat_ok ? "true" : "false",
        access(path, R_OK) == 0 ? "true" : "false",
        stat_ok ? (long long)st.st_size : 0LL,
        canonical_result
    );
}

static void qnn_direct_dep_dlopen(const char* result_path, const char* dependency, const char* path) {
    void* handle;
    const char* error;
    dlerror();
    handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    error = qnn_direct_dlerror_text();
    qnn_direct_appendf(
        result_path,
        "QNN_DIRECT_DEP_DLOPEN dependency=%s path=%s success=%s handle=%p dlerror=%s",
        dependency,
        path,
        handle != NULL ? "true" : "false",
        handle,
        error
    );
    if (
        strcmp(dependency, "libcdsprpc.so") == 0 &&
        qnn_direct_contains(path, "/vendor/lib64/") &&
        handle == NULL &&
        (qnn_direct_contains(error, "not accessible") || qnn_direct_contains(error, "namespace"))
    ) {
        qnn_direct_append_line(
            result_path,
            "QNN_DIRECT_NAMESPACE_CONCLUSION result=vendor-lib-exists-but-not-accessible dependency=libcdsprpc.so"
        );
    }
}

static int qnn_direct_parse_elf_needed(
    const char* result_path,
    const char* path,
    QnnDirectNeededList* all_needed
) {
    FILE* file;
    long size;
    unsigned char* bytes;
    int parsed = 0;
    QnnDirectNeededList target_needed;
    memset(&target_needed, 0, sizeof(target_needed));

    qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED_BEGIN target=%s", path);
    file = fopen(path, "rb");
    if (file == NULL) {
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_ELF_NEEDED target=%s result=fopen-failed errno=%d",
            path,
            errno
        );
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED_END target=%s neededCount=0", path);
        return 0;
    }
    if (fseek(file, 0, SEEK_END) != 0) {
        fclose(file);
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED target=%s result=fseek-failed", path);
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED_END target=%s neededCount=0", path);
        return 0;
    }
    size = ftell(file);
    if (size <= 0) {
        fclose(file);
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED target=%s result=empty", path);
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED_END target=%s neededCount=0", path);
        return 0;
    }
    rewind(file);
    bytes = (unsigned char*)malloc((size_t)size);
    if (bytes == NULL) {
        fclose(file);
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED target=%s result=malloc-failed size=%ld", path, size);
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED_END target=%s neededCount=0", path);
        return 0;
    }
    if (fread(bytes, 1, (size_t)size, file) != (size_t)size) {
        free(bytes);
        fclose(file);
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED target=%s result=fread-failed", path);
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED_END target=%s neededCount=0", path);
        return 0;
    }
    fclose(file);

    if (size >= (long)SELFMAG && memcmp(bytes, ELFMAG, SELFMAG) == 0) {
        if (bytes[EI_CLASS] == ELFCLASS64 && size >= (long)sizeof(Elf64_Ehdr)) {
            const Elf64_Ehdr* eh = (const Elf64_Ehdr*)bytes;
            if ((uint64_t)eh->e_phoff + ((uint64_t)eh->e_phnum * sizeof(Elf64_Phdr)) <= (uint64_t)size) {
                const Elf64_Phdr* phdrs = (const Elf64_Phdr*)(bytes + eh->e_phoff);
                const Elf64_Dyn* dyn = NULL;
                int dyn_count = 0;
                uint64_t strtab_vaddr = 0;
                uint64_t strsz = 0;
                long long strtab_offset = -1;
                int i;
                int j;
                for (i = 0; i < eh->e_phnum; ++i) {
                    if (phdrs[i].p_type == PT_DYNAMIC) {
                        dyn = (const Elf64_Dyn*)(bytes + phdrs[i].p_offset);
                        dyn_count = (int)(phdrs[i].p_filesz / sizeof(Elf64_Dyn));
                        break;
                    }
                }
                if (dyn != NULL) {
                    for (j = 0; j < dyn_count; ++j) {
                        if (dyn[j].d_tag == DT_STRTAB) strtab_vaddr = dyn[j].d_un.d_ptr;
                        if (dyn[j].d_tag == DT_STRSZ) strsz = dyn[j].d_un.d_val;
                    }
                    strtab_offset = qnn_direct_vaddr_to_offset64(phdrs, eh->e_phnum, strtab_vaddr);
                    if (strtab_offset >= 0 && strtab_offset < size) {
                        for (j = 0; j < dyn_count; ++j) {
                            if (
                                dyn[j].d_tag == DT_NEEDED ||
                                dyn[j].d_tag == DT_RPATH ||
                                dyn[j].d_tag == DT_RUNPATH
                            ) {
                                uint64_t name_offset = (uint64_t)strtab_offset + dyn[j].d_un.d_val;
                                if (name_offset < (uint64_t)size && (strsz == 0 || dyn[j].d_un.d_val < strsz)) {
                                    const char* value = (const char*)(bytes + name_offset);
                                    if (dyn[j].d_tag == DT_NEEDED) {
                                        qnn_direct_add_needed(&target_needed, value);
                                        qnn_direct_add_needed(all_needed, value);
                                    } else if (dyn[j].d_tag == DT_RPATH) {
                                        snprintf(target_needed.rpath, sizeof(target_needed.rpath), "%s", value);
                                    } else if (dyn[j].d_tag == DT_RUNPATH) {
                                        snprintf(target_needed.runpath, sizeof(target_needed.runpath), "%s", value);
                                    }
                                }
                            }
                        }
                        parsed = 1;
                    }
                }
            }
        } else if (bytes[EI_CLASS] == ELFCLASS32 && size >= (long)sizeof(Elf32_Ehdr)) {
            const Elf32_Ehdr* eh = (const Elf32_Ehdr*)bytes;
            if (eh->e_phoff + ((long)eh->e_phnum * (long)sizeof(Elf32_Phdr)) <= size) {
                const Elf32_Phdr* phdrs = (const Elf32_Phdr*)(bytes + eh->e_phoff);
                const Elf32_Dyn* dyn = NULL;
                int dyn_count = 0;
                uint32_t strtab_vaddr = 0;
                uint32_t strsz = 0;
                long long strtab_offset = -1;
                int i;
                int j;
                for (i = 0; i < eh->e_phnum; ++i) {
                    if (phdrs[i].p_type == PT_DYNAMIC) {
                        dyn = (const Elf32_Dyn*)(bytes + phdrs[i].p_offset);
                        dyn_count = (int)(phdrs[i].p_filesz / sizeof(Elf32_Dyn));
                        break;
                    }
                }
                if (dyn != NULL) {
                    for (j = 0; j < dyn_count; ++j) {
                        if (dyn[j].d_tag == DT_STRTAB) strtab_vaddr = dyn[j].d_un.d_ptr;
                        if (dyn[j].d_tag == DT_STRSZ) strsz = dyn[j].d_un.d_val;
                    }
                    strtab_offset = qnn_direct_vaddr_to_offset32(phdrs, eh->e_phnum, strtab_vaddr);
                    if (strtab_offset >= 0 && strtab_offset < size) {
                        for (j = 0; j < dyn_count; ++j) {
                            if (
                                dyn[j].d_tag == DT_NEEDED ||
                                dyn[j].d_tag == DT_RPATH ||
                                dyn[j].d_tag == DT_RUNPATH
                            ) {
                                uint32_t name_offset = (uint32_t)strtab_offset + dyn[j].d_un.d_val;
                                if (name_offset < (uint32_t)size && (strsz == 0 || (uint32_t)dyn[j].d_un.d_val < strsz)) {
                                    const char* value = (const char*)(bytes + name_offset);
                                    if (dyn[j].d_tag == DT_NEEDED) {
                                        qnn_direct_add_needed(&target_needed, value);
                                        qnn_direct_add_needed(all_needed, value);
                                    } else if (dyn[j].d_tag == DT_RPATH) {
                                        snprintf(target_needed.rpath, sizeof(target_needed.rpath), "%s", value);
                                    } else if (dyn[j].d_tag == DT_RUNPATH) {
                                        snprintf(target_needed.runpath, sizeof(target_needed.runpath), "%s", value);
                                    }
                                }
                            }
                        }
                        parsed = 1;
                    }
                }
            }
        }
    }

    if (!parsed) {
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED target=%s result=parse-failed", path);
    }
    for (int i = 0; i < target_needed.count; ++i) {
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_ELF_NEEDED target=%s needed=%s",
            path,
            target_needed.entries[i]
        );
    }
    if (target_needed.rpath[0] != '\0') {
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED target=%s rpath=%s", path, target_needed.rpath);
    }
    if (target_needed.runpath[0] != '\0') {
        qnn_direct_appendf(result_path, "QNN_DIRECT_ELF_NEEDED target=%s runpath=%s", path, target_needed.runpath);
    }
    qnn_direct_appendf(
        result_path,
        "QNN_DIRECT_ELF_NEEDED_END target=%s neededCount=%d",
        path,
        target_needed.count
    );
    free(bytes);
    return parsed;
}

static int qnn_direct_dependency_is_vndk_like(const char* dependency) {
    return strcmp(dependency, "libhidlbase.so") == 0 ||
        strcmp(dependency, "libutils.so") == 0 ||
        strcmp(dependency, "libcutils.so") == 0 ||
        strcmp(dependency, "libbase.so") == 0;
}

static void qnn_direct_search_dependency_path(
    const char* result_path,
    const char* dependency,
    const char* path
) {
    qnn_direct_appendf(result_path, "QNN_DIRECT_DEP_SEARCH dependency=%s path=%s", dependency, path);
    qnn_direct_append_dep_file(result_path, dependency, path);
    qnn_direct_dep_dlopen(result_path, dependency, path);
}

static void qnn_direct_search_dependency_glob(
    const char* result_path,
    const char* dependency,
    const char* pattern
) {
    glob_t glob_result;
    size_t i;
    memset(&glob_result, 0, sizeof(glob_result));
    qnn_direct_appendf(result_path, "QNN_DIRECT_DEP_SEARCH dependency=%s pattern=%s", dependency, pattern);
    if (glob(pattern, 0, NULL, &glob_result) != 0 || glob_result.gl_pathc == 0) {
        qnn_direct_appendf(result_path, "QNN_DIRECT_DEP_FILE dependency=%s pattern=%s exists=false", dependency, pattern);
        globfree(&glob_result);
        return;
    }
    for (i = 0; i < glob_result.gl_pathc; ++i) {
        qnn_direct_search_dependency_path(result_path, dependency, glob_result.gl_pathv[i]);
    }
    globfree(&glob_result);
}

static void qnn_direct_search_dependency(
    const char* result_path,
    const char* runtime_dir,
    const char* dependency
) {
    const char* dirs[] = {
        "/data/local/tmp/qairt",
        "/vendor/lib64",
        "/system/lib64",
        "/system_ext/lib64",
        "/apex/com.android.runtime/lib64",
        "/apex/com.android.i18n/lib64",
    };
    char path[QNN_DIRECT_MAX_PATH];
    char pattern[QNN_DIRECT_MAX_PATH];
    int i;

    qnn_direct_join_path(runtime_dir, dependency, path, sizeof(path));
    qnn_direct_search_dependency_path(result_path, dependency, path);

    for (i = 0; i < (int)(sizeof(dirs) / sizeof(dirs[0])); ++i) {
        qnn_direct_join_path(dirs[i], dependency, path, sizeof(path));
        qnn_direct_search_dependency_path(result_path, dependency, path);
    }

    snprintf(pattern, sizeof(pattern), "/apex/com.android.vndk.v*/lib64/%s", dependency);
    qnn_direct_search_dependency_glob(result_path, dependency, pattern);
}

static void qnn_direct_elf_dependency_diagnostics(
    const char* result_path,
    const char* runtime_dir
) {
    const char* target_names[] = {
        "libcdsprpc.so",
        "libQnnHtpV79Stub.so",
        "libQnnHtp.so",
        "libQnnHtpPrepare.so",
    };
    QnnDirectNeededList all_needed;
    int i;
    int has_vndk_dependency = 0;
    memset(&all_needed, 0, sizeof(all_needed));

    for (i = 0; i < (int)(sizeof(target_names) / sizeof(target_names[0])); ++i) {
        char path[QNN_DIRECT_MAX_PATH];
        qnn_direct_join_path(runtime_dir, target_names[i], path, sizeof(path));
        if (access(path, F_OK) != 0 && strcmp(target_names[i], "libcdsprpc.so") == 0) {
            if (access("/data/local/tmp/qairt/libcdsprpc.so", F_OK) == 0) {
                snprintf(path, sizeof(path), "%s", "/data/local/tmp/qairt/libcdsprpc.so");
            } else {
                snprintf(path, sizeof(path), "%s", "/vendor/lib64/libcdsprpc.so");
            }
        }
        qnn_direct_parse_elf_needed(result_path, path, &all_needed);
    }

    for (i = 0; i < all_needed.count; ++i) {
        if (qnn_direct_dependency_is_vndk_like(all_needed.entries[i])) {
            has_vndk_dependency = 1;
            qnn_direct_appendf(
                result_path,
                "QNN_DIRECT_NAMESPACE_CONCLUSION result=private-copy-needs-vndk-dependencies dependency=%s",
                all_needed.entries[i]
            );
        }
        qnn_direct_search_dependency(result_path, runtime_dir, all_needed.entries[i]);
    }

    if (has_vndk_dependency) {
        qnn_direct_append_line(
            result_path,
            "QNN_DIRECT_NAMESPACE_CONCLUSION result=simple-copy-insufficient"
        );
    }
}

static QnnDirectFastRpcStatus qnn_direct_fastrpc_diagnostics(
    const char* result_path,
    const char* runtime_dir
) {
    const char* fixed_candidates[] = {
        "/vendor/lib64/libcdsprpc.so",
        "/vendor/lib/libcdsprpc.so",
        "/system_ext/lib64/libcdsprpc.so",
        "/system_ext/lib/libcdsprpc.so",
        "/odm/lib64/libcdsprpc.so",
        "/odm/lib/libcdsprpc.so",
        "/data/local/tmp/qairt/libcdsprpc.so",
    };
    const int fixed_count = (int)(sizeof(fixed_candidates) / sizeof(fixed_candidates[0]));
    char runtime_candidate[QNN_DIRECT_MAX_PATH];
    QnnDirectFastRpcStatus status;
    int i;
    status.found = 0;
    status.loaded = 0;

    qnn_direct_append_line(result_path, "QNN_DIRECT_FASTRPC_DIAGNOSTICS_BEGIN library=libcdsprpc.so");
    qnn_direct_join_path(runtime_dir, "libcdsprpc.so", runtime_candidate, sizeof(runtime_candidate));

    for (i = 0; i <= fixed_count; ++i) {
        const char* path = i < fixed_count ? fixed_candidates[i] : runtime_candidate;
        void* handle;
        const char* error;
        int exists;
        qnn_direct_appendf(result_path, "QNN_DIRECT_FASTRPC_SEARCH index=%d path=%s", i, path);
        qnn_direct_append_fastrpc_file(result_path, path);
        exists = access(path, F_OK) == 0;
        if (exists) status.found = 1;
        dlerror();
        handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        error = qnn_direct_dlerror_text();
        if (handle != NULL) status.loaded = 1;
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_FASTRPC_DLOPEN path=%s success=%s handle=%p dlerror=%s",
            path,
            handle != NULL ? "true" : "false",
            handle,
            error
        );
    }

    if (!status.found) {
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_ERROR WARNING classification=missing-cdsprpc-likely found=%s loaded=%s",
            status.found ? "true" : "false",
            status.loaded ? "true" : "false"
        );
    } else if (!status.loaded) {
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_ERROR WARNING classification=permission-or-namespace-likely dependency=libcdsprpc.so found=true loaded=false"
        );
    }
    qnn_direct_appendf(
        result_path,
        "QNN_DIRECT_FASTRPC_DIAGNOSTICS_END found=%s loaded=%s",
        status.found ? "true" : "false",
        status.loaded ? "true" : "false"
    );
    return status;
}

static void qnn_direct_log_provider_words(
    const char* result_path,
    const char* label,
    const void* provider,
    uint32_t index
) {
    const uint32_t* words;
    void* const* pointers;
    if (provider == NULL) {
        qnn_direct_appendf(result_path, "QNN_DIRECT_PROVIDER label=%s index=%u provider=null", label, index);
        return;
    }
    words = (const uint32_t*)provider;
    pointers = (void* const*)provider;
    qnn_direct_appendf(
        result_path,
        "QNN_DIRECT_PROVIDER label=%s index=%u provider=%p rawU32_0=%u rawU32_1=%u rawU32_2=%u rawU32_3=%u possibleFunctionTable0=%p possibleFunctionTable1=%p",
        label,
        index,
        provider,
        words[0],
        words[1],
        words[2],
        words[3],
        pointers[1],
        pointers[2]
    );
}

static int qnn_direct_call_providers(
    const char* result_path,
    const char* last_run_path,
    const char* run_id,
    const char* label,
    void* symbol
) {
    typedef int (*QnnDirectGetProvidersFn)(const void*** providers, uint32_t* count);
    QnnDirectGetProvidersFn fn;
    const void** providers = NULL;
    uint32_t count = 0;
    uint32_t limit;
    uint32_t i;

    if (symbol == NULL) {
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_PROVIDER label=%s result=skipped reason=missing-symbol classification=dlsym-failure",
            label
        );
        return 20;
    }

    fn = (QnnDirectGetProvidersFn)symbol;
    qnn_direct_stage(result_path, last_run_path, run_id, "provider-call-before");
    int status = fn(&providers, &count);
    qnn_direct_stage(result_path, last_run_path, run_id, "provider-call-after");
    qnn_direct_appendf(
        result_path,
        "QNN_DIRECT_PROVIDER label=%s result=called status=%d count=%u providers=%p",
        label,
        status,
        count,
        providers
    );
    if (status != 0 || providers == NULL || count == 0) {
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_ERROR label=%s classification=provider-call-failure status=%d count=%u",
            label,
            status,
            count
        );
        return 21;
    }
    limit = count < 8 ? count : 8;
    for (i = 0; i < limit; ++i) {
        qnn_direct_log_provider_words(result_path, label, providers[i], i);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_ninbyo02_lami_debug_QnnDirectProbeNative_runProbe(
    JNIEnv* env,
    jobject unused,
    jstring runtime_dir_j,
    jstring probe_level_j,
    jstring result_path_j,
    jstring last_run_path_j,
    jstring run_id_j
) {
    char runtime_dir[QNN_DIRECT_MAX_PATH];
    char probe_level[64];
    char result_path[QNN_DIRECT_MAX_PATH];
    char last_run_path[QNN_DIRECT_MAX_PATH];
    char run_id[128];
    int result_code = 0;
    int qnn_system_ok = 0;
    int qnn_htp_ok = 0;
    int qnn_system_symbol_ok = 0;
    int qnn_htp_symbol_ok = 0;
    int dispatch_direct_ok = 0;
    QnnDirectFastRpcStatus fastrpc_status;
    int i;
    void* qnn_system_get_providers = NULL;
    void* qnn_backend_get_providers = NULL;
    void* litert_dispatch_get_api = NULL;
    QnnDirectLibrary libraries[] = {
        {"libQnnSystem.so", NULL, 0},
        {"libQnnHtp.so", NULL, 0},
        {"libLiteRtDispatch_Qualcomm.so", NULL, 0},
        {"libQnnHtpPrepare.so", NULL, 0},
        {"libQnnHtpV79Stub.so", NULL, 0},
        {"libQnnHtpV79Skel.so", NULL, 0},
    };
    const int library_count = (int)(sizeof(libraries) / sizeof(libraries[0]));

    (void)unused;
    qnn_direct_jstring_to_c(env, runtime_dir_j, runtime_dir, sizeof(runtime_dir));
    qnn_direct_jstring_to_c(env, probe_level_j, probe_level, sizeof(probe_level));
    qnn_direct_jstring_to_c(env, result_path_j, result_path, sizeof(result_path));
    qnn_direct_jstring_to_c(env, last_run_path_j, last_run_path, sizeof(last_run_path));
    qnn_direct_jstring_to_c(env, run_id_j, run_id, sizeof(run_id));

    qnn_direct_appendf(result_path, "QNN_DIRECT_STAGE nativeBegin runtimeDir=%s probeLevel=%s", runtime_dir, probe_level);
    qnn_direct_write_last_run(last_run_path, run_id, "native-begin", NULL);
    qnn_direct_elf_dependency_diagnostics(result_path, runtime_dir);
    fastrpc_status = qnn_direct_fastrpc_diagnostics(result_path, runtime_dir);

    for (i = 0; i < library_count; ++i) {
        char path[QNN_DIRECT_MAX_PATH];
        char stage_name[256];
        const char* error;
        qnn_direct_join_path(runtime_dir, libraries[i].name, path, sizeof(path));
        snprintf(stage_name, sizeof(stage_name), "dlopen-before-%s", libraries[i].name);
        qnn_direct_stage(result_path, last_run_path, run_id, stage_name);
        dlerror();
        libraries[i].handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        error = qnn_direct_dlerror_text();
        libraries[i].loaded = libraries[i].handle != NULL;
        snprintf(stage_name, sizeof(stage_name), "dlopen-after-%s", libraries[i].name);
        qnn_direct_stage(result_path, last_run_path, run_id, stage_name);
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_DLOPEN name=%s path=%s success=%s handle=%p dlerror=%s",
            libraries[i].name,
            path,
            libraries[i].loaded ? "true" : "false",
            libraries[i].handle,
            error
        );
        if (!libraries[i].loaded) {
            if (
                strcmp(libraries[i].name, "libLiteRtDispatch_Qualcomm.so") == 0 &&
                qnn_direct_contains(error, "LiteRtGetEnvironmentOptions")
            ) {
                qnn_direct_appendf(
                    result_path,
                    "QNN_DIRECT_ERROR WARNING name=%s classification=expected-direct-dispatch-dlopen-failure dlerror=%s",
                    libraries[i].name,
                    error
                );
            } else if (
                strcmp(libraries[i].name, "libQnnHtpV79Skel.so") == 0 &&
                (qnn_direct_contains(error, "32-bit") || qnn_direct_contains(error, "wrong ELF class"))
            ) {
                qnn_direct_appendf(
                    result_path,
                    "QNN_DIRECT_ERROR WARNING name=%s classification=expected-dsp-skel-not-host-dlopen dlerror=%s",
                    libraries[i].name,
                    error
                );
            } else if (
                strcmp(libraries[i].name, "libQnnHtpV79Stub.so") == 0 &&
                qnn_direct_contains(error, "libcdsprpc.so")
            ) {
                qnn_direct_appendf(
                    result_path,
                    "QNN_DIRECT_ERROR WARNING name=%s classification=missing-cdsprpc-likely dlerror=%s",
                    libraries[i].name,
                    error
                );
            } else if (
                strcmp(libraries[i].name, "libQnnHtpV79Stub.so") == 0 &&
                qnn_direct_contains(error, "libhidlbase.so")
            ) {
                qnn_direct_appendf(
                    result_path,
                    "QNN_DIRECT_ERROR WARNING name=%s classification=private-copy-needs-vndk-dependencies dependency=libhidlbase.so dlerror=%s",
                    libraries[i].name,
                    error
                );
                qnn_direct_append_line(
                    result_path,
                    "QNN_DIRECT_NAMESPACE_CONCLUSION result=simple-copy-insufficient"
                );
            } else {
                qnn_direct_appendf(
                    result_path,
                    "QNN_DIRECT_ERROR name=%s classification=dlopen-failure dlerror=%s",
                    libraries[i].name,
                    error
                );
                result_code = 10;
            }
        } else if (strcmp(libraries[i].name, "libQnnSystem.so") == 0) {
            qnn_system_ok = 1;
        } else if (strcmp(libraries[i].name, "libQnnHtp.so") == 0) {
            qnn_htp_ok = 1;
        } else if (strcmp(libraries[i].name, "libLiteRtDispatch_Qualcomm.so") == 0) {
            dispatch_direct_ok = 1;
        }
    }
    qnn_direct_append_maps(result_path, "after-dlopen");

    for (i = 0; i < library_count; ++i) {
        const char* symbol_name = NULL;
        void* symbol;
        const char* error;
        char stage_name[256];
        if (!libraries[i].loaded) continue;
        if (strcmp(libraries[i].name, "libQnnSystem.so") == 0) {
            symbol_name = "QnnSystemInterface_getProviders";
        } else if (strcmp(libraries[i].name, "libQnnHtp.so") == 0) {
            symbol_name = "QnnInterface_getProviders";
        } else if (strcmp(libraries[i].name, "libLiteRtDispatch_Qualcomm.so") == 0) {
            symbol_name = "LiteRtDispatchGetApi";
        }
        if (symbol_name == NULL) continue;

        snprintf(stage_name, sizeof(stage_name), "dlsym-before-%s", symbol_name);
        qnn_direct_stage(result_path, last_run_path, run_id, stage_name);
        dlerror();
        symbol = dlsym(libraries[i].handle, symbol_name);
        error = qnn_direct_dlerror_text();
        snprintf(stage_name, sizeof(stage_name), "dlsym-after-%s", symbol_name);
        qnn_direct_stage(result_path, last_run_path, run_id, stage_name);
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_DLSYM library=%s symbol=%s success=%s address=%p dlerror=%s",
            libraries[i].name,
            symbol_name,
            symbol != NULL ? "true" : "false",
            symbol,
            error
        );
        if (symbol == NULL) {
            if (strcmp(libraries[i].name, "libLiteRtDispatch_Qualcomm.so") == 0) {
                qnn_direct_appendf(
                    result_path,
                    "QNN_DIRECT_ERROR WARNING library=%s symbol=%s classification=expected-direct-dispatch-dlopen-failure dlerror=%s",
                    libraries[i].name,
                    symbol_name,
                    error
                );
            } else {
                qnn_direct_appendf(
                    result_path,
                    "QNN_DIRECT_ERROR library=%s symbol=%s classification=dlsym-failure dlerror=%s",
                    libraries[i].name,
                    symbol_name,
                    error
                );
                result_code = 11;
            }
        } else if (strcmp(symbol_name, "QnnSystemInterface_getProviders") == 0) {
            qnn_system_get_providers = symbol;
            qnn_system_symbol_ok = 1;
        } else if (strcmp(symbol_name, "QnnInterface_getProviders") == 0) {
            qnn_backend_get_providers = symbol;
            qnn_htp_symbol_ok = 1;
        } else if (strcmp(symbol_name, "LiteRtDispatchGetApi") == 0) {
            litert_dispatch_get_api = symbol;
            dispatch_direct_ok = 1;
        }
    }

    if (!qnn_direct_level_at_least(probe_level, "system")) {
        int symbols_ok = qnn_system_ok && qnn_htp_ok && qnn_system_symbol_ok && qnn_htp_symbol_ok;
        int symbols_code = symbols_ok ? 0 : result_code;
        qnn_direct_appendf(
            result_path,
            "QNN_DIRECT_RESULT result=%s level=symbols qnnSystem=%s qnnHtp=%s qnnSystemSymbol=%s qnnHtpSymbol=%s cdsprpc=%s dispatchDirect=%s nativeCode=%d",
            symbols_ok ? "SUCCESS_WITH_WARNINGS" : "FAILED",
            qnn_system_ok ? "true" : "false",
            qnn_htp_ok ? "true" : "false",
            qnn_system_symbol_ok ? "true" : "false",
            qnn_htp_symbol_ok ? "true" : "false",
            fastrpc_status.loaded ? "true" : "false",
            dispatch_direct_ok ? "true" : "false",
            symbols_code
        );
        qnn_direct_write_last_run(last_run_path, run_id, "native-end-symbols", symbols_ok ? "SUCCESS_WITH_WARNINGS" : "FAILED");
        return symbols_code;
    }

    int system_status = qnn_direct_call_providers(
        result_path,
        last_run_path,
        run_id,
        "QnnSystemInterface_getProviders",
        qnn_system_get_providers
    );
    if (system_status != 0) result_code = system_status;

    if (!qnn_direct_level_at_least(probe_level, "backend")) {
        qnn_direct_appendf(result_path, "QNN_DIRECT_RESULT nativeResult=complete level=system code=%d", result_code);
        qnn_direct_write_last_run(last_run_path, run_id, "native-end-system", result_code == 0 ? "SUCCESS" : "FAILED");
        return result_code;
    }

    int backend_status = qnn_direct_call_providers(
        result_path,
        last_run_path,
        run_id,
        "QnnInterface_getProviders",
        qnn_backend_get_providers
    );
    if (backend_status != 0) result_code = backend_status;

    qnn_direct_append_line(
        result_path,
        "QNN_DIRECT_BACKEND_CREATE result=skipped reason=function-table-layout-not-bound classification=api-version-mismatch-likely"
    );
    qnn_direct_appendf(
        result_path,
        "QNN_DIRECT_DLSYM library=libLiteRtDispatch_Qualcomm.so symbol=LiteRtDispatchGetApi cachedAddress=%p",
        litert_dispatch_get_api
    );

    if (!qnn_direct_level_at_least(probe_level, "device")) {
        qnn_direct_appendf(result_path, "QNN_DIRECT_RESULT nativeResult=complete level=backend code=%d", result_code);
        qnn_direct_write_last_run(last_run_path, run_id, "native-end-backend", result_code == 0 ? "SUCCESS" : "FAILED");
        return result_code;
    }

    qnn_direct_append_line(
        result_path,
        "QNN_DIRECT_DEVICE_CREATE result=skipped reason=backendCreate-skipped classification=api-version-mismatch-likely"
    );
    qnn_direct_appendf(result_path, "QNN_DIRECT_RESULT nativeResult=complete level=device code=%d", result_code);
    qnn_direct_write_last_run(last_run_path, run_id, "native-end-device", result_code == 0 ? "SUCCESS" : "FAILED");
    return result_code;
}
