package com.companion.chat.companion

import com.companion.chat.data.model.ChatMessage

class PreferenceLearningAdapter(
    private val coordinator: PreferenceLearningCoordinator
) : CompanionPostTurnLearning {

    override fun scheduleAfterIdle(
        sessionIdProvider: () -> String,
        messagesProvider: () -> List<ChatMessage>
    ) {
        coordinator.scheduleAfterIdle(
            sessionIdProvider = sessionIdProvider,
            messagesProvider = messagesProvider
        )
    }

    override fun triggerNow(
        reason: String,
        sessionId: String,
        messages: List<ChatMessage>
    ) {
        coordinator.triggerNow(
            reason = reason,
            sessionId = sessionId,
            messages = messages
        )
    }

    override fun cancelRunningSummary() {
        coordinator.cancelRunningSummary()
    }

    override fun release() {
        coordinator.release()
    }
}
