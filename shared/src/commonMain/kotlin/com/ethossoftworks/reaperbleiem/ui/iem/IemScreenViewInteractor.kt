package com.ethossoftworks.reaperbleiem.ui.iem

import com.ethossoftworks.reaperbleiem.coordinator.AppCoordinator
import com.ethossoftworks.reaperbleiem.interactor.CapabilityInteractor
import com.ethossoftworks.reaperbleiem.interactor.IemInteractor
import com.ethossoftworks.reaperbleiem.interactor.InfoMessageInteractor
import com.ethossoftworks.reaperbleiem.interactor.ServiceStatus
import com.ethossoftworks.reaperbleiem.service.iem.FaderInfo
import com.ethossoftworks.reaperbleiem.service.iem.IemContext
import com.ethossoftworks.reaperbleiem.service.iem.IemEvent
import com.ethossoftworks.reaperbleiem.service.iem.Track
import com.ethossoftworks.reaperbleiem.service.preferences.CentralPreferencesService
import com.ethossoftworks.reaperbleiem.service.preferences.PeripheralPreferencesService
import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.capability.NoPermissionReason
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import reacue.shared.generated.resources.Res
import reacue.shared.generated.resources.ble_protocol_mismatch
import reacue.shared.generated.resources.microphone_permission_is_required
import reacue.shared.generated.resources.talkback_jsfx_protocol_mismatch
import reacue.shared.generated.resources.tcp_protocol_mismatch
import kotlin.time.Duration.Companion.seconds

data class IemScreenViewState(
    val bluetoothStatus: CapabilityStatus = CapabilityStatus.Unknown,
    val selectedIemId: Int? = null,
    val lastSelectedIemName: String? = null,
    val tracks: Map<Int, Track> = emptyMap(),
    val faderInfo: FaderInfo = FaderInfo(),
    val projectName: String = "Unknown",
    val serviceStatus: ServiceStatus = ServiceStatus.Disconnected,
    val isRefreshing: Boolean = false,
    val numberInputModalType: NumberInputModalType? = null,
    val passcodeEntry: CompletableDeferred<String>? = null,
    val isViewPasscodeModalVisible: Boolean = false,
    val passcode: String = "",
    val showTalkbackButton: Boolean = true,
    val isTalkbackActive: Boolean = false,
)

enum class NumberInputModalType {
    Adjust,
    Set,
}

class IemScreenViewInteractor(
    private val iemContext: IemContext,
    private val iemInteractor: IemInteractor,
    private val capabilityInteractor: CapabilityInteractor,
    private val coordinator: AppCoordinator,
    private val peripheralPreferencesService: PeripheralPreferencesService?,
    private val centralPreferencesService: CentralPreferencesService?,
    private val infoMessageInteractor: InfoMessageInteractor,
) :
    Interactor<IemScreenViewState>(
        initialState = IemScreenViewState(),
        dependencies = listOf(iemInteractor, capabilityInteractor),
    ) {

    private val subscriptionJob = atomic<Job?>(null)

    override fun computed(state: IemScreenViewState): IemScreenViewState {
        return state.copy(
            bluetoothStatus = capabilityInteractor.state.bluetoothStatus,
            tracks = iemInteractor.state.tracks,
            projectName = iemInteractor.state.projectName,
            faderInfo = iemInteractor.state.faderInfo,
            serviceStatus = iemInteractor.state.serviceStatus,
            isRefreshing = iemInteractor.state.isRefreshing,
            showTalkbackButton =
                iemInteractor.isTalkbackChannelOpen && centralPreferencesService?.settings?.value?.showTalkBack == true,
        )
    }

    fun onMount() {
        interactorScope.launch { start() }
    }

    fun onUnmount() {
        if (iemContext is IemContext.Peripheral) iemInteractor.sendDisconnectEvent()
        subscriptionJob.value?.cancel()
    }

    fun onTalkbackPress() {
        interactorScope.launch {
            when (val status = capabilityInteractor.queryMicrophonePermissions()) {
                is CapabilityStatus.NoPermission -> {
                    interactorScope.launch {
                        if (status.reason == NoPermissionReason.NotRequested) {
                            capabilityInteractor.requestMicrophonePermission()
                            return@launch
                        } else {
                            infoMessageInteractor.enqueueMessage(
                                getString(Res.string.microphone_permission_is_required)
                            )
                        }
                    }
                }

                CapabilityStatus.Ready -> {
                    iemInteractor.startTalkback()
                    update { state -> state.copy(isTalkbackActive = true) }
                }

                else -> {
                    interactorScope.launch {
                        infoMessageInteractor.enqueueMessage(getString(Res.string.microphone_permission_is_required))
                    }
                }
            }
        }
    }

    fun onTalkbackRelease() {
        iemInteractor.stopTalkback()
        update { state -> state.copy(isTalkbackActive = false) }
    }

    fun onIemSelect(id: Int) {
        update { state -> state.copy(selectedIemId = id, lastSelectedIemName = state.tracks[id]?.name) }
    }

    fun onConnectClick() {
        start()
    }

    fun onBackToScanClick() {
        coordinator.onBackToScanClick()
    }

    fun onRefreshClick() {
        interactorScope.launch { iemInteractor.refresh() }
    }

    fun onSetAllTo0Click() {
        interactorScope.launch {
            val track = state.tracks[state.selectedIemId] ?: return@launch
            for ((_, receive) in track.receives) {
                iemInteractor.setReceiveVolume(track.id, receive.id, state.faderInfo.dbToNormalized(0.0f))
            }
        }
    }

    fun onSetAllToNClick() {
        update { state -> state.copy(numberInputModalType = NumberInputModalType.Set) }
    }

    fun onAdjustAllByNClick() {
        update { state -> state.copy(numberInputModalType = NumberInputModalType.Adjust) }
    }

    fun onNumberModalCommit(value: Float) {
        val modalType = state.numberInputModalType ?: return

        update { state -> state.copy(numberInputModalType = null) }

        interactorScope.launch {
            val track = state.tracks[state.selectedIemId] ?: return@launch

            for ((_, receive) in track.receives) {
                val clampedValue =
                    when (modalType) {
                        NumberInputModalType.Adjust ->
                            (state.faderInfo.dbToNormalized(state.faderInfo.normalizedToDb(receive.volume) + value))

                        NumberInputModalType.Set -> state.faderInfo.dbToNormalized(value)
                    }
                iemInteractor.setReceiveVolume(track.id, receive.id, clampedValue)
            }
        }
    }

    fun onNumberModalDismiss() {
        update { state -> state.copy(numberInputModalType = null) }
    }

    fun onOutputVolumeChange(trackId: Int, hardwareOutId: Int, value: Float) {
        iemInteractor.setOutputVolume(trackId, hardwareOutId, value)
    }

    fun onReceiveVolumeChange(trackId: Int, receiveId: Int, value: Float) {
        iemInteractor.setReceiveVolume(trackId, receiveId, value)
    }

    fun onReceivePanChange(trackId: Int, receiveId: Int, value: Float) {
        iemInteractor.setReceivePan(trackId, receiveId, value)
    }

    fun onReceiveMuteToggle(trackId: Int, receiveId: Int) {
        val isMuted = state.tracks[trackId]?.receives[receiveId]?.isMuted ?: return
        iemInteractor.setReceiveMute(trackId, receiveId, !isMuted)
    }

    fun onPasscodeEntryDismiss() {
        update { state -> state.copy(passcodeEntry = null) }
    }

    fun onViewPasscodeClick() {
        interactorScope.launch {
            val passcode = peripheralPreferencesService?.settings?.value?.hostPasscode
            update { state -> state.copy(isViewPasscodeModalVisible = true, passcode = passcode ?: "") }
        }
    }

    fun onViewPasscodeModalDismiss() {
        update { state -> state.copy(isViewPasscodeModalVisible = false) }
    }

    private fun start() {
        subscriptionJob.value?.cancel()
        subscriptionJob.value =
            iemInteractor
                .subscribe(iemContext)
                .onEach { event ->
                    when (event) {
                        is IemEvent.StructureChanged -> {
                            val trackMatch =
                                event.tracks.values.firstOrNull { track ->
                                    track.isIem && track.name == state.lastSelectedIemName
                                }
                            update { state -> state.copy(selectedIemId = trackMatch?.id) }
                        }
                        is IemEvent.PasscodeRequired -> update { state -> state.copy(passcodeEntry = event.passcode) }
                        is IemEvent.Error -> {
                            val message =
                                when (event) {
                                    is IemEvent.Error.TalkbackJsfxProtocolMismatch ->
                                        getString(
                                            Res.string.talkback_jsfx_protocol_mismatch,
                                            event.expected,
                                            event.received,
                                        )
                                    is IemEvent.Error.TcpProtocolMismatch ->
                                        getString(Res.string.tcp_protocol_mismatch, event.expected, event.received)
                                    is IemEvent.Error.BleProtocolMismatch ->
                                        getString(Res.string.ble_protocol_mismatch, event.expected, event.received)
                                    else -> null
                                }
                            if (message != null) {
                                infoMessageInteractor.enqueueMessage(message = message, duration = 4.seconds)
                            }
                        }
                        else -> {}
                    }
                }
                .onCompletion { update { state -> state.copy(passcodeEntry = null) } }
                .launchIn(interactorScope)
    }
}
