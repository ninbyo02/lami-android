package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalImagePreparedFileCleanupTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun cancelBeforeDatabaseInsertDeletesOnlyThisRequestsPreparedFiles() {
        val source = files.newFile("source.jpg")
        val prepared = files.newFile("local_att_own.jpg")
        val input = LocalImageInput(prepared.absolutePath, prepared.toURI().toString())
        assertEquals(0, deleteUnpersistedLocalImageInputs(listOf(input), persistenceStarted = false))
        assertFalse(prepared.exists())
        assertTrue(source.exists())
    }

    @Test fun attemptedDatabaseInsertRetainsFilesEvenIfCommitOutcomeIsUnknown() {
        val prepared = files.newFile("local_att_committed_or_unknown.jpg")
        val input = LocalImageInput(prepared.absolutePath, prepared.toURI().toString())
        assertEquals(0, deleteUnpersistedLocalImageInputs(listOf(input), persistenceStarted = true))
        assertTrue(prepared.exists())
    }

    @Test fun alreadyRemovedPreparedFileDoesNotFailCleanup() {
        val prepared = files.newFile("local_att_removed.jpg")
        prepared.delete()
        assertEquals(0, deleteUnpersistedLocalImageInputs(
            listOf(LocalImageInput(prepared.absolutePath, prepared.toURI().toString())), false,
        ))
    }
}
