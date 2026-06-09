package com.companion.chat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.companion.chat.data.local.dao.ConversationDao
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.dao.MessageDao
import com.companion.chat.data.local.dao.PreferenceDao
import com.companion.chat.data.local.dao.RoleCardDao
import com.companion.chat.data.local.dao.SkillDao
import com.companion.chat.data.local.entity.ConversationEntity
import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.MessageEntity
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.local.entity.Skill
import com.companion.chat.data.local.entity.UserPreference
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        Memory::class,
        UserPreference::class,
        Skill::class,
        RoleCard::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CompanionDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun skillDao(): SkillDao
    abstract fun roleCardDao(): RoleCardDao

    companion object {
        private const val DATABASE_NAME = "companion_chat.db"

        @Volatile
        private var instance: CompanionDatabase? = null

        fun getInstance(context: Context): CompanionDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CompanionDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(DatabaseInitializationCallback())
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS role_cards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        avatar TEXT NOT NULL,
                        persona TEXT NOT NULL,
                        speakingStyle TEXT NOT NULL,
                        background TEXT NOT NULL,
                        rules TEXT NOT NULL,
                        taboos TEXT NOT NULL,
                        openingMessage TEXT NOT NULL,
                        exampleDialogue TEXT NOT NULL,
                        isBuiltIn INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    DELETE FROM skills
                    WHERE isBuiltIn = 1 AND name IN ('通用助手', '代码助手', '写作助手')
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    UPDATE skills
                    SET description = '考虑语境、文化和母语差异的专业翻译',
                        systemPrompt = '你是一个专业的翻译助手。请根据使用者的语境、文化背景以及母语情况，给出准确、自然、符合目标表达习惯的翻译结果；在保持原意的前提下，优先保证易懂、得体和语用自然。',
                        updatedAt = ${'$'}{System.currentTimeMillis()}
                    WHERE name = '翻译助手'
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO skills(
                        id, name, description, systemPrompt, icon,
                        isBuiltIn, isActive, usageCount, createdAt, updatedAt
                    ) VALUES (
                        2,
                        '翻译助手',
                        '考虑语境、文化和母语差异的专业翻译',
                        '你是一个专业的翻译助手。请根据使用者的语境、文化背景以及母语情况，给出准确、自然、符合目标表达习惯的翻译结果；在保持原意的前提下，优先保证易懂、得体和语用自然。',
                        'translate',
                        1,
                        1,
                        0,
                        ${'$'}{System.currentTimeMillis()},
                        ${'$'}{System.currentTimeMillis()}
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    UPDATE skills
                    SET isActive = 1
                    WHERE name = '翻译助手'
                      AND NOT EXISTS (SELECT 1 FROM skills WHERE isActive = 1)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE role_cards ADD COLUMN avatarImageUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE role_cards ADD COLUMN galleryImageUris TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE role_cards ADD COLUMN imageStylePrompt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE role_cards ADD COLUMN voiceProfileUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE role_cards ADD COLUMN voiceMode TEXT NOT NULL DEFAULT 'SYSTEM_TTS'")
                db.execSQL("ALTER TABLE role_cards ADD COLUMN voiceDisplayName TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN roleCardId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_roleCardId ON conversations(roleCardId)")
                db.execSQL("ALTER TABLE memories ADD COLUMN roleCardId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_roleCardId ON memories(roleCardId)")
            }
        }

        private class DatabaseInitializationCallback : RoomDatabase.Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                createMemoryFtsTables(db)
                seedBuiltInSkills(db)
            }

            private fun createMemoryFtsTables(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS memories_fts USING fts4(
                        content,
                        category
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memories_ai AFTER INSERT ON memories BEGIN
                        INSERT INTO memories_fts(docid, content, category)
                        VALUES (new.id, new.content, new.category);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memories_ad AFTER DELETE ON memories BEGIN
                        DELETE FROM memories_fts WHERE docid = old.id;
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memories_au AFTER UPDATE ON memories BEGIN
                        DELETE FROM memories_fts WHERE docid = old.id;
                        INSERT INTO memories_fts(docid, content, category)
                        VALUES (new.id, new.content, new.category);
                    END
                    """.trimIndent()
                )
            }

            private fun seedBuiltInSkills(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                builtInSkills.forEachIndexed { index, skill ->
                    db.execSQL(
                        """
                        INSERT INTO skills(
                            id, name, description, systemPrompt, icon,
                            isBuiltIn, isActive, usageCount, createdAt, updatedAt
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any>(
                            index + 1L,
                            skill.name,
                            skill.description,
                            skill.systemPrompt,
                            skill.icon,
                            1,
                            if (index == 0) 1 else 0,
                            0,
                            now,
                            now
                        )
                    )
                }
            }
        }

        private data class BuiltInSkillSeed(
            val name: String,
            val description: String,
            val systemPrompt: String,
            val icon: String
        )

        private val builtInSkills = listOf(
            BuiltInSkillSeed(
                name = "翻译助手",
                description = "考虑语境、文化和母语差异的专业翻译",
                systemPrompt = "你是一个专业的翻译助手。请根据使用者的语境、文化背景以及母语情况，给出准确、自然、符合目标表达习惯的翻译结果；在保持原意的前提下，优先保证易懂、得体和语用自然。",
                icon = "translate"
            )
        )
    }
}
