package io.github.ninbyo02.lami.skills.web

import android.content.Context
import io.github.ninbyo02.lami.skills.AgentSkillDefinition
import io.github.ninbyo02.lami.skills.BundledAgentSkills
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class WebSearchRun(
    val reply: String,
    val skill: AgentSkillDefinition?,
    internal val epoch: Long,
    internal val requiresEnabled: Boolean,
)

/** The switch is checked at dispatch and again at commit. No work starts from a Flow collector. */
internal class WebSearchManager(
    private val preferences: WebSearchPreferenceStore,
    private val client: WebSearchClient,
    private val loadSkill: () -> AgentSkillDefinition,
    private val readAndroidReference: () -> String,
) {
    private val gate = Mutex()
    private var epoch = 0L
    private var activeJob: Job? = null
    private val enabledState = MutableStateFlow(preferences.enabled())
    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    companion object {
        @Volatile private var instance: WebSearchManager? = null
        fun get(context: Context): WebSearchManager = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val skills = BundledAgentSkills(app)
                WebSearchManager(WebSearchPreferences(app), DuckDuckGoWebSearchClient(),
                    { skills.load("web-search") }, { skills.readReference("web-search", "references/android.md") })
            }.also { instance = it }
        }
    }

    suspend fun setEnabled(value: Boolean) = withContext(Dispatchers.IO) {
        gate.withLock {
            // OFF takes effect in memory and cancels I/O even if disk persistence fails.
            epoch++
            if (!value) {
                enabledState.value = false
                activeJob?.cancel()
                activeJob = null
            }
            preferences.saveEnabled(value)
            enabledState.value = value
        }
    }

    suspend fun rejected(): WebSearchRun = gate.withLock {
        WebSearchRun(WebSearchIntentParser.REJECTED_REPLY, null, epoch, false)
    }

    suspend fun search(query: String): WebSearchRun = withContext(Dispatchers.IO) {
        if (!WebSearchIntentParser.validQuery(query)) return@withContext gate.withLock {
            WebSearchRun(WebSearchIntentParser.REJECTED_REPLY, null, epoch, false)
        }
        val job = currentCoroutineContext()[Job]
        val ticket = gate.withLock {
            if (!enabledState.value) return@withContext WebSearchRun(
                "Web検索はOFFです。設定の「Web検索（DuckDuckGo）」を有効にすると、明示した検索語だけを送信します。",
                null, epoch, false)
            epoch++
            activeJob?.cancel()
            activeJob = job
            epoch
        }
        var activated: AgentSkillDefinition? = null
        try {
            try {
                val definition = loadSkill()
                val reference = readAndroidReference()
                check(definition.name == "web-search" && definition.description.isNotBlank() && definition.body.isNotBlank() && reference.isNotBlank())
                activated = definition
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                return@withContext WebSearchRun("Web検索スキルを読み込めませんでした。検索は実行していません。", null, ticket, true)
            }
            currentCoroutineContext().ensureActive()
            gate.withLock { checkCurrent(ticket) }
            val response = client.search(query)
            currentCoroutineContext().ensureActive()
            gate.withLock { checkCurrent(ticket) }
            check(response.query == query)
            WebSearchRun(WebSearchFormatter.format(response), activated, ticket, true)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            WebSearchRun("Web検索の結果を取得できませんでした。別のサービスやサーバーモデルへの自動切替は行っていません。", activated, ticket, true)
        } finally {
            withContext(NonCancellable) {
                gate.withLock { if (activeJob === job) activeJob = null }
            }
        }
    }

    /** Serialize the last validity check with OFF so stale search data is never committed. */
    suspend fun commitIfCurrent(result: WebSearchRun, commit: suspend () -> Unit): Boolean = gate.withLock {
        currentCoroutineContext().ensureActive()
        if (result.epoch != epoch || (result.requiresEnabled && !enabledState.value)) return@withLock false
        commit()
        true
    }

    private fun checkCurrent(ticket: Long) {
        if (ticket != epoch || !enabledState.value) throw CancellationException("Web search cancelled")
    }
}
