package com.ethossoftworks.reaperbleiem.lib.bluetooth

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

internal suspend fun IKmpBlePeripheral.awaitBond(
    encryptedReadCharacteristicUuid: String,
    scope: CoroutineScope,
): Outcome<Unit, KmpBleError> =
    withScope(scope) {
        val deferred = CompletableDeferred<Outcome<Unit, KmpBleError>>()

        coroutineScope {
            async {
                connectionStatus
                    .filter { it == KmpBleConnectionStatus.Disconnected }
                    .collect {
                        deferred.complete(Outcome.Error(KmpBleError.NotBonded))
                        this@coroutineScope.coroutineContext.cancelChildren()
                    }
            }
            async {
                when (read(encryptedReadCharacteristicUuid)) {
                    is Outcome.Ok -> deferred.complete(Outcome.Ok(Unit))
                    is Outcome.Error -> deferred.complete(Outcome.Error(KmpBleError.NotBonded))
                }
                this@coroutineScope.coroutineContext.cancelChildren()
            }
        }

        deferred.await()
    }

internal suspend inline fun <T> withScope(
    scope: CoroutineScope,
    crossinline block: suspend () -> Outcome<T, KmpBleError>,
): Outcome<T, KmpBleError> =
    try {
        withContext(scope.coroutineContext) { block() }
    } catch (t: KmpBlePeripheralDisconnect) {
        Outcome.Error(KmpBleError.PeripheralDisconnected)
    } catch (t: Throwable) {
        Outcome.Error(KmpBleError.Unknown(t))
    }
