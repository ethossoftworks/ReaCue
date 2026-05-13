@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reaperbleiem.service.iem

import co.touchlab.kermit.Logger
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleAdvertisementCharacteristic
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleAdvertisementData
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleAdvertisementService
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleCentralId
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleGattPermission
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleGattProperty
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralEvent
import kotlin.math.ceil
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val REAPER_BLE_IEM_SERVICE_UUID = Uuid.parseHexDash("fa6e666c-2c23-43f1-84e4-4653ebf930f4")
val REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID = Uuid.parseHexDash("319893ca-5fa2-4c21-9f51-bc2b1116a352")
val REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID = Uuid.parseHexDash("aa57c9ce-ada3-4779-88bb-efce418a297e")

@OptIn(ExperimentalSerializationApi::class)
class BlePeripheralIemService(
    private val networkIemService: NetworkIemService,
    private val peripheralManager: IKmpBlePeripheralManager,
) : IIemService {

    private val lastRefreshedEvent = atomic<IemEvent.Refreshed?>(null)
    private val bleNotificationChannel = Channel<IemEvent>(capacity = Channel.UNLIMITED)
    private val notificationId = atomic<UShort>(0u)
    private val cbor = Cbor {
        ignoreUnknownKeys = true
        preferCborLabelsOverNames = true
    }

    private val advertisementData =
        KmpBleAdvertisementData(
            name = "",
            services =
                listOf(
                    KmpBleAdvertisementService(
                        uuid = REAPER_BLE_IEM_SERVICE_UUID,
                        isPrimaryService = true,
                        characteristics =
                            listOf(
                                KmpBleAdvertisementCharacteristic(
                                    uuid = REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID,
                                    properties = setOf(KmpBleGattProperty.Notify),
                                    permissions = setOf(KmpBleGattPermission.Readable),
                                ),
                                KmpBleAdvertisementCharacteristic(
                                    uuid = REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID,
                                    properties = setOf(KmpBleGattProperty.WriteWithoutResponse),
                                    permissions = setOf(KmpBleGattPermission.Writable),
                                ),
                            ),
                    )
                ),
        )

    override fun subscribe(): Flow<IemEvent> = channelFlow {
        val isAdvertising = CompletableDeferred<Unit>()

        val bleChannelJob = launch {
            for (notification in bleNotificationChannel) {
                sendBleNotification(notification)
            }
        }

        val advertiseJob =
            peripheralManager
                .advertise(advertisementData)
                .onEach { event ->
                    when (event) {
                        is KmpBlePeripheralEvent.Error -> cancel()
                        KmpBlePeripheralEvent.Advertising -> isAdvertising.complete(Unit)
                        is KmpBlePeripheralEvent.CentralSubscribed -> {}
                        is KmpBlePeripheralEvent.CentralUnsubscribed -> {}
                        is KmpBlePeripheralEvent.ReadRequest -> {}
                        is KmpBlePeripheralEvent.WriteRequest -> onWriteRequest(event)
                    }
                }
                .launchIn(this)

        isAdvertising.await()
        Logger.i { "Advertising started" }

        val networkJob =
            networkIemService
                .subscribe()
                .onEach { event ->
                    Logger.i { "Received event from Reaper - $event" }
                    send(event)
                    if (event is IemEvent.Refreshed) lastRefreshedEvent.update { event }
                    bleNotificationChannel.trySend(event)
                }
                .launchIn(this)

        awaitClose {
            bleChannelJob.cancel()
            advertiseJob.cancel()
            networkJob.cancel()
        }
    }

    private suspend fun onWriteRequest(request: KmpBlePeripheralEvent.WriteRequest) {
        try {
            val event = cbor.decodeFromByteArray(IemEvent.serializer(), request.data)
            Logger.i { "Received command - $event" }

            when (event) {
                IemEvent.Refresh -> networkIemService.refresh()
                is IemEvent.OutputVolumeUpdated -> networkIemService.setOutputVolume(event.trackId, event.value)
                is IemEvent.ReceivePanUpdated ->
                    networkIemService.setReceivePan(event.trackId, event.receiveId, event.value)
                is IemEvent.ReceiveVolumeUpdated ->
                    networkIemService.setReceiveVolume(event.trackId, event.receiveId, event.value)
                is IemEvent.Error,
                is IemEvent.ReceiveRegistered,
                IemEvent.Refreshing,
                is IemEvent.Refreshed,
                is IemEvent.TrackNameUpdated -> return
            }

            val centrals =
                peripheralManager.subscribedCentrals(REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID) - request.central
            sendBleNotification(event, centrals)
        } catch (t: Throwable) {
            Logger.e { "Could not decode write request ${t.message}" }
        }
    }

    override suspend fun refresh() {
        return networkIemService.refresh()
    }

    override suspend fun setOutputVolume(trackId: Int, value: Float) {
        networkIemService.setOutputVolume(trackId, value)
        sendBleNotification(IemEvent.OutputVolumeUpdated(trackId, value))
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        networkIemService.setReceiveVolume(trackId, receiveId, value)
        sendBleNotification(IemEvent.ReceiveVolumeUpdated(trackId, receiveId, value))
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        networkIemService.setReceivePan(trackId, receiveId, value)
        sendBleNotification(IemEvent.ReceivePanUpdated(trackId, receiveId, value))
    }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, isMuted: Boolean) {
        networkIemService.setReceiveMute(trackId, receiveId, isMuted)
        // TODO: Notify receive mute
    }

    private suspend fun sendBleNotification(event: IemEvent, centralList: Set<KmpBleCentralId>? = null) {
        val payload = cbor.encodeToByteArray(IemEvent.serializer(), event)
        val centrals = centralList ?: peripheralManager.subscribedCentrals(REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID)
        val requestId = notificationId.getAndUpdate { it.inc() }

        Logger.i { "Sending notification to ${centrals.size} centrals" }

        for (central in centrals) {
            val packetSize = peripheralManager.maximumUpdateValueLengthForCentral(central) - headerSize
            val packetCount = ceil(payload.size.toDouble() / packetSize.toDouble()).toUInt()

            for (packetIndex in 0u until packetCount) {
                val buffer =
                    Buffer().apply {
                        writeUShort(requestId)
                        writeUShort((packetCount - 1u - packetIndex).toUShort())
                        val startIndex = (packetIndex * packetSize).toInt()
                        write(payload, startIndex, minOf(startIndex + packetSize.toInt(), payload.size))
                    }

                Logger.i { "Sending notification packet $requestId - ${packetIndex.toInt() + 1}/$packetCount" }
                peripheralManager.notify(
                    REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID,
                    buffer.readByteArray(),
                    centrals = listOf(central),
                )
            }
        }
    }
}

/** Header Format: Request Id (UInt16), Packets remaining (UInt16) */
val headerSize = 4u
