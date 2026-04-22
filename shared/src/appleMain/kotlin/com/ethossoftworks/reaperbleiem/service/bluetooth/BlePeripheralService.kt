package com.ethossoftworks.reaperbleiem.service.bluetooth

class BlePeripheralService(
    val kmpBlePeripheralManager: AppleKmpBlePeripheralManager
) {
    val REAPER_BLE_IEM_SERVICE_UUID = "FA6E666C-2C23-43F1-84E4-4653EBF930F4"
    val REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID = "319893CA-5FA2-4C21-9F51-BC2B1116A352"
    val REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID = "AA57C9CE-ADA3-4779-88BB-EFCE418A297E"

    val advertisementData = KmpBleAdvertisementData(
        name = "",
        services = listOf(
            KmpBleAdvertisementService(
                uuid = REAPER_BLE_IEM_SERVICE_UUID,
                isPrimaryService = true,
                characteristics = listOf(
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
                )
            )
        ),
    )

    suspend fun start() {
        kmpBlePeripheralManager.advertise(advertisementData).collect { event ->
            when (event) {
                is KmpBlePeripheralManagerEvent.Error -> {
                    println("Error: ${event.error}")
                }
                is KmpBlePeripheralManagerEvent.ServiceAdded -> {
                    println("Service Added: ${event.uuid}")
                }
                KmpBlePeripheralManagerEvent.Advertising -> {
                    println("Advertising!")
                }
                is KmpBlePeripheralManagerEvent.CentralSubscribedToCharacteristic -> {
                    println("Central subscribed to characteristic: ${event.characteristicUuid}")
                }
                is KmpBlePeripheralManagerEvent.CentralUnsubscribedFromCharacteristic -> {
                    println("Central unsubscribed from characteristic: ${event.characteristicUuid}")
                }
            }
        }
    }
}