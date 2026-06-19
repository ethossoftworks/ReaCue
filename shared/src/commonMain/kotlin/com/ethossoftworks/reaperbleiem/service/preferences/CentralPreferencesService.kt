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
import kotlinx.coroutines.launch

data class CentralSettings(
    val showTalkBack: Boolean = true,
    val talkbackChannel: Int = -1,
)

class CentralPreferencesService(private val kvStore: IKmpKvStore) {
    private val node = CompletableDeferred<IKmpKvStoreNode>()
    private val loaded = CompletableDeferred<Unit>()
    private val _settings = MutableStateFlow(CentralSettings())
    val settings: StateFlow<CentralSettings> = _settings.asStateFlow()

    private val KeyShowTalkback = "show_talkback"
    private val KeyTalkbackChannel = "talkback_channel"

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val nodeResult =
                kvStore.openNode("ReaCue").unwrapOrReturn {
                    Logger.i { "Could not open KvStore" }
                    return@launch
                }

            val default = CentralSettings()
            if (!nodeResult.contains(KeyShowTalkback)) nodeResult.putBoolean(KeyShowTalkback, default.showTalkBack)
            if (!nodeResult.contains(KeyTalkbackChannel)) nodeResult.putInt(KeyTalkbackChannel, default.talkbackChannel)

            _settings.value =
                CentralSettings(
                    showTalkBack = nodeResult.getBoolean(KeyShowTalkback) ?: default.showTalkBack,
                    talkbackChannel = nodeResult.getInt(KeyTalkbackChannel) ?: default.talkbackChannel,
                )

            node.complete(nodeResult)
            loaded.complete(Unit)
        }
    }

    suspend fun awaitSettings(): CentralSettings {
        loaded.await()
        return settings.value
    }

    suspend fun putPasscode(peripheralId: String, passcode: String): Outcome<Unit, Any> {
        return node.await().putString(passcodeKey(peripheralId), passcode)
    }

    suspend fun getPasscode(peripheralId: String): String? {
        return node.await().getString(passcodeKey(peripheralId))
    }

    suspend fun resetToDefaults(): Outcome<Unit, Any> {
        val settings = CentralSettings()

        setShowTalkback(settings.showTalkBack).unwrapOrReturn {
            return it
        }

        setTalkbackChannel(settings.talkbackChannel).unwrapOrReturn {
            return it
        }

        _settings.value = settings

        return Outcome.Ok(Unit)
    }

    suspend fun setShowTalkback(value: Boolean): Outcome<Unit, Any> {
        val result = node.await().putBoolean(KeyShowTalkback, value)
        _settings.value = _settings.value.copy(showTalkBack = value)
        return result
    }

    suspend fun setTalkbackChannel(value: Int): Outcome<Unit, Any> {
        val result = node.await().putInt(KeyTalkbackChannel, value)
        _settings.value = _settings.value.copy(talkbackChannel = value)
        return result
    }

    private fun passcodeKey(peripheralId: String): String = "passcode-$peripheralId"
}
