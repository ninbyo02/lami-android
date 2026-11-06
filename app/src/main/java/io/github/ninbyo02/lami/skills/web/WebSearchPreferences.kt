package io.github.ninbyo02.lami.skills.web

import android.content.Context

internal interface WebSearchPreferenceStore {
    fun enabled(): Boolean
    fun saveEnabled(enabled: Boolean)
}

/** One dedicated store, used only through the application-scoped WebSearchManager. */
internal class WebSearchPreferences(context: Context) : WebSearchPreferenceStore {
    private val preferences = context.applicationContext.getSharedPreferences("web_search_skill_v1", Context.MODE_PRIVATE)
    override fun enabled(): Boolean = try { preferences.getBoolean("enabled", false) } catch (_: RuntimeException) { false }
    override fun saveEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean("enabled", enabled).commit()) { "Web検索の設定を保存できませんでした。" }
    }
}
