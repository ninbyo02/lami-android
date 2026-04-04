package io.github.ninbyo02.lami.db.repository

import io.github.ninbyo02.lami.db.dao.ModelPreferenceDao
import io.github.ninbyo02.lami.db.entity.SelectedModel

class ModelPreferenceRepository(private val modelPreferenceDao: ModelPreferenceDao) {
    suspend fun getSelectedModel(baseUrl: String): String? =
        modelPreferenceDao.getByBaseUrl(baseUrl)?.modelName

    suspend fun setSelectedModel(baseUrl: String, modelName: String) {
        modelPreferenceDao.upsert(SelectedModel(baseUrl = baseUrl, modelName = modelName))
    }

    suspend fun clearSelectedModel(baseUrl: String) {
        modelPreferenceDao.deleteByBaseUrl(baseUrl)
    }

    suspend fun pruneMissingBaseUrls(validBaseUrls: List<String>) {
        val uniqueBaseUrls = validBaseUrls.map { it.trimEnd('/') }.distinct()
        if (uniqueBaseUrls.isEmpty()) {
            modelPreferenceDao.clearAll()
            return
        }
        modelPreferenceDao.deleteAllExcept(uniqueBaseUrls)
    }
}
