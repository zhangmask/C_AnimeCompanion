package com.companion.chat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.companion.chat.data.local.dao.ConversationDao
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.dao.MemoryEntityDao
import com.companion.chat.data.local.dao.MemoryLinkDao
import com.companion.chat.data.local.dao.MessageDao
import com.companion.chat.data.local.dao.PreferenceDao
import com.companion.chat.data.local.dao.RoleCardDao
import com.companion.chat.data.local.dao.SkillDao
import com.companion.chat.data.local.entity.AgentExperience
import com.companion.chat.data.local.entity.ConversationEntity
import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.MemoryEntity
import com.companion.chat.data.local.entity.MemoryEntityMap
import com.companion.chat.data.local.entity.MemoryLink
import com.companion.chat.data.local.entity.MetaMemory
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
        RoleCard::class,
        MemoryLink::class,
        MemoryEntity::class,
        MemoryEntityMap::class,
        AgentExperience::class,
        MetaMemory::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CompanionDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memoryLinkDao(): MemoryLinkDao
    abstract fun memoryEntityDao(): MemoryEntityDao
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN roleCardId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_preferences_roleCardId ON user_preferences(roleCardId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. memories 表新增列（ALTER 添加新列）
                db.execSQL("ALTER TABLE memories ADD COLUMN strength REAL NOT NULL DEFAULT 0.6")
                db.execSQL("ALTER TABLE memories ADD COLUMN entityName TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN abstractionLevel INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE memories ADD COLUMN l0Summary TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN l1Overview TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN lastAccessedAt INTEGER NOT NULL DEFAULT 0")

                // 2. 迁移已有数据：layer 映射到 strength
                db.execSQL("UPDATE memories SET strength = 0.5 WHERE layer = 'long_term'")
                db.execSQL("UPDATE memories SET strength = 0.3 WHERE layer = 'short_term'")
                db.execSQL("UPDATE memories SET strength = 0.8 WHERE source = 'manual'")

                // 3. 新建 memory_links 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fromId INTEGER NOT NULL,
                        toId INTEGER NOT NULL,
                        linkType TEXT NOT NULL,
                        weight REAL NOT NULL DEFAULT 1.0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (fromId) REFERENCES memories(id) ON DELETE CASCADE,
                        FOREIGN KEY (toId) REFERENCES memories(id) ON DELETE CASCADE,
                        UNIQUE(fromId, toId, linkType)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_links_from ON memory_links(fromId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_links_to ON memory_links(toId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_links_type ON memory_links(linkType)")

                // 4. 新建 memory_entities 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory_entities (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        normalizedName TEXT NOT NULL UNIQUE,
                        type TEXT NOT NULL DEFAULT 'topic',
                        linkedMemoryCount INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 5. 新建 memory_entity_map 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory_entity_map (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entityId INTEGER NOT NULL,
                        memoryId INTEGER NOT NULL,
                        FOREIGN KEY (entityId) REFERENCES memory_entities(id) ON DELETE CASCADE,
                        FOREIGN KEY (memoryId) REFERENCES memories(id) ON DELETE CASCADE,
                        UNIQUE(entityId, memoryId)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_entity_map_entity ON memory_entity_map(entityId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_entity_map_memory ON memory_entity_map(memoryId)")

                // 6. 新建 agent_experiences 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS agent_experiences (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        situation TEXT NOT NULL,
                        approach TEXT NOT NULL,
                        reflect TEXT NOT NULL,
                        outcome TEXT NOT NULL DEFAULT 'success',
                        applyCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 7. 新建 meta_memories 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS meta_memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT 'retrieval',
                        applyCount INTEGER NOT NULL DEFAULT 0,
                        confidence REAL NOT NULL DEFAULT 0.5,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN isUserRenamed INTEGER NOT NULL DEFAULT 0")
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
