package com.ethossoftworks.reacue.interactor

import com.outsidesource.oskitkmp.interactor.Interactor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class InfoMessageState(val queue: List<InfoMessage> = emptyList(), val currentMessage: InfoMessage? = null)

data class InfoMessage(val id: Int, val text: String, val type: InfoMessageType, val duration: Duration)

enum class InfoMessageType {
    Info,
    Error,
}

class InfoMessageInteractor : Interactor<InfoMessageState>(initialState = InfoMessageState(), dependencies = listOf()) {

    private var counter = atomic(0)

    fun enqueueMessage(message: String, type: InfoMessageType = InfoMessageType.Info, duration: Duration = 2.seconds) {
        update { state ->
            val newMessage =
                InfoMessage(id = counter.incrementAndGet(), text = message, type = type, duration = duration)

            state.copy(
                queue = if (state.currentMessage == null) state.queue else state.queue + newMessage,
                currentMessage = state.currentMessage ?: newMessage,
            )
        }
    }

    fun onMessageFinished() {
        interactorScope.launch {
            val newMessage = state.queue.firstOrNull()
            update { state -> state.copy(queue = state.queue.drop(1), currentMessage = null) }
            delay(16) // Give time for UI to render NO info message before showing a new one
            update { state -> state.copy(currentMessage = newMessage) }
        }
    }
}
