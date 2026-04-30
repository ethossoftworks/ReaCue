package com.ethossoftworks.reaperbleiem.ui.home

import com.ethossoftworks.reaperbleiem.interactor.CapabilityInteractor
import com.ethossoftworks.reaperbleiem.interactor.IemInteractor
import com.ethossoftworks.reaperbleiem.interactor.IemMix
import com.ethossoftworks.reaperbleiem.interactor.InfoMessageInteractor
import com.ethossoftworks.reaperbleiem.service.iem.Track
import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.launch

data class HomeScreenViewState(
    val bluetoothStatus: CapabilityStatus = CapabilityStatus.Unknown,
    val isServerRunning: Boolean = false,
    val iemOptions: List<IemOption> = emptyList(),
    val selectedIem: Int? = null,
)

data class IemOption(val name: String, val trackId: Int)

class HomeScreenViewInteractor(
    private val iemInteractor: IemInteractor,
    private val capabilityInteractor: CapabilityInteractor,
    private val infoMessageInteractor: InfoMessageInteractor,
) : Interactor<HomeScreenViewState>(
    initialState = HomeScreenViewState(),
    dependencies = listOf(iemInteractor, capabilityInteractor),
) {

    override fun computed(state: HomeScreenViewState): HomeScreenViewState {
        return state.copy(
            bluetoothStatus = capabilityInteractor.state.bluetoothStatus,
            iemOptions = iemInteractor.state.iems.values.mapNotNull {
                val track = iemInteractor.state.tracks[it.trackId] ?: return@mapNotNull null
                IemOption(name = track.name, trackId = it.trackId)
            }
        )
    }

    fun onMounted() {
        interactorScope.launch {
            update { state -> state.copy(isServerRunning = true) }
            iemInteractor.subscribe().collect {
                println(it)
            }
            update { state -> state.copy(isServerRunning = false) }
        }
    }
}
