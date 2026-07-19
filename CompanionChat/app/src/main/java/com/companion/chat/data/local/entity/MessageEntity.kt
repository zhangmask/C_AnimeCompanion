package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.companion.chat.data.model.MessageQuote
import com.companion.chat.data.model.MessageRole

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val imageUris: List<String> = emptyList(),
    val timestamp: Long,
    val position: Int,
    /** 引用上下文（可为空）；序列化为 JSON 字符串 */
    val quote: String? = null,
    /** TTS 合成音频缓存路径；命中后直接播放，避免重复合成 */
    val audioUri: String? = null
) {
    /** 便捷访问：把 quote JSON 解析为 MessageQuote */
    fun quoteDomain(): MessageQuote? = quote?.let { QuoteJsonCodec.decode(it) }
}

/** 简单 JSON 编解码：避免引入额外依赖 */
object QuoteJsonCodec {
    fun encode(q: MessageQuote): String {
        // 形如 {"role":"USER","text":"..."}，text 中转义双引号与反斜杠
        val escaped = q.text.replace("\\", "\\\\").replace("\"", "\\\"")
        return "{\"role\":\"${q.sourceRole.name}\",\"text\":\"$escaped\"}"
    }

    fun decode(json: String): MessageQuote? = try {
        val roleStart = json.indexOf("\"role\":\"") + 8
        val roleEnd = json.indexOf("\"", roleStart)
        val roleName = json.substring(roleStart, roleEnd)
        val textStart = json.indexOf("\"text\":\"", roleEnd) + 8
        // 从 textStart 开始扫描，找到未转义的结束双引号
        val sb = StringBuilder()
        var i = textStart
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                val next = json[i + 1]
                if (next == '"') { sb.append('"'); i += 2; continue }
                if (next == '\\') { sb.append('\\'); i += 2; continue }
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        MessageQuote(sourceRole = MessageRole.valueOf(roleName), text = sb.toString())
    } catch (_: Exception) {
        null
    }
}
