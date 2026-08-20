package io.github.ninbyo02.lami.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import io.github.ninbyo02.lami.db.entity.MessageStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChatDatabaseMigration13To14Test {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun `migration preserves migrated v13 message and backfills completed lifecycle`() {
        createVersion13Database(createdAtHasDefault = true)
        openMigratedDatabaseAndAssertLegacyMessage()
    }

    @Test
    fun `migration accepts fresh v13 schema without created timestamp default`() {
        createVersion13Database(createdAtHasDefault = false)
        openMigratedDatabaseAndAssertLegacyMessage()
    }

    private fun openMigratedDatabaseAndAssertLegacyMessage() {
        val migrated = Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            TEST_DATABASE,
        )
            .addMigrations(ChatDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        try {
            migrated.openHelper.writableDatabase.query(
                """
                SELECT messageID, message, completionTokens, createdAtEpochMs,
                       status, errorCode, updatedAtEpochMs
                FROM chat_table
                WHERE messageID = 7
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("messageID")))
                assertEquals("legacy response", cursor.getString(cursor.getColumnIndexOrThrow("message")))
                assertEquals(42, cursor.getInt(cursor.getColumnIndexOrThrow("completionTokens")))
                assertEquals(123_456L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAtEpochMs")))
                assertEquals(MessageStatus.COMPLETED, cursor.getString(cursor.getColumnIndexOrThrow("status")))
                assertNull(cursor.getString(cursor.getColumnIndexOrThrow("errorCode")))
                assertEquals(123_456L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAtEpochMs")))
            }
        } finally {
            migrated.close()
        }
    }

    private fun createVersion13Database(createdAtHasDefault: Boolean) {
        val databaseFile = context.getDatabasePath(TEST_DATABASE)
        databaseFile.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        val createdAtColumn = if (createdAtHasDefault) {
            "createdAtEpochMs INTEGER NOT NULL DEFAULT 0"
        } else {
            "createdAtEpochMs INTEGER NOT NULL"
        }
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_table (
                chatId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                titleSource TEXT NOT NULL DEFAULT 'MANUAL'
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_table (
                messageID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatId INTEGER NOT NULL,
                message TEXT NOT NULL,
                isSendbyMe INTEGER NOT NULL,
                attachmentUriString TEXT,
                attachmentUriStringsJson TEXT,
                completionTokens INTEGER,
                generationTimeMs INTEGER,
                evalDurationNs INTEGER,
                modelName TEXT,
                inputTokens INTEGER,
                totalTokens INTEGER,
                tokensPerSecond REAL,
                inferenceTimeSec REAL,
                finishReason TEXT,
                imageInputCount INTEGER,
                timeToFirstTokenMs INTEGER,
                loadDurationNs INTEGER,
                promptEvalDurationNs INTEGER,
                generationDurationNs INTEGER,
                localSourceSummary TEXT,
                charsPerSecond REAL,
                tokenCountMode TEXT,
                inferenceNotes TEXT,
                decodeDurationMs INTEGER,
                totalDurationMs INTEGER,
                $createdAtColumn
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO user_table(chatId, title, titleSource)
            VALUES(1, 'legacy chat', 'MANUAL')
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO chat_table(
                messageID, chatId, message, isSendbyMe, completionTokens, createdAtEpochMs
            ) VALUES(7, 1, 'legacy response', 0, 42, 123456)
            """.trimIndent(),
        )
        database.version = 13
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "chat-migration-13-14-test"
    }
}
