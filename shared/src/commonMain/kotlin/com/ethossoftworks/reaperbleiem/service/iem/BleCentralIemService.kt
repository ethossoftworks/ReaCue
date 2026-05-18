@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reaperbleiem.service.iem

import co.touchlab.kermit.Logger
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleCentralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheral
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleConnectionStatus
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleWriteMode
import com.outsidesource.oskitkmp.lib.toUShort
import com.outsidesource.oskitkmp.lib.update
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor

@OptIn(ExperimentalSerializationApi::class)
class BleCentralIemService(private val bleCentralManager: IKmpBleCentralManager) : IIemService {

    private val peripheral = atomic<IKmpBlePeripheral?>(null)
    private val requestBuffers = atomic<Map<UShort, Buffer>>(emptyMap())
    private val cbor = Cbor {
        ignoreUnknownKeys = true
        preferCborLabelsOverNames = true
    }

    override fun subscribe(context: IemContext): Flow<IemEvent> = callbackFlow {
        // Connect to and observe peripheral
        val peripheralId = (context as? IemContext.Central ?: return@callbackFlow).peripheral.identifier
        val peripheral =
            bleCentralManager
                .connect(peripheralId)
                .unwrapOrReturn {
                    return@callbackFlow
                }
                .apply { this@BleCentralIemService.peripheral.update { this } }

        peripheral.requestMtu(517).unwrapOrReturn {
            return@callbackFlow
        }
        peripheral.discoverServices().unwrapOrReturn {
            return@callbackFlow
        }

        peripheral.connectionStatus.onEach { if (it == KmpBleConnectionStatus.Disconnected) cancel() }.launchIn(this)

        // Subscribe to notifications
        val job =
            peripheral
                .notifications(REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID)
                .onEach { notification ->
                    try {
                        val requestId = notification.toUShort(0)
                        val packetsRemaining = notification.toUShort(2)
                        Logger.i { "Received notification - $requestId - packets remaining $packetsRemaining" }

                        val buffer = requestBuffers.value[requestId] ?: Buffer()
                        buffer.write(notification, headerSize.toInt(), notification.size)

                        if (packetsRemaining > 0u.toUShort()) {
                            requestBuffers.update { it.update { this[requestId] = buffer } }
                            return@onEach
                        }

                        requestBuffers.update { it.update { remove(requestId) } }
                        val event = cbor.decodeFromByteArray(IemEvent.serializer(), buffer.readByteArray())
                        send(event)
                        Logger.i { "Received message - $event" }
                    } catch (e: Exception) {
                        Logger.e(e) { "Error while assembling packets: ${notification.toHexString()}" }
                    }
                }
                .launchIn(this)

        // Wait for collector to be ready to send refresh command
        delay(16.milliseconds)
        refresh()

        awaitClose {
            job.cancel()
            launch {
                peripheral.disconnect().unwrapOrReturn {
                    return@launch
                }
            }
        }
    }

    fun scan() = bleCentralManager.scan().filter { it.services.contains(REAPER_BLE_IEM_SERVICE_UUID) }

    override suspend fun refresh() {
        val payload = cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.Refresh)
        peripheral.value?.write(REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setOutputVolume(trackId: Int, value: Float) {
        val payload = cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.OutputVolumeUpdated(trackId, value))
        peripheral.value?.write(REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        val payload =
            cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.ReceiveVolumeUpdated(trackId, receiveId, value))
        peripheral.value?.write(REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        val payload =
            cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.ReceivePanUpdated(trackId, receiveId, value))
        peripheral.value?.write(REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, isMuted: Boolean) {
        TODO("Not yet implemented")
    }
}
