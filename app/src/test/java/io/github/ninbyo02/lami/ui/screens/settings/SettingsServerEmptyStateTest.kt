package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.api.BaseUrlInitializationState
import io.github.ninbyo02.lami.db.dao.BaseUrlDao
import io.github.ninbyo02.lami.db.dao.ModelPreferenceDao
import io.github.ninbyo02.lami.db.entity.BaseUrl
import io.github.ninbyo02.lami.db.entity.SelectedModel
import io.github.ninbyo02.lami.db.repository.BaseUrlRepository
import io.github.ninbyo02.lami.db.repository.ModelPreferenceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsServerEmptyStateTest {
    @Test
    fun `empty server list has no active connection validations`() = runTest {
        val validations = validateActiveConnections(emptyList()) {
            error("No validation should run for an empty server list")
        }

        assertEquals(emptyMap<String, ConnectionValidationResult>(), validations)
    }

    @Test
    fun `save empty server list keeps repository empty`() = runTest {
        val baseUrlRepository = BaseUrlRepository(SettingsFakeBaseUrlDao(emptyList()))
        val modelPreferenceRepository = ModelPreferenceRepository(SettingsFakeModelPreferenceDao())

        saveServers(
            inputsToSave = emptyList(),
            baseUrlRepository = baseUrlRepository,
            modelPreferenceRepository = modelPreferenceRepository,
        ) { _, _ ->
            BaseUrlInitializationState(baseUrl = "", usedFallback = false)
        }

        assertEquals(emptyList<BaseUrl>(), baseUrlRepository.getAll())
        assertEquals("", baseUrlRepository.activeBaseUrl.value)
    }
}

private class SettingsFakeBaseUrlDao(initialBaseUrls: List<BaseUrl>) : BaseUrlDao {
    private var nextId = 1
    private val rows = mutableListOf<BaseUrl>()

    init {
        rows += initialBaseUrls
        nextId = (initialBaseUrls.maxOfOrNull { it.id } ?: 0) + 1
    }

    override suspend fun getAll(): List<BaseUrl> = rows.toList()
    override suspend fun getActive(): BaseUrl? = rows.firstOrNull { it.isActive }
    override suspend fun insert(baseUrl: BaseUrl): Long {
        val id = if (baseUrl.id > 0) baseUrl.id else nextId++
        rows += baseUrl.copy(id = id)
        return id.toLong()
    }
    override suspend fun insertAll(baseUrls: List<BaseUrl>): List<Long> = baseUrls.map { insert(it) }
    override suspend fun update(baseUrl: BaseUrl) {
        val index = rows.indexOfFirst { it.id == baseUrl.id }
        if (index >= 0) rows[index] = baseUrl
    }
    override suspend fun delete(baseUrl: BaseUrl) {
        rows.removeAll { it.id == baseUrl.id }
    }
    override suspend fun deleteById(id: Int) {
        rows.removeAll { it.id == id }
    }
    override suspend fun clear() {
        rows.clear()
    }
    override suspend fun clearActive() {
        rows.replaceAll { it.copy(isActive = false) }
    }
    override suspend fun activateById(id: Int) {
        rows.replaceAll { it.copy(isActive = it.id == id) }
    }
}

private class SettingsFakeModelPreferenceDao : ModelPreferenceDao {
    private val rows = linkedMapOf<String, SelectedModel>()
    override suspend fun getByBaseUrl(baseUrl: String): SelectedModel? = rows[baseUrl]
    override suspend fun upsert(model: SelectedModel) {
        rows[model.baseUrl] = model
    }
    override suspend fun deleteByBaseUrl(baseUrl: String) {
        rows.remove(baseUrl)
    }
    override suspend fun getAllBaseUrls(): List<String> = rows.keys.toList()
    override suspend fun deleteAllExcept(baseUrls: List<String>) {
        rows.keys.retainAll(baseUrls.toSet())
    }
    override suspend fun clearAll() {
        rows.clear()
    }
}
