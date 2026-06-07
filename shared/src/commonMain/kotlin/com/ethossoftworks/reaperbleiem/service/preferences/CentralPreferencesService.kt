package com.ethossoftworks.reaperbleiem.service.preferences

import co.touchlab.kermit.Logger
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import com.outsidesource.oskitkmp.storage.IKmpKvStore
import com.outsidesource.oskitkmp.storage.IKmpKvStoreNode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class CentralPreferencesService(private val kvStore: IKmpKvStore) {
    private val node = CompletableDeferred<IKmpKvStoreNode>()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val nodeResult = kvStore.openNode("ReaCue").unwrapOrReturn {
                Logger.i { "Could not open KvStore" }
                return@launch
            }
            node.complete(nodeResult)
        }
    }

    suspend fun putPasscode(peripheralId: String, passcode: String): Outcome<Unit, Any> {
        return node.await().putString(passcodeKey(peripheralId), passcode)
    }

    suspend fun getPasscode(peripheralId: String): String? {
        return node.await().getString(passcodeKey(peripheralId))
    }

    private fun passcodeKey(peripheralId: String): String = "passcode-$peripheralId"
}
