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

@Database(entities = [Chat::class, Message::class], version = 3, exportSchema = false)
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

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat-database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
