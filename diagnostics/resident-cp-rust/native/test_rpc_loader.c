/* PC-only full-entry-point test. All loader calls are mocked; no SDK runtime is loaded. */
#include <assert.h>

#define dlopen mock_dlopen
#define dlsym mock_dlsym
#define dlclose mock_dlclose
#define dlerror mock_dlerror
#include "qnn_bridge.c"

enum Failure { RPC_OPEN, RPC_ALLOC_SYMBOL, RPC_FREE_SYMBOL, RPC_FD_SYMBOL, SYSTEM_OPEN, SYSTEM_PROVIDER };
static enum Failure failure;
static int rpc_handle, system_handle;
static unsigned rpc_opens, system_opens, backend_opens, provider_calls;
static unsigned rpc_closes, system_closes, allocator_calls;

static void *mock_alloc(int heap, uint32_t flags, int bytes) {
    (void)heap; (void)flags; (void)bytes;
    allocator_calls++; return NULL;
}
static void mock_free(void *memory) { (void)memory; allocator_calls++; }
static int mock_to_fd(void *memory) { (void)memory; allocator_calls++; return -1; }
static Qnn_ErrorHandle_t mock_system_providers(const QnnSystemInterface_t ***providers, uint32_t *count) {
    (void)providers; (void)count;
    provider_calls++; return UINT64_C(0x42);
}

void *mock_dlopen(const char *name, int flags) {
    assert(flags == (RTLD_NOW | RTLD_LOCAL));
    if (!strcmp(name, "libcdsprpc.so")) {
        rpc_opens++;
        return failure == RPC_OPEN ? NULL : &rpc_handle;
    }
    if (!strcmp(name, "libQnnSystem.so")) {
        system_opens++;
        return failure == SYSTEM_OPEN ? NULL : &system_handle;
    }
    assert(!strcmp(name, "libQnnHtp.so"));
    backend_opens++; return NULL;
}
void *mock_dlsym(void *handle, const char *name) {
    if (handle == &system_handle) {
        assert(!strcmp(name, "QnnSystemInterface_getProviders"));
        return (void *)mock_system_providers;
    }
    assert(handle == &rpc_handle);
    if (!strcmp(name, "rpcmem_alloc")) return failure == RPC_ALLOC_SYMBOL ? NULL : (void *)mock_alloc;
    if (!strcmp(name, "rpcmem_free")) return failure == RPC_FREE_SYMBOL ? NULL : (void *)mock_free;
    assert(!strcmp(name, "rpcmem_to_fd"));
    return failure == RPC_FD_SYMBOL ? NULL : (void *)mock_to_fd;
}
int mock_dlclose(void *handle) {
    if (handle == &rpc_handle) rpc_closes++;
    else { assert(handle == &system_handle); system_closes++; }
    return 0;
}
char *mock_dlerror(void) { return "mocked loader failure"; }

static void check(enum Failure scenario) {
    failure = scenario;
    rpc_opens = system_opens = backend_opens = provider_calls = 0;
    rpc_closes = system_closes = allocator_calls = 0;
    unsigned char binary = 0;
    LamiBuffer inputs[13] = {{0}}, outputs[11] = {{0}};
    LamiSession *session = (LamiSession *)(uintptr_t)1;
    assert(lami_qnn_open("libQnnHtp.so", "libQnnSystem.so", "libcdsprpc.so", &binary, 1,
                         inputs, outputs, &session) != 0);
    assert(session == NULL);
    assert(rpc_opens == 1 && backend_opens == 0 && allocator_calls == 0);
    if (scenario <= RPC_FD_SYMBOL) {
        /* Stronger than zero graph calls: no SDK library/provider can be reached. */
        assert(system_opens == 0 && provider_calls == 0 && system_closes == 0);
    } else {
        assert(system_opens == 1);
        assert(provider_calls == (scenario == SYSTEM_PROVIDER ? 1U : 0U));
        assert(system_closes == (scenario == SYSTEM_PROVIDER ? 1U : 0U));
    }
    assert(rpc_closes == (scenario == RPC_OPEN ? 0U : 1U));
}
int main(void) {
    for (enum Failure scenario = RPC_OPEN; scenario <= SYSTEM_PROVIDER; scenario++) check(scenario);
    puts("RPC loader: 6 full-entry-point ordering/missing-symbol/partial-cleanup cases passed; no SDK runtime or inference");
    return 0;
}
