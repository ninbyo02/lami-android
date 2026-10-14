/* QNN structures are compiled from the installed official SDK, never mirrored in Rust. */
#include <QnnInterface.h>
#include <System/QnnSystemInterface.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <stdarg.h>
#include <pthread.h>
#ifdef __ANDROID__
#include <android/log.h>
#define LAMI_QNN_ERROR(...) __android_log_print(ANDROID_LOG_ERROR, "LamiCpQnnBridge", __VA_ARGS__)
#else
#define LAMI_QNN_ERROR(...) fprintf(stderr, __VA_ARGS__)
#endif


#if QNN_API_VERSION_MAJOR != 2 || QNN_API_VERSION_MINOR != 36
#error This prototype requires the verified QAIRT 2.47 API headers.
#endif

/* Bounded diagnostic receipt; never stores tensors, paths, user text or audio. */
static pthread_mutex_t g_log_mutex = PTHREAD_MUTEX_INITIALIZER;
static char g_log_receipt[8192];
static size_t g_log_used = 0;

void lami_qnn_log_clear(void) {
    pthread_mutex_lock(&g_log_mutex);
    g_log_used = 0;
    g_log_receipt[0] = '\0';
    pthread_mutex_unlock(&g_log_mutex);
}
void lami_qnn_log_read(char *out, size_t capacity) {
    if (!out || capacity == 0) return;
    pthread_mutex_lock(&g_log_mutex);
    snprintf(out, capacity, "%s", g_log_receipt);
    pthread_mutex_unlock(&g_log_mutex);
}
static void lami_qnn_log_append(int level, const char *message) {
    pthread_mutex_lock(&g_log_mutex);
    if (g_log_used < sizeof(g_log_receipt) - 1) {
        int written = snprintf(g_log_receipt + g_log_used, sizeof(g_log_receipt) - g_log_used,
                               "L%d: %.300s\n", level, message);
        if (written > 0) {
            size_t available = sizeof(g_log_receipt) - 1 - g_log_used;
            g_log_used += (size_t)written > available ? available : (size_t)written;
        }
    }
    pthread_mutex_unlock(&g_log_mutex);
}
typedef struct { const char *name; void *data; uint32_t bytes; } LamiBuffer;
/* Exact rpcmem signatures/heap/flags from SDK 2.47 SampleAppSharedBuffer IOTensor.hpp. */
typedef void *(*RpcAlloc)(int, uint32_t, int);
typedef void (*RpcFree)(void *);
typedef int (*RpcToFd)(void *);
typedef struct { void *original, *mapped; uint32_t bytes; Qnn_MemHandle_t handle; } SharedBuffer;
typedef struct {
    void *backend_library, *system_library;
    QNN_INTERFACE_VER_TYPE api;
    QNN_SYSTEM_INTERFACE_VER_TYPE system_api;
    Qnn_LogHandle_t logger;
    Qnn_BackendHandle_t backend;
    Qnn_DeviceHandle_t device;
    Qnn_ContextHandle_t context;
    QnnSystemContext_Handle_t system_context;
    Qnn_GraphHandle_t graph;
    Qnn_Tensor_t inputs[13], outputs[11];
    void *rpc_library;
    RpcAlloc rpc_alloc;
    RpcFree rpc_free;
    RpcToFd rpc_to_fd;
    SharedBuffer shared[24];
    uint64_t registered_count, sdk_graph_ns, input_copy_ns, output_copy_ns;
} LamiSession;

#define LOCAL_ERROR UINT64_C(0xffff000000000001)

void lami_qnn_close(LamiSession *s) {
    if (!s) return;
    for (uint32_t i = 0; i < 24; ++i) {
        if (s->shared[i].handle && s->api.memDeRegister)
            s->api.memDeRegister(&s->shared[i].handle, 1);
    }
    if (s->context && s->api.contextFree) s->api.contextFree(s->context, NULL);
    /* Keep backing memory alive through context release, even if deregistration fails. */
    for (uint32_t i = 0; i < 24; ++i)
        if (s->shared[i].mapped && s->rpc_free) s->rpc_free(s->shared[i].mapped);
    if (s->device && s->api.deviceFree) s->api.deviceFree(s->device);
    if (s->backend && s->api.backendFree) s->api.backendFree(s->backend);
    if (s->logger && s->api.logFree) s->api.logFree(s->logger);
    if (s->system_context && s->system_api.systemContextFree)
        s->system_api.systemContextFree(s->system_context);
    if (s->backend_library) dlclose(s->backend_library);
    if (s->system_library) dlclose(s->system_library);
    if (s->rpc_library) dlclose(s->rpc_library);
    free(s);
}

static void lami_cp_qnn_log(const char *fmt, QnnLog_Level_t level, uint64_t timestamp, va_list args) {
    (void)timestamp;
    char message[512];
    vsnprintf(message, sizeof(message), fmt, args);
    LAMI_QNN_ERROR("sdk_level=%d %s", (int)level, message);
    lami_qnn_log_append((int)level, message);
}

static uint64_t now_ns(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (uint64_t)t.tv_sec * UINT64_C(1000000000) + (uint64_t)t.tv_nsec;
}

static uint64_t system_provider(LamiSession *s, const char *system) {
    s->system_library = dlopen(system, RTLD_NOW | RTLD_LOCAL);
    if (!s->system_library) { LAMI_QNN_ERROR("lami_cp_dlopen_system_error=%s\n", dlerror()); return LOCAL_ERROR; }
    __typeof__(&QnnSystemInterface_getProviders) get_system =
        (__typeof__(&QnnSystemInterface_getProviders))dlsym(s->system_library, "QnnSystemInterface_getProviders");
    if (!get_system) return LOCAL_ERROR;
    const QnnSystemInterface_t **systems = NULL;
    uint32_t ns = 0;
    uint64_t status = get_system(&systems, &ns);
    if (status) return status;
    if (!systems || !ns) return LOCAL_ERROR;
    for (uint32_t i = 0; i < ns; ++i)
        if (systems[i] && systems[i]->systemApiVersion.major == QNN_SYSTEM_API_VERSION_MAJOR &&
            systems[i]->systemApiVersion.minor == QNN_SYSTEM_API_VERSION_MINOR)
            s->system_api = systems[i]->QNN_SYSTEM_INTERFACE_VER_NAME;
    return s->system_api.systemContextCreate && s->system_api.systemContextGetBinaryInfo &&
        s->system_api.systemContextFree ? 0 : LOCAL_ERROR;
}

static uint64_t providers(LamiSession *s, const char *backend, const char *system) {
    uint64_t status = system_provider(s, system);
    if (status) return status;
    s->backend_library = dlopen(backend, RTLD_NOW | RTLD_LOCAL);
    if (!s->backend_library) { LAMI_QNN_ERROR("lami_cp_dlopen_backend_error=%s\n", dlerror()); return LOCAL_ERROR; }
    __typeof__(&QnnInterface_getProviders) get_backend =
        (__typeof__(&QnnInterface_getProviders))dlsym(s->backend_library, "QnnInterface_getProviders");
    if (!get_backend) return LOCAL_ERROR;
    const QnnInterface_t **backends = NULL;
    uint32_t nb = 0;
    status = get_backend(&backends, &nb);
    if (status) return status;
    if (!backends || !nb) return LOCAL_ERROR;
    for (uint32_t i = 0; i < nb; ++i)
        if (backends[i] && backends[i]->apiVersion.coreApiVersion.major == QNN_API_VERSION_MAJOR &&
            backends[i]->apiVersion.coreApiVersion.minor == QNN_API_VERSION_MINOR)
            s->api = backends[i]->QNN_INTERFACE_VER_NAME;
    if (!s->api.backendCreate || !s->api.deviceCreate || !s->api.contextCreateFromBinary ||
        !s->api.graphRetrieve || !s->api.graphExecute || !s->api.backendGetBuildId ||
        !s->system_api.systemContextCreate || !s->system_api.systemContextGetBinaryInfo) return LOCAL_ERROR;
    const char *build = NULL;
    status = s->api.backendGetBuildId(&build);
    if (status) return status;
    return build && strncmp(build, "v2.47.0.260601", 14) == 0 ? 0 : LOCAL_ERROR;
}

static void *bound_data(const Qnn_Tensor_t *tensor) {
    return tensor->version == QNN_TENSOR_VERSION_1 ? tensor->v1.clientBuf.data : tensor->v2.clientBuf.data;
}

static int bind(Qnn_Tensor_t *tensor, const LamiBuffer *buffers, uint32_t count) {
    const char *name;
    uint32_t rank, *dims;
    Qnn_DataType_t dtype;
    if (tensor->version == QNN_TENSOR_VERSION_1) {
        name = tensor->v1.name; rank = tensor->v1.rank; dims = tensor->v1.dimensions; dtype = tensor->v1.dataType;
    } else if (tensor->version == QNN_TENSOR_VERSION_2) {
        name = tensor->v2.name; rank = tensor->v2.rank; dims = tensor->v2.dimensions; dtype = tensor->v2.dataType;
        if (tensor->v2.isDynamicDimensions)
            for (uint32_t i = 0; i < rank; ++i) if (tensor->v2.isDynamicDimensions[i]) return 0;
    } else return 0;
    if (!name || !dims || rank == 0 || rank > 4) return 0;
    const LamiBuffer *buffer = NULL;
    for (uint32_t i = 0; i < count; ++i)
        if (buffers[i].name && strcmp(name, buffers[i].name) == 0) {
            if (buffer) return 0;
            buffer = &buffers[i];
        }
    if (!buffer || !buffer->data) return 0;
    uint64_t elements = 1;
    for (uint32_t i = 0; i < rank; ++i) {
        if (!dims[i] || elements > UINT32_MAX / dims[i]) return 0;
        elements *= dims[i];
    }
    if (elements * 4 != buffer->bytes) return 0;
    int is_id = strcmp(name, "serving_default_input_ids") == 0;
    if (dtype != (is_id ? QNN_DATATYPE_INT_32 : QNN_DATATYPE_FLOAT_32)) return 0;
    if (is_id && (rank != 1 || dims[0] != 1)) return 0;
    if (strstr(name, "kv_cache_") &&
        (rank != 4 || dims[0] != 1 || dims[1] != 32 || dims[2] != 8 || dims[3] != 128)) return 0;
    if (strstr(name, "embeddings") && (rank != 3 || dims[0] != 1 || dims[1] != 1 || dims[2] != 1024)) return 0;
    if (strstr(name, "hidden") && (rank != 2 || dims[0] != 1 || dims[1] != 1024)) return 0;
    if (strstr(name, "mask") && (rank != 4 || dims[0] != 1 || dims[1] != 1 || dims[2] != 1 || dims[3] != 32)) return 0;
    Qnn_ClientBuffer_t client = { buffer->data, buffer->bytes };
    if (tensor->version == QNN_TENSOR_VERSION_1) {
        tensor->v1.memType = QNN_TENSORMEMTYPE_RAW; tensor->v1.clientBuf = client;
    } else {
        tensor->v2.memType = QNN_TENSORMEMTYPE_RAW; tensor->v2.clientBuf = client;
    }
    return 1;
}

static uint64_t graph_metadata(LamiSession *s, void *binary, size_t bytes,
        const LamiBuffer *inputs, const LamiBuffer *outputs) {
    uint64_t status = s->system_api.systemContextCreate(&s->system_context);
    if (status) return status;
    const QnnSystemContext_BinaryInfo_t *info = NULL;
    Qnn_ContextBinarySize_t info_size = 0;
    status = s->system_api.systemContextGetBinaryInfo(s->system_context, binary, bytes, &info, &info_size);
    if (status || !info) return status ? status : LOCAL_ERROR;
    uint32_t count;
    QnnSystemContext_GraphInfo_t *graphs;
    switch (info->version) {
        case QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_1:
            count = info->contextBinaryInfoV1.numGraphs; graphs = info->contextBinaryInfoV1.graphs; break;
        case QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_2:
            count = info->contextBinaryInfoV2.numGraphs; graphs = info->contextBinaryInfoV2.graphs; break;
        case QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_3:
            count = info->contextBinaryInfoV3.numGraphs; graphs = info->contextBinaryInfoV3.graphs; break;
        default: return LOCAL_ERROR;
    }
    if (count != 1 || !graphs) return LOCAL_ERROR;
    const char *name;
    uint32_t ni, no;
    Qnn_Tensor_t *ti, *to;
#define GRAPH_FIELDS(v) name = graphs[0].v.graphName; ni = graphs[0].v.numGraphInputs; \
    no = graphs[0].v.numGraphOutputs; ti = graphs[0].v.graphInputs; to = graphs[0].v.graphOutputs
    switch (graphs[0].version) {
        case QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_1: GRAPH_FIELDS(graphInfoV1); break;
        case QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_2: GRAPH_FIELDS(graphInfoV2); break;
        case QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_3: GRAPH_FIELDS(graphInfoV3); break;
        default: return LOCAL_ERROR;
    }
#undef GRAPH_FIELDS
    if (!name || strcmp(name, "qnn_partition_0") || ni != 13 || no != 11 || !ti || !to) return LOCAL_ERROR;
    memcpy(s->inputs, ti, sizeof(s->inputs)); memcpy(s->outputs, to, sizeof(s->outputs));
    for (uint32_t i = 0; i < ni; ++i) {
        if (!bind(&s->inputs[i], inputs, ni)) return LOCAL_ERROR;
        for (uint32_t j = 0; j < i; ++j)
            if (bound_data(&s->inputs[i]) == bound_data(&s->inputs[j])) return LOCAL_ERROR;
    }
    for (uint32_t i = 0; i < no; ++i) {
        if (!bind(&s->outputs[i], outputs, no)) return LOCAL_ERROR;
        for (uint32_t j = 0; j < ni; ++j)
            if (bound_data(&s->outputs[i]) == bound_data(&s->inputs[j])) return LOCAL_ERROR;
        for (uint32_t j = 0; j < i; ++j)
            if (bound_data(&s->outputs[i]) == bound_data(&s->outputs[j])) return LOCAL_ERROR;
    }
    return 0;
}

static uint64_t prepare_rpc(LamiSession *s, const char *library) {
    s->rpc_library = dlopen(library, RTLD_NOW | RTLD_LOCAL);
    if (!s->rpc_library) { LAMI_QNN_ERROR("lami_cp_dlopen_rpc_error=%s\n", dlerror()); return LOCAL_ERROR; }
    s->rpc_alloc = (RpcAlloc)dlsym(s->rpc_library, "rpcmem_alloc");
    s->rpc_free = (RpcFree)dlsym(s->rpc_library, "rpcmem_free");
    s->rpc_to_fd = (RpcToFd)dlsym(s->rpc_library, "rpcmem_to_fd");
    if (!s->rpc_alloc || !s->rpc_free || !s->rpc_to_fd) return LOCAL_ERROR;
    return 0;
}

static uint64_t register_shared(LamiSession *s) {
    if (!s->api.memRegister || !s->api.memDeRegister || !s->rpc_library ||
        !s->rpc_alloc || !s->rpc_free || !s->rpc_to_fd) return LOCAL_ERROR;
    for (uint32_t i = 0; i < 24; ++i) {
        Qnn_Tensor_t *t = i < 13 ? &s->inputs[i] : &s->outputs[i-13];
        SharedBuffer *b = &s->shared[i];
        Qnn_MemDescriptor_t descriptor = QNN_MEM_DESCRIPTOR_INIT;
        if (t->version == QNN_TENSOR_VERSION_1) {
            b->original = t->v1.clientBuf.data; b->bytes = t->v1.clientBuf.dataSize;
            descriptor.memShape.numDim = t->v1.rank; descriptor.memShape.dimSize = t->v1.dimensions;
            descriptor.dataType = t->v1.dataType;
        } else {
            b->original = t->v2.clientBuf.data; b->bytes = t->v2.clientBuf.dataSize;
            descriptor.memShape.numDim = t->v2.rank; descriptor.memShape.dimSize = t->v2.dimensions;
            descriptor.dataType = t->v2.dataType;
        }
        b->mapped = s->rpc_alloc(25, 1, (int)b->bytes);
        if (!b->mapped) return LOCAL_ERROR;
        memset(b->mapped, 0, b->bytes);
        descriptor.memType = QNN_MEM_TYPE_ION;
        descriptor.ionInfo.fd = s->rpc_to_fd(b->mapped);
        if (descriptor.ionInfo.fd < 0) return LOCAL_ERROR;
        uint64_t status = s->api.memRegister(s->context, &descriptor, 1, &b->handle);
        if (status) return status;
        if (t->version == QNN_TENSOR_VERSION_1) {
            t->v1.memType = QNN_TENSORMEMTYPE_MEMHANDLE; t->v1.memHandle = b->handle;
        } else {
            t->v2.memType = QNN_TENSORMEMTYPE_MEMHANDLE; t->v2.memHandle = b->handle;
        }
        s->registered_count += 1;
    }
    return 0;
}

uint64_t lami_qnn_open(const char *backend, const char *system, const char *rpc_library, void *binary, size_t bytes,
        const LamiBuffer *inputs, const LamiBuffer *outputs, LamiSession **result) {
    lami_qnn_log_clear();
    if (!result) return LOCAL_ERROR;
    *result = NULL;
    if (!backend || !system || !binary || !bytes || !inputs || !outputs) return LOCAL_ERROR;
    LamiSession *s = calloc(1, sizeof(*s));
    if (!s) return LOCAL_ERROR;
    const char *stage = "rpc_loader";
    uint64_t status = rpc_library ? prepare_rpc(s, rpc_library) : 0;
    if (!status) { stage = "providers"; status = providers(s, backend, system); }
    if (!status) { stage = "metadata"; status = graph_metadata(s, binary, bytes, inputs, outputs); }
    if (!status) {
        stage = "logger";
        status = !s->api.logCreate ? LOCAL_ERROR :
            s->api.logCreate(lami_cp_qnn_log, QNN_LOG_LEVEL_VERBOSE, &s->logger);
    }
    if (!status) { stage = "backend"; status = s->api.backendCreate(s->logger, NULL, &s->backend); }
    if (!status) { stage = "device"; status = s->api.deviceCreate(s->logger, NULL, &s->device); }
    if (!status) { stage = "context"; status = s->api.contextCreateFromBinary(s->backend, s->device, NULL, binary, bytes, &s->context, NULL); }
    if (!status) { stage = "graph"; status = s->api.graphRetrieve(s->context, "qnn_partition_0", &s->graph); }
    if (!status && rpc_library) { stage = "registered_buffers"; status = register_shared(s); }
    if (status) {
        LAMI_QNN_ERROR("lami_cp_open_error_stage=%s status=0x%llx\n", stage, (unsigned long long)status);
        char summary[120];
        snprintf(summary, sizeof(summary), "stage=%s sdk_status=%llu", stage, (unsigned long long)status);
        lami_qnn_log_append(1, summary);
        // Stage is carried through the FFI result even when Android suppresses native stderr/logcat.
        const char *names[] = { "rpc_loader", "providers", "metadata", "backend",
                                "device", "context", "graph", "registered_buffers", "logger" };
        uint64_t stage_code = 0;
        for (uint64_t i = 0; i < 9; ++i) if (strcmp(stage, names[i]) == 0) stage_code = i + 1;
        lami_qnn_close(s);
        return (stage_code << 56) | (status & UINT64_C(0x00ffffffffffffff));
    }
    *result = s;
    return 0;
}

uint64_t lami_qnn_execute(LamiSession *s) {
    if (!s || !s->context || !s->graph) return LOCAL_ERROR;
    uint64_t started;
    if (s->registered_count) {
        started = now_ns();
        for (uint32_t i = 0; i < 13; ++i) memcpy(s->shared[i].mapped, s->shared[i].original, s->shared[i].bytes);
        s->input_copy_ns += now_ns()-started;
    }
    started = now_ns();
    uint64_t status = s->api.graphExecute(s->graph, s->inputs, 13, s->outputs, 11, NULL, NULL);
    s->sdk_graph_ns += now_ns()-started;
    if (!status && s->registered_count) {
        started = now_ns();
        for (uint32_t i = 13; i < 24; ++i) memcpy(s->shared[i].original, s->shared[i].mapped, s->shared[i].bytes);
        s->output_copy_ns += now_ns()-started;
    }
    return status;
}

void lami_qnn_memory_stats(LamiSession *s, uint64_t stats[4]) {
    if (!s || !stats) return;
    stats[0] = s->registered_count; stats[1] = s->sdk_graph_ns;
    stats[2] = s->input_copy_ns; stats[3] = s->output_copy_ns;
}

uint64_t lami_qnn_validate_metadata(const char *system, void *binary, size_t bytes,
        const LamiBuffer *inputs, const LamiBuffer *outputs) {
    if (!system || !binary || !bytes || !inputs || !outputs) return LOCAL_ERROR;
    LamiSession *s = calloc(1, sizeof(*s));
    if (!s) return LOCAL_ERROR;
    uint64_t status = system_provider(s, system);
    if (!status) status = graph_metadata(s, binary, bytes, inputs, outputs);
    lami_qnn_close(s);
    return status;
}
