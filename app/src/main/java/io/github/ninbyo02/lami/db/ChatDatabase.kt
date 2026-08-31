package io.github.ninbyo02.lami.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.ninbyo02.lami.db.dao.ChatDao
import io.github.ninbyo02.lami.db.dao.MessageDao
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.TitleSource

@Database(entities = [Chat::class, Message::class], version = 14, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_table ADD COLUMN titleSource TEXT NOT NULL DEFAULT '${TitleSource.MANUAL}'"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE chat_table ADD COLUMN attachmentUriString TEXT"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE chat_table ADD COLUMN attachmentUriStringsJson TEXT"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 既存行の backfill は行わず、追加列は NULL のまま残す。
                // そのため統計値は v5 移行後に新規保存されたメッセージのみ保持される。
                db.execSQL("ALTER TABLE chat_table ADD COLUMN completionTokens INTEGER")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN generationTimeMs INTEGER")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN evalDurationNs INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_table ADD COLUMN modelName TEXT")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN inputTokens INTEGER")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN totalTokens INTEGER")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN tokensPerSecond REAL")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN inferenceTimeSec REAL")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_table ADD COLUMN finishReason TEXT")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN imageInputCount INTEGER")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_table ADD COLUMN timeToFirstTokenMs INTEGER")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_table ADD COLUMN loadDurationNs INTEGER")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN promptEvalDurationNs INTEGER")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_table ADD COLUMN generationDurationNs INTEGER")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_table ADD COLUMN localSourceSummary TEXT")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_table ADD COLUMN charsPerSecond REAL")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN tokenCountMode TEXT")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN inferenceNotes TEXT")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN decodeDurationMs INTEGER")
                db.execSQL("ALTER TABLE chat_table ADD COLUMN totalDurationMs INTEGER")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 既存履歴は正確な作成時刻を持たないため 0 のままにし、UI側で非表示にする。
                db.execSQL("ALTER TABLE chat_table ADD COLUMN createdAtEpochMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE chat_table ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'"
                )
                db.execSQL("ALTER TABLE chat_table ADD COLUMN errorCode TEXT")
                db.execSQL(
                    "ALTER TABLE chat_table ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE chat_table SET updatedAtEpochMs = createdAtEpochMs"
                )
            }
        }

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat-database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14
                    )
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
