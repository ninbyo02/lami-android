package com.sonusid.ollama.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sonusid.ollama.db.dao.ChatDao
import com.sonusid.ollama.db.dao.MessageDao
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.db.entity.TitleSource

@Database(entities = [Chat::class, Message::class], version = 6, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE user_table ADD COLUMN titleSource TEXT NOT NULL DEFAULT '${TitleSource.MANUAL}'"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_table ADD COLUMN attachmentUriString TEXT"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_table ADD COLUMN attachmentUriStringsJson TEXT"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 既存行の backfill は行わず、追加列は NULL のまま残す。
                // そのため統計値は v5 移行後に新規保存されたメッセージのみ保持される。
                database.execSQL("ALTER TABLE chat_table ADD COLUMN completionTokens INTEGER")
                database.execSQL("ALTER TABLE chat_table ADD COLUMN generationTimeMs INTEGER")
                database.execSQL("ALTER TABLE chat_table ADD COLUMN evalDurationNs INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_table ADD COLUMN modelName TEXT")
                database.execSQL("ALTER TABLE chat_table ADD COLUMN inputTokens INTEGER")
                database.execSQL("ALTER TABLE chat_table ADD COLUMN totalTokens INTEGER")
                database.execSQL("ALTER TABLE chat_table ADD COLUMN tokensPerSecond REAL")
                database.execSQL("ALTER TABLE chat_table ADD COLUMN inferenceTimeSec REAL")
            }
        }

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat-database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
