package io.github.ninbyo02.lami.ui.screens.home

import java.io.File

/** Memoizes immutable installed-library diagnostics, never model data or inference results. */
internal class NativeFileDiagnosticCache<T>(private val cacheable: (T) -> Boolean = { true }) {
    private data class Stamp(val path: String, val size: Long, val modified: Long)
    private val entries = LinkedHashMap<Stamp, T>()

    @Synchronized
    fun read(file: File, compute: () -> T): T {
        if (!file.isFile) return compute()
        val stamp = Stamp(file.absolutePath, file.length(), file.lastModified())
        if (entries.containsKey(stamp)) return entries.getValue(stamp)
        val value = compute()
        if (cacheable(value) && file.isFile && file.length() == stamp.size && file.lastModified() == stamp.modified) {
            entries.keys.removeAll { it.path == stamp.path }
            if (entries.size >= 32) entries.remove(entries.keys.first())
            entries[stamp] = value
        }
        return value
    }
}
