package com.ethossoftworks.reacue.lib

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
fun dataToKotlinByteArray(data: NSData): ByteArray =
    ByteArray(data.length.toInt()).apply { usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun kotlinByteArrayToData(data: ByteArray): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(data), length = data.size.toULong())
}

open class SwiftOutcome<out V : Any, out E : Any>(private val outcome: Outcome<V, E>) {
    data class Ok<out V : Any, out E : Any>(val value: V) : SwiftOutcome<V, E>(outcome = Outcome.Ok(value))

    data class Error<out V : Any, out E : Any>(val error: E) : SwiftOutcome<V, E>(outcome = Outcome.Error(error))

    fun unbox() = outcome
}

open class SwiftOutcomeNullable<out V : Any, out E : Any>(private val outcome: Outcome<V?, E?>) {
    data class Ok<out V : Any, out E : Any>(val value: V?) : SwiftOutcomeNullable<V, E>(outcome = Outcome.Ok(value))

    data class Error<out V : Any, out E : Any>(val error: E?) :
        SwiftOutcomeNullable<V, E>(outcome = Outcome.Error(error))

    fun unbox() = outcome
}

class SwiftCompletableDeferred<T : Any>
private constructor(private val deferred: CompletableDeferred<T> = CompletableDeferred<T>()) {
    fun complete(value: T): Boolean = deferred.complete(value)

    fun completeExceptionally(t: Throwable): Boolean = deferred.completeExceptionally(t)

    suspend fun await(): T = deferred.await()
}

/**
 * SwiftFlow
 *
 * Allows creation of a cold flow in Swift. Supports cancellation but is only checked when trying to emit or by calling
 * ensureActive()
 *
 * This has to be a class because Objective-C does not support generics on functions causing all types to be erased
 */
abstract class SwiftFlow<T : Any> {
    private var scope: ProducerScope<T>? = null

    @Throws(SwiftFlowCancellationException::class, CancellationException::class)
    protected suspend fun emit(value: T) {
        if (scope?.isActive == false) throw SwiftFlowCancellationException()
        scope?.send(value)
    }

    protected fun tryEmit(value: T): Boolean {
        return scope?.trySend(value)?.isSuccess ?: false
    }

    protected fun tryEmitBlocking(value: T): Boolean {
        return scope?.trySendBlocking(value)?.isSuccess ?: false
    }

    @Throws(SwiftFlowCancellationException::class, CancellationException::class)
    protected suspend fun ensureActive() {
        if (scope?.isActive == false) throw SwiftFlowCancellationException()
    }

    abstract suspend fun produce()

    suspend fun awaitClose(block: () -> Unit) {
        scope?.launch { suspendCancellableCoroutine {} }?.join()
        block()
    }

    fun close() {
        scope?.close()
    }

    fun unbox(): Flow<T> =
        channelFlow {
                scope = this

                try {
                    produce()
                } catch (e: SwiftFlowCancellationException) {
                    // Do nothing
                }
            }
            .onCompletion { scope = null }
}

class SwiftMutableSharedFlow<T : Any>(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
) {
    private val flow = MutableSharedFlow<T>(replay, extraBufferCapacity, onBufferOverflow)

    fun unbox(): MutableSharedFlow<T> = flow

    fun unboxAsFlow(): Flow<T> = flow
}

class SwiftMutableStateFlow<T : Any>(value: T) {
    private val flow = MutableStateFlow(value)

    fun unbox(): MutableStateFlow<T> = flow

    fun unboxAsFlow(): Flow<T> = flow
}

class SwiftFlowCancellationException : CancellationException("SwiftFlow has been cancelled")

class EmptyFlow<T : Any> : SwiftFlow<T>() {
    override suspend fun produce() {}
}
