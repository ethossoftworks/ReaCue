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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppSettings(
    val hostId: String = "",
    val hostPasscode: String = "",
    val reaperWebPort: Int = 8000,
    val reaperOscDevicePort: Int = 9000,
    val reaperOscListenPort: Int = 8000,
)

class PreferencesService(private val kvStore: IKmpKvStore) {
    private val node = CompletableDeferred<IKmpKvStoreNode>()
    private val loaded = CompletableDeferred<Unit>()
    private val _settings = MutableStateFlow(AppSettings())

    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val KeyHostId = "host_id"
    private val KeyHostPasscode = "host_passcode"
    private val KeyReaperWebPort = "reaper_web_port"
    private val KeyReaperOscDevicePort = "reaper_osc_device_port"
    private val KeyReaperOscListenPort = "reaper_osc_listen_port"

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val nodeResult =
                kvStore.openNode("ReaCue").unwrapOrReturn {
                    Logger.i { "Could not open KvStore" }
                    return@launch
                }

            if (!nodeResult.contains(KeyHostId)) {
                nodeResult.putString(KeyHostId, "ReaCue" + randomNumbers(2))
            }

            if (!nodeResult.contains(KeyHostPasscode)) {
                nodeResult.putString(KeyHostPasscode, randomCharacters(8))
            }

            _settings.value =
                AppSettings(
                    hostId = nodeResult.getString(KeyHostId) ?: ("ReaCue" + randomNumbers(2)),
                    hostPasscode = nodeResult.getString(KeyHostPasscode) ?: "",
                    reaperWebPort = nodeResult.getInt(KeyReaperWebPort) ?: 8000,
                    reaperOscDevicePort = nodeResult.getInt(KeyReaperOscDevicePort) ?: 9000,
                    reaperOscListenPort = nodeResult.getInt(KeyReaperOscListenPort) ?: 8000,
                )

            node.complete(nodeResult)
            loaded.complete(Unit)
        }
    }

    suspend fun awaitSettings(): AppSettings {
        loaded.await()
        return settings.value
    }

    suspend fun setHostId(value: String): Outcome<Unit, Any> {
        val result = node.await().putString(KeyHostId, value)
        _settings.update { it.copy(hostId = value) }
        return result
    }

    suspend fun setHostPasscode(value: String): Outcome<Unit, Any> {
        val result = node.await().putString(KeyHostPasscode, value)
        _settings.update { it.copy(hostPasscode = value) }
        return result
    }

    suspend fun setReaperWebPort(value: Int): Outcome<Unit, Any> {
        val result = node.await().putInt(KeyReaperWebPort, value)
        _settings.update { it.copy(reaperWebPort = value) }
        return result
    }

    suspend fun setReaperOscDevicePort(value: Int): Outcome<Unit, Any> {
        val result = node.await().putInt(KeyReaperOscDevicePort, value)
        _settings.update { it.copy(reaperOscDevicePort = value) }
        return result
    }

    suspend fun setReaperOscListenPort(value: Int): Outcome<Unit, Any> {
        val result = node.await().putInt(KeyReaperOscListenPort, value)
        _settings.update { it.copy(reaperOscListenPort = value) }
        return result
    }

    private fun randomCharacters(length: Int): String {
        val charPool = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return CharArray(length) { charPool.random() }.concatToString()
    }

    private fun randomNumbers(length: Int): String {
        val charPool = ('0'..'9')
        return CharArray(length) { charPool.random() }.concatToString()
    }
}
