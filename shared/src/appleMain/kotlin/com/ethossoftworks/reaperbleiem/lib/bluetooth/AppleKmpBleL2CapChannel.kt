@file:OptIn(ExperimentalForeignApi::class)

package com.ethossoftworks.reaperbleiem.lib.bluetooth

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreFoundation.CFRunLoopPerformBlock
import platform.CoreFoundation.CFRunLoopRef
import platform.CoreFoundation.CFRunLoopRun
import platform.CoreFoundation.CFRunLoopStop
import platform.CoreFoundation.CFRunLoopWakeUp
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import platform.Foundation.NSQualityOfServiceUserInteractive
import platform.Foundation.NSRunLoop
import platform.Foundation.NSStream
import platform.Foundation.NSStreamDelegateProtocol
import platform.Foundation.NSStreamEvent
import platform.Foundation.NSStreamEventEndEncountered
import platform.Foundation.NSStreamEventErrorOccurred
import platform.Foundation.NSStreamEventHasBytesAvailable
import platform.Foundation.NSStreamEventHasSpaceAvailable
import platform.Foundation.NSThread
import platform.darwin.NSObject

/**
 * Wraps a CoreBluetooth [CBL2CAPChannel]'s NSInputStream/NSOutputStream as an [IKmpBleL2CapChannel].
 *
 * NSStream is not thread-safe and is event-driven (not blocking, unlike Android's BluetoothSocket): it must be driven
 * from the thread its run loop is scheduled on. We dedicate one [NSThread] whose run loop the streams are scheduled on.
 * The thread blocks in [CFRunLoopRun], dispatching the delegate callbacks and the flush blocks that [write] schedules
 * via [CFRunLoopPerformBlock] + [CFRunLoopWakeUp], until [close] calls [CFRunLoopStop].
 */
private const val MAX_BUFFERED_BYTES = 64 * 1024
private const val READ_BUFFER_SIZE = 4096

internal class AppleKmpBleL2CapChannel(
    // Stored (not just a constructor param) so the CBL2CAPChannel is retained for the wrapper's lifetime. It owns the
    // L2CAP connection and its streams; if it deallocates, the connection tears down (remote sees "Broken pipe").
    private val channel: CBL2CAPChannel
) : IKmpBleL2CapChannel {

    // A Channel (not SharedFlow) so the flow COMPLETES when the channel closes.
    private val incomingChannel = Channel<ByteArray>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val incoming: Flow<ByteArray> = incomingChannel.receiveAsFlow()

    private val input: NSInputStream? = channel.inputStream
    private val output: NSOutputStream? = channel.outputStream

    private val running = atomic(true)
    private val outbox = atomic<List<ByteArray>>(emptyList())
    private val runLoopRef = atomic<CFRunLoopRef?>(null)
    private val spaceAvailable = Channel<Unit>(Channel.CONFLATED)

    private val delegate =
        object : NSObject(), NSStreamDelegateProtocol {
            override fun stream(aStream: NSStream, handleEvent: NSStreamEvent) {
                when {
                    handleEvent and NSStreamEventHasBytesAvailable != 0uL && aStream == input -> drainInput()
                    handleEvent and NSStreamEventHasSpaceAvailable != 0uL && aStream == output -> flushOutbound()
                    handleEvent and (NSStreamEventErrorOccurred or NSStreamEventEndEncountered) != 0uL -> close()
                }
            }
        }

    private val thread =
        object : NSThread() {
            override fun main() {
                val runLoop = NSRunLoop.currentRunLoop
                runLoopRef.value = runLoop.getCFRunLoop()
                input?.setDelegate(delegate)
                input?.scheduleInRunLoop(runLoop, forMode = NSDefaultRunLoopMode)
                output?.setDelegate(delegate)
                output?.scheduleInRunLoop(runLoop, forMode = NSDefaultRunLoopMode)
                input?.open()
                output?.open()

                flushOutbound()
                CFRunLoopRun()

                input?.close()
                output?.close()
                input?.setDelegate(null)
                output?.setDelegate(null)
                runLoopRef.value = null
            }
        }

    init {
        thread.qualityOfService = NSQualityOfServiceUserInteractive
        thread.start()
    }

    private fun drainInput() {
        val stream = input ?: return
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (stream.hasBytesAvailable) {
            val read =
                buffer.usePinned { pinned ->
                    stream.read(pinned.addressOf(0).reinterpret(), READ_BUFFER_SIZE.convert()).toInt()
                }
            if (read <= 0) break
            incomingChannel.trySend(buffer.copyOf(read))
        }
    }

    override suspend fun write(data: ByteArray): Outcome<Unit, KmpBleError> {
        if (output == null || !running.value) return Outcome.Error(KmpBleError.Unknown("L2CAP channel closed"))
        while (running.value && bufferedBytes() >= MAX_BUFFERED_BYTES) {
            spaceAvailable.receive()
        }
        if (!running.value) return Outcome.Error(KmpBleError.Unknown("L2CAP channel closed"))
        outbox.update { it + data }
        scheduleOnRunLoop { flushOutbound() }
        return Outcome.Ok(Unit)
    }

    private inline fun scheduleOnRunLoop(crossinline block: () -> Unit) {
        val runLoop = runLoopRef.value ?: return
        CFRunLoopPerformBlock(runLoop, kCFRunLoopDefaultMode) { block() }
        CFRunLoopWakeUp(runLoop)
    }

    private fun flushOutbound() {
        val stream = output ?: return
        val batch = outbox.getAndSet(emptyList())
        var i = 0
        while (i < batch.size) {
            val chunk = batch[i]
            var offset = 0
            while (offset < chunk.size && stream.hasSpaceAvailable) {
                val written = chunk.usePinned { pinned ->
                    stream.write((pinned.addressOf(offset)).reinterpret(), (chunk.size - offset).convert()).toInt()
                }
                if (written <= 0) break
                offset += written
            }
            if (offset < chunk.size) {
                // Output buffer is full. Re-queue this chunk's unwritten tail plus every remaining chunk IN ORDER at
                // the front of the inbox so the byte stream stays sequential.
                val pending = ArrayList<ByteArray>(batch.size - i)
                pending.add(if (offset == 0) chunk else chunk.copyOfRange(offset, chunk.size))
                for (j in i + 1 until batch.size) pending.add(batch[j])
                outbox.update { pending + it }
                break
            }
            i++
        }
        if (bufferedBytes() < MAX_BUFFERED_BYTES) spaceAvailable.trySend(Unit)
    }

    private fun bufferedBytes(): Int = outbox.value.sumOf { it.size }

    override fun close() {
        if (!running.getAndSet(false)) return
        incomingChannel.close()
        spaceAvailable.trySend(Unit)
        runLoopRef.value?.let { CFRunLoopStop(it) }
    }
}
