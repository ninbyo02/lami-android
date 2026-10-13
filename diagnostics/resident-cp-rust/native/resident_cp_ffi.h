#ifndef LAMI_RESIDENT_CP_FFI_H
#define LAMI_RESIDENT_CP_FFI_H
#include <stddef.h>
#include <stdint.h>
/* Synchronous token handles, never raw pointers. Status 0/1/2/3/4 means
 * success/invalid argument/stale handle/backend error/internal error.
 * All caller storage is copied and never retained. Close waits for the worker.
 * Reset abandons a partial frame and clears KV; failed execution requires close
 * and a fresh context. Cancel is between steps, not graph-execution preemption. */
int32_t lami_cp_create(const uint8_t *, size_t, const char *, const char *, const char *, uint64_t *);
int32_t lami_cp_reset(uint64_t);
int32_t lami_cp_step(uint64_t, int32_t, const float *, float *, size_t);
int32_t lami_cp_cancel(uint64_t);
int32_t lami_cp_release(uint64_t);
int32_t lami_cp_stats(uint64_t, uint64_t *, size_t);
#endif
