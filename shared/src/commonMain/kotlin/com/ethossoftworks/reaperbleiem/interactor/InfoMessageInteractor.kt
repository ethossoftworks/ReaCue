package com.ethossoftworks.reaperbleiem.interactor

import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class InfoMessageState(val queue: List<InfoMessage> = emptyList(), val currentMessage: InfoMessage? = null)

data class InfoMessage(val id: Int, val text: String)

class InfoMessageInteractor : Interactor<InfoMessageState>(initialState = InfoMessageState(), dependencies = listOf()) {

    private var counter = atomic(0)

    fun enqueueMessage(message: String) {
        update { state ->
            val newMessage = InfoMessage(id = counter.incrementAndGet(), text = message)

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
