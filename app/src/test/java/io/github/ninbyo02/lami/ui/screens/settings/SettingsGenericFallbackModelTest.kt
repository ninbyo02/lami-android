package io.github.ninbyo02.lami.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsGenericFallbackModelTest {
    @Test
    fun `generic fallback model DataStore keys are separate from NPU preview model keys`() {
        assertEquals(
            "local_generic_model_display_name",
            LOCAL_GENERIC_MODEL_DISPLAY_NAME_DATASTORE_KEY,
        )
        assertEquals(
            "local_generic_model_file_path",
            LOCAL_GENERIC_MODEL_FILE_PATH_DATASTORE_KEY,
        )
        assertEquals(
            "local_base_model_display_name",
            LOCAL_BASE_MODEL_DISPLAY_NAME_DATASTORE_KEY,
        )
        assertEquals(
            "local_base_model_file_path",
            LOCAL_BASE_MODEL_FILE_PATH_DATASTORE_KEY,
        )
    }
}
