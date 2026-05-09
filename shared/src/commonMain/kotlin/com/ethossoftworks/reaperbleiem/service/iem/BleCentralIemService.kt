package com.ethossoftworks.reaperbleiem.service.iem

import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleCentralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheral
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralId
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BleCentralIemService(private val bleCentralManager: IKmpBleCentralManager) : IIemService {

    val peripheral = atomic<IKmpBlePeripheral?>(null)

    fun scan() = bleCentralManager.scan().filter { it.serviceUuids.contains(REAPER_BLE_IEM_SERVICE_UUID) }

    suspend fun connect(id: KmpBlePeripheralId): Outcome<Unit, Any> {
        val connectedPeripheral =
            bleCentralManager.connect(id).unwrapOrReturn {
                return it
            }

        connectedPeripheral.requestMtu(517).unwrapOrReturn {
            return it
        }
        connectedPeripheral.discoverServices().unwrapOrReturn {
            return it
        }

        peripheral.update { connectedPeripheral }

        return Outcome.Ok(Unit)
    }

    suspend fun disconnect(): Outcome<Unit, Any> {
        peripheral.value?.disconnect()?.unwrapOrReturn {
            return it
        }
        peripheral.update { null }
        return Outcome.Ok(Unit)
    }

    override fun subscribe(): Flow<IemEvent> = callbackFlow {
        val job =
            peripheral.value
                ?.notifications(REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID)
                ?.onEach { event -> println("Receiving bytes - ${event.toHexString()}") }
                ?.launchIn(this)

        awaitClose { job?.cancel() }
    }

    override suspend fun refresh() {
        TODO("Not yet implemented")
    }

    override suspend fun setOutputVolume(trackId: Int, value: Float) {
        TODO("Not yet implemented")
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        TODO("Not yet implemented")
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        TODO("Not yet implemented")
    }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, isMuted: Boolean) {
        TODO("Not yet implemented")
    }
}
