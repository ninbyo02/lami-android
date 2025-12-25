package com.sonusid.ollama.db.repository

import com.sonusid.ollama.db.dao.ModelPreferenceDao
import com.sonusid.ollama.db.entity.SelectedModel

class ModelPreferenceRepository(private val modelPreferenceDao: ModelPreferenceDao) {
    suspend fun getSelectedModel(baseUrl: String): String? =
        modelPreferenceDao.getByBaseUrl(baseUrl)?.modelName

    suspend fun setSelectedModel(baseUrl: String, modelName: String) {
        modelPreferenceDao.upsert(SelectedModel(baseUrl = baseUrl, modelName = modelName))
    }

    suspend fun clearSelectedModel(baseUrl: String) {
        modelPreferenceDao.deleteByBaseUrl(baseUrl)
    }
}
