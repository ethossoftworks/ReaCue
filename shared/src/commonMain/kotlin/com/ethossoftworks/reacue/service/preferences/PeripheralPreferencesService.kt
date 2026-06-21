@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reacue.service.preferences

import co.touchlab.kermit.Logger
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import com.outsidesource.oskitkmp.storage.IKmpKvStore
import com.outsidesource.oskitkmp.storage.IKmpKvStoreNode
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeripheralSettings(
    val hostId: Uuid = Uuid.random(),
    val hostName: String = "ReaCue" + randomNumbers(2),
    val hostPasscode: String = randomCharacters(12).chunked(4).joinToString("-"),
    val reacueReaScriptPort: Int = 9007,
)

class PeripheralPreferencesService(private val kvStore: IKmpKvStore) {
    private val node = CompletableDeferred<IKmpKvStoreNode>()
    private val loaded = CompletableDeferred<Unit>()
    private val _settings = MutableStateFlow(PeripheralSettings())
    val settings: StateFlow<PeripheralSettings> = _settings.asStateFlow()

    private val KeyHostId = "host_id"
    private val KeyHostName = "host_name"
    private val KeyHostPasscode = "host_passcode"
    private val KeyReacueReaScriptPort = "reacue_reascript_port"

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val nodeResult =
                kvStore.openNode("ReaCue").unwrapOrReturn {
                    Logger.i { "Could not open KvStore" }
                    return@launch
                }

            val default = PeripheralSettings()
            if (!nodeResult.contains(KeyHostId)) nodeResult.putBytes(KeyHostId, default.hostId.toByteArray())
            if (!nodeResult.contains(KeyHostName)) nodeResult.putString(KeyHostName, default.hostName)
            if (!nodeResult.contains(KeyHostPasscode)) nodeResult.putString(KeyHostPasscode, default.hostPasscode)

            _settings.value =
                PeripheralSettings(
                    hostId = Uuid.fromByteArray(nodeResult.getBytes(KeyHostId) ?: default.hostId.toByteArray()),
                    hostName = nodeResult.getString(KeyHostName) ?: default.hostName,
                    hostPasscode = nodeResult.getString(KeyHostPasscode) ?: default.hostPasscode,
                    reacueReaScriptPort = nodeResult.getInt(KeyReacueReaScriptPort) ?: default.reacueReaScriptPort,
                )

            node.complete(nodeResult)
            loaded.complete(Unit)
        }
    }

    suspend fun awaitSettings(): PeripheralSettings {
        loaded.await()
        return settings.value
    }

    suspend fun resetToDefaults(): Outcome<Unit, Any> {
        val settings = PeripheralSettings()
        setHostName(settings.hostName).unwrapOrReturn {
            return it
        }
        setHostPasscode(settings.hostPasscode).unwrapOrReturn {
            return it
        }
        setReaCueReaScriptPort(settings.reacueReaScriptPort).unwrapOrReturn {
            return it
        }
        _settings.value = settings

        return Outcome.Ok(Unit)
    }

    suspend fun setHostName(value: String): Outcome<Unit, Any> {
        val result = node.await().putString(KeyHostName, value)
        _settings.update { it.copy(hostName = value) }
        return result
    }

    suspend fun setHostPasscode(value: String): Outcome<Unit, Any> {
        val result = node.await().putString(KeyHostPasscode, value)
        _settings.update { it.copy(hostPasscode = value) }
        return result
    }

    suspend fun setReaCueReaScriptPort(value: Int): Outcome<Unit, Any> {
        val result = node.await().putInt(KeyReacueReaScriptPort, value)
        _settings.update { it.copy(reacueReaScriptPort = value) }
        return result
    }
}

private fun randomCharacters(length: Int): String {
    val charPool = ('a'..'z') + ('0'..'9')
    return CharArray(length) { charPool.random() }.concatToString()
}

private fun randomNumbers(length: Int): String {
    val charPool = ('0'..'9')
    return CharArray(length) { charPool.random() }.concatToString()
}
