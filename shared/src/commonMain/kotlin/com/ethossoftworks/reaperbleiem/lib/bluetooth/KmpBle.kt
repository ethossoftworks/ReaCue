package com.ethossoftworks.reaperbleiem.lib.bluetooth

enum class KmpBleWriteMode {
    WithoutResponse,
    WithResponse,
}

enum class KmpBleGattProperty(val value: Int) {
    Broadcast(1),
    Read(2),
    WriteWithoutResponse(4),
    Write(8),
    Notify(16),
    Indicate(32),
    SignedWrite(64),
    ExtendedProps(128),
}

enum class KmpBleGattPermission {
    Readable,
    Writable,
    ReadEncryptionRequired,
    WriteEncryptionRequired,
}

sealed class KmpBleError {
    object PlatformHandlerNotReady : KmpBleError()

    object PeripheralDisconnected : KmpBleError()

    object InvalidIdentifier : KmpBleError()

    object UnknownCharacteristic : KmpBleError()

    object CouldNotDiscoverCharacteristics : KmpBleError()

    /** Only occurs on iOS during a call to [IKmpBleCentralManager.connect] */
    object PeerRemovedPairingInfo : KmpBleError()

    data class Unknown(val error: Any) : KmpBleError()

    object NotBonded : KmpBleError()

    data class WriteError(val code: KmpBlePeripheralGattResult) : KmpBleError()
}

enum class KmpBleConnectionPriority {
    // Approximate connection interval 11-15ms. Maps to BluetoothGatt.CONNECTION_PRIORITY_HIGH on Android
    // and CBPeripheralManagerConnectionLatencyLow on Apple platforms
    High,

    // Approximate connection interval 30-50ms. Maps to BluetoothGatt.CONNECTION_PRIORITY_BALANCED on Android
    // and CBPeripheralManagerConnectionLatencyMedium on Apple platforms
    Balanced,

    // Approximate connection interval 100-125ms. Maps to BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER on Android
    // and CBPeripheralManagerConnectionLatencyHigh on Apple platforms
    Low,
}
