package com.companion.chat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.companion.chat.data.local.dao.ConversationDao
import com.companion.chat.data.local.dao.CustomApiConfigDao
import com.companion.chat.data.local.dao.ImageStudioMessageDao
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.dao.MemoryEntityDao
import com.companion.chat.data.local.dao.MemoryLinkDao
import com.companion.chat.data.local.dao.MessageDao
import com.companion.chat.data.local.dao.PreferenceDao
import com.companion.chat.data.local.dao.RoleCardDao
import com.companion.chat.data.local.dao.SkillDao
import com.companion.chat.data.local.dao.TtsAudioCacheDao
import com.companion.chat.data.local.entity.AgentExperience
import com.companion.chat.data.local.entity.ConversationEntity
import com.companion.chat.data.local.entity.CustomApiConfig
import com.companion.chat.data.local.entity.ImageStudioMessageEntity
import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.MemoryEntity
import com.companion.chat.data.local.entity.MemoryEntityMap
import com.companion.chat.data.local.entity.MemoryLink
import com.companion.chat.data.local.entity.MetaMemory
import com.companion.chat.data.local.entity.MessageEntity
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.local.entity.Skill
import com.companion.chat.data.local.entity.TtsAudioCacheEntity
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
        MetaMemory::class,
        CustomApiConfig::class,
        TtsAudioCacheEntity::class,
        ImageStudioMessageEntity::class
    ],
    version = 14,
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
    abstract fun customApiConfigDao(): CustomApiConfigDao
    abstract fun ttsAudioCacheDao(): TtsAudioCacheDao
    abstract fun imageStudioMessageDao(): ImageStudioMessageDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
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

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE role_cards ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 消息表新增引用列（可空 JSON 字符串）
                db.execSQL("ALTER TABLE messages ADD COLUMN quote TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 消息表新增 TTS 音频缓存列（可空文件 URI）
                db.execSQL("ALTER TABLE messages ADD COLUMN audioUri TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_api_configs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        model TEXT NOT NULL,
                        apiFormat TEXT NOT NULL DEFAULT 'OPENAI',
                        customParams TEXT NOT NULL DEFAULT '{}',
                        isActive INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tts_audio_cache (
                        cacheKey TEXT PRIMARY KEY NOT NULL,
                        role TEXT NOT NULL,
                        contentHash TEXT NOT NULL,
                        audioUri TEXT NOT NULL,
                        textPreview TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tts_audio_cache_cacheKey ON tts_audio_cache(cacheKey)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 双值遗忘曲线：baseline（最低值）+ 每日强化上限
                db.execSQL("ALTER TABLE memories ADD COLUMN baseline REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE memories ADD COLUMN dailyStrengthenDelta REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE memories ADD COLUMN lastStrengthenDate INTEGER NOT NULL DEFAULT 0")
                // 旧记忆 strength 降到 0.3（新标准：不再一开始就是"长期"）
                db.execSQL("UPDATE memories SET strength = 0.3 WHERE strength >= 0.5 AND source != 'manual'")
                db.execSQL("UPDATE memories SET baseline = strength * 0.3 WHERE baseline = 0.0 AND strength > 0.2")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS image_studio_messages (
                        id TEXT PRIMARY KEY NOT NULL,
                        roleCardId INTEGER NOT NULL,
                        prompt TEXT NOT NULL,
                        fullPrompt TEXT NOT NULL,
                        imageUri TEXT,
                        isError INTEGER NOT NULL,
                        errorMessage TEXT,
                        referenceMessageId TEXT,
                        timestamp INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        FOREIGN KEY (roleCardId) REFERENCES role_cards(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_image_studio_messages_roleCardId ON image_studio_messages(roleCardId)")
            }
        }

        private class DatabaseInitializationCallback : RoomDatabase.Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                createMemoryFtsTables(db)
                seedBuiltInSkills(db)
                seedDefaultRoleCard(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // 数据库损坏/清空后自动重建默认角色卡，避免角色卡缺失导致 TTS 配置异常
                if (!tableExists(db, "role_cards") || countRows(db, "role_cards") == 0L) {
                    android.util.Log.w("CompanionDB", "role_cards 表缺失或为空，重新注入默认角色卡")
                    if (!tableExists(db, "role_cards")) {
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
                                avatarImageUri TEXT NOT NULL DEFAULT '',
                                galleryImageUris TEXT NOT NULL DEFAULT '[]',
                                imageStylePrompt TEXT NOT NULL DEFAULT '',
                                voiceProfileUri TEXT NOT NULL DEFAULT '',
                                voiceMode TEXT NOT NULL DEFAULT 'CLONE',
                                voiceDisplayName TEXT NOT NULL DEFAULT '',
                                isBuiltIn INTEGER NOT NULL DEFAULT 0,
                                isActive INTEGER NOT NULL DEFAULT 0,
                                tags TEXT NOT NULL DEFAULT '[]',
                                createdAt INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }
                    seedDefaultRoleCard(db)
                }
            }

            private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
                val cursor = db.query(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(table)
                )
                return cursor.use { it.moveToFirst() }
            }

            private fun countRows(db: SupportSQLiteDatabase, table: String): Long {
                val cursor = db.query("SELECT count(*) FROM $table")
                return cursor.use {
                    if (it.moveToFirst()) it.getLong(0) else 0L
                }
            }

            private fun seedDefaultRoleCard(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                val voiceProfileUri = "file:///storage/emulated/0/Android/data/com.companion.chat/files/voice_clips/moss_voice_clone_ref.wav"
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO role_cards (
                        id, name, description, avatar, persona, speakingStyle, background, rules, taboos,
                        openingMessage, exampleDialogue, avatarImageUri, galleryImageUris, imageStylePrompt,
                        voiceProfileUri, voiceMode, voiceDisplayName, isBuiltIn, isActive, tags, createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        1L,
                        "小夏",
                        "你的 AI 伙伴，会用你提供的参考音频进行语音克隆",
                        "person",
                        "一个温柔体贴、乐于助人的 AI 助手",
                        "",
                        "",
                        "",
                        "",
                        "你好，我是小夏，很高兴认识你。",
                        "",
                        "",
                        "[]",
                        "",
                        voiceProfileUri,
                        "CLONE",
                        "小夏",
                        1,
                        1,
                        "[]",
                        now,
                        now
                    )
                )
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
