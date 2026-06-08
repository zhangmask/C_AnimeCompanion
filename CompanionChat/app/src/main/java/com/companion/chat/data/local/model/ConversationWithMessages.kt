package com.companion.chat.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.companion.chat.data.local.entity.ConversationEntity
import com.companion.chat.data.local.entity.MessageEntity

data class ConversationWithMessages(
    @Embedded val conversation: ConversationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "conversationId"
    )
    val messages: List<MessageEntity>
)
