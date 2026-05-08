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
}